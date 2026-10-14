# Desktop Remote Lite alpha.9 发布记录

北京时间 2026-10-15 02:17:21 已发布 alpha.9/versionCode 9/test/sequence 3，2026-10-15 02:18:50 完成实际 HTTPS 账号鉴权完整下载复验。**手机现在可以检查更新；本次没有自动安装，手机实际安装及使用验收仍待用户确认。**

## 本版体验

已确认动态预览已封存（SHA256 `bfbfda8bca1f67cec0f4c9821f725c8936888a9567aca9b292ff05c6d8dec2f6`）。三种玻璃、11 个字体选项、字色和文字阴影、背景配色、随机光蛇/方块/亮格及律动、速度和亮度均可在“我的 → 对话与玻璃”选择并保存在本机。默认清透玻璃、马善政、石墨底、彩色方块；已有明确深浅主题选择保留。

多轮任务随滚动以直角背景吸顶，点击定位原消息；Working 点与真实时间进入输入框，停止替换发送箭头；模式与模型共用弹窗，输入框短竖线分隔。问答选项与说明紧凑排列。模型修改和异步答题仍需在电脑处理，模式按真实能力禁用。

## 固定证据

| 项目 | 结果 |
| --- | --- |
| 源码 | `648527d2cf00eae5fe06bdd50630bf60d3f91a3e`；UI PR #12，传输期限修复 PR #13 |
| 受控构建 | [38612698531](https://github.com/nzkres/codex-desktop-remote/actions/runs/38612698531) / attempt 1 |
| 实际 Manifest | dev.codex.desktopremote.lite / versionCode 9 / minSdk 26 |
| APK | 40121564 字节 / SHA256 `271e994290e7dc5664da856769601c98935a4191faf2b004045aab4dc27ecc8c` |
| 原 APK 证书 | `ffcb12d413ee58613576b7914350ad4df887bf29d50d97f2b20c4e17dcebcf1a` |
| 更新索引 | sequence 3 / SHA256 `b8fbe7f382f121e467b9e83b82876956af146268aa6eee731f4a3f6b7ccf18fd` |
| 有效期 | 2026-10-21T18:16:33+00:00 |
| 下载鉴权 | 匿名索引与 APK 均 401；正常账号通过 TLS 完整下载，大小和 SHA256 一致 |
| 手机 | 尚未自动安装或确认实际使用效果 |

包先从私有暂存取回，独立 apksigner、aapt2 和包内 trust/版本记录校验通过，再签发索引并验证错误签名/旧序号拒绝。旧索引在发布锁内核对摘要后原子替换，保留旧包和备份。原子替换不承诺防止绕开发布锁的其他 root 写入者。

服务器发行审计：`/opt/codex-desktop-remote-test/operator/android-alpha9-feed-20261015-seq3`。Windows/relay 更新源、服务 PID/启动时间和 relay 运行版本均未变化；上传接收器仅调整了下述传输期限。验收账号仅产生临时会话并单独注销，未更换手机身份。

## 验证与传输修复

- 本机 Node 36/36；Python 139 项中 132 通过、7 平台跳过。版本记录检查通过。
- Android [38607961114](https://github.com/nzkres/codex-desktop-remote/actions/runs/38607961114) 单元测试、Lint、APK 元数据及 55/55 隔离模拟器回归通过。之后只修改交付工具；Android 目录与该验证的代码一致。受控签名工作流重复单元测试、Lint、签名及 trust 校验；其原配置不重复运行模拟器。
- 首次受控构建 38609926825 两次上传超时，未生成最终暂存构件。观察到数据正常增长，40 MB 包仍低于 48 MiB 上限；接收期限从 180 延长到 600 秒、发送方 210 延长到 630 秒。权限、账号、字节上限、配额和锁均保持。上传边界本机 14 通过/1 平台跳过，服务器 Linux 10/10 通过。详情见 [交付流程](SERVER-OTA-DELIVERY.md)。
- 核心 CI 的测试步骤通过，旧式源码构件上传因 GitHub 存储配额失败；Android 包只进入私有服务器，没有新增 GitHub APK 构件。

## 手机更新

等当前任务结束，保留原 APP 与登录，打开“我的 → 版本与更新说明 → 检查更新”，确认 alpha.9，下载并在 Android 系统弹窗确认覆盖安装。重开后检查实际版本、原电脑、任务和草稿，再体验皮肤与多轮吸顶。不要卸载或清数据；取消系统安装仍是旧版。

源为现有私有 [Android 签名索引](https://106.53.126.69:9443/desktop-remote-updates/test/android/latest.json)，直接浏览器匿名打开返回 401 是预期行为。新包固定名称为 `android-0.1.0-alpha.9.apk`。如果需要修复已见 sequence 3 的客户端，使用更高序号/合规版本，不回退旧索引或覆盖已发布同版本包。
