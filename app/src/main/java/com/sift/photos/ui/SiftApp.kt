package com.sift.photos.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.sift.photos.data.PhotoItem
import com.sift.photos.data.PhotoRef
import com.sift.photos.data.ThemeMode
import com.sift.photos.ui.components.BottomNav
import com.sift.photos.ui.components.LocalMediaRepository
import com.sift.photos.ui.components.Tab
import com.sift.photos.ui.components.ToastHost
import com.sift.photos.ui.screens.AlbumDetailScreen
import com.sift.photos.ui.screens.AlbumsScreen
import com.sift.photos.ui.screens.ActionsScreen
import com.sift.photos.ui.screens.AskScreen
import com.sift.photos.ui.screens.CardScreen
import com.sift.photos.ui.screens.ChooseStorageScreen
import com.sift.photos.ui.screens.CleanupConfirmScreen
import com.sift.photos.ui.screens.CleanupReviewScreen
import com.sift.photos.ui.screens.CleanupScreen
import com.sift.photos.ui.screens.CredentialsScreen
import com.sift.photos.ui.screens.EditPanelScreen
import com.sift.photos.ui.screens.LibraryScreen
import com.sift.photos.ui.screens.LockedScreen
import com.sift.photos.ui.screens.OffloadScreen
import com.sift.photos.ui.screens.OnboardingScreen
import com.sift.photos.ui.screens.ReceiptScreen
import com.sift.photos.ui.screens.SettingsScreen
import com.sift.photos.ui.screens.SmartFeaturesScreen
import com.sift.photos.ui.screens.StorageScreen
import com.sift.photos.ui.screens.TicketScreen
import com.sift.photos.ui.screens.TrashScreen
import com.sift.photos.ui.screens.ViewerScreen
import com.sift.photos.ui.theme.Motion
import com.sift.photos.ui.theme.Neutrals
import com.sift.photos.ui.theme.Sift
import com.sift.photos.ui.theme.SiftTheme

/** Real media operations go through the system's own consent dialogs (trash, favorite, share). */
class MediaActions(
    val trash: (List<PhotoItem>) -> Unit,
    val restore: (List<PhotoItem>) -> Unit,
    val favorite: (PhotoItem, Boolean) -> Unit,
    val share: (List<PhotoItem>) -> Unit,
    val requestAccess: () -> Unit,
)

val LocalActions = staticCompositionLocalOf<MediaActions> { error("No MediaActions") }

val MediaPermissions = arrayOf(
    Manifest.permission.READ_MEDIA_IMAGES,
    Manifest.permission.READ_MEDIA_VIDEO,
    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    Manifest.permission.ACCESS_MEDIA_LOCATION,
)

