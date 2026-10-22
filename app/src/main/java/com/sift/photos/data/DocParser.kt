package com.sift.photos.data

import java.util.Calendar

/**
 * Reads recognized text and decides whether a photo is a receipt, a ticket or a business card.
 * Every result carries the fields it found, a confidence and a plain-language reason, so the UI can
 * show why it's suggesting something. Pure Kotlin so it is unit-tested without a device.
 */
object DocParser {

    enum class Kind(val label: String, val icon: String) {
        Receipt("Receipt → expense", "receipt_long"),
        Ticket("Ticket → calendar", "confirmation_number"),
        Card("Card → contact", "contact_page"),
    }

    data class Doc(
        val kind: Kind,
        val title: String,
        val fields: List<Pair<String, String>>,
        val confidence: Double,
        val why: String,
        /** Fields the parser could not read reliably; the UI asks about these instead of guessing. */
        val unsure: List<String> = emptyList(),
        val dateMillis: Long? = null,
        val email: List<String> = emptyList(),
        val phone: String? = null,
        val name: String? = null,
        val total: String? = null,
    ) {
        fun field(k: String) = fields.firstOrNull { it.first == k }?.second
    }

    private val amount = Regex("""(?<![\d.,])(\d{1,5}(?:[.,]\d{3})*[.,]\d{2})(?![\d])""")
    private val currency = Regex("""€|EUR|\$|USD|£|GBP|CHF|SEK|kr""")
    private val totalWords = Regex("""\b(total|totale|gesamt|summe|montant|importe|a pagar|to pay|amount due)\b""", RegexOption.IGNORE_CASE)
    private val vatWords = Regex("""\b(vat|iva|tva|mwst|ust|gst|nif|nipc)\b""", RegexOption.IGNORE_CASE)
    private val ticketWords = Regex("""\b(boarding|gate|seat|row|flight|ticket|admit|doors|platform|carriage|coach|bilhete|billet|entrada|plateia|section|sector|venue)\b""", RegexOption.IGNORE_CASE)
    private val email = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val phone = Regex("""(?:\+\d{1,3}[\s.-]?)?(?:\(?\d{2,4}\)?[\s.-]?){2,4}\d{2,4}""")
    private val time = Regex("""\b([01]?\d|2[0-3])[:h.]([0-5]\d)\b""")
    private val dmy = Regex("""\b(\d{1,2})[./-](\d{1,2})[./-](\d{2,4})\b""")
    private val ymd = Regex("""\b(20\d{2})-(\d{2})-(\d{2})\b""")
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val monthAlt = mapOf("fev" to 1, "abr" to 3, "mai" to 4, "ago" to 7, "set" to 8, "out" to 9, "dez" to 11, "mär" to 2, "okt" to 9, "dic" to 11)
    private val textDate = Regex("""\b(\d{1,2})\s+([A-Za-zä]{3,9})\.?\s+(20\d{2})\b""")

