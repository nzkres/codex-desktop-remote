> 当前手机最终更新为alpha.15，配套Windows alpha.18已发布原私有源。保留现有应用数据与身份，使用正常检查更新流程；不重新首装或配对。见[交付与安装验收边界](../ANDROID-ALPHA15-PUBLICATION.md)。

# 三个原型的安装起点

**下载起点是已发布的 [v0.1.0-alpha.5 私有 prerelease](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.5)，七项资产已上传，服务端大小和 SHA-256 与本地一致；逐件证据见 [STATUS](../../STATUS.md)。** 可下载、解压并在全新临时目录做签名首装验证。隔离服务器 `https://106.53.126.69:9443` 已通过主电脑公网 HTTPS 鉴权下载验收，`17443` FRPS 已通过公网合成 FRPC/TLS 夹具；真实手机安装、登录和在线升级尚未验收，详见[隔离部署记录](../OTA-ISOLATED-DEPLOYMENT-20261008.md)。[alpha.4 开发预览](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.4) 保留归档，不能作为受控签名升级基线。三端 OTA 边界见 [OTA](../OTA.md)。

| 组件 | 第一次使用 | 后续更新入口 |
| --- | --- | --- |
| [Android](ANDROID.md) | 已发布 `android-0.1.0-alpha.5.apk`：受控测试证书和固定 trust；真实手机首装待验收 | App 内原有检查入口；私有源已从主电脑公网验证，手机实际检查/下载仍待验收 |
| [Windows](WINDOWS.md) | 已发布 `windows-0.1.0-alpha.5.zip`：需外部 Python/Node/FRP/Desktop，可在**全新临时目录**校验签名后 bootstrap | 源码运行包，不是 EXE 或一键安装器；不迁旧身份 |
| [Linux 中继](RELAY.md) | 已发布 `relay-0.1.0-alpha.5.zip`：需外部 Python；独立服务器实例已安装并通过受管启停和 cgroup 验收 | 源码运行包；新账号/数据/服务与现用中继隔离 |

想先看界面，可运行 `python windows/demo.py`，它只用合成数据。实际手机文字闭环需已审核的 HTTPS 中继、独立测试账号、专用 Desktop 原生任务、新连接器配对和手机确认；下载、安装、连通与任务完成是不同证据层。旧 App 包名不同，可并存；不要为了测试卸载旧 App 或复制其凭据。

其他四项资产为 `bootstrap-0.1.0-alpha.5.zip`（已核对根目录 `README.txt`，`android/`、`windows/`、`relay/` 各含 `trust.json` 与 `latest.json`）、`artifact-verification.json`、`SHA256SUMS.txt` 和 `android-reports-0.1.0-alpha.5.zip`。先核对资产哈希与签名，再将 bootstrap 中对应组件的文件用于首装；公钥须对照**已认证的仓库记录**：`keyId=cdr-ota-test-20261008-v1`，SPKI SHA-256 `c3835e3e59b362e0b356d3a7b3589c255187ddce34692167f5160b0e5f5e3a4d`。同一下载地址提供的哈希表不能单独证明发布者身份。元数据 sequence 1，有效至 **2026-10-15 06:33:03 UTC（北京时间 14:33:03）**；过期由维护者重新受控签发，不能关闭验证。Windows Python 3.12.14 与 WSL Linux Python 3.14.4 的全新临时首装已验证通过，重复安装被拒且原目录未变；隔离服务器另已完成 alpha.5 安装与合成升级/回退验收。主电脑公网已完成私有下载及合成 FRP 链路验证，没有使用真实身份或任务。具体命令见 [Windows](WINDOWS.md)、[中继](RELAY.md)和[发布说明](../OTA-PUBLISH.md)。旧 App 与服务、身份和数据均保留；签名冲突时不要卸载绕过。GitHub 归档与 CI 临时 artifact 不能替代受鉴权在线源；当前公网证据仍是合成账号/设备，`needs_review` 按 [恢复说明](../OTA-RECOVERY.md)保留现场。
