package com.andyxu.readmd.data

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset
import org.json.JSONArray
import org.json.JSONObject

class DocumentRepository(private val context: Context) {
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

    fun readText(uri: Uri): String {
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法打开文件输入流" }
            BufferedReader(InputStreamReader(input, Charset.forName("UTF-8"))).use { reader ->
                return reader.readText()
            }
        }
    }

    fun writeText(uri: Uri, content: String) {
        resolver.openOutputStream(uri, "wt").use { output ->
            requireNotNull(output) { "无法打开文件输出流" }
            output.write(content.toByteArray(Charsets.UTF_8))
            output.flush()
        }
    }

    fun displayName(uri: Uri): String {
        return queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "未命名.md"
    }

    fun canWrite(uri: Uri): Boolean {
        val persisted = resolver.persistedUriPermissions.firstOrNull { it.uri == uri }
        return persisted?.isWritePermission == true
    }

    fun rememberRecentFile(uri: Uri, displayName: String, canWrite: Boolean) {
        val current = recentFiles().filterNot { it.uri == uri.toString() }.toMutableList()
        current.add(
            index = 0,
            element = RecentFile(
                uri = uri.toString(),
                displayName = displayName,
                lastOpenedAt = System.currentTimeMillis(),
                canWrite = canWrite,
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

    private fun saveRecentFiles(files: List<RecentFile>) {
        val array = JSONArray()
        files.forEach { file ->
            array.put(
                JSONObject()
                    .put("uri", file.uri)
                    .put("displayName", file.displayName)
                    .put("lastOpenedAt", file.lastOpenedAt)
                    .put("canWrite", file.canWrite),
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

    private companion object {
        const val KEY_RECENT_FILES = "recent_files"
        const val KEY_ELDER_MODE = "elder_mode"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_LINE_HEIGHT_SCALE = "line_height_scale"
        const val KEY_DRAFT = "draft"
    }
}
