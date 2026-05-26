@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.andyxu.readmd

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.andyxu.readmd.data.ReaderSettings
import com.andyxu.readmd.data.RecentFile
import com.andyxu.readmd.file.OpenMarkdownDocument
import com.andyxu.readmd.markdown.MarkdownPreview
import com.andyxu.readmd.ui.theme.ReadMDTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ReadMDViewModel = viewModel()
            val state by viewModel.state.collectAsState()
            ReadMDTheme(
                darkTheme = state.settings.darkMode,
                elderMode = state.settings.elderMode,
            ) {
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

    BackHandler(enabled = showSettings) {
        showSettings = false
    }

    BackHandler(enabled = !showSettings && isDocumentMode) {
        viewModel.handleBack()
    }

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
            darkMode = state.settings.darkMode,
            onDismiss = { showSettings = false },
            onFontScaleChange = viewModel::setFontScale,
            onLineHeightChange = viewModel::setLineHeightScale,
            onToggleElderMode = viewModel::toggleElderMode,
            onToggleDarkMode = viewModel::toggleDarkMode,
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
            onEnterEdit = viewModel::enterEditMode,
            onReturnToReading = viewModel::previewDraft,
            onDraftChange = viewModel::updateDraft,
            onReadingFontScaleChange = viewModel::setReadingFontScale,
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
    val textScale = uiTextScale(elderMode, state.settings.fontScale)
    val spacing = uiSpacing(state.settings.lineHeightScale, elderMode)
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
                        fontSize = appTextSize(elderMode, state.settings.fontScale, 28.sp),
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    TextButton(onClick = onOpenSettings) {
                        Text("设置", fontSize = appTextSize(elderMode, state.settings.fontScale, 16.sp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
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
                    ActionButton(
                        text = "导入",
                        fontScale = textScale,
                        elderMode = elderMode,
                        modifier = Modifier.weight(1f),
                        onClick = onImport,
                    )
                    ActionButton(
                        text = "新建",
                        fontScale = textScale,
                        elderMode = elderMode,
                        modifier = Modifier.weight(1f),
                        onClick = onCreateNew,
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "搜索",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = appTextSize(elderMode, state.settings.fontScale, 16.sp),
                    ),
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
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "最近笔记",
                            fontSize = appTextSize(elderMode, state.settings.fontScale, 18.sp),
                            fontWeight = FontWeight.Medium,
                        )
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
    onEnterEdit: () -> Unit,
    onReturnToReading: () -> Unit,
    onDraftChange: (String) -> Unit,
    onReadingFontScaleChange: (Float) -> Unit,
) {
    val elderMode = state.settings.elderMode
    val textScale = uiTextScale(elderMode, state.settings.fontScale)
    val spacing = uiSpacing(state.settings.lineHeightScale, elderMode)
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
                            fontSize = appTextSize(elderMode, state.settings.fontScale, 28.sp),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = state.displayName,
                            fontSize = appTextSize(elderMode, state.settings.fontScale, 15.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = if (state.isEditing) onReturnToReading else onOpenHome) {
                        Text(
                            if (state.isEditing) "阅读" else "首页",
                            fontSize = appTextSize(elderMode, state.settings.fontScale, 15.sp),
                        )
                    }
                    TextButton(onClick = onOpenSettings) {
                        Text("设置", fontSize = appTextSize(elderMode, state.settings.fontScale, 15.sp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { innerPadding ->
        if (state.isEditing) {
            ReadMDEditMode(
                state = state,
                spacing = spacing,
                textScale = textScale,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                onSave = onSave,
                onSaveAs = onSaveAs,
                onExport = onExport,
                onDraftChange = onDraftChange,
            )
        } else {
            ReadMDReadingMode(
                state = state,
                previewContent = previewContent,
                spacing = spacing,
                textScale = textScale,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                onEnterEdit = onEnterEdit,
                onReadingFontScaleChange = onReadingFontScaleChange,
            )
        }
    }
}

@Composable
private fun ReadMDReadingMode(
    state: DocumentState,
    previewContent: String,
    spacing: Dp,
    textScale: Float,
    modifier: Modifier = Modifier,
    onEnterEdit: () -> Unit,
    onReadingFontScaleChange: (Float) -> Unit,
) {
    val elderMode = state.settings.elderMode
    val readingTextScale = (textScale * state.readingFontScale)
        .coerceIn(ReaderSettings.MIN_FONT_SCALE, ReaderSettings.MAX_FONT_SCALE)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        val previewScalePercent = (readingTextScale * 100).roundToInt()
        Text(
            text = state.displayName,
            fontSize = appTextSize(elderMode, state.settings.fontScale, 20.sp),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "阅读字号 ${previewScalePercent}% · 可双指缩放",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = appTextSize(elderMode, state.settings.fontScale, 13.sp),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        ) {
            if (previewContent.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "暂无内容，点击下方按钮开始编辑。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = appTextSize(elderMode, state.settings.fontScale, 16.sp),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                MarkdownPreview(
                    content = previewContent,
                    fontScale = readingTextScale,
                    lineHeightScale = state.settings.lineHeightScale,
                    gestureFontScale = state.readingFontScale,
                    onFontScaleChange = onReadingFontScaleChange,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                )
            }
        }
        SecondaryActionButton(
            text = "进入编辑模式",
            fontScale = textScale,
            elderMode = elderMode,
            modifier = Modifier.fillMaxWidth(),
            onClick = onEnterEdit,
        )
    }
}

@Composable
private fun ReadMDEditMode(
    state: DocumentState,
    spacing: Dp,
    textScale: Float,
    modifier: Modifier = Modifier,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onExport: () -> Unit,
    onDraftChange: (String) -> Unit,
) {
    val elderMode = state.settings.elderMode
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (state.hasUnsavedChanges) "编辑中 *" else "编辑中",
                    fontSize = appTextSize(elderMode, state.settings.fontScale, 18.sp),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton("保存", textScale, elderMode, Modifier.weight(1f), onSave)
                    ActionButton("另存", textScale, elderMode, Modifier.weight(1f), onSaveAs)
                    ActionButton("导出", textScale, elderMode, Modifier.weight(1f), onExport)
                }
            }
        }
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        ) {
            OutlinedTextField(
                value = state.draftContent,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = appTextSize(elderMode, state.settings.fontScale, 19.sp),
                    lineHeight = appTextSize(elderMode, state.settings.fontScale, 30.sp) * state.settings.lineHeightScale,
                ),
                placeholder = {
                    Text(
                        "在这里输入 Markdown 内容",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                shape = RoundedCornerShape(18.dp),
                maxLines = Int.MAX_VALUE,
            )
        }
    }
}

