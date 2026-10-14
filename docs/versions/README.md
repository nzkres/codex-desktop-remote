# 版本说明索引

**仓库中的 Markdown 是三个软件版本说明的主要阅读入口。** 按软件查看完整历史：[Android 手机端](ANDROID.md)、[Windows 连接器](WINDOWS.md)、[服务器中继](RELAY.md)。每份按新到旧排列，逐版列出日期、更新内容、在哪里体验、怎样验证以及限制。仓库组合变化见根目录 [CHANGELOG.md](../../CHANGELOG.md)，维护规则见 [ITERATION.md](../ITERATION.md)；实际构建、安装、运行与部署证据以根目录 [STATUS.md](../../STATUS.md) 为准。

| 仓库版本 | 日期 | 本版更新摘要 | 下载 |
| --- | --- | --- | --- |
| Android `0.1.0-alpha.8`（私有OTA已发布） | 2026-10-14 | 含按需加载原对话、普通问答和特殊交互说明；普通问答需Windows alpha.15 | 原APP检查更新；[已验收的源与包](../ANDROID-ALPHA8-PUBLICATION.md)，手机安装待验收 |
| Android `0.1.0-alpha.7`（历史候选，功能已并入alpha.8） | 2026-10-14 | 明确选择电脑，主动点击标题请求加载原对话；Windows alpha.14单独准备 | 未单独发布；使用已发布alpha.8，见 [STATUS](../../STATUS.md) |
| `0.1.0-alpha.5` | 2026-10-08 | 三端受控测试首装包：固定发布 trust、Android 连续测试证书，Windows/relay 在全新临时目录验签引导；更新源与实机验收尚未完成 | [已发布的私有 prerelease](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.5)，七项资产及复验证据见 [STATUS](../../STATUS.md) |
| `0.1.0-alpha.4` | 2026-10-08 | 三端私有下载鉴权；账号/配对身份复用、严格同源与组件授权；新增落地验收步骤 | [开发预览](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.4) |
| `0.1.0-alpha.3` | 2026-10-07 | 三端签名 OTA、独立更新助手、事务和隔离升级/回退验证 | [开发预览](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.3) |
| `0.1.0-alpha.2` | 2026-10-07 | 三端独立版本记录；手机/Windows 增加说明入口，中继增加版本查询；校验仓库文档与 APK 内说明 | [开发预览](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.2) |
| `0.1.0-alpha.1` | 2026-10-07 | 建立三个独立原型，复用手机 UI 和 Windows 窗口，提取最小中继，加入自测和原型关系图 | [开发预览](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.1) |

以后发布任一软件版本时，须把对应组件的版本记录及生成的 Markdown **随源码提交**，并更新本索引与根 CHANGELOG，保留旧记录；不能只写在聊天、GitHub Release 或软件内。三个组件可以独立升级，文档勘误本身不虚增软件版本。alpha.5 资产已发布且完成服务端哈希复验，隔离临时首装已测试；本索引不证明 APK 已装机、更新源或新中继已部署。
