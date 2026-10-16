## alpha.23 接入已有验收对话

本机维护配置可添加 linkedConversations，逐条固定 threadId、projectPath、title、originator、threadSource、allowEmptyHistory；只对已明确授权并由原生 Desktop 核验的已有对话使用。目录扫描同时比对原 SQLite 元数据，不写 Desktop 数据；本机显示标题不改原标题。不修改原创建/发送回执。配置在正常安全退出后原子写入，重新启动原连接器生效；发布包不包含用户接入记录。安装必须继续通过原空闲、权限、身份和回执门禁。见 [接入证据](../EXISTING-CONVERSATION-ADMISSION.md)。

## alpha.21 激活修复

alpha.20存在激活时身份锁冲突，应更新至alpha.21。已有needs_review现场先由维护者核对候选是否接单和原进程，不能删除记录强行重开。复核恢复旧版本后，等待任务空闲、安全断开，再检查更新和安装21。新窗口在助手写入committed后自动连接，保留原身份与回执，不重新配对。安装及真实通道证据见STATUS。

## alpha.20 修复包

替代alpha.19的新版本包，不覆盖旧版本文件。当前连接继续使用alpha.18；等Desktop任务空闲，正常断开并确认安全后，在原受管窗口检查更新、下载并安装。完整配对且无运行保护的窗口在打开或升级激活后自动连接，无需再次点击连接；不设置Windows开机自启。隔离验证期间不自动接单，不清空身份、回执或保护。旧助手只能显示通用健康失败时，查看state/candidate-health.json里的本次候选版本及脱敏错误码；保留整个state供复核，不删交易记录重试。发布和本机安装结果分别见STATUS。

## alpha.19 更新说明

为手机alpha.20提供明确运行状态、结果阅读标记和同GPT账号的匿名匹配信息。等待当前任务结束后，在原连接器正常断开并确认安全，再沿用检查更新、下载并安装、重新连接流程。不关闭Codex Desktop，不删身份、回执或保护记录。发布不代表已安装，不在任务运行中强行切换。

## alpha.18 更新说明

新增授权任务额度事件的套餐枚举。当前任务完成后，沿用受管连接器的正常断开、检查更新、下载并安装、重新连接流程；保留原身份、回执和保护标志。发布不代表自动安装，不因套餐缺失绕过安全门禁。旧任务没有套餐事件时手机显示套餐未同步。

> 最新：Android alpha.11 与 Windows alpha.17 已发布到原私有更新源，两端原签名及实际 HTTPS 完整下载通过。手机/电脑安装待确认，当前连接器仍alpha.16；额度须更新电脑后同步。 见 [安装顺序与发布证据](../ANDROID-ALPHA11-PUBLICATION.md)。

> 2026-10-15：本机已维护安装并实际连接alpha.16；Windows私有源sequence12。异常运行恢复入口与边界见[核验恢复](../WINDOWS-RUNTIME-RECOVERY.md)，具体证据见根STATUS。本次不是新配对或重装身份。

> 2026-10-14发布结果：Windows私有测试更新源已发布alpha.15/sequence11，现装alpha.13已完成正常TLS鉴权下载、签名和51文件校验；尚未安装alpha.15。详见[发布验收](../WINDOWS-ALPHA15-OTA-REVIEW.md)。不用重新配对。

当前用户操作：等任务空闲、本轮对话结束后，在连接器点「断开」，确认「已安全断开」，再点「检查更新」；应显示0.1.0-alpha.15及更新说明，随后点「下载并安装」。Codex Desktop保持打开。若出现等待/错误或新窗口没有出现，保留提示并反馈，不重复安装、不删除保护记录。看到alpha.15窗口后点击连接，再验收手机原对话。下载成功不等于安装或手机功能已验收。

> 版本配合：alpha.15包含按需打开原对话（需要Android alpha.7或更高）及普通手机问答（需要Android alpha.8）。Android alpha.8尚待受控签名发布与手机安装；本次只发布Windows。异步题仍需在电脑回答。沿用原配对及原签名证书，不卸载重配。详见[特殊交互](../MOBILE-SPECIAL-INTERACTIONS.md)。

