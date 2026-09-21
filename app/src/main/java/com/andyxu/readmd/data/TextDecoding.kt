package com.andyxu.readmd.data

import com.andyxu.readmd.file.DocumentTooLargeException
import com.andyxu.readmd.file.UnsupportedTextEncodingException
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

internal fun readAllBytesLimited(input: InputStream, maxBytes: Long): ByteArray {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = input.read(chunk)
        if (read <= 0) break
        total += read
        if (total > maxBytes) throw DocumentTooLargeException()
        buffer.write(chunk, 0, read)
    }
    return buffer.toByteArray()
}

internal fun decodeDocumentText(bytes: ByteArray): String {
    if (bytes.isEmpty()) return ""

    val decoded = when {
        bytes.hasPrefix(0xEF, 0xBB, 0xBF) -> decodeStrict(bytes, Charsets.UTF_8, 3)
        bytes.hasPrefix(0xFF, 0xFE) -> decodeStrict(bytes, Charsets.UTF_16LE, 2)
        bytes.hasPrefix(0xFE, 0xFF) -> decodeStrict(bytes, Charsets.UTF_16BE, 2)
        bytes.any { it == 0.toByte() } -> throw UnsupportedTextEncodingException()
        else -> decodeStrictOrNull(bytes, Charsets.UTF_8)
            ?: decodeStrictOrNull(bytes, Charset.forName("GB18030"))
            ?: throw UnsupportedTextEncodingException()
    }
    return sanitizeDocumentText(decoded)
}

internal fun sanitizeDocumentText(text: String): String {
    return text
        .removePrefix("\uFEFF")
        .replace("\u0000", "")
        .replace("\r\n", "\n")
        .replace("\r", "\n")
}

private fun decodeStrictOrNull(bytes: ByteArray, charset: Charset): String? {
    return runCatching { decodeStrict(bytes, charset, 0) }.getOrNull()
}

private fun decodeStrict(bytes: ByteArray, charset: Charset, offset: Int): String {
    val decoder = charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return decoder.decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()
}

private fun ByteArray.hasPrefix(vararg values: Int): Boolean {
    return size >= values.size && values.indices.all { index -> this[index] == values[index].toByte() }
}
