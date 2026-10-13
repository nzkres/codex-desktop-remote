# 简化重连与三端 OTA：2026-10-13 实施记录

## 本批目标与顺序

只维护“手机 → 私有中继 → 本机连接器 → 原 Codex Desktop 任务”这条链路。Desktop 始终执行任务；不复制任务、不新增执行器、不修改 Desktop 权限、不重新配对。

| 优先级 | 本批工作 | 完成证据 |
| --- | --- | --- |
| P0 | 保存注释、学习地图，建立功能分支 | 注释提交 `8f61adc`；开发分支 `codex/simple-reconnect-alpha9` |
| P1 | 断网时仍能主动安全断开，再用原身份连接 | 本机 drain 核验、完整身份 CAS、原句柄两棵进程树退出；合成生命周期回归 |
| P2 | Desktop 失联后重新找到原任务 | 重新发现 owner、全新快照、固定任务/项目/完整权限，未知回执不重放 |
| P3 | 简单状态与再次连接提示 | 登记、传输、任务验收分开；按钮不排重复连接 |
| P4 | Windows alpha.9 候选与更新验收 | 源码测试、签名包、隔离助手提交/回退；实际切换单列 |
| P5 | Android alpha.6 与 relay alpha.6 连续 OTA | Android 模拟器故障诊断、受控 APK 构建；relay 独立 cgroup 演练及维护升级 |

## 修复的具体原因

旧版将“服务器登记仍 connected”作为正常退出的条件。断网时登记转为 reconnecting，即使本机进程仍受控也跳过 drain，留下 runtimeNeedsReview。本版把网络可达与本机退出证明分开：用户主动退出，且最终状态和完整身份一致时，允许本机核验；仍必须确认没有结果未知的操作、两个原句柄进程树退出后才能解除本次运行标志。旧的异常现场不能靠本版自动清除。

owner 是当前在 Desktop 内承接原任务的执行窗口身份，revision 是这条状态流里的快照编号。明确 IPC 断开或 owner 离线后，旧 owner/revision 失效；本版重新发现并读取同一任务的新快照。没有明确失联却换 owner 仍拒绝。每次发送继续检查任务、目录、权限、忙闲、回执；不因重连自动发送任何消息。

connected 只说明服务器登记在线。probe_verified 只证明中继与本机接口的传输探针通过，不能证明 Desktop 可以执行新消息。手机打开原任务和手机/电脑双向接续是另外两层验收。

## 用户操作

- 短暂断网：保持窗口，等待自动重试；不重复配对。
- 主动断开：点击“断开”，待窗口明确显示安全断开；恢复时点“再次连接”。
- Desktop 重开：打开原任务，手机重新进入或刷新；权限不一致时在电脑核对，不能以重连代替授权。
- 结果未知：先在原任务核对，不能重复发同一条消息。
- 更新：先安全断开，在原窗口检查更新、阅读说明、下载并安装。下载完成不等于成功，独立助手必须实际启动并核验新版。

## 三端候选与切换次序

开发目录：`C:\code\my-code\codex-desktop-remote`。独立组件版本为 Windows alpha.9、Android alpha.6、relay alpha.6；本机已安装 Windows alpha.8。手机与服务器最近已验收记录是 alpha.5，实际切换前须重新读取现场。

1. Windows：核对签名、sequence 大于本机 ledger 4、固定 Desktop 兼容。当前 alpha.8 的旧退出保护阻断普通 GUI OTA，先审查并确认本次维护方案，再经完整重启边界和独立事务切换；不能覆盖已签版本槽。失败在清理与保全证据完整时回退，激活不明则保持现场。本次维护不能冒称从 GUI 在线完成 alpha.9 OTA；此前 alpha.6→alpha.7 的实际在线更新是独立历史证据。
2. Android：先让 PR 的模拟器回归通过，再合并审核过的输入并从 main 显式构建受控签名 APK；核对包名、versionCode、原证书、嵌入的 test trust。发布独立 Android feed，用户确认覆盖安装；不卸载、不清数据。已安装故障用更高版本修复，不承诺 Android 自动降级。
3. relay：从私有 HTTPS 下载并暂存已签 alpha.6。真实维护前检查活动任务、审批、配对和未确认操作；通过现有宿主 apply，核对旧树退出、数据库关键内容、新服务及设备重注册。失败只在候选树退出、数据保持后回退；不恢复旧数据库覆盖新数据。

服务切换需要具体候选和回退方案核对完成后安排。本批的测试、签名或 CI 都不自动构成真实安装成功。

## 验收记录

结果持续补充至 STATUS.md 和 WORKLOG.md。真实手机验收使用新的唯一标记，每条只发一次；先在手机发送，收到回复后在 Desktop 原任务接续，再确认手机同任务能看到两条。历史已完成标记不重复发送。

Android 旧 CI 37796918966/37796903996 的已知失败是 adb 等待设备 180 秒超时，UI 测试没有启动。旧运行没有保留模拟器日志，不能断言具体 GPU 参数导致故障；本批加入 KVM 预检、启动退出诊断和设备/开机状态核对，新 CI 结果才是是否修复的证据。