以下候选及旧版记录按时间保留，以本文顶部和STATUS最新验收状态为准。

## Windows alpha.14 候选：手机点开原对话

此功能需要Windows alpha.14与Android alpha.7配合。现用Windows仍alpha.13；本段不表示候选已经签名发布或安装。先完成CI、原签名包校验及受控升级，保留原配对，不重新配置身份。手机点击原电脑中的标题会请求Desktop加载同一个对话，可能切换电脑页面；后台刷新不自动打开。详细流程与验收见[按需打开](../DESKTOP-ON-DEMAND-OPEN.md)与[最新状态](../../STATUS.md)。

Android必须使用原受控证书连续升级；普通PR debug APK不能覆盖现用测试APP，不要卸载绕过。中继不需要随此次功能升级。下方旧版本安装记录仅供历史参考，以STATUS的实际交付记录为准。

## alpha.12 开发候选：全部对话标题目录

默认新配置不再要求任务 UUID：`scripts/configure.py --home <全新安装目录> --desktop-root <受支持Desktop目录> --node <Node20.exe> --frpc <frpc.exe>`。脚本默认写 `taskScope=all-local`，验证本机目录与兼容性；不读取正文、不发模型输入、不配对。保留旧命令兼容：显式传 `--thread-id`/`--project` 时使用 selected；也可明确 `--scope selected`。旧已配对安装不要重跑配置脚本，必须经保全审查的维护事务升级并切换范围。

同一Python环境提供只读SQLite；连接器自动把已核对的Python解释器路径传给Node，无需额外安装数据库库。按需打开任务，最多缓存16个可回收Follower；活跃写入/未知停止不回收。列表不拉全历史，手机点开才获取，原权限由 Desktop 实时继承。历史不可读不表示没有记录。

原来显式配置的任务继续作为升级核对基线；手机实际接入的活动任务或发送/停止会记在数据目录 `active-tasks.json`，更新前须实时确认空闲且发送/停止结果确定。仅显示标题的历史不视为已接入执行端。这不停止 Desktop 自身的任务。不要删除交接文件或 runtimeNeedsReview 来解除保护。

此段仅描述新候选；实际签名、安装与手机验收见 STATUS。下文保留首装和旧版本历史步骤，版本以 STATUS 最新记录为准。

## alpha.11 候选：增加另一条已授权对话

alpha.11 保留原配置顶层任务，可在 `additionalTasks` 数组追加明确选择的现有 Desktop 对话（总计最多8条）。每项必须单独给出 `threadId`、`projectPath`、`title`、`permissionsHash`，可选该任务明确批准的 `approvedAlternatePermissionsHash`；不会继承主任务权限或自动扫描其他任务。手机继续使用同一已配对电脑，在任务列表选择对话，不重复配对，不新建副本。

本版尚无图形添加入口。维护者先只读检查新对话的真实ID/目录/权限并形成配置提案，测试、签名和回退方案就绪后再正常断开旧连接器，持有应用/更新/运行锁核对完整旧配置和身份后追加配置并升级。不得在连接器运行时直接修改 `fixture.local.json`，不能把配置文件复制到别的电脑。升级前所有开放任务必须空闲；新增任务也包含正在执行本次维护的对话时，必须等待本轮结束，不能为了升级绕过忙碌检查。

手机列表保留无法读取的已授权任务为 `UNKNOWN`；进入后显示真实错误，其他已开放任务仍可使用。共享回执按任务ID隔离；任一发送结果未知会阻止新发送及升级，不能通过切换任务重放。`scripts/diagnose_desktop.mjs` 对所有任务分别诊断，`scripts/update_probe.mjs` 核对全部任务；旧 `scripts/probe.mjs` 是单任务验收工具，会拒绝多任务配置。

实现、签名包、实机安装与手机验收的当前状态以 STATUS.md 为准，下面的 alpha.5 首装示例保留作历史记录。

## alpha.10 候选的已有安装说明（2026-10-13）

原配置的 permissionsHash 继续有效。仅当用户已经明确审核同一原任务的第二套设置时，维护者可在停止连接、正常退出得到确认并保全配置后，写入 approvedAlternatePermissionsHash；它必须是另一份完整权限摘要，不能填写任意值或从报错自动采集。不得在运行中编辑现用配置或已签版本目录。普通 OTA 不会自动添加此字段。

