package com.andyxu.readmd.markdown

import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.text.method.ScrollingMovementMethod
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.MaterialTheme
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import kotlin.math.roundToInt

private const val MAX_PREVIEW_CHARS = 120_000
private const val MIN_READING_FONT_SCALE = 0.85f
private const val MAX_READING_FONT_SCALE = 1.55f
private const val LATEX_SCALE_BUCKET = 0.05f

@Composable
fun MarkdownPreview(
    content: String,
    fontScale: Float,
    lineHeightScale: Float,
    gestureFontScale: Float = fontScale,
    onFontScaleChange: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val markwon = remember(context, fontScale) {
        Markwon.builder(context)
            .usePlugin(MarkwonInlineParserPlugin.create())
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(JLatexMathPlugin.create(16f * fontScale) { builder ->
                builder.inlinesEnabled(true)
            })
            .usePlugin(HtmlPlugin.create())
            .build()
    }
    val safeContent = remember(content) { sanitizeMarkdownText(content).limitForPreview() }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            ZoomableMarkdownTextView(viewContext)
        },
        update = { textView ->
            textView.bind(
                markwon = markwon,
                markdown = safeContent,
                fontScale = fontScale,
                gestureFontScale = gestureFontScale,
                lineHeightScale = lineHeightScale,
                textColor = colors.onSurface.toArgb(),
                hintColor = colors.onSurfaceVariant.toArgb(),
                linkColor = colors.primary.toArgb(),
                backgroundColor = colors.surface.toArgb(),
                onFontScaleChange = onFontScaleChange,
            )
        },
    )
}

private class ZoomableMarkdownTextView(
    context: Context,
) : TextView(context) {
    private var renderedMarkdown: String? = null
    private var renderedLatexScaleBucket: Int? = null
    private var currentFontScale: Float = 1f
    private var scaleCallback: ((Float) -> Unit)? = null
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val target = (currentFontScale * detector.scaleFactor)
                    .coerceIn(MIN_READING_FONT_SCALE, MAX_READING_FONT_SCALE)
                if (kotlin.math.abs(target - currentFontScale) >= 0.01f) {
                    currentFontScale = target
                    scaleCallback?.invoke(target)
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        },
    )

    init {
        includeFontPadding = true
        isVerticalScrollBarEnabled = true
        overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        movementMethod = ScrollingMovementMethod()
        setTextIsSelectable(false)
    }

    fun bind(
        markwon: Markwon,
        markdown: String,
        fontScale: Float,
        gestureFontScale: Float,
        lineHeightScale: Float,
        textColor: Int,
        hintColor: Int,
        linkColor: Int,
        backgroundColor: Int,
        onFontScaleChange: ((Float) -> Unit)?,
    ) {
        currentFontScale = gestureFontScale
        scaleCallback = onFontScaleChange
        textSize = 16f * fontScale
        val safeLineHeight = lineHeightScale
            .coerceIn(0.85f, 1.8f)
            .coerceAtLeast(if (fontScale >= 1.35f) 1.12f else 1f)
        setLineSpacing(0f, safeLineHeight)
        setTextColor(textColor)
        setHintTextColor(hintColor)
        setLinkTextColor(linkColor)
        setBackgroundColor(backgroundColor)
        val latexScaleBucket = if (containsLatexMath(markdown)) {
            (fontScale / LATEX_SCALE_BUCKET).roundToInt()
        } else {
            null
        }
        if (renderedMarkdown != markdown || renderedLatexScaleBucket != latexScaleBucket) {
            markwon.setMarkdown(this, markdown)
            renderedMarkdown = markdown
            renderedLatexScaleBucket = latexScaleBucket
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        if (event.pointerCount > 1 || scaleDetector.isInProgress) {
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        return super.onTouchEvent(event)
    }
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

fun containsLatexMath(content: String): Boolean {
    if (content.contains("$$")) return true
    if (content.contains("\\[") || content.contains("\\(")) return true
    return Regex("""(^|[^\\])\$[^$\n]+\$""").containsMatchIn(content)
}

fun countSearchMatches(content: String, query: String): Int {
    if (query.isBlank()) return 0
    return Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(content).count()
}
