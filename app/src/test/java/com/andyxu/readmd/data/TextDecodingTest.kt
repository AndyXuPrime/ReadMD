package com.andyxu.readmd.data

import com.andyxu.readmd.file.DocumentTooLargeException
import com.andyxu.readmd.file.UnsupportedTextEncodingException
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TextDecodingTest {
    @Test
    fun decodeDocumentText_readsUtf8Bom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "标题".toByteArray()

        assertEquals("标题", decodeDocumentText(bytes))
    }

    @Test
    fun decodeDocumentText_readsUtf16OnlyWithBom() {
        val body = "公式".toByteArray(Charsets.UTF_16LE)
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + body

        assertEquals("公式", decodeDocumentText(bytes))
    }

    @Test
    fun decodeDocumentText_readsGb18030WithoutUtf16Misclassification() {
        val bytes = "中文备忘录".toByteArray(Charset.forName("GB18030"))

        assertEquals("中文备忘录", decodeDocumentText(bytes))
    }

    @Test
    fun decodeDocumentText_rejectsBomlessUtf16AndBinaryNulls() {
        val bytes = "AB".toByteArray(Charsets.UTF_16LE)

        assertThrows(UnsupportedTextEncodingException::class.java) {
            decodeDocumentText(bytes)
        }
    }

    @Test
    fun decodeDocumentText_rejectsMalformedBytesInsteadOfReturningGarbledText() {
        assertThrows(UnsupportedTextEncodingException::class.java) {
            decodeDocumentText(byteArrayOf(0x81.toByte()))
        }
    }

    @Test
    fun readAllBytesLimited_rejectsUnknownSizeStreamPastLimit() {
        val input = ByteArrayInputStream(ByteArray(11))

        assertThrows(DocumentTooLargeException::class.java) {
            readAllBytesLimited(input, 10)
        }
    }
}
