## R6确认后正式迭代

R6的先100再回实际、数字遮光及斜向散光接入alpha.20草稿，同时加入纯Loading、运行状态、阅读水位和首页头部精简。预览与正式功能隔离，R预览仍独立刷新。发布、真机与真实任务证据见STATUS。

## alpha.19 本地正文修复

alpha.18预览下载成功后误拦data主文档，真机截图为ERR_HTTP_RESPONSE_CODE_FAILURE。alpha.19修复宿主，不需要重发R3；预览仅合成数据，正式对话/连接/回执不改。

## alpha.18 独立更新的手机预览

用户明确要求我的预览保留、避免每次APK更新。RemotePreviewDialog增加刷新与独立修订，只有兼容演示片段可更新，不扩充真实任务执行能力；旧原生预览作为内置示例。docs/prototypes/quota-live-preview.html是首个合成演示，可由scripts.preview_feed签名，服务器独立发布。预览变更不递增APP；宿主或原生能力变更仍按正常版本流程。

## alpha.17 原生额度动效预览

我的 → 界面预览 · 额度与交互默认进入额度动效，顶部可回原未读/发送预览。两个同账号设备和一个不同账号设备可分别开关任务，五种额度皮肤可切换；演示数字固定。正式数据与协议没有接入，预览不证明真实账号聚合或任务执行。

## 手机内离线界面预览（alpha.16）

原私有网页在手机上打开受限，新增原生预览入口，不依赖网页或外部登录。NativeInterfacePreview仅接收关闭回调，复用TerminalGrid、GlassMaterial、ComposerEditor和矢量图标；InterfacePreviewState是纯合成状态。正式功能未接入，不把演示当作实际运行或回执。

## 已封存预览：任务仓库与多电脑对话（2026-10-15）

[批准预览](prototypes/project-hub-approved-20261015.html) 仅包含合成数据。SHA-256：`d0da01cdb126d0e2b69512fd5b6e66032117322c5f20713537a88aa6c7c201ea`。用户明确授权按此版开发到Desktop Remote Lite并发布手机更新。浏览器中的模拟回执不作为产品实际收到答案或已安装的证据。


# 三个原型

本项目先验证一件事：手机向已选电脑上的 **同一个 Desktop 原生任务**发送文字，Desktop 可接着处理，手机再读到该任务的结果。三个原型共同组成这条链路；Desktop 是外部执行器和任务拥有者，不是要重写的第四个产品。当前证据和未完成项以根目录 [STATUS.md](../STATUS.md) 为准。

| 原型 | 用户动作与输入 | 可见输出 | 职责边界及原代码映射 |
| --- | --- | --- | --- |
| 手机遥控 UI | 用户配对并选择已授权电脑、打开任务、发文字、查看同一任务的后续内容。输入是配对信息、电脑/任务选择和文字。 | 显示目标电脑、发送结果及按游标同步的任务内容；不把连接状态当成任务完成。 | 沿用原 `android-native/app/src/main/java/dev/codex/remote/` 的 `MainActivity.kt`、`Gateway.kt`、`TaskTarget.kt`、`ChatScreen.kt` 及相关 UI。它不执行 Codex、不保存 Desktop 原生任务的权威记录，也不替换用户手机上现装 APK。新仓库的 Android 导入和新包验收须另行记录。 |
| Windows 连接器 | 用户在本机指定受支持的 Desktop 安装、配对并明确选定任务，随后连接或正常断开。输入是本机设置、受限任务选择和配对凭据。 | 提供本机适配端点、连接/操作回执与明确的未知状态；把请求送往已运行的 Desktop。 | `windows/controller.py`、`windows/gui.py`、`src/{main,service,follower,receipts,compatibility}.js` 及 `windows/vendor/` 承接 Lite；原代码对应关系见 [PROVENANCE.md](../PROVENANCE.md)。本机配对身份受 CurrentUser DPAPI 保护。连接器不接管 Desktop 任务所有权，不以进程/端口空闲推断安全迁移。 |
| 服务器中继 | 用户经授权入口选择目标电脑并配对；手机请求按电脑路由到该电脑连接器。输入是账户/设备授权与目标电脑标识。 | 返回目标电脑的转发响应或明确错误。 | 原 `relay-auth/`、`relay-control/control_server.py`、`forwarder.py`、`gateway_link.py` 等是提取来源。中继负责认证、设备绑定和路由，不执行任务、不持有 Desktop 原生对话的权威状态。新仓库提取物需单独测试；原公网服务保持不变。 |

UI 以原 `docs/beginner-guide/15-界面风格规范.md` 为视觉基线；本仓库已保存副本 [UI-BASELINE.md](UI-BASELINE.md)。沿用原生聊天布局、字体/层次、动效与草稿行为，不另起一套视觉语言或管理后台。中继源码已经放入 `relay/`，认证适配在 `relay/auth/`，不是只有旧仓库路径引用。

三个原型均新增 **OTA 为必做能力**：手机在现有“我的”页检查并交由系统安装；Windows 在原窗口检查、由独立助手切换；中继由管理员在线更新工具执行。首次安装建立信任，后续更新保留身份与授权，不要求用户每版重新构建或手动上传程序。当前 alpha.2 尚未实现，边界与验收见 [OTA.md](OTA.md)。

路由设计允许区分多台电脑；首版实际验收只聚焦一台已选电脑上的一条专用任务。alpha.11 候选按用户要求增加显式任务白名单，保留原测试任务并允许加入当前开发对话；任务ID、目录和权限各自核对，不开放全部电脑任务。新增 Android 构建、提取后的中继、公网部署和现装 APK 切换均不能由旧项目或 Lite 的通过记录推定成功。

### alpha.13 真实数据接入

GitHub项目清单从仓库文档只读同步，补充review状态防止缺失证据被当作构思；额度背景光映射各电脑真实快照。沿用已批准的玻璃、多电脑横向名字与下划线，无新独立页面。真机动态效果待安装验收。
