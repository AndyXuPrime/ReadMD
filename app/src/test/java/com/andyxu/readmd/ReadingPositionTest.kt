package com.andyxu.readmd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingPositionTest {
    @Test
    fun approximateOffset_movesToNearbyLineStart() {
        val text = (1..100).joinToString("\n") { "第 $it 行内容" }

        val offset = approximateTextOffsetForFraction(text, 0.7f)

        assertTrue(offset > text.length / 2)
        assertTrue(offset == 0 || text[offset - 1] == '\n')
    }

    @Test
    fun approximateOffset_clampsDocumentEdges() {
        assertEquals(0, approximateTextOffsetForFraction("abc", -1f))
        assertEquals(3, approximateTextOffsetForFraction("abc", 2f))
    }
}
