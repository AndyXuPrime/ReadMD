# ReadMD 阶段 03 补充记录

日期：2026-05-24

## 本次修复

- 修复 `MainActivity` 中 `rememberSaveable` 的导入问题，以及 Material3 实验性 API 的编译告警。
- 将 Markdown 文件读取改为带字节上限的流式读取，降低 Android 10/12 机型打开未知大小文件时闪退的风险。
- 调整适老化按钮的字号和最小高度，让大字版切换更明显。

## 验证

- `.\gradlew.bat :app:assembleDebug`
- 结果：`BUILD SUCCESSFUL`

## 备注

- 当前没有真机联调日志，Android 10/12 的闪退问题已先从文件读取链路做了防护。