此次主电脑两份审查已有明确授权，现场记录在 STATUS；alpha.10 当前仅候选，安装交接与手机双向验收未完成。用户不需要重新配对，不重复已完成的两条验收消息。失败时保留旧版本和现场，不能声称已确认有读取故障的alpha.9是健康回退。

# Windows 连接器：源码预览与全新受管安装

2026-10-11重连提示：本机Desktop已到26.1007.2314.0，已装连接器仍alpha.7且需维护复核。alpha.8仅为精确兼容与只读诊断候选，未签名安装；不要重装、复制身份或清保护。旧版普通OTA不能越过已消失的Desktop路径，先按[恢复审查](../DESKTOP-26-1007-RECOVERY.md)核对，实际状态以STATUS为准。

返回[安装起点](README.md)，版本变化见 [Windows 历史](../versions/WINDOWS.md)。[alpha.5 私有 prerelease](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.5) 已发布 `windows-0.1.0-alpha.5.zip` 和独立的 `bootstrap-0.1.0-alpha.5.zip`；服务端大小/SHA 与本地一致，详见 [STATUS](../../STATUS.md)。它是受控测试渠道的**源码运行包**，不是 EXE 或一键安装器。测试 origin `https://106.53.126.69:9443` 已从主电脑公网通过正常 TLS、账号鉴权和完整包校验；公网 FRPS `17443` 的合成 FRPC 夹具也已连通。alpha.6 已在测试电脑经受控维护安装，用户确认手机看到同一 Desktop 任务的两条接续回复；这不证明普通 GUI OTA 或 alpha.7 已验收，最新证据见[STATUS](../../STATUS.md)。全新受管安装不读取、迁移或停用旧连接器、旧 Gateway/FRP 和现用身份。

## 前置条件

**当前测试边界：** alpha.6 已通过限定维护路径安装，`guardian_subagent`/`auto_review` 别名造成的手机权限误报在同一任务接续后恢复；这条维护路径不是普通 GUI OTA，历史故障与保护见[设备基线记录](../OTA-DEVICE-BASELINE-20261008.md)和[维护说明](../WINDOWS-ALPHA5-MAINTENANCE.md)。alpha.7 已实现连接窗口的配对失败提示修正：提交结果或重开状态若为 `claim_pending` 且 `lastError=connection_unavailable`，应明确显示“申请尚未确认”，提示核对网络、HTTPS 地址、端口和证书；原申请保留，编辑输入框不能改变已提交申请，也不自动重新配对或删除身份。固定输入与服务器响应错误给出可理解的原因，未知错误不回显原始内容。AppX 路径问题和取消失败申请仍未解决。

alpha.7 的目标验收是从**已实际安装的 alpha.6** 在连接窗口走正常 HTTPS 检查、下载和安装，再核对真实运行版本、原身份/任务数据及手机同任务显示。候选现已通过两平台CI、43文件匹配及本机原密钥签名/验签；固定包、有效期和操作顺序见[alpha.7审查](../WINDOWS-ALPHA7-OTA-REVIEW.md)。**Windows私有feed已发布alpha.7/sequence3；用户随后经原GUI完成下载并安装，实际alpha.7/ledger3/committed、新窗口及身份/权限/任务保持已验收。用户已确认升级后手机两条新测试回复均显示，同任务接续通过。** 此电脑无需再运行安装步骤。对仍处于旧版的安装，须先正常断开；若断开后仍要求复核，保留现场交由维护者处理。 Android 与中继仍为 alpha.5，Windows 仍需外部运行时，未提供一体化 EXE 或托盘。

