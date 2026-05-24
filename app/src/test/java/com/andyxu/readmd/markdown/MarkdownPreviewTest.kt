package com.andyxu.readmd.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownPreviewTest {
    @Test
    fun countSearchMatches_isCaseInsensitive() {
        val content = "ReadMD can read markdown.\nreadmd can edit Markdown."

        val count = countSearchMatches(content, "readmd")

        assertEquals(2, count)
    }

    @Test
    fun countSearchMatches_returnsZeroForBlankQuery() {
        val count = countSearchMatches("# Title", " ")

        assertEquals(0, count)
    }
}

