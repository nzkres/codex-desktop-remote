# 工作记录

## 2026-10-07 — 三个原型的安装入口与操作步骤

- 用户要求说明三个原型从哪里开始安装。在独立分支 `codex/installation-guides` 新增 `docs/install/README.md`、`ANDROID.md`、`WINDOWS.md`、`RELAY.md`，README 前部与组件 README 直接导航；安装说明随依赖/入口变化维护的规则写入 AGENTS、ITERATION。
- 手机指南覆盖私有 Release 下载、APK 完整性、手动安装、测试服务器登录、配对确认、同任务验收和 debug 签名限制。Windows 指南覆盖官方运行时获取、合成窗口、固定 Desktop 兼容档、专用任务配置、真实窗口、快捷方式、退出及失败恢复。中继指南给出全新临时数据库、显式认证环境变量、两个前台服务的本地步骤与无凭据检查；明确没有公网 HTTPS/FRPS 一键部署。
- GPT-6 Sol medium 分担 Android、中继初稿及独立只读审查；主代理核对源码、完成 Windows/入口并验收。审查发现首次 probe 失败后已有配置会阻止再次 configure，已补同一新测试配置的无发送 probe 重试步骤，保留身份和保护。另补 set-account 失败即停止、12 位以上密码、端口检查和隔离启动预期。
- 已执行：`python -B scripts/check_versions.py`；`python -B scripts/configure.py --help`；`python -B relay/control_server.py --help` / `--version` / `--release-notes`；`python -B relay/auth/auth_server.py --help`。参数、alpha.2 版本与源码吻合；本机 Python 3.12.14、Tk 8.6 导入成功。`gh release view v0.1.0-alpha.2 --repo nzkres/codex-desktop-remote --json assets` 重新核对 APK/报告/`SHA256SUMS.txt` 文件名、大小和下载位置。
- 文档检查：11 份安装相关 Markdown、73 个本地链接目标全部存在，12 个 PowerShell 示例代码块通过语法解析，`git diff --check` 通过。官方 Python/Node/FRP 获取页已只读核对；语法解析不等于在全新电脑完整安装过。
- 本次只改文档，没有运行账号创建/服务启动命令、停止现用进程、改真实身份、安装新 APK 或部署公网服务；也没有新增软件版本/产物或重复产品构建。下一步由用户先按安装索引安装 APK/预览窗口，核验测试中继和 Desktop 条件后，再单独记录真实配对与同任务验收。

## 2026-10-07 — 仓库版本说明作为主要入口

- 用户再次明确：三个软件主要在仓库文档中记录更新，每次更新都能查到。本次只调整文档导航与维护规则，不新增软件版本、构建或部署。
- README 前部直接链接 Android、Windows、中继三份完整历史；新增 docs/versions/README.md 索引；CHANGELOG 保存组合版本摘要及本次文档修订。
- 在 AGENTS.md 与 ITERATION.md 明确每次软件更新必须随代码提交相应组件记录与版本文档，保留旧版本；聊天、软件内说明和 Release 页面不替代仓库记录。既有生成校验继续保证文档与版本记录一致。
- 验证命令：`python -B scripts/check_versions.py` 及本地 Markdown 链接检查；本次不重跑无代码变更的产品测试，也不改已发布 alpha.2 构件。

## 2026-10-07 — 三个原型分别记录并显示版本变化

- 用户要求每个软件都有版本迭代备注，并能看出每版变化。独立分支 `codex/component-version-notes` 实现；三个组件各自增加 releases.json，本次均为 alpha.2，保留 alpha.1，以后允许独立版本。
- GPT-6 Sol medium 分别实现 Android 说明入口、中继查询、版本校验/文档生成；主代理实现 Windows 窗口/心跳版本一致性并负责源码审核、测试与 CI。
- 每条变化包含 title / experience / verify，记录摘要、日期与限制。Android 构建版本和内置说明同源，Windows/中继采用启动时快照；中继查询保留账号认证，plugin 端口不提供此路由。
- 当前本机：Node 30 项、Python 38 项、中继 49 项通过。Windows 合成窗口已验证读取/滚动/返回不触发断开，截图保存在忽略的 artifacts/；截图检查后调整说明窗布局，保证返回按钮始终可见。GitHub CI 与 Android 新构件结果将在本段后补充；没有真机安装或公网部署。
- 首轮 Android `37614853237` 编译/72 项单测/Lint 虽成功，但主代理解包发现缺失 `assets/releases.json`，产物未发布。修复 Gradle 的生成目录与 mergeAssets 依赖；新增 CI 强制核对 APK 内版本说明字节与源码一致、构建输出版本/包名一致。4 项合成 ZIP 回归覆盖缺文件、旧文件和错版本，已通过；不能再只凭编译绿灯放行。
- 修复源码 `27b07da`：核心 CI `37615952040` 在 Windows/Linux 成功（Node 30、Python 42、relay 49；Linux 跳过 Windows 专属测试），Android CI `37615952157` 成功（72 JVM、Lint 0 errors / 40 warnings、主/测试 APK、包内校验）。下载后再次执行 `scripts/check_android_apk.py --apk artifacts/android-ci-37615952157/outputs/apk/debug/app-debug.apk --metadata artifacts/android-ci-37615952157/outputs/apk/debug/output-metadata.json`，得到 alpha.2/code2、11,720,835 字节和与源码一致的包内说明；SHA-256 见 STATUS.md。
- 私有预览 `v0.1.0-alpha.2` 提供验证后的 APK、报告和校验值；软件三个入口及仓库三份历史已对应。最终只补文档证据，使用 `[skip ci]`，保留产品源码与通过的 CI 一致。用户下一步按 TESTING.md 体验并分别报告三个软件版本；未安装新 APK、切换现用服务或部署新中继。

