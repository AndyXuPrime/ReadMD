package com.andyxu.readmd

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andyxu.readmd.data.DocumentState
import com.andyxu.readmd.data.RecentFile
import com.andyxu.readmd.data.SaveTarget
import com.andyxu.readmd.file.OpenMarkdownDocument
import com.andyxu.readmd.markdown.MarkdownPreview
import com.andyxu.readmd.markdown.countSearchMatches
import com.andyxu.readmd.ui.theme.ReadMDTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ReadMDViewModel = viewModel()
            val state by viewModel.state.collectAsState()
            ReadMDTheme(elderMode = state.settings.elderMode) {
                ReadMDApp(
                    state = state,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
fun ReadMDApp(
    state: DocumentState,
    viewModel: ReadMDViewModel,
) {
    val openDocumentLauncher = rememberLauncherForActivityResult(OpenMarkdownDocument()) { document ->
        if (document != null) {
            viewModel.openPickedDocument(document)
        }
    }
    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri: Uri? ->
        val target = state.pendingSaveTarget
        if (uri != null && target != null) {
            viewModel.writeCreatedDocument(uri, target)
        } else {
            viewModel.clearPendingSaveTarget()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var showDiscardDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.pendingSaveTarget) {
        val target = state.pendingSaveTarget ?: return@LaunchedEffect
        val suggestedName = when (target) {
            SaveTarget.Export -> viewModel.suggestedFileName().replaceBeforeLast(".", "export", missingDelimiterValue = "export.md")
            else -> viewModel.suggestedFileName()
        }
        createDocumentLauncher.launch(suggestedName)
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("放弃修改？") },
            text = { Text("当前编辑内容尚未保存，放弃后将恢复到上次保存的内容。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        viewModel.discardChanges()
                    },
                ) {
                    Text("放弃")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("继续编辑")
                }
            },
        )
    }

    ReadMDScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onOpenFile = { openDocumentLauncher.launch(Unit) },
        onNewFile = viewModel::newUnsavedDocument,
        onEdit = viewModel::enterEditMode,
        onPreview = viewModel::previewDraft,
        onSave = viewModel::saveCurrentFile,
        onSaveAs = viewModel::requestSaveAs,
        onExport = viewModel::requestExport,
        onDraftChange = viewModel::updateDraft,
        onToggleElderMode = viewModel::toggleElderMode,
        onIncreaseFont = viewModel::increaseFont,
        onDecreaseFont = viewModel::decreaseFont,
        onSearchChange = viewModel::updateSearch,
        onRecentFileClick = viewModel::openRecentFile,
        onClearRecentFiles = viewModel::clearRecentFiles,
        onDiscard = {
            if (state.hasUnsavedChanges) {
                showDiscardDialog = true
            } else {
                viewModel.discardChanges()
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadMDScreen(
    state: DocumentState,
    snackbarHostState: SnackbarHostState,
    onOpenFile: () -> Unit,
    onNewFile: () -> Unit,
    onEdit: () -> Unit,
    onPreview: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onExport: () -> Unit,
    onDraftChange: (String) -> Unit,
    onToggleElderMode: () -> Unit,
    onIncreaseFont: () -> Unit,
    onDecreaseFont: () -> Unit,
    onSearchChange: (String) -> Unit,
    onRecentFileClick: (String) -> Unit,
    onClearRecentFiles: () -> Unit,
    onDiscard: () -> Unit,
) {
    val elderMode = state.settings.elderMode
    val bodySize = if (elderMode) 22.sp else 16.sp
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ReadMD")
                        Text(
                            text = state.displayName + if (state.hasUnsavedChanges) " *" else "",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onToggleElderMode) {
                        Text(if (elderMode) "普通" else "大字")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ActionPanel(
                    state = state,
                    onOpenFile = onOpenFile,
                    onNewFile = onNewFile,
                    onEdit = onEdit,
                    onPreview = onPreview,
                    onSave = onSave,
                    onSaveAs = onSaveAs,
                    onExport = onExport,
                    onDiscard = onDiscard,
                    onIncreaseFont = onIncreaseFont,
                    onDecreaseFont = onDecreaseFont,
                )
            }

            if (!state.isEditing) {
                item {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchChange,
                        label = { Text("文内搜索") },
                        placeholder = { Text("输入关键词") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = bodySize),
                    )
                    val matches = countSearchMatches(state.activeContent, state.searchQuery)
                    if (state.searchQuery.isNotBlank()) {
                        Text(
                            text = "找到 $matches 处匹配",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }

            item {
                DocumentBody(
                    state = state,
                    onDraftChange = onDraftChange,
                )
            }

            if (state.recentFiles.isNotEmpty()) {
                item {
                    RecentFilesPanel(
                        files = state.recentFiles,
                        elderMode = elderMode,
                        onRecentFileClick = onRecentFileClick,
                        onClearRecentFiles = onClearRecentFiles,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionPanel(
    state: DocumentState,
    onOpenFile: () -> Unit,
    onNewFile: () -> Unit,
    onEdit: () -> Unit,
    onPreview: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onExport: () -> Unit,
    onDiscard: () -> Unit,
    onIncreaseFont: () -> Unit,
    onDecreaseFont: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onOpenFile, enabled = !state.isLoading) {
                    Text("打开")
                }
                OutlinedButton(onClick = onNewFile, enabled = !state.isLoading) {
                    Text("新建")
                }
                if (state.isEditing) {
                    Button(onClick = onPreview, enabled = !state.isLoading) {
                        Text("预览")
                    }
                } else {
                    OutlinedButton(onClick = onEdit, enabled = !state.isLoading) {
                        Text("编辑")
                    }
                }
                Button(onClick = onSave, enabled = !state.isLoading) {
                    Text("保存")
                }
                OutlinedButton(onClick = onSaveAs, enabled = !state.isLoading) {
                    Text("另存")
                }
                OutlinedButton(onClick = onExport, enabled = !state.isLoading) {
                    Text("导出")
                }
                OutlinedButton(onClick = onDiscard, enabled = !state.isLoading && state.hasUnsavedChanges) {
                    Text("放弃")
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(onClick = onDecreaseFont, label = { Text("字小") })
                AssistChip(onClick = onIncreaseFont, label = { Text("字大") })
                AssistChip(
                    onClick = {},
                    label = { Text(if (state.canWriteCurrentFile) "可写文件" else "需另存") },
                )
                if (state.isLoading) {
                    AssistChip(onClick = {}, label = { Text("处理中") })
                }
            }
        }
    }
}

@Composable
private fun DocumentBody(
    state: DocumentState,
    onDraftChange: (String) -> Unit,
) {
    val elderMode = state.settings.elderMode
    val fontScale = state.settings.fontScale * if (elderMode) 1.25f else 1f
    val bodyHeight = if (state.isEditing) 520.dp else 360.dp
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !state.isEditing,
                    onClick = {},
                    label = { Text("阅读") },
                )
                FilterChip(
                    selected = state.isEditing,
                    onClick = {},
                    label = { Text("编辑") },
                )
            }
            if (state.isEditing) {
                OutlinedTextField(
                    value = state.draftContent,
                    onValueChange = onDraftChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = bodyHeight),
                    label = { Text("Markdown 内容") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp * fontScale,
                        lineHeight = 24.sp * fontScale,
                    ),
                )
            } else if (state.activeContent.isBlank()) {
                EmptyState(fontScale = fontScale)
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    MarkdownPreview(
                        content = state.activeContent,
                        fontScale = fontScale,
                        lineHeightScale = state.settings.lineHeightScale,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = bodyHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(fontScale: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "还没有打开备忘录",
            style = MaterialTheme.typography.titleLarge,
            fontSize = 22.sp * fontScale,
        )
        Text(
            text = "可以打开手机里的 Markdown 文件，也可以新建一个本地备忘录。",
            fontSize = 16.sp * fontScale,
            lineHeight = 24.sp * fontScale,
        )
    }
}

@Composable
private fun RecentFilesPanel(
    files: List<RecentFile>,
    elderMode: Boolean,
    onRecentFileClick: (String) -> Unit,
    onClearRecentFiles: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("最近打开", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onClearRecentFiles) {
                    Text("清空")
                }
            }
            files.forEach { file ->
                RecentFileRow(
                    file = file,
                    elderMode = elderMode,
                    onClick = { onRecentFileClick(file.uri) },
                )
            }
        }
    }
}

@Composable
private fun RecentFileRow(
    file: RecentFile,
    elderMode: Boolean,
    onClick: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = false, role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = file.displayName,
            fontSize = if (elderMode) 20.sp else 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${dateFormat.format(Date(file.lastOpenedAt))} · ${if (file.canWrite) "可写" else "只读/需另存"}",
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReadMDScreenPreview() {
    ReadMDTheme {
        ReadMDScreen(
            state = DocumentState(
                displayName = "示例.md",
                content = "# ReadMD\n\n- 打开文件\n- 编辑保存\n\n```kotlin\nprintln(\"hello\")\n```",
            ),
            snackbarHostState = SnackbarHostState(),
            onOpenFile = {},
            onNewFile = {},
            onEdit = {},
            onPreview = {},
            onSave = {},
            onSaveAs = {},
            onExport = {},
            onDraftChange = {},
            onToggleElderMode = {},
            onIncreaseFont = {},
            onDecreaseFont = {},
            onSearchChange = {},
            onRecentFileClick = {},
            onClearRecentFiles = {},
            onDiscard = {},
        )
    }
}
