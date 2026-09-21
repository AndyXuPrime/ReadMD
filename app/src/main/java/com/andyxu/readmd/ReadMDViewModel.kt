package com.andyxu.readmd

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.core.net.toUri
import com.andyxu.readmd.data.DocumentState
import com.andyxu.readmd.data.DocumentStore
import com.andyxu.readmd.data.DraftSnapshot
import com.andyxu.readmd.data.MAX_DOCUMENT_BYTES
import com.andyxu.readmd.data.NavigationTarget
import com.andyxu.readmd.data.PendingNavigation
import com.andyxu.readmd.data.ReaderSettings
import com.andyxu.readmd.data.SaveTarget
import com.andyxu.readmd.file.DocumentTooLargeException
import com.andyxu.readmd.file.PickedDocument
import com.andyxu.readmd.file.UnsupportedDocumentTypeException
import com.andyxu.readmd.file.isDocumentAccessDenied
import com.andyxu.readmd.file.isSupportedReadMDDocument
import com.andyxu.readmd.file.openDocumentMessage
import com.andyxu.readmd.file.saveDocumentMessage
import com.andyxu.readmd.file.suggestedReadMDFileName
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReadMDViewModel internal constructor(
    private val repository: DocumentStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private data class OpenedDocumentResult(
        val name: String,
        val content: String,
        val canWrite: Boolean,
    )

    private val restoredDraft = repository.draftSnapshot()
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<DocumentState> = _state
    private var draftSaveJob: Job? = null

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
                currentUri = restoredContent.currentUri?.toUri(),
                displayName = restoredContent.displayName,
                content = restoredContent.content,
                draftContent = restoredContent.draftContent,
                isEditing = true,
                hasUnsavedChanges = true,
                draftUpdatedAt = restoredContent.updatedAt,
                canWriteCurrentFile = restoredContent.canWriteCurrentFile,
                settings = settings,
                recentFiles = repository.recentFiles(),
                message = "已恢复上次未保存草稿",
            )
        } else {
            if (restoredContent != null) repository.clearDraft()
            DocumentState(settings = settings, recentFiles = repository.recentFiles())
        }
    }

    fun openPickedDocument(document: PickedDocument) {
        repository.persistUriPermission(document.uri, document.grantFlags)
        openUri(
            uri = document.uri,
            hasWriteGrant = document.grantFlags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0,
        )
    }

    fun requestOpenRecentFile(uriText: String) {
        requestNavigation(PendingNavigation(NavigationTarget.RecentFile, uriText))
    }

    fun requestNewDocument() {
        requestNavigation(PendingNavigation(NavigationTarget.NewDocument))
    }

    fun requestImportDocument() {
        requestNavigation(PendingNavigation(NavigationTarget.ImportDocument))
    }

    fun consumeDocumentPickerRequest() {
        _state.update { it.copy(shouldLaunchDocumentPicker = false) }
    }

    fun continueEditing() {
        _state.update { it.copy(pendingNavigation = null, isResolvingNavigation = false) }
    }

    fun discardChangesAndContinue() {
        val pending = _state.value.pendingNavigation ?: return
        cancelDraftSaveAndClear()
        _state.update {
            it.copy(
                draftContent = it.content,
                previewContent = null,
                hasUnsavedChanges = false,
                draftUpdatedAt = null,
                pendingNavigation = null,
                isResolvingNavigation = false,
            )
        }
        performNavigation(pending)
    }

    fun saveChangesAndContinue() {
        if (_state.value.pendingNavigation == null) return
        _state.update { it.copy(isResolvingNavigation = true) }
        saveCurrentFile()
    }

    private fun requestNavigation(navigation: PendingNavigation) {
        if (_state.value.hasUnsavedChanges) {
            _state.update { it.copy(pendingNavigation = navigation, isResolvingNavigation = false) }
        } else {
            performNavigation(navigation)
        }
    }

    private fun performNavigation(navigation: PendingNavigation) {
        when (navigation.target) {
            NavigationTarget.Home -> closeDocument()
            NavigationTarget.NewDocument -> newUnsavedDocument()
            NavigationTarget.ImportDocument -> _state.update { it.copy(shouldLaunchDocumentPicker = true) }
            NavigationTarget.RecentFile -> {
                navigation.recentUri?.let { openUri(it.toUri(), forgetRecentOnAccessDenied = true) }
            }
        }
    }

    private fun newUnsavedDocument() {
        val initial = "# 新备忘录\n\n在这里记录内容。"
        _state.update {
            it.copy(
                currentUri = null,
                displayName = "新备忘录.md",
                content = initial,
                draftContent = initial,
                previewContent = null,
                isEditing = true,
                readingFontScale = 1f,
                readingScrollFraction = 0f,
                pendingEditScrollFraction = null,
                hasUnsavedChanges = true,
                draftUpdatedAt = System.currentTimeMillis(),
                canWriteCurrentFile = false,
                pendingNavigation = null,
                isResolvingNavigation = false,
                message = "已创建新备忘录，请保存到文件",
            )
        }
    }

    fun requestSaveAs() {
        _state.update { it.copy(pendingSaveTarget = SaveTarget.SaveAs) }
    }

    fun requestExport() {
        _state.update { it.copy(pendingSaveTarget = SaveTarget.Export) }
    }

    private fun closeDocument() {
        _state.update {
            it.copy(
                currentUri = null,
                displayName = "未命名.md",
                content = "",
                draftContent = "",
                previewContent = null,
                isEditing = false,
                readingFontScale = 1f,
                readingScrollFraction = 0f,
                pendingEditScrollFraction = null,
                hasUnsavedChanges = false,
                draftUpdatedAt = null,
                canWriteCurrentFile = false,
                pendingNavigation = null,
                isResolvingNavigation = false,
                message = null,
            )
        }
    }

    fun handleBack() {
        val current = _state.value
        when {
            current.isEditing -> previewDraft(showMessage = false)
            current.currentUri != null || current.content.isNotBlank() || current.draftContent.isNotBlank() -> {
                requestNavigation(PendingNavigation(NavigationTarget.Home))
            }
        }
    }

    fun clearPendingSaveTarget() {
        _state.update { it.copy(pendingSaveTarget = null, isResolvingNavigation = false) }
    }

    fun saveCurrentFile() {
        val current = _state.value
        val uri = current.currentUri
        if (uri == null || !current.canWriteCurrentFile) {
            _state.update { it.copy(pendingSaveTarget = SaveTarget.SaveAs) }
            return
        }
        writeToUri(
            uri = uri,
            content = current.draftContent,
            switchCurrentFile = true,
            successMessage = "已保存",
        )
    }

    fun writeCreatedDocument(document: PickedDocument, target: SaveTarget) {
        val current = _state.value
        val content = current.draftContent.ifBlank { current.activeContent }
        val switchCurrent = target == SaveTarget.SaveAs || target == SaveTarget.NewDocument
        if (switchCurrent) {
            repository.persistUriPermission(document.uri, document.grantFlags)
        }
        val message = when (target) {
            SaveTarget.Export -> "已导出 Markdown"
            SaveTarget.SaveAs -> "已另存为新文件"
            SaveTarget.NewDocument -> "新备忘录已保存"
            SaveTarget.Current -> "已保存"
        }
        writeToUri(document.uri, content, switchCurrent, message)
        _state.update { it.copy(pendingSaveTarget = null) }
    }

    fun updateDraft(content: String) {
        _state.update {
            val changed = content != it.content
            it.copy(
                draftContent = content,
                previewContent = null,
                hasUnsavedChanges = changed,
                draftUpdatedAt = if (changed) System.currentTimeMillis() else null,
            )
        }
        scheduleDraftSave()
    }

    fun flushDraft() {
        draftSaveJob?.cancel()
        val snapshot = currentDraftSnapshot() ?: return
        draftSaveJob = viewModelScope.launch(ioDispatcher) { repository.saveDraft(snapshot) }
    }

    private fun scheduleDraftSave() {
        draftSaveJob?.cancel()
        val snapshot = currentDraftSnapshot()
        if (snapshot == null) {
            repository.clearDraft()
            return
        }
        draftSaveJob = viewModelScope.launch {
            delay(DRAFT_SAVE_DEBOUNCE_MS)
            withContext(ioDispatcher) { repository.saveDraft(snapshot) }
        }
    }

    fun enterEditMode() {
        _state.update {
            it.copy(
                isEditing = true,
                draftContent = it.previewContent ?: it.content,
                previewContent = null,
                pendingEditScrollFraction = it.readingScrollFraction,
                message = null,
            )
        }
    }

    fun previewDraft(showMessage: Boolean = true) {
        _state.update { current ->
            val hasUnsaved = current.draftContent != current.content
            current.copy(
                previewContent = current.draftContent,
                isEditing = false,
                hasUnsavedChanges = hasUnsaved,
                draftUpdatedAt = if (hasUnsaved) System.currentTimeMillis() else null,
                pendingEditScrollFraction = null,
                message = if (showMessage && hasUnsaved) "正在预览未保存内容" else null,
            )
        }
        scheduleDraftSave()
    }

    fun toggleElderMode() = updateSettings { copy(elderMode = !elderMode) }
    fun toggleDarkMode() = updateSettings { copy(darkMode = !darkMode) }
    fun setFontScale(scale: Float) = updateSettings {
        copy(fontScale = scale.coerceIn(ReaderSettings.MIN_FONT_SCALE, ReaderSettings.MAX_FONT_SCALE))
    }
    fun setLineHeightScale(scale: Float) = updateSettings {
        copy(
            lineHeightScale = scale.coerceIn(
                ReaderSettings.MIN_LINE_HEIGHT_SCALE,
                ReaderSettings.MAX_LINE_HEIGHT_SCALE,
            ),
        )
    }

    private fun updateSettings(transform: ReaderSettings.() -> ReaderSettings) {
        _state.update {
            val settings = it.settings.transform()
            repository.saveReaderSettings(settings)
            it.copy(settings = settings)
        }
    }

    fun setReadingFontScale(scale: Float) {
        _state.update {
            it.copy(readingFontScale = scale.coerceIn(ReaderSettings.MIN_FONT_SCALE, ReaderSettings.MAX_FONT_SCALE))
        }
    }

    fun setReadingScrollFraction(fraction: Float) {
        _state.update {
            val safeFraction = fraction.coerceIn(0f, 1f)
            if (kotlin.math.abs(it.readingScrollFraction - safeFraction) < 0.003f) it
            else it.copy(readingScrollFraction = safeFraction)
        }
    }

    fun clearPendingEditScrollFraction() {
        _state.update { it.copy(pendingEditScrollFraction = null) }
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

    private fun openUri(
        uri: Uri,
        hasWriteGrant: Boolean = false,
        forgetRecentOnAccessDenied: Boolean = false,
    ) {
        _state.update {
            it.copy(
                isLoading = true,
                message = null,
                pendingNavigation = null,
                isResolvingNavigation = false,
            )
        }
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                runCatching {
                    val name = repository.displayName(uri)
                    val mimeType = repository.mimeType(uri)
                    if (!isSupportedReadMDDocument(name, mimeType)) throw UnsupportedDocumentTypeException()
                    val size = repository.fileSize(uri)
                    if (size != null && size > MAX_DOCUMENT_BYTES) throw DocumentTooLargeException()
                    val content = repository.sanitizeText(repository.readText(uri))
                    val canWrite = hasWriteGrant || repository.canWrite(uri)
                    repository.rememberRecentFile(
                        uri,
                        name,
                        canWrite,
                        repository.buildPreviewSnippet(content),
                    )
                    OpenedDocumentResult(name, content, canWrite)
                }
            }
            result.onSuccess { document ->
                cancelDraftSaveAndClear()
                _state.update {
                    it.copy(
                        currentUri = uri,
                        displayName = document.name,
                        content = document.content,
                        draftContent = document.content,
                        previewContent = null,
                        isEditing = false,
                        readingFontScale = 1f,
                        readingScrollFraction = 0f,
                        pendingEditScrollFraction = null,
                        hasUnsavedChanges = false,
                        draftUpdatedAt = null,
                        isLoading = false,
                        canWriteCurrentFile = document.canWrite,
                        recentFiles = repository.recentFiles(),
                        message = null,
                    )
                }
            }.onFailure { error ->
                if (forgetRecentOnAccessDenied && error.isDocumentAccessDenied()) repository.forgetRecentFile(uri)
                _state.update {
                    it.copy(
                        isLoading = false,
                        recentFiles = repository.recentFiles(),
                        message = error.openDocumentMessage(isRecentFile = forgetRecentOnAccessDenied),
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
            val result = withContext(ioDispatcher) {
                runCatching {
                    repository.writeText(uri, content)
                    val name = repository.displayName(uri)
                    val canWrite = repository.canWrite(uri)
                    val safeContent = repository.sanitizeText(content)
                    if (switchCurrentFile) {
                        repository.rememberRecentFile(
                            uri,
                            name,
                            canWrite,
                            repository.buildPreviewSnippet(safeContent),
                        )
                    }
                    Triple(name, safeContent, canWrite)
                }
            }
            result.onSuccess { (name, savedContent, canWrite) ->
                val pending = _state.value.pendingNavigation
                _state.update {
                    if (switchCurrentFile) {
                        it.copy(
                            currentUri = uri,
                            displayName = name,
                            content = savedContent,
                            draftContent = savedContent,
                            previewContent = null,
                            isEditing = false,
                            readingFontScale = 1f,
                            readingScrollFraction = 0f,
                            pendingEditScrollFraction = null,
                            hasUnsavedChanges = false,
                            draftUpdatedAt = null,
                            canWriteCurrentFile = canWrite,
                            isLoading = false,
                            recentFiles = repository.recentFiles(),
                            pendingNavigation = null,
                            isResolvingNavigation = false,
                            message = successMessage,
                        )
                    } else {
                        it.copy(isLoading = false, message = successMessage)
                    }
                }
                if (switchCurrentFile) cancelDraftSaveAndClear()
                if (switchCurrentFile && pending != null) performNavigation(pending)
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        isResolvingNavigation = false,
                        message = error.saveDocumentMessage(),
                    )
                }
            }
        }
    }

    fun suggestedFileName(): String {
        val name = _state.value.displayName.ifBlank { "ReadMD-${System.currentTimeMillis()}.md" }
        return suggestedReadMDFileName(name)
    }

    private fun currentDraftSnapshot(): DraftSnapshot? {
        val current = _state.value
        if (!current.hasUnsavedChanges) return null
        return DraftSnapshot(
            currentUri = current.currentUri?.toString(),
            displayName = current.displayName,
            content = current.content,
            draftContent = current.draftContent,
            canWriteCurrentFile = current.canWriteCurrentFile,
            updatedAt = current.draftUpdatedAt ?: System.currentTimeMillis(),
        )
    }

    private fun cancelDraftSaveAndClear() {
        draftSaveJob?.cancel()
        draftSaveJob = null
        repository.clearDraft()
    }

    companion object {
        private const val DRAFT_SAVE_DEBOUNCE_MS = 750L

        fun factory(repository: DocumentStore): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(ReadMDViewModel::class.java))
                    return ReadMDViewModel(repository) as T
                }
            }
        }
    }
}
