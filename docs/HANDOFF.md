# OTA 交接

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

