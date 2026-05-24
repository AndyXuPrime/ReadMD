# ReadMD

一款简单的 Android Markdown 文件浏览与编辑备忘录应用。

## 项目目标

ReadMD 计划解决手机端 Markdown 文件阅读、编辑和导出不方便的问题。它面向普通手机用户、LLM 高频用户、学生和老年用户，提供类似手机备忘录的轻量体验：

- 通过 Android 系统文件选择器打开 `.md` 文件
- 正确渲染 Markdown 内容
- 本地编辑并保存 Markdown 文件
- 首版支持导出 Markdown，PDF 或图片导出作为后续增强
- 支持适老化大字模式

当前不规划 iOS、云同步、多端同步、双链、知识图谱、插件系统、密码保护或本地加密笔记。

## 文档

- [需求分析文档](docs/requirements-analysis.md)
- [阶段 01 开发记录：Android 工程骨架](docs/development/stage-01-android-foundation.md)

## 当前状态

项目已完成 Android + Jetpack Compose 工程骨架，当前首屏为占位 UI，已支持 Debug 构建。

已验证：

```powershell
.\gradlew.bat :app:assembleDebug
```

下一阶段将实现系统文件选择器、Markdown 文件读取、编辑、保存和 Markdown 导出闭环。