## 2026-10-07 — 独立仓库与首个自测版本

- 用户要求新仓库保存原型说明、关系图并复用 UI，作为自己测试和后续版本迭代的起点。新仓库命名 `codex-desktop-remote`，私有，默认分支 main；不覆盖旧工作区。
- Windows/Node 来源为独立 Lite 提交 `2fcf9e22ba28923cbbd36afd56ac34d0d0ee7652`；Android 与 relay 的已提交来源为旧仓库 `f8350e400cf6e7420d8283e03eb6efa37104ca79`。只迁源码、测试与必要许可，未迁真实身份、数据库、安装产物、旧签名私钥或本机配置。
- GPT-6 Sol medium 分担 Android 独立构建迁入、中继最小提取和文档；主代理核对边界、整合、测试和发布证据。
- 保留根 `src/`、`windows/` 避免为目录整理改动核心接口；新增 `android-native/` 与最小 `relay/`。未迁旧 app-server、Gateway、安装器、升级事务或大规模历史文档。
- 新版本统一为 `0.1.0-alpha.1`。Android 使用独立应用ID与默认 debug 签名，禁止复用旧 OTA；Windows 服务器默认留空，由用户填写。现用手机 APP/旧连接仍保持原状。
- 新增原型/架构图、测试/迭代指南、合成窗口演示、SelfTest.ps1 和只读测试配置准备脚本。原风格规范保留为 UI-BASELINE，原型功能支持与界面控件是否存在分别说明。
- 本机命令：`node --test` → 30 通过；`python -B -m unittest discover -s test -p test_*.py -q` → 28 通过；`python -B -m unittest discover -s relay -p test_*.py -q` → 45 通过。SelfTest.ps1 组合入口也已跑通；命令均在新仓库执行，未连接现用公网服务。
- GitHub CI、APK 下载以及后续人工验证结果继续追加；当前证据以 STATUS.md 为准，不把旧 APK 或旧部署当成本仓库的新验收。

## 2026-10-07 — CI 验收与可下载开发预览

- 新私有仓库：`https://github.com/nzkres/codex-desktop-remote`；提交与默认分支已推送。
- 首轮 Android CI 在 SDK 安装阶段发现旧 `tools` 包不可用，`ebdb6e4` 改为安装 `platform-tools`；未修改兼容门槛。首轮核心 CI 的 Ubuntu 成功、Windows 因测试临时路径的 8.3 别名比较失败，`ae9ed7c` 仅把测试期望根目录 resolve，4 项配置测试本机复验通过。
- `gh run view 37608868813 --repo nzkres/codex-desktop-remote`：Windows、Linux 均 success。`gh run view 37608678246 --repo nzkres/codex-desktop-remote`：Android success，70 项 JVM 单测通过，Lint 0 errors / 39 warnings，主/测试 APK 编译通过；未运行模拟器或手机 UI 测试。
- 使用 `gh run download 37608678246 --repo nzkres/codex-desktop-remote --name desktop-remote-android-debug --dir artifacts/android-ci-37608678246` 下载；核对报告、APK ZIP CRC、文件大小及 SHA-256。APK 和完整测试报告归入私有 `v0.1.0-alpha.1` 开发预览，避免只依赖保留 14 天的 Actions artifact。哈希、来源与未验收项见 STATUS.md。
- 最后只修订文档：Unix 使用 `bash ./gradlew`；澄清配对顺序、测试服务器选择、开发预览与稳定版本的区别。产品源码未在 CI 后变更，文档提交使用 `[skip ci]` 避免重复相同编译。
- 下一步由用户按 TESTING.md 自测并反馈。现用手机、Gateway/FRP、公网中继及 DPAPI 身份均未切换；本仓库不宣称已完成新包真机验收。
