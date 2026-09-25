package com.unifiedcomms.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class EmailBodyTest {
    @Test
    fun normalizesCrLineBreaksAndCollapsesBlankRuns() {
        // Body captured from the device DB: CR-only breaks, 3 blank lines, trailing nbsp.
        val raw = "Subject: Music\r\r\rFrom: Keith\r\r\u00A0\r\r"
        assertEquals("Subject: Music\n\nFrom: Keith", squashBlankLines(raw))
    }

    @Test
    fun keepsSingleAndDoubleBlankLines() {
        assertEquals("a\n\nb", squashBlankLines("a\n\nb"))
        assertEquals("a\n\nb", squashBlankLines("a\r\n\rb"))
    }
}
