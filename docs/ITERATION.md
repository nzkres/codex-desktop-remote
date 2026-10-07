# 小步迭代

版本从 `0.1.0-alpha.1` 起。每轮只定义一个可观察的小目标和相应失败边界，例如“同一专用任务断开后重连并继续显示文字”；不顺手扩大到旧 Gateway 迁移、安装器或公网切换。

1. 在 [STATUS.md](../STATUS.md) 写清当前证据与本轮验收条件。修改前先读 [UI-BASELINE.md](UI-BASELINE.md)；保留原聊天布局、导航与草稿，不因源 UI 有按钮就声称新建/审批/模式可用。
2. 用合成身份、临时数据库和随机回环端口完成针对性测试，运行 [TESTING.md](TESTING.md) 的统一自测；Android 修改另跑其 Gradle 检查。失败和未知操作保持阻断。
3. 在专用 Desktop 任务和新手动确认的测试设备上做明确的人工验收，分别记录代码、CI、构件、安装、真实手机/桌面结果。原公网服务与已安装旧 APK 保持原状。
4. 准备测试版本时同步 `package.json` 与 `android-native/app/build.gradle.kts` 的版本号/版本码，更新根 [CHANGELOG.md](../CHANGELOG.md) 和状态证据，再创建 tag 与 GitHub private prerelease。开发预览可以在真机验收前发布，但发布说明必须列明未验收项；不能标为稳定版。CI artifact 仅是临时下载物，不是 OTA 或稳定签名发布；连续覆盖安装须同一私有签名。

任何未完成层级都留在 STATUS 的待验收项中；不把合成测试、CI 成功或 relay 探针换算成真实任务完成。
