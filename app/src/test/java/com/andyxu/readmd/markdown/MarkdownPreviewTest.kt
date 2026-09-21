package com.andyxu.readmd.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownPreviewTest {
    @Test
    fun countSearchMatches_isCaseInsensitive() {
        val content = "ReadMD can read markdown.\nreadmd can edit Markdown."

        val count = countSearchMatches(content, "readmd")

        assertEquals(2, count)
    }

    @Test
    fun countSearchMatches_returnsZeroForBlankQuery() {
        val count = countSearchMatches("# Title", " ")

        assertEquals(0, count)
    }

    @Test
    fun containsLatexMath_detectsBlockMath() {
        val content = "公式：\n${'$'}${'$'}\na^2 + b^2 = c^2\n${'$'}${'$'}"

        assertTrue(containsLatexMath(content))
    }

    @Test
    fun containsLatexMath_detectsInlineMath() {
        val content = "行内公式 ${'$'}${'$'}E = mc^2${'$'}${'$'} 可以预览。"

        assertTrue(containsLatexMath(content))
    }

    @Test
    fun containsLatexMath_ignoresPlainMarkdown() {
        val content = "# 标题\n\n普通 Markdown 内容。"

        assertFalse(containsLatexMath(content))
    }

    @Test
    fun styleOnlyChanges_doNotRequestFullMarkdownRender() {
        val markdown = "# 标题\n\n普通内容"

        assertFalse(
            shouldRenderMarkdown(
                renderedMarkdown = markdown,
                renderedLatexScaleBucket = null,
                markdown = markdown,
                latexScaleBucket = latexScaleBucket(markdown, 1.55f),
            ),
        )
    }

    @Test
    fun latexScaleBucketChange_requestsRender() {
        val markdown = "公式：${'$'}${'$'}E = mc^2${'$'}${'$'}"

        assertTrue(
            shouldRenderMarkdown(
                renderedMarkdown = markdown,
                renderedLatexScaleBucket = latexScaleBucket(markdown, 1f),
                markdown = markdown,
                latexScaleBucket = latexScaleBucket(markdown, 1.2f),
            ),
        )
    }

    @Test
    fun touchRouting_keepsSingleFingerEventsForTextViewScrolling() {
        assertFalse(shouldConsumeAsScaleGesture(pointerCount = 1, scaleInProgress = false))
        assertTrue(shouldConsumeAsScaleGesture(pointerCount = 2, scaleInProgress = false))
        assertTrue(shouldConsumeAsScaleGesture(pointerCount = 1, scaleInProgress = true))
    }
}