- 同一个完整的 **Python 3.12 + Tkinter + `cryptography`** 环境用于 bootstrap、配置和启动；源码根目录的 `updates/requirements.txt` 声明 `cryptography>=41,<51`。本源码包不带 Python。
- Windows x64 **Node 20.19.0** 和 **FRP 0.71.0** 的独立可执行文件；脚本固定其 SHA-256，不能用新文件悄悄替换。它们不随源码 ZIP 自动安装。
- 原生 Codex Desktop **26.930.7945.0**，并匹配 [兼容检查](../../src/compatibility.js)内固定的 `app.asar` 与 `app/resources/codex.exe` 指纹；Desktop 是外部执行器，本项目不升级它。还需一条已完成 `DESKTOP_REMOTE_READY` 的专用测试任务及其 UUID、项目路径、Desktop 包根目录。
- 从已核验 bootstrap ZIP 的 `windows/trust.json`、`windows/latest.json` 取得测试渠道首装输入，并与 `windows-0.1.0-alpha.5.zip` 配套。先核对发行公钥的 `keyId=cdr-ota-test-20261008-v1`、DER SPKI SHA-256 `c3835e3e59b362e0b356d3a7b3589c255187ddce34692167f5160b0e5f5e3a4d` 与**已认证仓库记录**一致；同页 `SHA256SUMS.txt` 或 `artifact-verification.json` 不能独自证明来源。元数据 sequence 1，有效至 2026-10-15 06:33:03 UTC（北京时间 14:33:03）；过期需维护者重新受控签发，不能关闭验证。发布顺序见 [OTA 发布](../OTA-PUBLISH.md)。

若只想看界面，在源码根目录执行 `& $PythonExe windows/demo.py`；预览仅使用合成数据，不配对、不保存身份、不连接服务器。

## 全新受管目录

以下可用于**本地隔离临时目录**复验；目标测试电脑的真实连接仍须新身份配对与专用任务另行验收。下载两个 ZIP 并分别解到不同新目录；bootstrap 根目录已核对有 `README.txt`，`windows/` 下有 `trust.json`、`latest.json`。在 PowerShell 中填写绝对路径；`$InstallRoot` 必须尚不存在，不填旧安装或已配对 `data`。签名与整个 ZIP 清单由 bootstrap 核对。Windows Python 3.12.14 临时首装已成功，重复首装被拒且原目录未变；没有启动真实任务。

```powershell
$PythonExe = '<PYTHON_3_12_EXE>'
$SourceRoot = '<EXTRACTED_WINDOWS_ZIP_ROOT>'
$BootstrapRoot = '<EXTRACTED_BOOTSTRAP_ZIP_ROOT>'
$Package = '<DOWNLOADED_WINDOWS_0.1.0_ALPHA.5_ZIP>'
$InstallRoot = '<NEW_MANAGED_HOME>'
$Trust = Join-Path $BootstrapRoot 'windows/trust.json'
$Metadata = Join-Path $BootstrapRoot 'windows/latest.json'
& $PythonExe -B -c "import tkinter, cryptography; print('dependencies available')"
& $PythonExe -B (Join-Path $SourceRoot 'updates/bootstrap.py') --home $InstallRoot --trust $Trust --metadata $Metadata --package $Package --component windows
```

Bootstrap 验证签名、平台、版本和完整 ZIP 后创建 `data/`、`state/`、`versions/`、`staging/`、`trust.json` 与固定入口 `Start.pyw`；失败不会把旧目录变成新安装。**这不是迁移工具。**随后从同一已核验的 Windows 源码目录配置新实例，`--home` 会把全新 `fixture.local.json` 和任务数据放进受管 `data/`：

```powershell
$NodeExe = '<NODE_20_19_0_EXE>'
$FrpcExe = '<FRPC_0_71_0_EXE>'
& $PythonExe -B (Join-Path $SourceRoot 'scripts/configure.py') --home $InstallRoot --thread-id '<TEST_TASK_UUID>' --project '<TEST_PROJECT_PATH>' --desktop-root '<DESKTOP_PACKAGE_ROOT>' --node $NodeExe --frpc $FrpcExe
```

配置脚本只读探测专用 Desktop 任务的 marker、兼容文件与权限，**不发模型输入、不配对**；已有配置不会覆盖。只有它报告成功，且测试 HTTPS 中继与手机账号另行就绪，才启动：

```powershell
$PythonwExe = Join-Path (Split-Path -Parent $PythonExe) 'pythonw.exe'
& $PythonwExe (Join-Path $InstallRoot 'Start.pyw')
```

