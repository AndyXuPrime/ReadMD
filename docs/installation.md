# ReadMD 下载安装说明

本文说明如何获取、安装和构建 ReadMD。当前项目仍处于预发布阶段，已提供 GitHub Releases 预发布 APK。

## 1. 适合普通体验用户的安装方式

如果你已经拿到了 `app-debug.apk`，可以直接手动安装到 Android 手机。

### 1.1 手动复制 APK 到手机

1. 将 `app-debug.apk` 复制到手机。
2. 在手机文件管理器中找到并点击 `app-debug.apk`。
3. 按系统提示允许安装。
4. 安装完成后，在手机桌面或应用列表中打开 ReadMD。

注意：

- 不同 Android 系统可能要求你允许“安装未知来源应用”。
- Debug APK 可能显示为“不安全来源”，这是开发测试包的正常现象。
- Debug APK 仍主要用于开发测试；正式体验请优先使用 GitHub Releases 中的签名预发布 APK。

Android 设备要求：

- Android 8.0 或更高版本。
- 建议 Android 10 及以上，以获得更稳定的系统文件选择器体验。

## 2. APK 文件在哪里

如果你是从本项目源码构建，Debug APK 的生成位置是：

```text
app/build/outputs/apk/debug/app-debug.apk
```

当前预发布 Release APK 下载入口：<https://github.com/AndyXuPrime/ReadMD/releases/tag/v0.1.0>。

未配置签名环境时仍可以生成未签名 Release APK，用于本地观察正式包体积：

```powershell
.\gradlew.bat :app:assembleRelease
```

生成位置：

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

说明：

- `app-release-unsigned.apk` 未正式签名，通常不能直接作为公开安装包分发。
- Release 包已启用 R8 代码压缩和资源压缩，体积会明显小于 Debug APK。
- GitHub Actions 发布构建使用仓库外正式签名证书，通过 Secrets 注入；证书文件和密码不在仓库中。

## 3. 从源码构建 Debug APK

这一部分更适合开发者或希望自己编译 APK 的用户。

开发环境要求：

- Windows 10/11
- Android Studio
- Android SDK
- JDK 17 或更高版本
- Gradle Wrapper，项目已内置

在项目根目录执行：

```powershell
.\gradlew.bat :app:assembleDebug
```

构建成功后，APK 位置为：

```text
app/build/outputs/apk/debug/app-debug.apk
```

如需在安装前同时执行单元测试和构建，可以运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

说明：

- Debug APK 适合开发测试。
- Debug APK 不是正式发布包。
- 本地如需生成签名 Release APK，需设置 `READMD_SIGNING_STORE_FILE`、`READMD_SIGNING_STORE_PASSWORD`、`READMD_SIGNING_KEY_ALIAS` 和 `READMD_SIGNING_KEY_PASSWORD` 四个环境变量。

## 4. 开发者安装方式

### 4.1 使用 adb 安装

确保手机开启开发者选项和 USB 调试，然后连接电脑。

检查设备：

```powershell
adb devices
```

安装 APK：

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 4.2 使用 Android Studio 运行

1. 用 Android Studio 打开项目根目录。
2. 等待 Gradle Sync 完成。
3. 连接手机或启动模拟器。
4. 点击 Run。

本项目当前不强制要求模拟器验证，可以直接在真机上手动测试。

## 5. 常见问题

### 5.0 VS Code 提示 Gradle 初始化脚本不存在

如果 `build.gradle.kts` 顶部出现类似 `Could not run phased build action` 或 `specified initialization script ... redhat.java ... does not exist` 的提示，通常是 VS Code Java/Gradle 扩展缓存了旧的初始化脚本，不是项目脚本语法错误。

项目已在 `.vscode/settings.json` 中固定使用 Gradle Wrapper，并关闭 Gradle Build Server。修改配置后请执行一次：

1. `Developer: Reload Window`
2. `Java: Clean Java Language Server Workspace`
3. 重新打开项目根目录 `D:\ReadMD_proj\ReadMD`

命令行仍可用下面的项目自带 Wrapper 验证构建：

```powershell
.\gradlew.bat :app:assembleDebug
```

### 5.1 无法安装 APK

可以检查：

- 手机系统是否允许安装未知来源应用。
- 设备 Android 版本是否为 Android 8.0 或更高。
- APK 文件是否完整复制到手机。
- 如果使用 adb，手机是否开启 USB 调试并授权当前电脑。

### 5.2 Gradle 首次构建很慢

首次构建会下载 Gradle、Android Gradle Plugin、Kotlin、Compose 等依赖，耗时较长。

后续构建会使用本地缓存，速度会明显变快。

### 5.3 提示找不到 Android SDK

确认环境变量：

```text
ANDROID_HOME=D:\AndroidDevelop\AndroidSdk
ANDROID_SDK_ROOT=D:\AndroidDevelop\AndroidSdk
```

确认 PATH 包含：

```text
D:\AndroidDevelop\AndroidSdk\platform-tools
D:\AndroidDevelop\AndroidSdk\emulator
D:\AndroidDevelop\AndroidSdk\cmdline-tools\latest\bin
```

如果路径不同，请按你的本机 Android SDK 实际位置调整。

### 5.4 打开文件后无法保存

原因通常是系统文件选择器只授予了读取权限，或第三方文件提供方不允许写入。

解决方法：

- 使用“另存”保存为新 Markdown 文件。
- 将文件保存到本机文档目录后再编辑。

## 6. 发布版本计划

正式 Release 构建使用仓库外的签名证书。CI 通过以下 Secrets 注入签名信息，不把 `.jks` 文件、密码或私钥提交到仓库：

- `READMD_RELEASE_KEYSTORE_BASE64`
- `READMD_RELEASE_STORE_PASSWORD`
- `READMD_RELEASE_KEY_ALIAS`
- `READMD_RELEASE_KEY_PASSWORD`

推送形如 `v0.1.0` 的版本标签后，GitHub Actions 会执行完整门禁、验证 APK 签名，并创建 GitHub 预发布版。

后续可以增加：

- 版本更新说明

当前阶段以签名预发布 APK 和手动安装为主；正式版本仍需继续积累真机反馈后再决定。
