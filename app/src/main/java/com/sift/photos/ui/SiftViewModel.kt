package com.sift.photos.ui

import android.app.Application
import android.database.ContentObserver
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sift.photos.data.AccentSource
import com.sift.photos.data.Album
import com.sift.photos.data.DaySection
import com.sift.photos.data.MediaAccess
import com.sift.photos.data.MediaRepository
import com.sift.photos.data.PhotoItem
import com.sift.photos.data.PhotoRef
import com.sift.photos.data.SampleData
import com.sift.photos.data.SiftSettings
import com.sift.photos.data.StorageStats
import com.sift.photos.ui.components.Tab
import com.sift.photos.ui.components.ToastState
import com.sift.photos.ui.theme.PhotoSeed
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Screens above the tab shell. The back stack is a plain list; predictive back pops it. */
sealed interface Screen {
    data object Onboarding : Screen
    data object Home : Screen
    data class Ask(val query: String = "", val submitted: Boolean = false) : Screen
    data object Cleanup : Screen
    data object CleanupReview : Screen
    data object CleanupConfirm : Screen
    data object Trash : Screen
    data class Viewer(val items: List<PhotoItem>, val index: Int) : Screen
    data class EditPanel(val item: PhotoItem) : Screen
    data class Editor(val item: PhotoItem) : Screen
    data class Credentials(val item: PhotoItem) : Screen
    data object Receipt : Screen
    data object Ticket : Screen
    data object Card : Screen
    data object Offload : Screen
    data object ChooseStorage : Screen
    data object Settings : Screen
    data object SmartFeatures : Screen
    data class AlbumDetail(val album: Album) : Screen
    data object Locked : Screen
}

class SiftViewModel(app: Application) : AndroidViewModel(app) {
    val settings = SiftSettings(app)
    val repo = MediaRepository(app)
    val toast = ToastState()

    val stack = mutableStateListOf<Screen>(if (settings.onboarded) Screen.Home else Screen.Onboarding)
    var tab by mutableStateOf(Tab.Library)

    var access by mutableStateOf(repo.access())
        private set
    var deviceItems by mutableStateOf<List<PhotoItem>>(emptyList())
        private set
    var deviceTrash by mutableStateOf<List<PhotoItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var storage by mutableStateOf<StorageStats?>(null)
        private set

    /** Sample-library state so every flow is reversible without touching real files. */
    val sampleTrashed = mutableStateListOf<String>()
    val sampleFavorites = mutableStateListOf<String>()
    var cleanupDone by mutableStateOf(false)
    val actionsDone = mutableStateListOf<String>()
    var editsReverted = mutableStateListOf<String>()
    var flightUndone by mutableStateOf(false)

    /** Favorite state confirmed this session, ahead of the next MediaStore reload. */
    val favorites = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()

    private var observer: ContentObserver? = null

    init {
        observer = repo.observe { refresh() }
        refresh()
    }

    override fun onCleared() {
        observer?.let(repo::unobserve)
    }

    val current: Screen get() = stack.last()

