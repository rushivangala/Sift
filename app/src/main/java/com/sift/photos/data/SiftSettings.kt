package com.sift.photos.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.sift.photos.ui.theme.CuratedAccent

/** Smart features with per-feature toggles under one master switch (Settings → Smart features). */
enum class SmartFeature(val icon: String, val title: String, val sub: String, val defaultOn: Boolean = true) {
    Search("search", "Search by text and objects", "\"beach\", \"passport\", \"red car\""),
    People("face", "People and pets", "Groups faces. Names stay on this phone."),
    Documents("receipt_long", "Receipts and documents", "Reads text for search and filing"),
    Duplicates("content_copy", "Duplicate finder", "Never deletes without review"),
    Trips("luggage", "Trip and event albums", "Suggests albums with a reason"),
    Edits("auto_fix_high", "Edit suggestions", "Auto straighten, exposure, crop"),
    Places("image_search", "Place names", "Matches GPS to nearby places", defaultOn = false),
}

/** User settings, persisted in SharedPreferences and exposed as Compose state. */
class SiftSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sift", Context.MODE_PRIVATE)

    var onboarded by pref("onboarded", false)
    var sampleLibrary by pref("sample_library", false)
    var themeMode by enumPref("theme_mode", ThemeMode.System)
    var trueBlack by pref("true_black", false)
    var accentSource by enumPref("accent_source", AccentSource.Wallpaper)
    var curated by enumPref("curated", CuratedAccent.Slate)
    var photoSeed by pref("photo_seed", 0x2F7FB5)
    var aiEnabled by pref("ai_enabled", true)
    var stripLocation by pref("strip_location", true)
    var lockedBiometrics by pref("locked_biometrics", true)
    var hidePreviews by pref("hide_previews", false)
    var cloudProcessing by pref("cloud_processing", false)
    var autoFileReceipts by pref("auto_file_receipts", true)

    private val featureState = SmartFeature.entries.associateWith {
        mutableStateOf(prefs.getBoolean("feature_${it.name}", it.defaultOn))
    }

    fun feature(f: SmartFeature): Boolean = aiEnabled && featureState.getValue(f).value
    fun featureSetting(f: SmartFeature): Boolean = featureState.getValue(f).value
    fun setFeature(f: SmartFeature, on: Boolean) {
        featureState.getValue(f).value = on
        prefs.edit { putBoolean("feature_${f.name}", on) }
    }

    private fun pref(key: String, default: Boolean) = Delegate(
        mutableStateOf(prefs.getBoolean(key, default))
    ) { prefs.edit { putBoolean(key, it) } }

    private fun pref(key: String, default: Int) = Delegate(
        mutableStateOf(prefs.getInt(key, default))
    ) { prefs.edit { putInt(key, it) } }

    private inline fun <reified E : Enum<E>> enumPref(key: String, default: E): Delegate<E> {
        val stored = prefs.getString(key, null)
        val initial = enumValues<E>().firstOrNull { it.name == stored } ?: default
        return Delegate(mutableStateOf(initial)) { prefs.edit { putString(key, it.name) } }
    }

    class Delegate<T>(private val state: androidx.compose.runtime.MutableState<T>, private val save: (T) -> Unit) {
        operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = state.value
        operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) {
            state.value = value
            save(value)
        }
    }
}
