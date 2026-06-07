package com.andyxu.readmd.file

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedFileTypesTest {
    @Test
    fun isSupportedReadMDDocument_acceptsMarkdownFiles() {
        assertTrue(isSupportedReadMDDocument("note.md", null))
        assertTrue(isSupportedReadMDDocument("note.markdown", null))
        assertTrue(isSupportedReadMDDocument("NOTE.MD", null))
    }

    @Test
    fun isSupportedReadMDDocument_acceptsTxtFiles() {
        assertTrue(isSupportedReadMDDocument("todo.txt", "text/plain"))
    }

    @Test
    fun isSupportedReadMDDocument_rejectsUnsupportedExtensions() {
        assertFalse(isSupportedReadMDDocument("report.pdf", "application/pdf"))
        assertFalse(isSupportedReadMDDocument("image.png", "image/png"))
        assertFalse(isSupportedReadMDDocument("archive.zip", "application/zip"))
    }

    @Test
    fun isSupportedReadMDDocument_acceptsExtensionlessMarkdownMime() {
        assertTrue(isSupportedReadMDDocument("README", "text/markdown"))
    }
}
