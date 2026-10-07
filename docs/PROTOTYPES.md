# 三个原型

本项目先验证一件事：手机向已选电脑上的 **同一个 Desktop 原生任务**发送文字，Desktop 可接着处理，手机再读到该任务的结果。三个原型共同组成这条链路；Desktop 是外部执行器和任务拥有者，不是要重写的第四个产品。当前证据和未完成项以根目录 [STATUS.md](../STATUS.md) 为准。

| 原型 | 用户动作与输入 | 可见输出 | 职责边界及原代码映射 |
| --- | --- | --- | --- |
| 手机遥控 UI | 用户配对并选择已授权电脑、打开任务、发文字、查看同一任务的后续内容。输入是配对信息、电脑/任务选择和文字。 | 显示目标电脑、发送结果及按游标同步的任务内容；不把连接状态当成任务完成。 | 沿用原 `android-native/app/src/main/java/dev/codex/remote/` 的 `MainActivity.kt`、`Gateway.kt`、`TaskTarget.kt`、`ChatScreen.kt` 及相关 UI。它不执行 Codex、不保存 Desktop 原生任务的权威记录，也不替换用户手机上现装 APK。新仓库的 Android 导入和新包验收须另行记录。 |
| Windows 连接器 | 用户在本机指定受支持的 Desktop 安装、配对并明确选定任务，随后连接或正常断开。输入是本机设置、受限任务选择和配对凭据。 | 提供本机适配端点、连接/操作回执与明确的未知状态；把请求送往已运行的 Desktop。 | `windows/controller.py`、`windows/gui.py`、`src/{main,service,follower,receipts,compatibility}.js` 及 `windows/vendor/` 承接 Lite；原代码对应关系见 [PROVENANCE.md](../PROVENANCE.md)。本机配对身份受 CurrentUser DPAPI 保护。连接器不接管 Desktop 任务所有权，不以进程/端口空闲推断安全迁移。 |
| 服务器中继 | 用户经授权入口选择目标电脑并配对；手机请求按电脑路由到该电脑连接器。输入是账户/设备授权与目标电脑标识。 | 返回目标电脑的转发响应或明确错误。 | 原 `relay-auth/`、`relay-control/control_server.py`、`forwarder.py`、`gateway_link.py` 等是提取来源。中继负责认证、设备绑定和路由，不执行任务、不持有 Desktop 原生对话的权威状态。新仓库提取物需单独测试；原公网服务保持不变。 |

UI 以原 `docs/beginner-guide/15-界面风格规范.md` 为视觉基线；本仓库已保存副本 [UI-BASELINE.md](UI-BASELINE.md)。沿用原生聊天布局、字体/层次、动效与草稿行为，不另起一套视觉语言或管理后台。中继源码已经放入 `relay/`，认证适配在 `relay/auth/`，不是只有旧仓库路径引用。

路由设计允许区分多台电脑；首版实际验收只聚焦一台已选电脑上的一条专用任务。新增 Android 构建、提取后的中继、公网部署和现装 APK 切换均不能由旧项目或 Lite 的通过记录推定成功。
