package com.andyxu.readmd.markdown

import android.text.method.ScrollingMovementMethod
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin

private const val MAX_PREVIEW_CHARS = 120_000

@Composable
fun MarkdownPreview(
    content: String,
    fontScale: Float,
    lineHeightScale: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .build()
    }
    val safeContent = remember(content) { sanitizeMarkdownText(content).limitForPreview() }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextView(viewContext).apply {
                setTextIsSelectable(true)
                includeFontPadding = true
                isVerticalScrollBarEnabled = true
                movementMethod = ScrollingMovementMethod()
            }
        },
        update = { textView ->
            textView.textSize = 16f * fontScale
            textView.setLineSpacing(0f, lineHeightScale.coerceIn(0.85f, 1.8f))
            markwon.setMarkdown(textView, safeContent)
        },
    )
}

private fun String.limitForPreview(): String {
    if (length <= MAX_PREVIEW_CHARS) return this
    return take(MAX_PREVIEW_CHARS) + "\n\n> 预览内容较长，已先显示前 ${MAX_PREVIEW_CHARS} 个字符；完整内容仍可在编辑区查看和保存。"
}

fun sanitizeMarkdownText(content: String): String {
    return content
        .replace("\uFEFF", "")
        .replace("\u0000", "")
        .replace("\r\n", "\n")
        .replace("\r", "\n")
}

fun countSearchMatches(content: String, query: String): Int {
    if (query.isBlank()) return 0
    return Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(content).count()
}
