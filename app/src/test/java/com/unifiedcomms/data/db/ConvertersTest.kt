package com.unifiedcomms.data.db

import com.unifiedcomms.data.db.converters.StringListConverter
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converter = StringListConverter()

    @Test
    fun `empty list round trip`() {
        val db = converter.fromList(emptyList())
        val back = converter.toList(db)
        assertEquals(emptyList<String>(), back)
    }

    @Test
    fun `non-empty list round trip`() {
        val original = listOf("a", "b", "c")
        val db = converter.fromList(original)
        val back = converter.toList(db)
        assertEquals(original, back)
    }

    @Test
    fun `null input returns empty list`() {
        val back = converter.toList(null)
        assertEquals(emptyList<String>(), back)
    }
}
