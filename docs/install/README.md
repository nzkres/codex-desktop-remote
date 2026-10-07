# 三个原型的安装起点

**下一下载起点是准备发布的 [v0.1.0-alpha.3 私有开发预览](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.3)。** Release 须在最终 CI 验收后由维护者创建；页面尚未出现时不要把链接或本地构建当作已发布。已有 [alpha.2 Release](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.2) 保留为历史归档。实际证据看 [STATUS](../../STATUS.md)，三端 OTA 边界看 [OTA](../OTA.md)。

| 组件 | 第一次使用 | 后续更新入口 |
| --- | --- | --- |
| [Android](ANDROID.md) | alpha.3 Release 创建后下载 `desktop-remote-lite-0.1.0-alpha.3-debug.apk` 并手动安装 | App 内有检查入口，但此 debug 包的 trust 为空、无受控连续签名，不能作为生产 OTA 基线 |
| [Windows](WINDOWS.md) | 下载 `desktop-remote-windows-0.1.0-alpha.3-source.zip`；仍需外部 Python/Node/FRP/Desktop | 受管 OTA 须另有签名 ZIP、元数据与公钥，在**新目录** bootstrap；没有 EXE 安装器 |
| [Linux 中继](RELAY.md) | 下载 `desktop-remote-relay-0.1.0-alpha.3-source.zip`；仍需外部 Python 与 Linux 运行条件 | 受管 OTA 须另有签名 ZIP、元数据、公钥和委派 cgroup；不接管现用中继 |

想先看界面，可运行 `python windows/demo.py`，它只用合成数据。实际手机文字闭环需已审核的 HTTPS 中继、独立测试账号、专用 Desktop 原生任务、新连接器配对和手机确认；下载、安装、连通与任务完成是不同证据层。旧 App 包名不同，可并存；不要为了测试卸载旧 App 或复制其凭据。

alpha.3 的三个下载文件是开发预览产物，Windows/relay ZIP 都是源码包，不是 EXE 安装器或已签 OTA feed。首次受管安装与发布仍须独立的测试公钥/私钥管理、可达的 HTTPS 静态源及候选包；命令见 [发布步骤](../OTA-PUBLISH.md)。普通 debug 签名不保证覆盖已装 alpha.2，遇到冲突先保留应用和数据，勿卸载绕过。产品代码完成不等于真机已升级。GitHub Actions 14 天临时 artifact 不能充当在线更新服务；遇到 `needs_review` 按 [恢复说明](../OTA-RECOVERY.md)保留现场。
