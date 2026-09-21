package com.andyxu.readmd

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.andyxu.readmd.data.DocumentState
import com.andyxu.readmd.data.ReaderSettings
import com.andyxu.readmd.data.RecentFile
import com.andyxu.readmd.ui.theme.ReadMDTheme
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val recent = RecentFile(
        uri = "content://test/note.md",
        displayName = "note.md",
        lastOpenedAt = 1L,
        canWrite = true,
        previewSnippet = "项目记录",
    )

    @Test
    fun blankSearch_showsRecentFileOnlyOnce() {
        setHomeContent(DocumentState(recentFiles = listOf(recent)))

        composeRule.onAllNodesWithText("note.md").assertCountEquals(1)
        composeRule.onNodeWithText("最近笔记").assertIsDisplayed()
    }

    @Test
    fun unmatchedSearch_showsSpecificEmptyStateWithoutRecentList() {
        setHomeContent(DocumentState(searchQuery = "不存在", recentFiles = listOf(recent)))

        composeRule.onNodeWithText("未找到匹配笔记").assertIsDisplayed()
        composeRule.onAllNodesWithText("note.md").assertCountEquals(0)
    }

    @Test
    fun elderModeAtMaximumScale_keepsPrimaryActionsAvailable() {
        setHomeContent(
            DocumentState(
                settings = ReaderSettings(elderMode = true, fontScale = 1.55f),
            ),
        )

        composeRule.onNodeWithText("导入").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("新建笔记").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("设置").assertIsDisplayed()
    }

    private fun setHomeContent(state: DocumentState) {
        composeRule.setContent {
            ReadMDTheme(
                darkTheme = state.settings.darkMode,
                elderMode = state.settings.elderMode,
            ) {
                ReadMDHomeScreen(
                    state = state,
                    snackbarHostState = SnackbarHostState(),
                    onOpenSettings = {},
                    onImport = {},
                    onCreateNew = {},
                    onSearchChange = {},
                    onOpenRecent = {},
                    onClearRecents = {},
                )
            }
        }
    }
}
