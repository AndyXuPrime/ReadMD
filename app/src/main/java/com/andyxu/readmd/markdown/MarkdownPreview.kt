package com.andyxu.readmd.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed interface MarkdownBlock {
    data object Blank : MarkdownBlock
    data object Divider : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Task(val checked: Boolean, val text: String) : MarkdownBlock
    data class ListItem(val text: String) : MarkdownBlock
    data class Table(val cells: List<String>) : MarkdownBlock
    data class CodeBlock(val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
}

@Composable
fun MarkdownPreview(
    content: String,
    fontScale: Float,
    lineHeightScale: Float,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(content) {
        runCatching { parseMarkdownBlocks(content) }
            .getOrElse { listOf(MarkdownBlock.Paragraph(content)) }
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.forEach { block ->
            MarkdownBlockView(block, fontScale, lineHeightScale)
        }
    }
}

private fun parseMarkdownBlocks(content: String): List<MarkdownBlock> {
    val lines = content.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()
        when {
            trimmed.isBlank() -> blocks += MarkdownBlock.Blank
            trimmed.startsWith("```") -> {
                val codeLines = mutableListOf<String>()
                index += 1
                while (index < lines.size && !lines[index].trim().startsWith("```")) {
                    codeLines += lines[index]
                    index += 1
                }
                blocks += MarkdownBlock.CodeBlock(codeLines.joinToString("\n"))
            }
            trimmed.matches(Regex("^[-*_]{3,}$")) -> blocks += MarkdownBlock.Divider
            trimmed.startsWith("#") -> {
                val level = line.takeWhile { it == '#' }.length.coerceIn(1, 6)
                blocks += MarkdownBlock.Heading(level = level, text = line.drop(level).trim())
            }
            trimmed.startsWith(">") -> blocks += MarkdownBlock.Quote(trimmed.removePrefix(">").trimStart())
            trimmed.startsWith("- [ ]") || trimmed.startsWith("- [x]", ignoreCase = true) -> {
                blocks += MarkdownBlock.Task(
                    checked = trimmed.startsWith("- [x]", ignoreCase = true),
                    text = trimmed.drop(5).trim(),
                )
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.matches(Regex("^\\d+\\.\\s+.+")) -> {
                blocks += MarkdownBlock.ListItem(
                    trimmed
                        .removePrefix("- ")
                        .removePrefix("* ")
                        .replace(Regex("^\\d+\\.\\s+"), "")
                        .trim(),
                )
            }
            trimmed.contains("|") && trimmed.count { it == '|' } >= 2 -> {
                blocks += MarkdownBlock.Table(trimmed.trim('|').split('|').map { it.trim() })
            }
            else -> blocks += MarkdownBlock.Paragraph(trimmed)
        }
        index += 1
    }
    return blocks
}

@Composable
private fun MarkdownBlockView(
    block: MarkdownBlock,
    fontScale: Float,
    lineHeightScale: Float,
) {
    when (block) {
        MarkdownBlock.Blank -> Box(modifier = Modifier.padding(vertical = 3.dp))
        MarkdownBlock.Divider -> HorizontalDivider()
        is MarkdownBlock.Heading -> HeadingLine(block.level, block.text, fontScale, lineHeightScale)
        is MarkdownBlock.Quote -> QuoteLine(block.text, fontScale, lineHeightScale)
        is MarkdownBlock.Task -> TaskLine(block.checked, block.text, fontScale, lineHeightScale)
        is MarkdownBlock.ListItem -> ListLine(block.text, fontScale, lineHeightScale)
        is MarkdownBlock.Table -> TableLikeLine(block.cells, fontScale)
        is MarkdownBlock.CodeBlock -> CodeBlock(block.text, fontScale)
        is MarkdownBlock.Paragraph -> StyledText(
            raw = block.text,
            fontSize = 16.sp * fontScale,
            lineHeight = 24.sp * fontScale * lineHeightScale,
        )
    }
}

@Composable
private fun HeadingLine(
    level: Int,
    text: String,
    fontScale: Float,
    lineHeightScale: Float,
) {
    val size = when (level) {
        1 -> 30.sp
        2 -> 26.sp
        3 -> 22.sp
        4 -> 20.sp
        else -> 18.sp
    } * fontScale
    StyledText(
        raw = text,
        fontSize = size,
        lineHeight = size * 1.25f * lineHeightScale,
        fontWeight = FontWeight.Bold,
        topPadding = if (level <= 2) 10.dp else 4.dp,
    )
}

@Composable
private fun QuoteLine(
    text: String,
    fontScale: Float,
    lineHeightScale: Float,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
        StyledText(
            raw = text,
            fontSize = 16.sp * fontScale,
            lineHeight = 24.sp * fontScale * lineHeightScale,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = FontStyle.Italic,
        )
    }
}

@Composable
private fun TaskLine(
    checked: Boolean,
    text: String,
    fontScale: Float,
    lineHeightScale: Float,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null)
        StyledText(
            raw = text,
            fontSize = 16.sp * fontScale,
            lineHeight = 24.sp * fontScale * lineHeightScale,
            textDecoration = if (checked) TextDecoration.LineThrough else null,
        )
    }
}

@Composable
private fun ListLine(
    text: String,
    fontScale: Float,
    lineHeightScale: Float,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "•",
            fontSize = 16.sp * fontScale,
            lineHeight = 24.sp * fontScale * lineHeightScale,
        )
        StyledText(
            raw = text,
            fontSize = 16.sp * fontScale,
            lineHeight = 24.sp * fontScale * lineHeightScale,
        )
    }
}

@Composable
private fun TableLikeLine(
    cells: List<String>,
    fontScale: Float,
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cells.forEach { cell ->
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.width(140.dp),
            ) {
                StyledText(
                    raw = cell,
                    fontSize = 14.sp * fontScale,
                    lineHeight = 20.sp * fontScale,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(
    text: String,
    fontScale: Float,
) {
    Text(
        text = text.ifBlank { " " },
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp * fontScale,
        lineHeight = 22.sp * fontScale,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(12.dp),
    )
}

@Composable
private fun StyledText(
    raw: String,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    textDecoration: TextDecoration? = null,
    color: Color = MaterialTheme.colorScheme.onSurface,
    topPadding: Dp = 0.dp,
) {
    Text(
        text = raw
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .replace(Regex("__([^_]+)__"), "$1")
            .replace(Regex("\\*([^*]+)\\*"), "$1")
            .replace(Regex("_([^_]+)_"), "$1")
            .replace(Regex("~~([^~]+)~~"), "$1"),
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        textDecoration = textDecoration,
        color = color,
        modifier = modifier.padding(top = topPadding),
    )
}

fun countSearchMatches(content: String, query: String): Int {
    if (query.isBlank()) return 0
    return Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(content).count()
}
