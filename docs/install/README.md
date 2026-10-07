# 三个原型的安装起点

先区分**已发布的 alpha.2 手动安装**与**仓库中新增的受管 OTA 源码路径**。当前没有正式发行密钥、长期 HTTPS 更新源或生产部署；alpha.3 记录与代码不等于已发布/已安装。实际证据看 [STATUS](../../STATUS.md)，三端 OTA 边界看 [OTA](../OTA.md)。

| 组件 | 第一次使用 | 后续更新入口 |
| --- | --- | --- |
| [Android](ANDROID.md) | 私有 alpha.2 Release 的测试 APK 可手动安装；受控签名的 OTA 首装包尚待发行 | App 内“我的 → 版本与更新说明”；需要预置公钥、HTTPS 源与相同 APK 证书，普通 debug 包不可连续升级 |
| [Windows](WINDOWS.md) | alpha.2 可从源码开窗口；受管 OTA 须用签名 ZIP + 元数据在**新目录** bootstrap | 受管窗口检查、下载，由独立助手切换；旧身份不自动迁入 |
| [Linux 中继](RELAY.md) | alpha.2 可隔离回环启动；受管 OTA 须用签名 ZIP + 元数据在**新目录** bootstrap | 管理员通过受管宿主本机 socket 执行固定命令；不接管现用中继 |

想先看界面，可运行 `python windows/demo.py`，它只用合成数据。实际手机文字闭环需已审核的 HTTPS 中继、独立测试账号、专用 Desktop 原生任务、新连接器配对和手机确认；下载、安装、连通与任务完成是不同证据层。旧 App 包名不同，可并存；不要为了测试卸载旧 App 或复制其凭据。

首次受管安装与发布要先具备独立的测试公钥/私钥管理、可达的 HTTPS 静态源及候选包；命令见 [发布步骤](../OTA-PUBLISH.md)。仓库源码与 GitHub Actions 临时 artifact 不能充当在线更新服务。遇到 `needs_review` 或身份/数据不明，按 [恢复说明](../OTA-RECOVERY.md)保留现场，不删除锁、ledger 或账号数据。
