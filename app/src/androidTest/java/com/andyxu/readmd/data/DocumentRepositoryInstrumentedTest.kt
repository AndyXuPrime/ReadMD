package com.andyxu.readmd.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.provider.ProviderTestRule
import java.io.File
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentRepositoryInstrumentedTest {
    @get:Rule
    val providerRule: ProviderTestRule = ProviderTestRule.Builder(
        FakeDocumentProvider::class.java,
        "com.andyxu.readmd.test.documents",
    ).build()

    private lateinit var targetContext: Context
    private lateinit var repositoryContext: Context
    private lateinit var backingFile: File
    private lateinit var repository: DocumentRepository
    private val uri = Uri.parse("content://com.andyxu.readmd.test.documents/note.md")

    @Before
    fun setUp() {
        targetContext = ApplicationProvider.getApplicationContext()
        targetContext.getSharedPreferences("readmd", Context.MODE_PRIVATE).edit().clear().commit()
        File(targetContext.noBackupFilesDir, "readmd-draft.json").delete()
        backingFile = File(targetContext.cacheDir, "repository-test.md").apply {
            writeText("# 初始内容")
        }
        FakeDocumentProvider.backingFile = backingFile
        repositoryContext = object : ContextWrapper(targetContext) {
            override fun getContentResolver() = providerRule.resolver
        }
        repository = DocumentRepository(repositoryContext)
    }

    @After
    fun tearDown() {
        backingFile.delete()
        File(targetContext.noBackupFilesDir, "readmd-draft.json").delete()
        targetContext.getSharedPreferences("readmd", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun fakeContentResolver_supportsReadWriteMetadataAndWriteCapability() {
        assertEquals("note.md", repository.displayName(uri))
        assertEquals(backingFile.length(), repository.fileSize(uri))
        assertEquals("# 初始内容", repository.readText(uri))
        assertTrue(repository.canWrite(uri))

        repository.writeText(uri, "# 已更新")

        assertEquals("# 已更新", backingFile.readText())
    }

    @Test
    fun draft_usesAtomicNoBackupFileInsteadOfSharedPreferences() {
        val snapshot = DraftSnapshot(null, "草稿.md", "原文", "修改", false, 42L)

        repository.saveDraft(snapshot)

        assertEquals(snapshot, repository.draftSnapshot())
        assertFalse(
            targetContext.getSharedPreferences("readmd", Context.MODE_PRIVATE).contains("draft"),
        )

        repository.clearDraft()
        assertNull(repository.draftSnapshot())
    }

    @Test
    fun legacySharedPreferencesDraft_isMigratedWithoutDataLoss() {
        val legacy = DraftSnapshot(null, "旧草稿.md", "原文", "未保存修改", false, 84L)
        val raw = JSONObject()
            .put("currentUri", legacy.currentUri)
            .put("displayName", legacy.displayName)
            .put("content", legacy.content)
            .put("draftContent", legacy.draftContent)
            .put("canWriteCurrentFile", legacy.canWriteCurrentFile)
            .put("updatedAt", legacy.updatedAt)
            .toString()
        File(targetContext.noBackupFilesDir, "readmd-draft.json").delete()
        targetContext.getSharedPreferences("readmd", Context.MODE_PRIVATE)
            .edit()
            .putString("draft", raw)
            .commit()

        val migratedRepository = DocumentRepository(repositoryContext)

        assertEquals(legacy, migratedRepository.draftSnapshot())
        assertFalse(
            targetContext.getSharedPreferences("readmd", Context.MODE_PRIVATE).contains("draft"),
        )
    }

    @Test
    fun recentFiles_areDeduplicatedAndCappedAtTwenty() {
        repeat(21) { index ->
            repository.rememberRecentFile(
                Uri.parse("content://com.andyxu.readmd.test.documents/$index.md"),
                "$index.md",
                true,
                "preview $index",
            )
        }
        repository.rememberRecentFile(uri, "note.md", true, "latest")
        repository.rememberRecentFile(uri, "note.md", true, "latest again")

        val recents = repository.recentFiles()

        assertEquals(MAX_RECENT_FILES, recents.size)
        assertEquals(uri.toString(), recents.first().uri)
        assertEquals(1, recents.count { it.uri == uri.toString() })
    }
}

class FakeDocumentProvider : ContentProvider() {
    companion object {
        lateinit var backingFile: File
    }

    override fun onCreate() = true

    override fun getType(uri: Uri) = "text/markdown"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val columns = projection ?: arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
            DocumentsContract.Document.COLUMN_FLAGS,
        )
        return MatrixCursor(columns).apply {
            val row = arrayOfNulls<Any>(columns.size)
            columns.forEachIndexed { index, column ->
                row[index] = when (column) {
                    OpenableColumns.DISPLAY_NAME -> "note.md"
                    OpenableColumns.SIZE -> backingFile.length()
                    DocumentsContract.Document.COLUMN_FLAGS -> DocumentsContract.Document.FLAG_SUPPORTS_WRITE
                    else -> null
                }
            }
            addRow(row)
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        return ParcelFileDescriptor.open(backingFile, ParcelFileDescriptor.parseMode(mode))
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0
}
