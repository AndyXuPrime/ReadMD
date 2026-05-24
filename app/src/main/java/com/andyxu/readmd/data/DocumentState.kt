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
)

data class ReaderSettings(
    val elderMode: Boolean = false,
    val fontScale: Float = 1f,
    val lineHeightScale: Float = 1f,
)

data class DocumentState(
    val currentUri: Uri? = null,
    val displayName: String = "未命名.md",
    val content: String = "",
    val draftContent: String = "",
    val previewContent: String? = null,
    val isEditing: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val isLoading: Boolean = false,
    val canWriteCurrentFile: Boolean = false,
    val message: String? = null,
    val searchQuery: String = "",
    val settings: ReaderSettings = ReaderSettings(),
    val recentFiles: List<RecentFile> = emptyList(),
    val pendingSaveTarget: SaveTarget? = null,
) {
    val activeContent: String
        get() = if (isEditing) draftContent else previewContent ?: content
}
