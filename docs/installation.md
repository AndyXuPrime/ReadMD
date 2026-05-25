# ReadMD 下载安装说明

本文说明如何从源码构建和安装 ReadMD。当前项目仍处于开发阶段，尚未提供正式应用商店版本。

## 1. 环境要求

开发环境：

- Windows 10/11
- Android Studio
- Android SDK
- JDK 17 或更高版本
- Gradle Wrapper，项目已内置

Android 设备：

- Android 8.0 或更高版本
- 建议 Android 10 及以上，以获得更稳定的系统文件选择器体验

## 2. 从源码构建 Debug APK

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
- 当前项目尚未配置正式签名证书。

## 3. 安装到手机

### 方法一：使用 adb 安装

确保手机开启开发者选项和 USB 调试，然后连接电脑。

检查设备：

```powershell
adb devices
```

安装 APK：

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 方法二：手动复制 APK

1. 将 `app-debug.apk` 复制到手机。
2. 在手机文件管理器中点击 APK。
3. 按系统提示允许安装。

注意：

- 不同 Android 系统可能要求你允许“安装未知来源应用”。
- Debug APK 可能显示为“不安全来源”，这是开发包的正常现象。

## 4. 使用 Android Studio 运行

1. 用 Android Studio 打开项目根目录。
2. 等待 Gradle Sync 完成。
3. 连接手机或启动模拟器。
4. 点击 Run。

本项目当前不强制要求模拟器验证，你可以直接在真机上手动测试。

## 5. 常见问题

### 5.1 Gradle 首次构建很慢

首次构建会下载 Gradle、Android Gradle Plugin、Kotlin、Compose 等依赖，耗时较长。

后续构建会使用本地缓存，速度会明显变快。

### 5.2 提示找不到 Android SDK

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

### 5.3 无法安装 APK

可以检查：

- 手机是否开启 USB 调试。
- 是否授权当前电脑调试。
- 手机系统是否允许安装未知来源应用。
- 设备 Android 版本是否为 Android 8.0 或更高。

### 5.4 打开文件后无法保存

原因通常是系统文件选择器只授予了读取权限，或第三方文件提供方不允许写入。

解决方法：

- 使用“另存”保存为新 Markdown 文件。
- 将文件保存到本机文档目录后再编辑。

## 6. 发布版本计划

后续可以增加：

- Release 构建配置
- 正式签名
- GitHub Releases 自动上传 APK
- 版本更新说明

当前阶段先以 Debug APK 和手动安装为主。
