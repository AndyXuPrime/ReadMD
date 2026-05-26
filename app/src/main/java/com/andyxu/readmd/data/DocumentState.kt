package com.andyxu.readmd.data

import android.net.Uri

enum class SaveTarget {
    Current,
    SaveAs,
    Export,
    NewDocument,
}

data class RecentFile(
    val uri: String,
    val displayName: String,
    val lastOpenedAt: Long,
    val canWrite: Boolean,
    val previewSnippet: String = "",
)

data class ReaderSettings(
    val elderMode: Boolean = false,
    val darkMode: Boolean = false,
    val fontScale: Float = 1f,
    val lineHeightScale: Float = 1f,
) {
    companion object {
        const val MIN_FONT_SCALE = 0.85f
        const val MAX_FONT_SCALE = 1.55f
        const val MIN_LINE_HEIGHT_SCALE = 0.85f
        const val MAX_LINE_HEIGHT_SCALE = 1.8f
    }
}

data class DraftSnapshot(
    val currentUri: String?,
    val displayName: String,
    val content: String,
    val draftContent: String,
    val canWriteCurrentFile: Boolean,
    val updatedAt: Long,
)

data class DocumentState(
    val currentUri: Uri? = null,
    val displayName: String = "未命名.md",
    val content: String = "",
    val draftContent: String = "",
    val previewContent: String? = null,
    val isEditing: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val draftUpdatedAt: Long? = null,
    val isLoading: Boolean = false,
    val canWriteCurrentFile: Boolean = false,
    val message: String? = null,
    val searchQuery: String = "",
    val settings: ReaderSettings = ReaderSettings(),
    val readingFontScale: Float = 1f,
    val recentFiles: List<RecentFile> = emptyList(),
    val pendingSaveTarget: SaveTarget? = null,
) {
    val activeContent: String
        get() = if (isEditing) draftContent else previewContent ?: content
}
