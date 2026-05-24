package com.andyxu.readmd.data

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import org.json.JSONArray
import org.json.JSONObject

class DocumentRepository(private val context: Context) {
    private companion object {
        const val DEFAULT_MAX_READ_BYTES = 8L * 1024L * 1024L
        const val KEY_RECENT_FILES = "recent_files"
        const val KEY_ELDER_MODE = "elder_mode"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_LINE_HEIGHT_SCALE = "line_height_scale"
        const val KEY_DRAFT = "draft"
    }

    private val resolver = context.contentResolver
    private val prefs = context.getSharedPreferences("readmd", Context.MODE_PRIVATE)

    fun persistUriPermission(uri: Uri, grantFlags: Int) {
        val supportedFlags = grantFlags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        if (supportedFlags != 0) {
            runCatching { resolver.takePersistableUriPermission(uri, supportedFlags) }
        }
    }

    fun displayName(uri: Uri): String {
        return queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "未命名.md"
    }

    fun fileSize(uri: Uri): Long? {
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

    fun readText(uri: Uri, maxBytes: Long = DEFAULT_MAX_READ_BYTES): String {
        val charsets = listOf(
            Charsets.UTF_8,
            Charset.forName("UTF-16LE"),
            Charset.forName("UTF-16BE"),
            Charset.forName("GBK"),
        )
        var lastError: Throwable? = null
        for (charset in charsets) {
            runCatching {
                resolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "无法打开文件输入流" }
                    val bytes = readAllBytesLimited(input, maxBytes)
                    val decoder: CharsetDecoder = charset.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPLACE)
                        .onUnmappableCharacter(CodingErrorAction.REPLACE)
                    return decoder.decode(ByteBuffer.wrap(bytes)).toString()
                }
            }.onFailure { lastError = it }
        }
        throw IllegalStateException(lastError?.message ?: "无法读取文件内容")
    }

    fun writeText(uri: Uri, content: String) {
        resolver.openOutputStream(uri, "wt").use { output ->
            requireNotNull(output) { "无法打开文件输出流" }
            output.write(content.toByteArray(Charsets.UTF_8))
            output.flush()
        }
    }

    fun canWrite(uri: Uri): Boolean {
        val persisted = resolver.persistedUriPermissions.firstOrNull { it.uri == uri }
        return persisted?.isWritePermission == true
    }

    fun rememberRecentFile(uri: Uri, displayName: String, canWrite: Boolean) {
        rememberRecentFile(uri, displayName, canWrite, previewSnippet = "")
    }

    fun rememberRecentFile(
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
                displayName = displayName,
                lastOpenedAt = System.currentTimeMillis(),
                canWrite = canWrite,
                previewSnippet = previewSnippet,
            ),
        )
        saveRecentFiles(current.take(10))
    }

    fun recentFiles(): List<RecentFile> {
        val raw = prefs.getString(KEY_RECENT_FILES, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        RecentFile(
                            uri = item.getString("uri"),
                            displayName = item.getString("displayName"),
                            lastOpenedAt = item.optLong("lastOpenedAt"),
                            canWrite = item.optBoolean("canWrite"),
                            previewSnippet = item.optString("previewSnippet"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun clearRecentFiles() {
        prefs.edit().remove(KEY_RECENT_FILES).apply()
    }

    fun readerSettings(): ReaderSettings {
        return ReaderSettings(
            elderMode = prefs.getBoolean(KEY_ELDER_MODE, false),
            fontScale = prefs.getFloat(KEY_FONT_SCALE, 1f),
            lineHeightScale = prefs.getFloat(KEY_LINE_HEIGHT_SCALE, 1f),
        )
    }

    fun saveReaderSettings(settings: ReaderSettings) {
        prefs.edit()
            .putBoolean(KEY_ELDER_MODE, settings.elderMode)
            .putFloat(KEY_FONT_SCALE, settings.fontScale)
            .putFloat(KEY_LINE_HEIGHT_SCALE, settings.lineHeightScale)
            .apply()
    }

    fun saveDraft(snapshot: DraftSnapshot) {
        val value = JSONObject()
            .put("currentUri", snapshot.currentUri)
            .put("displayName", snapshot.displayName)
            .put("content", snapshot.content)
            .put("draftContent", snapshot.draftContent)
            .put("canWriteCurrentFile", snapshot.canWriteCurrentFile)
            .put("updatedAt", snapshot.updatedAt)
            .toString()
        prefs.edit().putString(KEY_DRAFT, value).apply()
    }

    fun draftSnapshot(): DraftSnapshot? {
        val raw = prefs.getString(KEY_DRAFT, null) ?: return null
        return runCatching {
            val value = JSONObject(raw)
            DraftSnapshot(
                currentUri = value.optString("currentUri").takeUnless { it.isBlank() || it == "null" },
                displayName = value.optString("displayName", "自动恢复草稿.md"),
                content = value.optString("content"),
                draftContent = value.optString("draftContent"),
                canWriteCurrentFile = value.optBoolean("canWriteCurrentFile"),
                updatedAt = value.optLong("updatedAt"),
            )
        }.getOrNull()
    }

    fun clearDraft() {
        prefs.edit().remove(KEY_DRAFT).apply()
    }

    fun buildPreviewSnippet(content: String, maxChars: Int = 160): String {
        val normalized = content.lines()
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
                    .put("displayName", file.displayName)
                    .put("lastOpenedAt", file.lastOpenedAt)
                    .put("canWrite", file.canWrite)
                    .put("previewSnippet", file.previewSnippet),
            )
        }
        prefs.edit().putString(KEY_RECENT_FILES, array.toString()).apply()
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

    private fun readAllBytesLimited(input: java.io.InputStream, maxBytes: Long): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(chunk)
            if (read <= 0) break
            total += read
            if (total > maxBytes) {
                throw IllegalStateException("文件过大，建议拆分后再打开")
            }
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }
}
