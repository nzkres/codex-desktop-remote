# 工作记录

## 2026-10-07 — 三个原型增加 OTA 的正式要求

- 用户明确“所有原型必须增加 OTA 的方式更新迭代版本”。在独立分支 `codex/ota-requirements` 将三端 OTA 从此前排除项改为正式必做范围，新增 `docs/OTA.md`，同步项目范围、原型关系、安装/迭代/测试指南与状态；已发布 alpha.1/alpha.2 的记录保持原样。
- 明确三种方式：Android 应用内检查、系统确认覆盖；Windows 原窗口与独立助手；中继独立管理员工具在线更新。第一版用户触发安装，版本、签名、平台、渠道、数据与兼容性校验，以及真实启动/健康和失败恢复都是要求的一部分。中继自身升级不等同于分发手机 APK。
- GPT-6 Sol medium 只读审查旧 Android 下载/安装与发布代码、Windows 签名/事务/助手，以及新中继准入和活跃请求边界；主代理核对源码并整合。旧 Gateway 维护驱动依赖旧 profile/退出 Desktop，不适用于新项目，未整体迁入。当前 Android debug 签名不保证连续覆盖；中继也还没有可证明的安全维护窗口，文档明确列为实现缺口。
- 更新目录暂按独立 HTTPS 服务目录设计，测试/稳定渠道分离，公开配置及部署仍待核定；没有读取既有签名私钥或真实身份，也没有修改旧 OTA、公网路由或现用 Gateway/FRP。官方 Android 签名与安装 API 文档已只读核对。
- 验证：`python -B scripts/check_versions.py` 通过，受影响 Markdown 本地链接检查通过，`git diff --check` 通过。本次只完成要求、复用审查和验收标准，**没有实现或发布 OTA 代码**，没有软件版本递增、CI 构件或升级实测；不得把文档完成记成 OTA 完成。
- 实现起点：确定长期签名/发布信任和可连续升级的安装基线，再实现统一元数据与分发、三端更新执行、隔离 N → N+1 验收。实际切换现用服务与公网部署仍按用户约定，先有可审查实现、验证和回退方案再确认。

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

## 2026-10-07 OTA alpha.3 实现中

- 分支 codex/ota-implementation；基于 main b7db21e。加入 RSA3072 签名元数据、隔离版本目录、持久化事务、独立助手和三端入口；未部署公网、未迁移现用身份。
- 本机命令：`python -B -m unittest discover -s test -p "test_*.py"` 69 项通过；`node --test` 32 项通过。Windows 原始句柄/Job 子树合成测试通过。中继 agent suite 65 项通过含 2 个 Linux 跳过。
- 下一步：Windows driver 审查、HTTPS 下载/两版本演练、Linux cgroup 实测 CI、Android 编译与安装文档。此检查点不是完整验收或生产发布。

### OTA 第二轮验证

- 本机 87 项 Python tests 通过（含真实临时 TLS 服务器 3 项）；Windows mock driver 10/10。
- `python -B scripts/windows_ota_smoke.py` 在隔离临时 home、合成签名包、新 DPAPI unpaired 身份上实际跑过 pythonw/Tk → 独立 helper → 新 GUI，升级 0.0.1→0.0.2 committed；候选探针失败 rolled_back 0.0.1。根代理复验通过。Desktop 探针由签名 fixture 替换，仅测试助手与窗口生命周期，未操作真实任务。
- 首轮 CI c663a62：Android JSONObject API/可见性编译失败、Windows 临时目录清理时序及私钥路径别名断言失败，已针对修复；不能把首轮记为成功。新增 Linux cgroup 真实服务两版演练等待下一轮 CI。

### OTA 第三轮修正

