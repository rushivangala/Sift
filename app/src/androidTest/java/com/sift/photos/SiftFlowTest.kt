package com.sift.photos

import android.Manifest
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end flows on a device. Each test saves screenshots to
 * /sdcard/Android/data/com.sift.photos/files/shots so CI can publish them.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SiftFlowTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private val inserted = mutableListOf<Uri>()

    private fun launch(onboarded: Boolean, sample: Boolean, extra: Map<String, Any> = emptyMap()) {
        ctx.getSharedPreferences("sift", 0).edit().clear().apply {
            putBoolean("onboarded", onboarded)
            putBoolean("sample_library", sample)
            // Deterministic accent for screenshots; the wallpaper source differs per device.
            putString("accent_source", "Curated")
            extra.forEach { (k, v) -> if (v is Boolean) putBoolean(k, v) else putString(k, v.toString()) }
        }.commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    @After
    fun tearDown() {
        scenario?.close()
        inserted.forEach { runCatching { ctx.contentResolver.delete(it, null, null) } }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val bmp = compose.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap()
        val dir = File(ctx.getExternalFilesDir(null), "shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun onboardingLeadsToSampleLibrary() {
        launch(onboarded = false, sample = false)
        compose.onNodeWithText("Allow what Sift needs").assertExists()
        shot("01-onboarding")
        compose.onNodeWithText("Explore with the sample library").performClick()
        waitForText("Ask your photos")
        compose.onNodeWithText("3 photos can become actions").assertExists()
        compose.onAllNodes(hasText("Why:", substring = true)).onFirst().assertExists()
        shot("02-home-light")
    }

    @Test
    fun askReceiptsFromMayAsksBeforeIncludingLowConfidence() {
        launch(onboarded = true, sample = true)
        compose.onNodeWithText("receipts from May").performClick()
        waitForText("14 receipts · €612.40")
        compose.onNodeWithText("2 more might be from May").assertExists()
        shot("03-ask-results")
        compose.onNodeWithText("Include both").performClick()
        waitForText("16 receipts · €651.30")
    }

    @Test
    fun askTypingShowsUnderstoodAs() {
        launch(onboarded = true, sample = true)
        compose.onNodeWithContentDescription("Voice search").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("receipts fr")
        waitForText("Top match understood as", substring = true)
        shot("04-ask-typing")
    }

    @Test
    fun cleanupReviewAndConfirmNeedsHold() {
        launch(onboarded = true, sample = true)
        compose.onNodeWithContentDescription("Cleanup").performClick()
        waitForText("5.6 GB")
        shot("05-cleanup-summary")
        compose.onNodeWithText("Review 4 groups").performClick()
        waitForText("Miradouro da Graça")
        shot("06-cleanup-review")
        compose.onNodeWithText("Continue").performClick()
        waitForText("Move 342 items to Trash?")
        compose.onNodeWithText("Over 100 items, so this needs a hold. A tap alone won't do it.").assertExists()
        shot("07-cleanup-confirm")
    }

    @Test
    fun viewerShowsTransparentAiEdit() {
        launch(onboarded = true, sample = true)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription("Cais do Sodré", substring = true))
        compose.onAllNodes(hasContentDescription("18:52", substring = true)).onFirst().performClick()
        waitForText("Auto-edit applied · original kept")
        shot("08-viewer-auto-edit")
        compose.onNodeWithText("What changed").performClick()
        waitForText("What changed and why")
        compose.onNodeWithText("Remove person, left edge").assertExists()
        shot("09-what-changed")
    }

    @Test
    fun actionsAndConfirmFlows() {
        launch(onboarded = true, sample = true)
        compose.onAllNodesWithText("Actions").onFirst().performClick()
        waitForText("Save contact: Mara Lindqvist")
        shot("10-actions")
        compose.onNodeWithText("Answer 2 questions").performClick()
        waitForText("Which email is it?")
        compose.onNodeWithText("mara.lindqvist@nordvik.se").performClick()
        compose.onNodeWithText("nordvik.se").performClick()
        shot("11-card-contact")
    }

    @Test
    fun storageHasNoUpsell() {
        launch(onboarded = true, sample = true)
        compose.onAllNodesWithText("Storage").onFirst().performClick()
        waitForText("312 GB library")
        for (word in listOf("Upgrade", "Buy", "Subscribe", "Premium", "plan")) {
            assertTrue("found upsell word $word", compose.onAllNodes(hasText(word, substring = true, ignoreCase = true)).fetchSemanticsNodes().isEmpty())
        }
        shot("12-storage")
    }

    @Test
    fun darkTrueBlackAndAccentPicker() {
        launch(onboarded = true, sample = true, extra = mapOf("theme_mode" to "Dark"))
        shot("13-home-dark")
        compose.onNodeWithContentDescription("Settings").performClick()
        waitForText("Theme")
        compose.onNodeWithText("Curated").performClick()
        compose.onNodeWithContentDescription("Moss accent").performClick()
        waitForText("seed #4F8A5B", substring = true)
        shot("14-settings-dark-moss")
        compose.onNodeWithText("True black").performClick()
        compose.waitForIdle()
        shot("15-settings-true-black")
    }

    @Test
    fun globalAiOffPausesEverySurface() {
        launch(onboarded = true, sample = true)
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onAllNodesWithText("Smart features").onFirst().performScrollTo().performClick()
        waitForText("Master switch", substring = true)
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        waitForText("Off. Search, faces", substring = true)
        shot("16-smart-features-off")
        InstrumentationRegistry.getInstrumentation().runOnMainSync { scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() } }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { scenario?.onActivity { it.onBackPressedDispatcher.onBackPressed() } }
        waitForText("Search your photos")
        assertTrue(compose.onAllNodes(hasText("can become actions", substring = true)).fetchSemanticsNodes().isEmpty())
        shot("17-home-ai-off")
    }

    @Test
    fun realLibraryReadsMediaStore() {
        val pkg = ctx.packageName
        val ua = InstrumentationRegistry.getInstrumentation().uiAutomation
        ua.grantRuntimePermission(pkg, Manifest.permission.READ_MEDIA_IMAGES)
        ua.grantRuntimePermission(pkg, Manifest.permission.READ_MEDIA_VIDEO)
        listOf(Color.rgb(47, 127, 181), Color.rgb(194, 69, 58), Color.rgb(60, 125, 78)).forEachIndexed { i, color ->
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "sift_test_$i.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SiftTest")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
            inserted += uri
            ctx.contentResolver.openOutputStream(uri)!!.use { out ->
                Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { Canvas(this).drawColor(color) }
                    .compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        launch(onboarded = true, sample = false)
        compose.waitUntil(15_000) { compose.onAllNodes(hasContentDescription("Photo,", substring = true)).fetchSemanticsNodes().size >= 3 }
        compose.onNodeWithText("Today").assertExists()
        shot("18-real-library")
        compose.onAllNodes(hasContentDescription("Photo,", substring = true)).onFirst().performClick()
        compose.onNodeWithContentDescription("Info").performClick()
        waitForText("sift_test_", substring = true)
        shot("19-real-viewer-info")
    }
}
