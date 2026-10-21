package com.sift.photos.data

import java.util.Calendar

/**
 * The sample library: the realistic content from the design files (Lisbon, October 2026).
 * Shown when "Sample library" is on, so every flow can be explored without real photos.
 * Photos are color placeholders, exactly as in the designs.
 */
object SampleData {

    private fun date(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Long =
        Calendar.getInstance().apply { set(y, m - 1, d, h, min, 0) }.timeInMillis

    private var n = 0
    private fun art(a: PhotoArt, day: Long, name: String? = null, duration: String? = null, doc: String? = null,
                    cloud: Boolean = false, fav: Boolean = false, place: String? = null, size: Long = 3_200_000) =
        PhotoItem(
            key = "s${n++}", ref = PhotoRef.Art(a), dateMillis = day,
            name = name ?: "IMG_${2100 + n}.HEIC", duration = duration, docIcon = doc, cloud = cloud,
            favorite = fav, sizeBytes = size, width = 4080, height = 3072, place = place,
        )

    val heroPhoto = art(PhotoArt.Sea, date(2026, 10, 4, 18, 52), name = "IMG_3318.HEIC", place = "Cais do Sodré, Lisbon")

    val sections: List<DaySection> = listOf(
        DaySection("Today", "Mon 19 Oct · Lisbon", listOf(
            art(PhotoArt.Tram, date(2026, 10, 19, 10, 2), place = "Lisbon"),
            art(PhotoArt.Sky, date(2026, 10, 19, 9, 40), name = "VID_0412.MP4", duration = "0:14", place = "Lisbon"),
            art(PhotoArt.Paper, date(2026, 10, 19, 9, 12), doc = "receipt_long", place = "Lisbon"),
            art(PhotoArt.Tile, date(2026, 10, 19, 8, 55), cloud = true, place = "Lisbon"),
            art(PhotoArt.Rose, date(2026, 10, 19, 8, 30), fav = true, place = "Lisbon"),
            art(PhotoArt.Shot, date(2026, 10, 19, 8, 1), name = "Screenshot_20261019.png", doc = "screenshot"),
        )),
        DaySection("Yesterday", "Sun 18 Oct · Sintra", listOf(
            art(PhotoArt.Forest, date(2026, 10, 18, 16, 2), place = "Sintra"),
            art(PhotoArt.Fog, date(2026, 10, 18, 15, 40), place = "Sintra"),
            art(PhotoArt.Moss, date(2026, 10, 18, 15, 12), name = "VID_0409.MP4", duration = "1:02", place = "Sintra"),
            art(PhotoArt.Gold, date(2026, 10, 18, 14, 50), place = "Sintra"),
            art(PhotoArt.Sand, date(2026, 10, 18, 13, 20), place = "Sintra"),
            art(PhotoArt.Wine, date(2026, 10, 18, 12, 5), cloud = true, place = "Sintra"),
        )),
        DaySection("Sat 17 Oct", "Cascais", List(8) { art(PhotoArt.cycle(it, 6), date(2026, 10, 17, 18 - it), place = "Cascais") }),
        DaySection("Fri 16 Oct", "", List(4) { art(PhotoArt.cycle(it, 2), date(2026, 10, 16, 18 - it)) }),
        DaySection("Sun 4 Oct", "Cais do Sodré", listOf(heroPhoto) + List(8) { art(PhotoArt.cycle(it, 3), date(2026, 10, 4, 18 - it), place = "Cais do Sodré, Lisbon") }),
    )

    val allItems: List<PhotoItem> get() = sections.flatMap { it.items }

    // ---- 01 Ask your photos -------------------------------------------------------------

    val askSuggestions = listOf("receipts from May", "Inês at the beach", "tickets this month")

    data class Suggestion(val icon: String, val text: String, val meta: String)

    val typeahead = listOf(
        Suggestion("receipt_long", "receipts from May", "14 photos · May 2026"),
        Suggestion("receipt_long", "receipts from Lisbon trip", "9 photos · 2–7 Oct 2026"),
        Suggestion("storefront", "receipts from Fnac", "3 photos · Fnac Chiado"),
        Suggestion("group", "receipts from Jonas", "5 photos · shared by Jonas Weber"),
        Suggestion("beach_access", "Inês at the beach", "38 photos · Cascais, Comporta"),
        Suggestion("confirmation_number", "tickets this month", "4 photos · Oct 2026"),
    )

    data class Receipt(val merchant: String, val detail: String, val total: String, val art: PhotoArt, val rotation: Float)

    val receipts = listOf(
        Receipt("Time Out Market Lisboa", "Sun 3 May · Cais do Sodré", "€38.50", PhotoArt.ReceiptDark, -4f),
        Receipt("Uber", "Sat 9 May · screenshot", "€14.20", PhotoArt.Shot, 0f),
        Receipt("Fnac Chiado", "Tue 12 May · Chiado", "€129.00", PhotoArt.ReceiptGrey, 3f),
        Receipt("Cervejaria Ramiro", "Fri 15 May · Anjos", "€86.40", PhotoArt.ReceiptBrown, -2f),
        Receipt("Continente Amoreiras", "Mon 18 May · Amoreiras", "€46.12", PhotoArt.ReceiptLight, 5f),
        Receipt("CP Comboios de Portugal", "Sun 24 May · Lisboa–Porto", "€24.85", PhotoArt.ReceiptDark, -3f),
    )

    // ---- 02 Smart cleanup -----------------------------------------------------------------

    data class CleanupGroup(
        val icon: String, val name: String, val count: String, val size: String,
        val why: String, val confidence: Double, val items: Int, val bytes: Long,
    )

    val cleanupGroups = listOf(
        CleanupGroup("content_copy", "Duplicates", "214 exact copies", "1.8 GB", "byte-identical files. One copy of each is kept.", 1.00, 214, 1_800_000_000),
        CleanupGroup("burst_mode", "Near-duplicates", "96 sets · 431 photos", "3.1 GB", "same scene within 10 s. One suggested to keep per set.", 0.91, 75, 1_500_000_000),
        CleanupGroup("blur_on", "Blurry", "61 photos", "240 MB", "motion or focus blur. 8 below 0.70 are left for you.", 0.82, 53, 210_000_000),
        CleanupGroup("screenshot", "Screenshots", "388 older than 30 days", "410 MB", "never reopened. Ones with tickets or receipts are excluded.", 0.95, 388, 410_000_000),
    )

    data class BurstSet(
        val title: String, val meta: String, val shots: List<PhotoArt>, val keep: Int?,
        val why: String, val confidence: Double,
    )

    val bursts = listOf(
        BurstSet("Miradouro da Graça", "Sun 4 Oct · 18:41 · 6 shots",
            listOf(PhotoArt.Burst1, PhotoArt.Burst2, PhotoArt.Burst3, PhotoArt.Burst4, PhotoArt.Burst1, PhotoArt.Burst2), keep = 0,
            why = "sharpest of the set, Inês's eyes open, horizon level. The other 5 are within 0.6 s.", confidence = 0.91),
        BurstSet("Jonas's birthday cake", "Sat 10 Oct · 21:07 · 3 shots",
            listOf(PhotoArt.Cake, PhotoArt.Cake, PhotoArt.Cake), keep = null,
            why = "all three are equally sharp and faces differ. Pick the one to keep, or keep all.", confidence = 0.64),
    )

    // ---- 03 Transparent AI edit --------------------------------------------------------

    data class EditStep(
        val name: String, val value: String, val why: String, val confidence: Double,
        val cloud: Boolean = false, val applied: Boolean = true, val generative: Boolean = false,
    )

    val edits = listOf(
        EditStep("Exposure", "+0.4 EV", "Face metered 1.3 stops under the sky.", 0.94),
        EditStep("White balance", "5200 → 5650 K", "Shade cast a blue tone on skin.", 0.90),
        EditStep("Highlights", "−22", "Sky clipping held back to 4%.", 0.93),
        EditStep("Straighten", "−1.2°", "Horizon tilted against the river wall.", 0.97),
        EditStep("Remove person, left edge", "not applied", "Partly cut off and may be someone you know. Would generate ~6% of the frame.", 0.64, cloud = true, applied = false, generative = true),
    )

    val c2pa = listOf(
        "Captured" to "Pixel 10 Pro · Sun 4 Oct 2026, 18:52",
        "Edited" to "Sift 1.0 on this phone · Mon 19 Oct, 10:41",
        "AI-assisted" to "Exposure, white balance, straighten",
        "Generated" to "None. Removal was not applied.",
        "Signed" to "Sift · C2PA 2.1 · valid",
    )

    val heroInfo = listOf(
        Triple("event", "Sun 4 Oct 2026 · 18:52", "GMT+1 · Lisbon"),
        Triple("photo_camera", "Pixel 10 Pro", "f/1.7 · 1/320 s · ISO 50 · 24 mm"),
        Triple("image", "IMG_3318.HEIC", "3.2 MB · 12.4 MP · 4080 × 3072"),
        Triple("location_on", "Cais das Colunas, Lisbon", "38.7066, −9.1363"),
        Triple("memory", "On this phone · copied to Home NAS", "original untouched · 1 edit"),
    )

    // ---- 04 Photos that act ------------------------------------------------------------

    val receiptFields = listOf("Merchant" to "Cervejaria Ramiro", "Date" to "Fri 15 May 2026", "Total" to "€86.40", "VAT 23%" to "€16.16")

    data class TicketField(val icon: String, val value: String, val detail: String)

    val ticketFields = listOf(
        TicketField("music_note", "Rodrigo Leão", "Coliseu dos Recreios"),
        TicketField("event", "Sat 7 Nov 2026 · 21:30", "Doors 20:30 · reminder 1 h before"),
        TicketField("location_on", "Rua das Portas de Santo Antão 96", "Lisboa"),
        TicketField("event_seat", "Plateia · Row F · Seat 12", "Personal calendar"),
    )

    val cardFields = listOf("Name" to "Mara Lindqvist", "Title" to "Head of Partnerships", "Company" to "Nordvik Studio", "Phone" to "+46 70 412 88 31")
    val emailOptions = listOf("mara.lindqvist@nordvik.se", "m.lindqvist@nordvik.se")
    val websiteOptions = listOf("nordvik.se", "nordvikstudio.se")

    // ---- 05 Storage freedom -------------------------------------------------------------

    data class Provider(val icon: String, val name: String, val detail: String, val cloud: Boolean)

    val providers = listOf(
        Provider("smartphone", "This phone only", "41.2 GB free · nothing leaves", false),
        Provider("dns", "Home NAS", "found on Wi‑Fi · Raman-NAS · SMB · 1.6 TB free", false),
        Provider("cloud", "Dropbox", "connected · 88 GB free", true),
        Provider("add_to_drive", "Google Drive", "sign in to connect", true),
        Provider("cloud_circle", "OneDrive", "sign in to connect", true),
        Provider("lan", "Nextcloud or WebDAV", "your server address", false),
        Provider("database", "S3-compatible", "Backblaze B2, Wasabi, MinIO", true),
    )

    val types = listOf(
        Triple("Photos", "148 GB · 21,882", 0.47f),
        Triple("Videos", "141 GB · 4,210", 0.45f),
        Triple("RAW", "18 GB · 612", 0.06f),
        Triple("Screenshots", "5 GB · 1,940", 0.02f),
    )

    // ---- Albums --------------------------------------------------------------------------

    val albums: List<Album> by lazy {
        listOf(
            Album("lisbon", "Lisbon, Oct 2026", "214 items", PhotoRef.Art(PhotoArt.Sea), "luggage",
                List(9) { art(PhotoArt.cycle(it, 1), date(2026, 10, 4, 18 - it), duration = if (it == 8) "0:14" else null, place = "Cais do Sodré, Lisbon") }),
            Album("family", "Family", "3,420 items · shared", PhotoRef.Art(PhotoArt.Rose), "group",
                List(12) { art(PhotoArt.cycle(it, 2), date(2026, 9, 20, 18 - it)) }),
            Album("receipts", "Receipts", "128 items · auto", PhotoRef.Art(PhotoArt.Paper), "memory",
                receipts.map { art(it.art, date(2026, 5, 15), doc = "receipt_long") }),
            Album("porto", "Porto weekend", "96 items", PhotoRef.Art(PhotoArt.Tile), null,
                List(9) { art(PhotoArt.cycle(it, 4), date(2026, 9, 12, 18 - it)) }),
        )
    }

    val lockedItems: List<PhotoItem> by lazy {
        List(8) { art(PhotoArt.cycle(it, 9), date(2026, 8, 2, 18 - it)) } +
            art(PhotoArt.Night, date(2026, 8, 1), duration = "0:41")
    }

    data class TrashRow(val name: String, val sub: String, val art: PhotoArt, val left: String, val urgent: Boolean)

    val trash = listOf(
        TrashRow("IMG_2022.HEIC", "Lisbon · deleted 22 Sep", PhotoArt.Sea, "3 d", true),
        TrashRow("VID_0381.MP4", "0:14 · deleted 27 Sep", PhotoArt.Night, "8 d", false),
        TrashRow("Screenshot_20260930", "Deleted 30 Sep", PhotoArt.Shot, "11 d", false),
        TrashRow("IMG_1987.HEIC", "Duplicate · deleted 7 Oct", PhotoArt.Tram, "18 d", false),
        TrashRow("IMG_1950.HEIC", "Deleted 12 Oct", PhotoArt.Cake, "23 d", false),
        TrashRow("IMG_1902.HEIC", "Deleted 17 Oct", PhotoArt.Forest, "28 d", false),
    )
}
