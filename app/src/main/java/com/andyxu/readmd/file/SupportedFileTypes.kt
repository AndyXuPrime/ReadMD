package com.andyxu.readmd.file

private val supportedExtensions = setOf("md", "markdown", "txt")
private val supportedMimeTypes = setOf(
    "text/markdown",
    "text/x-markdown",
)

fun isSupportedReadMDFileName(displayName: String): Boolean {
    val extension = displayName
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
    return extension in supportedExtensions
}

fun isSupportedReadMDDocument(displayName: String, mimeType: String?): Boolean {
    if (isSupportedReadMDFileName(displayName)) return true
    val hasExtension = displayName.substringAfterLast('.', missingDelimiterValue = "").isNotBlank()
    return !hasExtension && mimeType?.lowercase() in supportedMimeTypes
}

fun suggestedReadMDFileName(displayName: String): String {
    val safeName = displayName.ifBlank { "ReadMD-${System.currentTimeMillis()}.md" }
    return if (isSupportedReadMDFileName(safeName)) safeName else "$safeName.md"
}
