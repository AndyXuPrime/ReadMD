@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.andyxu.readmd

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.andyxu.readmd.data.DocumentState
import com.andyxu.readmd.data.RecentFile
import com.andyxu.readmd.file.OpenMarkdownDocument
import com.andyxu.readmd.markdown.MarkdownPreview
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
                ReadMDAppShell(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ReadMDAppShell(
    state: DocumentState,
    viewModel: ReadMDViewModel,
) {
    val isDocumentMode = state.currentUri != null || state.content.isNotBlank() || state.draftContent.isNotBlank()
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

    var showSettings by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.pendingSaveTarget) {
        val target = state.pendingSaveTarget ?: return@LaunchedEffect
        val suggestedName = viewModel.suggestedFileName()
        createDocumentLauncher.launch(
            if (target == com.andyxu.readmd.data.SaveTarget.Export) {
                suggestedName.replace(".md", ".export.md")
            } else {
                suggestedName
            },
        )
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    if (showSettings) {
        ReadMDSettingsSheet(
            fontScale = state.settings.fontScale,
            lineHeightScale = state.settings.lineHeightScale,
            elderMode = state.settings.elderMode,
            onDismiss = { showSettings = false },
            onFontScaleChange = viewModel::setFontScale,
            onLineHeightChange = viewModel::setLineHeightScale,
            onToggleElderMode = viewModel::toggleElderMode,
        )
    }

    if (isDocumentMode) {
        ReadMDDocumentScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onOpenHome = viewModel::closeDocument,
            onOpenSettings = { showSettings = true },
            onSave = viewModel::saveCurrentFile,
            onSaveAs = viewModel::requestSaveAs,
            onExport = viewModel::requestExport,
            onDraftChange = viewModel::updateDraft,
        )
    } else {
        ReadMDHomeScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onOpenSettings = { showSettings = true },
            onImport = { openDocumentLauncher.launch(Unit) },
            onCreateNew = viewModel::newUnsavedDocument,
            onSearchChange = viewModel::updateSearch,
            onOpenRecent = viewModel::openRecentFile,
            onClearRecents = viewModel::clearRecentFiles,
        )
    }
}