## 最终本地签名候选（未发布）

程序输入固定为 `2a783a06cac7ff0f4fe527280a92af031ca28cac`。后续 CI-only 提交不改变候选源文件。签名标识为现有 `cdr-ota-test-20261008-v1`，私钥仅在本机 DPAPI 库中使用；分别通过真实 Windows/服务器公钥和账本只读验签。

打包时发现工作区和 git archive 的换行转换会影响字节，最终改为直接读取固定 Git blob；所有包内文件逐字节核对。前期未发布的草稿保留并标为 DO-NOT-PUBLISH，没有放入真实候选记录、账本或公网 feed。**只使用 artifacts/alpha9/release-git/build-verification.json 指定的最终文件。**

| 候选 | 字节 | SHA-256 | sequence | Git 文件核对 |
| --- | --- | --- | --- | --- |
| windows-0.1.0-alpha.9.zip | 132648 | `449c5a4b5cf88764dd4662629c1e835dc401d0f81663d1c0796f13da9e2f2c9f` | 5 | 43 |
| relay-0.1.0-alpha.6.zip | 84517 | `0d2b828cc75a19d7e861e576c1da420856df7eb8178bc2726a04d61b4778be8d` | 2 | 29 |


windows metadata SHA-256：`5e9afe18f2d42d3bf49b59fb2720490b5d384b8bfb1bfe0c32fa3a0dcbe3f9b2`。

relay metadata SHA-256：`a49869d58599a5c2f1c43ddb0f67001a68d179e514ed1dfecfc407523bcda880`。

