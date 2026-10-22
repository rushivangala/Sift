package com.sift.photos.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisTest {

    private fun image(w: Int, h: Int, f: (Int, Int) -> Int) = IntArray(w * h) { f(it % w, it / w) or 0xFF000000.toInt() }

    @Test
    fun dHashIsStableAcrossBrightnessAndDiffersAcrossScenes() {
        val a = image(64, 48) { x, _ -> (x * 4).let { (it shl 16) or (it shl 8) or it } }
        val brighter = image(64, 48) { x, _ -> (x * 4 + 10).coerceAtMost(255).let { (it shl 16) or (it shl 8) or it } }
        val other = image(64, 48) { x, _ -> (255 - x * 4).let { (it shl 16) or (it shl 8) or it } }
        val ha = Analysis.dHash(a, 64, 48)
        assertTrue(Analysis.hamming(ha, Analysis.dHash(brighter, 64, 48)) <= 4)
        assertTrue(Analysis.hamming(ha, Analysis.dHash(other, 64, 48)) > 20)
    }

    @Test
    fun sharpEdgesScoreHigherThanSoftGradient() {
        val checker = image(64, 64) { x, y -> if ((x / 4 + y / 4) % 2 == 0) 0xFFFFFF else 0 }
        val soft = image(64, 64) { x, _ -> (x * 2).let { (it shl 16) or (it shl 8) or it } }
        assertTrue(Analysis.sharpness(checker, 64, 64) > 1000)
        assertTrue(Analysis.sharpness(soft, 64, 64) < 5)
        assertEquals(0.95, Analysis.blurConfidence(1.0), 0.001)
        assertEquals(0.0, Analysis.blurConfidence(500.0), 0.001)
    }

    @Test
    fun nearDuplicatesGroupOnlyCloseInTimeAndLooks() {
        val shots = listOf(
            Analysis.Shot(1, 0, 0L, 50.0, 1),
            Analysis.Shot(2, 2_000, 1L, 80.0, 1),
            Analysis.Shot(3, 4_000, 3L, 30.0, 1),
            Analysis.Shot(4, 60_000, 3L, 30.0, 1),
            Analysis.Shot(5, 61_000, -1L, 30.0, 1),
        )
        val sets = Analysis.nearDuplicateSets(shots)
        assertEquals(1, sets.size)
        assertEquals(listOf(1L, 2L, 3L), sets[0].map { it.id })
    }

    @Test
    fun tripsAreRunsAwayFromHome() {
        val day = 86_400_000L
        val home = List(40) { Analysis.Located(it.toLong(), it * day, 52.52, 13.40) }
        val lisbon = List(15) { Analysis.Located(100L + it, 50 * day + it * 3_600_000L, 38.71, -9.14) }
        val trips = Analysis.trips(home + lisbon)
        assertEquals(1, trips.size)
        assertEquals(15, trips[0].ids.size)
        assertTrue(trips[0].km in 2200..2400)
    }

    @Test
    fun receiptIsReadWithTotalDateAndMerchant() {
        val doc = DocParser.parse(
            """
            Cervejaria Ramiro
            Av. Almirante Reis 1
            15/05/2026 22:14
            Gambas 2x 24,00
            Sapateira 32,60
            Subtotal 70,24
            IVA 23% 16,16
            TOTAL 86,40 EUR
            """.trimIndent()
        )
        assertNotNull(doc)
        assertEquals(DocParser.Kind.Receipt, doc!!.kind)
        assertEquals("€86.40", doc.total)
        assertEquals("Cervejaria Ramiro", doc.field("Merchant"))
        assertNotNull(doc.dateMillis)
        assertTrue(doc.confidence >= 0.9)
    }

    @Test
    fun ticketNeedsTicketWordsAndReadsTime() {
        val doc = DocParser.parse(
            """
            Rodrigo Leão
            Coliseu dos Recreios
            Sat 7 Nov 2026
            Doors 20:30
            Plateia Row F Seat 12
            """.trimIndent()
        )
        assertNotNull(doc)
        assertEquals(DocParser.Kind.Ticket, doc!!.kind)
        assertEquals("Rodrigo Leão", doc.title)
        assertNotNull(doc.dateMillis)
    }

    @Test
    fun businessCardWithTwoEmailsAsks() {
        val doc = DocParser.parse(
            """
            Mara Lindqvist
            Head of Partnerships
            Nordvik Studio
            +46 70 412 88 31
            mara.lindqvist@nordvik.se
            m.lindqvist@nordvik.se
            """.trimIndent()
        )
        assertNotNull(doc)
        assertEquals(DocParser.Kind.Card, doc!!.kind)
        assertEquals("Mara Lindqvist", doc.name)
        assertTrue("Email" in doc.unsure)
        assertTrue(doc.confidence < 0.9)
    }

    @Test
    fun defaultToneCurveIsIdentityAndLevelsClip() {
        val p = EditParams()
        for (i in 0..20) assertEquals(i / 20f, p.tone(i / 20f), 0.002f)
        assertEquals((0..255).toList(), p.lut().toList())
        val levels = EditParams(black = 0.2f, white = 0.8f)
        assertEquals(0f, levels.tone(0.1f), 0.001f)
        assertEquals(1f, levels.tone(0.9f), 0.001f)
        assertTrue(EditParams(c2 = 0.6f).tone(0.5f) > 0.55f)
    }

    @Test
    fun ordinaryTextIsNotADocument() {
        assertNull(DocParser.parse("Happy birthday Jonas\nSee you at eight"))
    }
}
