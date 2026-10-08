# OTA 交接

**配对恢复停点：** 用户手机已登录并生成申请，电脑地址误填 `:944`，首次安装身份停在claim_pending/connection_unavailable。保留原CodexDesktopRemoteTest全部状态，不重置、不改身份端点重放。另一个物理目录CodexDesktopRemoteTest9443已完成原签名包首装和只读任务probe、权限一致性核对。用户已关闭旧窗口并取消手机申请；取得原应用/运行/操作锁、核对pending状态未变后，新目录的原Start.pyw已实际打开alpha.5窗口。当前等待用户手动填写完整 `https://106.53.126.69:9443` 和新配对码，在手机确认后电脑连接。具体路径仅在本机忽略报告windows-retry-install.json；账号显示工具仍在原目录operator，不迁移秘密。新配对、手机任务与OTA尚未验收。

**最新停点（2026-10-08 实机阶段）：** 主电脑全新安装原始签名 alpha.5，用户手动完成 `OTA_ALPHA5_READY` 后，原任务 `01a1158c-72ea-7db3-84d1-b47471220252`「回复 DESKTOP_REMOTE_READY」的实时历史/权限/空闲探针通过。图形窗口已打开，当前等手机登录、配对；用户报告APK已安装并在登录页。先读[设备基线记录](OTA-DEVICE-BASELINE-20261008.md)：AppX重定向曾导致逻辑home启动失败，当前使用已核验物理home的原Start.pyw，不能重复bootstrap/configure或修改签名槽。新账号在本机DPAPI辅助窗口交接，不要求用户在聊天发送口令/配对码。以下部署记录是历史阶段证据；尚未真实任务N基线或alpha.6 OTA验收。

2026-10-08：用户已批准并完成PR #1合并、专用测试签名、六项Android Secrets、alpha.5构建/复验，以及现有服务器上的**独立隔离测试部署**。服务器内安装与验收、主电脑公网 `9443` HTTPS 鉴权下载和 `17443` FRPS 合成夹具连接已通过；现用服务和身份保持原样。手机安装、真实身份迁移和真实任务N→N+1仍未获本轮授权或验收。详细证据见[隔离部署记录](OTA-ISOLATED-DEPLOYMENT-20261008.md)。

先读[STATUS](../STATUS.md)、[隔离部署记录](OTA-ISOLATED-DEPLOYMENT-20261008.md)、[落地验收](OTA-ACCEPTANCE.md)、[部署审查](OTA-DEPLOYMENT-REVIEW.md)、[发布](OTA-PUBLISH.md)、[安装起点](install/README.md)和[恢复](OTA-RECOVERY.md)。保留旧alpha.4资产，核对当前远端、分支及未提交改动后再继续；不要只凭本页旧提交猜测最新状态。

## 已完成的受控基线

