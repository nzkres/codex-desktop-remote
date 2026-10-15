# 版本说明索引

**仓库中的 Markdown 是三个软件版本说明的主要阅读入口。** 按软件查看完整历史：[Android 手机端](ANDROID.md)、[Windows 连接器](WINDOWS.md)、[服务器中继](RELAY.md)。每份按新到旧排列，逐版列出日期、更新内容、在哪里体验、怎样验证以及限制。仓库组合变化见根目录 [CHANGELOG.md](../../CHANGELOG.md)，维护规则见 [ITERATION.md](../ITERATION.md)；实际构建、安装、运行与部署证据以根目录 [STATUS.md](../../STATUS.md) 为准。

| 仓库版本 | 日期 | 本版更新摘要 | 下载 |
| --- | --- | --- | --- |
| Android `0.1.0-alpha.19`（核验中） | 2026-10-15 | 修复本地预览正文误拦截，区分下载与显示 | 原私有源，发布状态见[STATUS](../../STATUS.md) |
| Android `0.1.0-alpha.18`（已发布，手机待验收） | 2026-10-15 | 一次升级预览宿主，之后兼容预览独立刷新；当前R2 | 原私有源检查更新；[发布证据](../ANDROID-ALPHA18-PUBLICATION.md) |
| Android `0.1.0-alpha.17`（已发布，手机待验收） | 2026-10-15 | 同账号额度动效离线预览，渐强呼吸、外环加速与停止渐弱 | 原私有源检查更新；[发布证据](../ANDROID-ALPHA17-PUBLICATION.md)，手机待验收 |
| Android `0.1.0-alpha.16`（已发布，手机待验收） | 2026-10-15 | 手机内原生预览，演示完成未读/可见已读与固定箭头/旋转细环/停止方块；正式链路不变 | 原私有源检查更新；[发布证据](../ANDROID-ALPHA16-PUBLICATION.md)，手机安装待确认 |
| Android `0.1.0-alpha.15` / Windows `0.1.0-alpha.18`（私有源已发布，待安装） | 2026-10-15 | 五种中心光格数字皮肤；Plus双额度/Pro周单环；移除实时预览与输入框额度 | [签名与实际HTTPS交付证据](../ANDROID-ALPHA15-PUBLICATION.md) |
| Android `0.1.0-alpha.11` / Windows `0.1.0-alpha.17`（已发布，待安装） | 2026-10-15 | 输入框额度快照；独立网格亮度与核心外散流光 | 构建、发布和安装分别见 [STATUS](../../STATUS.md) |
| Windows `0.1.0-alpha.15` / Android `0.1.0-alpha.8`（候选） | 2026-10-14 | 普通问答原题提交、加密草稿与回执核对；异步题显示但仍需电脑回答 | 构建、下载与实机状态见 [STATUS](../../STATUS.md)，[特殊交互说明](../MOBILE-SPECIAL-INTERACTIONS.md) |
| Windows `0.1.0-alpha.14` / Android `0.1.0-alpha.7`（候选） | 2026-10-14 | 手机主动点标题请求Desktop加载原对话，等待同一任务历史；可能切换电脑页面 | Windows本机已签名验签；Android已合入main并通过原证书构建/校验，但上传受存储额度阻止，无可下载APK，未发布/安装，见 [按需打开](../DESKTOP-ON-DEMAND-OPEN.md) |
| Windows `0.1.0-alpha.13`（现用实机已安装） | 2026-10-14 | 可点击错误清单与源码定位；保留alpha.12全部对话标题目录 | 原Windows私有源已发布，签名/安装保全核验通过；按需自动打开不包含于本版，见 [STATUS](../../STATUS.md) |
| Windows `0.1.0-alpha.6`（待发行） | 2026-10-08 | 修复同一审批模式名称切换引发的手机同步权限误报；Android/relay仍alpha.5 | [CI临时源码构件](https://github.com/nzkres/codex-desktop-remote/actions/runs/37758224837)已核对，未签名/安装；不是受控发行或OTA源，见 [STATUS](../../STATUS.md) |
| `0.1.0-alpha.5` | 2026-10-08 | 三端受控测试首装包：固定发布 trust、Android 连续测试证书，Windows/relay 在全新临时目录验签引导；更新源与实机验收尚未完成 | [已发布的私有 prerelease](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.5)，七项资产及复验证据见 [STATUS](../../STATUS.md) |
| `0.1.0-alpha.4` | 2026-10-08 | 三端私有下载鉴权；账号/配对身份复用、严格同源与组件授权；新增落地验收步骤 | [开发预览](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.4) |
| `0.1.0-alpha.3` | 2026-10-07 | 三端签名 OTA、独立更新助手、事务和隔离升级/回退验证 | [开发预览](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.3) |
| `0.1.0-alpha.2` | 2026-10-07 | 三端独立版本记录；手机/Windows 增加说明入口，中继增加版本查询；校验仓库文档与 APK 内说明 | [开发预览](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.2) |
| `0.1.0-alpha.1` | 2026-10-07 | 建立三个独立原型，复用手机 UI 和 Windows 窗口，提取最小中继，加入自测和原型关系图 | [开发预览](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.1) |

以后发布任一软件版本时，须把对应组件的版本记录及生成的 Markdown **随源码提交**，并更新本索引与根 CHANGELOG，保留旧记录；不能只写在聊天、GitHub Release 或软件内。三个组件可以独立升级，文档勘误本身不虚增软件版本。alpha.5 资产已发布且完成服务端哈希复验，隔离临时首装已测试；本索引不证明 APK 已装机、更新源或新中继已部署。
