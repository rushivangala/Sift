package com.sift.photos.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 1 §06 and the Stage 5 audit as executable checks: every accent source must produce
 * roles that pass WCAG 2.2 AA in light, dark and true black.
 */
class AccentPipelineTest {

    private val wallpaperSeeds = listOf(0x6E8F7A, 0x8A7BB0, 0xC08A5A)
    private val photoSeeds = listOf(0x2F7FB5, 0xC2453A, 0xE2B93B, 0x3C7D4E)
    private val allSeeds = CuratedAccent.entries.map { it.seed } + wallpaperSeeds + photoSeeds

    @Test
    fun contrastMatchesWcagReferenceValues() {
        assertEquals(21.0, ColorMath.contrast(0x000000, 0xFFFFFF), 0.001)
        assertEquals(1.0, ColorMath.contrast(0x777777, 0x777777), 0.001)
        // #767676 on white is the classic 4.54:1 AA threshold color.
        assertEquals(4.54, ColorMath.contrast(0x767676, 0xFFFFFF), 0.01)
    }

    @Test
    fun hslRoundTripIsStable() {
        for (seed in allSeeds) {
            val (h, s, l) = ColorMath.toHsl(seed).let { Triple(it[0], it[1], it[2]) }
            val back = ColorMath.fromHsl(h, s, l)
            val delta = listOf(16, 8, 0).maxOf { sh -> kotlin.math.abs(((seed shr sh) and 0xFF) - ((back shr sh) and 0xFF)) }
            assertTrue("round trip drifted for ${ColorMath.hex(seed)}", delta <= 1)
        }
    }

    @Test
    fun toneHitsTargetLightness() {
        for (seed in allSeeds) for (t in listOf(10.0, 40.0, 80.0, 90.0)) {
            assertEquals("L* for ${ColorMath.hex(seed)} at T$t", t, ColorMath.lstar(ColorMath.tone(seed, t, 0.62)), 1.5)
        }
    }

    @Test
    fun everyAccentPassesAaInEveryTheme() {
        val failures = mutableListOf<String>()
        for (seed in allSeeds) for (n in Neutrals.All) {
            val r = AccentPipeline.roles(seed, n)
            fun check(name: String, fg: Int, bg: Int, min: Double) {
                val ratio = ColorMath.contrast(fg, bg)
                if (ratio < min) failures += "${ColorMath.hex(seed)} ${n.key} $name %.2f".format(ratio)
            }
            n.primarySurfaces.forEach { check("primary/surface", r.primary, it, AccentPipeline.AA_TEXT) }
            check("onPrimary/primary", r.onPrimary, r.primary, AccentPipeline.AA_TEXT)
            check("onPC/PC", r.onPrimaryContainer, r.primaryContainer, AccentPipeline.AA_TEXT)
            check("inversePrimary/inverse", r.inversePrimary, n.inverseSurface, AccentPipeline.AA_TEXT)
            check("badge/PC", r.selectionBadge, r.primaryContainer, AccentPipeline.AA_UI)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun curatedRolesMatchTheDesignFile() {
        // Values from the Stage 1 reference implementation (tone 40 light / 80 dark, chroma cap 0.62).
        val slate = AccentPipeline.roles(CuratedAccent.Slate.seed, Neutrals.Light)
        assertEquals(40.0, ColorMath.lstar(slate.primary), 1.5)
        val slateDark = AccentPipeline.roles(CuratedAccent.Slate.seed, Neutrals.Dark)
        assertEquals(80.0, ColorMath.lstar(slateDark.primary), 1.5)
    }

    @Test
    fun rawSeedsFlaggedByStage1StillFailAsText() {
        // The design flags these seeds as unusable for text; the pipeline must not ship them raw.
        assertTrue(ColorMath.contrast(CuratedAccent.Ochre.seed, Neutrals.Light.surface) < 4.5)
        assertTrue(AccentPipeline.roles(CuratedAccent.Ochre.seed, Neutrals.Light).primary != CuratedAccent.Ochre.seed)
    }

    @Test
    fun neutralTextAndOutlinesPass() {
        for (n in Neutrals.All) {
            assertTrue(ColorMath.contrast(n.onSurface, n.surface) >= 4.5)
            assertTrue(ColorMath.contrast(n.onSurfaceVariant, n.surface) >= 4.5)
            assertTrue(ColorMath.contrast(n.onSurfaceVariant, n.containerHigh) >= 4.5)
            assertTrue(ColorMath.contrast(n.outline, n.surface) >= 3.0)
            assertTrue(ColorMath.contrast(n.inverseOnSurface, n.inverseSurface) >= 4.5)
        }
    }

    @Test
    fun photoScrimHoldsAaOnWhite() {
        // 56% black over the lightest possible photo (pure white), white text on top.
        val scrimOnWhite = ColorMath.mix(0xFFFFFF, 0x000000, 0.56)
        assertTrue(ColorMath.contrast(0xFFFFFF, scrimOnWhite) >= 4.5)
    }

    @Test
    fun photoSeedFindsDominantColorAndSkipsNeutrals() {
        val forest = IntArray(400) { if (it < 300) 0xFF3C7D4E.toInt() else 0xFFE8E4DC.toInt() }
        val seed = PhotoSeed.extract(forest)
        assertNotNull(seed)
        val hue = ColorMath.toHsl(seed!!)[0] * 360
        assertTrue("expected green hue, got $hue", hue in 100.0..160.0)

        val grey = IntArray(400) { 0xFF808080.toInt() }
        assertNull(PhotoSeed.extract(grey))
    }

    @Test
    fun photoSeedIgnoresSkinTones() {
        // Mostly skin with a smaller patch of sky: the sky should win.
        val px = IntArray(1000) { if (it < 700) 0xFFD9A58A.toInt() else 0xFF2F7FB5.toInt() }
        val seed = PhotoSeed.extract(px)!!
        val hue = ColorMath.toHsl(seed)[0] * 360
        assertTrue("expected blue hue, got $hue", hue in 190.0..230.0)
    }
}
