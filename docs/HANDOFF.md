## 2026-10-15 — 最终alpha.15与Windows alpha.18已发布

手机原更新源检查更新至alpha.15，五种中心数字额度皮肤、本机保存，Plus实际双窗口/Pro周单环，缺套餐/快照明确未同步。输入框额度与实时背景预览已移除。配套Windows alpha.18已发布、未自动安装；等任务结束按原更新流程，不重配对或清回执。两端实际HTTPS完整包核验及原签名通过，服务未停。手机安装、UI测试执行和真机视觉待验收；见[最终发布记录](ANDROID-ALPHA15-PUBLICATION.md)。

## 2026-10-15 — alpha.13 已发布，待手机GitHub与额度验收

原私有源Android alpha.13/序号7已发布、鉴权完整下载核验。见[发布证据](ANDROID-ALPHA13-PUBLICATION.md)。手机正常覆盖更新，不卸载或清数据。GitHub私有库需在手机设置指定仓库Contents只读令牌；英文学习PROGRESS阶段表有部分矛盾，需在源文档核对，不能替用户确认已完成。额度使用每台电脑的账号共享快照，离线/旧样本不假装实时。无需重复连接器恢复，不停止服务或重发旧消息。

## 2026-10-15 — alpha.12 已发布，下一步仅手机更新验收

按用户批准预览开发的多电脑对话、任务仓库、背景与问题保护已发布原私有更新源，详细证据见[alpha.12](ANDROID-ALPHA12-PUBLICATION.md)。当前没有需要重复运行的连接器恢复动作；不要停止现用服务、清身份/回执、重新配对或重复发送未知操作。等原任务空闲后手机正常检查更新，覆盖安装并验收同一对话与草稿。


# OTA 交接

## 2026-10-13 — alpha.11 当前开发对话手机读取通过

用户在 alpha.11 点击连接后，明确确认手机能看到“开发 Windows 连接器客户端”及最近消息。当前对话的手机实际读取验收通过；从手机发送并在同一 Desktop 对话接续尚待验收，不能用读取成功替代写入成功。下一步等本轮回复结束，由用户在手机本对话发送一次 PHONE_CURRENT_ALPHA11_OK 验收指令；结果不明不重复发送。没有重新配对或改变授权范围。

## 2026-10-13 — alpha.11 已实际安装，当前开发对话手机验收待完成

本段取代此前等待窗口退出/任务空闲的停点。用户正常断开并关闭 alpha.10 后，原助手观察同一原始句柄退出成功，等两条任务空闲后完成事务，返回 committed/success/version=0.1.0-alpha.11。独立审计确认 sequence7、签名和包有效、旧槽可验证、双事务记录一致、归档精确；原身份、任务数据、原测试任务配置与保全文件均保持，只新增明确批准的“开发 Windows 连接器客户端”。新版本激活前认证通道与两任务空闲检查通过，安装后两任务权限和回执再次核对通过。当前开发对话处于本轮执行中，不将此时忙碌误报为空闲。

已核对实际 GUI 标题为“Desktop 手机联调 · 0.1.0-alpha.11”，应用锁由新实例持有。脱敏本机证据 artifacts/alpha11/installed-alpha11-audit.json，真实配置/维护脚本仍仅在忽略目录。此次为本地签名维护安装，不是在线 OTA；Android、relay、公网 feed 未更新。CI 上传配额问题仍未解决，不宣称有本次 CI 下载包。

下一步用户点击连接，在手机刷新原电脑任务列表并打开“开发 Windows 连接器客户端”，先确认最近消息可见，再等本轮结束测试同一对话发送。手机验收尚未完成，不重复运行 apply、不重新配对、不重发结果未知的消息。

## 2026-10-13 — alpha.11 已获准，助手已固定原窗口并等待正常退出

用户明确批准升级并新增当前对话。助手及review哈希复核一致、审查仍有效，实际apply已启动并回报guiPinned/waitingNormalExit/installationModified=false。已通知用户在alpha.10正常断开、关闭；助手随后等待两个原任务均空闲，再自行进入已审查事务。此时尚未确认安装切换，不能重复运行apply，也不能清保护、强停进程或重发消息。出现alpha.11后再做独立安装与手机双任务验收。

## 2026-10-13 — alpha.11 签名及维护审查就绪，等待切换确认

alpha.11候选已按固定Git输入204426b打包并使用既有DPAPI签名，45文件匹配Git blob，139045字节、sequence7，现用信任验签通过。切换助手16项合成测试和真实隔离GUI提交/坏候选闭锁通过；完整旧状态、两任务权限与签名的只读审查完成，未改安装。当前开发对话正在执行，实际切换需等待本轮结束、两任务均空闲；不绕过thread_busy。

已请求用户批准从alpha.10维护升级并新增当前对话。现用原任务和身份保持，助手尚未执行apply；手机/中继/feed不改。候选失败将确认自有树退出、恢复旧指针并停待复核，原配置与旧槽归档，不把旧版忽略第二任务当成功回退。完整固定哈希、命令入口、审查时限及恢复边界见 [alpha.11切换记录](WINDOWS-ALPHA11-MULTITASK.md)。

