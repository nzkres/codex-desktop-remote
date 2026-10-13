## 2026-10-13 — 当前权限已获批准，alpha.9 维护准备完成，等待完整重启

用户明确选择“采用 Desktop 当前设置，继续修复”。固定本次权限审查后，按该设置只读核验原任务完整历史、空闲和回执全部通过；网络关闭、原任务与目录不变。审批者别名之外，明确纳入本任务可视化目录和两项临时目录标志变化。批准输入只保存在本机忽略目录，不自动跟随未来权限变化。

本机 `artifacts/alpha9/repair-approved.local.py` 为本次获批维护器；原 `repair-once.local.py` 保留但不再用于此次维护。18/18 合成测试、真实隔离GUI成功提交alpha.9/坏候选回退alpha.8均通过。独立审查补齐旧配置及staged归档的持续哈希保全，归档变化时闭锁、不启动回退。正常回退保留用户本次批准的权限摘要，不盲目恢复旧授权或数据。

真实 prepare 已执行成功：主事务写入启动围栏，审查记录绑定完整旧配置/身份/任务、批准输入、候选包、脚本和boot。独立复核所有原始保全文件与完整身份摘要仍一致，保护runtimeNeedsReview仍true；实际版本仍alpha.8，尚未执行resume或改真实permissionsHash。证据 `artifacts/alpha9/approved-prepared-audit.json`。

- 最终维护器SHA：`469c7ffef449d84c90c64208c8e3f5dc7ce661ca6ac15d6aee626f28635cb349`。
- 准备review文件SHA：`dd79f610b0623785481421479cb7a8fe9c0fdc23e4291e490661796fa94ac5f8`。
- 批准权限review SHA：`43b7128ff341a75207a9a83b6536bb78d6d2dcdf0a0c1567f57d43de5795f763`。

已通知用户保存工作、完整重启Windows，连接器继续关闭。重启后先确认新的boot和全部摘要，再核验原任务；如仅历史页无owner，需用户在同一任务发送一次新的ALPHA9_AFTER_RESTART_READY。任何差异停止，不重跑prepare、不改已固定审查文件。只有预检通过后才执行获批脚本resume，以当前任务设置更新连接器摘要并切换候选，再验证真实身份/数据/权限/健康和安卓原任务。尚不声称安装或手机验收成功。

26项获批debug临时产物清理已完成，备份和其他产物保留；配额是否重算及新CI上传仍未验证。Android/relay和公网feed本轮未切换。

## 2026-10-13 — 已批准清理完成，原任务恢复；权限差异待选择

用户批准固定清单的26项清理。执行前再次核对 review-list.csv SHA、26份本机ZIP的大小/CRC/SHA，以及远端ID/名称/来源run/创建时间/大小；26个DELETE均成功。删除后完整清单确认恰好只少这26项，最新debug样本11558639319和其余产物保持，剩余87项。共296503472字节，未碰Release或签名APK。忽略目录 ci-artifact-backup/deletion-verification.json 与逐项journal保存证据；尚未验证GitHub配额重算或重新上传成功。

用户完成 ALPHA9_PREFLIGHT_READY；Desktop read_thread 独立确认同一原任务 completed/idle。新鲜只读快照 sameTask/兼容/空闲/回执通过，但 permissions_changed；历史快照 isComplete=false，未在权限不匹配时发起历史加载。对照此前已批准权限记录，其摘要仍与现用配置吻合：当前审批者名称为已支持的旧别名，sandbox writableRoots从1到2（新增项是原任务可视化目录，原runtimeWorkspaceRoots已包含）；excludeSlashTmp/excludeTmpdirEnvVar从true到false，networkAccess仍false。不能据此推定整套权限等价或自动重写摘要。

已请用户选择沿用旧授权还是采用Desktop当前设置。真实alpha.8、身份、配置、权限记录、运行保护和主事务均未修改，未执行prepare/resume或安装alpha.9，不要求立即重启；已完成的标记不再重发。下一步按用户选择完成权限核对及具体维护准备，不能直接运行此前要求原配置不变的一次性脚本。

## 2026-10-13 — Android 模拟器 14 项实测通过，剩余配额阻断

