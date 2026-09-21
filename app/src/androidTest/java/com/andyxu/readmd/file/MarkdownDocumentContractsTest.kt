package com.andyxu.readmd.file

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownDocumentContractsTest {
    @Test
    fun createDocument_preservesReturnedReadWriteGrantFlags() {
        val expectedFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val resultIntent = Intent().apply {
            data = Uri.parse("content://test/new.md")
            flags = expectedFlags
        }

        val document = CreateMarkdownDocument().parseResult(Activity.RESULT_OK, resultIntent)

        assertNotNull(document)
        assertEquals(expectedFlags, document?.grantFlags)
    }
}