    fun push(s: Screen) { stack.add(s) }
    fun pop(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false
    fun replace(s: Screen) { stack[stack.lastIndex] = s }
    fun popTo(predicate: (Screen) -> Boolean) {
        while (stack.size > 1 && !predicate(stack.last())) stack.removeAt(stack.lastIndex)
    }

    fun finishOnboarding(sample: Boolean) {
        settings.onboarded = true
        if (sample) settings.sampleLibrary = true
        stack.clear()
        stack.add(Screen.Home)
        refresh()
    }

    fun refresh() {
        access = repo.access()
        viewModelScope.launch {
            loading = true
            deviceItems = repo.load()
            // MediaStore now reflects confirmed favorites; drop the session overrides for real items.
            favorites.keys.filter { it.startsWith("m") }.forEach { favorites.remove(it) }
            deviceTrash = runCatching { repo.load(trashed = true) }.getOrDefault(emptyList())
            storage = repo.storage(deviceItems)
            loading = false
        }
    }

    val sample: Boolean get() = settings.sampleLibrary
    val ai: Boolean get() = settings.aiEnabled

    fun isFavorite(item: PhotoItem) = favorites[item.key] ?: item.favorite

    /** The freshest copy of an item (favorites, after a reload) for screens that hold a snapshot. */
    fun live(item: PhotoItem): PhotoItem {
        val fresh = if (item.ref is PhotoRef.Media) deviceItems.firstOrNull { it.key == item.key } ?: item else item
        return fresh.copy(favorite = isFavorite(fresh))
    }

    suspend fun saveEdit(item: PhotoItem, params: com.sift.photos.data.EditParams) {
        val ref = item.ref as? PhotoRef.Media ?: return
        val uri = repo.saveEdited(item, ref, params)
        if (uri == null) {
            toast.show("Couldn't save the edit. The original is unchanged.", icon = "error", onAction = null)
            return
        }
        pop()
        refresh()
        toast.show("Saved an edited copy · original kept", icon = "check_circle") {
            repo.deleteOwn(uri); refresh()
        }
    }

    val sections: List<DaySection>
        get() = if (sample) {
            SampleData.sections.map { s -> s.copy(items = s.items.filter { it.key !in sampleTrashed }.map { it.copy(favorite = isFavorite(it)) }) }
                .filter { it.items.isNotEmpty() }
        } else group(deviceItems.map { if (it.key in favorites) it.copy(favorite = isFavorite(it)) else it })

    val libraryItems: List<PhotoItem> get() = sections.flatMap { it.items }

    val albums: List<Album>
        get() = if (sample) SampleData.albums else deviceItems
            .groupBy { it.bucket ?: "Other" }
            .entries
            .sortedByDescending { it.value.size }
            .map { (name, items) ->
                Album("b:$name", name, "%,d items".format(items.size), items.first().ref,
                    icon = if (name.equals("Screenshots", true)) "screenshot" else if (name.equals("Camera", true)) "photo_camera" else null,
                    items = items)
            }

    private fun group(items: List<PhotoItem>): List<DaySection> {
        val now = Calendar.getInstance()
        val today = MediaRepository.dayKey(now.timeInMillis)
        now.add(Calendar.DAY_OF_YEAR, -1)
        val yesterday = MediaRepository.dayKey(now.timeInMillis)
        val dayFmt = SimpleDateFormat("EEE d MMM", Locale.getDefault())
        val dayYearFmt = SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault())
        val thisYear = Calendar.getInstance().get(Calendar.YEAR)
        return items.groupBy { MediaRepository.dayKey(it.dateMillis) }.map { (key, list) ->
            val d = Date(list.first().dateMillis)
            val sameYear = key / 1000 == thisYear
            val label = if (sameYear) dayFmt.format(d) else dayYearFmt.format(d)
            val buckets = list.mapNotNull { it.bucket }.distinct().take(2).joinToString(", ")
            when (key) {
                today -> DaySection("Today", label + if (buckets.isNotEmpty()) " · $buckets" else "", list)
                yesterday -> DaySection("Yesterday", label + if (buckets.isNotEmpty()) " · $buckets" else "", list)
                else -> DaySection(label, buckets, list)
            }
        }
    }

    // ---- Accent sources -----------------------------------------------------------------

    /** Seed for the active accent source. Wallpaper reads the system's wallpaper-derived seed. */
    fun seed(): Int = when (settings.accentSource) {
        AccentSource.Wallpaper -> wallpaperSeed() ?: settings.curated.seed
        AccentSource.Curated -> settings.curated.seed
        AccentSource.Photo -> settings.photoSeed
    }

    fun wallpaperSeed(): Int? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        runCatching { getApplication<Application>().getColor(android.R.color.system_accent1_500) and 0xFFFFFF }.getOrNull()
    } else null

    /** "Use its color": sample the most prominent non-skin, non-neutral cluster, on device. */
    fun useColorOf(item: PhotoItem) {
        viewModelScope.launch {
            val seed = when (val ref = item.ref) {
                is PhotoRef.Media -> repo.samplePixels(ref)?.let(PhotoSeed::extract)
                is PhotoRef.Art -> ref.art.mid.let { c ->
                    ((c.red * 255).toInt() shl 16) or ((c.green * 255).toInt() shl 8) or (c.blue * 255).toInt()
                }
            }
            if (seed == null) {
                toast.show("No clear color in this photo. Accent unchanged.", icon = "colorize")
            } else {
                val prevSource = settings.accentSource
                val prevSeed = settings.photoSeed
                settings.photoSeed = seed
                settings.accentSource = AccentSource.Photo
                toast.show("Accent matched to this photo", icon = "colorize") {
                    settings.photoSeed = prevSeed
                    settings.accentSource = prevSource
                }
            }
        }
    }

    // ---- Sample-library actions (all undoable) -------------------------------------------

    fun trashSample(keys: Collection<String>) {
        val added = keys.filter { it !in sampleTrashed }
        sampleTrashed.addAll(added)
        toast.show("${added.size} ${if (added.size == 1) "item" else "items"} moved to Trash", icon = "delete") {
            sampleTrashed.removeAll(added.toSet())
        }
    }

    fun toggleSampleFavorite(item: PhotoItem) {
        favorites[item.key] = !isFavorite(item)
    }
}
