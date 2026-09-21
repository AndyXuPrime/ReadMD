package com.andyxu.readmd.file

private const val ACTION_OPEN_DOCUMENT_HINT = "ACTION_OPEN_DOCUMENT"
private const val PERMISSION_DENIAL_HINT = "Permission Denial"

fun Throwable.isDocumentAccessDenied(): Boolean {
    return generateSequence(this) { it.cause }.any { error ->
        error is SecurityException ||
            error.message?.contains(PERMISSION_DENIAL_HINT, ignoreCase = true) == true ||
            error.message?.contains(ACTION_OPEN_DOCUMENT_HINT, ignoreCase = true) == true
    }
}

fun Throwable.openDocumentMessage(isRecentFile: Boolean = true): String {
    return if (isDocumentAccessDenied()) {
        if (isRecentFile) "该记录已失效，请重新选择" else "无法读取该文件，请重新选择"
    } else {
        "无法打开文件：${message ?: "请重新选择"}"
    }
}

fun Throwable.saveDocumentMessage(): String {
    return if (isDocumentAccessDenied()) {
        "保存失败：原文件授权已失效，请使用“另存”保存到新文件"
    } else {
        "保存失败：${message ?: "请另存为新文件"}"
    }
}