CI38376943673两平台测试/演练/打包通过，上传因Artifact存储配额失败，整体失败；本次无CI下载物，已有本地签名候选。不要把候选完成写成现用升级或手机验收成功。

## 2026-10-13 — alpha.11 多任务接续候选，当前开发对话尚未接入手机

用户明确要求将当前“开发 Windows 连接器客户端”对话也显示到手机，并确认需要从手机发送消息接续。原测试任务保留，不自动开放其他对话。独立分支 codex/phone-current-conversation 基于已验收 alpha.10 开发。

alpha.11 增加 additionalTasks 显式白名单：每条任务单独绑定原生ID、目录和完整权限摘要，独立 Follower/Service；复用当前手机任务列表、中继、DPAPI配对身份和现有协议，无复制任务或新建执行器。任务的项目映射、读取、停止、回执与同步游标隔离；全局发送串行。断开先禁止全部任务新操作；OTA和导出的诊断均核对全部任务，任一忙碌/权限变化/未知回执拒绝升级。未打开的已授权任务以UNKNOWN留在列表，进入时报告真实错误，不让其他任务消失。单任务验收和历史alias工具拒绝多任务配置。

本机 Node 全量默认195通过（其中84是忽略目录历史副本）；最终当前源112/112通过，含14项多任务回归（补充单任务工具误用拒绝）。Python157项中151通过、6平台跳过；打包白名单更新后8项发布器复验通过；真实隔离Windows GUI/独立助手提交及回退通过；GPT-6 Sol medium只读复审无阻断。

真实只读隔离联调：原测试任务COMPLETED，当前开发对话RUNNING，两条均可投影24条最近可见记录且可向前分页，当前对话单页56187字节；没有发模型输入、没有修改现用配置。首次联合读取有一项暂时UNKNOWN，随后逐项完整读取均通过，未将初次失败隐藏为全程成功。明确配置提案和脱敏证据仅在 artifacts/alpha11；未写真实身份或对话正文到Git。

现用仍alpha.10，当前对话尚未在手机实测。下一步固定签名候选、准备受审查切换与回退，然后由用户正常断开旧连接器。新范围包含本维护对话，因此切换须等本轮结束、所有开放任务空闲；不能绕过忙碌门禁。Android、relay、公网feed不在本次变更内。

## 2026-10-13 — alpha.10 已安装，原任务新消息双向接续通过

用户批准 alpha.10 维护升级及唯一的既有获批备用权限摘要。原助手固定 alpha.9 原始窗口句柄后等待 600 秒超时，返回 old_process_exit_unconfirmed，尚未开始修改安装；用户随后明确确认“已安全断开并关闭连接器”。未按旧 PID 重开/终止进程，没有伪称观察到旧句柄退出码。复用既有 verify_clean_exit：旧签名控制器已正常清理并移除自身 runtimeNeedsReview、application/runtime 锁均可取得、旧 PID 缺席；完整身份/任务/配置、proof、旧账本及历次维护记录与原审查完全一致。

本机独立续接 continue.local.py 保留原助手和审查，先验原助手固定字节再执行同一字节，探针前后三次复核审查期限/完整现场，仅调用原 apply_after_exit。6/6 续接回归及 GPT-6 Sol medium 独立复审通过。未清任何保护或修改 Desktop 设置，未重新配对或发送模型消息。续接 SHA256：ee43bf5b9f2d8bf80c31b10448b6e108e3e7f3799d375fd51906c142960b4e71。

实际事务 committed/success；独立审计确认 alpha.10、sequence6、签名/ZIP/版本槽有效，alpha.9 原槽保留；原身份和任务数据保持，主权限摘要不变，只追加已批准第二摘要，旧配置/旧 staged 精确归档；两份事务日志及完整保全摘要一致。真实候选认证通道健康检查、新鲜原任务 idle/permissionsVerified/receiptsCertain 均通过；新 GUI 标题“Desktop 手机联调 · 0.1.0-alpha.10”，应用锁持有。脱敏证据：artifacts/alpha10/installed-alpha10-audit.json；本机续接审计 normal-close-continuation.local.json 明确 originalHandleExitObserved=false。

用户在 alpha.10 点击连接后，已明确确认手机原任务中 PHONE_ALPHA9_OK 与 DESKTOP_ALPHA9_CONTINUE_OK“两条回复都能看到”。升级后真实手机历史读取恢复通过；两条消息未重发。用户随后明确确认手机能看到 PHONE_ALPHA10_OK 和 DESKTOP_ALPHA10_CONTINUE_OK 两条新回复。Desktop 只读复核确认两个回合均在同一原测试任务 completed、error=null，任务 idle；本轮未由维护工具发送或重放消息。alpha.10 的手机发送→Desktop执行→电脑接续→手机显示的新消息双向实机验收通过。此结果不替代长时间闲置、断网和再次重连的稳定性验收。本次是本地签名维护安装，不是 alpha.10 在线 OTA；Android、relay 与公网 feed 未更新。不要重复运行维护脚本或删除保护。

