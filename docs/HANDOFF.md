# OTA 交接

2026-10-08：用户已批准并完成PR #1合并、专用测试签名、六项Android Secrets和alpha.5构建/复验；**暂不部署**。此前“先本地准备、不合并或签名”已被明确替代，不需重复确认。公网部署、开放端口、停止现用服务、真实身份迁移及手机安装仍未批准或执行。

先读[STATUS](../STATUS.md)、[落地验收](OTA-ACCEPTANCE.md)、[部署审查](OTA-DEPLOYMENT-REVIEW.md)、[发布](OTA-PUBLISH.md)、[安装起点](install/README.md)和[恢复](OTA-RECOVERY.md)。保留旧alpha.4资产，核对当前远端、分支及未提交改动后再继续；不要只凭本页旧提交猜测最新状态。

## 已完成的受控基线

- PR #1合并提交`1e405ad`。alpha.5程序输入为`1a4b4c0ced91d1a384e723374a6141d84e43de9e`，分支`codex/controlled-alpha5`经Core37736389583/Android37736389589通过后，main非强制快进。后续文档提交不改变这些程序输入。
- main受控Android构建[37736837251](https://github.com/nzkres/codex-desktop-remote/actions/runs/37736837251)成功：94 JVM、lint0errors/45warnings、apksigner证书验证和包内trust比对。实际APK下载SHA与CI相同，AAPT2核对包名/code5/minSdk26。它仍为debuggable测试构建，不是生产发行包。
- [alpha.5私有归档](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.5)已发布，七项资产服务端大小/哈希与本地一致，见STATUS。包含APK、Windows/relay源码ZIP、bootstrap公开信任/已签元数据、Android报告、verification和SHA256SUMS。三端版本说明在仓库`docs/versions/`，不以Release文字代替。
- Windows43/relay29源码文件与Git输入逐字节相符。三端实际包已签RSA元数据；Windows Python3.12/WSL Python3.14在全新临时home通过bootstrap、账本与重复安装拒绝。没有启动真实连接或使用账号/身份；真实GUI/helper及cgroup升级/回退来自CI合成演练。

## 签名资料与有效期

专用库在本机`%LOCALAPPDATA%\CodexDesktopRemoteSigning\test-v1`，CurrentUser DPAPI保护RSA和Android加密PKCS12/口令。RSA私钥没有上传；Android六项Secrets已配置在固定私有仓库。`github-provision.json`为configured。**不要重复执行create/provision、覆盖文件或重新生成同名证书。** Git克隆不能恢复该库；独立备份和跨电脑恢复尚未实现/验收，不把复制DPAPI文件当作可靠备份。

keyId为`cdr-ota-test-20261008-v1`，公开SPKI/APK证书指纹见STATUS。首次trust须核对已认证仓库中的公开指纹；同页SHA表只能核对完整性。后续Android构建必须沿用同一APK签名证书，RSA发布私钥不传CI/服务器，不在客户端嵌入PAT。

三端alpha.5元数据为test、sequence1，固定`https://106.53.126.69:9443/desktop-remote-updates/test/{component}/latest.json`，有效至**2026-10-15 06:33:03 UTC（北京时间14:33:03）**。部署/首装前重查时间。过期时由维护者以新序号重新签发相同已核验程序包，单独归档新元数据；不能修改已签字段、替换同版本包或删除ledger绕过保护。

## 最小下一步

具体隔离部署还需确认，候选已可审查，见部署清单。用户已选择现有中继服务器隔离环境、确认origin与保持私有，不再询问这些选择。部署前重新检查106.53.126.69候选端口/目录、云安全组权限和IP证书；上次证书有效至2026-10-13 22:07:54 UTC，新容器必须有独立续证重载。部署草稿不是运行证据。

获准后先建立新的Linux账号/受管home、数据库和明确委派的cgroup v2，再完成HTTPS/FRPS与私有feed；保持现用服务/数据不变。验证匿名/过期/撤销/暂停拒绝、账号三组件、设备仅Windows、每次元数据与包均鉴权，同origin固定目录且不转发跨源凭据。私有GitHub Release和14天Actions artifact都不是OTA服务。

随后分别确认Windows新目录和测试手机首装，用户手动登录、配对和系统安装；未知手机签名不能默认卸载。Windows需Python3.12/Tk/cryptography、Node20.19.0、FRP0.71.0、Desktop26.930.7945.0及精确文件指纹，不放宽pin。只选择一条专用Desktop原生任务，先完成手机→Desktop同任务接续→手机的N基线，再构建尚不存在的alpha.6并逐组件验收N→N+1。

下载、bootstrap或probe_verified都不能替代真实交互与数据保全。每条证据记实际版本、包哈希、签名标识、事务状态与结果；未知操作不重放，回退失败保留needs_review，不覆盖新数据库。只保存脱敏结果，不把私钥、token、DPAPI文件、数据库和原始任务内容放进仓库。
