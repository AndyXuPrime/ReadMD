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

@Composable
fun MarkdownPreview(
    content: String,
    fontScale: Float,
    lineHeightScale: Float,
    modifier: Modifier = Modifier,
) {
    val lines = content.lines()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.trim().startsWith("```")) {
                val codeLines = mutableListOf<String>()
                index += 1
                while (index < lines.size && !lines[index].trim().startsWith("```")) {
                    codeLines += lines[index]
                    index += 1
                }
                CodeBlock(codeLines.joinToString("\n"), fontScale)
            } else {
                MarkdownLine(
                    line = line,
                    fontScale = fontScale,
                    lineHeightScale = lineHeightScale,
                )
            }
            index += 1
        }
    }
}

@Composable
private fun MarkdownLine(
    line: String,
    fontScale: Float,
    lineHeightScale: Float,
) {
    val trimmed = line.trim()
    when {
        trimmed.isBlank() -> Box(modifier = Modifier.padding(vertical = 3.dp))
        trimmed.matches(Regex("^[-*_]{3,}$")) -> HorizontalDivider()
        trimmed.startsWith("#") -> HeadingLine(trimmed, fontScale, lineHeightScale)
        trimmed.startsWith(">") -> QuoteLine(trimmed.removePrefix(">").trimStart(), fontScale, lineHeightScale)
        trimmed.startsWith("- [ ]") || trimmed.startsWith("- [x]", ignoreCase = true) -> TaskLine(trimmed, fontScale, lineHeightScale)
        trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.matches(Regex("^\\d+\\.\\s+.+")) -> ListLine(trimmed, fontScale, lineHeightScale)
        trimmed.contains("|") && trimmed.count { it == '|' } >= 2 -> TableLikeLine(trimmed, fontScale)
        else -> StyledText(
            raw = trimmed,
            fontSize = 16.sp * fontScale,
            lineHeight = 24.sp * fontScale * lineHeightScale,
        )
    }
}

@Composable
private fun HeadingLine(
    line: String,
    fontScale: Float,
    lineHeightScale: Float,
) {
    val level = line.takeWhile { it == '#' }.length.coerceIn(1, 6)
    val text = line.drop(level).trim()
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
    line: String,
    fontScale: Float,
    lineHeightScale: Float,
) {
    val checked = line.startsWith("- [x]", ignoreCase = true)
    val text = line.drop(5).trim()
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
    line: String,
    fontScale: Float,
    lineHeightScale: Float,
) {
    val text = line
        .removePrefix("- ")
        .removePrefix("* ")
        .replace(Regex("^\\d+\\.\\s+"), "")
        .trim()
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
    line: String,
    fontScale: Float,
) {
    val cells = line.trim('|').split('|').map { it.trim() }
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