最终 `e77079a` 的 [Android PR run 38345972326](https://github.com/nzkres/codex-desktop-remote/actions/runs/38345972326) completed/failure：单测/lint/debug APK、包内版本核验、API35 AOSP 实际仪器回归三步骤 success；最后保存产物 failure。日志实际报告 Starting 14 tests / Finished 14 tests / BUILD SUCCESSFUL in 2m 58s。根代理独立查看步骤结论，GPT-6 Sol medium 提取日志证据；记录在忽略 JSON，未更改 App 功能或上传门禁。

仅同提交重复 Android push 与重复 Core 被取消，保留唯一 Android PR 验证；Core 产品输入仍为已通过两平台测试/演练/打包的 2a783a0。GitHub artifact 配额、用户原任务新鲜预检、具体真实维护/发布批准仍是后续门槛。尚未清远端产物、合并 PR #6、签发 Android alpha.6、安装 alpha.9 或切换 relay/feed。源码、候选、CI、安装和手机证据分别记录。

## 2026-10-13 — 最终签名候选与本机维护隔离验收

Windows/relay 固定 2a783a0 Git blob 的 43/29 文件逐字节核对，最终已签包在 artifacts/alpha9/release-git，分别 132648/84517 字节、sequence5/2，现用公钥和账本只读验签通过；无发布或安装。错误换行草稿完整保留并标 DO-NOT-PUBLISH。精确哈希和来源写入 docs/RECONNECT-AND-OTA-20261013.md。

只读现场发现实际 alpha.8 runtimeNeedsReview=true，普通 OTA 被拒绝；原任务打开历史后 owner_unavailable，Desktop read_thread 报 notLoaded，未发送模型消息。一次性维护候选经过双代理审查，根代理 13/13 合成测试、真实临时 GUI 成功提交 alpha9/坏候选回退 alpha8 均通过；已前置拒绝历史归档和次 journal 冲突。真实 prepare/resume 零次，未清保护、改权限、重配对或要求立即重启。

服务器仍 relay alpha.5/healthy，匿名下载六项401，新 feed 未发布。GitHub 26 个旧 debug 临时产物已全量备份，大小/ZIP CRC/SHA复验，共296503472字节；清理仍等精确批准，尚未删除。STATUS与HANDOFF同步当前停点，不能把测试、签名或历史手机验收当作本轮真实切换。

## 2026-10-13 — Android 仪器测试仍待启动

80c03c7 的 Android PR 38345259612 再次通过单元测试、lint、debug APK 与包内版本核验，但即使 AVD config.ini 写入 2048M，模拟器仍要求 7372.80 MB、运行机可用 6539.45 MB，四组仪器测试未开始。该需求与 AOSP emulator 源码中 PlayStoreImage 特性的 6 GiB 下限和 1.2 倍空间检查一致，但旧日志未记录特性标志，不能确定唯一原因。现将隔离回归镜像改为同 API 35 的 default x86_64 镜像；失败时仅输出 AVD 的分区大小、PlayStore.enabled 与 tag.id 以核对配置，保留完整 UI 测试。debug artifact 上传仍因 GitHub Actions 存储配额失败，上传门禁未放宽；待下一 PR CI 实测。

## 2026-10-13 — Android 模拟器空间故障复核

13cdab6 的 Android push 38344533538 / PR 38344536681 均通过单元测试、lint、debug APK 与包内版本核验，随后在模拟器启动前失败，四组 UI 仪器测试仍未执行。日志显示 KVM 可用、AVD 已找到，临时磁盘剩余约 6.4 GB；模拟器默认 userdata 仍要求 7372.80 MB（PR 运行报告可用 6539.36 MB）。`-partition-size 2048` 未覆盖 AVD 自身设置，现改为在临时 AVD 的 config.ini 明确写入 `disk.dataPartition.size=2048M`。两条运行的 debug artifact 上传仍因 GitHub Actions 配额失败；签名与产物门禁未放宽。待新 CI 验证模拟器及 UI 测试。

## 2026-10-13 — Android AVD 路径修复通过，继续解决测试盘空间

08b784e 的 Android push 38343850851 / PR 38343854235 已能找到 cdr-regression 并通过 KVM 检查，但模拟器创建默认 userdata 分区要求 7372.80 MB，运行机仅余 6539.56 MB，启动前报 Not enough space。新增临时模拟器 -partition-size 2048 和 df 诊断；不修改 App 数据结构、不跳过 UI 测试、上传失败仍保留失败状态。待下一运行核验。Windows/relay 签名候选输入仍2a783a0，CI-only修复不改变候选内容。

## 2026-10-13 — 新 CI 暴露 AVD 路径与产物配额问题

Core push 38343263687 在 Windows/Linux 的测试、隔离演练和打包步骤均成功；两个 upload-artifact 步骤因账号 Artifact storage quota 拒绝，整体失败，没有本轮可下载 CI 包。Android push 38343263837 单测、lint、APK 构建及版本核验成功；新增日志确认 emulator 37.2.12 无法找到刚创建的 cdr-regression AVD（KVM 可用）。已显式统一 avdmanager 与 emulator 的 ANDROID_USER_HOME/ANDROID_AVD_HOME 和 --path，并在启动前检查可见性，等待新 CI。未跳过界面回归、降低签名检查或清理现用服务。

## 2026-10-13 — P0–P3 实现，P4/P5 候选验收中

当前开发目录为 C:\code\my-code\codex-desktop-remote，分支 codex/simple-reconnect-alpha9。已有学习注释已单独保存为 8f61adc，不覆盖旧工作树。Windows alpha.9 修复断网时主动退出的本机核验，明确失联后重新发现 Desktop 原任务，完善分层状态/再次连接提示；完整身份、权限、未知回执、原句柄树清理与异常现场保护保留。补充转发前快照变化竞态和未核权限加载历史的回归。Android alpha.6 候选整合原 PR #4；旧 CI 确认为 ADB 等待超时，新增模拟器启动诊断，仍需新 CI 证明。relay alpha.6 仅作为连续 OTA 演练候选，不改 API 或数据库结构。

本机主代理复验：Node 84/84；Python 157 项中 151 通过、6 平台跳过；Windows relay 77 项中 73 通过、4 平台跳过；WSL relay 77/77。Windows 实际合成 GUI/helper 升级与回退、六种维护提交/闭锁、私有 HTTPS GUI 提交/回退通过；WSL 合成 cgroup 升级、坏候选回退、四棵自有树退出通过，临时单元已回收。Tk 合成窗口和版本说明截图已核对。版本历史校验通过。日志在忽略目录 artifacts/alpha9；不含真实身份。具体实现/验收见 docs/RECONNECT-AND-OTA-20261013.md。

本轮只读核对现用 Windows 仍 alpha.8、transaction committed/success、ledger4，Desktop 26.1007.2314.0；未观察到连接器可见窗口，不能据此断言实时通道健康。未升级现用安装、未修改原身份/权限、未部署 feed 或中继；手机本轮未验收。候选签名、CI 和真实三端切换分别追加结果，不把代码或合成测试当实机成功。

# 工作记录

## 2026-10-12 — service.js 逐步教学实验

本机忽略目录 artifacts/learning 新增 service-walkthrough.mjs 与 Open-Service-Lesson.cmd。真实本机 HTTP/Service/Receipts，模拟 Desktop，9/9 场景自动验证通过；交互模式逐步回车。产品源码、现用任务与身份未变。

## 2026-10-12 — Follower 零基础术语注释

为 src/follower.js 增加100行自包含中文教学说明，解释 Desktop、owner/clientId、任务ID、IPC、快照、事件、Promise 与请求流程；强调协议客户端标识不等于进程PID，范围检查不等于完整权限检查。仅插入注释/空行，原行保留；Node语法检查与3项Follower回归测试通过。现用安装不变。

## 2026-10-12 — 中文核心注释

- 在新目录 C:/code/my-code/codex-desktop-remote 完成 38 个文件的中文教学注释；分工复核后确认 diff 只有注释/空行新增，Python AST 与非注释 token 一致。保留已有未提交教学文档。
- Node 72/72 通过；Python 共152项，146通过/6跳过，版本目录一致性校验通过。报告位于忽略目录 artifacts/learning，不改真实连接、身份或已签名安装。

## 2026-10-12 — 教学目录与隔离测试菜单

新增完整 Git 文件树快照和逐步阅读指南，复用现有 demo 与 81 项合成测试。本机菜单在忽略目录 artifacts/learning；正常用户环境三组通过。首次 PowerShell stderr 处理已修正，沙箱任务测试失败记录保留。产品代码和现用安装未变。

## 2026-10-12 — 获准修复、安装及安卓验收完成

- 用户明确同意以当前原任务设置继续修复。保留原审查记录、启动证明和完整状态检查，新增本机忽略目录 approved-repair.local.py，先审查后 amend/resume；没有将一次性维护代码加入产品。
- 一次性维护测试最终 10/10 通过（含原 7 项；首次夹具缺文件失败已修正重跑），独立代码审查无阻断。真实 amend 保持安装未变；resume 返回 committed/success、0.1.0-alpha.8。
- python -B artifacts/desktop1007/verify-alpha8.local.py 独立核验签名槽、身份、任务、批准配置、旧槽、应用锁和新鲜任务探针，结果保存 installed-alpha8-audit.json。窗口检查确认 alpha.8 GUI 已启动。未修改 Desktop 设置、未重配对。
- 用户连接后在安卓原任务读到 REMOTE_AFTER_RESTART_READY，回复“能看到，原任务已恢复”。标记未重发；手机读取通过，不额外声称本轮新消息双向执行通过。此次只补文档，不改签名包、不发布源、不变更 Android/relay。

## 2026-10-12 — 重启后权限差异复现，尚未resume

- 完整重启已实测，verify-prepared通过；首次owner_unavailable后用户打开原任务并完成REMOTE_AFTER_RESTART_READY。随后实时快照仍permissions_changed，不能把之前匹配的状态当现在通过。
- 只读源码复核具名profile请求虽不直接发送snapshot sandbox，但追加目录筛选仍使用它，缺完整后端展开证据；未实现忽略或自动规范化。当前任务只读审查idle/receipts通过，网络关闭、原任务和目录固定，候选设置仅保存忽略目录。
- 已请求用户明确允许按当前任务设置重新确认连接器记录，等待答复。原安装、权限摘要、DPAPI、任务数据及维护围栏未改；没有运行resume，不要求再次重启。

## 2026-10-12 — 一次性修复进入重启等待

- 用户完成REMOTE_RECONNECT_READY，recheck.local.mjs实机确认标记completed、原权限摘要匹配、idle/receipts通过。此前profile推断差异不是需要改权限的证据；原permissionsHash保持。
- 用户正常关闭GUI。忽略目录repair-once.local.py复用现有签名/存储/Transaction/WindowsDriver，只针对本home的alpha.7→alpha.8，不进入产品包。预期唯一配置变化desktopRoot；原数据完整CAS、完整重启边界、旧版本禁止健康回退。7项合成测试覆盖prepare保持、同boot拒绝、回执变化、重复实例、未结旧维护、事务间竞争、成功及坏候选；boot/probe/GUI在这些测试中模拟，签名/DPAPI/文件与事务真实。
- 代理复核发现事务间数据竞争和旧维护未结检查缺口，修正后复核通过；不把prepare双写中间中断称启动围栏成功，原运行guard仍阻止通道。当前脚本摘要4d8014b72c68668e43cdc84a30799eb38e326d366417451d69e8c4dbb53ed131。
- prepare实机成功，verify-prepared.local.py独立复核身份/配置/任务/信任/账本/current前后不变，primary围栏确实写入，原guard仍true。未执行resume，等待用户完整重启后再验。不重发未知消息、不关闭Desktop、不修改Desktop内部文件或授权。

## 2026-10-12 — 简化维护准备

- 最终移除未接入模块后，完整Python152项146通过/6跳过（正常用户隔离环境），Node72通过；版本历史只读校验及git diff --check通过。候选未安装，继续等待用户原任务标记。

- 用户要求停止新增通用恢复框架并继续修原任务连接。删除本轮未接入的recovery_evidence模块及测试，无现用功能删除。保留分项诊断，Node72项通过。
- 本机只读audit-install确认alpha.7包清单、committed成功事务、DPAPI存在、2条accepted回执和受保护文件前后原字节相同。当前端口无监听，原Job不可恢复；Windows休眠/唤醒时间不能充当断线因果证明。
- review-current.local.mjs限定原任务和既定目录、网络关闭，未通过实时profile核对即停止，未生成或应用新配置；已请用户在原任务执行无工具REMOTE_RECONNECT_READY。
- check_versions.py --write和pack windows通过；sign-candidate.local.py在内存解封原DPAPI密钥签发sequence4，按现用trust/ledger验签通过。新ZIP96,115字节/SHA e9ae53cced437131a8220ec461d00978f1555cfd8245329c5feac2430d1225d8；未发布安装。忽略目录保留脱敏证据和本机维护脚本，不将真实状态提交Git。

## 2026-10-11 — 标记完成后的只读核对

- 用户回报已回复；recheck.local.mjs读取原任务确认DESKTOP_1007_READY completed、compatible/sameTask=true、permissionsPreserved=false，permissions_changed；自动模型输入0。未发新消息或改变安装。
- diagnose.local.mjs在内存对比10条turn_context，仅替换sandbox writableRoots和两个tmp排除标志时均匹配原摘要。历史记录不能成为实时授权依据；没有修改产品权限判断。asar只读跟踪发现具名profile和sandbox投影并存，完整等价性仍未证明。
- 核对PR CI38124021866：两平台测试、构建及合成生命周期步骤通过，upload-artifact因存储配额满失败；整体失败，无可下载CI产物。保留现有产物和配额配置。本轮仅增补证据文档，不重复无变化的产品测试。

## 2026-10-11 — 新版 Desktop 重连候选

- 重新fetch确认PR2/3/4仍open，保留三个已有worktree，从515313d新建codex/desktop-1007-recovery。没有改Android工作树。
- 本机26.1007.2314.0双文件哈希与asar关键协议只读核对；保留旧pin并新增精确pin。独立代理复核兼容及恢复证据，原句柄丢失不能凭PID/端口清保护。
- 实机只读sameTask/marker通过，permissionsMatch=false、permissions_changed，模型输入0。原任务历史仅核对权限字段，原始数据留在本机；忽略目录artifacts/desktop1007存诊断与日志。不改安装配置、身份、权限摘要或Desktop文件。
- 新增只读诊断脚本与5项合成测试；完整node --test 70通过，check_versions.py通过。用户已打开任务，等待DESKTOP_1007_READY完成后复验；冷恢复未实现/切换，详见专项审查。
- 最终Node70、Python146通过/6跳过；未签名源码pack及内部解包清单自检通过，95,914字节/SHA e054754f8eba28afe6ef60762bdd295b9aeec1ae5b61252f1a2435881f744932。沙箱首次pack_failed，正常用户重跑通过。正式诊断实机输出compatible/sameTask=true、taskReady/reconnectAuthorized=false、permissions_changed；未写原配置。

## 2026-10-08 — 用户主动完成 Windows alpha.7 GUI 在线安装

- 用户回报“我已经点击下载并安装了”。没有再触发安装、重发任务或停止窗口；立即只读核对current alpha.7、ledger3、主事务committed/success、old alpha.6，签名槽有效。
- 执行忽略目录`python -B artifacts/alpha7-preparation-20261008/verify-installed-alpha7.local.py`：staged envelope与原发布metadata一致、事务releaseDigest和candidate/old指针匹配；原信任/兼容档/seq3签名通过，暂存ZIP95,085字节/SHA与发布包一致，旧alpha.6槽重验保留。用产品snapshot重新计算完整稳定身份、配置/权限、任务目录所有文件摘要，与事务preserved完全相同；应用锁持有。只读专用Desktop探针idle/permissionsVerified/receiptsCertain=true、version alpha.7。install/current/trust/ledger/transaction核验前后原字节相同，自动模型输入0。
- 只读CIM与窗口核对仅限本安装精确路径：唯一非gate GUI标题「Desktop 手机联调 · 0.1.0-alpha.7」、窗口句柄有效，其父进程为同一签名槽启动命令的可信child_gate；两者创建身份记录仅存忽略目录。脱敏报告final/installed-alpha7-verification.json和installed-alpha7-gui.json。没有按PID或名称终止任何进程。
- 已请用户在新窗口连接，于手机原任务发送一次PHONE_ALPHA7_OK，收到后由Desktop同一任务发送一次DESKTOP_ALPHA7_CONTINUE_OK，确认手机两条均显示。用户随后明确回复“手机两条回复都能看到”；实际GUI在线升级和升级后同任务接续闭环验收通过，手机结果来自用户确认，不把探针当完成。Android/relay仍alpha.5；本轮只写验收记录，无产品代码/版本变化。

## 2026-10-08 — 已批准的 Windows alpha.7 私有源发布

- 用户明确回复“批准发布 Windows 私有更新源”；本轮不执行当前电脑安装、手机/relay升级或服务停止。最初正常SSH执行自动审批两次超时，受限环境被拒；随后取得本轮网络及SSH目录只读权限，正常用户上下文成功连接，StrictHostKeyChecking保持，无跳过指纹、无私钥打印/复制。此执行权限问题已解决。
- 原目录cdrtest/0700，latest cdrtest/0600；只读签名/包核对原源alpha.5 seq1，metadata SHA `c67f65f67a32b42a4e1c8ea5444146aaa3d97159bedf130ba9dd0d8e8f020db2`。记录Android/relay全部feed摘要、relay current、旧Caddy配置和测试relay/FRPS原进程身份。GPT-6 Sol medium只读审查发布助手，指出root导入源码/审计目录权限必须先证实；根代理补所有相关祖先/root源码树root-owned且不可由group/other写的断言，并用Python -B -I执行，复验通过。
- 命令入口均为忽略目录一次性本机助手：`python -B artifacts/alpha7-preparation-20261008/publish-feed.local.py inspect`（只读）；`verify-feed-https.local.py before`使用产品for_installation遭`identity_or_runtime_requires_review`拒绝。源码确认连接运行期保留此保护，未绕过或删除、未断开连接。改为已有隔离测试账号临时会话：`verify-feed-https.local.py before --account`通过，凭据只在内存；只注销本次会话、不影响手机会话。
- 执行`publish-feed.local.py publish`成功：原包95,085字节/固定SHA及原metadata签名复验、磁盘检查、root-only审计、独占发布锁、旧完整快照比较、独立暂存和43文件校验；新ZIP无覆盖落位、再次核对旧索引原字节后原子替换latest并fsync。仅Windows seq1→3，旧alpha.5包保留，其他feed/relay当前槽/旧Caddy/服务进程均未变。发布者私钥未上传，源包未重新构建。
- 独立`publish-feed.local.py verify-server`只读复验通过；`verify-feed-https.local.py after --account`从主电脑公网正常TLS下载alpha.7，metadata/ZIP匿名401，原信任/兼容档/seq3/95,085字节/SHA `91676bd9b0e1526c5808fb6a8d8156c2511f38ea4f306b1069d20d95d0412266`及43文件清单全部通过，临时会话注销后拒绝。客户端install/current/trust/ledger/transaction前后原字节相同，仍alpha.6/ledger2，未调用engine check/stage/apply。
- 服务器root-only审计`/opt/codex-desktop-remote-test/operator/windows-alpha7-feed-20261008/`保留旧新索引、固定ZIP、前后报告；本机final/feed-{preflight,published,server-verification,https-after}.json脱敏报告保留在忽略目录。本轮只更新仓库发布/安装/交接证据，不改软件版本或重跑不相关产品测试；检查版本文档与diff。下一步安排用户正常断开后的真实设备下载、alpha.6→7 GUI安装及同任务验证，未将账号下载冒充设备OTA。

## 2026-10-08 — alpha.7 配对错误提示与 Windows 在线 OTA 准备

- 最终代码`c20c2036e6319f9dca1ef108f7e8830f893c7f89`的Core push37781824455/PR37781829558均Windows/Linux成功，Windows六种维护与HTTPS GUI提交/健康回退均通过。日志核对Windows Node64、Python146通过/6跳过、relay73通过/4跳过；Linux Node62通过/2跳过、Python132通过/20跳过、relay77通过、deploy3通过。根代理额外完整Python152项146通过/6跳过，耗时122.354s。原生GUI、Linux cgroup演练均成功。
- `gh run download 37781824455 --name desktop-remote-source-Linux --dir artifacts/alpha7-preparation-20261008/final/ci`；根代理用updates.security清单/解包和逐文件Git原字节核对43源文件，确认与实际已签alpha.6相比仅windows/gui.py、windows/releases.json变化。原unsigned候选未签未发布，独立final目录保存本次固定包；CI重打relay包不替换已部署同版本资产。
- 使用原DPAPI库执行`python -B -m updates.publish sign windows --artifact <final/windows-0.1.0-alpha.7.zip> --output <final/windows-latest.json> --signing-home <原项目库> --key-id cdr-ota-test-20261008-v1 --source https://106.53.126.69:9443/desktop-remote-updates/test/windows/latest.json --url https://106.53.126.69:9443/desktop-remote-updates/test/windows/windows-0.1.0-alpha.7.zip --sequence 3 --channel test`。只在内存中解封原私钥，不导出/重建/配置Secrets。包95,085字节/SHA256 `91676bd9b0e1526c5808fb6a8d8156c2511f38ea4f306b1069d20d95d0412266`；metadata SHA256 `aa6dbad8c3eb1e1bf941008cc31c5bcd46277af91059d6bdbcce9c7db3cec81c`，issuedAt1791465129/expiresAt1792069929。
- 根代理用真实安装trust/ledger/兼容档只读验证签名、包大小/哈希和序号2→3可接受，实际current仍alpha.6、ledger仍2、transaction committed；install/current/trust/ledger/transaction原字节前后不变。脱敏证据final/signed-candidate-verification.json。未发布alpha.7 Release/feed、未暂存进真实安装、未断开/退出窗口或发送任务。
- GPT-6 Sol medium复核版本/安装/发布文档，根代理修正旧alpha.6未构建说法，并补发布seq3前不要查旧seq1 feed、安装弹窗只描述成功路径、未知结果保留needs_review等操作说明。alpha.7仅配对提示，不继续扩大GUI范围；签名后只追加仓库证据，不重打包。下一步按审查清单请求Windows私有test源发布及本机alpha.6→7在线升级确认，Android/relay后续分开推进。
- 首轮 ab738c0 的 Core push37779857781 两平台通过；PR37779946976 的 Linux通过、Windows在既有maintenance第4例失败：原helper句柄退出后立即读取外层Job仍非零。保留失败结果，未签发该轮候选。夹具改为同一原Job最多5秒观测，必须实际归零才能通过；不终止进程制造通过、不按旧PID重开、不改产品树清理或保护，持续残留/观测错误仍失败。后续定向实测与两轮CI结果单独补记。
- 上述夹具修正根代理复验：`python -B scripts/windows_maintenance_smoke.py` 六例全部通过，初次/等待续审/正常关闭续审各 committed 与 needs_review；`python -B -m unittest discover -s test -p test_maintenance_smoke.py -v` 三项通过，包含持续残留超时和观测异常失败。版本目录检查、diff检查通过。真实安装只读校验仍为alpha.6、ledger2、transaction committed、签名槽有效；未解密身份、未改变连接或状态。
- 根代理独立执行`python -B scripts/windows_https_ota_smoke.py`两例通过：synthetic_https_gui_committed / rolled_back，身份与任务保持。随机loopback TLS、临时CA、两项匿名401、真实GUI worker设备鉴权GET、seq2→3、独立helper原句柄与Job、旧primary归档/secondary原字节保持、陈旧candidate/staged替换均验收。新增CI step。代理首次演练漏持旧GUI应用锁，补为与原smoke同样的完整主窗口生命周期后重跑，根复验通过；没有改产品门禁。两版本为当前源码合成包，Desktop/FRP探针替换明确隔离，不使用真实身份/签槽。
- 用户批准按先Windows、再Android、最后relay的小步方案执行；当前轮只准备alpha.7代码、测试、签名候选与私有源审查，发布和实际切换保持最终确认。远端main为4f50d30，原分支421a4c3和PR #2草稿未变；从该提交建立独立工作树codex-desktop-remote-alpha7 / codex/windows-alpha7，保留原开发与现用安装。
- 根因在GUI忽略配对返回和status中的lastError，核心并非没有记录网络失败。新增固定文案status_message复用两个显示入口；安全退出保护优先，网络错误有核对项及原申请保留说明，后续成功清除错误，unknown exception/lastError不泄露正文。补固定输入/响应错误白名单，不改Controller、重发/取消策略或DPAPI数据。
- GPT-6 Sol medium负责状态回归、版本/安装说明与在线演练辅助，根代理实现与验收。完整Node64通过；Python149项143通过/6跳过；状态定向3项通过。Tk合成窗口实际错误/状态重读/成功确认/连接/断开通过，截图只含测试数据；根代理缩短提示换行，并将合成后续成功改为原申请状态推进，未模拟对已有申请再次配对。最初测试硬编码旧generic文案失败，改为脱敏/操作语义断言后通过。
- alpha.7 catalog与根版本同步、仓库历史生成并校验；旧版catalog保持不变。永久版本说明不包含“尚未构建/签名”等会过时的阶段结论；实际进度另记STATUS。草拟WINDOWS-ALPHA7-OTA-REVIEW：同源私有Windows feed、sequence3、完整签名和不可变包、原子metadata发布、撤回不能降ledger；无手机/服务器程序升级。实际包和CI结果后续追加。

## 2026-10-08 — 确认正常关闭后续审，真实 Windows alpha.6 提交完成

- 最终代码提交`9f6e93a18bc76b218e7dcdbb322743d342194d17`已推送草稿PR #2。Core [push37775664273](https://github.com/nzkres/codex-desktop-remote/actions/runs/37775664273)/[PR37775672832](https://github.com/nzkres/codex-desktop-remote/actions/runs/37775672832)两平台全部成功。日志独立核对Windows Node64、Python140通过/6跳过、relay73通过/4跳过；Linux Node62通过/2跳过、Python126通过/20跳过、relay77通过。六项Windows维护演练与既有GUI/helper、Linux cgroup演练成功；CI产物已上传，但未取代本次安装的原签名alpha.6包。最后仅追加验证文档，main未合并。
- 首次`resume-wait`现场核对发现原GUI已不存在，返回`old_process_owner_unverified`，没有改原超时journal或切换；用户随后明确确认按“×”正常关闭。只读核对原控制器已正常移除运行保护、应用/运行锁释放、完整身份/任务配置保持。保留原时点记录，不按旧PID重新打开句柄或强杀。
- 新增仅等待退出超时场景的`resume-clean-exit --normal-close-confirmed`：完整复算已签旧/新metadata账本和staging，secondary必须不存在；双锁下要求runtimeNeedsReview键完全缺失及CIM旧PID完全未被占用。fresh alias探针前后再次确认冷态/全部绑定，生成新30分钟审查，继续原AliasRecoveryDriver/MaintenanceTransaction。用户确认不替代树证明或旧退出码；工具不在发行包内，原签名alpha.6 ZIP未改。
- GPT-6 Sol medium补22项维护回归、操作说明及独立只读审查，根代理验收。`python -B -m unittest discover -s test -p 'test_*.py'`146项140通过/6跳过；本轮Node64通过；实际Windows合成DPAPI/Tk/Job/独立助手初次、resume-wait、resume-clean-exit各成功/失败共六例通过。release catalog与diff检查通过。0b3532f的Core push37773618452/PR37773623778两平台success；正常关闭增量新CI另记。
- 按持续有效的维护升级批准，执行`python -B -m updates.windows_maintenance resume-clean-exit --normal-close-confirmed --review <原审查> --expect-review e19672fa6647a6aec018f64d545ad2acfa65204fe7f2f0212fa823be663e5ff9 --baseline-metadata <已签alpha.5> --metadata <同一已签alpha.6> --package <原93986字节ZIP>`。输出normalCloseVerified/reviewRenewed，随后committed/success/alpha.6，退出码0；未改手机或服务器。
- 独立复验主/次journal完全一致、signed slot正确、实际唯一新GUI与可信门进程父子归属一致、应用锁持有、原版本保留；稳定身份、fixture、权限、task/receipts、install/trust/launcher逐项保持。最初核验把可信child_gate也算作GUI，断言失败；按准确argv入口和父子关系区分后唯一GUI通过，未更改进程或产品。
- 用户在新窗口重新连接后，本机认证GET health及update-status通过，mobile-sync200、sameThread=true，两条已经完成的回复均读取成功；模型输入0。忽略目录记录maintenance-committed-verification.json，令牌/原始日志/身份未入Git。用户随后明确回复“两条回复都能看到”，手机原任务同步验收通过；维护切换不等同于三端联网OTA完成；alpha.6 feed/Release未发布，PR #2与main保持边界。

## 2026-10-08 — 等待超时的受限继续交接

- 用户回复“准备好了”；此前维护升级批准持续有效，不再请求重复授权。只读核对原GUI创建身份/路径/用户会话仍匹配，稳定身份/任务/配置保持，current仍alpha.5，secondary事务不存在；原review已过期，故没有直接重跑apply。
- 新增repo-only resume-wait：历史review结构/哈希独立验证；用原签名alpha.5元数据重建原ledger并匹配历史digest，再由同一签名alpha.6元数据计算当前ledger，完整相等才接受；其他全部绑定、staged/candidate清单、waiting-only主屏障和超时原因严格相等。两份元数据均要求当前有效，原alpha.5仍至10月15日。新30分钟审查来自再次固定原进程与实时alias探针；不重写原审查/主屏障、不退ledger或重打包。
- 等待原句柄正常退出600秒，之后仍由原AliasRecoveryDriver/MaintenanceTransaction做独占锁、完整冷态绑定、签名/CAS、隔离健康和保全。维护事务继续使用原fence归属hash，新审查单独留审计证据。候选事务已开始、其他失败、原GUI已消失都拒绝；这不是任意冷恢复。
- 根代理验收：完整Node64通过；Python144项138通过/6跳过；维护定向20通过。实际Windows合成DPAPI/Tk/Job/独立助手的初次提交、坏候选停点、过期审查后继续提交、继续时坏候选停点四例全部通过。真实resume_evidence只读核对原签名首装账本、当前账本与暂存候选一致，身份/任务未改。原签名alpha.6 ZIP不重打，新CI与实际继续结果后续分别记录。

## 2026-10-08 — 已批准维护的实际等待超时

- 用户明确回复“批准维护升级”，范围为当前配对Windows测试连接器alpha.5→签名alpha.6，保留原身份/权限/任务，不更新手机或服务器。
- 执行已审查工具apply后，独立助手完成原GUI固定/包复验/暂存/ledger与primary屏障，输出guiPinned=true、waitingNormalExit=true；随后请用户正常关闭alpha.5窗口、保留Desktop。120秒内原句柄未退出，助手以old_process_exit_unconfirmed退出1，未启动候选事务。
- 只读复核currentVersion=alpha.5，stableIdentityAndDataPreserved=true，candidateTransactionStarted=false，oldGuiStillVerified=true，phase=needs_review。未结束进程、回写旧数据库/ledger、删保护或重配对。原窗口应保持，下一次在用户准备好后先审查继续交接路径；原apply会因已持久屏障拒绝，不能盲重试。用户切换授权持续有效，不再重复询问。
- 文档推送与PR描述更新首轮遇TLS握手/GraphQL EOF，原分支推送重试成功，未改远端历史；该网络故障与维护窗口超时分开记录。

## 2026-10-08 — alpha.5 别名故障维护交接与冷启动屏障

- 在独立分支codex/controlled-alpha5保留现用状态，新增仓库专用review/apply工具；固定alpha.5→alpha.6/test、签名大小哈希与manifest、原GUI原始句柄/用户/会话/路径、全身份/配置/回执绑定。review只读与输出脱敏哈希；apply等待用户正常退出并持独占锁，不按PID终止或重配对。
- GPT-6 Sol medium分别补低复杂度回归、维护文档、独立只读审查；主代理实现/验收。审查发现直接脚本导入路径、Path.open不支持opener以及GUI退出到事务预检之间缺少冷启动屏障，均已修复。primary journal在等待退出前原子记录needs_review并保留旧记录；secondary engine journal先落盘再CAS镜像，断电只会保守拒绝。镜像失败、初始预检异常/过期、候选树清理失败和激活结果不明都不得清门禁重试。
- 本机Python3.12 `-B -m unittest discover -s test -p 'test_*.py'`141项135通过/6跳过；固定Node20.19 `--test`64通过。Node首轮沙箱EPERM未能启动子进程，主机同命令通过。`python -B scripts/windows_maintenance_smoke.py`采用临时签名包与合成DPAPI身份/探针，真实Tk原GUI正常退出后独立helper完成提交；坏候选清理全部自有后代后回指旧槽但needs_review，不启动旧版。首次夹具错误地期待unfinished而实际interrupted错误码，修正断言后两例通过。
- 真实只读预检首次拒绝缺省unconfirmedTurnSubmissions。固定Desktop26.930.7945.0/app.asar（原pin不变）源码显示其长度清零后delete该字段，添加时??=[]，空闲判断缺省false；因此仅接受字段不存在或明确空数组，null/自有undefined/其他格式仍拒绝。19项定向回归通过，原任务真实maintenance预检idle/permissionsVerified/receiptsCertain/reviewerAliasOnly全部true。没有发送模型输入或更改权限摘要。
- 增docs/WINDOWS-ALPHA5-MAINTENANCE.md、安装链接与alpha.6版本体验记录；工具repo-only不打入运行包。当前alpha.5窗口和服务保持，包签发、CI复验、实际维护review/用户切换确认继续单列，不以合成演练宣称真实OTA通过。
- 首提交ece3eb6的push37762969281/PR37762974145：Linux成功，Windows唯一错误是合成GUI命令行单测使用未规范化的TEMP短路径；生产review已要求physical_home，夹具未遵守。将单测与新维护烟测的临时根resolve后重验，不改产品路径归属比较；未采用失败运行的包进行签发。
- 修正56f488e的push37763446214/PR37763453724两平台全部成功；Windows实际维护两例committed/needs_review，原GUI/helper与Linux cgroup演练均通过。下载Linux Windows包逐43文件对比Git56f488e原字节，ZIP93986字节/SHA256 4df87881c5d0f704a4b2ac0a1a2d12e022f5b11062a8740d7ad43f96ca7811cf。
- 原项目DPAPI签名库仅内存解封、签发本地Windows候选sequence2；公钥按当前安装trust复验，未发Release或部署feed，未重新生成密钥。元数据/报告在忽略目录artifacts/alpha6-maintenance-20261008。实际`python -B -m updates.windows_maintenance review ...`成功（只读现用身份/进程/任务）：reviewHash e19672fa6647a6aec018f64d545ad2acfa65204fe7f2f0212fa823be663e5ff9 / expiresAt1791457239。实际apply仍待用户确认；过期须重新review。没有重发测试回复、停止连接、迁移身份或切换三端版本。

## 2026-10-08 — 同任务回复已完成，手机同步被审批别名误判阻断

- 用户回报电脑已回复但手机未同步，截图明确 permissions_changed。应用查询与原生历史只读核对：PHONE_ALPHA5_OK、DESKTOP_ALPHA5_CONTINUE_OK 均在原 UUID 完成；活动 alpha.5 health200，但 update-status/mobile-sync409，仍保持新配对连接，没有重发消息或重配对。
- 仅把当前 currentPermissions.approvalsReviewer 的 auto_review 换成就绪回合的 guardian_subagent，原始完整摘要就精确匹配已保存值。OpenAI protocol/config_types.rs 的 serde alias 及本机固定 app.asar 的 SM 判断均把两者视为同一模式；没有真实权限扩张。未改 Desktop 内部文件、配置、摘要或任何保护。
- src/service.js 新增 permissionsMatch，保留 permissionDigest 原算法；只兼容顶层两个精确值，读/更新预检/发/停及 scripts/probe.mjs 共用。Windows 迭代 alpha.6，Android/relay 不变。GPT-6 Sol medium 补回归和版本记录，主代理检查后要求补 live snapshot/config 逐次不变、嵌套字段变化拒绝，最终验收。
- 命令：固定 Node20.19 `--test`45/45；Python3.12 `-B -m unittest discover -s test -p 'test_*.py' -q`124项中118通过/6跳过。Node初次沙箱子进程遇用户目录EPERM，正常用户上下文重跑同一套通过；不是产品错误，也未放宽产品检查。
- 忽略目录中的只读诊断使用新源码 Follower/Service/手机协议，限制只能加载历史；原摘要仍不同、别名比较通过、两条精确回复都在同一 mobile-sync 数据中、更新状态 idle/permissionsVerified/receiptsCertain通过。current/install/trust/ledger/fixture/加密身份/回执逐文件摘要前后相同，模型输入0。报告 reviewer-alias-source-verification.json；诊断脚本首轮语法错误未执行任何读取动作，修正后通过。
- 当前运行的仍是原签名 alpha.5；新版代码验证不代表手机已恢复。旧版更新预检和回退健康也会因同一缺陷拒绝，维护切换须先有完整审查与安全停点，不能强改权限摘要或绕过预检。CI、签名/安装与实际手机重试结果后续单独记录。
- 修复提交67302e7已推送到原独立分支和草稿PR #2，未合并main。Core push37756849170的Windows/Linux全部通过并产出包；但同提交PR37756853884的Windows GUI演练在两种事务通过后清理临时目录失败，WinError32指向 `.update-*`。检查发现终态journal可早于独立helper收尾写入完成，测试不应立即终止其Job；本次失败保留，不仅采用另一组绿色结果。
- 下载push两平台源码包并用updates.security解包/清单/SHA核验，逐文件对比Git67302e7原字节。Linux产Windows alpha.6 ZIP为92767字节、SHA256 d0d32f2b6f062f0252411dcc34c763793d2feeb9199ab2fd62f57d9372e11f3d，43文件完全相同；Windows checkout仅两项TXT换行不同。均未签名、安装或上传feed；CI重打的relay alpha.5 ZIP仅作验证，不能替换已发同版本旧包。
- GPT-6 Sol medium仅修scripts/windows_ota_smoke.py，主代理验收：旧合成GUI交接后提供自有helper身份；父测试先固定该进程原始句柄并回ACK，旧GUI才正常退出；终态journal后再wait同一helper退出，才清理原合成Job与临时目录。没有扩大删除重试或吞掉异常，没有改产品进程/版本。实际 `python -B scripts/windows_ota_smoke.py` 两例各一次：committed0.0.2、坏候选rolled_back0.0.1，退出码0；新CI仍待复验。
- 最终ea45239的Core push37758224837、PR37758232762两平台均success。读取日志确认Node W45/L43+skip2；Python W118+skip6/L104+skip20；relay W73+skip4/L77；Windows真实合成GUI/helper提交0.0.2、坏候选回退0.0.1，Linux cgroup提交/回退0.0.2、4棵树通过。`gh run download 37758224837 --name desktop-remote-source-Linux`后复验Windows alpha.6 ZIP92767字节、SHA256 a7c92ee9e4d12826c7e27a2e93b1fd010f263ece90ac15bf784c25c7494ca0f6；43源文件与先前逐字节Git核验完全相同，完整清单验证通过。未签名、上线或安装；PR #2仍draft/open，main未变。
- 最小下一步是实现并合成验收独立的维护交接路径，再提交具体切换审查：固定原GUI句柄等用户正常退出，保留应用/运行锁、完整身份/配置/回执CAS、已签候选及事务检查；只用明确的alias等价证据处理旧探针缺陷。候选失败不能宣称旧alpha.5健康回退，应保留可诊断现场。此路径目前未实现，不能拿本轮普通合成OTA演练替代。新旧真实身份和当前服务本轮均未切换。

## 2026-10-08 — 错误端口导致配对申请未送达，准备全新重试安装

- 手机截图为配对码已生成、等待电脑申请；用户确认电脑显示正在提交配对。仅对本轮新安装DPAPI状态做白名单诊断：claim_pending/connection_unavailable，服务器公开端口实际是944，正确应为9443；未输出码或token。
- 现有GUI忽略lastError，持久化pending申请又无法从界面撤销；修改文本框无法重新begin。保留失败安装与全部身份状态，未删除任何保护、改端点重放或替换凭据。
- 在物理父目录下全新安装CodexDesktopRemoteTest9443，原alpha.5签名包/bootstrap核验与专用任务probe通过，权限摘要和原测试一致，未复制身份/回执。等用户正常关闭旧窗口、手机取消配对后再启动新窗口；此时尚未启动、配对或取得手机任务结果。具体恢复停点见设备基线记录。
- 用户随后确认旧窗口关闭且手机取消。取得旧application/runtime/operation锁并核对原pending错误地址与无绑定状态后，使用物理新home的Start.pyw打开原签名窗口；实际窗口标题alpha.5、句柄非0、stderr0，原状态保持。已给出完整9443地址和重新生成配对码的操作步骤，等待新配对结果。
- 用户确认手机已批准、电脑已点击连接后，独立核验新身份connected/lastError=null/正确9443，LoopbackHealthProbe和现有幂等tunnel_probe均true；活动适配器GET update-status200、alpha.5/idle/permissionsVerified/receiptsCertain通过，诊断前后完整核心身份与adapterBinding一致，模型输入0。运行期runtimeNeedsReview按原保护保留；没有停止连接。已请求用户执行PHONE_ALPHA5_OK和DESKTOP_ALPHA5_CONTINUE_OK同任务双向验收，当前结果待回复。

## 2026-10-08 — 手机登录后首页404诊断

- 用户确认alpha.5登录后首页顶部HTTP404，地址含9443。只读源码核对默认TaskTarget空ID走旧路由，MainActivity后台refresh未等待电脑选择便请求`/api/projects`；新隔离Caddy不提供该无目标路由。
- 主电脑正常TLS GET复验auth/status200、三个旧任务接口404、匿名control-api/terminals401。没有读取凭据或手机私密日志，没有更改服务器；指导用户先完成配对并显式选择电脑。已把未选择目标时的引导和停止旧接口轮询记为下一版候选，详见设备基线记录；本轮未修改或重发产品包。

## 2026-10-08 — 主电脑 alpha.5 全新安装、任务恢复与图形启动

- 在预先确认不存在的 `%LOCALAPPDATA%\CodexDesktopRemoteTest` 使用原始签名 alpha.5/bootstrap 完成全新安装与账本校验。读取旧专用测试配置仅限任务 UUID/项目路径/Desktop 路径等白名单；重新创建新配置与独立端口，未复制旧身份或旧配置文件。
- Python3.12.14/Tk8.6/cryptography50.0.1、Node20.19.0、FRP0.71.0 和 Desktop26.930.7945.0 两项固定文件指纹通过。只读 CIM 进一步确认当前主进程是该包内 ChatGPT.exe，不读取进程命令行。
- 安装槽 `scripts/configure.py --home ...` 保留新配置并报 `desktop_fixture_unverified_config_retained`。直接运行同槽 `scripts/probe.mjs` 得到 `owner_unavailable`，模型输入0；用户表示已打开原测试任务后仍失败。结构化只读 IPC 诊断握手成功、owner discovery 约17ms远端no-handler，应用查询准确UUID为notLoaded；没有改协议、另建执行器或删除现场。
- 请用户在原任务手动发送 `OTA_ALPHA5_READY` 的无工具测试提示，等执行端恢复后只重跑 probe；不能重复配置、代发或重放未知操作。连接器尚未启动、未配对，手机受控APK首装结果待用户回复。
- 新隔离测试账号仅经进程内存转入本机CurrentUser DPAPI及CurrentUser/SYSTEM私有operator目录；无明文文件、秘密命令行或聊天输出。独立本机账号显示窗口已启动，按需45秒显示口令；是维护辅助工具，不是签名产品功能。旧账号、签名库、服务和真实身份未迁移。
- GPT-6 Sol medium核对历史说明，主代理保留失败证据并新增 `docs/OTA-DEVICE-BASELINE-20261008.md`。本轮仅安装/诊断/文档，无产品修改、换包或新版本，未把旧CI成功当作实机联调通过。
- 后续用户确认收到OTA_ALPHA5_READY，应用读取相同UUID最新回合completed且内容吻合、线程idle；安装槽probe passed/httpHistoryVerified=true/modelTurnsStarted=0，update_probe的idle/permissionsVerified/receiptsCertain均true。
- 首次启动真实图形入口后用户看到错误；启动预检定位`managed_data_directory_required`。原因是AppX重定向：configure写物理home，启动器传逻辑home，词法包含检查拒绝。同一安装六文件samefile、拒链接、全槽签名清单及unpaired状态核对后，改为从物理home调用原Start.pyw，未改槽或配置。实际自有子进程显示alpha.5窗口标题且窗口句柄非0，stderr0。GPT-6 Sol medium复核根因与现有smoke覆盖缺口，后续alpha.6候选需统一规范home并补回归；当前不重签换包。
- 用户报告手机alpha.5已安装并停在登录页；已给出新服务器登录与新身份配对步骤，等待用户完成。未取得手机任务或OTA成功证据。维护命令与两次失败现场、恢复边界见设备基线记录。

## 2026-10-08 — 云端放行后公网私有源与FRP验收通过

- 用户亲自放行9443/17443并回复完成。主电脑以正常系统TLS验证访问新origin，六项metadata/包匿名401；新测试账号登录后使用产品updates.security.fetch/download/verify实际下载三端alpha.5包，RSA/大小/SHA全部与发布一致，跨origin在生成鉴权头前拒绝。退出登录后旧token401，17443TCP连通。没有打印口令/token或将其放入参数。
- 新合成设备外部claim/confirm/connection成功，Windows元数据200，Android/relay403。本机固定FRPC0.71.0（SHA c9c59f9bb99561405b81d68f688384aec49369df10489044315292b0eaac957a）在独立Windows Job中经公网新FRPS/专用CA/TLS/设备插件连接本机无任务HTTP夹具；服务器loopback实际读到CDR_EXTERNAL_FRP_OK。原始Popen句柄和Job整树清空确认后清理；暂停/撤销403，设备已撤销、会话已退出，无真实Desktop、任务或身份。三次夹具保留撤销墓碑，不重置分配账本。
- 新服务器以cdrtest UID实际执行现有产品login→受管socket check→logout，经固定公网origin取得已签元数据；返回no_update_available、relayHealthy=true、loggedOut=true。当前就是alpha.5，不强制同版本安装。秘密仅root读取新测试账号后在降权进程内使用，没有复制旧凭据。
- b107726推送到codex/controlled-alpha5，创建草稿PR #2。Core push37744798752及PR37744901531 Windows/Linux均success；已读取测试汇总和真实生命周期步骤，统计见部署记录。Android源码/已发布alpha.5包未变，不重签或触发新发行。
- GPT-6 Sol medium再次同步安装/OTA/交接文档，主代理验收并记录外部真实证据。仍未手机安装、真实Windows接入、alpha.5→alpha.6或实际新证书轮换；下一步须按N基线顺序由用户完成安装、登录、配对和专用任务确认。

## 2026-10-08 — 获批隔离部署，服务器内验收完成

- 用户明确选择“批准隔离部署”，范围为106.53.126.69独立用户/目录/数据库、alpha.5 relay/FRPS/HTTPS/私有OTA、TCP9443/17443、独立续证hook与鉴权回退；保留现用服务和身份，不含手机安装/真实身份迁移。按最新授权执行，没有重复要求部署确认。
- 上传并逐文件核对Git1a4b4c0源码、已发布alpha.5包/bootstrap、官方FRP0.71.0包；创建cdrtest和全新受管home，调用现有bootstrap.install/configure初始化专属账号和数据库。填写独立transport六字段及准确config引用；FRPS CA/token/服务私钥仅远端保护保存，测试管理员口令不输出，RSA/APK发布私钥仍在本机。
- 两单元通过`systemd-analyze verify`；固定真实FRPS通过`verify -c`。实际Caddy发现固定镜像二进制file capability与cap_drop ALL冲突，保留唯一NET_BIND_SERVICE后执行成功；IP无SNI客户端握手失败由`default_sni 106.53.126.69`修复。保留正常TLS验证，只更新本轮新容器；无状态代理unless-stopped，relay仍Restart=no。部署模板同步修正，不换alpha.5包。
- 服务器内loopback TCP+正常IP证书HTTPS真实检查：六项匿名metadata/包401；新账号三组件完整下载/RSA/SHA/大小匹配Release，退出后拒绝；合成设备仅Windows可下，Android/relay403，暂停/撤销拒绝。实际FRPC0.71→独立FRPS0.71，经专用TLS/设备插件/代理至无任务HTTP夹具成功。不明活动状态阻止受管stop；测试设备最终撤销、会话退出，无活动设备或待配对。
- 目标Python3.12.3/systemd255/cgroupv2下，临时委派单元运行`python3 -B scripts/relay_ota_smoke.py`，committed0.0.2、坏候选rolledBack0.0.2、ownedTrees4，临时单元回收。新真实alpha.5以cdr UID的受管CLI stop确认整组退出后systemctl start，auth/registry逻辑摘要保持，phase ready/healthy true。未使用systemctl restart绕过维护门禁。
- 一次诊断脚本遗漏-B导致安装槽出现两个root-owned pyc，完整性门禁报package_file_set_changed。核对29个签名源文件全部未变、额外文件仅两个确切诊断字节码、归属/格式/路径/无链接符合后仅移除这两文件和空目录；保留脱敏修复报告，没有删保护标志或数据库。后续所有导入安装代码的诊断统一-B/dont_write_bytecode，完整复验通过；不隐藏首次失败。
- 安装独立Certbot deploy hook，实际匹配lineage调用成功且容器ID不变；现有codex-remote-certbot-renew.timer active，固定renew命令未禁用hooks。没有强制续证或调用旧hooks；证书实际轮换待观察，当前证书到2026-10-13 22:07:54 UTC。旧Caddy文件SHA前后相同，旧443/7443/7000监听保留。
- 系统UFW仅增加IPv4 TCP9443/17443，内部端口保持loopback。Windows外部请求两新端口超时；18秒只抓SYN的诊断同期443连接成功且1个SYN到达，新端口均0，未读取请求载荷。云控制台在登录页，无法核对云端规则，已请用户登录或补两条规则。公网OTA不可宣称通过。
- 本轮本机回归：固定Node20.19 `--test`33通过；Python3.12 `-B -m unittest discover -s test -p 'test_*.py' -q`124项118通过/6跳过；relay77项73通过/4跳过。GPT-6 Sol medium整理安装/OTA/交接文档，主代理负责部署与验收。详细结果和剩余步骤见docs/OTA-ISOLATED-DEPLOYMENT-20261008.md；alpha.6/手机安装/真实Windows接入尚未执行。

## 2026-10-08 — alpha.5 受控签名构建与首装复验

- 完成本轮已批准范围：实际配置六项Secrets，构件输入`1a4b4c0`先经分支Core37736389583/Android37736389589成功；核对远端main仍为预期`1e405ad`后以非强制快进推进main，再执行`gh workflow run android.yml --ref main -f signed_ota=true`。main Core37736836192和普通Android37736836186也成功。
- main受控Android run37736837251成功：94 JVM测试、lint0errors/45warnings，apksigner在minSdk26校验单签名者及预设证书SHA，APK trust严格比对，临时签名输入清理成功。用`gh run download ... --name desktop-remote-android-ota-test`取得成品；下载SHA/大小与CI输出完全相符。APK不含v1 META-INF证书块，未用缺少v1推定签名失败；签名证据来自CI的真实apksigner和同一文件SHA。
- 官方Google Maven的AAPT2 8.11.1-12782657独立工具核对实际AndroidManifest：包名dev.codex.desktopremote.lite、code5、alpha.5、minSdk26、targetSdk36、debuggable。ZIP CRC、包内releases与Git原始字节、trust与外部受控文件逐字节相等。沙箱无权读私有库的公开trust，改在正常用户上下文复核，没有放宽ACL或DPAPI。
- Windows43/relay29文件采用Linux CI来源包，完整manifest/单文件哈希/Git原始字节一致。早期本机打包仅作dry run，不发布混合CRLF构件，也未批量改动工作树换行符。最终程序包指纹见STATUS。
- 以`updates.publish.sign(... signing_home=..., sequence=1)`在内存解封RSA，为三端实际包签名；固定已批准origin，2026-10-15 06:33:03 UTC过期。公开bootstrap包包含三个组件各自trust/latest及README；完整扫描待发包和解压成员，未发现本次真实APK keystore字节、base64或口令。私钥/秘密不进入日志或附件。
- 调用`updates.bootstrap.install`，分别在Windows Python3.12.14和WSL Python3.14.4的全新临时目录核对实际alpha.5版本、清单、trust与sequence1；data为空，第二次安装拒绝且全目录摘要不变。未配置或启动连接/中继，没有真实身份、账号、任务或公网访问。
- GPT-6 Sol medium整理五份安装/版本索引文档，主代理复核包名、bootstrap子目录和命令；同步状态、交接、授权和过期说明。普通CI调试包不冒称受控包，测试签名不冒称生产发布。
- 创建私有alpha.5草稿，独立核对7个附件的uploaded状态、大小和GitHub服务端digest全部与本地一致后，执行`gh release edit v0.1.0-alpha.5 --draft=false --prerelease --latest=false`。已发布于2026-10-08 06:41:33 UTC，目标1a4b4c0，旧alpha.4不替换。部署、手机安装、真实三端OTA与独立密钥恢复尚未执行。
- 发布后核对tag实际指向1a4b4c0。最终仅15份Markdown记录更新：版本校验、121个本地链接目标和diff检查通过；文档提交标记`[skip ci]`，不重跑未变更产品，也不改变已发布包。后续从部署审查开始，保留“暂不部署”的边界。

## 2026-10-08 — 批准受控首装，合并PR #1

- 用户明确选择“批准签名与合并，暂不部署”：确认测试origin `https://106.53.126.69:9443`、本项目专用RSA/APK测试签名、本机加密保存、六项Android Secrets和alpha.5首装构件。公网部署、停现用服务、真实身份迁移与手机安装仍不在范围；不再重复询问已批准的签名/合并。
- `755fe14`最终Core push37734382112/PR37734386777与Android push37734382114/PR37734386832全成功。Core日志核对119项主测试的Windows113通过/6跳过、Linux101通过/18跳过，并保留真实GUI/helper及cgroup升级/回退结果。Android为普通debug构建，受控签名步骤明确跳过。
- 执行`gh pr merge 1 --merge --match-head-commit 755fe145c76766e51cd1abf8bbb1a0012cb0e5a6`，PR已MERGED，main为`1e405ad596644f773aa0aa8207e43b079de4042b`。从该main建立独立`codex/controlled-alpha5`继续，不覆盖其他工作区。
- GPT-6 Sol medium更新三端alpha.5 catalog及体验/验证/限制说明、Android code5、根package版本和生成历史；旧alpha.4保留。主代理实现可复用`updates.android_publisher`，本机DPAPI封装专用加密PKCS12与口令、绑定RSA公钥和固定origin、六项Secrets通过stdin提交并记录无秘密journal。Sol补合成测试，主代理验收；真实执行结果随后记录。
- 新工具完整本机回归：Node33通过、Python124项中118通过/6跳过、relay77项中73通过/4跳过。然后才实际创建专用签名：全新本机受控父目录/库，RSA3072；APK RSA3072、自签长期测试证书、随机强口令PKCS12，外层CurrentUser DPAPI。无明文PEM/p12/口令落盘，无原身份读取。公开keyId与指纹记于STATUS。
- 获准执行`updates.android_publisher provision-github`，六项Secrets经stdin逐一提交，返回configured=true/secretCount=6，本机journal为configured。仅作用于固定私有仓库；RSA发行私钥没有上传。真实受控构建和构件核验尚待执行，不能把配置完成视为APK验签完成。

## 2026-10-08 — 受控首装签名工具准备

- 用户要求继续下一阶段。只读核对PR #1仍OPEN/head aa757cf、main b7db21e、仓库Secrets列表为空；上一轮明确的暂缓合并/真实签名边界尚未按具体执行清单解除。先补实际签名工具，不触碰服务或真实身份。
- 主代理实现`updates.signing`：新建仓库外专用目录、CurrentUser DPAPI RSA3072、仅加密私钥与公钥记录落盘；load核对keyId、公钥、DER摘要及文件类型/链接/大小，失败保留现场。`updates.publish sign --signing-home`仅内存解封，和原外部PEM二选一；没有自动落明文降级。工具不随三端运行包分发。
- GPT-6 Sol medium负责签名库测试及Android trust预检工具，主代理验收并接入CI。复核发现Python可解析PKCS#1而手机要求SPKI，补规范SPKI及拼接/尾随拒绝回归；Android构建前验证RSA3072和准确test/android源，构建后比对APK内trust原始字节。Gradle明确PKCS12，保留main-only和apksigner单签名者/指纹校验。
- 本机最终测试：固定Node20.19 `--test` 33/33；Python3.12 `-B -m unittest discover -s test -p 'test_*.py' -q` 共119项，113通过/6跳过；relay相同入口共77项，73通过/4跳过；版本校验和diff检查通过。DPAPI沙箱定向初测不具备正常用户保护上下文，获准在本机合成范围运行后签名/验签通过；未放宽DPAPI或改为机器作用域。
- 当前仅发行工具/构建门禁变化；三端版本保留alpha.4，不替换已发布包，不将普通PR调试构建当受控首装。已更新发行说明与准确执行范围；跨电脑密钥备份/恢复、真实密钥和六项Secrets仍未完成。CI待推送后单独记录。

## 2026-10-08 — 本地准备推送与三平台 CI 验收

- 用户继续推进；保持“暂不合并或配置签名”的最新边界。先核对远端PR #1为OPEN，分支head为3277a8a，本机工作区干净且仅领先be5a01c；执行`git push origin codex/ota-implementation`成功，无force、无main改动。
- `gh run list`及`gh run view --json`核对同一head：Core push37726150384、PR37726153483的Windows/Linux均success；Android37726153367 success。进一步读取日志中的测试汇总、真实升级/回退标志、APK校验结果；不是仅根据旧绿色状态判断。
- Core PR日志：Node Windows33通过、Linux31通过/2跳过；Python主套件107项中Windows102通过/5跳过、Linux94通过/13跳过；relay77项中Windows73通过/4跳过、Linux77通过；续证钩子Linux3通过、Windows3跳过。真实pythonw助手升级0.0.2/坏候选回退0.0.1；Linux中继升级0.0.2/坏0.0.3回退0.0.2、4棵树清理。没有放宽检查或忽略失败。
- Android普通PR构建通过单测/lint、主/测试APK编译及包内说明检查；APK为alpha.4/code4、11,771,608字节、bundledNotesMatchSource=true。未启用受控签名步骤，未安装模拟器/手机；本轮不将CI调试包替换原Release。
- `gh api .../actions/runs/.../artifacts`核对Windows/Linux源码ZIP与Android debug/报告构件均已上传、未过期，14天保留至2026-10-22 UTC。这里只验证CI和上传状态，没有把临时构件当长期更新服务或声称真实OTA成功。
- GPT-6 Sol medium只读复核安装/OTA说明，主代理核对结果并更新STATUS、CHANGELOG、README、HANDOFF与本地准备记录；修正本地测试统计为通过与跳过分列。保留上一节推进前记录和早期Windows偶发问题，没有编造新软件版本。
- 最后仅6份Markdown变化：`python -B scripts/check_versions.py`、62个本地链接目标和`git diff --check`通过。文档提交使用`[skip ci]`避免重跑未变更的产品；上列CI证据精确对应be5a01c，后续文档提交不冒称又执行过一次完整CI。
- 下一步仍是受控首装信任和连续签名、隔离HTTPS部署、三端真实N→N+1；这些尚未执行。当前签名保管/合并/部署选择未改变，不把“继续”扩张为实际切换授权。

## 2026-10-08 — 本地部署准备与发布路径修正

以下是`be5a01c`推送前的本地准备记录；后续推送与CI结果另立增量，不将当时“未推送”视为当前状态。

- 用户在“继续下一步”后明确选择“先完成本地准备，暂不合并或配置签名”；本轮不创建项目真实发行密钥、不配置Secrets、不合并或公网部署。只读核对PR3277a8a三平台检查全成功，仓库Secrets/Variables列表为空；没有读取秘密值。本轮没有新CI或新发布。
- GPT-6 Sol medium准备deploy/test下独立systemd/FRPS/Caddy/续证钩子草稿，主代理复核。主代理要求证书挂载只限指定lineage，续证以固定容器ID/项目/镜像和明确22019地址重载，保持旧2019隔离；无ExecStop假门禁，relay拒绝手动stop且Restart=no。另由Sol复核发布器精确路由与服务端一致。
- 修复本地发行工具可能签出私有服务器无法提供的URL：现在签名前要求准确渠道/组件/latest.json与catalog版本文件名/扩展，拒绝不写metadata；规范bootstrap/Windows smoke夹具URL，新增7类拒绝和HTTPS9443正常用例。工具不随应用运行包分发，因此三个软件版本仍为alpha.4，旧发布不可换包；alpha.5/alpha.6仍未签发。
- Node33通过（沙箱EPERM在加载测试前发生，获准本机复验通过）；Python3.12主套件107项通过、5Linux跳过；relay77项通过、4Linux跳过。真实Windows pythonw/helper在临时身份下committed0.0.2、坏探针rolled_back0.0.1。无真实Desktop探针/手机/身份切换。
- 新发现本机已有Ubuntu WSL：Python3.14.4/cryptography46.0.5/systemd259/cgroupv2。用一次性systemd单元、User=sheng/Delegate=yes/RefuseManualStop=yes/Restart=no/NoNewPrivileges=yes运行实际relay_ota_smoke：committed0.0.2、坏0.0.3回退0.0.2、4棵树退出，单元已回收。另一12秒sleep单元实际拒绝root的systemctl stop，随后自行退出。此附加本机证据不替代目标Python3.12/systemd255或公网验收。
- 记录实际结果于docs/OTA-LOCAL-PREPARATION.md，同步STATUS/验收/发布/交接。具体WSL参数、合成密钥边界、尚未执行项都有说明；没有把签名准备、下载或自动测试当成三端OTA已完成。
- 续证钩子Linux合成3/3通过、Windows3项显式skip；最终Compose解析、shell语法、TOML解析和版本校验通过。Core加入deploy/test触发与测试，审核文件强制LF；本轮只保留本地改动和提交，未推送/触发新CI。GitHub官方FRP发布工具下载初次无进度已取消，再以60秒上限重试，只有摘要匹配才允许运行。
- FRPS官方0.71.0工具经有界同源续传最终完整下载，13,932,092字节/SHA256 `84f27e39f11169f7adcef8e8b70c9329de17747b1f14dad9fb95eef5682ea716`与官方API一致；WSL以临时TLS/token替换配置路径，真实`frps verify -c`返回0。不启动监听，不创建项目真实签名；临时材料随验证退出清理，只有脱敏报告/工具缓存留在ignored artifacts。

## 2026-10-08 — 现有中继隔离部署预检

- 用户选择“现有中继服务器上新增隔离环境”。从旧本机部署审查定位ubuntu@106.53.126.69，而非把不相关SSH配置当作目标；当前实际SSH只读核对Python3.12.3/cryptography41.0.7/systemd255/cgroup2、磁盘、现用服务、Caddy挂载/摘要、FRPS0.71.0与端口池、UFW、候选路径和用户。未写服务器文件、未开端口、未停服/reload。
- 现用443/7443/7000和18081/18091/18092保留；候选9443/17443/22019/28181/28191/28192/28200–28220当时无监听，两个旧FRPS池也不重叠。候选路径和cdrtest用户不存在，云安全组与新服务cgroup委派尚未验证。现有TLS正常校验收到404，只作为握手证据，不能计作登录/OTA成功。
- 发现现用IP短期证书于2026-10-13 22:07:54 UTC到期；新独立Caddy需独立续证reload钩子，不能只复用证书路径而忽略续期。新增docs/OTA-DEPLOYMENT-REVIEW.md与deploy/test/Caddyfile.review，明确精确资源、私有路由、签名/首装顺序、撤回和待确认事项。候选auth路由覆盖X-Real-IP，避免客户端伪造限流来源。
- 将候选经stdin送入现有容器中独立的`caddy adapt --config - --adapter caddyfile --validate`进程，返回0；未保存为远端配置、未reload、未创建新监听。没有修改旧Android下载路径，仍禁止新OTA静态公开。GPT-6 Sol medium只读审查源码前提，主代理验收并核对现场。
- 验证：最终候选Caddy adapt/validate返回0（仅格式提示）；`python -B scripts/check_versions.py`、受影响Markdown本地链接和`git diff --check`通过。未重跑未改变的产品测试，也不把历史CI计作本次候选部署成功。
- 本轮仅文档/部署候选维护，无软件版本递增；受控alpha.5/alpha.6、发行密钥、CI签名Secrets、手机安装、真实三端OTA仍未完成。下一步确认候选origin与签名保管/合并安排，完成最终受控构件及可审查服务配置后，再按用户要求确认实际公网部署。

## 2026-10-08 — alpha.4 构建复验

- 最终 `c92ea98` Core push 37657708244、PR37657716116 的 Windows/Linux 全成功；Android PR37657716149也成功。发布前目录经过完整清单/Git输入/APK说明/哈希验证，私有 `v0.1.0-alpha.4` 六附件全部 uploaded，服务端 digest/size 与本地相同。发布首次 HEAD 预检遇 EOF，GET确认404未创建后重试成功，未覆盖旧Release。STATUS/安装起点/版本索引已写实际包信息，PR描述同步最终范围。

- 产品提交 `78ff65d` 推送前两次被 GitHub Internal Server Error 拒绝；只读确认远端未变后以 HTTP/1.1 推送成功。main 未修改。
- 本机最终主套件 105 项（5 Linux 专属跳过），Android CI [37655844718](https://github.com/nzkres/codex-desktop-remote/actions/runs/37655844718) 94 JVM 测试、lint 0 errors / 45 warnings 与 APK 构建通过。下载后 alpha.4/code4、说明、ZIP 完整性及空 trust 均核对。
- 同 head `78ff65d` Core push 37655845113 的 Windows smoke 等待首条 GUI 响应超时，PR [37655857428](https://github.com/nzkres/codex-desktop-remote/actions/runs/37655857428) 全部通过。仅为 smoke 加固定阶段诊断 `a206d1f`，不增超时或跳过检查；本机三次连续完整演练成功，Core push [37656996876](https://github.com/nzkres/codex-desktop-remote/actions/runs/37656996876) Windows/Linux 全通过。首轮偶发 GUI 超时/本机回退原因仍未定位，保留为待观察问题，不能据此宣布真实设备 OTA 验收。
- `a206d1f` PR 37657003946 在新 private-feed 测试发现另一条时序：收到 body 早于 handler 退出维护门禁。夹具改为观察实际 handler 返回 Event，产品门禁保持不变；本机私有鉴权定向 5/5 通过，最终 CI 继续验证。
- 三端包下载后逐文件校验：Windows43文件、relay29文件符合完整 manifest 与 Git 输入（Windows仅两份 text=auto TXT为CI checkout CRLF）；APK包内说明与Git78ff65d原始字节一致。尚未将包发布为已签 feed、部署公网、安装手机或迁移真实身份。

## 2026-10-08 — alpha.4 私有下载鉴权

- 用户确定“保持私有，先补下载鉴权”；主代理拆出服务器、Android 和共享 HTTPS 鉴权给 GPT-6 Sol medium，逐项复核后整合。复用现有账号/DPAPI 配对，不新增登录协议；同 origin 固定私有路径、分组件授权、匿名/过期/暂停/撤销拒绝；保持发布签名和本地事务验证。
- 主代理修正 Android 鉴权类型与服务器常量不一致、放宽路径范围两处整合问题；新增 Linux 交互 login/logout（密码不落盘、限时会话0600）、Windows 缺身份时无创建副作用、登出同内容换 inode 的 CAS。补发布白名单、实际 CLI/GUI/宿主入口与 smoke 的合成凭据加载。
- 本机命令：固定 Node 20.19.0 `--test` 33/33；Python 3.12 `-B -m unittest discover -s test -p 'test_*.py'` 104 项通过（5 Linux 跳过），relay 同命令 77 项（4 Linux 跳过）；新增回退脱敏诊断后 `-m unittest test.test_update_engine test.test_update_credentials -v` 21 项通过（5 Linux 跳过）。Node 沙箱先被路径 EPERM 阻断，获准在本机合成范围复验通过。
- `python -B scripts/windows_ota_smoke.py` 首轮意外 rolled_back，原日志缺失败首因；未操作真实服务，自动回退成功。随后两轮实际 pythonw/helper 均 committed0.0.2 与预期坏探针 rolled_back0.0.1；增加事务 cause 的受限错误码及脱敏测试，保留首轮未定位事实，CI 继续验证。
- 新增 [OTA-ACCEPTANCE](docs/OTA-ACCEPTANCE.md)：先服务器/固定签名首装，再 Windows→Android→relay 一次一端真实升级，最后故障演练和 BUG 批次。alpha.4 开发预览与将来受控 N/N+1 分开，旧包不换签/换包；三个版本历史、安装和跨电脑交接同步更新。本版 CI/构件下载复验待补；未部署、未合并 main、未迁真实身份。

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

### alpha.3 标签后的测试同步修正

- alpha.3 六个归档文件均已上传，服务端大小/SHA 与验收文件逐一匹配。PR #1 已建立；自动审批拒绝直接快进 main，原因是未明确授权绕过 PR 修改默认分支，因此保持独立分支并向用户请求 PR 合并确认，未操作现用服务。
- PR 重复 CI 37648259999 的 Windows relay 门禁测试出现时序失败：客户端读完 HTTP body，不代表服务线程已执行 admit 的 finally。产品正确拒绝仍 active 的维护请求；测试现通过 handler 完成 Event 等待真正释放后再 prepare，没有改产品门禁、增加盲重试或改写已发布构件。产品包仍对应已成功的 169b449 CI；修正后的 PR 检查继续核验。
