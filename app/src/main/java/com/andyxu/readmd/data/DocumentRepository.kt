package com.andyxu.readmd.data

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.AtomicFile
import androidx.core.content.edit
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class DocumentRepository(private val context: Context) : DocumentStore {
    private companion object {
        const val KEY_RECENT_FILES = "recent_files"
        const val KEY_ELDER_MODE = "elder_mode"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_LINE_HEIGHT_SCALE = "line_height_scale"
        const val KEY_LEGACY_DRAFT = "draft"
        const val DRAFT_FILE_NAME = "readmd-draft.json"
    }

    private val resolver = context.contentResolver
    private val prefs = context.getSharedPreferences("readmd", Context.MODE_PRIVATE)
    private val draftFile = AtomicFile(File(context.noBackupFilesDir, DRAFT_FILE_NAME))

    init {
        migrateLegacyDraft()
    }

    override fun persistUriPermission(uri: Uri, grantFlags: Int) {
        val supportedFlags = grantFlags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        if (supportedFlags != 0) {
            runCatching { resolver.takePersistableUriPermission(uri, supportedFlags) }
            if (supportedFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) {
                runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            }
            if (supportedFlags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0) {
                runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            }
        }
    }

    override fun displayName(uri: Uri): String {
        return queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "未命名.md"
    }

    override fun fileSize(uri: Uri): Long? {
        var cursor: Cursor? = null
        return try {
            cursor = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
            } else {
                null
            }
        } finally {
            cursor?.close()
        }
    }

    override fun mimeType(uri: Uri): String? {
        return resolver.getType(uri)
    }

    override fun readText(uri: Uri, maxBytes: Long): String {
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法打开文件输入流" }
            val bytes = readAllBytesLimited(input, maxBytes)
            return decodeDocumentText(bytes)
        }
    }

    override fun writeText(uri: Uri, content: String) {
        resolver.openOutputStream(uri, "wt").use { output ->
            requireNotNull(output) { "无法打开文件输出流" }
            output.write(content.toByteArray(Charsets.UTF_8))
            output.flush()
        }
    }

    override fun canWrite(uri: Uri): Boolean {
        val persisted = resolver.persistedUriPermissions.firstOrNull { it.uri == uri }
        if (persisted?.isWritePermission == true) return true
        if (uri.scheme == "file") return uri.path?.let { File(it).canWrite() } == true
        return documentSupportsWrite(uri)
    }

    override fun rememberRecentFile(
        uri: Uri,
        displayName: String,
        canWrite: Boolean,
        previewSnippet: String,
    ) {
        val current = recentFiles().filterNot { it.uri == uri.toString() }.toMutableList()
        current.add(
            index = 0,
            element = RecentFile(
                uri = uri.toString(),
                displayName = sanitizeText(displayName),
                lastOpenedAt = System.currentTimeMillis(),
                canWrite = canWrite,
                previewSnippet = sanitizeText(previewSnippet),
            ),
        )
        saveRecentFiles(current.take(MAX_RECENT_FILES))
    }

    override fun recentFiles(): List<RecentFile> {
        val raw = prefs.getString(KEY_RECENT_FILES, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        RecentFile(
                            uri = item.getString("uri"),
                            displayName = sanitizeText(item.getString("displayName")),
                            lastOpenedAt = item.optLong("lastOpenedAt"),
                            canWrite = item.optBoolean("canWrite"),
                            previewSnippet = sanitizeText(item.optString("previewSnippet")),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    override fun clearRecentFiles() {
        prefs.edit { remove(KEY_RECENT_FILES) }
    }

    override fun forgetRecentFile(uri: Uri) {
        val remaining = recentFiles().filterNot { it.uri == uri.toString() }
        saveRecentFiles(remaining)
    }

    override fun readerSettings(): ReaderSettings {
        return ReaderSettings(
            elderMode = prefs.getBoolean(KEY_ELDER_MODE, false),
            darkMode = prefs.getBoolean(KEY_DARK_MODE, false),
            fontScale = prefs.getFloat(KEY_FONT_SCALE, 1f)
                .coerceIn(ReaderSettings.MIN_FONT_SCALE, ReaderSettings.MAX_FONT_SCALE),
            lineHeightScale = prefs.getFloat(KEY_LINE_HEIGHT_SCALE, 1f)
                .coerceIn(ReaderSettings.MIN_LINE_HEIGHT_SCALE, ReaderSettings.MAX_LINE_HEIGHT_SCALE),
        )
    }

    override fun saveReaderSettings(settings: ReaderSettings) {
        prefs.edit {
            putBoolean(KEY_ELDER_MODE, settings.elderMode)
            putBoolean(KEY_DARK_MODE, settings.darkMode)
            putFloat(
                KEY_FONT_SCALE,
                settings.fontScale.coerceIn(ReaderSettings.MIN_FONT_SCALE, ReaderSettings.MAX_FONT_SCALE),
            )
            putFloat(
                KEY_LINE_HEIGHT_SCALE,
                settings.lineHeightScale.coerceIn(
                    ReaderSettings.MIN_LINE_HEIGHT_SCALE,
                    ReaderSettings.MAX_LINE_HEIGHT_SCALE,
                ),
            )
        }
    }

    override fun saveDraft(snapshot: DraftSnapshot) {
        val value = JSONObject()
            .put("currentUri", snapshot.currentUri)
            .put("displayName", sanitizeText(snapshot.displayName))
            .put("content", sanitizeText(snapshot.content))
            .put("draftContent", sanitizeText(snapshot.draftContent))
            .put("canWriteCurrentFile", snapshot.canWriteCurrentFile)
            .put("updatedAt", snapshot.updatedAt)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val output = draftFile.startWrite()
        try {
            output.write(value)
            output.flush()
            draftFile.finishWrite(output)
        } catch (error: Exception) {
            draftFile.failWrite(output)
            throw error
        }
    }

    override fun draftSnapshot(): DraftSnapshot? {
        if (!draftFile.baseFile.exists()) return null
        val raw = runCatching { draftFile.readFully().toString(Charsets.UTF_8) }.getOrNull() ?: return null
        return parseDraft(raw)
    }

    override fun clearDraft() {
        draftFile.delete()
    }

    override fun buildPreviewSnippet(content: String, maxChars: Int): String {
        val normalized = sanitizeText(content)
            .lines()
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(4)
            .joinToString(" ")
        val trimmed = if (normalized.length > maxChars) normalized.take(maxChars) + "…" else normalized
        return trimmed.ifBlank { "空白文档" }
    }

    private fun saveRecentFiles(files: List<RecentFile>) {
        val array = JSONArray()
        files.forEach { file ->
            array.put(
                JSONObject()
                    .put("uri", file.uri)
                    .put("displayName", sanitizeText(file.displayName))
                    .put("lastOpenedAt", file.lastOpenedAt)
                    .put("canWrite", file.canWrite)
                    .put("previewSnippet", sanitizeText(file.previewSnippet)),
            )
        }
        prefs.edit { putString(KEY_RECENT_FILES, array.toString()) }
    }

    private fun migrateLegacyDraft() {
        val raw = prefs.getString(KEY_LEGACY_DRAFT, null) ?: return
        if (draftFile.baseFile.exists()) {
            prefs.edit { remove(KEY_LEGACY_DRAFT) }
            return
        }
        val snapshot = parseDraft(raw)
        if (snapshot == null) {
            prefs.edit { remove(KEY_LEGACY_DRAFT) }
            return
        }
        runCatching { saveDraft(snapshot) }
            .onSuccess { prefs.edit { remove(KEY_LEGACY_DRAFT) } }
    }

    private fun parseDraft(raw: String): DraftSnapshot? {
        return runCatching {
            val value = JSONObject(raw)
            DraftSnapshot(
                currentUri = value.optString("currentUri").takeUnless { it.isBlank() || it == "null" },
                displayName = sanitizeText(value.optString("displayName", "自动恢复草稿.md")),
                content = sanitizeText(value.optString("content")),
                draftContent = sanitizeText(value.optString("draftContent")),
                canWriteCurrentFile = value.optBoolean("canWriteCurrentFile"),
                updatedAt = value.optLong("updatedAt"),
            )
        }.getOrNull()
    }

    private fun queryDisplayName(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        } finally {
            cursor?.close()
        }
    }

    private fun documentSupportsWrite(uri: Uri): Boolean {
        var cursor: Cursor? = null
        return try {
            cursor = resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
                if (index >= 0 && !cursor.isNull(index)) {
                    val flags = cursor.getInt(index)
                    flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0
                } else {
                    false
                }
            } else {
                false
            }
        } catch (_: Exception) {
            false
        } finally {
            cursor?.close()
        }
    }

    override fun sanitizeText(text: String): String {
        return sanitizeDocumentText(text)
    }
}
