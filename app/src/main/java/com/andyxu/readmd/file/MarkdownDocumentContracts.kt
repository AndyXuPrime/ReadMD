package com.andyxu.readmd.file

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

data class PickedDocument(
    val uri: Uri,
    val grantFlags: Int,
)

class OpenMarkdownDocument : ActivityResultContract<Unit, PickedDocument?>() {
    override fun createIntent(context: Context, input: Unit): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "text/markdown",
                    "text/x-markdown",
                    "text/plain",
                    "application/octet-stream",
                ),
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): PickedDocument? {
        if (resultCode != Activity.RESULT_OK) return null
        val uri = intent?.data ?: return null
        val flags = intent.flags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        return PickedDocument(uri = uri, grantFlags = flags)
    }
}
