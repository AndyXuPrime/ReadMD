# ReadMD 阶段 01 开发记录：Android 工程骨架

日期：2026-05-24

## 阶段目标

本阶段目标是把 ReadMD 从需求文档推进到可被 Android Studio 打开、可通过 Gradle 构建的 Android 原生工程。功能实现暂不展开，先建立后续开发的稳定工程基础。

## 已完成

1. 创建 Android 单模块工程骨架。
2. 配置 Gradle Wrapper，后续可通过 `gradlew.bat` 构建，不依赖全局 Gradle。
3. 配置 Kotlin + Jetpack Compose + Material 3。
4. 配置最低支持 Android 8.0：
   - `minSdk = 26`
5. 配置 Android 应用基本信息：
   - `applicationId = "com.andyxu.readmd"`
   - `namespace = "com.andyxu.readmd"`
   - `versionName = "0.1.0"`
6. 创建 `MainActivity`，接入 Jetpack Compose。
7. 按 Android edge-to-edge 设计要求调用 `enableEdgeToEdge()`。
8. 在 Manifest 中设置 `windowSoftInputMode="adjustResize"`，为后续编辑器键盘适配做准备。
9. 创建基础 Compose 主题：
   - 普通浅色/深色主题
   - 大字模式高对比色方案雏形
10. 创建首屏占位 UI：
    - ReadMD 标题
    - 产品说明
    - 大字模式开关
11. 创建基础启动图标资源。
12. 创建 `.gitignore`，排除：
    - Gradle 缓存
    - 构建产物
    - Android Studio 本地文件
    - `local.properties`
    - Codex 临时依赖目录
13. 验证 Debug 构建通过。

## 未完成

以下内容尚未进入实现阶段：

1. 系统文件选择器打开 `.md`、`.markdown`、`.txt` 文件。
2. Markdown 文件读取与 URI 持久化授权。
3. Markdown 内容渲染。
4. 编辑模式。
5. 保存到原文件。
6. 另存为 Markdown。
7. 新建备忘录。
8. 最近打开文件列表。
9. 文档内搜索。
10. 自动保存草稿。
11. 设置页。
12. 更完整的适老化字号、间距、触控面积验收。
13. 单元测试和 Compose UI 测试。

## 下一阶段计划

阶段 02 建议聚焦首个可用闭环：

1. 建立应用状态模型：
   - 当前文件名
   - 当前 URI
   - Markdown 原文
   - 是否有未保存修改
   - 是否处于编辑模式
   - 是否开启大字模式
2. 接入 Android Storage Access Framework：
   - 打开文件
   - 新建文件
   - 另存为文件
3. 实现基础文件读写：
   - 读取 UTF-8 Markdown 文本
   - 写回原文件
   - 保存失败时保留用户输入
4. 实现阅读/编辑切换：
   - 阅读页先使用轻量 Markdown 渲染或基础结构化预览
   - 编辑页使用多行文本编辑器
5. 保留阶段文档和构建验证记录。

## 构建验证

已执行：

```powershell
.\gradlew.bat :app:assembleDebug
```

结果：

```text
BUILD SUCCESSFUL
```

Debug APK 生成位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

该 APK 属于构建产物，已由 `.gitignore` 排除，不提交到仓库。

## 遇到的问题与解决方案

### 1. 首次构建下载耗时较长

现象：

- `gradlew.bat` 第一次运行时下载 Gradle 8.13 分发包，用时较长。

原因：

- 项目使用 Gradle Wrapper。首次执行 wrapper 时会下载指定版本的 Gradle 到用户 Gradle 缓存。

下载和安装位置：

- 临时手动下载：
  - `.codex_deps/gradle-8.13-bin.zip`，约 130.64 MB
  - `.codex_deps/gradle-8.13`，约 144.62 MB
- Gradle Wrapper 用户缓存：
  - `%USERPROFILE%/.gradle/wrapper/dists`

说明：

- `.codex_deps/` 是 Codex 临时目录，已被 `.gitignore` 排除。
- Gradle Wrapper 缓存位于用户目录，不属于项目仓库。

### 2. Android SDK 自动补装组件

现象：

- 构建时自动安装了 Android SDK Build-Tools 35 和 Android SDK Platform 36。

原因：

- 当前工程配置 `compileSdk = 36`、`targetSdk = 36`，而本机原先主要安装的是 Android SDK Platform 36.1 和 Build-Tools 36.1/37。

安装位置：

- `D:\AndroidDevelop\AndroidSdk\build-tools\35.0.0`，约 138.16 MB
- `D:\AndroidDevelop\AndroidSdk\platforms\android-36`，约 101.28 MB

说明：

- 这些是 Android SDK 的正常构建组件，不提交到仓库。

### 3. Kotlin 与 Java JVM target 不一致

现象：

```text
Inconsistent JVM-target compatibility detected for tasks
'compileDebugJavaWithJavac' (1.8) and 'compileDebugKotlin' (17).
```

原因：

- Kotlin 编译目标设置为 JVM 17，但 Android Java 编译任务仍使用默认 1.8。

解决方案：

在 `app/build.gradle.kts` 中增加：

```kotlin
compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
```

修复后重新执行 `.\gradlew.bat :app:assembleDebug`，构建成功。

## 参考与约束

本阶段遵循：

- 需求分析文档中的 Android only、本地备忘录、最低 Android 8.0、系统文件选择器、适老化大字模式等约束。
- Android/Jetpack Compose skills：
  - `android-cli`
  - `edge-to-edge`
  - `testing-setup`
  - `styles`

说明：

- `styles` skill 涉及实验性 Compose Styles API，本阶段未启用实验 API，只采用普通 Material 3 主题，避免引入不必要风险。