Android 当前没有受控签名 alpha.6 APK，不能用普通 debug APK代替。Core CI 38343263687 两平台测试、演练与打包成功，整体因 artifact 存储配额失败；没有本轮可下载 CI 包。Android 最终 [e77079a / 38345972326](https://github.com/nzkres/codex-desktop-remote/actions/runs/38345972326) 实际模拟器 14/14 仪器测试、单测/lint/编译/版本检查通过；artifact 上传因配额失败，整体失败，尚无受控签名 alpha.6 APK。

## 当前真实现场与后续切换

只读核对本机仍 Windows alpha.8/committed/ledger4，但 runtimeNeedsReview=true，普通 OTA 身份前检拒绝。当时应用/运行锁可取得，未发现指向本安装的进程或65250监听；这不能替代原句柄完整树退出证明。alpha.9 的正常断网退出修复不能自动清理旧现场。原任务仅打开历史后仍 owner_unavailable，已请用户用一次新标记恢复执行端。没有删除保护、修改权限或重配对。

本次维护准备必须保留完整原身份/配置/权限/任务、旧签名槽和旧事务，锁定候选与原状态，在新的完整 Windows 启动后重新核对全部内容及原任务空闲/权限/回执。任何差异停止。这种一次性维护工具不进入产品包，也不代表通用自动恢复。实际安装成功仍需事务/版本/保全证据及手机同任务验证。

服务器仍 relay alpha.5，宿主 healthy/ready、ledger1；Windows feed alpha.7/seq3，Android和relay feed alpha.5/seq1。三包大小/SHA匹配，六个匿名HTTPS请求均401；未部署。发布只替换对应组件 latest.json，保留版本化包及旧索引审计副本；不公开静态目录、不修改旧Android接口、不停止FRPS。relay真实apply安排在Windows和Android验收之后，经受管维护门禁执行，不用systemctl restart。

GitHub 本仓库26个旧未签名debug临时产物已下载，大小、ZIP CRC、SHA独立复验，共296503472字节。清理需用户批准 exact review-list.csv，保留最新debug、受控签名APK和Release；额度可能需6–12小时重算。

## 本机一次性维护工具验收（尚未运行真实维护）

工具只在本机忽略目录 `artifacts/alpha9/repair-once.local.py`，SHA-256 `e0b49467fad3b44253ceb564e8e9f93397d64142af604f99ddf68025a5d8d2ea`。它固定本安装、旧 alpha.8、最终 alpha.9 包和 sequence5，不随产品分发，不可跨电脑运行。

GPT-6 Sol medium 实现及另一代理独立审查；根代理检查代码并独立复验 13/13 合成测试。审查发现的旧 staged 归档/次 journal 路径预存问题已改为清 guard 和推进账本前拒绝。测试覆盖新启动边界、身份/任务 CAS、权限/回执拒绝、候选替换、并发实例、写入中断、旧 staged 原字节保存、候选回退和激活结果不明。

另由根代理实际运行 `python -B artifacts/alpha9/repair-gui-smoke.local.py`：完全临时合成 home 中真实 `RepairDriver.start/verify/activate` 成功提交 alpha.9（一次启动）；故意让新候选健康检查失败后回退 alpha.8（两次启动）。两例激活窗口持有应用锁，外层原始 Windows Job 完整清理自有树。此演练的 boot 和 Desktop 探针是合成夹具，不是实际重启或原任务验收。

真实 `prepare/resume` 均未执行。只有原任务新鲜探针通过、用户审查具体维护并批准后才准备；之后通知用户保存工作并完整重启。恢复时重新验证所有固定摘要及原任务，不要求重新配对、不修改 Desktop 或连接器权限配置。任一步出现差异停在可诊断状态，不反复尝试或删除围栏。

## Android CI 最终回归结果

`e77079a` 使用明确 AVD 路径、API35 AOSP default x86_64 镜像、2048M 临时数据分区，KVM与启动诊断保持。run `38345972326` 的四组仪器回归实际执行 14 项：日志先报 `Starting 14 tests on emulator-5554 - 15`，随后 `Finished 14 tests` / `BUILD SUCCESSFUL in 2m 58s`；主代理独立核验该步骤 success。它覆盖首页选择目标、任务目标切换、远程终端界面和通知服务，不等于真实手机 OTA 安装。

同一运行单元测试/lint/debug APK 构建与包内说明核验均 success；最后 `Save untrusted debug APK and reports` 因 `Artifact storage quota has been hit` failure，整体 failure。没有跳过回归或软化上传门禁。下一步仍需获批清理已完整备份的精确临时产物、等待额度恢复，然后受控 main 构建与真机 OTA；普通 debug 产物不能代替原证书签名包。

## 2026-10-13 — 已批准清理完成，原任务恢复；权限差异待选择

用户批准固定清单的26项清理。执行前再次核对 review-list.csv SHA、26份本机ZIP的大小/CRC/SHA，以及远端ID/名称/来源run/创建时间/大小；26个DELETE均成功。删除后完整清单确认恰好只少这26项，最新debug样本11558639319和其余产物保持，剩余87项。共296503472字节，未碰Release或签名APK。忽略目录 ci-artifact-backup/deletion-verification.json 与逐项journal保存证据；尚未验证GitHub配额重算或重新上传成功。

用户完成 ALPHA9_PREFLIGHT_READY；Desktop read_thread 独立确认同一原任务 completed/idle。新鲜只读快照 sameTask/兼容/空闲/回执通过，但 permissions_changed；历史快照 isComplete=false，未在权限不匹配时发起历史加载。对照此前已批准权限记录，其摘要仍与现用配置吻合：当前审批者名称为已支持的旧别名，sandbox writableRoots从1到2（新增项是原任务可视化目录，原runtimeWorkspaceRoots已包含）；excludeSlashTmp/excludeTmpdirEnvVar从true到false，networkAccess仍false。不能据此推定整套权限等价或自动重写摘要。

已请用户选择沿用旧授权还是采用Desktop当前设置。真实alpha.8、身份、配置、权限记录、运行保护和主事务均未修改，未执行prepare/resume或安装alpha.9，不要求立即重启；已完成的标记不再重发。下一步按用户选择完成权限核对及具体维护准备，不能直接运行此前要求原配置不变的一次性脚本。

## 2026-10-13 — 当前权限已获批准，alpha.9 维护准备完成，等待完整重启

用户明确选择“采用 Desktop 当前设置，继续修复”。固定本次权限审查后，按该设置只读核验原任务完整历史、空闲和回执全部通过；网络关闭、原任务与目录不变。审批者别名之外，明确纳入本任务可视化目录和两项临时目录标志变化。批准输入只保存在本机忽略目录，不自动跟随未来权限变化。

本机 `artifacts/alpha9/repair-approved.local.py` 为本次获批维护器；原 `repair-once.local.py` 保留但不再用于此次维护。18/18 合成测试、真实隔离GUI成功提交alpha.9/坏候选回退alpha.8均通过。独立审查补齐旧配置及staged归档的持续哈希保全，归档变化时闭锁、不启动回退。正常回退保留用户本次批准的权限摘要，不盲目恢复旧授权或数据。

真实 prepare 已执行成功：主事务写入启动围栏，审查记录绑定完整旧配置/身份/任务、批准输入、候选包、脚本和boot。独立复核所有原始保全文件与完整身份摘要仍一致，保护runtimeNeedsReview仍true；实际版本仍alpha.8，尚未执行resume或改真实permissionsHash。证据 `artifacts/alpha9/approved-prepared-audit.json`。

- 最终维护器SHA：`469c7ffef449d84c90c64208c8e3f5dc7ce661ca6ac15d6aee626f28635cb349`。
- 准备review文件SHA：`dd79f610b0623785481421479cb7a8fe9c0fdc23e4291e490661796fa94ac5f8`。
- 批准权限review SHA：`43b7128ff341a75207a9a83b6536bb78d6d2dcdf0a0c1567f57d43de5795f763`。

已通知用户保存工作、完整重启Windows，连接器继续关闭。重启后先确认新的boot和全部摘要，再核验原任务；如仅历史页无owner，需用户在同一任务发送一次新的ALPHA9_AFTER_RESTART_READY。任何差异停止，不重跑prepare、不改已固定审查文件。只有预检通过后才执行获批脚本resume，以当前任务设置更新连接器摘要并切换候选，再验证真实身份/数据/权限/健康和安卓原任务。尚不声称安装或手机验收成功。

26项获批debug临时产物清理已完成，备份和其他产物保留；配额是否重算及新CI上传仍未验证。Android/relay和公网feed本轮未切换。
