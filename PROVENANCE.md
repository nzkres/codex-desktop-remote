# 复用清单

本独立仓库的 Windows/Node 起点为 Desktop Remote Lite `2fcf9e22ba28923cbbd36afd56ac34d0d0ee7652`（原版本 `0.0.2-test.1`）。新仓库版本从 `0.1.0-alpha.1` 开始，保留协议、安全检查和退出机制，服务器地址由用户填写。自 alpha.2 起，Windows/Node/心跳版本来自 `windows/releases.json`，根 package.json 由校验器保持一致；Android 与 relay 各自维护同名记录文件，详见 [迭代规则](docs/ITERATION.md)。

Android 与中继起初直接提取自下述旧提交：`android-native/` 保留源码、测试、字体；独立 wrapper 替代旧 HAPI 子模块构建依赖，使用新应用标识，删除旧签名私钥/OTA；`relay/` 保留 registry、control_server、frp_policy、forwarder、gateway_link、transport_settings，`relay/auth/` 保留单账号 auth_server。提取后 alpha.2 新增 Android 离线版本入口、中继版本命令及账号认证后的版本查询，改动与旧协议复用分别见组件版本历史。Android/HAPI/字体许可见 `third-party/licenses/` 与 `android-native/FONTS.md`。

未迁入真实 DPAPI 身份、账号/设备令牌、数据库、APK、运行日志、服务器配置、旧安装器或升级执行器。

来源：用户原仓库 `nzkres/personal-codex-remote`，本机独立副本的提交 `f8350e400cf6e7420d8283e03eb6efa37104ca79`。保持原协议与安全核心；没有复制原 Android APK、真实配置、数据库、身份或日志。

| 本项目文件 | 原源码 | 处理 |
| --- | --- | --- |
| `src/vendor/desktop-ipc.js` | `apps/server/src/desktop-ipc.ts` 的既有编译产物 | 移除 source map 注释；不注册任何 owner handler |
| `src/vendor/desktop-state.js` | `apps/server/src/desktop-state.ts` | 只保留 nativeTurns、activeTurn、assertIdle；删除状态发布/执行路径 |
| `src/vendor/mobile-sync.js` | `apps/server/src/mobile-sync.ts` | 保留同步投影与游标；移除 Fastify 路由装配 |
| `src/vendor/conversation-timing.js` | `apps/server/src/conversation-timing.ts` | 仅保留纯时间字段转换；不读取 rollout 或 Desktop 数据库 |
| `windows/vendor/connector_core.py` | `connector/windows/connector_core.py` | 复用配对、DPAPI、锁、心跳；删除无用旧 CLI 入口；Lite 正常退出增加完整绑定与状态 CAS |
| `windows/vendor/runtime_core.py` | `connector/windows/runtime_core.py` | 只保留 LoopbackHealthProbe、OwnedProcess、tunnel_probe；删除旧 Gateway/adoption/profile 逻辑 |
| `windows/vendor/frp_config.py` | `connector/windows/frp_config.py` | 原样复用 |
| `windows/vendor/windows_job.py`、`transport_worker.py` | 同名原文件 | 基于原 FRP Job 机制扩展至本项目 Node adapter；加入启动管道、原始句柄分配、Job 活进程计数与退出证明；不用于旧 Gateway 或 Desktop |
| `windows/vendor/connection_settings.py` | `connector/windows/managed_runtime.py` | 仅提取 validate_connection、public_asset；保留 TLS/身份/地址/证书校验 |

实际 Desktop 安装代码已只读核对：start-turn version 2、interrupt version 4，owner-discovery 1；start 的回执仍有两层 result，`inheritThreadSettings` 语义在本机专用测试任务实际验证。没有修改 Desktop 安装文件。

本项目没有复用旧 Gateway 的 app-server、数据库、升级执行器或权限接管逻辑。旧版本/CI 记录不是本项目的验收证据。
