# 项目范围

首个目标只有一条用户选定的 Desktop 原生测试任务：手机读取其文字历史、发送一次文字，Desktop 在同一任务接续后手机可见。多电脑路由由中继区分目标，但首版只验一台电脑的一条任务。Desktop 是外部执行器及任务拥有者；项目不启动独立 Codex 执行器、不复制任务、不接管旧 Gateway。

| 组件 | 本仓库目录 | 保留的职责 |
| --- | --- | --- |
| Android 遥控 UI | `android-native/` | 复用原生聊天、导航、草稿与视觉；发往已授权电脑。原 UI 有按钮不等于后端功能已接通。 |
| Windows 连接器 | `windows/`、`src/` | 配对、独立 FRP/loopback 通道、受限任务跟随、Desktop IPC、权限核对和发送回执；不持有原生任务。 |
| 服务器中继 | `relay/` | 账户/设备授权、电脑路由、受控转发；不执行任务或保存 Desktop 原生对话。 |

身份分别由中继认证与本机 CurrentUser DPAPI 配对状态管理；Desktop 持有任务数据和执行权限。未知发送结果不自动重发。详细动作、源码复用和信任边界见 [原型说明](docs/PROTOTYPES.md)、[架构关系](docs/ARCHITECTURE.md)、[PROVENANCE.md](PROVENANCE.md)。

用户新增的正式要求是：**三个原型都必须支持 OTA 迭代版本**。Android 使用应用内检查与系统确认安装，Windows 使用窗口入口与独立更新助手，中继使用管理员在线更新工具；完整要求见 [docs/OTA.md](docs/OTA.md)。保留简化界面与同一原生任务的核心范围，不额外建设管理网站。

alpha.3 已加入三端 OTA 实现与全新受管安装引导；安装包为源码 ZIP，外部运行时仍需首次准备，没有一体化 EXE 安装器或托盘。尚无生产更新源或真实用户升级证据。旧 Gateway 数据迁移、原身份复制仍不在范围内。Android 新包、提取后的中继、Windows 新启动周期以及公网链路各要独立验收；此前 Lite 的单任务手机文字闭环是来源证据，不是新仓库全链路验收。以 [STATUS.md](STATUS.md) 记录当前结论，故障与旧实例限制见 [RECOVERY.md](RECOVERY.md)。
