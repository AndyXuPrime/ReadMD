package com.andyxu.readmd.file

sealed class DocumentReadException(message: String) : IllegalStateException(message)

class UnsupportedDocumentTypeException : DocumentReadException(
    "当前仅支持打开 .md、.markdown 或 .txt 文件",
)

class DocumentTooLargeException : DocumentReadException(
    "文件超过 2MB，建议拆分后再打开",
)

class UnsupportedTextEncodingException : DocumentReadException(
    "无法识别文件编码，请转换为 UTF-8 或 GB18030 后重试",
)
