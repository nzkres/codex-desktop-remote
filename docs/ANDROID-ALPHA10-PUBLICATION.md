# Desktop Remote Lite alpha.10 发布记录

2026-10-15T03:51:44.321839+00:00 已发布 alpha.10/versionCode 10/test/sequence 4；2026-10-15T03:53:14.150496+00:00 完成实际 HTTPS 账号鉴权完整 APK 下载复验。手机可检查更新，手机实际安装与使用效果仍待用户确认。

## 本版变化

更新失败显示具体原因，区分终端暂停、离线、连接通道未就绪、任务运行中、权限未核验、网络失败，以及手机本地或连接器发送回执未确认。保留原始服务端错误码，目标电脑或连接器版本变化单独提示。保留更新门禁和原身份、回执，不重发任务或清除保护。

## 构建与核验

- PR [#14](https://github.com/nzkres/codex-desktop-remote/pull/14) 已合并，固定源码 `aa1dd1d7c75a01b53c4a4661c051ce8053c5cbac`。
- [Actions 38676159755](https://github.com/nzkres/codex-desktop-remote/actions/runs/38676159755) 因账户付款/消费额度问题未分配 runner、没有开始构建。本次改为本机固定提交构建，使用原 DPAPI 保护的 APK 签名证书；没有更改 GitHub 账单、Secrets 或签名身份，不将本机包伪称 CI 构件。
- 本机 JDK17/Gradle8.13/API36/Build Tools35；官方工具下载摘要校验通过。单元测试 106 项通过，Lint 无 Error/Fatal，构建通过；临时签名文件清理完成。
- [Android 回归 38670257531](https://github.com/nzkres/codex-desktop-remote/actions/runs/38670257531) 编译、单元、Lint 和 55 项模拟器测试通过。本次 APK 的 app/src 与该回归提交逐字节一致。Node 36/36，Python 139 项中 132 通过、7 平台跳过（修复合并前记录）。
- 独立 apksigner 确认单签名者、原证书 `ffcb12d413ee58613576b7914350ad4df887bf29d50d97f2b20c4e17dcebcf1a`；aapt2 确认包名 dev.codex.desktopremote.lite、versionCode10、minSdk26。包内原 trust 和版本记录校验通过。仍为原受控测试签名 debug 构建。

| 项目 | 结果 |
| --- | --- |
| APK 字节数 | 40122400 |
| APK SHA256 | `4f58e466181d0719ab1baacbe6c3eb000cef35d39598f7e606e1107432383d01` |
| 索引 SHA256 | `9aa98e97985d11f14320506e12b8a40ccd9390649f84ee965a13bf4ab52f8f04` |
| 索引有效至 UTC | 2026-10-22T03:51:02+00:00 |
| 更新序号 | 4 |
| 下载鉴权 | 匿名索引/APK 均 401；普通账号正常 TLS 完整下载，大小和 SHA256 一致 |
| 实机 | 未自动安装，手机覆盖安装待用户确认 |

原 RSA 发布签名、错误签名拒绝、旧序号拒绝及 alpha.5–9 升级候选核验通过。本机验证包通过管理员 SSH 上传到 root 私有暂存，明确记录本机构建来源；发布锁内核对旧 alpha.9/sequence3 索引后原子替换。旧 APK 和旧索引保留；Windows/relay 更新源、现用服务 PID/启动时间和 relay 程序均经发布前后快照确认未变化。没有变更运行服务或端口。验收登录只注销自己的临时会话，手机登录保持。

服务器审计目录：`/opt/codex-desktop-remote-test/operator/android-alpha10-feed-20261015-seq4`。

## 手机更新

等当前任务结束，在原 APP 打开“我的 → 版本与更新说明 → 检查更新”，确认 alpha.10 后下载并按系统提示覆盖安装。保留原 APP 和数据，不卸载或重新配对。重开核对 alpha.10、原电脑、任务和草稿；在更新页面检查各类失败原因。取消系统安装仍是旧版。

更新源沿用原私有 [Android 签名索引](https://106.53.126.69:9443/desktop-remote-updates/test/android/latest.json)，匿名浏览器返回 401 是预期行为。旧版手机需要先通过原有安全门禁才能安装，本次不绕过门禁；若当前任务运行中，应结束任务再检查。
