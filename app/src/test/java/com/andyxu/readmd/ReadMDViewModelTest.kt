package com.andyxu.readmd

import android.content.Intent
import android.net.Uri
import com.andyxu.readmd.data.DraftSnapshot
import com.andyxu.readmd.data.MAX_DOCUMENT_BYTES
import com.andyxu.readmd.data.NavigationTarget
import com.andyxu.readmd.data.RecentFile
import com.andyxu.readmd.data.SaveTarget
import com.andyxu.readmd.file.PickedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReadMDViewModelTest {
    private lateinit var dispatcher: TestDispatcher

    @Before
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun updateDraft_debouncesAtomicDraftWrites() {
        val store = FakeDocumentStore()
        val viewModel = ReadMDViewModel(store, dispatcher)

        viewModel.requestNewDocument()
        viewModel.updateDraft("first")
        viewModel.updateDraft("second")
        dispatcher.scheduler.advanceTimeBy(749)
        dispatcher.scheduler.runCurrent()

        assertNull(store.savedDraft)

        dispatcher.scheduler.advanceTimeBy(1)
        dispatcher.scheduler.runCurrent()

        assertEquals(1, store.draftSaveCount)
        assertEquals("second", store.savedDraft?.draftContent)
    }

    @Test
    fun restoredDraft_opensInEditorWithoutLosingContent() {
        val store = FakeDocumentStore(
            restoredDraft = DraftSnapshot(
                currentUri = null,
                displayName = "恢复.md",
                content = "原文",
                draftContent = "未保存修改",
                canWriteCurrentFile = false,
                updatedAt = 123L,
            ),
        )

        val state = ReadMDViewModel(store, dispatcher).state.value

        assertTrue(state.isEditing)
        assertTrue(state.hasUnsavedChanges)
        assertEquals("未保存修改", state.draftContent)
    }

    @Test
    fun importWithUnsavedChanges_requiresExplicitChoice() {
        val store = FakeDocumentStore()
        val viewModel = ReadMDViewModel(store, dispatcher)
        viewModel.requestNewDocument()

        viewModel.requestImportDocument()

        assertEquals(NavigationTarget.ImportDocument, viewModel.state.value.pendingNavigation?.target)
        assertFalse(viewModel.state.value.shouldLaunchDocumentPicker)

        viewModel.continueEditing()

        assertNull(viewModel.state.value.pendingNavigation)
        assertTrue(viewModel.state.value.isEditing)
    }

    @Test
    fun discardThenImport_clearsDraftAndLaunchesPicker() {
        val store = FakeDocumentStore()
        val viewModel = ReadMDViewModel(store, dispatcher)
        viewModel.requestNewDocument()
        viewModel.updateDraft("需要确认")
        viewModel.requestImportDocument()

        viewModel.discardChangesAndContinue()

        assertFalse(viewModel.state.value.hasUnsavedChanges)
        assertTrue(viewModel.state.value.shouldLaunchDocumentPicker)
        assertTrue(store.draftClearCount > 0)
    }

    @Test
    fun newAndRecentNavigation_shareTheUnsavedChangesGuard() {
        val recentUri = "content://test/recent.md"

        val newDocumentViewModel = ReadMDViewModel(FakeDocumentStore(), dispatcher)
        newDocumentViewModel.requestNewDocument()
        newDocumentViewModel.updateDraft("尚未保存")
        newDocumentViewModel.requestNewDocument()
        assertEquals(
            NavigationTarget.NewDocument,
            newDocumentViewModel.state.value.pendingNavigation?.target,
        )

        val recentViewModel = ReadMDViewModel(FakeDocumentStore(), dispatcher)
        recentViewModel.requestNewDocument()
        recentViewModel.updateDraft("尚未保存")
        recentViewModel.requestOpenRecentFile(recentUri)
        assertEquals(
            NavigationTarget.RecentFile,
            recentViewModel.state.value.pendingNavigation?.target,
        )
        assertEquals(recentUri, recentViewModel.state.value.pendingNavigation?.recentUri)
    }

    @Test
    fun backToHome_usesTheUnsavedChangesGuardAfterPreview() {
        val viewModel = ReadMDViewModel(FakeDocumentStore(), dispatcher)
        viewModel.requestNewDocument()
        viewModel.updateDraft("尚未保存")
        viewModel.previewDraft(showMessage = false)

        viewModel.handleBack()

        assertEquals(NavigationTarget.Home, viewModel.state.value.pendingNavigation?.target)
        assertTrue(viewModel.state.value.hasUnsavedChanges)
    }

    @Test
    fun readingZoom_doesNotChangeGlobalFontScale() {
        val store = FakeDocumentStore()
        val viewModel = ReadMDViewModel(store, dispatcher)
        viewModel.setFontScale(1.2f)

        viewModel.setReadingFontScale(1.55f)

        assertEquals(1.2f, viewModel.state.value.settings.fontScale)
        assertEquals(1.55f, viewModel.state.value.readingFontScale)
    }

    @Test
    fun readingAndLineScales_areClampedToHistoricalLimits() {
        val viewModel = ReadMDViewModel(FakeDocumentStore(), dispatcher)

        viewModel.setReadingFontScale(9f)
        viewModel.setLineHeightScale(-2f)

        assertEquals(1.55f, viewModel.state.value.readingFontScale)
        assertEquals(0.85f, viewModel.state.value.settings.lineHeightScale)
    }

    @Test
    fun saveCurrentFile_writesOriginalUriAndClearsDraftState() {
        val store = FakeDocumentStore()
        val viewModel = ReadMDViewModel(store, dispatcher)
        val uri = Uri.parse("content://test/original.md")
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        viewModel.openPickedDocument(PickedDocument(uri, flags))
        dispatcher.scheduler.runCurrent()
        viewModel.enterEditMode()
        viewModel.updateDraft("# 修改后")

        viewModel.saveCurrentFile()
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(uri.toString() to "# 修改后"), store.writes)
        assertFalse(viewModel.state.value.hasUnsavedChanges)
        assertFalse(viewModel.state.value.isEditing)
        assertEquals("# 修改后", viewModel.state.value.content)
    }

    @Test
    fun newDocumentSave_requestsSaveAsAndPersistsReturnedGrant() {
        val store = FakeDocumentStore()
        val viewModel = ReadMDViewModel(store, dispatcher)
        viewModel.requestNewDocument()
        viewModel.updateDraft("# 新内容")

        viewModel.saveCurrentFile()

        assertEquals(SaveTarget.SaveAs, viewModel.state.value.pendingSaveTarget)

        val uri = Uri.parse("content://test/new.md")
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        viewModel.writeCreatedDocument(PickedDocument(uri, flags), SaveTarget.SaveAs)
        dispatcher.scheduler.runCurrent()

        assertEquals(uri.toString() to flags, store.persistedPermissions.last())
        assertEquals(uri.toString() to "# 新内容", store.writes.last())
        assertEquals(uri, viewModel.state.value.currentUri)
        assertFalse(viewModel.state.value.hasUnsavedChanges)
    }

    @Test
    fun saveFailure_keepsUnsavedContentAndOffersSaveAsRecovery() {
        val store = FakeDocumentStore()
        val viewModel = ReadMDViewModel(store, dispatcher)
        val uri = Uri.parse("content://test/original.md")
        viewModel.openPickedDocument(
            PickedDocument(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
        )
        dispatcher.scheduler.runCurrent()
        viewModel.enterEditMode()
        viewModel.updateDraft("不能丢失")
        store.writeFailure = SecurityException("Permission Denial")

        viewModel.saveCurrentFile()
        dispatcher.scheduler.runCurrent()

        assertTrue(viewModel.state.value.hasUnsavedChanges)
        assertEquals("不能丢失", viewModel.state.value.draftContent)
        assertTrue(viewModel.state.value.message?.contains("另存") == true)
    }

    @Test
    fun knownOversizeDocument_isRejectedBeforeReadingContent() {
        val store = FakeDocumentStore().apply {
            documentSize = MAX_DOCUMENT_BYTES + 1
        }
        val viewModel = ReadMDViewModel(store, dispatcher)

        viewModel.openPickedDocument(
            PickedDocument(Uri.parse("content://test/large.md"), Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
        dispatcher.scheduler.runCurrent()

        assertEquals(0, store.readCount)
        assertNull(viewModel.state.value.currentUri)
        assertTrue(viewModel.state.value.message?.contains("2MB") == true)
    }

    @Test
    fun expiredRecentPermission_removesOnlyTheStaleRecentEntry() {
        val staleUri = Uri.parse("content://test/stale.md")
        val store = FakeDocumentStore().apply {
            recents += RecentFile(staleUri.toString(), "stale.md", 1L, false, "")
            readFailure = SecurityException("Permission Denial")
        }
        val viewModel = ReadMDViewModel(store, dispatcher)

        viewModel.requestOpenRecentFile(staleUri.toString())
        dispatcher.scheduler.runCurrent()

        assertEquals(listOf(staleUri.toString()), store.forgottenUris)
        assertTrue(viewModel.state.value.recentFiles.isEmpty())
        assertNull(viewModel.state.value.currentUri)
        assertTrue(viewModel.state.value.message?.contains("失效") == true)
    }
}
