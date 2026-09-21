package com.andyxu.readmd.data

import android.net.Uri

interface DocumentStore {
    fun persistUriPermission(uri: Uri, grantFlags: Int)
    fun displayName(uri: Uri): String
    fun fileSize(uri: Uri): Long?
    fun mimeType(uri: Uri): String?
    fun readText(uri: Uri, maxBytes: Long = MAX_DOCUMENT_BYTES): String
    fun writeText(uri: Uri, content: String)
    fun canWrite(uri: Uri): Boolean
    fun rememberRecentFile(uri: Uri, displayName: String, canWrite: Boolean, previewSnippet: String)
    fun recentFiles(): List<RecentFile>
    fun clearRecentFiles()
    fun forgetRecentFile(uri: Uri)
    fun readerSettings(): ReaderSettings
    fun saveReaderSettings(settings: ReaderSettings)
    fun saveDraft(snapshot: DraftSnapshot)
    fun draftSnapshot(): DraftSnapshot?
    fun clearDraft()
    fun buildPreviewSnippet(content: String, maxChars: Int = 160): String
    fun sanitizeText(text: String): String
}

const val MAX_DOCUMENT_BYTES = 2L * 1024L * 1024L
const val MAX_RECENT_FILES = 20
