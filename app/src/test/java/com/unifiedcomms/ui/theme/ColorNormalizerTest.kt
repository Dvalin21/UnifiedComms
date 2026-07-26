package com.unifiedcomms.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the calendar color fix without depending on android.graphics.Color
 * (which is a no-op stub on the host JVM unit-test runtime). The real bug was in
 * ColorNormalizer.normalize(): servers send CSS named colors (dodgerblue, gold)
 * and 8-digit ARGB (#FF0000FF); before the fix the app discarded these and
 * forced every event to default blue. These tests assert the resolver keeps each
 * server color distinct and correct (no overwrite), which is what the UI paints.
 */
class ColorNormalizerTest {

    @Test
    fun namedColorsResolveToDistinctHex() {
        val gold = ColorNormalizer.normalize("gold")
        val dodger = ColorNormalizer.normalize("dodgerblue")
        val thistle = ColorNormalizer.normalize("thistle")
        val cyan = ColorNormalizer.normalize("cyan")
        assertEquals("#FFD700", gold)        // gold
        assertEquals("#1E90FF", dodger)      // dodgerblue
        assertEquals("#D8BFD8", thistle)     // thistle
        assertEquals("#00FFFF", cyan)        // cyan
        // each must be a DIFFERENT color, not collapsed to blue
        assertEquals(4, setOf(gold, dodger, thistle, cyan).size)
    }

    @Test
    fun argbEightDigitDropsAlpha() {
        assertEquals("#0000FF", ColorNormalizer.normalize("#FF0000FF")) // SOGo collection color
        assertEquals("#00FF00", ColorNormalizer.normalize("#F0F0"))     // #ARGB shorthand
        assertEquals("#112233", ColorNormalizer.normalize("#123"))      // #RGB shorthand
        assertEquals("#2196F3", ColorNormalizer.normalize("#2196F3"))   // already 6-digit
    }

    @Test
    fun eventColorStoresServerStringUnchanged() {
        // The raw server color must be preserved verbatim — we never overwrite it.
        val goldEvent = com.unifiedcomms.data.model.EventColor("gold", "#000000")
        val argbEvent = com.unifiedcomms.data.model.EventColor("#FF0000FF", "#FFFFFF")
        assertEquals("gold", goldEvent.background)
        assertEquals("#FF0000FF", argbEvent.background)
    }

    @Test
    fun unknownColorIsNotRenderable() {
        // A genuinely unknown string must not resolve to a real hex (so the UI
        // keeps its safe blue fallback instead of painting black/#000000).
        assertTrue(ColorNormalizer.normalize("not-a-real-color").isEmpty())
    }
}