- Android run 37643173564 / 560bf39 已成功（JVM、lint、APK 构建）。新增安装回执已接 UI，随后又加强 APK minSdk 一致性，需最终构建。
- Core run 37643173534：Linux 已实际提交新版，但失败候选回退暴露已退出管道关闭的 BrokenPipe 被错误算作树退出失败；现仅在同一原始句柄及空 Job/cgroup 已证明后忽略无效管道关闭错误，增加本机实进程回归。Windows 测试夹具的短路径已统一规范化。
- Windows 在开放新 GUI 操作前解除助手 Job 的 kill-on-close；事务未 committed/rolled_back 时窗口禁用新连接。中继 needs_review 保留原始句柄；更新期间配置、控制 token、传输配置变化均拒绝。

### OTA 最终候选

- e7602ec：Android run 37644338199 成功；Core run 37644337904 Linux 全部通过（Node33/Python88/relay72 + 真实cgroup服务提交/回退）。Windows Job断言全部通过，但测试空目录最终删除因OS释放延迟WinError32失败。夹具现仅在树退出证明后对自己创建的空目录 rmdir 有界重试3秒，超时仍失败；本机10轮重复通过，未放宽产品树证明。
- 根代理与子代理已完成实际Windows隔离helper演练，文档记录首次信任配置、源码包依赖、Linux更新宿主正常重启边界。删除重复的纯fake-driver smoke脚本，保留更完整的Windows/relay实际进程演练。

### OTA 分发前复验

- 0c1f37b / Core 37645343523：Windows 全部单元测试通过，GUI smoke 交接未核验；Linux 再次全过。检查发现 helper/candidate 把已核验版本目录的短路径别名与 canonical ROOT 直接比较，现先保留 no-links 检查再规范化比较；新增别名/不同版本拒绝回归，本机 1/1 及两种真实 GUI/helper 演练通过，CI 待复验。烟测补固定阶段/错误码，不输出凭据。
- Android run 37644338199 构件实际下载后核对：alpha.3/code3、11,771,316 字节、SHA-256 d188ed0522f8fc36361565495536cdda141e36160443ededee314dbb843107f6。包内说明与 `git show e7602ec:android-native/releases.json` 原始字节一致；Windows checkout 的 CRLF 不能直接当作 Linux 构建输入比较。87 JVM 测试、Lint 0 errors / 45 warnings；默认 trust 无 keys/source，没有宣称受控签名发行或真机升级。

### OTA alpha.3 验收与归档

- 最终产品提交 `169b4497f196cc390a333657e98f5fb70519f5b0`，Core [37646940566](https://github.com/nzkres/codex-desktop-remote/actions/runs/37646940566) Windows/Linux 均成功。Node33、Python89（Linux skip13）、relay72（Windows skip4）。Windows 实际 pythonw GUI/helper 两例 committed0.0.2 / rolled_back0.0.1；Linux 实际监听/socket/cgroup 服务 committed0.0.2、坏0.0.3回退0.0.2，4棵树证明清空。Android 源码自通过的 e7602ec 后未变，无需重复同一构建。
- 命令：`gh run download 37646940566 --name desktop-remote-source-Windows --dir artifacts/ota-alpha3/Windows`、同 run 的 Linux artifact，以及 `gh run download 37644338199 --name desktop-remote-android-debug --dir artifacts/ota-alpha3/android`。下载后使用 `updates.security.unpack/verify_tree` 校验每个原始包哈希、文件清单与内容，再逐文件对比 Git 构建输入。Windows 仅 AGPL TXT 与 requirements TXT 的 text=auto checkout 换行不同；校验未忽略任何文件或包哈希。
- `v0.1.0-alpha.3` 私有开发预览归档主 APK、两个源码 ZIP、Android 报告、逐包核对报告与 SHA256SUMS；哈希和大小写入 STATUS。此记录仅交付源码与构件，签名首装、长期更新源、手机覆盖安装、真实 Desktop/手机升级和现用中继部署均未执行。
- 下一步：确认独立测试更新源及受控首装签名资源，在新安装完成真实 HTTPS N→N+1；任何现用 Gateway/FRP、身份或公网服务切换仍依用户先前约定另行批准。后续版本继续在三个 releases.json 与生成历史记录变化。
