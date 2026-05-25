package com.andyxu.readmd

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andyxu.readmd.data.DocumentRepository
import com.andyxu.readmd.data.DocumentState
import com.andyxu.readmd.data.DraftSnapshot
import com.andyxu.readmd.data.SaveTarget
import com.andyxu.readmd.file.PickedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReadMDViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val MAX_OPEN_BYTES = 8L * 1024L * 1024L
    }

    private data class OpenedDocumentResult(
        val name: String,
        val content: String,
        val canWrite: Boolean,
        val snippet: String,
    )

    private val repository = DocumentRepository(application)
    private val restoredDraft = repository.draftSnapshot()
    private val _state = MutableStateFlow(
        initialState(),
    )
    val state: StateFlow<DocumentState> = _state

    private fun initialState(): DocumentState {
        val settings = repository.readerSettings()
        val restoredContent = restoredDraft?.let {
            it.copy(
                displayName = repository.sanitizeText(it.displayName),
                content = repository.sanitizeText(it.content),
                draftContent = repository.sanitizeText(it.draftContent),
            )
        }
        val shouldRestoreDraft = restoredContent != null &&
            restoredContent.draftContent.isNotBlank() &&
            restoredContent.draftContent != restoredContent.content

        return if (shouldRestoreDraft) {
            DocumentState(
                currentUri = restoredContent.currentUri?.let(Uri::parse),
                displayName = restoredContent.displayName,
                content = restoredContent.content,
                draftContent = restoredContent.draftContent,
                isEditing = true,
                hasUnsavedChanges = restoredContent.draftContent != restoredContent.content,
                draftUpdatedAt = restoredContent.updatedAt,
                canWriteCurrentFile = restoredContent.canWriteCurrentFile,
                settings = settings,
                recentFiles = repository.recentFiles(),
                message = "已恢复上次未保存草稿",
            )
        } else {
            if (restoredContent != null) {
                repository.clearDraft()
            }
            DocumentState(
                settings = settings,
                recentFiles = repository.recentFiles(),
            )
        }
    }

    fun openPickedDocument(document: PickedDocument) {
        repository.persistUriPermission(document.uri, document.grantFlags)
        openUri(document.uri)
    }

    fun openRecentFile(uriText: String) {
        openUri(Uri.parse(uriText))
    }

    fun newUnsavedDocument() {
        val initial = "# 新备忘录\n\n在这里记录内容。"
        _state.update {
            it.copy(
                currentUri = null,
                displayName = "新备忘录.md",
                content = initial,
                draftContent = initial,
                previewContent = null,
                isEditing = true,
                hasUnsavedChanges = true,
                draftUpdatedAt = System.currentTimeMillis(),
                canWriteCurrentFile = false,
                message = "已创建新备忘录，请保存到文件",
            )
        }
    }

    fun requestNewFileSave() {
        _state.update { it.copy(pendingSaveTarget = SaveTarget.NewDocument) }
    }

    fun requestSaveAs() {
        _state.update { it.copy(pendingSaveTarget = SaveTarget.SaveAs) }
    }

    fun requestExport() {
        _state.update { it.copy(pendingSaveTarget = SaveTarget.Export) }
    }

    fun closeDocument() {
        _state.update {
            it.copy(
                currentUri = null,
                displayName = "未命名.md",
                content = "",
                draftContent = "",
                previewContent = null,
                isEditing = false,
                hasUnsavedChanges = false,
                draftUpdatedAt = null,
                canWriteCurrentFile = false,
                message = "已返回首页",
            )
        }
    }

    fun clearPendingSaveTarget() {
        _state.update { it.copy(pendingSaveTarget = null) }
    }

    fun saveCurrentFile() {
        val current = _state.value
        val uri = current.currentUri
        if (uri == null || !current.canWriteCurrentFile) {
            requestSaveAs()
            return
        }
        writeToUri(
            uri = uri,
            content = current.draftContent,
            switchCurrentFile = true,
            successMessage = "已保存",
        )
    }

    fun writeCreatedDocument(uri: Uri, target: SaveTarget) {
        val current = _state.value
        val content = current.draftContent.ifBlank { current.activeContent }
        val switchCurrent = target == SaveTarget.SaveAs || target == SaveTarget.NewDocument
        val message = when (target) {
            SaveTarget.Export -> "已导出 Markdown"
            SaveTarget.SaveAs -> "已另存为新文件"
            SaveTarget.NewDocument -> "新备忘录已保存"
            SaveTarget.Current -> "已保存"
        }
        writeToUri(uri, content, switchCurrent, message)
        clearPendingSaveTarget()
    }

    fun updateDraft(content: String) {
        _state.update {
            it.copy(
                draftContent = content,
                previewContent = null,
                hasUnsavedChanges = content != it.content,
                draftUpdatedAt = if (content != it.content) System.currentTimeMillis() else it.draftUpdatedAt,
            )
        }
        saveCurrentDraft()
    }

    fun enterEditMode() {
        _state.update {
            it.copy(
                isEditing = true,
                draftContent = it.previewContent ?: it.content,
                previewContent = null,
                message = null,
            )
        }
    }

    fun previewDraft() {
        _state.update {
            it.copy(
                previewContent = it.draftContent,
                isEditing = false,
                hasUnsavedChanges = true,
                draftUpdatedAt = System.currentTimeMillis(),
                message = "正在预览未保存内容",
            )
        }
        saveCurrentDraft()
    }

    fun discardChanges() {
        _state.update {
            it.copy(
                draftContent = it.content,
                previewContent = null,
                isEditing = false,
                hasUnsavedChanges = false,
                draftUpdatedAt = null,
                message = "已放弃未保存修改",
            )
        }
        repository.clearDraft()
    }

    fun toggleElderMode() {
        _state.update {
            it.copyAndSaveSettings(it.settings.copy(elderMode = !it.settings.elderMode))
        }
    }

    fun toggleDarkMode() {
        _state.update {
            it.copyAndSaveSettings(it.settings.copy(darkMode = !it.settings.darkMode))
        }
    }

    fun increaseFont() {
        _state.update {
            val settings = it.settings
            it.copyAndSaveSettings(settings.copy(fontScale = (settings.fontScale + 0.1f).coerceAtMost(1.8f)))
        }
    }

    fun decreaseFont() {
        _state.update {
            val settings = it.settings
            it.copyAndSaveSettings(settings.copy(fontScale = (settings.fontScale - 0.1f).coerceAtLeast(0.85f)))
        }
    }

    fun increaseLineHeight() {
        _state.update {
            val settings = it.settings
            it.copyAndSaveSettings(settings.copy(lineHeightScale = (settings.lineHeightScale + 0.1f).coerceAtMost(1.8f)))
        }
    }

    fun decreaseLineHeight() {
        _state.update {
            val settings = it.settings
            it.copyAndSaveSettings(settings.copy(lineHeightScale = (settings.lineHeightScale - 0.1f).coerceAtLeast(0.85f)))
        }
    }

    fun setFontScale(scale: Float) {
        _state.update { it.copyAndSaveSettings(it.settings.copy(fontScale = scale.coerceIn(0.85f, 1.8f))) }
    }

    fun setLineHeightScale(scale: Float) {
        _state.update { it.copyAndSaveSettings(it.settings.copy(lineHeightScale = scale.coerceIn(0.85f, 1.8f))) }
    }

    fun updateSearch(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun clearRecentFiles() {
        repository.clearRecentFiles()
        _state.update { it.copy(recentFiles = emptyList(), message = "最近文件已清空") }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun openUri(uri: Uri) {
        _state.update { it.copy(isLoading = true, message = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val name = repository.displayName(uri)
                    val size = repository.fileSize(uri)
                    if (size != null && size > MAX_OPEN_BYTES) {
                        throw IllegalStateException("文件过大，建议拆分后再打开")
                    }
                    val content = repository.sanitizeText(repository.readText(uri))
                    val canWrite = repository.canWrite(uri)
                    val snippet = repository.buildPreviewSnippet(content)
                    repository.rememberRecentFile(uri, name, canWrite, snippet)
                    OpenedDocumentResult(name, content, canWrite, snippet)
                }
            }
            result.onSuccess { document ->
                _state.update {
                    it.copy(
                        currentUri = uri,
                        displayName = document.name,
                        content = document.content,
                        draftContent = document.content,
                        previewContent = null,
                        isEditing = false,
                        hasUnsavedChanges = false,
                        draftUpdatedAt = null,
                        isLoading = false,
                        canWriteCurrentFile = document.canWrite,
                        recentFiles = repository.recentFiles(),
                        message = "已打开 ${document.name}",
                    )
                }
                repository.clearDraft()
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        message = "无法打开文件：${error.message ?: "请重新选择"}",
                    )
                }
            }
        }
    }

    private fun writeToUri(
        uri: Uri,
        content: String,
        switchCurrentFile: Boolean,
        successMessage: String,
    ) {
        _state.update { it.copy(isLoading = true, message = null) }
        viewModelScope.launch {
            val currentEditing = _state.value.isEditing
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    repository.writeText(uri, content)
                    val name = repository.displayName(uri)
                    val canWrite = repository.canWrite(uri)
                    val safeContent = repository.sanitizeText(content)
                    if (switchCurrentFile) {
                        repository.rememberRecentFile(
                            uri = uri,
                            displayName = name,
                            canWrite = canWrite,
                            previewSnippet = repository.buildPreviewSnippet(safeContent),
                        )
                    }
                    Triple(name, safeContent, canWrite)
                }
            }
            result.onSuccess { (name, savedContent, canWrite) ->
                _state.update {
                    if (switchCurrentFile) {
                        it.copy(
                            currentUri = uri,
                            displayName = name,
                            content = savedContent,
                            draftContent = savedContent,
                            previewContent = null,
                            isEditing = currentEditing,
                            hasUnsavedChanges = false,
                            draftUpdatedAt = null,
                            canWriteCurrentFile = canWrite,
                            isLoading = false,
                            recentFiles = repository.recentFiles(),
                            message = successMessage,
                        )
                    } else {
                        it.copy(
                            isLoading = false,
                            message = successMessage,
                        )
                    }
                }
                if (switchCurrentFile) {
                    repository.clearDraft()
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        message = "保存失败：${error.message ?: "请另存为新文件"}",
                    )
                }
            }
        }
    }

    fun suggestedFileName(): String {
        val name = _state.value.displayName.ifBlank { "ReadMD-${System.currentTimeMillis()}.md" }
        return if (name.endsWith(".md", ignoreCase = true) || name.endsWith(".markdown", ignoreCase = true)) {
            name
        } else {
            "$name.md"
        }
    }

    private fun DocumentState.copyAndSaveSettings(settings: com.andyxu.readmd.data.ReaderSettings): DocumentState {
        repository.saveReaderSettings(settings)
        return copy(settings = settings)
    }

    private fun saveCurrentDraft() {
        val current = _state.value
        if (!current.hasUnsavedChanges) return
        repository.saveDraft(
            DraftSnapshot(
                currentUri = current.currentUri?.toString(),
                displayName = current.displayName,
                content = current.content,
                draftContent = current.draftContent,
                canWriteCurrentFile = current.canWriteCurrentFile,
                updatedAt = current.draftUpdatedAt ?: System.currentTimeMillis(),
            ),
        )
    }
}
