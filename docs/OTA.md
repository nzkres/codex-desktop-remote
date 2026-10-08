# 三端 OTA：当前实现边界

Android、Windows连接器和Linux中继都有更新实现。alpha.5已建立专用测试RSA/APK签名与固定test信任，受控构建、下载复核和Windows/WSL临时首装通过；隔离服务器已安装测试 HTTPS 私有源、relay 与 FRPS，并完成服务器内合成验收。**Windows 外网到 `9443`、`17443` 仍超时，公网可用性、三端真实在线升级和生产渠道均未验收。**代码、CI、构件、隔离部署和真实设备结果分别以[STATUS](../STATUS.md)及[部署记录](OTA-ISOLATED-DEPLOYMENT-20261008.md)为准。服务器内下载不等于手机或电脑可在线升级。

统一协议见 [wire contract](../updates/PROTOCOL.md)。首次安装固定渠道、HTTPS source 和 RSA 3072 位以上发布公钥；签名覆盖原始 payload 字节，包另校验大小与 SHA-256。测试与稳定渠道分别配置信任根。无公钥或无源时明确拒绝，不能从一次未经验证的下载取得新公钥。拒绝重定向、跨源目录、过期、序号倒退、同序号换内容、同版本换包和不兼容目标。发行私钥、Android 签名密钥与服务器凭据不入库、包或日志。

alpha.4 的私有下载：Android 使用现有加密登录会话，Windows 使用已配对 DPAPI 设备身份，relay 管理员先交互 `login` 再更新。凭据仅发往已登录/配对 relay 的同 HTTPS origin 固定目录；服务端对元数据和包分别鉴权。账号可下载三端，未暂停/撤销的设备仅可下载 Windows。拒绝匿名/过期/错误凭据，不给客户端嵌入 PAT。源须反代到受管 API，不能公开为静态目录。首轮资源、N→N+1 顺序和 BUG 批次见 [落地验收](OTA-ACCEPTANCE.md)。

| 组件 | 用户入口 | 完成前仍须核对 |
| --- | --- | --- |
| Android | “我的 → 版本与更新说明”检查、下载，系统确认安装 | 同包名及同 APK 签名证书；重开后实际 version/versionCode、APK 哈希、ledger 和新鲜任务证据。取消安装可重试，不是成功。普通 CI debug 包信任为空、签名不连续，不可用于生产 OTA。 |
| Windows | 受管安装窗口内检查、下载并安装，独立助手等原窗口退出 | Desktop 任务空闲、权限和回执确定，旧运行时正常清理；候选隔离验证、数据摘要不变后才激活。未知激活结果保留 `needs_review`，不杀可能已接单的候选。 |
| Linux 中继 | 管理员在受管宿主本机执行 `check/stage/apply/status` | 维护门禁先排空写入和配对，连接器实时证明空闲；委派 cgroup v2 证明进程树退出，新服务和数据摘要验证后开放。未知状态保持门禁与现场。 |

三端版本独立，更新器不升级 Codex Desktop/CLI，不迁移旧身份或现用 Gateway/中继。Windows 与 relay 的首次安装**只能创建新目录**。安装与启动见 [安装起点](install/README.md)，离线准备和发布见 [OTA-PUBLISH](OTA-PUBLISH.md)，阻断与恢复见 [OTA-RECOVERY](OTA-RECOVERY.md)。同源私有 HTTPS 路由已在独立测试服务器实例中验证；外网连通、正式生产密钥及运营部署尚待后续验收。14 天 Actions artifact、私有 GitHub Release 下载和本地源码 ZIP 都不能替代受鉴权在线源。

验收须使用同一信任基线的两个真实构件完成 N→N+1 在线下载和实际切换，并分别记录错误签名、坏包、活跃/未知任务、空间不足、中断、树清理失败及身份/数据保留。Android 覆盖安装还须有相同签名证书的真机证据；中继还须有隔离维护窗口的服务重启证据。不能把按钮、签名包生成或测试通过写成升级完成。