@Composable
fun SiftApp(vm: SiftViewModel) {
    val s = vm.settings
    val systemDark = isSystemInDarkTheme()
    val dark = when (s.themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val neutrals = when {
        !dark -> Neutrals.Light
        s.trueBlack -> Neutrals.Black
        else -> Neutrals.Dark
    }
    val context = LocalContext.current
    val activity = context as? Activity
    LaunchedEffect(dark) {
        activity?.window?.let { w ->
            WindowCompat.getInsetsController(w, w.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    LaunchedEffect(s.hidePreviews) { activity?.setRecentsScreenshotEnabled(!s.hidePreviews) }

    // One entry per system request, so results line up with what was asked. Empty = no Undo toast.
    val pending = remember { ArrayDeque<Pair<List<PhotoItem>, () -> Unit>>() }
    val restoreRef = remember { arrayOfNulls<(List<PhotoItem>) -> Unit>(1) }
    val senderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val (trashed, onOk) = pending.removeFirstOrNull() ?: (emptyList<PhotoItem>() to {})
        if (result.resultCode == Activity.RESULT_OK) {
            onOk()
            vm.refresh()
            if (trashed.isNotEmpty()) {
                vm.toast.show("${trashed.size} ${if (trashed.size == 1) "item" else "items"} moved to Trash", icon = "delete") {
                    restoreRef[0]?.invoke(trashed)
                }
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refresh()
    }

    val actions = remember(vm) {
        MediaActions(
            trash = { items ->
                val sampleKeys = items.filter { it.ref is PhotoRef.Art }.map { it.key }
                if (sampleKeys.isNotEmpty()) vm.trashSample(sampleKeys)
                val uris = items.mapNotNull { (it.ref as? PhotoRef.Media)?.uri }
                if (uris.isNotEmpty()) {
                    pending.addLast(items.filter { it.ref is PhotoRef.Media } to {})
                    val pi = MediaStore.createTrashRequest(context.contentResolver, uris, true)
                    senderLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                }
            },
            restore = { items ->
                val uris = items.mapNotNull { (it.ref as? PhotoRef.Media)?.uri }
                if (uris.isNotEmpty()) {
                    pending.addLast(emptyList<PhotoItem>() to {})
                    val pi = MediaStore.createTrashRequest(context.contentResolver, uris, false)
                    senderLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                }
            },
            favorite = { item, fav ->
                when (val ref = item.ref) {
                    is PhotoRef.Art -> vm.toggleSampleFavorite(item)
                    is PhotoRef.Media -> {
                        // Shown at once; the system asks for consent unless Sift has "Media management" access.
                        pending.addLast(emptyList<PhotoItem>() to { vm.favorites[item.key] = fav })
                        val pi = MediaStore.createFavoriteRequest(context.contentResolver, listOf(ref.uri), fav)
                        senderLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    }
                }
            },
            share = { items -> vm.push(Screen.Share(items)) },
            requestAccess = { permissionLauncher.launch(MediaPermissions) },
        ).also { restoreRef[0] = it.restore }
    }

    SiftTheme(seed = vm.seed(), neutrals = neutrals) {
        CompositionLocalProvider(LocalMediaRepository provides vm.repo, LocalActions provides actions) {
            val c = Sift.colors
            BackHandler(enabled = vm.stack.size > 1 || vm.tab != Tab.Library) {
                if (!vm.pop()) vm.tab = Tab.Library
            }
            Box(Modifier.fillMaxSize().background(c.surface)) {
                val reduce = Sift.reduceMotion
                val depth = vm.stack.size
                AnimatedContent(
                    targetState = vm.current to depth,
                    transitionSpec = {
                        val forward = targetState.second >= initialState.second
                        if (reduce) fadeIn(Motion.effectsDefault(reduce)) togetherWith fadeOut(Motion.effectsDefault(reduce))
                        else (fadeIn(Motion.effectsDefault(reduce)) + slideInHorizontally(Motion.spatialDefault(reduce)) { w -> if (forward) w / 10 else -w / 10 }) togetherWith
                            (fadeOut(Motion.effectsFast(reduce)) + slideOutHorizontally(Motion.spatialDefault(reduce)) { w -> if (forward) -w / 10 else w / 10 })
                    },
                    contentKey = { it.first::class to it.second },
                    label = "screens",
                ) { (screen, _) ->
                    Box(Modifier.fillMaxSize().background(c.surface)) { ScreenContent(vm, screen) }
                }
                ToastHost(vm.toast, Modifier.align(Alignment.BottomCenter).padding(bottom = if (vm.current == Screen.Home) 104.dp else 24.dp))
            }
        }
    }
}

@Composable
private fun ScreenContent(vm: SiftViewModel, screen: Screen) {
    when (screen) {
        Screen.Onboarding -> OnboardingScreen(vm)
        Screen.Home -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(vm.tab, transitionSpec = {
                    fadeIn(Motion.effectsDefault(false)) togetherWith fadeOut(Motion.effectsFast(false))
                }, label = "tabs") { tab ->
                    when (tab) {
                        Tab.Library -> LibraryScreen(vm)
                        Tab.Albums -> AlbumsScreen(vm)
                        Tab.Actions -> ActionsScreen(vm)
                        Tab.Storage -> StorageScreen(vm)
                    }
                }
            }
            BottomNav(vm.tab, { vm.tab = it }, actionsDot = vm.ai && vm.actionsDone.size < 3 && vm.sample)
        }
        is Screen.Ask -> AskScreen(vm, screen)
        Screen.Cleanup -> CleanupScreen(vm)
        Screen.CleanupReview -> CleanupReviewScreen(vm)
        Screen.CleanupConfirm -> CleanupConfirmScreen(vm)
        Screen.Trash -> TrashScreen(vm)
        is Screen.Viewer -> ViewerScreen(vm, screen)
        is Screen.EditPanel -> EditPanelScreen(vm, screen.item)
        is Screen.Editor -> com.sift.photos.ui.screens.EditorScreen(vm, screen.item)
        is Screen.Credentials -> CredentialsScreen(vm, screen.item)
        Screen.Receipt -> ReceiptScreen(vm)
        Screen.Ticket -> TicketScreen(vm)
        Screen.Card -> CardScreen(vm)
        Screen.Offload -> OffloadScreen(vm)
        Screen.ChooseStorage -> ChooseStorageScreen(vm)
        Screen.Settings -> SettingsScreen(vm)
        Screen.SmartFeatures -> SmartFeaturesScreen(vm)
        is Screen.AlbumDetail -> AlbumDetailScreen(vm, screen.album)
        Screen.Locked -> LockedScreen(vm)
        is Screen.CleanupGroupReview -> com.sift.photos.ui.screens.CleanupGroupReviewScreen(vm, screen.key)
        is Screen.DocConfirm -> com.sift.photos.ui.screens.DocConfirmScreen(vm, screen.item)
        Screen.Map -> com.sift.photos.ui.screens.MapScreen(vm)
        is Screen.Place -> com.sift.photos.ui.screens.PlaceScreen(vm, screen)
        is Screen.Share -> com.sift.photos.ui.screens.ShareScreen(vm, screen.items)
        Screen.Family -> com.sift.photos.ui.screens.FamilyScreen(vm)
        Screen.Import -> com.sift.photos.ui.screens.ImportScreen(vm)
        Screen.FirstSync -> com.sift.photos.ui.screens.FirstSyncScreen(vm)
    }
}