    fun parse(text: String): Doc? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return null
        return listOfNotNull(receipt(lines, text), ticket(lines, text), card(lines, text)).maxByOrNull { it.confidence }
    }

    private fun parseAmount(s: String): Double? {
        val t = s.replace(" ", "")
        val dec = t.last { it == '.' || it == ',' }
        val normalized = t.substring(0, t.lastIndexOf(dec)).replace(".", "").replace(",", "") + "." + t.substringAfterLast(dec)
        return normalized.toDoubleOrNull()
    }

    fun findDate(text: String): Long? {
        val cal = Calendar.getInstance().apply { clear() }
        ymd.find(text)?.let { m ->
            val (y, mo, d) = m.destructured
            if (mo.toInt() in 1..12 && d.toInt() in 1..31) { cal.set(y.toInt(), mo.toInt() - 1, d.toInt(), 12, 0); return cal.timeInMillis }
        }
        dmy.find(text)?.let { m ->
            val (d, mo, y) = m.destructured
            val year = y.toInt().let { if (it < 100) 2000 + it else it }
            if (mo.toInt() in 1..12 && d.toInt() in 1..31 && year in 2000..2100) { cal.set(year, mo.toInt() - 1, d.toInt(), 12, 0); return cal.timeInMillis }
        }
        textDate.find(text)?.let { m ->
            val (d, mon, y) = m.destructured
            val key = mon.lowercase().take(3)
            val mi = months.indexOf(key).takeIf { it >= 0 } ?: monthAlt[key] ?: return@let
            if (d.toInt() in 1..31) { cal.set(y.toInt(), mi, d.toInt(), 12, 0); return cal.timeInMillis }
        }
        return null
    }

    private fun firstWordyLine(lines: List<String>): String? =
        lines.take(4).firstOrNull { l -> l.count { it.isLetter() } >= 3 && l.length <= 40 && !email.containsMatchIn(l) }

    private fun receipt(lines: List<String>, text: String): Doc? {
        val amounts = lines.flatMap { l -> amount.findAll(l).mapNotNull { m -> parseAmount(m.value)?.let { l to it } } }
        if (amounts.isEmpty()) return null
        val totalLine = lines.indexOfLast { totalWords.containsMatchIn(it) && !it.contains("sub", true) }
        val totalAmount = if (totalLine >= 0) {
            // The amount may sit on the total line or on the line right after it.
            (amount.find(lines[totalLine]) ?: lines.getOrNull(totalLine + 1)?.let { amount.find(it) })?.value?.let(::parseAmount)
        } else null
        val total = totalAmount ?: amounts.maxOf { it.second }
        val hasCurrency = currency.containsMatchIn(text)
        val hasVat = vatWords.containsMatchIn(text)
        if (totalLine < 0 && !hasVat) return null
        val merchant = firstWordyLine(lines)
        val date = findDate(text)
        val cur = when {
            text.contains("€") || text.contains("EUR") -> "€"
            text.contains("£") -> "£"
            text.contains("$") -> "$"
            else -> ""
        }
        val totalText = cur + "%.2f".format(total)
        var conf = 0.55
        if (totalAmount != null) conf += 0.15
        if (hasVat) conf += 0.1
        if (date != null) conf += 0.07
        if (merchant != null) conf += 0.05
        if (hasCurrency) conf += 0.05
        val found = listOfNotNull("total".takeIf { totalAmount != null }, "VAT".takeIf { hasVat }, "date".takeIf { date != null }, "merchant".takeIf { merchant != null })
        return Doc(
            kind = Kind.Receipt,
            title = listOfNotNull(merchant, totalText).joinToString(" · "),
            fields = listOfNotNull(
                merchant?.let { "Merchant" to it },
                date?.let { "Date" to formatDay(it) },
                "Total" to totalText,
            ),
            confidence = conf.coerceAtMost(0.98),
            why = "printed ${found.joinToString(", ").ifEmpty { "amounts" }} read on this phone.",
            unsure = listOfNotNull("Date".takeIf { date == null }, "Merchant".takeIf { merchant == null }),
            dateMillis = date,
            total = totalText,
        )
    }

    private fun ticket(lines: List<String>, text: String): Doc? {
        val hits = ticketWords.findAll(text).map { it.value.lowercase() }.toSet()
        if (hits.size < 2) return null
        val date = findDate(text)
        val t = time.find(text)
        val title = lines.firstOrNull { l -> l.contains("flight", true) }
            ?: lines.firstOrNull { l -> l.count { it.isLetter() } >= 4 && !ticketWords.matches(l) && l.length <= 40 }
            ?: "Event"
        val seat = lines.firstOrNull { it.contains("seat", true) || it.contains("row", true) || it.contains("gate", true) }
        var conf = 0.5 + 0.06 * hits.size.coerceAtMost(4)
        if (date != null) conf += 0.12
        if (t != null) conf += 0.08
        val start = date?.let { d ->
            Calendar.getInstance().apply {
                timeInMillis = d
                if (t != null) { set(Calendar.HOUR_OF_DAY, t.groupValues[1].toInt()); set(Calendar.MINUTE, t.groupValues[2].toInt()) }
            }.timeInMillis
        }
        return Doc(
            kind = Kind.Ticket,
            title = title,
            fields = listOfNotNull(
                "Event" to title,
                start?.let { "When" to formatDay(it) + (t?.let { m -> " · %02d:%s".format(m.groupValues[1].toInt(), m.groupValues[2]) } ?: "") },
                seat?.let { "Seat" to it },
            ),
            confidence = conf.coerceAtMost(0.97),
            why = "ticket layout with ${hits.take(3).joinToString(", ")}" + (if (date != null) " and a date" else "") + ".",
            unsure = listOfNotNull("Date".takeIf { date == null }, "Start time".takeIf { t == null }, "End time"),
            dateMillis = start,
        )
    }

    private fun card(lines: List<String>, text: String): Doc? {
        val emails = email.findAll(text).map { it.value }.distinct().toList()
        val phoneMatch = lines.asSequence().filter { l -> !email.containsMatchIn(l) }
            .mapNotNull { l -> phone.find(l)?.value?.trim()?.takeIf { p -> p.count(Char::isDigit) in 7..15 } }.firstOrNull()
        if (emails.isEmpty() && phoneMatch == null) return null
        if (lines.size > 16) return null
        if (totalWords.containsMatchIn(text)) return null
        val name = lines.firstOrNull { l ->
            val words = l.split(" ").filter { it.isNotEmpty() }
            words.size in 2..3 && words.all { w -> w.first().isUpperCase() && w.all { it.isLetter() || it == '-' || it == '.' } }
        }
        var conf = 0.45
        if (emails.isNotEmpty()) conf += 0.15
        if (phoneMatch != null) conf += 0.12
        if (name != null) conf += 0.13
        if (emails.size > 1) conf -= 0.08
        return Doc(
            kind = Kind.Card,
            title = "Save contact: " + (name ?: emails.firstOrNull() ?: phoneMatch ?: "Unknown"),
            fields = listOfNotNull(
                name?.let { "Name" to it },
                phoneMatch?.let { "Phone" to it },
                emails.singleOrNull()?.let { "Email" to it },
            ),
            confidence = conf.coerceIn(0.3, 0.95),
            why = "card layout with " + listOfNotNull("a name".takeIf { name != null }, "a phone number".takeIf { phoneMatch != null }, "an email".takeIf { emails.isNotEmpty() }).joinToString(", ") + ".",
            unsure = listOfNotNull("Name".takeIf { name == null }, "Email".takeIf { emails.size > 1 }),
            email = emails,
            phone = phoneMatch,
            name = name,
        )
    }

    fun formatDay(millis: Long): String =
        java.text.SimpleDateFormat("EEE d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(millis))
}
