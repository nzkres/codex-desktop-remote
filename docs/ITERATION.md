# 小步迭代

三个软件分别从 `0.1.0-alpha.1` 起记录版本，本次各自新增 `0.1.0-alpha.2`。以后只更新发生变化的组件，三个版本不必相同。每轮只定义一个可观察的小目标和相应失败边界，例如“同一专用任务断开后重连并继续显示文字”。

| 软件 | 唯一版本记录 | 用户在哪里看 | 仓库可读历史 |
| --- | --- | --- | --- |
| 手机 App | `android-native/releases.json` | 我的 → 版本与更新说明；离线读取已安装包中的历史 | [手机历史](versions/ANDROID.md) |
| Windows 连接器 | `windows/releases.json` | 窗口底部版本与更新说明；适配器/心跳使用同版本 | [Windows 历史](versions/WINDOWS.md) |
| 服务器中继 | `relay/releases.json` | 本地 `--version` / `--release-notes`；已登录的 `/control-api/version` 查询实际运行服务 | [中继历史](versions/RELAY.md) |

每个版本必须写日期、摘要、限制；每项变化必须有 **更新内容（title）、在哪里体验（experience）、怎样确认生效（verify）**。例如“新增版本入口”要写“我的 → 版本与更新说明；看到本版和上一版，返回后草稿不变”，不能只写“优化体验”。历史条目保留，新版本插在最前面。

1. 在 [STATUS.md](../STATUS.md) 写清当前证据与本轮验收条件。修改前先读 [UI-BASELINE.md](UI-BASELINE.md)；保留原聊天布局、导航与草稿，不因源 UI 有按钮就声称新建/审批/模式可用。
2. 用合成身份、临时数据库和随机回环端口完成针对性测试，运行 [TESTING.md](TESTING.md) 的统一自测；Android 修改另跑其 Gradle 检查。失败和未知操作保持阻断。
3. 在专用 Desktop 任务和新手动确认的测试设备上做明确的人工验收，分别记录代码、CI、构件、安装、真实手机/桌面结果。原公网服务与已安装旧 APK 保持原状。
4. 准备测试版本时，只在变化组件的 `releases.json` 新增记录。Android 的 `versionCode` 必须递增，Gradle 自动读取版本名/版本码并打包同一份记录；Windows 版本同时同步根 `package.json`。中继独立迭代，不能把手机或 Windows 的版本当作服务器版本。
5. 运行 `python scripts/check_versions.py --write` 生成三份仓库历史，再运行 `python scripts/check_versions.py` 做只读校验。SelfTest 和 CI 都执行校验：版本重复/倒序、版本码倒退、缺少体验或验证步骤、Windows 包版本不一致、生成文档过期均失败。
6. Android 构建后必须运行 `python scripts/check_android_apk.py`，核对 APK 内确实包含同一份说明以及构建输出版本相符；CI 已执行此门槛。下载构件后可用 `--apk` 和 `--metadata` 参数再验，不能只看编译成功。
7. 更新根 [CHANGELOG.md](../CHANGELOG.md) 和状态证据，再创建 tag 与 GitHub private prerelease；发布说明列明三个组件版本及各自验收结果。开发预览可以在真机验收前发布，但必须列明未验收项，不能标为稳定版。CI artifact 仅是临时下载物，不是 OTA 或稳定签名发布；连续覆盖安装须同一私有签名。

任何未完成层级都留在 STATUS 的待验收项中；不把合成测试、CI 成功或 relay 探针换算成真实任务完成。

运行中软件显示启动时加载的记录，手机显示 APK 内的记录。只改仓库文件不会升级已运行进程；旧中继未实现版本接口时应视为“版本未知”，不能从新仓库版本猜测。服务器仍须单独部署并核验后才能记录部署成功。
