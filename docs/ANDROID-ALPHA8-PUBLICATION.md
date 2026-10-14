# Android alpha.8 私有 OTA 发布实录

## 已发布与待验收

用户2026-10-14明确授权“准备签名更新索引，核验通过后发布Android alpha.8私有更新源”。北京时间17:36:03完成发布，17:36:38实际HTTPS验证通过。**现在手机可检查并下载新版；尚未收到手机安装成功或功能验收结果。**

| 项目 | 已核对结果 |
| --- | --- |
| 发布版本 | 0.1.0-alpha.8 / versionCode8 / test渠道 / sequence2 |
| 受控构建 | [38540921047](https://github.com/nzkres/codex-desktop-remote/actions/runs/38540921047)，源码d41466a385e2b66e0485c0892a2a53617ccf67a2 |
| APK | 11806944字节，SHA256 `02de6dddf6d36cf75bf658711d0731ab8b6dd0d48aa3fd8233095748449e6ee9` |
| APK证书 | `ffcb12d413ee58613576b7914350ad4df887bf29d50d97f2b20c4e17dcebcf1a`，保持原证书 |
| 实际Manifest | dev.codex.desktopremote.lite、versionCode8、minSdk26 |
| 索引SHA256 | `fe29a7e43ea82f282af887f9979c08d47d008abad95c4d18ff406fe6e024e29f` |
| 索引有效期 | 至2026-10-21 09:30:30 UTC / 北京时间17:30:30 |
| 信任根 | 原cdr-ota-test-20261008-v1，RSA SPKI SHA256 c3835e3e59b362e0b356d3a7b3589c255187ddce34692167f5160b0e5f5e3a4d |
| 匿名访问 | latest.json与APK均HTTP401 |
| 账号访问 | 从主电脑经正常TLS读取索引200、完整下载APK200，大小/SHA及签名一致 |
| 手机 | 未自动安装；实际覆盖安装和原任务功能待用户验收 |

源：[Android签名索引](https://106.53.126.69:9443/desktop-remote-updates/test/android/latest.json)。它需要APP账号鉴权；浏览器直接打开返回401是预期行为，不能把源改为公开下载。APK固定路径为同目录`android-0.1.0-alpha.8.apk`。原APK不在GitHub新增构件或Release，GitHub仅继续保存源码、说明和CI记录。

## 手机操作

1. 等当前任务回复完成，保留原APP和登录，服务器地址仍为`https://106.53.126.69:9443`，选择原电脑并保持连接。
2. “我的→版本与更新说明→检查更新”，确认alpha.8。
3. 点击下载并安装，在Android系统弹窗确认覆盖；不卸载、不清数据、不重新配对。
4. 重开确认实际alpha.8，检查原电脑、对话和草稿仍在。再测试读取原任务和普通问题选项；普通问答需要Windows alpha.15，异步选择框仍需电脑回答。

若检查或下载失败，记录界面错误和当前APP版本，不重复发送任务消息。若任务忙或发送结果不确定，先等任务结束/核对结果；不要绕过安装门禁。系统取消安装仍是旧版，没有升级成功。手机收到新版本并安装的结果必须另记，服务器下载成功不能替代实机验收。

## 发布过程及保留项

先核对本机及服务器可用签发记录：现用Android只有alpha.5/sequence1；用原DPAPI RSA密钥签sequence2，原APK未重建。验证alpha.5/6/7可选升级、签名错误拒绝、旧序号拒绝。独立GPT-6 Sol复核后，以实际运行代码的写入边界确认发布窗口：relay/private_updates.py只读；上传用户不能写正式目录；管理员发布者须共用`/opt/codex-desktop-remote-test/operator/android-feed-publish.lock`。

锁内复核旧索引SHA256 `0e46801282e1ed0e153bd585b9bf9216379b86607b313998072437077189fbf5`，先创建并校验不可覆盖的版本APK，再核对预期旧摘要、原子替换latest.json并fsync。这是受控发布窗口内的旧摘要检查与原子可见替换，**不是能阻挡不合作root写入者的内核级原子CAS**；不允许其他管理员绕过同一锁并行发布。

- 正式目录：`/srv/codex-desktop-remote-test/data/update-feed/test/android/`，cdrtest所有、0700，APK/索引0600。
- 原alpha.5 APK完整保留；新APK大小及哈希在落位后和实际HTTPS下载后分别复验。
- root私有备份/审计：`/opt/codex-desktop-remote-test/operator/android-alpha8-feed-20261014-seq2/`，含previous-latest.json、before.json、新APK/索引/public-trust.json和published.json。
- relay、FRPS、Caddy进程身份/启动时间，Windows及relay源摘要、服务器current.json均与发布前一致。没有停止服务、改Desktop权限或迁移身份。
- 验证使用已有服务器验收账号正常登录，口令不离开服务器内存；会话令牌仅在进程内通过SSH/HTTPS传递，未入日志、命令参数或文件。只注销此次创建的会话。

本机公有证据在工作树忽略目录`artifacts/server-ota/android-alpha8-seq2/`：review.json、server-prepare.json、server-publish.json、https-preflight.json、https-published.json及实际下载APK。工作树为`C:\Users\sheng\Documents\Codex\work\cdr-server-ota-delivery`。本轮无客户端业务代码变化；沿用已通过Node36/36、Python132通过7跳过、37项模拟器及受控签名CI结果，新增实际签名/发布/鉴权下载验收。

## 恢复与下一次迭代

操作结果未知时先只读核对正式latest.json、新APK哈希和root阶段目录；禁止直接重跑发布脚本覆盖现场。已切换且已有客户端可能读取sequence2时，不直接恢复sequence1；需要原密钥签更高序号和合规版本修复。Android已升级后通常不能降versionCode，不卸载抹数据绕过。若手机取消安装，旧版继续可用。

索引过期前应由维护者核验现用包并签更高序号续期或发布下一版，不改时钟或关闭有效期校验；尚未建立自动续期任务。后续业务变更更新android-native/releases.json并生成版本文档，构建新versionCode，继续原证书和服务器交付。错误修复按版本记录迭代，不替换已发布alpha.8同版本包。
