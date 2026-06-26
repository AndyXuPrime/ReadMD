package com.andyxu.readmd.file

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentAccessErrorsTest {
    @Test
    fun isDocumentAccessDenied_matchesSecurityExceptionCause() {
        val error = IllegalStateException("wrapped", SecurityException("missing grant"))

        assertTrue(error.isDocumentAccessDenied())
    }

    @Test
    fun isDocumentAccessDenied_matchesAndroidPermissionDenialMessage() {
        val error = IllegalStateException(
            "Permission Denial: reading ExternalStorageProvider uri requires ACTION_OPEN_DOCUMENT",
        )

        assertTrue(error.isDocumentAccessDenied())
    }

    @Test
    fun isDocumentAccessDenied_ignoresRegularReadErrors() {
        val error = IllegalStateException("文件超过 2MB，建议拆分后再打开")

        assertFalse(error.isDocumentAccessDenied())
    }

    @Test
    fun openDocumentMessage_hidesRawPermissionDenialDetails() {
        val error = SecurityException("Permission Denial: content://private")

        assertEquals("该记录已失效，请重新选择", error.openDocumentMessage())
    }
}