- PR #1合并提交`1e405ad`。alpha.5程序输入为`1a4b4c0ced91d1a384e723374a6141d84e43de9e`，分支`codex/controlled-alpha5`经Core37736389583/Android37736389589通过后，main非强制快进。后续文档提交不改变这些程序输入。
- main受控Android构建[37736837251](https://github.com/nzkres/codex-desktop-remote/actions/runs/37736837251)成功：94 JVM、lint0errors/45warnings、apksigner证书验证和包内trust比对。实际APK下载SHA与CI相同，AAPT2核对包名/code5/minSdk26。它仍为debuggable测试构建，不是生产发行包。
- [alpha.5私有归档](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.5)已发布，七项资产服务端大小/哈希与本地一致，见STATUS。包含APK、Windows/relay源码ZIP、bootstrap公开信任/已签元数据、Android报告、verification和SHA256SUMS。三端版本说明在仓库`docs/versions/`，不以Release文字代替。
- Windows43/relay29源码文件与Git输入逐字节相符。三端实际包已签RSA元数据；Windows Python3.12/WSL Python3.14在全新临时home通过bootstrap、账本与重复安装拒绝。没有启动真实连接或使用账号/身份；真实GUI/helper及cgroup升级/回退来自CI合成演练。

## 签名资料与有效期

专用库在本机`%LOCALAPPDATA%\CodexDesktopRemoteSigning\test-v1`，CurrentUser DPAPI保护RSA和Android加密PKCS12/口令。RSA私钥没有上传；Android六项Secrets已配置在固定私有仓库。`github-provision.json`为configured。**不要重复执行create/provision、覆盖文件或重新生成同名证书。** Git克隆不能恢复该库；独立备份和跨电脑恢复尚未实现/验收，不把复制DPAPI文件当作可靠备份。

keyId为`cdr-ota-test-20261008-v1`，公开SPKI/APK证书指纹见STATUS。首次trust须核对已认证仓库中的公开指纹；同页SHA表只能核对完整性。后续Android构建必须沿用同一APK签名证书，RSA发布私钥不传CI/服务器，不在客户端嵌入PAT。

三端alpha.5元数据为test、sequence1，固定`https://106.53.126.69:9443/desktop-remote-updates/test/{component}/latest.json`，有效至**2026-10-15 06:33:03 UTC（北京时间14:33:03）**。部署/首装前重查时间。过期时由维护者以新序号重新签发相同已核验程序包，单独归档新元数据；不能修改已签字段、替换同版本包或删除ledger绕过保护。

## 隔离服务器结果与最小下一步

106.53.126.69 上已以独立 `cdrtest` 账号、目录/数据库和端口完成 alpha.5 relay、FRPS、HTTPS 与私有 OTA 安装；目标环境为 Python3.12.3、systemd255、cgroupv2。服务器 loopback 经正常 IP 证书 HTTPS 完成三组件账号下载及 RSA/哈希/大小验证，六项匿名请求获401；合成设备 Windows 获200，Android/relay及暂停/撤销获403。合成0.0.2升级、坏候选回退、四棵自有树、alpha.5受管stop/start、cgroup清空和DB摘要保持均通过；FRPC/FRPS0.71 TLS与设备插件通过。独立Caddy reload hook已验，certbot既有timer active，**尚未实际续出新证**。见[隔离部署记录](OTA-ISOLATED-DEPLOYMENT-20261008.md)。

主电脑公网 `/auth/status` 返回200；三组件元数据与包共六项匿名请求返回401，新隔离账号三端完整下载经 `updates.security` 验证正常TLS、RSA/大小/哈希匹配alpha.5；跨origin凭据在发请求前拒绝、logout后401。独立Windows Job内合成FRPC0.71经公网FRPS/TLS/plugin到无任务HTTP夹具，服务器loopback读到 `CDR_EXTERNAL_FRP_OK`；原始句柄与Job整树退出确认，夹具凭据已撤销。**下一步是经用户手动在测试手机首装、登录和配对新Windows身份，选一条专用Desktop任务建立真实N基线；尚未完成三端OTA。**现用服务/数据/身份保持原样；私有GitHub Release和14天Actions artifact都不能替代受鉴权在线源。

随后分别确认Windows新目录和测试手机首装，用户手动登录、配对和系统安装；未知手机签名不能默认卸载。Windows需Python3.12/Tk/cryptography、Node20.19.0、FRP0.71.0、Desktop26.930.7945.0及精确文件指纹，不放宽pin。只选择一条专用Desktop原生任务，先完成手机→Desktop同任务接续→手机的N基线，再构建尚不存在的alpha.6并逐组件验收N→N+1。

下载、bootstrap或probe_verified都不能替代真实交互与数据保全。每条证据记实际版本、包哈希、签名标识、事务状态与结果；未知操作不重放，回退失败保留needs_review，不覆盖新数据库。只保存脱敏结果，不把私钥、token、DPAPI文件、数据库和原始任务内容放进仓库。
