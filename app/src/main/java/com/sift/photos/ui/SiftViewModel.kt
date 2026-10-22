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
    data class CleanupGroupReview(val key: String) : Screen
    data class DocConfirm(val item: PhotoItem) : Screen
    data object Map : Screen
    data class Place(val title: String, val items: List<PhotoItem>, val lat: Double, val lon: Double) : Screen
    data class Share(val items: List<PhotoItem>) : Screen
    data object Family : Screen
    data object Import : Screen
    data object FirstSync : Screen
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

    /** On-device index: hashes, sharpness, GPS, labels and text. */
    val index = com.sift.photos.data.MediaIndex(app)
    private var indexJob: kotlinx.coroutines.Job? = null
    private var cacheLoaded = false
    var cleanup by mutableStateOf<List<CleanupGroup>?>(null)
        private set
    val importer = com.sift.photos.data.TakeoutImporter(app)
    private var importJob: kotlinx.coroutines.Job? = null
    var onboardingStep by mutableStateOf(0)

    fun startImport(uris: List<android.net.Uri>, skipExisting: Boolean) {
        importJob?.cancel()
        importJob = viewModelScope.launch { importer.import(uris, skipExisting); refresh() }
    }

    fun pauseImport() { importJob?.cancel() }

    private var observer: ContentObserver? = null


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

    private var refreshJob: kotlinx.coroutines.Job? = null

    /**
     * Reloads the library. Debounced, because MediaStore fires a change for every file during imports
     * and edits; and guarded, so a provider or permission error leaves the last good state in place.
     */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            kotlinx.coroutines.delay(250)
            access = runCatching { repo.access() }.getOrDefault(MediaAccess.None)
            loading = true
            try {
                val items = runCatching { repo.load() }.getOrNull()
                if (items != null) {
                    deviceItems = items
                    deviceSections = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { group(items) }
                    deviceAlbums = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { bucketAlbums(items) }
                }
                // MediaStore now reflects confirmed favorites; drop the session overrides for real items.
                favorites.keys.filter { it.startsWith("m") }.forEach { favorites.remove(it) }
                deviceTrash = runCatching { repo.load(trashed = true) }.getOrDefault(deviceTrash)
                storage = runCatching { repo.storage(deviceItems) }.getOrDefault(storage)
            } finally {
                loading = false
            }
            startIndexing()
        }
    }

    var deviceSections by mutableStateOf<List<DaySection>>(emptyList())
        private set
    var deviceAlbums by mutableStateOf<List<Album>>(emptyList())
        private set

    /** The previous run's crash report, if Sift closed unexpectedly. Stays on this phone. */
    var lastCrash by mutableStateOf<String?>(
        runCatching { com.sift.photos.SiftApplication.crashFile(app).takeIf { it.exists() }?.readText() }.getOrNull()
    )
        private set

    fun dismissCrash() {
        runCatching { com.sift.photos.SiftApplication.crashFile(getApplication()).delete() }
        lastCrash = null
    }

    fun startIndexing() {
        if (access == MediaAccess.None || !ai) return
        indexJob?.cancel()
        indexJob = viewModelScope.launch {
            if (!cacheLoaded) { index.loadCached(); cacheLoaded = true }
            val read = settings.feature(com.sift.photos.data.SmartFeature.Search) || settings.feature(com.sift.photos.data.SmartFeature.Documents)
            index.run(deviceItems, read)
        }
    }

    fun stopIndexing() { indexJob?.cancel() }

    fun entry(item: PhotoItem): com.sift.photos.data.IndexEntry? =
        (item.ref as? PhotoRef.Media)?.let { index.entries[it.mediaId] }

    fun mediaId(item: PhotoItem): Long = (item.ref as? PhotoRef.Media)?.mediaId ?: -1

    /** Receipts, tickets and cards found in the real library that you haven't acted on or dismissed. */
    val foundDocs: List<Pair<PhotoItem, com.sift.photos.data.DocParser.Doc>> by androidx.compose.runtime.derivedStateOf { computeFoundDocs() }

    private fun computeFoundDocs(): List<Pair<PhotoItem, com.sift.photos.data.DocParser.Doc>> =
        if (!settings.feature(com.sift.photos.data.SmartFeature.Documents)) emptyList() else deviceItems.mapNotNull { i ->
            val d = entry(i)?.doc ?: return@mapNotNull null
            if (d.confidence < 0.55 || index.decided(mediaId(i), "doc")) null else i to d
        }

    val doneDocs: List<Pair<PhotoItem, com.sift.photos.data.DocParser.Doc>>
        get() = deviceItems.mapNotNull { i ->
            val d = entry(i)?.doc ?: return@mapNotNull null
            if (index.decided(mediaId(i), "done")) i to d else null
        }

    /** Real cleanup groups. Favorites and anything edited in the last 30 days are never suggested. */
    suspend fun computeCleanup() {
        // Heavy work off the main thread; a read error leaves the previous result instead of crashing.
        val groups = runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { buildCleanup() }
        }.getOrNull() ?: return
        cleanup = groups
    }

    private suspend fun buildCleanup(): List<CleanupGroup> {
        val recent = System.currentTimeMillis() / 1000 - 30L * 86_400
        val candidates = deviceItems.filter { !it.favorite && (it.ref as? PhotoRef.Media)?.isVideo == false && it.modifiedSec < recent }
        val groups = ArrayList<CleanupGroup>()
        val exact = index.exactDuplicates(candidates).map { set -> set.sortedBy { it.dateMillis } }
        if (exact.isNotEmpty()) groups += CleanupGroup(
            "dupes", "content_copy", "Duplicates", "%,d exact copies".format(exact.sumOf { it.size - 1 }),
            "byte-identical files. The oldest copy of each is kept.", 1.0, exact,
            exact.flatMap { it.drop(1) }.map { it.key }.toSet(),
        )
        val inExact = exact.flatten().map { it.key }.toSet()
        val shots = candidates.filter { it.key !in inExact }.mapNotNull { i ->
            entry(i)?.let { com.sift.photos.data.Analysis.Shot(mediaId(i), i.dateMillis, it.dHash, it.sharpness, i.sizeBytes) }
        }
        val byId = candidates.associateBy { mediaId(it) }
        val near = com.sift.photos.data.Analysis.nearDuplicateSets(shots).map { set -> set.sortedByDescending { it.sharp }.mapNotNull { byId[it.id] } }
        if (near.isNotEmpty()) groups += CleanupGroup(
            "near", "burst_mode", "Near-duplicates", "%,d sets · %,d photos".format(near.size, near.sumOf { it.size }),
            "same scene within 10 s. The sharpest of each set is suggested to keep.", 0.88, near,
            near.flatMap { it.drop(1) }.map { it.key }.toSet(),
        )
        val inNear = near.flatten().map { it.key }.toSet()
        val blurry = candidates.filter { it.key !in inExact && it.key !in inNear && it.docIcon == null }.mapNotNull { i ->
            entry(i)?.let { e -> com.sift.photos.data.Analysis.blurConfidence(e.sharpness).takeIf { it >= 0.6 }?.let { i to it } }
        }
        if (blurry.isNotEmpty()) groups += CleanupGroup(
            "blurry", "blur_on", "Blurry", "%,d photos".format(blurry.size),
            "motion or focus blur measured on this phone. Ones below 0.70 are left for you.", blurry.map { it.second }.average(),
            listOf(blurry.map { it.first }), blurry.filter { it.second >= 0.7 }.map { it.first.key }.toSet(),
        )
        val cutoff = System.currentTimeMillis() - 30L * 86_400_000
        val docIds = foundDocs.map { it.first.key }.toSet()
        val screenshots = deviceItems.filter { it.docIcon == "screenshot" && it.dateMillis < cutoff && !it.favorite && it.key !in docIds }
        if (screenshots.isNotEmpty()) groups += CleanupGroup(
            "shots", "screenshot", "Screenshots", "%,d older than 30 days".format(screenshots.size),
            "older than 30 days. Ones with tickets or receipts are excluded.", 0.95, listOf(screenshots), screenshots.map { it.key }.toSet(),
        )
        return groups
    }

    /** Text and object search over the index, plus dates, places, albums and file names. */
    fun search(q: String): List<Pair<PhotoItem, Double>> {
        val words = q.lowercase(Locale.getDefault()).split(' ', ',').filter { it.length > 1 && it !in stop }
        if (words.isEmpty()) return emptyList()
        return libraryItems.mapNotNull { item ->
            val e = entry(item)
            val cal = Calendar.getInstance().apply { timeInMillis = item.dateMillis }
            val month = java.text.DateFormatSymbols(Locale.getDefault()).months[cal.get(Calendar.MONTH)]
            val meta = listOfNotNull(item.name, item.bucket, item.place, month, cal.get(Calendar.YEAR).toString(),
                if (item.duration != null) "video videos" else "photo photos",
                if (item.docIcon == "screenshot") "screenshot screenshots" else null).joinToString(" ").lowercase()
            val docKind = e?.doc?.kind?.name?.lowercase()?.let { "$it ${it}s" } ?: ""
            val text = (e?.text ?: "").lowercase()
            var score = 0.0
            for (w in words) {
                val label = e?.labels?.firstOrNull { it.first.lowercase().startsWith(w.trimEnd('s')) }
                score += when {
                    meta.contains(w) || docKind.contains(w) -> 1.0
                    label != null -> label.second.toDouble()
                    text.contains(w) -> 0.9
                    else -> return@mapNotNull null
                }
            }
            item to (score / words.size)
        }.sortedByDescending { it.second }
    }

    private val stop = setOf("from", "in", "the", "at", "of", "my", "with", "and", "a", "on")

    /** Trip albums: runs of photos taken far from home. */
    val trips: List<Album> by androidx.compose.runtime.derivedStateOf { computeTrips() }

    private fun computeTrips(): List<Album> {
        if (!settings.feature(com.sift.photos.data.SmartFeature.Trips)) return emptyList()
        val located = deviceItems.mapNotNull { i -> entry(i)?.let { e -> if (e.lat != null && e.lon != null) com.sift.photos.data.Analysis.Located(mediaId(i), i.dateMillis, e.lat, e.lon) else null } }
        val byId = deviceItems.associateBy { mediaId(it) }
        val fmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        return com.sift.photos.data.Analysis.trips(located).filter { !index.decided(it.start, "trip") }.map { t ->
            val items = t.ids.mapNotNull { byId[it] }.sortedByDescending { it.dateMillis }
            Album("trip:${t.start}", "Trip · ${fmt.format(Date(t.start))}", "%,d photos · %d km from home".format(items.size, t.km),
                items.firstOrNull()?.ref, "luggage", items)
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
        } else if (favorites.isEmpty()) deviceSections
        else deviceSections.map { s -> s.copy(items = s.items.map { if (it.key in favorites) it.copy(favorite = isFavorite(it)) else it }) }

    val libraryItems: List<PhotoItem> get() = sections.flatMap { it.items }

    val albums: List<Album>
        get() = if (sample) SampleData.albums else deviceAlbums

    private fun bucketAlbums(items: List<PhotoItem>): List<Album> = items
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

    data class CleanupGroup(
        val key: String, val icon: String, val name: String, val count: String, val why: String, val confidence: Double,
        val sets: List<List<PhotoItem>>, val suggested: Set<String>,
    ) {
        val bytes: Long get() = sets.flatten().filter { it.key in suggested }.sumOf { it.sizeBytes }
    }

    fun toggleSampleFavorite(item: PhotoItem) {
        favorites[item.key] = !isFavorite(item)
    }

    // Last in the class body so every property above is initialized before the first refresh.
    init {
        observer = repo.observe { refresh() }
        refresh()
    }
}