Core PR CI [38368132481](https://github.com/nzkres/codex-desktop-remote/actions/runs/38368132481) 两平台测试、隔离演练及打包成功，但上传均明确报 Artifact storage quota has been hit，整体失败，无本次 CI 可下载产物。源码产品输入仍 11bbbb3a8e98ae041306af8f939a1858940ca696。详细故障、签名和恢复边界见 [alpha.10 维护记录](WINDOWS-ALPHA10-MAINTENANCE.md)。

## 2026-10-13 — alpha.9 双向验收失败已定位，alpha.10 修复候选

用户反馈“电脑回复后手机未同步”。只读核对同一原任务中 PHONE_ALPHA9_OK 与 DESKTOP_ALPHA9_CONTINUE_OK 均 completed、任务 idle。现用 alpha.9 的本机认证 health 返回200且实例一致；mobile-sync 返回409/permissions_changed。新鲜快照兼容、原任务、空闲和回执通过，但权限不匹配原安装摘要，恰好匹配用户此前明确批准并固定的第二份摘要（approved-permissions.local.json）。因此手机发送与 Desktop 执行已经完成，读取被连接器权限门禁拒绝；不能把 alpha.9 初次读取成功写成双向接续通过。

alpha.10 候选新增唯一可选 approvedAlternatePermissionsHash，保留原 permissionsHash：两份完整摘要都必须明确批准且格式合法、不重复；不会捕获或学习新的权限。服务读写、正常OTA前检、诊断和验收工具共用同一判定；旧 alias-only 维护探针遇备用摘要提前拒绝。未列出的权限、网络/目录变化、未知回执和活跃任务保护仍在。用户已批准的两份范围不表示它们是同一个序列化别名，不做通用等价转换。

验证：源 Node 套件98/98（含14项新回归）；Python157项中151通过、6平台跳过；版本目录/生成历史检查通过，独立审查通过。默认 node --test 还发现忽略目录下历史测试副本，共182通过；98是明确限定test目录的当前源测试计数。只读实机候选投影在内存采用已批准第二摘要，能同时输出两条已有回复，idle/permissionsVerified/receiptsCertain通过；未写现用配置、未发送任何消息。这证明候选读取修复，不证明手机已收到或新版本已安装。

现用仍 alpha.9、原连接器保持运行；alpha.10 尚未安装，Android/relay/feed未切换。下一步完成签名候选和正常退出交接审查，保留原身份/数据及旧槽，用户操作前固定原GUI句柄；不再要求重新配对或重复发送已完成标记。

## 2026-10-13 — alpha.9 已实际安装，安卓原任务读取通过

本段取代下方“等待重启/待安装”停点。用户完成完整重启并在原任务收到 ALPHA9_AFTER_RESTART_READY；Desktop 只读核对同一 UUID 的该回合 completed、任务 idle。新的 Windows boot、原完整文件清单/身份和签名均通过。重启后权限恰好恢复为原安装设置，与旧审查逐字段无差异、网络关闭；重启前获批的新 proposal 已不匹配，因此未执行原 repair-approved 的 resume，也未改权限摘要。

保留原冻结脚本、review 和围栏，另建仅用于本机的 repair-preserved.local.py：固定原审查和操作器、续接脚本及原配置摘要；只读探针必须匹配现有配置。21/21 合成测试通过（107.106 秒），真实隔离 GUI 成功提交 alpha.9 / 坏候选回退 alpha.8 均通过，GPT-6 Sol medium 独立复审通过。探针期间审查材料变化、完整身份/任务变化、结果未知或回退证据不足均停止；未增加产品自动授权或通用冷恢复逻辑。

真实 resume 已执行一次并 committed/success。独立复核：已安装 alpha.9、sequence5、候选 RSA/包/版本槽有效、alpha.8 旧槽保留；原身份及任务数据保全、配置文件精确字节和 permissionsHash 不变；归档、双事务日志和完整保全摘要一致。实际唯一连接器窗口标题为“Desktop 手机联调 · 0.1.0-alpha.9”，应用锁持有；候选启动时真实认证通道健康检查通过，之后新鲜原任务 idle/permissionsVerified/receiptsCertain 通过。

用户点击新窗口“连接”后，已在安卓原任务看到既有 ALPHA9_AFTER_RESTART_READY，明确回复“能看到，原任务已恢复”。手机实际读取验收通过；新的双向接续尚未验收，已请求 PHONE_ALPHA9_OK → DESKTOP_ALPHA9_CONTINUE_OK，每条仅发送一次，结果不明不重试。本次为本地签名维护安装，不是 alpha.9 在线 feed OTA；Android、relay 和公网 feed 本轮未切换。不要再运行 prepare/resume、清标志、改权限或重新配对。

本机忽略证据：artifacts/alpha9/post-restart-preserved-preflight.json、preserved-continuation.local.json、installed-alpha9-audit.json。续接脚本 SHA256：90285ecda56de3ecba6a57a3ab88731aa4c89205eb4b72c6f6b65d53f481c753；续接记录 SHA256：0f27cfd62b8c3893c31ef2211ebedcea5614aa21edcba8c888eb02dee87a04d6。原准备 review 仍 DD79F610…、原脚本仍469C7FFE…；完整旧摘要见下方准备记录。

## 2026-10-13 — 当前权限已获批准，alpha.9 维护准备完成，等待完整重启

用户明确选择“采用 Desktop 当前设置，继续修复”。固定本次权限审查后，按该设置只读核验原任务完整历史、空闲和回执全部通过；网络关闭、原任务与目录不变。审批者别名之外，明确纳入本任务可视化目录和两项临时目录标志变化。批准输入只保存在本机忽略目录，不自动跟随未来权限变化。

本机 `artifacts/alpha9/repair-approved.local.py` 为本次获批维护器；原 `repair-once.local.py` 保留但不再用于此次维护。18/18 合成测试、真实隔离GUI成功提交alpha.9/坏候选回退alpha.8均通过。独立审查补齐旧配置及staged归档的持续哈希保全，归档变化时闭锁、不启动回退。正常回退保留用户本次批准的权限摘要，不盲目恢复旧授权或数据。

真实 prepare 已执行成功：主事务写入启动围栏，审查记录绑定完整旧配置/身份/任务、批准输入、候选包、脚本和boot。独立复核所有原始保全文件与完整身份摘要仍一致，保护runtimeNeedsReview仍true；实际版本仍alpha.8，尚未执行resume或改真实permissionsHash。证据 `artifacts/alpha9/approved-prepared-audit.json`。

- 最终维护器SHA：`469c7ffef449d84c90c64208c8e3f5dc7ce661ca6ac15d6aee626f28635cb349`。
- 准备review文件SHA：`dd79f610b0623785481421479cb7a8fe9c0fdc23e4291e490661796fa94ac5f8`。
- 批准权限review SHA：`43b7128ff341a75207a9a83b6536bb78d6d2dcdf0a0c1567f57d43de5795f763`。

已通知用户保存工作、完整重启Windows，连接器继续关闭。重启后先确认新的boot和全部摘要，再核验原任务；如仅历史页无owner，需用户在同一任务发送一次新的ALPHA9_AFTER_RESTART_READY。任何差异停止，不重跑prepare、不改已固定审查文件。只有预检通过后才执行获批脚本resume，以当前任务设置更新连接器摘要并切换候选，再验证真实身份/数据/权限/健康和安卓原任务。尚不声称安装或手机验收成功。

26项获批debug临时产物清理已完成，备份和其他产物保留；配额是否重算及新CI上传仍未验证。Android/relay和公网feed本轮未切换。

## 当前停点：2026-10-13 简化重连已实现，三端 OTA 候选待实机切换

源码在 `C:\code\my-code\codex-desktop-remote`，独立分支 `codex/simple-reconnect-alpha9`，草稿 [PR #6](https://github.com/nzkres/codex-desktop-remote/pull/6)。先读 [本轮实施与候选哈希](RECONNECT-AND-OTA-20261013.md) 和 [STATUS](../STATUS.md) 顶部；下方旧停点仅为历史，不重跑旧维护脚本。

1. P0–P3 已实现并通过本地验收：断网主动退出仍安全 drain，明确 Desktop 失联后重新发现原任务，新状态与再次连接提示；没有新增执行器或放宽权限。候选产品输入 `2a783a0`，最终 Windows/relay 签名包只使用忽略目录 `artifacts/alpha9/release-git/`；其他草稿标记 DO-NOT-PUBLISH。
2. 本机真实 alpha.8 仍有退出未确认保护；普通更新被拒绝。用户打开原任务后仍无 owner，已请仅发送一次 `ALPHA9_PREFLIGHT_READY`，不要自动补发。下一步先只读复核原任务空闲、原权限与回执，再完成具体维护审查并取得批准；没有授权清 guard、改权限或直接安装。本机一次性 alpha8→alpha9 工具未执行真实 prepare/resume，预计需要准备后完整重启 Windows，不能复用以前的 boot 或旧恢复脚本。
3. Android 最终 `e77079a` / run `38345972326` 单测/lint/编译/版本核验及实际模拟器 14/14 仪器测试全部通过，只有 artifact 上传因配额失败。Core 两平台测试/演练/打包也成功，同样上传失败；两个 CI 整体均为 failure，没有本轮可下载 CI 包。26 个旧未签名 debug artifact 已有大小/CRC/SHA 验证的完整本机备份；只在用户批准 exact review-list.csv 后删除这些 ID，不能删除最新样本、签名 APK、Release 或其他仓库。
4. Android main-only 受控签名 gate 保留；PR #6 尚未合并，尚无 alpha.6 受控 APK。具体审核通过后再确认合并和受控构建。服务器仍 relay alpha.5，所有新 feed/真实服务切换尚未执行；继续按 Windows→Android→relay 验收，每一步独立记录实际版本与手机原任务证据。

## 当前停点：2026-10-12 alpha.8 已安装，安卓原任务恢复

用户已明确批准当前任务权限重新确认，随后本机维护事务 committed/success。独立签名/身份/任务/批准配置/旧槽/新鲜探针检查通过；用户在 alpha.8 点击连接后，确认安卓原任务能看到 REMOTE_AFTER_RESTART_READY。本次修复已完成，不再运行 prepare/amend/resume，不要求重启、重配对或重复标记。

这只证明本次实际安装和手机原任务读取恢复；未新增双向消息执行验收，未发布 alpha.8 公网 OTA，未更新 Android/relay。完整证据见 STATUS 最新段与本机忽略目录 installed-alpha8-audit.json。后续如果再次失败，先检查当时真实状态，不能用旧探针证明当前健康，也不能忽略权限差异。以下带“最新/最高优先级”字样的旧停点均为历史记录，以本段为准。

**2026-10-11最新停点：重连未恢复。** codex-desktop-remote-desktop1007 / codex/desktop-1007-recovery准备alpha.8双pin和只读诊断；实际alpha.7有runtimeNeedsReview，旧26.930文件消失。候选新26.1007只读同任务/marker通过，权限摘要不匹配。用户已打开任务，等待DESKTOP_1007_READY后复验。先读STATUS和DESKTOP-26-1007-RECOVERY.md，不清保护/改摘要/复用旧维护器。原句柄丢失的冷恢复未实现、未切换；Android PR4工作树保持。

**最高优先级停点：用户已在原窗口点击下载并安装，Windows alpha.6→alpha.7 GUI在线事务现已committed/success、ledger3；不是待安装。** 根代理只读独立验收实际签名槽/发布包绑定、原alpha.6保留、完整身份/配置/权限/任务摘要保持、应用锁及唯一新GUI与可信启动门归属全部通过；真实Desktop探针idle/permissionsVerified/receiptsCertain与版本alpha.7通过，自动模型输入0。用户随后在新窗口连接，在同一原测试任务完成PHONE_ALPHA7_OK与DESKTOP_ALPHA7_CONTINUE_OK，并明确确认“手机两条回复都能看到”。**Windows真实OTA及升级后同任务接续闭环已通过**；下一阶段才准备Android alpha.5的独立OTA，不捎带更新relay。不要重试安装、重跑维护或重复未知消息；先读STATUS最新段。本机脱敏证据final/installed-alpha7-{verification,gui}.json。下面发布前/待安装停点均属历史。

**最新停点优先：Windows alpha.7 test私有源已按用户明确授权发布sequence3；本机未安装，仍alpha.6/ledger2。** 服务器旧包保留、其他feed/服务保持，正常TLS公网匿名metadata/ZIP均401，隔离测试管理员会话完整下载并验签/大小/SHA/43文件通过，注销后拒绝。设备鉴权曾因现用连接的`runtimeNeedsReview`保护拒绝，未清标记、未断开或改身份；故不声称真实设备下载或OTA通过。用户本次仅批准源发布，实际客户端安装留到下一阶段安排。先读STATUS最新段及[发布审查](WINDOWS-ALPHA7-OTA-REVIEW.md)，不要重发sequence3或重跑发布助手；未知状态先只读检查。下一步正常断开后验证设备入口，再按原GUI执行检查/下载/独立助手和同任务验收，不重跑alpha.5维护器。公网可用包为原`91676bd9...412266`，有效至北京时间2026-10-15 21:12:09。

以下保留发布前候选记录，其“待发布”已由上方完成状态取代。

**alpha.7当前停点：** 独立分支`codex/windows-alpha7`的c20c203已通过push37781824455/PR37781829558两平台CI；本机Node64、Python146通过/6跳过、六种维护及HTTPS GUI提交/回退通过。已核验43文件并用原项目库签发sequence3，ZIP95,085字节/SHA`91676bd9b0e1526c5808fb6a8d8156c2511f38ea4f306b1069d20d95d0412266`，有效至北京时间2026-10-15 21:12:09；仅使用忽略目录`artifacts/alpha7-preparation-20261008/final/`中固定ZIP和metadata。草稿PR #3叠加PR #2，未合并main。先读STATUS与[发布审查](WINDOWS-ALPHA7-OTA-REVIEW.md)；**准备完成，实际feed发布和当前alpha.6→alpha.7在线切换仍待用户最终确认**。当前真实alpha.6/ledger2保持，Android/relay仍alpha.5；不提前让用户检查旧feed、关闭窗口或重新配对，不重跑alpha.5维护器。发布须CAS旧metadata、包不可变、保留鉴权与原数据；不能降ledger来撤回。获批后先验证源，再让用户在原窗口正常断开并安装，最终核对同一任务。

**当前停点（优先于下方全部历史）：Windows已实际升级到alpha.6，用户确认手机原任务两条回复都显示，同步验收通过。** 用户确认此前按“×”正常关闭；`resume-clean-exit --normal-close-confirmed`经完整绑定、签名账本、冷态双锁、保护键缺失、旧PID无人占用及新鲜alias探针续审后，原事务已`committed`。没有保留旧退出句柄，不声称追溯证明旧退出码；不删门禁/降账本/重配对。原signed ZIP保持`4df87881...7811cf`，实际新GUI唯一、签名槽正确，身份/配置/权限/任务和回执保持。详见STATUS与忽略目录`maintenance-committed-verification.json`。

用户已重新连接；本机alpha.6 health与idle/permissionsVerified/receiptsCertain通过，原UUID的mobile-sync HTTP200且包含两条既有回复。用户已确认`PHONE_ALPHA5_OK`和`DESKTOP_ALPHA5_CONTINUE_OK`都显示；不要重发消息、重跑维护命令、重新配对或要求再关窗口。当前本次维护任务完成；Android/relay仍alpha.5、feed未发布alpha.6，三端联网OTA验收仍未完成。后续按OTA-ACCEPTANCE逐组件安排N→N+1，不把本地签名包维护交接计作HTTP OTA闭环。最新repo-only正常关闭恢复代码140项Python通过/6跳过，Node64、维护22单测与六项Windows合成演练通过；最终代码9f6e93a的Core push37775664273/PR37775672832均Windows/Linux成功，含六例真实合成维护演练；最后仅文档记录，不重新打包或改签。PR #2草稿未合并。

## 历史停点（下方授权、窗口与实现状态已由上方取代）

**最高优先级停点：用户已批准，实际apply因旧GUI120秒未退出而停止。** 不再请求相同升级授权；也不要照下方旧指令直接重跑apply。`current`仍alpha.5，主transaction与maintenance为needs_review/old_process_exit_unconfirmed；候选已暂存、ledger已接受sequence2，secondary maintenance-transaction不存在。只读复核身份/配置/回执/任务保持、原GUI仍匹配（当时PID20032）。请用户先保持原窗口，在其准备操作后重新审查固定原进程、已有签名staging/ledger和完整数据绑定，再安排安全继续交接；不能删除primary、runtimeNeedsReview或ledger，也不能把用户确认“关闭”代替原始句柄退出证明。审查文件已可能过期，不能凭旧hash绕过时效。当前工具没有自动resume命令；需要完成受审查的中断继续方案，原窗口提前退出会失去可固定原句柄的机会。见STATUS和忽略报告apply-timeout-verification.json。候选仍是同一签名包，不重打或替换同版本。

**可执行停点（晚于下方实现记录）：** 56f488e的Core push37763446214/PR37763453724两平台成功；43文件Windows CI包逐字节匹配Git，已用原测试keyId在本地签发sequence2。包`artifacts/alpha6-maintenance-20261008/windows-0.1.0-alpha.6.zip`，SHA256 `4df87881c5d0f704a4b2ac0a1a2d12e022f5b11062a8740d7ad43f96ca7811cf`；元数据`windows-latest.json`。真实review成功，审查文件同目录`maintenance-review.json`，hash `e19672fa6647a6aec018f64d545ad2acfa65204fe7f2f0212fa823be663e5ff9` / expiresAt1791457239。原GUI PID20032只作当时证据，必须重新核实原句柄、不可按旧PID杀进程。用户实际切换确认已询问但尚未收到；未apply/部署feed/新Release。先检查审查时效和最新用户答复，过期则只读重做review到新文件；批准后助手独立运行，输出waitingNormalExit后再请用户正常关闭连接器（不关Desktop），按工具验证结果推进手机原任务同步，禁止重发已完成的两条消息。若不批准就保留现用alpha.5连接。

**维护路径最新增量：** `updates/windows_maintenance.py` 的review/apply已实现，限alpha.5→alpha.6/test/已核实审批别名；用户正常退出前持久primary启动屏障，完整绑定/原句柄/签名与原事务健康校验，失败保留needs_review。Node64通过、Python135通过/6跳过，Windows合成身份真实GUI/helper提交和坏候选保留现场通过。真实原任务只读alias预检已通过；没有停止现用窗口、改身份/配置或发送消息。先读[维护指南](WINDOWS-ALPHA5-MAINTENANCE.md)和STATUS最新段，完成受控候选签发与review后再请求实际切换确认。下面“工具尚未实现”属于此前停点，不应据此重写工具或重建身份。

## 此前停点与基线

**最新停点：** 两条 PHONE_ALPHA5_OK、DESKTOP_ALPHA5_CONTINUE_OK 已在原 Desktop UUID 完成，但手机因 permissions_changed 无法读取；不得重发。根因是 guardian_subagent/auto_review 兼容别名切换。Windows alpha.6 已实现最小修复，45项Node、118项Python通过（另6跳过）；新源码只读实机能返回这两条手机协议消息，原权限摘要/身份/配置/回执未改。运行中的三端仍为 alpha.5，手机恢复及 OTA 未验收。具体物理路径见忽略报告windows-retry-install.json，故障与新源码证据见alpha5-sync-failure.json、reviewer-alias-source-verification.json。

ea45239的Core push37758224837和PR37758232762两平台均已通过；已下载复验43文件Windows候选包，大小/SHA见STATUS。仅合成GUI/helper收尾等待修正，不改产品槽；候选尚未签名/发布/安装。PR #2仍draft/open，main未合并。

下一步需实现独立维护交接工具并做合成验证，之后才形成可执行的切换审查。alpha.5 的 GUI/helper 仍会执行旧 update_probe，原生 OTA 会被同一比较缺陷阻断；候选失败后的 alpha.5 健康检查也不能保证通过。新工具必须保留固定原GUI句柄正常退出、独占锁、完整身份/配置/回执CAS、签名候选/账本和事务门禁；只接受已核实的顶层alias差异，真实权限变化仍拒绝。失败保留needs_review，不冒称健康回退。不要简单点击安装后宣称成功，不改 permissionsHash、已签槽或 runtimeNeedsReview；保持当前实例，实际停用/切换需按已有用户边界确认。**这一维护路径尚未实现，普通合成OTA演练不证明能恢复本次现场。**

原任务UUID为`01a1158c-72ea-7db3-84d1-b47471220252`「回复 DESKTOP_REMOTE_READY」。先读[设备基线记录](OTA-DEVICE-BASELINE-20261008.md)：早期逻辑home因AppX重定向启动失败，随后原安装误填944端口；失败目录CodexDesktopRemoteTest完整保留，用户取消申请后另建当前新安装，未重置或迁移身份。账号显示工具仍在原目录operator；不要重复bootstrap/configure、删除保护或修改签名槽。alpha.6仅Windows修复，Android和中继未改版本。

2026-10-08部署阶段已批准并完成PR #1合并、专用测试签名、六项Android Secrets、alpha.5构建/复验，以及现有服务器上的**独立隔离测试部署**。服务器内安装与验收、主电脑公网 `9443` HTTPS 鉴权下载和 `17443` FRPS 合成夹具连接已通过；现用服务和身份保持原样。后续用户已自行完成手机安装、登录与全新身份配对，最新状态以上方为准；真实身份迁移和现用服务切换未获批准，真实N→N+1尚未验收。部署证据见[隔离部署记录](OTA-ISOLATED-DEPLOYMENT-20261008.md)。

先读[STATUS](../STATUS.md)、[隔离部署记录](OTA-ISOLATED-DEPLOYMENT-20261008.md)、[落地验收](OTA-ACCEPTANCE.md)、[部署审查](OTA-DEPLOYMENT-REVIEW.md)、[发布](OTA-PUBLISH.md)、[安装起点](install/README.md)和[恢复](OTA-RECOVERY.md)。保留旧alpha.4资产，核对当前远端、分支及未提交改动后再继续；不要只凭本页旧提交猜测最新状态。

## 已完成的受控基线

- PR #1合并提交`1e405ad`。alpha.5程序输入为`1a4b4c0ced91d1a384e723374a6141d84e43de9e`，分支`codex/controlled-alpha5`经Core37736389583/Android37736389589通过后，main非强制快进。后续文档提交不改变这些程序输入。
- main受控Android构建[37736837251](https://github.com/nzkres/codex-desktop-remote/actions/runs/37736837251)成功：94 JVM、lint0errors/45warnings、apksigner证书验证和包内trust比对。实际APK下载SHA与CI相同，AAPT2核对包名/code5/minSdk26。它仍为debuggable测试构建，不是生产发行包。
- [alpha.5私有归档](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.5)已发布，七项资产服务端大小/哈希与本地一致，见STATUS。包含APK、Windows/relay源码ZIP、bootstrap公开信任/已签元数据、Android报告、verification和SHA256SUMS。三端版本说明在仓库`docs/versions/`，不以Release文字代替。
- Windows43/relay29源码文件与Git输入逐字节相符。三端实际包已签RSA元数据；Windows Python3.12/WSL Python3.14在全新临时home通过bootstrap、账本与重复安装拒绝。没有启动真实连接或使用账号/身份；真实GUI/helper及cgroup升级/回退来自CI合成演练。

## 签名资料与有效期

专用库在本机`%LOCALAPPDATA%\CodexDesktopRemoteSigning\test-v1`，CurrentUser DPAPI保护RSA和Android加密PKCS12/口令。RSA私钥没有上传；Android六项Secrets已配置在固定私有仓库。`github-provision.json`为configured。**不要重复执行create/provision、覆盖文件或重新生成同名证书。** Git克隆不能恢复该库；独立备份和跨电脑恢复尚未实现/验收，不把复制DPAPI文件当作可靠备份。

keyId为`cdr-ota-test-20261008-v1`，公开SPKI/APK证书指纹见STATUS。首次trust须核对已认证仓库中的公开指纹；同页SHA表只能核对完整性。后续Android构建必须沿用同一APK签名证书，RSA发布私钥不传CI/服务器，不在客户端嵌入PAT。

三端alpha.5元数据为test、sequence1，固定`https://106.53.126.69:9443/desktop-remote-updates/test/{component}/latest.json`，有效至**2026-10-15 06:33:03 UTC（北京时间14:33:03）**。部署/首装前重查时间。过期时由维护者以新序号重新签发相同已核验程序包，单独归档新元数据；不能修改已签字段、替换同版本包或删除ledger绕过保护。

## 隔离服务器结果与最小下一步

106.53.126.69 上已以独立 `cdrtest` 账号、目录/数据库和端口完成 alpha.5 relay、FRPS、HTTPS 与私有 OTA 安装；目标环境为 Python3.12.3、systemd255、cgroupv2。服务器 loopback 经正常 IP 证书 HTTPS 完成三组件账号下载及 RSA/哈希/大小验证，六项匿名请求获401；合成设备 Windows 获200，Android/relay及暂停/撤销获403。合成0.0.2升级、坏候选回退、四棵自有树、alpha.5受管stop/start、cgroup清空和DB摘要保持均通过；FRPC/FRPS0.71 TLS与设备插件通过。独立Caddy reload hook已验，certbot既有timer active，**尚未实际续出新证**。见[隔离部署记录](OTA-ISOLATED-DEPLOYMENT-20261008.md)。

主电脑公网 `/auth/status` 返回200；三组件元数据与包共六项匿名请求返回401，新隔离账号三端完整下载经 `updates.security` 验证正常TLS、RSA/大小/哈希匹配alpha.5；跨origin凭据在发请求前拒绝、logout后401。独立Windows Job内合成FRPC0.71经公网FRPS/TLS/plugin到无任务HTTP夹具，服务器loopback读到 `CDR_EXTERNAL_FRP_OK`；原始句柄与Job整树退出确认，夹具凭据已撤销。**下一步是经用户手动在测试手机首装、登录和配对新Windows身份，选一条专用Desktop任务建立真实N基线；尚未完成三端OTA。**现用服务/数据/身份保持原样；私有GitHub Release和14天Actions artifact都不能替代受鉴权在线源。

随后分别确认Windows新目录和测试手机首装，用户手动登录、配对和系统安装；未知手机签名不能默认卸载。Windows需Python3.12/Tk/cryptography、Node20.19.0、FRP0.71.0、Desktop26.930.7945.0及精确文件指纹，不放宽pin。只选择一条专用Desktop原生任务，先完成手机→Desktop同任务接续→手机的N基线，再构建尚不存在的alpha.6并逐组件验收N→N+1。

下载、bootstrap或probe_verified都不能替代真实交互与数据保全。每条证据记实际版本、包哈希、签名标识、事务状态与结果；未知操作不重放，回退失败保留needs_review，不覆盖新数据库。只保存脱敏结果，不把私钥、token、DPAPI文件、数据库和原始任务内容放进仓库。
# 2026-10-12 最新接续：已准备，等待完整重启

本段取代下方“等待标记/未部署围栏”旧状态。用户已完成REMOTE_RECONNECT_READY并正常关闭旧GUI。原权限摘要实时通过，无需换绑。用户已同意准备好后手动重启；现在prepare已成功，独立verify-prepared通过。现用仍alpha.7，primary transaction为needs_review/error=one_time_repair_waiting_restart，原runtimeNeedsReview=true；这是有意围栏，禁止删标志或再运行prepare。

本机忽略目录`artifacts/desktop1007/repair-once.local.py`为一次性工具，sha256 `4d8014b72c68668e43cdc84a30799eb38e326d366417451d69e8c4dbb53ed131`。`repair-review.local.json`的storage.digest为`a85cd245b4e631ef5b80d85a98e0b244d96af648f8fa3071824fa23e4cac6970`；prepared-audit.json可复核。不要编辑已绑定脚本或review。原committed日志保留在review/主围栏previousJournal。该本机工具不进Git/产品包，跨电脑不可执行。

收到用户“已重启”后：先核对boot已晚于review.boot、原文件摘要及围栏一致，确认Desktop原任务已打开。用正常用户Node执行`artifacts/desktop1007/recheck.local.mjs`，必须compatible/sameTask/permissionsPreserved/idle/receipts全部true；若Desktop恢复后快照仍推断/权限不匹配，只读停下，必要时请用户在原任务完成新无工具标记，不能改权限摘要。

满足上述条件才使用正常用户Python `-B artifacts/desktop1007/repair-once.local.py resume`。它复验签名/boot/完整CAS，将原配置仅desktopRoot切至固定26.1007，保留permissionsHash；在主围栏保护及运行锁下消除已由完整重启证明的旧运行标志，复用既有事务验证新版本身份数据和真实通道。签名槽旧版保留，但旧Desktop不兼容，候选失败不得启动旧版；失败保持needs_review。仅允许一次尝试，中断后人工复核，不重复resume。

完成后核验primary/secondary committed、实际GUI标题alpha.8、原身份/权限/任务保持，再让用户点击连接、安卓重试原任务。真实手机读取仍未验收；不要把7项合成测试（模拟boot/probe/GUI）当作实际重启或任务验收。签名包SHA见STATUS。本机工具仍需后续实际执行验收，不宣称是通用冷恢复或OTA闭环。

# 以下为此前准备记录

用户要求简化，不再开发通用恢复框架；未接入恢复模块已移除。现用alpha.7未更改。用户允许准备后手动重启，但现在不要让其重启；未部署启动围栏或完成一次性恢复执行器。

已签名本地alpha.8 sequence4候选与当前安装审计在忽略目录artifacts/desktop1007，精确哈希见STATUS。真实配置仍指旧Desktop目录，runtimeNeedsReview仍true。当前任务可读，但最新权限快照profile=null且仅一条runtime root，一次性review以review_scope_changed停止，未生成新配置；用户已被要求在原任务发送REMOTE_RECONNECT_READY，等待其完成后再只读复查。**没有允许拿历史值覆盖实时权限，也没有执行换绑或清guard。** 原身份和2条accepted回执保留。

下一步：收到标记后核对新鲜权限/空闲/回执；准备限于此安装的一次性、可审查维护步骤，复用现有签名与进程管理工具，完整绑定原数据和待安装候选。完成准备后才通知用户关闭窗口/重启。原Job未命名且句柄丢失，端口/PID不能补退出证明。升级失败保留诊断现场，不盲目回退至已不兼容旧版；成功后验证真实通道，再请用户安卓读取原任务。不要把本地签名、探针或构建当作实机升级/安卓验收。
