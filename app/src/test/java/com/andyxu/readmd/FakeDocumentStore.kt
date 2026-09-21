package com.andyxu.readmd

import android.net.Uri
import com.andyxu.readmd.data.DocumentStore
import com.andyxu.readmd.data.DraftSnapshot
import com.andyxu.readmd.data.ReaderSettings
import com.andyxu.readmd.data.RecentFile

class FakeDocumentStore(
    var restoredDraft: DraftSnapshot? = null,
) : DocumentStore {
    var savedDraft: DraftSnapshot? = null
    var draftSaveCount = 0
    var draftClearCount = 0
    var settings = ReaderSettings()
    val recents = mutableListOf<RecentFile>()
    var documentName = "note.md"
    var documentSize: Long? = 0L
    var documentMime = "text/markdown"
    var documentContent = "# 原文"
    var writable = true
    var readFailure: Throwable? = null
    var writeFailure: Throwable? = null
    val persistedPermissions = mutableListOf<Pair<String, Int>>()
    val writes = mutableListOf<Pair<String, String>>()
    val forgottenUris = mutableListOf<String>()
    var readCount = 0

    override fun persistUriPermission(uri: Uri, grantFlags: Int) {
        persistedPermissions += uri.toString() to grantFlags
    }
    override fun displayName(uri: Uri) = documentName
    override fun fileSize(uri: Uri): Long? = documentSize
    override fun mimeType(uri: Uri) = documentMime
    override fun readText(uri: Uri, maxBytes: Long): String {
        readCount += 1
        readFailure?.let { throw it }
        return documentContent
    }
    override fun writeText(uri: Uri, content: String) {
        writeFailure?.let { throw it }
        documentContent = content
        writes += uri.toString() to content
    }
    override fun canWrite(uri: Uri) = writable
    override fun rememberRecentFile(
        uri: Uri,
        displayName: String,
        canWrite: Boolean,
        previewSnippet: String,
    ) {
        recents.removeAll { it.uri == uri.toString() }
        recents.add(0, RecentFile(uri.toString(), displayName, 1L, canWrite, previewSnippet))
    }

    override fun recentFiles(): List<RecentFile> = recents.toList()
    override fun clearRecentFiles() = recents.clear()
    override fun forgetRecentFile(uri: Uri) {
        forgottenUris += uri.toString()
        recents.removeAll { it.uri == uri.toString() }
    }
    override fun readerSettings() = settings
    override fun saveReaderSettings(settings: ReaderSettings) {
        this.settings = settings
    }

    override fun saveDraft(snapshot: DraftSnapshot) {
        savedDraft = snapshot
        draftSaveCount += 1
    }

    override fun draftSnapshot() = restoredDraft
    override fun clearDraft() {
        savedDraft = null
        restoredDraft = null
        draftClearCount += 1
    }

    override fun buildPreviewSnippet(content: String, maxChars: Int) = content.take(maxChars)
    override fun sanitizeText(text: String) = text.replace("\u0000", "")
}