@Composable
private fun ReadMDSettingsSheet(
    fontScale: Float,
    lineHeightScale: Float,
    elderMode: Boolean,
    darkMode: Boolean,
    onDismiss: () -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onToggleElderMode: () -> Unit,
    onToggleDarkMode: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp * lineHeightScale),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("设置", fontSize = 24.sp * fontScale, fontWeight = FontWeight.Bold)
                TextButton(onClick = onDismiss) {
                    Text("关闭", fontSize = 16.sp * fontScale)
                }
            }
            Text("字体", fontSize = 17.sp * fontScale, fontWeight = FontWeight.Medium)
            Slider(
                value = fontScale,
                onValueChange = onFontScaleChange,
                valueRange = ReaderSettings.MIN_FONT_SCALE..ReaderSettings.MAX_FONT_SCALE,
            )
            Text("行距", fontSize = 17.sp * fontScale, fontWeight = FontWeight.Medium)
            Slider(
                value = lineHeightScale,
                onValueChange = onLineHeightChange,
                valueRange = ReaderSettings.MIN_LINE_HEIGHT_SCALE..ReaderSettings.MAX_LINE_HEIGHT_SCALE,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp * lineHeightScale)) {
                ActionButton(
                    text = if (elderMode) "关闭大字" else "大字模式",
                    fontScale = fontScale,
                    elderMode = elderMode,
                    modifier = Modifier.weight(1f),
                    onClick = onToggleElderMode,
                )
                ActionButton(
                    text = if (darkMode) "日间模式" else "夜间模式",
                    fontScale = fontScale,
                    elderMode = elderMode,
                    modifier = Modifier.weight(1f),
                    onClick = onToggleDarkMode,
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
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = file.displayName,
                    fontSize = appTextSize(elderMode, fontScale, 18.sp),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (file.canWrite) "可写" else "只读",
                    fontSize = appTextSize(elderMode, fontScale, 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (file.previewSnippet.isNotBlank()) {
                Text(
                    text = file.previewSnippet,
                    fontSize = appTextSize(elderMode, fontScale, 14.sp),
                    lineHeight = appTextSize(elderMode, fontScale, 22.sp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = dateFormat.format(Date(file.lastOpenedAt)),
                fontSize = appTextSize(elderMode, fontScale, 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyHomeState(elderMode: Boolean, fontScale: Float) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "还没有备忘录",
                fontSize = appTextSize(elderMode, fontScale, 18.sp),
                fontWeight = FontWeight.Medium,
            )
            Text(
                "点击“导入”打开 md 文件，或点击“新建”创建本地备忘录。",
                fontSize = appTextSize(elderMode, fontScale, 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    val textSize = (if (elderMode) 19.sp else 15.sp) * fontScale
    val minHeight = when {
        elderMode && text.length >= 4 -> 72.dp
        elderMode -> 66.dp
        text.length >= 4 -> 54.dp
        else -> 50.dp
    }
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = minHeight),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
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

@Composable
private fun SecondaryActionButton(
    text: String,
    fontScale: Float,
    elderMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val textSize = (if (elderMode) 20.sp else 16.sp) * fontScale
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (elderMode) 70.dp else 54.dp),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
    ) {
        Text(
            text = text,
            fontSize = textSize,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun appTextSize(elderMode: Boolean, fontScale: Float, base: TextUnit): TextUnit {
    val elderScale = if (elderMode) 1.18f else 1f
    return base * fontScale * elderScale
}

private fun uiTextScale(elderMode: Boolean, fontScale: Float): Float {
    return fontScale * if (elderMode) 1.12f else 1f
}

private fun uiSpacing(lineHeightScale: Float, elderMode: Boolean): Dp {
    val base = if (elderMode) 14.dp else 12.dp
    return base * lineHeightScale
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
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