在窗口填经核验的 HTTPS 中继和设备名，手机生成一次性配对码、核对电脑申请并确认，然后电脑点连接。仅在预先选定的一条 Desktop 原生任务上验收读取、手机文字发送、Desktop 同任务接续及正常断开重连。`connected` 与通道探针不能代替任务完成证据。旧窗口和其身份目录保持原样；不能复制 `identity`、回执或旧 `fixture.local.json` 进入 `$InstallRoot`。

**alpha.5 实机启动注意：** 2026-10-08 已在主电脑新目录完成签名安装、原任务只读探针与实际窗口启动。由 Microsoft Store 打包的 Codex 环境操作 `%LOCALAPPDATA%` 时，可能发生 AppX 路径重定向：配置记录物理路径，但启动器收到逻辑别名，报 `managed_data_directory_required`。保留现场，由维护者核对两路径同一文件、拒链接及完整安装清单后，统一从已核验的**物理安装目录**调用原 `Start.pyw`；不要重装、重复配置或改保护。具体证据、恢复命令及后续版本修复项见[设备基线记录](../OTA-DEVICE-BASELINE-20261008.md)。真实手机任务与 OTA 升级仍需另行验收。

受管窗口底部可检查和下载签名更新。**本测试电脑已经完成alpha.7安装，勿重复安装；以下顺序仅适用于尚未升级的受管alpha.6安装**：确认专用任务空闲，先正常断开，再“检查更新”并阅读说明，最后“下载并安装”。已安装alpha.6账本仍为sequence2，新源为sequence3；如检查仍报保护/防重放错误，保留现场，不能删除账本或runtimeNeedsReview。下载校验与助手交接成功后旧窗口才退出。弹窗中的“将验证并重开”描述成功路径，候选或回退失败仍可能停在需维护者复核状态，不能以窗口关闭证明升级成功。维护者可在源码根目录只读调用 `& $PythonExe -B -m updates.cli --home $InstallRoot status`，`check` 和 `stage` 会访问已配置的 HTTPS source，**普通 Windows OTA 的 `apply`/`stop` 命令会拒绝，应使用窗口安装按钮**。alpha.5 的旧审核别名误报曾使普通窗口预检拒绝，因此 alpha.5→alpha.6 使用仓库专用的[维护切换路径](../WINDOWS-ALPHA5-MAINTENANCE.md)；alpha.6→alpha.7 仍须以实际 GUI 在线事务验收，不可从此前维护切换推定成功。发生 `needs_review` 时保留 `data/`、`state/` 与版本目录，见 [恢复说明](../OTA-RECOVERY.md)。

alpha.4 增加私有下载：首次应先在手机完成新设备确认，再检查更新。它读取本安装已有 DPAPI 身份，不新增更新账号或复制其他电脑身份；缺失身份只提示未配对。更新 source 必须位于已配对 relay 的同 HTTPS origin 的 `/desktop-remote-updates/test/windows/`（stable 独立配置），否则在发凭据前拒绝。设备暂停、撤销或凭据无效时服务器拒绝下载。旧 alpha.3 中继不能提供此私有路由；新的受管 relay 已在隔离环境安装，主电脑公网合成设备 Windows 下载及权限/暂停/撤销拒绝已验证，真实电脑配对和 App 内下载仍待验收。

## alpha.13诊断界面候选

[错误清单与纯演示](../WINDOWS-ALPHA13-DIAGNOSTICS.md)介绍点击错误查看原因和源码。源码/包不等于现用安装；安装状态以STATUS最新记录为准。证书过期需管理员续期并让HTTPS入口重载，客户端不需要重新配对。
## alpha.22 构建验证候选：接管手机创建的空对话

仅 all-local 与固定 Desktop 26.1007.9446.0 开放，使用 Desktop 安装内已核验 codex.exe，不额外下载执行器。辅助进程只经 stdio 创建、命名和读回空对话，正常退出后交回 Desktop，第一条消息仍走原 follower 和发送回执。

本轮只准备构件与测试；现有 alpha.21、身份/回执及私有源不变。尚未确认的断开、运行现场或发送/创建回执不能直接清标志更新。实际候选构建和安装证据见 STATUS.md；没有本机切换或真实手机验收记录时，不视为已启用。
