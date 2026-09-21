# ReadMD 测试与防回归门禁

本文把阶段 04 第 9 节的历史问题转换为持续执行的测试清单。自动化检查不能替代真机手势、输入法和文件提供方兼容性验证。

## 自动化命令

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug :app:assembleDebug :app:assembleRelease
```

- 本地单元测试位于 `app/src/test`。
- Compose、SAF contract 和伪 ContentResolver 测试位于 `app/src/androidTest`。
- `assembleDebugAndroidTest` 保证设备测试可编译；连接设备后运行 `connectedDebugAndroidTest`。
- GitHub Actions 对每次 push 和 pull request 执行同一质量门禁。
- 不使用 lint baseline 隐藏错误。

## 阶段 04 防回归矩阵

| 历史约束 | 自动化覆盖 | 真机验收 |
| --- | --- | --- |
| 1. 阅读缩放不影响首页 | ViewModel 局部缩放测试 | 放大后返回首页检查布局 |
| 2. 双指缩放不破坏单指滚动 | AndroidView 触摸实现与 Lint | 长文单指滚动、双指缩放 |
| 3. 样式变化不重复整文渲染 | Markdown 渲染键逻辑 | 长文切换主题、缩放观察卡顿 |
| 4. 字号上限 155%，大字不重叠 | 缩放边界测试、Compose 大字测试 | 四种主题组合检查按钮和标题 |
| 5. 草稿恢复不形成闪退循环 | ViewModel 恢复与防抖测试 | 新建/编辑后杀进程并恢复 |
| 6. 已有文件阅读优先，新建编辑优先 | ViewModel 状态测试 | 导入、新建、保存后页面检查 |
| 7. 成功打开不弹提示，失败必须提示 | ViewModel 消息状态测试 | 导入成功和失败各验证一次 |
| 8. 保存写回原文件，另存创建副本 | Fake Repository 与 SAF contract 测试 | 可写、只读、授权失效文件 |
| 9. 仅支持 md/markdown/txt | 文件类型单元测试 | 尝试 PDF、PNG、ZIP |
| 10. 2MB 上限和 120000 字符预览边界 | 已知/未知大小流测试 | 接近及超过 2MB 文件 |
| 11. 最近记录最多 20 条且去重 | 伪 ContentResolver 仓库测试 | 连续打开 21 个文件 |
| 12. Release 保持压缩且小于 Debug | CI 同时构建两种 APK | 发布前记录产物大小 |
| 13. 阅读定位和编辑近似同步 | 偏移算法测试 | 70% 位置进入编辑页 |

## 每次发布前的设备矩阵

- Android 8、10、12、14、16 各至少一台设备或模拟器。
- 至少一种国产 Android 系统和两种输入法。
- 日间、夜间、大字日间、大字夜间四种组合。
- 普通文档、长文档、公式、表格、任务列表、GB18030 文档。
- 可写文档、只读文档、授权失效记录、未知大小流、超过 2MB 文件。
