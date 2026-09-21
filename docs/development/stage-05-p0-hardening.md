# ReadMD 阶段 05：P0 稳定性门禁与数据保护

日期：2026-09-21

## 本阶段范围

本阶段保持现有 UI 风格，集中处理自动化门禁、草稿可靠性、本地隐私、未保存内容确认、SAF 授权、Android 8 兼容、首页状态和编码识别。

## 核心约束

- 草稿正文写入 `noBackupFilesDir` 的原子文件，不写入 SharedPreferences。
- 输入期间使用 750ms 防抖，Activity 进入后台时主动刷新草稿。
- 应用关闭 Android 数据备份，文档、草稿、摘要和最近 URI 不进入系统云备份。
- 离开未保存文档前必须选择保存、放弃或继续编辑。
- 新建和另存文件必须持久化系统返回的 URI 授权。
- UTF-16 只接受带 BOM 的文件；无 BOM 文本依次严格尝试 UTF-8 和 GB18030。
- 阶段 04 第 9 节的防回归清单进入 `docs/testing.md` 和 CI。

## 验证命令

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug :app:assembleDebug :app:assembleRelease
```

## 本次验证结果

- JVM 单元测试：38 项通过，0 失败。
- Android 仪器测试 APK：编译通过；本机无已连接设备，尚未执行 `connectedDebugAndroidTest`。
- Android Lint：0 error；没有配置 lint baseline。
- Debug APK：构建通过，约 11.8 MB。
- Release APK：R8 与资源压缩构建通过，未签名产物约 1.8 MB。