@Composable
private fun ReadMDHomeScreen(
    state: DocumentState,
    snackbarHostState: SnackbarHostState,
    onOpenSettings: () -> Unit,
    onImport: () -> Unit,
    onCreateNew: () -> Unit,
    onSearchChange: (String) -> Unit,
    onOpenRecent: (String) -> Unit,
    onClearRecents: () -> Unit,
) {
    val elderMode = state.settings.elderMode
    val textScale = uiTextScale(state.settings.elderMode, state.settings.fontScale)
    val spacing = uiSpacing(state.settings.lineHeightScale)
    val searchMatches = state.recentFiles.filter {
        val query = state.searchQuery.trim()
        query.isBlank() ||
            it.displayName.contains(query, ignoreCase = true) ||
            it.previewSnippet.contains(query, ignoreCase = true)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "ReadMD",
                        fontSize = appTextSize(elderMode, state.settings.fontScale, 26.sp),
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    TextButton(onClick = onOpenSettings) {
                        Text("设置", fontSize = appTextSize(elderMode, state.settings.fontScale, 16.sp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    ActionButton(text = "导入", fontScale = textScale, elderMode = elderMode, modifier = Modifier.weight(1f), onClick = onImport)
                    ActionButton(text = "新建", fontScale = textScale, elderMode = elderMode, modifier = Modifier.weight(1f), onClick = onCreateNew)
                }
            }
            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = appTextSize(elderMode, state.settings.fontScale, 16.sp)),
                )
            }
            if (searchMatches.isNotEmpty()) {
                items(searchMatches) { file ->
                    RecentFileCard(
                        file = file,
                        elderMode = elderMode,
                        fontScale = state.settings.fontScale,
                        onClick = { onOpenRecent(file.uri) },
                    )
                }
            } else {
                item {
                    EmptyHomeState(elderMode = elderMode, fontScale = state.settings.fontScale)
                }
            }
            if (state.recentFiles.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("最近笔记", fontSize = appTextSize(elderMode, state.settings.fontScale, 18.sp), fontWeight = FontWeight.Medium)
                        TextButton(onClick = onClearRecents) {
                            Text("清空", fontSize = appTextSize(elderMode, state.settings.fontScale, 14.sp))
                        }
                    }
                }
                items(state.recentFiles) { file ->
                    RecentFileCard(
                        file = file,
                        elderMode = elderMode,
                        fontScale = state.settings.fontScale,
                        onClick = { onOpenRecent(file.uri) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadMDDocumentScreen(
    state: DocumentState,
    snackbarHostState: SnackbarHostState,
    onOpenHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onExport: () -> Unit,
    onDraftChange: (String) -> Unit,
) {
    val elderMode = state.settings.elderMode
    val textScale = uiTextScale(state.settings.elderMode, state.settings.fontScale)
    val spacing = uiSpacing(state.settings.lineHeightScale)
    val previewContent = state.draftContent.ifBlank { state.content }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "ReadMD",
                            fontSize = appTextSize(elderMode, state.settings.fontScale, 24.sp),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = state.displayName + if (state.hasUnsavedChanges) " *" else "",
                            fontSize = appTextSize(elderMode, state.settings.fontScale, 12.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onOpenHome) {
                        Text("首页", fontSize = appTextSize(elderMode, state.settings.fontScale, 15.sp))
                    }
                    TextButton(onClick = onOpenSettings) {
                        Text("设置", fontSize = appTextSize(elderMode, state.settings.fontScale, 15.sp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                ActionButton(text = "保存", fontScale = textScale, elderMode = elderMode, modifier = Modifier.weight(1f), onClick = onSave)
                ActionButton(text = "另存", fontScale = textScale, elderMode = elderMode, modifier = Modifier.weight(1f), onClick = onSaveAs)
                ActionButton(text = "导出", fontScale = textScale, elderMode = elderMode, modifier = Modifier.weight(1f), onClick = onExport)
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    Text(
                        text = state.displayName,
                        fontSize = appTextSize(elderMode, state.settings.fontScale, 18.sp),
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "点开即可编辑，修改会自动保留草稿。",
                        fontSize = appTextSize(elderMode, state.settings.fontScale, 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = state.draftContent,
                        onValueChange = onDraftChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = appTextSize(elderMode, state.settings.fontScale, 18.sp)),
                    )
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    Text(
                        text = "预览",
                        fontSize = appTextSize(elderMode, state.settings.fontScale, 18.sp),
                        fontWeight = FontWeight.Medium,
                    )
                    if (previewContent.isBlank()) {
                        Text("暂无内容")
                    } else {
                        MarkdownPreview(
                            content = previewContent,
                            fontScale = textScale,
                            lineHeightScale = state.settings.lineHeightScale,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadMDSettingsSheet(
    fontScale: Float,
    lineHeightScale: Float,
    elderMode: Boolean,
    onDismiss: () -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onToggleElderMode: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp * lineHeightScale),
        ) {
            Text("设置", fontSize = 22.sp * fontScale, fontWeight = FontWeight.Bold)
            Text("字体", fontSize = 16.sp * fontScale)
            Slider(
                value = fontScale,
                onValueChange = onFontScaleChange,
                valueRange = 0.85f..1.8f,
            )
            Text("行距", fontSize = 16.sp * fontScale)
            Slider(
                value = lineHeightScale,
                onValueChange = onLineHeightChange,
                valueRange = 0.85f..1.8f,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp * lineHeightScale)) {
                ActionButton(
                    text = if (elderMode) "关闭大字" else "开启大字",
                    fontScale = fontScale,
                    elderMode = elderMode,
                    modifier = Modifier.weight(1f),
                    onClick = onToggleElderMode,
                )
                ActionButton(
                    text = "关闭",
                    fontScale = fontScale,
                    elderMode = elderMode,
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun RecentFileCard(
    file: RecentFile,
    elderMode: Boolean,
    fontScale: Float,
    onClick: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = file.displayName,
                    fontSize = appTextSize(elderMode, fontScale, 18.sp),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (file.canWrite) "可写" else "只读",
                    fontSize = appTextSize(elderMode, fontScale, 12.sp),
                )
            }
            if (file.previewSnippet.isNotBlank()) {
                Text(
                    text = file.previewSnippet,
                    fontSize = appTextSize(elderMode, fontScale, 14.sp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = dateFormat.format(Date(file.lastOpenedAt)),
                fontSize = appTextSize(elderMode, fontScale, 12.sp),
            )
        }
    }
}

@Composable
private fun EmptyHomeState(elderMode: Boolean, fontScale: Float) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("还没有备忘录", fontSize = appTextSize(elderMode, fontScale, 18.sp), fontWeight = FontWeight.Medium)
            Text("点击“导入”打开 md 文件，或点击“新建”创建一个本地备忘录。", fontSize = appTextSize(elderMode, fontScale, 14.sp))
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    fontScale: Float,
    elderMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val textSize = (if (elderMode) 20.sp else 15.sp) * fontScale
    val minHeight = when {
        elderMode && text.length >= 4 -> 72.dp
        elderMode -> 64.dp
        text.length >= 4 -> 52.dp
        else -> 48.dp
    }
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = minHeight),
    ) {
        Text(
            text = text,
            fontSize = textSize,
            lineHeight = textSize * 1.15f,
            maxLines = 2,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private fun appTextSize(elderMode: Boolean, fontScale: Float, base: TextUnit): TextUnit {
    val elderScale = if (elderMode) 1.25f else 1f
    return base * fontScale * elderScale
}

private fun uiTextScale(elderMode: Boolean, fontScale: Float): Float {
    return fontScale * if (elderMode) 1.25f else 1f
}

private fun uiSpacing(lineHeightScale: Float): Dp {
    return 12.dp * lineHeightScale
}

@Preview(showBackground = true)
@Composable
private fun ReadMDHomePreview() {
    ReadMDTheme {
        val state = DocumentState(
            recentFiles = listOf(
                RecentFile(
                    uri = "content://demo",
                    displayName = "示例.md",
                    lastOpenedAt = System.currentTimeMillis(),
                    canWrite = true,
                    previewSnippet = "第一行内容 第二行内容 第三行内容",
                ),
            ),
        )
        Box(modifier = Modifier.fillMaxSize()) {
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
