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
- [使用说明](docs/user-guide.md)
- [下载安装说明](docs/installation.md)
- [阶段 01 开发记录：Android 工程骨架](docs/development/stage-01-android-foundation.md)
- [阶段 02 开发记录：本地备忘录 MVP 闭环](docs/development/stage-02-local-memo-mvp.md)
- [阶段 03 开发记录：稳定性与文档](docs/development/stage-03-stability-docs.md)

## 当前状态

项目已完成 Android + Jetpack Compose 工程骨架，并实现本地 Markdown 备忘录 MVP 闭环：

- 打开本地 Markdown/Text 文件
- 阅读基础 Markdown 预览
- 编辑、预览、保存
- 新建备忘录
- 另存为 Markdown
- 导出 Markdown
- 最近打开文件
- 文内搜索匹配统计
- 大字模式和字号调整
- 设置持久化
- 自动草稿保护

已验证：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Debug APK 构建产物：

```text
app/build/outputs/apk/debug/app-debug.apk
```

下一阶段将继续打磨 Markdown 渲染增强、真机测试问题修复、Release APK 和发布流程。
