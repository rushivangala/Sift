package com.sift.photos.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.provider.MediaStore
import android.util.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** What Sift learned about one photo, on this phone. */
data class IndexEntry(
    val id: Long,
    val modified: Long,
    val dHash: Long,
    val sharpness: Double,
    val luma: Double,
    val labels: List<Pair<String, Float>>,
    val text: String,
    val lat: Double?,
    val lon: Double?,
    val read: Boolean,
) {
    val doc: DocParser.Doc? by lazy { if (text.isBlank()) null else DocParser.parse(text) }
}

data class IndexProgress(val done: Int = 0, val total: Int = 0, val running: Boolean = false, val stage: String = "")

/** Local SQLite store. It lives in app-private storage and is deleted with "Delete AI data". */
private class IndexDb(context: Context) : SQLiteOpenHelper(context, "sift_index.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE media (id INTEGER PRIMARY KEY, modified INTEGER, dhash INTEGER, sharp REAL, luma REAL, labels TEXT, text TEXT, lat REAL, lon REAL, read INTEGER)")
        db.execSQL("CREATE TABLE decisions (id INTEGER, what TEXT, PRIMARY KEY (id, what))")
        db.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY, merchant TEXT, total TEXT, date INTEGER, added INTEGER)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS media"); db.execSQL("DROP TABLE IF EXISTS decisions"); db.execSQL("DROP TABLE IF EXISTS expenses")
        onCreate(db)
    }
}

data class Expense(val id: Long, val merchant: String, val total: String, val date: Long?, val added: Long)

class MediaIndex(private val context: Context) {
    private val db = IndexDb(context)
    private val resolver get() = context.contentResolver

    var entries by mutableStateOf<Map<Long, IndexEntry>>(emptyMap())
        private set
    var progress by mutableStateOf(IndexProgress())
        private set
    var decisions by mutableStateOf<Set<String>>(emptySet())
        private set
    var expenses by mutableStateOf<List<Expense>>(emptyList())
        private set

