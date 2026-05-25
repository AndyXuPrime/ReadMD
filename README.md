# ReadMD

一款面向 Android 的本地 Markdown 备忘录应用，主打“阅读优先”的打开、浏览、编辑和保存体验，适合快速查看和修改手机里的 Markdown 内容。

## 项目目标

ReadMD 解决手机端 Markdown 文件阅读和编辑不顺手的问题，重点覆盖下面几件事：

- 通过 Android 系统文件选择器打开本地 `.md`、`.markdown` 和 `.txt` 文件
- 先阅读、后编辑，减少页面切换和误操作
- 本地修改并保存 Markdown 原文
- 支持适老化大字模式、夜间模式、行距调整
- 支持本地草稿保护，降低误关应用造成的内容丢失

当前不规划 iOS、云同步、多端同步、双链、知识图谱、插件系统、密码保护或本地加密笔记。

## 项目亮点

- 阅读页和编辑页分离，默认先进入阅读页，给 Markdown 预览更大的空间。
- 夜间模式、日间模式和大字模式彼此独立，适合长时间阅读。
- 使用系统文件选择器接入本地文件，不主动扫描整个手机存储。
- 支持草稿自动恢复，避免新建或编辑中断后内容丢失。
- 阅读页支持双指缩放字号，便于按阅读习惯临时放大或缩小。
- Markdown 预览基于成熟渲染方案实现，覆盖常见标题、列表、任务列表、代码块和表格展示。

## 技术实现

| 模块 | 作用 |
| --- | --- |
| `MainActivity` | Compose 界面入口，组织首页、阅读页、编辑页和设置面板 |
| `ReadMDViewModel` | 管理页面状态、草稿恢复、保存/另存/导出流程 |
| `DocumentRepository` | 负责系统文件读写、编码识别、最近文件和设置持久化 |
| `MarkdownPreview` | Markdown 预览渲染与内容预处理 |
| `Theme` | 日间/夜间/大字模式主题与配色切换 |

## 文档

- [需求分析文档](docs/requirements-analysis.md)
- [使用说明](docs/user-guide.md)
- [下载安装说明](docs/installation.md)
- [设计准则文档](docs/design-principles.md)
- [阶段 01 开发记录：Android 工程骨架](docs/development/stage-01-android-foundation.md)
- [阶段 02 开发记录：本地备忘录 MVP 闭环](docs/development/stage-02-local-memo-mvp.md)
- [阶段 03 开发记录：稳定性与文档](docs/development/stage-03-stability-docs.md)
- [阶段 04 开发记录：真机反馈修复](docs/development/stage-04-user-feedback-fixes.md)

## 当前状态

项目已完成 Android + Jetpack Compose 工程骨架，并实现本地 Markdown 备忘录的完整基础闭环：

- 打开本地 Markdown/Text 文件
- 默认进入阅读页，提供更大的 Markdown 预览空间
- 点击“进入编辑模式”后编辑 Markdown 原文
- 保存、另存和导出 Markdown
- 新建备忘录
- 最近打开文件
- 首页最近文件搜索
- 大字模式、字号和行距调整
- 日间/夜间模式切换
- 阅读页双指缩放字号
- 系统返回手势支持编辑页返回阅读页、阅读页返回首页
- 设置持久化
- 自动草稿保护
- 基于成熟 Android Markdown 渲染方案显示常见 Markdown 内容

## 构建验证

已验证：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Debug APK 构建产物：

```text
app/build/outputs/apk/debug/app-debug.apk
```

下一阶段将继续打磨真机阅读体验、输入法场景、Release APK 和发布流程。