    private val labeler by lazy { ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.6f).build()) }
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun loadCached() = withContext(Dispatchers.IO) {
        val map = HashMap<Long, IndexEntry>()
        db.readableDatabase.rawQuery("SELECT id, modified, dhash, sharp, luma, labels, text, lat, lon, read FROM media", null).use { c ->
            while (c.moveToNext()) {
                val e = IndexEntry(
                    id = c.getLong(0), modified = c.getLong(1), dHash = c.getLong(2), sharpness = c.getDouble(3), luma = c.getDouble(4),
                    labels = decodeLabels(c.getString(5)), text = c.getString(6) ?: "",
                    lat = if (c.isNull(7)) null else c.getDouble(7), lon = if (c.isNull(8)) null else c.getDouble(8),
                    read = c.getInt(9) == 1,
                )
                map[e.id] = e
            }
        }
        val d = HashSet<String>()
        db.readableDatabase.rawQuery("SELECT id, what FROM decisions", null).use { c -> while (c.moveToNext()) d += "${c.getLong(0)}:${c.getString(1)}" }
        val list = readExpenses()
        onMain { entries = map; decisions = d; expenses = list }
    }

    /** Compose state is only written on the main thread, so composition never sees a half-applied change. */
    private suspend fun onMain(block: () -> Unit) = withContext(Dispatchers.Main) { block() }

    private fun loadExpenses() { expenses = readExpenses() }

    private fun readExpenses(): List<Expense> {
        val list = ArrayList<Expense>()
        db.readableDatabase.rawQuery("SELECT id, merchant, total, date, added FROM expenses ORDER BY added DESC", null).use { c ->
            while (c.moveToNext()) list += Expense(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", if (c.isNull(3)) null else c.getLong(3), c.getLong(4))
        }
        return list
    }

    /**
     * Indexes photos that are new or changed, newest first. [read] runs text recognition and labels
     * (the "Search", "Receipts and documents" features); hashes, sharpness and GPS always run.
     */
    suspend fun run(items: List<PhotoItem>, read: Boolean, onBatch: () -> Unit = {}) = withContext(Dispatchers.Default) {
        val photos = items.filter { (it.ref as? PhotoRef.Media)?.isVideo == false }
        val todo = photos.filter { p ->
            val id = (p.ref as PhotoRef.Media).mediaId
            val e = entries[id]
            e == null || e.modified != p.modifiedSec || (read && !e.read)
        }
        if (todo.isEmpty()) { onMain { progress = IndexProgress(photos.size, photos.size, false) }; return@withContext }
        var done = photos.size - todo.size
        onMain { progress = IndexProgress(done, photos.size, true, if (read) "Reading text and objects" else "Indexing") }
        val batch = HashMap<Long, IndexEntry>()
        for (p in todo) {
            ensureActive()
            val ref = p.ref as PhotoRef.Media
            val entry = runCatching { analyze(p, ref, read) }.getOrNull()
            if (entry != null) { batch[entry.id] = entry; write(entry) }
            done++
            if (batch.size >= 20) {
                val snapshot = HashMap(batch); batch.clear()
                val d = done
                onMain { entries = entries + snapshot; progress = progress.copy(done = d); onBatch() }
            }
        }
        val rest = HashMap(batch)
        val d = done
        onMain { entries = entries + rest; progress = IndexProgress(d, photos.size, false); onBatch() }
    }

    private suspend fun analyze(p: PhotoItem, ref: PhotoRef.Media, read: Boolean): IndexEntry {
        val small = softBitmap(resolver.loadThumbnail(ref.uri, Size(256, 256), null))
        val px = IntArray(small.width * small.height).also { small.getPixels(it, 0, small.width, 0, 0, small.width, small.height) }
        val hash = Analysis.dHash(px, small.width, small.height)
        val sharp = Analysis.sharpness(px, small.width, small.height)
        val luma = Analysis.meanLuma(px)
        var labels = emptyList<Pair<String, Float>>()
        var text = ""
        if (read) {
            labels = runCatching { labeler.process(InputImage.fromBitmap(small, 0)).await().map { it.text to it.confidence } }.getOrDefault(emptyList())
            val big = runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, ref.uri)) { d, info, _ ->
                    val s = maxOf(1f, maxOf(info.size.width, info.size.height) / 1280f)
                    d.setTargetSize((info.size.width / s).toInt(), (info.size.height / s).toInt())
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }.getOrNull()
            if (big != null) text = runCatching { recognizer.process(InputImage.fromBitmap(big, 0)).await().text }.getOrDefault("")
        }
        val ll = runCatching {
            resolver.openInputStream(MediaStore.setRequireOriginal(ref.uri))?.use { s ->
                val out = FloatArray(2)
                if (ExifInterface(s).getLatLong(out) && (out[0] != 0f || out[1] != 0f)) out else null
            }
        }.getOrNull()
        return IndexEntry(ref.mediaId, p.modifiedSec, hash, sharp, luma, labels, text, ll?.get(0)?.toDouble(), ll?.get(1)?.toDouble(), read)
    }

    private fun softBitmap(b: Bitmap): Bitmap = if (b.config == Bitmap.Config.HARDWARE) b.copy(Bitmap.Config.ARGB_8888, false) else b

    private fun write(e: IndexEntry) {
        db.writableDatabase.insertWithOnConflict("media", null, ContentValues().apply {
            put("id", e.id); put("modified", e.modified); put("dhash", e.dHash); put("sharp", e.sharpness); put("luma", e.luma)
            put("labels", e.labels.joinToString(";") { "${it.first}:${it.second}" }); put("text", e.text)
            put("lat", e.lat); put("lon", e.lon); put("read", if (e.read) 1 else 0)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun decodeLabels(s: String?): List<Pair<String, Float>> =
        s.orEmpty().split(";").mapNotNull { part -> part.substringBeforeLast(":").takeIf { it.isNotEmpty() }?.let { it to (part.substringAfterLast(":").toFloatOrNull() ?: 0f) } }

    // ---- Decisions the user made (dismissed suggestions, kept photos) ----------------------

    fun decided(id: Long, what: String) = "$id:$what" in decisions

    fun decide(id: Long, what: String, on: Boolean = true) {
        if (on) {
            db.writableDatabase.insertWithOnConflict("decisions", null, ContentValues().apply { put("id", id); put("what", what) }, SQLiteDatabase.CONFLICT_IGNORE)
            decisions = decisions + "$id:$what"
        } else {
            db.writableDatabase.delete("decisions", "id = ? AND what = ?", arrayOf(id.toString(), what))
            decisions = decisions - "$id:$what"
        }
    }

    fun addExpense(id: Long, merchant: String, total: String, date: Long?) {
        db.writableDatabase.insertWithOnConflict("expenses", null, ContentValues().apply {
            put("id", id); put("merchant", merchant); put("total", total); put("date", date); put("added", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
        loadExpenses()
    }

    fun removeExpense(id: Long) {
        db.writableDatabase.delete("expenses", "id = ?", arrayOf(id.toString()))
        loadExpenses()
    }

    /** Removes faces, text, labels and decisions. Photos are untouched. */
    fun deleteAll() {
        db.writableDatabase.apply { delete("media", null, null); delete("decisions", null, null) }
        entries = emptyMap(); decisions = emptySet()
    }

    /** Byte-identical files: same size first, then a SHA-256 over the content. */
    suspend fun exactDuplicates(items: List<PhotoItem>): List<List<PhotoItem>> = withContext(Dispatchers.IO) {
        items.filter { it.ref is PhotoRef.Media && it.sizeBytes > 0 }.groupBy { it.sizeBytes }.values.filter { it.size > 1 }
            .flatMap { same ->
                same.groupBy { item ->
                    runCatching {
                        resolver.openInputStream((item.ref as PhotoRef.Media).uri)?.use { s ->
                            val md = MessageDigest.getInstance("SHA-256")
                            val buf = ByteArray(64 * 1024)
                            while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) }
                            md.digest().joinToString("") { "%02x".format(it) }
                        }
                    }.getOrNull() ?: item.key
                }.values.filter { it.size > 1 }
            }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
    addOnCanceledListener { c.cancel() }
}
