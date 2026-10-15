## 2026-10-15 — Android alpha.12 已发布，手机安装待验收

原私有更新源现为alpha.12/versionCode12/sequence6。原证书、实际Manifest/trust/版本资产、118项单测、Lint、APP/UI测试APK编译、实际HTTPS鉴权完整下载及匿名401均通过。仅Android更新；连接、发送回执、Windows、中继与配对身份保持。新页面与限制见[发布记录](docs/ANDROID-ALPHA12-PUBLICATION.md)。

当前只确认已发布可下载，未自动安装手机；真实UI与原任务答题/发送接续仍待用户更新后验收。


## 2026-10-15 — alpha.12 页面开发与发布验证进行中

已封存用户批准预览并接入原生新导航、多电脑对话及任务仓库。正在运行Android、Node与Python检查，尚未签发或更新私有源；当前已发布手机版本仍alpha.11。本次只迭代Android，不切换运行中的Windows连接器、中继服务或配对身份。


## 2026-10-15 额度与核心流光已发布

Android alpha.11 与 Windows alpha.17 已发布到原私有更新源，两端原签名及实际 HTTPS 完整下载通过。手机/电脑安装待确认，当前连接器仍alpha.16；额度须更新电脑后同步。

输入框额度快照与重置时间、三种独立亮度、核心沿网格外散光效及本地记忆已开发。Android110单元/Lint与UI测试包编译、Node182回归、Python199项中192通过7跳过；模拟器/手机视觉及配套连接器安装后的额度链路待确认。见 [发布记录](docs/ANDROID-ALPHA11-PUBLICATION.md)。

## 2026-10-15 Windows alpha.17 候选

额度读取器已在当前任务只读核对成功；Node178/178、Python183项中177通过6跳过。额度使用日志快照、最多读最近2MiB，有界缓存20秒；不代表实时账单。安装与发布另记，不修改原身份或回执。

## 2026-10-15 Windows alpha.16 已签名安装、恢复连接并同步私有源

用户明确授权补齐恢复流程、保留身份与回执并实际恢复。固定源码7b9fd98，签名包181354字节，SHA256 8e76f9f2dd2fe0f58363d979c8112a97cd2d9f48147295d6e7b78999acc11043；53项文件与源码、原公钥验签通过。Windows元数据sequence12，SHA256 482a639bb812d6fcc6a659e91f9b7e7b89ee84e5abc20ba40e72481b0cae5184；北京时间11:18:03私有源原子发布，实际HTTPS鉴权下载及签名/大小/摘要通过。旧索引和包、服务器进程/程序及其他feed保持不变。

管理员核验了普通账户无权读取的3个Node进程；没有清理未知进程。独占锁、旧Job收容实现、原任务身份/权限、完整回执和数据CAS通过。恢复记录及维护安装记录committed，现装alpha.16。原设备身份、10条accepted发送回执与任务数据保全通过；仅更新精确核验的Desktop26.1007.9446.0路径。管理员新文件的继承权限问题经确认后，仅对69项本次新增/改写路径复制旧槽原owner/DACL，保持其他目录原状。随后从原Start.pyw以普通权限 --connect 启动。

实际本地健康HTTP200、实例匹配；终端active/online、connectorVersion alpha.16。手机认证/执行电脑同一路径：任务列表200（64条）、本轮mobile-sync200（protocol1，24项）。两条锚点均idle且无活动turn/待确认提交；本轮对话active、无待回答请求和未确认提交，是update-status409 thread_busy的唯一原因。Android源仍alpha.9/sequence3，元数据签名和固定摘要验证通过。本轮结束后的手机实际点击/安装仍由用户确认；手机本地回执没有调试通道，不能用电脑回执冒充已核验。

Node178/178，本地Python全套与后续21项专项通过；Core CI38675221928 Windows/Linux测试步骤、Windows配对维护交接与失败候选围栏演练通过，整体仅artifact存储配额失败。源码/实际安装/更新源/手机UI分别记录。PR15面向现用Windows分支；本次没有合并PR14或发布新的Android包。

## 2026-10-15 Windows alpha.16：异常运行核验恢复（开发验证中）

增加独占运行锁下的进程清单、旧 Job 收容实现、原任务/权限/回执检查及完整状态 CAS；只解除已核验运行周期的保护，保留设备身份与任务数据。支持精确 Desktop 26.1007.9446.0 指纹。未完成的恢复事务继续阻断启动。当前尚未应用现场或签名安装，实测结果另记。

## 2026-10-14 — Windows alpha.15私有源已发布，现装alpha.13鉴权下载验收通过

用户明确批准仅发布Windows。北京时间15:53:48在隔离服务器将Windows测试feed从alpha.13/sequence9原子切换为alpha.15/sequence11；先核对旧索引摘要，再在独立root私有目录验签、解包验证51文件并保留旧索引。固定173385字节签名包SHA256为98f707824659ad3c67a0384f8c5ba7b830040e93d82b140f7313e560ba08cb5e，元数据SHA256为beb98d652ec155db0a1ed6d643f14661b7312161f696f578b050d89ef62dcd25。

15:54:58使用本机现装alpha.13自身更新模块、现有配对设备身份及正常TLS实际下载：metadata/ZIP匿名均401，设备鉴权成功，原发行签名、升级序号、下载大小/SHA、隔离解包51文件全部通过。install/current/trust/ledger/candidate/DPAPI身份前后摘要一致，账本仍9。服务器relay/FRPS/Caddy进程及启动记录、relay current和其他组件feed未变化，旧Windows包保留，目录0700/文件0600。

已发布且私有源可下载；尚未安装alpha.15或实机验收新功能。现用alpha.13保持原状，不自动停止/安装，不合并PR，不更新手机或服务器程序。用户等任务空闲后正常断开，检查更新应看到alpha.15，再自行下载并安装；安装及手机体验另行验收。普通手机问答仍需Android alpha.8，异步选择仍在电脑回答。

审查及恢复限制见docs/WINDOWS-ALPHA15-OTA-REVIEW.md。命令：Python -B artifacts/alpha15/publish-private-feed-local.py prepare / publish；Python -B artifacts/alpha15/verify-published-private-feed.py。审计脚本和结果仅在忽略目录artifacts/alpha15保存，不含凭据；结果private-feed-prepared.json、private-feed-publish.json、private-feed-https-verification.json。服务器审计目录/opt/codex-desktop-remote-test/operator/windows-alpha15-feed-20261014-seq11保留原索引和验证记录。

## 2026-10-14 — Windows alpha.15原密钥签名及旧客户端验包完成，待发布

用户要求进入下一步。固定Git提交0dbf345重新打包后签发sequence11；此前候选与Git差异仅换行，未签包保留，不作为正式发布输入。新包173385字节，SHA256 98f707824659ad3c67a0384f8c5ba7b830040e93d82b140f7313e560ba08cb5e；metadata SHA256 beb98d652ec155db0a1ed6d643f14661b7312161f696f578b050d89ef62dcd25，有效至北京时间2026-10-21 15:45。

原DPAPI发行密钥与现用trust完全匹配，51文件与Git归档逐字节一致；使用现装alpha.13更新代码验签/序号及临时解包清单通过，原alpha.13版本树完整。固定源码问答专项18/18通过；此前176项Node和166通过6跳过Python及跨平台CI证据继续适用。更新引擎内容不变、数据格式仍1，无迁移/权限重绑定。未写现用安装状态、未停止服务、未发布源或安装。

线上仍alpha.13/sequence9，本机ledger9；alpha.14签名序号10只在本地，故alpha.15使用11。发布审查与失败/撤回方案见docs/WINDOWS-ALPHA15-OTA-REVIEW.md。普通手机问答仍需Android alpha.8，异步题仍在电脑回答；Windows发布可独立于GitHub构件额度恢复推进。

## 2026-10-14 — 问答完成状态与候选包复核：检查通过，构件上传受阻

代码提交0dbf345：已回答/已关闭题卡保留说明，但不再触发待回答通知和输入框关注提示；未知状态仍提示核对。独立GPT-6 Sol审查确认原题revision变化会拒绝旧答案，Android持久化后才发送，超时/取消不重发。异步提问只读展示，不能写成手机选择已完成。

本机仅运行当前test目录的全部Node测试176/176（命令：PowerShell收集test/*.test.mjs，node --test @testFiles）；先前257/259计数包含artifacts中历史源码副本，不作为本轮当前源码测试数量。普通node --test的受限沙箱运行还遇到临时目录/junction权限失败；允许合成临时文件与回环端口后，当前测试目录全部通过。Python unittest discover -s test -p "test_*.py"：172项，166通过、6平台跳过。scripts/check_versions.py通过；demo_mobile_special_actions.mjs合成普通问答accepted、异步desktop_required，真实Desktop调用0。windows_ota_smoke.py实际隔离GUI/独立助手升级及失败回退均通过。

前一提交542c194的Core CI38522632230：Windows/Linux所有测试、适用平台演练和打包通过，仅构件上传因GitHub存储额度失败。Android CI38522632286：编译/JVM/lint/APK核对通过，37项模拟器中2项失败；一项旧测试仍点击已明确禁用的新建入口，现改为任务行B项目隔离、A项目不出现、入口禁用与无弹窗检查；另一项草稿恢复增加写入前后存储和实际编辑文本核对，尚待新CI判定。未因旧测试重新开放未支持动作。最终源码0dbf345的Core CI38524365265也已确认：Windows/Linux全部测试与适用演练、打包通过，仅artifact上传因相同存储额度失败。Android CI38524365342已完成：编译、JVM单元、lint、APK包内版本/备注核对以及37项模拟器测试全部通过；旧的两项失败不再复现。只有Save untrusted debug APK and reports因Artifact storage quota has been hit失败，整轮CI仍为failure。Actions API artifacts.total_count=0，没有可下载APK。未删除远端资产、修改额度或绕过签名流程。

本地未签名Windows alpha.15候选2：173997字节，SHA256 6508aede5dd9c461467a2df0ca80584bb43f56c2822d871017e428d495251ae6；51文件逐项SHA、ZIP CRC、包清单、当前源码字节一致验证通过。路径artifacts/alpha15/windows-0.1.0-alpha.15-candidate2-unsigned.zip，审计package-candidate2-audit.json。不是可覆盖现用安装的受信OTA包，没有发布/安装，不含外部Python/Node/FRP。Android CI端debug APK为0.1.0-alpha.8/versionCode8、11806368字节、SHA256 4fe6cfdfd9c8154770396d716d3a62743166ce9b9bf4ffd5075fd5b679e0a7a3；这是CI构建核对，不是本机下载复验，更不是原证书OTA包。受控签名仍需main发布流程，现有PR #9批准不扩展为本批PR #8自动合并。下一步先解决构件保存、准备本批审查与受控签名，然后再做现用服务升级和手机验收。

## 2026-10-14 — 普通手机问答闭环候选；异步仅显示并明确限制

用户批准按特殊交互方案实现。Windows alpha.15 / Android alpha.8新增普通requestUserInput卡片、选项说明、自由输入/多题、原题revision提交、持久摘要回执与completed原生答题记录核对。待处理请求不再遮断历史；手机加密草稿按账号/电脑/任务/题目版本隔离，未知状态禁止重交。未支持操作禁用并指向电脑处理。

协议只读核对固定26.1007 ASAR；同步保留requestId原类型，IPC成功不是完成证据。异步follower steer没有调用方expectedTurnId，存在跨回合误投竞态，故没有开放；只显示选项与状态。不是原方案所有交互均完成。GPT-6 Sol负责Android并独立审查原生协议/后端；已修正同题不同答案假确认及旧revision覆盖，普通聊天不能伪装已答。

本地Node257项通过；Python完整回归、Android CI编译/测试/构件仍在核验。当前未修改现用安装/身份/权限/服务或更新源，未签名发布。本机无Java，Android以CI结果为准。下一步取得构建证据并准备受控升级，真实手机答题验收尚未进行。详见docs/MOBILE-SPECIAL-INTERACTIONS.md。

## 2026-10-14 — 手机特殊交互缺口审计与合成演示

核对普通问答、异步选择框及14类特殊操作。当前Service阻断待请求历史并返回空问题列表；MobileSync丢弃agentMessage.questions；问答、审批、模式、实施路由未接通。Android已有卡片不等于当前连接器支持。GPT-6 Sol完成Android入口只读复核。

新增docs/MOBILE-SPECIAL-INTERACTIONS.md，逐项记录当前能力、手机操作方式和第一批问答闭环范围。运行node scripts/demo_mobile_special_actions.mjs，6项合成缺口全部复现，真实Desktop调用0。可点击交互演示通过选择/自由输入提交、电脑已回答、问题变化、提交结果待核对及需电脑授权五种状态检查；该演示不连接真实任务。

本轮仅审计文档、独立复现脚本与界面提案，没有修改业务源码、版本、当前安装、签名或更新源。尚未修复真实APP问答。下一步分别打通普通及异步问题的原生回答链路，再补新鲜问题核对、草稿隔离与未知回执；敏感授权不以普通消息替代。Android alpha.7构件存储问题保持待解决，与本轮交互缺口分别记录。

## 2026-10-14 — PR #9获批合并；Android alpha.7受控构建通过校验，产物上传受阻

用户明确批准独立Android PR #9测试步骤通过后合入main并沿用原证书构建，不包含更新源发布或手机安装。最新头864b35dad9980889ed45d2d07a6a1e9537576542的Core CI38514242145与Android CI38514242210均已完成：所有测试/适用检查通过，唯一失败是存储额度导致artifact上传失败。精确匹配该头合并，main合并提交69e6ecfb69bc970be94706a2d3c7b12e24460d62；Android源码/工作流与审查版本无差异。

main受控签名构建[38515183358](https://github.com/nzkres/codex-desktop-remote/actions/runs/38515183358)：原六项Secrets存在且未改动，单元测试/lint/受控APK构建、包名版本记录、原APK证书和内置trust逐字节核对全部通过，临时签名输入清理成功。受控流程不重复模拟器测试；合并前模拟器结果已通过，不能记成本轮签名run执行过模拟器。

CI生成APK为0.1.0-alpha.7、versionCode7、11773392字节、SHA256 4975c5d8f78b5766947bec847cdadb229d6a5cfc24e33ae8604d3a62aaeb852b。以上是CI端构建/校验证据，不是本机下载复验。Save verified controlled-signature test APK and reports因Artifact storage quota has been hit失败，Actions API artifacts.total_count=0；整轮结论failure，没有可下载或已取回APK，不能声称交付完成。没有删除其他资产、修改账单、绕过签名或从日志搬运二进制。

下一步先解决构件保存（恢复Actions存储额度后重新构建，或另审查私有构件归档方式），取回APK并独立校验后再准备Android更新源发布及真实覆盖安装。当前Windows仍alpha.13；本地已签Windows alpha.14保持未发布/安装。真实配对、权限、Gateway/FRP、中继源与手机未改变。PR #9已合并；PR #8仍为Windows功能开发审查。

## 2026-10-14 — alpha.14本地签名与Android构建证据；独立发布PR #9待批准

固定源码e75a20924c91a9f18090fe46f310ce169c73378c的Windows alpha.14包166779字节，SHA256 b51eaf5ebdb68825bad83a2df46d03b3b00e9b73f5c19db9529c033bd312525a。沿用原DPAPI发行密钥签名sequence10，现用trust验签、解包清单/文件树验证通过；metadata SHA256 d5e0ebf3b31cd1339100c9ecb5a874f187edcd45bfc7289da8d459d01ea95498。未发布或安装；原alpha.13运行保持。命令windows_ota_smoke.py的实际合成GUI/独立助手提交与回退通过。

Core CI 38513358592：Windows/Linux测试、平台适用GUI/helper/维护/HTTPS/cgroup检查和打包均通过；唯一失败为artifact上传，明确Artifact storage quota has been hit。Android CI 38513358589：编译、JVM单元、lint、APK版本记录核对及19项模拟器测试通过；同样仅artifact上传失败。两条整轮CI结论均failure，不能称整体CI成功或产物可下载。未删除GitHub资产或改变付费设置。

独立Android发布PR #9（基于main，仅Android/其CI/文档）已建，108份Android/工作流文件与e75a209逐一一致；本机Node33/33、Python118通过6跳过，独立CI运行中。原完整功能PR #8保持draft。现有受控APK签名main-only，已请求用户批准PR #9测试步骤通过后合并并沿用原证书构建，不包含更新源发布或手机安装；未擅自合并或绕过受控签名流程。

GPT-6 Sol复核本轮代码无阻断。网络读取有25秒超时，Android15秒等待协程的取消可能等底层读取结束，不承诺严格墙钟；不会自动重放打开意图。主电脑实机HTTP成功不是新APK手机验收。下一步处理独立CI结果、合并授权与可下载受控产物，再准备双方升级。审计文件仅本机artifacts/alpha14/package-audit.json。

## 2026-10-14 — Windows alpha.14 / Android alpha.7：点击标题加载原对话候选

用户确认手机已有多条标题，只是找不到之前报错的一条。改用已有的纯口令测试对话验证，不要求用户再找或发送消息。原生入口核对发现安装清单 codex:// 对应 app/ChatGPT.exe；同目录 Codex.exe 虽签名有效，但本次未恢复目标 owner，不能仅凭启动成功判定可用。

真实未加载测试：固定 ChatGPT.exe 及原兼容指纹后，同一 UUID 从 owner_unavailable 到历史可读、idle，模型输入0。另一个隔离鉴权HTTP测试先返回 desktop_open_pending，后续普通读取200、sameTask=true、2条条目、仅一次导航、模型输入0；未使用真实配对凭据，未修改运行服务或任务权限。

候选实现：手机显式点标题/手动重试才带 open=1；连接器只在明确 no-client-found 后请求 Desktop 打开原 UUID。列表、轮询、通知、分页、发送和OTA诊断不自动导航。相同目标合并、不同目标忙碌；断开/更新/未知回执阻止导航。未取得原生快照的打开沿用 active-tasks 记录，阻止更新猜测空闲；后续仍由原 Service 核对目录、实时权限与回执。

验证命令：`node --test` 242/242；`python -B -m unittest discover -s test -p "test_*.py"` 172项，166通过、6平台跳过；`python -B scripts/check_versions.py`通过。Android新增一次性意图与HTTP查询测试；本机 Gradle 下载缓慢且缺SDK，已停止下载，尚无本机编译/APK证据。改由仓库CI执行，并把GatewaySyncTest加入现有模拟器测试集合。CI、签名包、真实手机体验须另记。

当前实际安装仍Windows alpha.13；alpha.14/Android alpha.7未发布、未安装。手机需要新版APP与连接器配合才有按需打开能力；可能切换电脑的Desktop页面。中继转发现有查询参数即可，不需升级中继。下一步核对CI与包，再准备原签名连续升级和手机点击旧对话验收。详见docs/DESKTOP-ON-DEMAND-OPEN.md。

## 2026-10-14 — 手机按需打开原对话：验证工具完成，真实未加载路径待测

用户批准继续解决“必须电脑打开才能手机读取”。已只读核对固定Desktop 26.1007源码：存在按UUID打开原对话的原生链接，但不能从导航成功推断owner就绪。新增操作员工具scripts/desktop_open_probe.mjs，默认仅观察；显式--open只在remote no-client-found、二次目录核对和固定启动文件指纹通过后打开一次同一原任务，再验证新快照。没有接入手机HTTP/OTA，没有发送模型输入或更改现用alpha.13。

专用10项合成测试通过，最终node --test 231/231；python -B -m unittest discover -s test -p "test_*.py" 172项（166通过6跳过）。GPT-6 Sol只读审查并复核修正通过。原测试对话只读实测owner已存在、同任务历史可读、idle；不代表未打开任务的自动加载通过。实时本机alpha.13鉴权health/sessions均200、instance一致，返回62条且包含原测试与当前对话；手机展示仍待核实。

用户表示手机没找到目标；已询问是看不到其他对话还是没找到提示加载的那条。尚未真实执行--open，未生成alpha.14或宣称功能已落地。下一步定位一个未加载的现有测试对话验证；通过后仅在手机进入目标时接入，并保留后台列表/轮询及OTA诊断不自动打开的边界。详见docs/DESKTOP-ON-DEMAND-OPEN.md。现有身份、权限、连接、服务器和已签包保持。

## 2026-10-14 — alpha.13实际升级完成，手机新对话验收待测

用户确认新窗口出现。独立审计：续接助手输出 committed/success=true/version=0.1.0-alpha.13，助手已退出；实际窗口标题为“Desktop 手机联调 · 0.1.0-alpha.13”。current、staged候选、主事务候选一致；原信任公钥验证发布签名通过，发布包SHA及releaseDigest一致，当前完整文件树与可回退alpha.12槽均验证通过。

当前identity/configuration/taskData摘要逐项与升级前review及事务preserved一致，未重新配对或改变权限。此证据确认已签版本安装、原事务健康门禁及新窗口激活完成；不是手机任务/长时间重连验收，也不是无人维护的完整OTA体验通过。本次正常OTA在切换前任务预检退出后，经已审查的一次性助手复用原暂存包续接；错误窗口缺失仍是后续版本待修体验问题。

只读审计复用installed、verify、slot、snapshot，脱敏结果为artifacts/alpha13/installation-audit.json，所有比较通过。无产品源码修改，不重复打包或修改已发布alpha.13。下一步：用户检查“错误清单与代码说明”，连接后在Desktop新建专用测试对话，手机刷新自动出现标题，点开读取ALPHA13_NEW_CHAT_OK；不逐条添加或重新配对。手机结果待用户回报。

## 2026-10-14 — alpha.13续接助手已启动，等待本轮对话空闲

用户已收到原测试任务 ALPHA13_UPDATE_READY；实时只读检查原任务 idle=true，当前维护对话 thread_busy。保留已暂存、验签的 alpha.13，现用仍 alpha.12。独立续接助手于11:40:31（北京时间）启动，首次输出 waitingTasksIdle=true / switched=false；此记录不是安装成功。

助手仅对明确 thread_busy 有界等待15分钟，其他异常停止；冻结安装文件、原身份/config/taskData摘要与助手/审查哈希，检查原已签源码和候选完整性。空闲后调用未修改的原 Transaction.apply，再执行原生预检、保全、健康和回退流程；不改权限或配对、不跳过健康检查。独占且持久的一次性执行标记防止失败后自动重试交易。原 helper-result 和拒绝记录保留。

命令：`python -B artifacts/alpha13/test-resume-staged.local.py`，7/7隔离测试通过（busy→idle、异常拒绝、超时、状态变化、失败不重试、错误版本、独占标记）；GPT-6 Sol只读复核通过。通过隐藏独立Python进程启动 `resume-staged.local.py <review-sha256>`；固定review SHA256 e931193dc042ca9f8ccad84ddd2d69dbabcad444c30b20a2964de4a00505c49e。一次性脚本与审查数据仅在本机ignored artifacts/alpha13，未加入发行包或Git。

下一步：本轮结束后等待助手安装；再独立核对current、事务committed、签名、身份和权限保全及实际窗口，最后由用户测试错误清单与手机新对话标题/历史。Android、服务器和已签alpha.13字节未改。不得以等待器启动代替实机安装/手机验收。脱敏启动记录：artifacts/alpha13/resume-launch-evidence.json。

## 2026-10-14 — alpha.13用户点击安装后停在切换前任务预检

用户反馈下载并安装后未出现alpha.13窗口。只读核验：current仍alpha.12，staged候选alpha.13；helper-result为active_or_unverified_desktop_task。primary仍是此前alpha.12 committed，未进入本次prepared/切换/候选启动。应用、更新、runtime三锁可得，身份/任务保全快照可读取，没有重新配对或删除保护。

调用现用alpha.12的只读原任务检查，分别得到原测试任务owner_unavailable、当前维护对话thread_busy；全量原升级检查因owner_unavailable拒绝。前者表示Desktop暂未提供原测试任务执行端，后者是本轮助手正在回复。下载与暂存成功不能当升级成功。GUI先关闭、助手预检失败后没有重新呈现错误窗口，是现有交接体验缺口，后续产品修复需独立版本，不能修改已签alpha.13。

已请用户在原“回复 DESKTOP_REMOTE_READY”发送一次ALPHA13_UPDATE_READY并回报；保持连接器关闭，不重复下载。**尚未启动任何续接等待器或再次apply；收到用户确认后重新核验现场，待当前回合空闲后复用原已验签暂存包继续。** 脱敏证据artifacts/alpha13/installation-preflight-refusal.json；未变更身份、权限、服务器或安装指针。

## 2026-10-14 — alpha.13签名包与私有源已发布，等待用户安装

固定源码8191cad32af62a5902a72e0eea44bc94bebb49f1构建Windows alpha.13，sequence9，163147字节，ZIP SHA256 fecbcfbc092a19d30840a25dc8d31066e4fcadc92c2bb013b382fdab721e79a1；metadata SHA256 d7e682bc7d06b0e3af81cc5c8c3df749e58c0c42ef037820ec39e4d63cfafeda。原DPAPI发布密钥签名、本机真实trust验签、解包manifest及包内211条源码/仓库位置检查通过。初次审核脚本误将verify的二元返回值直接交unpack，在审核层被拒；按正确返回值复验通过，未重新打包/签名或更改安装。

[Core CI 38505660082](https://github.com/nzkres/codex-desktop-remote/actions/runs/38505660082)的Windows/Linux测试、真实助手/维护/HTTPS/cgroup适用步骤和打包均通过；**整轮结论failure，唯一失败步骤为两平台actions/upload-artifact@v4，不能称CI成功或产物可下载。** 本机实际GUI/helper提交与回退通过。

按既有Windows私有源授权，服务端对原alpha.7 latest SHA精确比较并备份，先落不可变alpha.13包，服务器用原信任公钥再验签后原子发布latest；未改Android/relay feed、未重启服务。公网正常TLS匿名metadata/ZIP均401。现用连接仍alpha.12；在连接活跃时下载预检被原identity_or_runtime_requires_review门禁拒绝，未绕过，未执行check/stage或写真实升级账本。因此现设备认证下载与实际安装待用户安全断开后由原GUI执行。

已提示用户等本轮结束，在alpha.12点击断开，确认安全断开，再检查更新/下载并安装alpha.13；Desktop保留，不重配对。之后确认新窗口错误清单入口，并继续手机新标题/历史验收。不要声称现用已升级；不要另起维护助手或删除运行保护。脱敏审计均在artifacts/alpha13/，发布源码已推送原开发分支，PR#8仍draft未合并。

## 2026-10-14 — 自动重试根因已修复；alpha.13错误清单候选

现用alpha.12自动重试确认为9443仍提供过期证书：磁盘新证已存在，专用Caddy hook相同配置未强制重载。已在既有隔离部署授权内经3项合成测试和原hook精确比较，补--force并重载专用入口。正常TLS200、证书新指纹匹配；原连接自行恢复connected/lastError=null，未重配对或重启Desktop/FRPS。手机新对话仍待验收。

alpha.13新增当前/最近错误、211项可搜索代码手册、点击原因/处理步骤/源码位置、复制安全说明与纯演示。Python172项（166通过6跳过）、Node221项、定向32项、实际合成Tk点击/高DPI验证通过。**当前安装仍alpha.12；候选未切换，签名包/CI另记。** [证据与使用说明](docs/WINDOWS-ALPHA13-DIAGNOSTICS.md)。

## 2026-10-14 — alpha.12实际安装提交并独立核验通过，等待手机新对话验收

用户确认新窗口已出现。续接实际输出committed/success=true/version=0.1.0-alpha.12；独立审计current=候选、primary与alpha12-continuation1日志一致，ZIP/发布签名/包清单与固定候选匹配，原身份/config/taskData及保护摘要与续接review和事务preserved完全一致。旧槽可验证、原失败journal精确保留，配置仅增加获批taskScope=all-local，旧权限摘要/任务记录保留；runtimeNeedsReview已正常移除，应用锁由新实例持有。

实际窗口PID4728、标题Desktop 手机联调 · 0.1.0-alpha.12；脱敏完整审计artifacts/alpha12/installed-alpha12-audit.json。这证明本地签名维护安装与激活前真实健康验证完成，不等于长期稳定或手机新对话验收。首次健康拒绝的具体诱因仍未证实，保留原失败及成功续接，不将一次复验成功写成根因已修复。

已请求用户在新窗口点击连接，在Desktop新建专用测试对话并发送一次ALPHA12_NEW_CHAT_OK验收指令，再在手机刷新原电脑任务列表、点进查看同一回复。无需添加任务或重新配对。等待用户分别确认新标题自动显示与历史读取；暂不重复运行任何维护助手。本次未升级Android/relay或发布公网feed。

## 2026-10-14 — 首次alpha.12候选健康拒绝；传输诊断通过，受控续接已等待

用户未看到新窗口。助手实际结果update_needs_review；primary与alpha12-repair-transaction均needs_review，cause=candidate_health_unverified、error=rollback_or_cleanup_unconfirmed。原引擎在cleanup_target和preserved成功后把current指针恢复alpha.11；回退启动因旧版不理解all-local被明确拒绝，故此汇总错误不等于已证明清理失败。候选从未激活。配置已为批准的all-local，不能手动启动旧版。原运行guard已按正常清理移除，三锁可得；身份/config/taskData与失败事务preserved完全一致，5条回执文件未变。原失败时间01:09:55（本地），诊断未留具体健康异常，不能把87秒时长当确定网络根因。

一次受锁保护、真实已配对但quarantine的传输诊断通过：登记connected、本机gateway healthy、transport healthy、probe_verified；随后正常停止、自有两棵Job树退出、身份/任务保全及旧指针/主fence一致。未开放手机写入或GUI。脱敏证据artifacts/alpha12/transport-diagnostic.txt。首次异常的具体诱因仍未证实。

新一次性续接continue-once.local.py保留原failed journal不改，复用同一个签名包与原事务引擎，另写alpha12-continuation1.json；不重复prepare/resume，不再改变配置/身份。候选诊断入口仅调用原签名ota_entry.run(home,True)，保留全部健康检查，新增安全错误码回报。7项合成事务测试通过（含审查记录变化、身份/数据变化、重复应用拒绝、失败闭锁）；真实隔离GUI健康提交/坏候选清理停needs_review通过。GPT-6 Sol medium独立复核通过。

真实只读续接review已保存：%LOCALAPPDATA%/CodexDesktopRemoteMaintenance/alpha12/continuation1-review.json，SHA256 1a8e4075afa5c0061854b167438ec6819f1fd46c5114b492475f5fb0f74ac70c。助手SHA256 e79eba04c83991733f4532d8d51528cfae00d104f1db201c18fa80a74fe90428；诊断entry 53c2c86b9422dc15ded9b2177c83048723a64021598353648dbd7e8c16842033。入口核对review SHA，等待/快照/启动/激活前持续核对完整审查语义（纯JSON空白变化允许，不采用新字段）。

独立续接已启动PID3164，实际输出waitingTasksIdle=true,retry=1,switched=false；仍等当前维护对话结束，不是已安装成功。日志artifacts/alpha12/continuation-output.txt、continuation-error.txt。下一轮先检查这些日志及新事务和current，再独立核对保全/签名/窗口，禁止启动第二份助手。用户无需再次重启或配对。

## 2026-10-14 — 独立恢复助手已启动，等待本轮对话空闲

GPT-6 Sol medium只读复核等待器通过。固定SHA一致后已启动独立助手PID15156，实际输出waitingTasksIdle=true、switched=false；标准错误为空。它将在当前维护回合结束、两条任务明确空闲且完整CAS不变后执行原已审查resume；无需再次批准或重启。此时尚未确认安装提交，不得重复启动助手/运行resume或提前打开旧连接器。

助手输出位于artifacts/alpha12/resume-wait-output.txt，错误位于resume-wait-error.txt。下一轮先检查输出、primary与alpha12-repair-transaction双记录、current指针、签名、scope-only配置、原身份/任务数据和旧槽归档，再检查新窗口；不能仅凭出现窗口宣布升级或手机验收成功。用户看到alpha.12窗口后先回报，未出现或报错直接报告，不重配对。

## 2026-10-14 — alpha.12重启后保全核对通过，原测试任务已恢复

用户确认已完整重启；只读审计确认boot严格增加、原review SHA-256仍189b383b0bb2683f24d6c83dde4732a0bdf67ecc1b194b079ab2977ea22d5614，primary围栏、完整身份/配置/保护文件/任务数据及代码指纹一致，三锁可得，guard保留，候选签名有效。脱敏证据artifacts/alpha12/post-restart-audit.json。未重复prepare，未清保护。

原测试任务最初owner_unavailable；用户随后在原任务收到ALPHA12_AFTER_RESTART_READY。只读独立诊断两任务兼容/同任务/权限/回执均通过，原测试任务idle、当前维护对话thread_busy；首次原生快照历史标为未完整，后续完整历史联合探针补取成功后返回thread_busy。不能把首份诊断incomplete_history隐藏为始终成功。

为等待当前维护回合结束新增本机独立等待器artifacts/alpha12/resume-when-idle.local.py（SHA-256 84a965492764c27744d23019cd649e3b3f670c0841ef0160a610cf52ab73309f）；探针wait-idle.mjs（28fda2b74b5a63a608018d26f4a1e0e8bed823d8e737e957afe56483474d11d7）。不改原恢复助手或review；循环前后复核全部状态，只等待明确thread_busy，15分钟超时退出，其他错误立即拒绝；idle后仅调用原resume一次。6项合成等待门禁测试通过。此刻尚未启动等待器或执行真实安装，完成独立复核后启动，另记结果。

## 2026-10-13 — alpha.12重启前恢复记录已保存，等待用户完整重启

用户已确认正常关闭alpha.11窗口。执行固定SHA-256为4316f8d8c116678d4a8139154d3fae2e1bf55fadc1b765bed7ab5b4d47aeffea的本机助手 `artifacts/alpha12/repair.local.py prepare` 成功，返回prepared=true、restartRequired=true、configurationUnchanged=true。没有执行resume，没有停止Desktop或其他服务。

随后独立只读审计：application/update/runtime三锁均可取得；primary精确匹配准备围栏；完整身份摘要、保护文件清单、任务数据、配置及代码指纹未变；runtimeNeedsReview保留；签名再验通过；当前仍alpha.11、同一boot，未切换版本。脱敏证据 `artifacts/alpha12/prepared-audit.json`。仓库外恢复记录 `%LOCALAPPDATA%/CodexDesktopRemoteMaintenance/alpha12/review.json` 的SHA-256为189b383b0bb2683f24d6c83dde4732a0bdf67ecc1b194b079ab2977ea22d5614。

现在可以通知用户保存其他工作并通过Windows菜单完整“重启”，不是休眠。重启后保持连接器关闭，先回到本对话回复；复核boot严格增加和全部CAS后，再检查原任务就绪。当前维护回合执行时不安装，需待原两任务空闲。不得重复prepare、清标记、重配对或直接启动旧版。下一步是审查记录对应的resume，具体步骤见docs/WINDOWS-ALPHA12-AUTOMATIC-CONVERSATIONS.md；若任何状态变化停止复核，不覆盖。

## 2026-10-13 — alpha.12双平台CI全部通过，产物可下载；安装仍待关窗

[Core CI38420879205](https://github.com/nzkres/codex-desktop-remote/actions/runs/38420879205) Windows/Linux两任务全部成功，包括单元测试、版本目录、relay/deploy检查、平台对应真实隔离升级演练、不可变源码包构建和上传。此次CI临时源码产物可下载；本机签名包仍单独验签，不能互相替代。现用alpha.11窗口仍在，本次尚未执行prepare、未建立重启围栏、未切换版本；待用户正常关闭窗口后继续。不要提前重启或清除runtimeNeedsReview。

## 2026-10-13 — alpha.12签名包及恢复助手已就绪，等待正常关窗

源码a3b85a9；本机签名候选147470字节、48文件逐一匹配Git、sequence8，现用trust验签通过。独立GPT-6 Sol medium复核源码及一次性恢复助手，无剩余实质阻断。恢复助手18项合成测试通过，真实隔离GUI提交/失败清理后needs_review通过。完整路径、固定哈希、恢复中断边界及手机步骤见[alpha.12维护与验收](docs/WINDOWS-ALPHA12-AUTOMATIC-CONVERSATIONS.md)。

真实安装尚未修改；已请用户正常关闭alpha.11，先不要重启。下一步锁齐后prepare保留guard并建立主事务围栏，再通知完整Windows重启。不能直接运行resume，不能把本机候选当已安装。CI38420879205目前Linux全套含上传通过、Windows单元套件通过且GUI演练进行中；尚不能写全CI成功。草稿PR#8已推送，未合并、未发布公网feed。

## 2026-10-13 — alpha.12 全部对话候选，准备签名及受控恢复

本段取代上一增量中的开发计数及等待更新方案表述。用户继续要求完成测试、打包并升级，手机将验收新对话自动标题显示和点进读取记录。实现采用目录元数据与实际手机操作分开：未打开的历史标题不创建 follower，不要求它具有执行端；迁移前两条任务仍保留为更新基线，手机读到非空闲或实际发送/停止的任务持久跟踪。忙碌、待审批、未知发送/停止以及正在读取时仍延期更新；不停止 Desktop 任务。

已实现：只读正常 WAL 的本机目录、按需同任务历史、实时 Desktop 权限继承、有限 follower 缓存、明确空历史/不可读错误；未知停止在 IPC 之前落盘。补全历史失败、待审批显示失败、并发读与断开竞争均不能遗忘可信活动快照。已由 GPT-6 Sol medium 独立复核，未见剩余阻断。

验证：当前源 Node 137/137；默认 node --test 221/221（含84条忽略目录历史测试，不能作为当前源计数）；Python全量161项、155通过、6平台跳过；真实隔离Windows GUI/独立助手提交与回退演练通过。版本目录检查通过。候选只读实机读取62条标题、当前对话83回合，无模型输入；不代替手机验收。

现用 alpha.11 保留 runtimeNeedsReview。只读检查5条已接受发送回执、0未知，runtime锁可取得，但没有旧匿名Job原始句柄退出证明；transportCrashContained布尔值与进程列表不能替代证明。需要先准备完整身份/配置/任务数据CAS及签名包，再正常关闭窗口并建立重启恢复围栏；完整重启后才允许复核恢复。此刻未清标记、未改真实配置、未安装alpha.12。Android、relay、公网feed保持本次范围之外。签名、安装及手机验收继续分别记录。

## 2026-10-13 — 全部对话自动标题目录与按需历史（开发中）

验证增量：当前 Node 源测试123/123通过，包含列表不订阅历史、单任务按需读取、确实空历史与不可读/忙碌/未知提交区分、目录移除释放连接。Python全量160项首轮出现2项旧配置入口兼容失败（6跳过）；已修复并定向复验配置5/5、目录3/3，尚未重跑完整Python套件，不能称全量全绿。版本目录只读校验通过。新代码仍为本地开发中，不是已发布/已安装版本。

用户已批准手机查看/接续全部本机 Desktop 对话、新对话自动同步，并要求列表先显示标题，点进后再获取旧记录。当前开发分支 codex/automatic-desktop-conversations 已实现只读 SQLite WAL 元数据目录、按需原生 follower、任务实时权限继承和有限连接缓存；旧配置缺省仍 selected，现用 alpha.11 没有切换。目录不读取正文、不调用模型；暂不可读不能当成空历史。完整且明确空闲的原生快照确无回合时返回 history_empty，保留手机既有重试入口；没有 owner/快照或状态未定时仍分别报告原错误。

本机只读候选发现62条目录记录，两条既有获批任务均包含；当前开发对话投影成功（83回合，无模型输入、无现用配置修改）。发现并修复 Windows 扩展路径写法导致范围核对失败，保留真实路径及规范路径双重比较。自动目录包含可识别 Desktop 来源及旧版未标注 originator 的 vscode 候选；实际读取/发送还需本机 Desktop owner 和同一原任务目录验证。网络路径、远端目录、子代理/内部审查不作为本机交互任务。

OTA 规则调整尚未获明确答复：此前已说明“只检查手机操作中的任务而非全部历史”的原因，用户目前只明确标题/按需获取交互，不能推定同意修改更新门禁。全模式更新不得把没有执行端的历史任务当空闲。签名打包、现用安装迁移和手机全目录验收尚未完成，不发布、不替换现用 alpha.11；继续完成并记录更新检查方案后再交付候选。

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

已请求用户批准从alpha.10维护升级并新增当前对话。现用原任务和身份保持，助手尚未执行apply；手机/中继/feed不改。候选失败将确认自有树退出、恢复旧指针并停待复核，原配置与旧槽归档，不把旧版忽略第二任务当成功回退。完整固定哈希、命令入口、审查时限及恢复边界见 [alpha.11切换记录](docs/WINDOWS-ALPHA11-MULTITASK.md)。

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

Core PR CI [38368132481](https://github.com/nzkres/codex-desktop-remote/actions/runs/38368132481) 两平台测试、隔离演练及打包成功，但上传均明确报 Artifact storage quota has been hit，整体失败，无本次 CI 可下载产物。源码产品输入仍 11bbbb3a8e98ae041306af8f939a1858940ca696。详细故障、签名和恢复边界见 [alpha.10 维护记录](docs/WINDOWS-ALPHA10-MAINTENANCE.md)。

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

## 2026-10-13 — alpha.9 候选就绪，真实安装尚未切换

- 功能代码已在 `codex/simple-reconnect-alpha9`、草稿 PR #6；Windows alpha.9 / Android alpha.6 / relay alpha.6 的用户体验说明均已入库。程序候选输入固定 `2a783a06cac7ff0f4fe527280a92af031ca28cac`，后续仅修复 Android CI 环境。
- Windows/relay 最终本地签名包分别 132648 / 84517 字节、sequence 5 / 2，43 / 29 个文件逐字节匹配 Git blob；使用现用信任和账本只读验签通过。完整哈希见 [本轮记录](docs/RECONNECT-AND-OTA-20261013.md)。未发布或安装。
- Core CI 38343263687 两平台测试、真实隔离演练和打包成功，但上传因账号 artifact 配额失败，整体失败，没有本轮 CI 下载产物。Android 最终 e77079a 的 [CI 38345972326](https://github.com/nzkres/codex-desktop-remote/actions/runs/38345972326) 已实际启动 API35 AOSP 模拟器并完成 14/14 界面仪器测试，单测/lint/APK/包内版本检查均成功；同样仅上传因配额失败，整体失败。受控签名 alpha.6 APK 和手机覆盖安装尚未完成。
- 真实 Windows 仍 alpha.8：原身份存在，主事务 committed，账本 4；但 `runtimeNeedsReview=true`，普通 OTA 前检拒绝。原任务打开历史后只读诊断仍 `owner_unavailable`，Desktop 任务工具另报 `notLoaded`，最近完成内容仍为旧的 REMOTE_AFTER_RESTART_READY。已请求用户在原任务仅发送一次 ALPHA9_PREFLIGHT_READY，尚未确认，不自动补发。
- 已准备只在本机忽略目录中的一次性维护候选；新 Windows 启动、完整身份/配置/任务 CAS、旧/新探针和原签名必须全部满足。没有执行真实 prepare/resume，也没有清保护、改授权或重配对。真实维护前仍需具体切换确认。
- 服务器只读核验仍 relay alpha.5、宿主 healthy/ready；Windows feed alpha.7，Android/relay feed alpha.5，正常 HTTPS 匿名六项请求均 401。未发布新 feed 或切换服务。
- 26 个旧未签名 Android debug artifact 已完整备份并逐包校验，共 296503472 字节；等待用户批准精确清单后才删除远端，不清最新 debug、受控签名包、Release 或其他仓库。额度刷新不是即时生效。

以下为历史阶段记录，以本段和本轮专项记录为当前状态。

## 2026-10-13 — P0–P3 实现，P4/P5 候选验收中

当前开发目录为 C:\code\my-code\codex-desktop-remote，分支 codex/simple-reconnect-alpha9。已有学习注释已单独保存为 8f61adc，不覆盖旧工作树。Windows alpha.9 修复断网时主动退出的本机核验，明确失联后重新发现 Desktop 原任务，完善分层状态/再次连接提示；完整身份、权限、未知回执、原句柄树清理与异常现场保护保留。补充转发前快照变化竞态和未核权限加载历史的回归。Android alpha.6 候选整合原 PR #4；旧 CI 确认为 ADB 等待超时，新增模拟器启动诊断，仍需新 CI 证明。relay alpha.6 仅作为连续 OTA 演练候选，不改 API 或数据库结构。

本机主代理复验：Node 84/84；Python 157 项中 151 通过、6 平台跳过；Windows relay 77 项中 73 通过、4 平台跳过；WSL relay 77/77。Windows 实际合成 GUI/helper 升级与回退、六种维护提交/闭锁、私有 HTTPS GUI 提交/回退通过；WSL 合成 cgroup 升级、坏候选回退、四棵自有树退出通过，临时单元已回收。Tk 合成窗口和版本说明截图已核对。版本历史校验通过。日志在忽略目录 artifacts/alpha9；不含真实身份。具体实现/验收见 docs/RECONNECT-AND-OTA-20261013.md。

本轮只读核对现用 Windows 仍 alpha.8、transaction committed/success、ledger4，Desktop 26.1007.2314.0；未观察到连接器可见窗口，不能据此断言实时通道健康。未升级现用安装、未修改原身份/权限、未部署 feed 或中继；手机本轮未验收。候选签名、CI 和真实三端切换分别追加结果，不把代码或合成测试当实机成功。

# 当前验证状态：alpha.8 已安装，安卓原任务恢复

## 2026-10-12 — alpha.8 已安装，安卓原任务恢复验收通过

用户明确批准按当前原任务设置重新确认连接器权限记录后，已完成本机签名 alpha.7→alpha.8 维护升级，事务 committed/success、sequence4。原 DPAPI 身份、任务文件及旧版本保留；仅应用已批准的 Desktop 路径和权限摘要变化。Desktop 设置未修改，仍限定原任务、网络关闭，没有重新配对。

独立安装复核：签名与槽有效、原身份/任务文件保持、批准配置匹配、应用锁持有；新鲜任务探针 idle/permissionsVerified/receiptsCertain 均通过，实际新 GUI 为 alpha.8。证据在忽略目录 artifacts/desktop1007/installed-alpha8-audit.json。用户随后点击连接，并在安卓原任务看到既有 REMOTE_AFTER_RESTART_READY，明确回复“能看到，原任务已恢复”。这是手机实际读取验收；本轮未发送新的手机/电脑双向接续消息，不将历史读取等同于新消息执行验收。

本次是本地签名维护升级，不是 alpha.8 公网 OTA；没有更新 Android、中继或私有 feed。CI 上传曾因存储配额失败，无本轮可下载 CI 包。下方所有“待安装/待确认/未恢复”是历史阶段，由本段取代。下一步保持当前连接供用户使用；不要重跑维护或重复标记。权限表示再次变化时仍需诊断，不声称已完成通用自动重连。

## 2026-10-12 — 已重启，仍未安装；当前权限记录待用户确认

真实完整启动时间为2026-10-12 01:25:15北京时间，晚于prepare记录。verify-prepared复验原身份/配置/任务/信任/账本/current与围栏完全一致。用户完成REMOTE_AFTER_RESTART_READY后已可读取原任务；但新鲜快照恢复为auto_review、sandbox写根1项/tmp排除true，而不是重启前通过旧摘要的guardian_subagent、写根2项/tmp排除false。最新turn_context仍记录后者的有效sandbox形式；源码追踪不足以证明两种表示完全等价，未放宽匹配或修改旧摘要。

只读review-current.local.mjs核对原任务/既定目录、网络关闭、idle与回执通过，在忽略目录生成permission-review.json及reviewed-fixture.local.json，**未应用**。已向用户请求明确同意按Desktop当前设置重新确认连接器权限记录；不得将之前“修好连接”的笼统授权当作此次确认。没有执行repair-once resume，现用仍alpha.7且保持围栏。若用户批准，需先修订/审查本机维护计划（保留旧review/boot证明/原摘要），不能直接执行此前要求原摘要保持不变的脚本；无需再让用户重启或重复发标记。

## 2026-10-12 — 原权限通过，重启准备已完成，等待用户重启

用户完成REMOTE_RECONNECT_READY后，实机只读确认completed/sameTask/permissionsPreserved/idle/receiptsCertain全部通过，**无需更换权限摘要**。用户已正常关闭alpha.7 GUI。一次性本机维护脚本7项合成测试通过，独立代码复核后执行prepare成功，主transaction已写入needs_review/one_time_repair_waiting_restart，保留原committed记录；这是维护围栏，不是已升级或新故障。原runtimeNeedsReview仍true。

随后独立复核原DPAPI身份、配置、任务文件、trust/ledger/current原字节均保持，现用仍alpha.7；仅准备审查记录与主围栏，未安装alpha.8、未启动进程或改变Desktop授权。忽略目录prepared-audit.json记录review摘要 `a85cd245b4e631ef5b80d85a98e0b244d96af648f8fa3071824fa23e4cac6970` 和一次性脚本摘要 `4d8014b72c68668e43cdc84a30799eb38e326d366417451d69e8c4dbb53ed131`。用户接下来手动完整重启Windows，打开Desktop原任务后告知；不要手动启动旧连接器。后续仅在新boot、原数据CAS和新鲜任务检查全部通过后执行resume，详见HANDOFF。

## 2026-10-12 — 简化为本次维护，候选已签名，未切换

用户要求保留原身份/任务，先修好安卓连接，停止增加通用恢复框架。已移除未接入的恢复证据模块及其测试，仅保留精确版本识别与分项只读诊断。实机安装清单完整、原身份存在、回执2条accepted、原事务committed；新鲜诊断中任务空闲且回执确定，权限不匹配。后续快照又出现profile=null的推断状态，一次性审查及时拒绝，未写安装配置。已请用户在原任务完成REMOTE_RECONNECT_READY，不需重复配对；尚未要求关闭窗口或重启。

本地alpha.8新候选96,115字节，SHA256 `e9ae53cced437131a8220ec461d00978f1555cfd8245329c5feac2430d1225d8`；使用既有测试密钥签发sequence4，现用trust/ledger验签通过，metadata SHA256 `1baa44100e1aa6d8ddaf7f8e1bb744fc3da7aab53d014057038f9cb80d920ba7`。只在忽略目录artifacts/desktop1007，未发布、未安装、手机仍待恢复。此包取代此前未签名ZIP作为本次候选，不能称CI产物。证据与睡眠时间线见[专项记录](docs/DESKTOP-26-1007-RECOVERY.md)。

## 2026-10-11 — 用户标记已完成，仍未授权重连

原任务`DESKTOP_1007_READY`已由只读IPC确认completed，不需重复发送。兼容与同任务检查通过，权限检查仍为permissions_changed。已将差异定位至sandbox写入根与两个临时目录标志；历史执行记录与旧摘要可匹配，但不能替代实时授权。原身份、配置和runtimeNeedsReview保持；未安装alpha.8或恢复连接。后续需完成权限语义核对和受控冷恢复实现/隔离验证，再提交具体切换清单。

CI更新：Windows/Linux测试及打包步骤通过，最终上传因GitHub Actions存储配额满失败；整体CI失败，未产生本轮可下载产物。详见[审查记录](docs/DESKTOP-26-1007-RECOVERY.md)。下方“等待标记”和“CI待核对”为此前状态，由本段取代。

## 2026-10-11 — 新 Desktop 精确兼容候选，未恢复连接

实际alpha.7仍已配对、上次OTA committed；当前runtimeNeedsReview=true，旧26.930路径消失，系统为26.1007.2314.0。未清标志、重配对、停止进程或改权限。独立分支codex/desktop-1007-recovery源自515313d，Android alpha.6工作树保留。

alpha.8候选增加新版精确双指纹并保留旧pin；本机asar关键协议表已只读比对。实机候选读取原测试任务及marker通过，但permissions_changed阻断，不是连接成功。新增scripts/diagnose_desktop.mjs只读阶段诊断，成功也不授权冷恢复。Node70通过、版本校验通过；其他测试/构建后续记录。恢复证据缺口见[专项审查](docs/DESKTOP-26-1007-RECOVERY.md)。用户已打开任务，等待DESKTOP_1007_READY后复验。

以下10月8日是当时验收基线，不代表10月11日仍在线。未签发/安装alpha.8、未发布feed、未捎带Android部署。

本机最终Node70通过；Python152项中146通过/6平台跳过；版本历史与diff校验通过。未签名源码ZIP95,914字节，SHA-256 `e054754f8eba28afe6ef60762bdd295b9aeec1ae5b61252f1a2435881f744932`，打包器解包/清单自检通过（首次沙箱pack失败，正常用户环境重跑成功）。它不是可安装的已签OTA包。最终诊断仍为compatible=true、sameTask=true、taskReady=false、permissions_changed，reconnectAuthorized=false。CI待远端运行核对。

## 2026-10-08 — 用户点击下载并安装，alpha.6→alpha.7 实际事务完成

用户明确回报已点击“下载并安装”。只读核对实际current为alpha.7、ledger3、主事务`committed/success=true`，old为alpha.6；staged原签名metadata、releaseDigest、目标槽和95,085字节原发布包绑定全部一致，实际暂存ZIP重新验SHA通过。不是仅下载或仅准备记录。

独立验收通过：签名alpha.7槽有效、原alpha.6槽完整保留；重新计算完整稳定身份、配置/权限和任务目录逐文件摘要，与事务preserved完全相同；应用锁持有。唯一实际新GUI标题为「Desktop 手机联调 · 0.1.0-alpha.7」、窗口句柄有效，准确签名槽路径及可信child_gate父子归属一致。当前真实Desktop只读探针`idle=true/permissionsVerified=true/receiptsCertain=true/version=alpha.7`，自动模型输入0；核验前后安装及事务记录原字节未变。

脱敏证据位于忽略目录`artifacts/alpha7-preparation-20261008/final/installed-alpha7-verification.json`和`installed-alpha7-gui.json`。**Windows真实GUI升级及升级后手机/电脑同任务双向接续均已通过。** 用户按要求重新连接，于手机发送PHONE_ALPHA7_OK并在Desktop同一任务接续DESKTOP_ALPHA7_CONTINUE_OK后，明确回复“手机两条回复都能看到”。这一层证据来自用户实机确认，与自动事务/探针核验分开记录；不要重发这两条已完成消息。Android/relay仍alpha.5，无本轮更新或服务切换。

下方“本机仍alpha.6/安装未执行”均为此前发布阶段记录，已由本段实际安装结果取代。

## 2026-10-08 — 已批准并完成 Windows 私有源发布

用户明确批准“发布 Windows 私有更新源”，本轮只发布，不启动本机安装。已在原隔离服务器Windows目录落位原签名alpha.7包并原子切换`latest.json`至sequence3；服务端签名/95,085字节/SHA/43源文件清单全部通过。旧alpha.5包及原索引审计副本保留；Android/relay feed逐文件摘要、relay当前槽、旧Caddy配置与测试relay/FRPS进程身份均保持，未重启服务。固定包及metadata哈希如下方候选记录，未重新打包或签发。

主电脑正常TLS公网验收通过：metadata和ZIP匿名均401；**已有隔离测试管理员临时会话**取得alpha.7索引并下载完整包，RSA/平台/兼容档/序号/大小/SHA和清单通过；会话注销后再次访问被拒。验证前后本机install/current/trust/ledger/transaction原字节相同，实际仍alpha.6/ledger2，未调用check/stage/apply。

**设备端真实下载与在线安装尚未验收。** 原设备更新鉴权返回`identity_or_runtime_requires_review`；连接核心运行期间会设置该保护，正常断开并证明自有树退出后才清除。没有绕过、删除该标记或停止连接，改用上述账号完成本次源发布验收。下一步是用户准备好后安排本机正常断开、设备鉴权检查及alpha.6→alpha.7 GUI在线升级，再核对同一Desktop任务。不能把本次账号下载当作设备OTA成功。

本机脱敏报告：忽略目录`artifacts/alpha7-preparation-20261008/final/feed-preflight.json`、`feed-published.json`、`feed-server-verification.json`、`feed-https-after.json`。服务器root-only审计：`/opt/codex-desktop-remote-test/operator/windows-alpha7-feed-20261008/`，包含旧/新索引、固定包、前后快照；没有发布私钥或身份。alpha.7仍未创建GitHub Release、PR #3未合并；私有feed已可下载，不依赖14天CI artifact。

## 以下为候选准备记录（发布停点已由上方取代）

2026-10-08用户同意按“Windows alpha.6→alpha.7，然后 Android，再 relay”顺序推进。此次先完成源码、测试、签名候选和发布审查；实际私有feed发布和当前电脑在线升级仍为准备完成后的确认步骤。新独立工作树/分支`codex/windows-alpha7`基于已验收的`421a4c3`，原PR #2与已安装alpha.6保留。

alpha.7只修配对失败显示：核心已记录的connection_unavailable不再被GUI忽略，提交结果与重开状态提示一致、保留原申请；白名单输入/响应错误有明确原因，未知错误不回显。没有改身份/协议/任务权限或运行保护。Node64通过；最终完整Python152项146通过/6跳过，合成Tk错误预览/后续状态恢复通过，实际截图已检查。新增实际回环TLS、设备鉴权、GUI worker/独立助手/Job演练由根代理复验：正常committed alpha.7与坏候选rolled_back alpha.6均通过，合成身份/任务保持，匿名metadata/ZIP拒绝、旧staged/candidate替换及旧maintenance记录保留均核对。Desktop/FRP健康是临时合成探针，两份夹具源取自当前checkout，不冒充真实alpha.6发布包或实机验收。

最终代码`c20c2036e6319f9dca1ef108f7e8830f893c7f89`的[push37781824455](https://github.com/nzkres/codex-desktop-remote/actions/runs/37781824455)和[PR37781829558](https://github.com/nzkres/codex-desktop-remote/actions/runs/37781829558)均Windows/Linux成功，六种维护与真实合成HTTPS提交/回退通过。首轮PR旧维护夹具的即时Job空断言失败及修正过程保留在WORKLOG；产品树检查未放宽。[PR #3](https://github.com/nzkres/codex-desktop-remote/pull/3)为叠加在PR #2上的草稿，main未合并。

Linux CI的Windows包已逐43文件核对Git；与实际已签alpha.6相比仅GUI和版本目录两源文件变化。已用原项目DPAPI密钥在本机签发sequence3，并按现用安装trust/ledger只读复验。ZIP **95,085字节**、SHA256 `91676bd9b0e1526c5808fb6a8d8156c2511f38ea4f306b1069d20d95d0412266`；元数据SHA256 `aa6dbad8c3eb1e1bf941008cc31c5bcd46277af91059d6bdbcce9c7db3cec81c`，有效至北京时间2026-10-15 21:12:09。固定文件在忽略目录`artifacts/alpha7-preparation-20261008/final/`。**已实现、自动测试通过、CI产物可下载、候选已本地签名；尚未发布alpha.7 feed/Release、未在线安装或实机验收。当前实际仍alpha.6/ledger2/committed，安装内容保持。**

发布和失败恢复方案见 [alpha.7 OTA 审查](docs/WINDOWS-ALPHA7-OTA-REVIEW.md)。实际手机/Windows同步基线已通过，具体证据如下；本轮未重新配对、切换现用实例或部署更新源。

## Windows alpha.6 实际维护升级与手机同步基线

2026-10-08最新实际结果：用户确认此前按“×”正常关闭原alpha.5窗口。补充并验收受限的`resume-clean-exit`后，按既有维护授权执行同一已签名alpha.6包，主/次事务与maintenance均`committed`，实际新GUI唯一且与可信启动门父进程匹配，应用锁持有。签名槽重新校验通过；原alpha.5槽保留，完整稳定身份、配置、权限、任务/回执和启动器摘要保持。没有重新配对、重发已完成消息、改手机/中继或发布新feed。

候选实际隔离启动通过固定Desktop兼容性、空闲/权限/回执检查及真实登记/传输探针，再清理自有连接进入新GUI。用户随后重新连接；独立只读检查确认alpha.6本机health正常、实时任务检查通过、`mobile-sync` HTTP200、UUID不变，两条已有`PHONE_ALPHA5_OK`和`DESKTOP_ALPHA5_CONTINUE_OK`均可读取。**用户随后明确确认手机两条回复都能看到，原任务同步验收通过。** 本次完成Windows本地签名包维护升级；Android/relay联网N→N+1和三端OTA验收仍未完成。 脱敏证据见忽略目录`artifacts/alpha6-maintenance-20261008/maintenance-committed-verification.json`；自动模型输入0。

本次维护工具仅在仓库运行，不修改已签alpha.6 ZIP。完整Python146项中140通过/6跳过、Node64通过、维护定向22通过；Windows合成初次/等待续审/正常关闭续审各成功和失败共六例通过。先前`resume-wait`提交0b3532f的Core [push37773618452](https://github.com/nzkres/codex-desktop-remote/actions/runs/37773618452) / [PR37773623778](https://github.com/nzkres/codex-desktop-remote/actions/runs/37773623778)均两平台成功；最终正常关闭续审代码`9f6e93a`的Core [push37775664273](https://github.com/nzkres/codex-desktop-remote/actions/runs/37775664273) / [PR37775672832](https://github.com/nzkres/codex-desktop-remote/actions/runs/37775672832)也均Windows/Linux成功，六项Windows维护演练及既有GUI/cgroup升级回退通过；只追加文档证据，不替换已签包。PR #2仍草稿，main未合并；Android和relay仍alpha.5。

## 此前等待退出超时的历史现场（已由上述续审完成）

**最新实际结果：用户已明确批准维护升级，无需重复请求该授权。** 独立助手复验成功并报告guiPinned/waitingNormalExit；原GUI在120秒内未退出，助手返回 `old_process_exit_unconfirmed`，主journal与maintenance结果保持 `needs_review`。随后只读复核：当前槽仍alpha.5、稳定身份/配置/任务/回执与审查一致、候选事务尚未开始、原GUI仍能通过创建身份/路径/用户/会话核验。候选已暂存且ledger接受sequence2，故不能直接重跑原review/apply，也不能删屏障或回退ledger。没有启动alpha.6或停止原GUI，手机恢复未验收。下一次须在用户准备操作时先复核本次中断和原句柄，安排受审查的继续交接；不要让用户提前关掉原窗口。原始脱敏结果在忽略目录`apply-timeout-verification.json`。

## 已完成的候选与审查准备（以下“待批准”是执行前记录）

最终维护输入 `56f488eb3fbeb752d21d8af87b5fb5dd02ab3016` 的Core [push37763446214](https://github.com/nzkres/codex-desktop-remote/actions/runs/37763446214) / [PR37763453724](https://github.com/nzkres/codex-desktop-remote/actions/runs/37763453724) 均Windows/Linux成功。Windows Node64、Python135通过/6跳过、relay73通过/4跳过；Linux Node62通过/2跳过、Python121通过/20跳过、relay77通过。新维护Tk/helper合成两例committed/needs_review和既有GUI/helper、cgroup升级回退均通过。

已下载Linux产Windows候选，43个源码文件与Git输入逐字节相同，清单/解包校验通过；ZIP **93986字节**、SHA256 **`4df87881c5d0f704a4b2ac0a1a2d12e022f5b11062a8740d7ad43f96ca7811cf`**。原测试keyId `cdr-ota-test-20261008-v1`在本机签发sequence2，元数据SHA256 `5710fe23a9781b632f02ef1caea1fb56d7941b6ecbb39a45807b8adcdac9c2de`，issuedAt1791455409/expiresAt1792060209，按当前安装trust/ledger验签通过。**只在本地准备，未创建Release、上传feed、暂存进真实安装或切换版本。** CI重打relay alpha.5包不用于替换已发布资产。

对当前配对安装执行`review`成功：新包别名探针、原GUI归属、稳定身份/配置/回执/current/ledger/启动器前后绑定一致；审查哈希 `e19672fa6647a6aec018f64d545ad2acfa65204fe7f2f0212fa823be663e5ff9`，expiresAt1791457239。审查有时效，过期需只读重做，不能直接apply旧结果。输出仅在忽略目录`artifacts/alpha6-maintenance-20261008/`，原身份等不入Git。**已向用户请求本机alpha.5→alpha.6维护切换确认，尚未收到；当前仍运行alpha.5，手机显示未验收。**

## 维护实现与本地验证

2026-10-08维护增量：仓库新增仅用于 alpha.5→alpha.6 审批别名故障的 `updates/windows_maintenance.py`。审查绑定完整稳定身份、配置、任务/回执文件、当前槽、trust、ledger和启动器；签名候选从本地独立暂存，固定同用户/会话原GUI句柄，等待正常退出，再经原事务做保全与健康校验。等待退出前先写旧启动器识别的持久屏障；过期、预检失败、断电或候选不健康均保留需复核状态。alpha.5仍有已知探针缺陷，失败时不启动它或宣称健康回退。工具不随产品包交付，不是通用冷恢复器。操作与边界见[维护指南](docs/WINDOWS-ALPHA5-MAINTENANCE.md)。

本地验收：Node **64/64**，Python **141项中135通过/6跳过**；新增Windows真实合成DPAPI/Tk/独立助手/Job演练完成 `committed` 与坏候选 `needs_review`，身份和任务文件保持，旧进程正常退出后助手继续运行。签名/篡改/过期/重复锁/候选清理失败/激活不明/双journal镜像中断路径有回归。首次演练仅因夹具预期错误码不符失败，修正夹具后两例通过；不改产品门槛。

真实选定任务的**只读维护预检通过**，模型输入0：固定兼容性通过，当前仍仅顶层审批别名差异，requests为空、回执确定。首次预检因要求未确认提交必须为空数组而拒绝；只读核对固定 app.asar 后确认其代码在清空时删除 `unconfirmedTurnSubmissions` 字段，现允许该字段缺省，但自有null/undefined/格式错误/非空仍拒绝；不修改Desktop或配置。本轮CI、候选签发与实际review结果后续单列；**当前三端仍alpha.5，未停止现用连接、未部署新feed、未实际安装或手机验收。**

## 此前审批别名定位与CI证据（历史记录）

2026-10-08最新实机证据：用户完成手机发送与 Desktop 接续，两条回复 PHONE_ALPHA5_OK、DESKTOP_ALPHA5_CONTINUE_OK 均已在同一个原生任务完成；手机没有同步，截图明确报“Desktop 任务权限已变化”。活动 alpha.5 登记仍 connected、适配器健康200，但 update-status 和 mobile-sync 均409/permissions_changed，不能宣称闭环通过。

根因已隔离为审批名称从 guardian_subagent 变为 auto_review；只替换这一字段后摘要准确等于原保存值，官方协议也将二者定义为兼容别名。Windows alpha.6 源码保留原摘要算法和配置，仅在完整权限比较时兼容这两个顶层值；真实权限差异仍拒绝。Node45项通过，Python124项118通过/6跳过；修复源码只读连接真实选定任务后，手机协议数据已同时包含两条测试回复，idle/permissionsVerified/receiptsCertain通过，身份文件、配置、账本和回执前后未变。自动模型输入0。

**当前运行三端仍是原 alpha.5。** alpha.6 尚未签名发行、安装或获得手机显示验收。旧版更新预检也使用同一错误比较，普通 OTA 会被拒绝；须先完成可审查的维护切换与失败恢复方案，再确认停用这条新配对连接。不得改原 permissionsHash、删除 runtimeNeedsReview 或覆盖已签 alpha.5 槽。详见[设备基线记录](docs/OTA-DEVICE-BASELINE-20261008.md)。

最终提交ea45239的Core [push37758224837](https://github.com/nzkres/codex-desktop-remote/actions/runs/37758224837) / [PR37758232762](https://github.com/nzkres/codex-desktop-remote/actions/runs/37758232762) Windows/Linux均成功：Node Windows45通过、Linux43通过/2跳过；Python Windows118通过/6跳过、Linux104通过/20跳过；relay Windows73通过/4跳过、Linux77通过。真实合成Windows GUI/helper提交0.0.2与坏候选回退0.0.1、Linux cgroup提交/回退0.0.2及4棵树退出均通过。它们不模拟本次真实alias阻断的旧版维护升级。

最终Linux产Windows alpha.6包已下载复验：92767字节、SHA256 `a7c92ee9e4d12826c7e27a2e93b1fd010f263ece90ac15bf784c25c7494ca0f6`，43文件清单/内容与已按Git67302e7逐文件核对的产品源相同；ea45239仅改夹具和文档。包未签名、未发布到feed、未安装，CI artifact保留14天。CI重打的relay alpha.5包不用于覆盖旧发布资产。早期PR37756853884曾在终态记录后清理临时目录WinError32；已修为父测试先固定helper原始句柄、等其实际退出才清理，失败经过保留在WORKLOG。

本轮发现并保留记录的缺口：未选择电脑时请求旧任务路由导致顶部404；AppX逻辑/物理home不一致导致启动拒绝；错误端口944造成申请失败但界面仍显示提交中，且无受控重新配对入口。旧失败目录CodexDesktopRemoteTest完整保留，用户取消申请后另建新安装完成配对；未重置身份或保护。alpha.5原包没有修改，后续版本修复项见[设备基线记录](docs/OTA-DEVICE-BASELINE-20261008.md)。

实际 Desktop26.930.7945.0、两项固定指纹及Python/Node/FRP依赖通过。用户恢复原任务后，只读历史与权限探针通过，所有自动诊断模型输入数为0；旧项目身份、Gateway/FRP和服务未迁移或停止。下方是此前各阶段的历史证据。

## 隔离部署阶段验收记录

用户已批准在106.53.126.69新建隔离用户/目录/数据库，部署alpha.5中继、FRPS、HTTPS与私有OTA源，开放TCP9443/17443并验证续证/鉴权/回退。**不含手机安装、真实身份迁移或停止现用服务。** 下方“暂不部署”是先前阶段的历史限制，已由本次具体授权解除。

| 本次层次 | 实际结果 |
| --- | --- |
| 服务器安装 | cdrtest独立账号/数据库、alpha.5受管relay、FRPS0.71.0、独立Caddy与私有feed均已安装运行；端口/目录/控制socket隔离 |
| 服务器内实际验收 | 正常IP证书HTTPS；六项匿名metadata/包401；账号三端下载RSA/大小/SHA通过；合成设备仅Windows、暂停/撤销拒绝；实际FRPC/FRPS TLS+插件+HTTP夹具通过 |
| 目标服务器事务 | Python3.12.3/systemd255委派cgroup合成升级0.0.2、坏候选回退0.0.2、4棵自有树清理通过；实际alpha.5受管stop/start、整个cgroup退出、两库摘要保持通过 |
| 独立续证 | 新容器reload hook已安装并实际调用成功；现有codex-remote-certbot-renew.timer active。未强制续证，实际新证书轮换尚待验收 |
| 公网验收 | 用户放行云端两端口后，主电脑正常TLS三包完整下载/RSA/大小/SHA通过，六匿名401、退出后401、跨origin凭据拒绝；真实Windows FRPC→公网FRPS→Windows HTTP夹具通过，设备权限/暂停撤销与原始句柄+Job整树清理通过 |
| 中继更新检查 | cdrtest实际产品login/check/logout通过，当前alpha.5返回no_update_available，服务healthy；无强制同版本重装 |
| 现用环境 | 旧Caddy配置SHA未变，443/7443/7000仍监听；无旧身份、数据库、Gateway迁移或停止 |
| 本轮回归 | Node33通过；Python124项118通过/6跳过；relay77项73通过/4跳过；目标Linux续证hook合成3项通过 |
| 本轮CI | b107726的Core push37744798752/PR37744901531 Windows/Linux成功，含真实GUI/helper和cgroup升级/回退；三端原包不变 |
| 尚未完成 | 手机首装、真实Windows接入、真实三端alpha.5→alpha.6、证书实际轮换和独立签名密钥恢复 |

详细资源、命令、故障修正和保护见[隔离部署记录](docs/OTA-ISOLATED-DEPLOYMENT-20261008.md)。这张表记录隔离部署阶段完成时的证据；后续主电脑安装和任务探针停点以本页顶部及[设备基线记录](docs/OTA-DEVICE-BASELINE-20261008.md)为准。程序仍是已发布的alpha.5原包，alpha.6尚未构建。

## alpha.5 受控构建与发布基线（发布时历史快照）

以下记录 alpha.5 发布时的授权和构件证据，不代表当前部署状态；最新隔离服务、公网验收与实机待测项以本页顶部为准。当时用户已明确批准：合并PR #1，固定测试origin `https://106.53.126.69:9443`，创建本项目专用RSA/APK测试签名并加密保存，配置本私有仓库六项Android Secrets，构建并验证alpha.5首装包。**当时授权不包含隔离服务器部署、停止现用服务、真实身份迁移或手机安装；隔离部署后来另获批准并已完成## 2026-10-15 Android alpha.11 候选

实现输入框额度快照（含采样与过期状态）和可配置核心网格流光。Windows 连接器需同步升级提供额度字段；当前手机源仍alpha.10。本轮编译/测试/实机与发布分别记录，未宣称实机通过。

## 2026-10-15 Android alpha.10 已发布

Android alpha.10/versionCode 10/sequence 4 已发布；原证书、实际 Manifest、内置 trust 与实际 HTTPS 完整下载核验通过。手机可检查更新，覆盖安装待用户确认。

更新错误按暂停、离线、任务忙、权限、网络与两端未知回执分别提示。原身份、回执、任务保护保留。GitHub runner 因账户额度未启动，改用固定合并提交本机构建及原签名；本机单元/Lint 通过，55 项模拟器回归覆盖相同运行源码。完整证据见 [发布记录](docs/ANDROID-ALPHA10-PUBLICATION.md)。

## 2026-10-15 更新状态诊断修复（alpha.10，已通过构建验证，未发布）

终端最初为 active / offline，用户点击连接后心跳在线，但更新状态接口仍返回 503 terminal_credential_unavailable。本机 runtimeNeedsReview=true，本地任务接口拒绝连接。电脑配置对应的 10 条回执全部 accepted 且具备 turnId；没有手机调试通道，手机本地回执未核验。没有清除保护、重发或清除回执。更新源 HTTP 200，仍为 alpha.9 / sequence 3。

修复提交 a225ca4；Android CI 38663853142 的编译、单元测试、lint、APK 说明校验与 55 项模拟器回归通过。Node 36/36；Python 139 项中 132 通过、7 平台跳过；Core CI Windows/Linux 测试步骤通过，但构件上传失败。修复未签名发布或安装，连接器现场仍需按 RECOVERY.md 复核，不能把心跳在线当作恢复完成。

## 2026-10-15 — Desktop Remote Lite alpha.9 已发布（手机待安装）

alpha.9/versionCode 9/sequence 3 已发布，原证书、实际 Manifest、内置 trust、服务器回执和实际 HTTPS 完整下载均独立校验通过。APK 40121564 字节，SHA256 `271e994290e7dc5664da856769601c98935a4191faf2b004045aab4dc27ecc8c`。源码 `648527d2cf00eae5fe06bdd50630bf60d3f91a3e`，受控构建 38612698531，55/55 模拟器回归通过；手机实际安装待用户确认。

已落地封存预览的终端玻璃皮肤、离线字体、随机网格、吸顶与输入框状态。模型修改与异步答题仍需电脑。较大字体包的上传期限已修复；权限、配额、旧包、其他组件源和现用服务保持。完整证据及安装步骤见 [alpha.9 发布记录](docs/ANDROID-ALPHA9-PUBLICATION.md)。

## 2026-10-14 — Android alpha.8 私有 OTA 已发布（手机待安装）

用户明确批准签名并发布Android alpha.8。北京时间17:36:03在独占Android发布锁内核对旧索引摘要后原子切换到alpha.8/versionCode8/sequence2；17:36:38完成本机经正常TLS、账号鉴权的索引读取及完整APK下载复验。匿名索引和APK均401；临时验收会话单独注销，不影响手机登录。原索引备份、旧APK、Windows/relay更新源、服务PID/启动时间及运行版本均保持。

构建38540921047的原证书APK，11806944字节，SHA256 `02de6dddf6d36cf75bf658711d0731ab8b6dd0d48aa3fd8233095748449e6ee9`；新签名索引SHA256 `fe29a7e43ea82f282af887f9979c08d47d008abad95c4d18ff406fe6e024e29f`，有效至北京时间2026-10-21 17:30:30。原RSA信任验签、错误签名拒绝、旧序号拒绝、alpha.5/6/7升级候选验证均通过。此次无业务代码改动，沿用上一轮自动化结果并完成真实发布验收，不重建同版本APK。

手机现在可在原APP“我的→版本与更新说明”检查并安装alpha.8，登录原:9443、选择原电脑，不卸载或重新配对。手机实际安装、身份/草稿保留、原任务与普通问答验收尚未完成；普通问答需Windows alpha.15，异步问题仍在Desktop回答。完整路径、备份、核验和恢复边界见[发布实录](docs/ANDROID-ALPHA8-PUBLICATION.md)。

## 2026-10-14 — Android alpha.8 已直传自有服务器并独立验包（未发布到手机）

用户批准的专用账号、CI配置、合并和原证书构建已完成。PR #11合并348fb2d，上传换行修复d41466a；[受控构建38540921047](https://github.com/nzkres/codex-desktop-remote/actions/runs/38540921047)成功，直接保存到106.53.126.69的`/srv/codex-desktop-remote-incoming/android/38540921047-1/`。本轮Android APK不存GitHub Actions artifacts或Release；源码/CI仍在GitHub，Core源码ZIP流程未改。

从服务器管理员SSH取回后独立验收：APK **0.1.0-alpha.8 / versionCode 8**，**11806944字节**，SHA256 **02de6dddf6d36cf75bf658711d0731ab8b6dd0d48aa3fd8233095748449e6ee9**；真实Manifest包名dev.codex.desktopremote.lite、minSdk26，单签名者及原APK证书、包内版本记录和原trust逐字节匹配，服务器收据全部文件大小/SHA一致。APK仍为现有debuggable受控测试构建，不伪称生产稳定版。

上传账号真实合成验收及修复后Node36/36、Python132通过7跳过已经记录；CI签名构建通过编译/单元/lint/签名/trust/服务器上传，模拟器测试在先前PR Android CI完成，签名构建按设计不重跑模拟器。原签名/身份未更换，服务和Android旧索引保持。**此次批准明确不切换正式Android feed、不安装手机**，手机检查更新尚不能看到alpha.8；下一步准备并审查新的签名元数据、原子发布和私有下载，再经批准切换。详见[部署实录](docs/SERVER-OTA-DEPLOYMENT-20261014.md)与[Android发布审查](docs/ANDROID-ALPHA8-OTA-REVIEW.md)。

## 2026-10-14 — 自有服务器上传入口已部署，修复 CI 私钥文本格式

用户明确批准专用账号、两项CI配置、PR #11合并及原证书构建。服务器cdr-ota-upload已部署并通过真实SSH合成上传、重复/任意命令/错误主机键拒绝和权限核验，现用服务及Android feed不变。两项Secret实际配置；PR #11已合并main 348fb2d。Android PR CI38537322078整轮成功，Core两平台检查通过、仅旧源码构件上传受GitHub配额阻止。

首轮受控构建38539346508的APK编译/单元/lint/原证书/trust检查通过，SSH上传失败且服务器未接收。定位到gh secret set去掉末尾换行；Linux合成密钥复现缺LF导致libcrypto错误，补LF恢复。上传脚本补规范LF及回归测试，保持原密钥/账号/证书。GPT-6 Sol medium只读复核无阻断。源码交付范围仍仅Android，未改Core构件保存；尚未发布新版Android源或安装手机。详细证据和维护步骤见docs/SERVER-OTA-DEPLOYMENT-20261014.md。

## 2026-10-14 — Android签名构件改存自有服务器（实现待部署）

用户要求不再依赖GitHub存储，使用现有服务器做OTA。新增固定SSH命令接收器及main受控签名构建的直接上传脚本，替换Android upload-artifact；普通PR/debug不存GitHub或服务器。正式私有HTTPS下载源、原APK证书/公钥、手机身份保持，应用版本仍alpha.8。暂存与正式feed分权，不增加公网端口。

本机接收协议9通过1权限跳过，服务器Linux临时目录10/10通过；GPT-6 Sol复核无阻断并建议补接收总字节核对，已落实。本机Node36/36；Python完整134项中127通过、7平台跳过，之后新增发送边界4/4通过。服务器接收器Linux10/10通过。服务器真实SSH集成和受控签名包尚未执行。

已生成仓库外DPAPI加密的专用上传密钥；创建服务器cdr-ota-upload账号/授权文件/私有目录被自动审批拒绝，原因是新增持久SSH权限须用户明确批准。拒绝后未换路径执行，服务器和CI Secrets未改变。完整配置、测试及撤回清单见docs/SERVER-OTA-DELIVERY.md。既有PR #10合并与原证书构建授权继续有效，不再以恢复GitHub存储作为这条新交付路径的前提。

## 2026-10-14 — Android PR #10已获批合并；受控构建等待构件保存恢复

用户明确批准合并PR #10，并在构件保存恢复后沿用原证书受控签名构建alpha.8；本次不包含Android私有源发布或手机安装。北京时间16:26:18按精确head 4371604af9f8171a2e0fa781dc61c304ff2902a7合并，main提交e66f710660b58d35da67db4ceab41c6d6dd01114。合并后android-native及.github/workflows/android.yml与批准分支完全一致；相较已完整测试的b7a5245，head只增补4份审查文档，没有业务代码变化。

最新Core CI38534848945于16:25:22（Linux）、16:26:23（Windows）上传均仍报Artifact storage quota has been hit，其余适用步骤通过。当前仓库仍只有保留的4份旧最新构件，未获得alpha.8新APK。合并自动触发普通main Android CI38535101592及Core CI38535101712；它们不是受控签名构建，不能用于手机覆盖安装。没有把清理成功当额度恢复，也未反复触发签名构建。

后续签名授权已具备，不重复询问：确认构件保存实际恢复、main Android源码/签名工作流没有未经审查变化后，执行gh workflow run android.yml --repo nzkres/codex-desktop-remote --ref main -f signed_ota=true。必须保留run ID并下载复验证书、包名/versionCode8、包内trust与版本说明、大小/SHA；取回后才准备RSA元数据签名和私有源审查。若main业务代码变化，先复核范围。此刻受控签名构建尚未触发、APK不可下载、Android feed仍未切换，手机未安装。不能声称后续任务已被自动调度。

命令证据：gh pr merge 10 --merge --match-head-commit 4371604af9f8171a2e0fa781dc61c304ff2902a7；gh pr view 10确认MERGED与合并提交；git diff origin/main HEAD --exit-code -- android-native .github/workflows/android.yml通过；最新CI日志仅提取固定配额错误，不保存私密日志。

## 2026-10-14 — Android alpha.8独立PR #10验收完成，待批准合并/签名

PR https://github.com/nzkres/codex-desktop-remote/pull/10，业务提交b7a524554fbd5f0240fa26ef67834d5e6cfc8d2d。109份Android文件及Android CI工作流与已测试源码0dbf345的Git对象完全一致（tree b40aa270b71bd5124de60ad4f18246c2365057ec，workflow blob 5f1d29989955cc5707a5d91a6845ddc8994e506a）。更新器、应用Manifest、构建签名配置与main无差异；未改Windows/relay业务代码。本机Node36/36，Python118通过6平台跳过；版本历史与git diff --check通过。GPT-6 Sol medium只读复核未发现阻断，强调须配套已发布Windows alpha.15及完整版本文档，均已记录。

PR Android CI38533382850：编译、JVM、lint、包内版本说明及37项模拟器全部通过。CI端debug APK alpha.8/versionCode8，11806368字节，SHA256 3a46fd9a2295db0f5104abe65a48da6c3fc6468626930ebd1ca06ef9db14b3d7；这不是原证书包，也未本地下载复验。北京时间16:22:01构件上传仍报Artifact storage quota has been hit，API artifacts.total_count=0，整轮failure。Core CI38533382485 Windows/Linux适用检查全通过，仅构件上传同样受阻。重复push Android构建已取消，保留完整PR CI；重复Core取消时已完成，无额外重跑。

尚未合并PR #10、受控签名或发布Android源。线上仍alpha.5，手机不能取得尚未发布的alpha.8。下一步请求批准只合并此独立PR并沿用原证书进行受控签名构建；额度未恢复不循环重建或修改付费。取回APK验签后另请批准私有源切换，不包含手机自动安装。证据artifacts/android-alpha8/pre-merge-review.json仅本机，完整交接见docs/ANDROID-ALPHA8-OTA-REVIEW.md。

## 2026-10-14 — Android alpha.8独立发布准备

用户要求继续解决手机无法OTA。线上Android源仍alpha.5/sequence1，APK大小11772524、签名元数据及SHA通过；旧metadata于北京时间2026-10-15 14:33:03过期。Windows alpha.15已发布不代表Android发布。最新核验Android run38530732869仍因GitHub构件配额上传失败，暂无alpha.8可交付包。

独立分支基于main 7f4d80d，提取已测试0dbf345的Android源码/测试/工作流，保留现有main-only签名、原证书及权限；不携带Windows/relay业务改动。准备审查、独立CI和提取完整性验证，尚未合并、签名或发布Android源。后续步骤和恢复边界见docs/ANDROID-ALPHA8-OTA-REVIEW.md。

## 2026-10-14 — Actions构件默认只保留同类最新一份

用户授权清理当前仓库旧Actions构件，并明确不扩展到其他仓库。独立main维护变更新增Keep latest Actions artifacts：Android/Core self-test完成后以及手动触发时，按构件名分别保留最新已完成运行的未过期构件，删除更旧同名构件；按created_at而非ID判断新旧。签名Android、debug Android、Windows和Linux分开保留。运行中的构件和未知名称不清理；保留件失效时立即停止。没有checkout或执行触发分支的代码，不访问Release、缓存、源码、签名Secrets或其他仓库。

独立维护提交cfb6240已进入main并启用规则；首次手动运行38526457237成功。清理前95个构件，实际删除91个旧Windows/Linux构件，释放15,097,027字节（约14.4 MiB）；剩余4个，共23,284,628字节。独立API重读确认各类均为清理前created_at最新的保留件：Linux11826389040、Windows11825894898、Android debug11558639319、Android签名测试11532685397。两个APK各只有一份，均保留。Release标签与asset ID/名称/大小清单逐字一致，未修改其他仓库。这里的最新指最新已上传构件，不表示alpha.8 APK已成功上传。现有14天到期上限仍适用，最新一份不代表永久归档；长期安装包使用Release/私有更新源。GitHub存储统计通常6–12小时更新，清理不等于上传额度立刻恢复，也不保证当前账号其他仓库不占配额。

本机main维护分支Node36/36通过；Python124项，118通过6平台跳过；版本目录检查通过。新增3项精确执行工作流内联程序的合成测试，覆盖时间/ID乱序、类别隔离、活动运行、未知名称、过期记录、保留件消失、删除失败和空仓库。不会在测试中调用真实删除API。维护变更不合并功能PR #8，也不生成新的软件版本。

## 2026-10-14 — Android alpha.7独立发布候选（未合并、未受控签名）

本分支只从已测试功能提交e75a209提取Android代码、测试、版本目录及Android CI，不合入Windows连接器或中继代码。包含此前alpha.6的明确选择电脑/通知目标隔离，以及alpha.7的手机点击标题按需加载原对话。Windows alpha.14候选与实机验证记录在PR #8；本分支的Windows/relay版本仍为main基线，不能当成当前主电脑安装版本。

手机显式点标题或重试附open=1；待加载后只用普通读取等待，后台、分页和通知不导航。不发送唤醒消息，不复制对话，不改Desktop权限。需要Windows alpha.14配合；可能切换电脑Desktop页面。旧连接器忽略参数，仍须在电脑打开对话。

源提交Android CI 38513358589编译、单元测试、lint、APK版本记录检查与19项模拟器测试均通过；只有artifact上传因存储额度失败，不能称整体CI成功或构件可下载。本分支提取后本机Node33/33、Python118通过/6跳过；首次沙箱运行被临时目录/回环权限拒绝，正常用户权限复跑通过。本分支仍需独立CI核对。受控签名工作流要求main，须先审查并批准合并本PR才能构建同证书APK。普通debug构件不能覆盖现用测试APP，不卸载绕过；当前未发布、未安装、未修改Secrets或私有源。

# 当前验证状态：0.1.0-alpha.5 受控测试首装候选

用户已明确批准：合并PR #1，固定测试origin `https://106.53.126.69:9443`，创建本项目专用RSA/APK测试签名并加密保存，配置本私有仓库六项Android Secrets，构建并验证alpha.5首装包。**不包含公网部署、停止现用服务、真实身份迁移或手机安装。** PR #1已合并为`1e405ad`；受控构件输入为`1a4b4c0ced91d1a384e723374a6141d84e43de9e`，经分支CI通过后main非强制快进到同一提交。

签名工具提交`755fe14`的Core [push37734382112](https://github.com/nzkres/codex-desktop-remote/actions/runs/37734382112) / [PR37734386777](https://github.com/nzkres/codex-desktop-remote/actions/runs/37734386777) Windows/Linux，以及Android [push37734382114](https://github.com/nzkres/codex-desktop-remote/actions/runs/37734382114) / [PR37734386832](https://github.com/nzkres/codex-desktop-remote/actions/runs/37734386832)均通过。主Python119项：Windows113通过/6跳过、Linux101通过/18跳过；Android的普通PR构建未执行真实受控签名步骤。

新增`updates.android_publisher`用于加密PKCS12生成、公开trust绑定和六项Secrets的固定仓库/标准输入提交；已有Secret或中途失败会拒绝自动覆盖/重试。已实际创建本项目专用RSA/APK测试签名并以CurrentUser DPAPI加密保存；六项Secrets配置成功，RSA私钥留本机。alpha.5完整本机回归：Node33通过；Python124项中118通过/6跳过；relay77项中73通过/4跳过。完整用法与加密备份限制见[发行说明](docs/OTA-PUBLISH.md#windows发行密钥与受控android构建)。

公开签名标识：keyId `cdr-ota-test-20261008-v1`；RSA SPKI SHA-256 `c3835e3e59b362e0b356d3a7b3589c255187ddce34692167f5160b0e5f5e3a4d`；APK证书SHA-256 `ffcb12d413ee58613576b7914350ad4df887bf29d50d97f2b20c4e17dcebcf1a`；Android trust文件SHA-256 `e271d06db56cc508b27911c7f37a30e0ffe6671d0c9b6bce46718c061aa67134`。只记录公开指纹，不记录私钥、口令或Secrets值。签名材料的独立备份/跨电脑恢复尚未验收。

| 层次 | alpha.5 发布时证据 |
| --- | --- |
| 核心 CI | [分支37736389583](https://github.com/nzkres/codex-desktop-remote/actions/runs/37736389583)与[main37736836192](https://github.com/nzkres/codex-desktop-remote/actions/runs/37736836192)成功。Node Windows33通过、Linux31通过/2跳过；Python124项：Windows118通过/6跳过、Linux104通过/20跳过；relay77项：Windows73通过/4跳过、Linux77通过；续证钩子Linux3通过、Windows3跳过 |
| 隔离运行事务 CI | 真实Windows GUI/pythonw独立helper升级0.0.2及坏候选回退0.0.1；Linux委派cgroup服务升级0.0.2、坏候选回退0.0.2及4棵自有树清理通过。合成数据/探针，不等于真实Desktop任务验收 |
| 受控 Android CI | main [37736837251](https://github.com/nzkres/codex-desktop-remote/actions/runs/37736837251)，94 JVM测试通过、lint 0 errors / 45 warnings、编译、apksigner单签名者/证书校验、APK内trust逐字节比对全部成功；清理签名输入成功 |
| 下载后复验 | APK SHA与CI准确一致；Google官方AAPT2核对真实包名`dev.codex.desktopremote.lite`、versionCode5、minSdk26/targetSdk36；包内说明与Git原始字节、trust与受控输入相同。APK仍为debuggable受控测试构建，无手机/模拟器安装 |
| 源码运行包 | 使用Linux CI构件：Windows43/relay29源码文件、完整manifest、逐文件SHA与Git `1a4b4c0`原始字节全部一致；没有运行时或发行私钥 |
| 已签首装资料 | 三组件test元数据均使用专用RSA签名并复验，sequence=1；`bootstrap-0.1.0-alpha.5.zip`含各组件`trust.json`/`latest.json`及README。有效至2026-10-15 06:33:03 UTC（北京时间14:33:03）；过期需新序号重新签发相同包，不能关闭验证 |
| 实际临时首装 | Windows Python3.12.14、WSL Linux Python3.14.4新临时home：实际alpha.5签名/清单/ledger/current通过；重复安装拒绝且原文件未变。未配置账号/任务、未启动运行服务；WSL不是目标服务器Python3.12验收 |
| 私有归档 | [v0.1.0-alpha.5](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.5)已发布为私有prerelease，7个附件全部uploaded，服务端大小/SHA与本地一致；标签输入1a4b4c0。GitHub归档不能替代私有HTTPS更新源；后续隔离源部署证据见本页顶部 |
| 发布时尚未执行 | 当时尚未部署公网源，也未做手机首装、真实Windows连接/身份迁移、真实三端alpha.5→alpha.6与失败演练；后续隔离源已部署并完成公网合成验收。没有EXE安装器或托盘，Windows/relay仍需外部依赖；独立密钥恢复未验收 |

| alpha.5 程序包 | 字节数 | SHA-256 |
| --- | ---: | --- |
| android-0.1.0-alpha.5.apk | 11,772,524 | `4fc366cd4580930ca020c5535d1f2798de7991fbad214e81c0f4ae822c111d3f` |
| windows-0.1.0-alpha.5.zip | 92,070 | `572c81a414b14f920c48e01007b85f73e29403e75ab25b97fdedcd7d95b1bb17` |
| relay-0.1.0-alpha.5.zip | 74,465 | `21b970e91df34bac9e74737b749f082cfacc379771db6e15d9d8caefc7795fe0` |

发布时的下一步曾是按[部署审查](docs/OTA-DEPLOYMENT-REVIEW.md)核对现场并另行确认隔离部署；该步骤后来已获批准并完成服务器及主电脑公网合成验收，见本页顶部和[隔离部署记录](docs/OTA-ISOLATED-DEPLOYMENT-20261008.md)。alpha.6尚未构建；真实N基线仍须另行验收后才逐组件升级。安装输入与步骤见[安装起点](docs/install/README.md)。

## alpha.4 与签名工具的阶段归档

以下保持当时记录；“暂缓合并/签名、未生成密钥、PR未合并”等描述已被上面的alpha.5授权与执行证据替代，不能作为当前决策。alpha.4资产保持不变。

**签名工具增量：** RSA发行密钥的Windows DPAPI保存/内存解封已实现；Android受控构建补RSA公钥与私有source预检、APK内trust逐字节核验，并固定PKCS12。当前本机Node33通过；Python主套件119项中113通过/6跳过；relay77项中73通过/4跳过。只使用临时密钥，CI结果待本次提交后补充；真实密钥、Secrets、合并、首装和部署均未执行，详见[发行说明](docs/OTA-PUBLISH.md#windows发行密钥与受控android构建)。

**2026-10-08 当时决定（已被alpha.5授权替代）：先完成本地准备，暂不合并或配置签名。** 本地增加隔离部署草稿和发布器私有路径校验；Windows GUI/helper 及 WSL 非 root 委派 cgroup 的真实合成升级/回退已通过。当时没有创建项目真实签名密钥、写入 Secrets、合并 main 或部署服务器，alpha.4 归档保持原样；完整证据见[本地准备记录](docs/OTA-LOCAL-PREPARATION.md)。

**本地准备已推送并通过CI：`be5a01c`。** Core [push 37726150384](https://github.com/nzkres/codex-desktop-remote/actions/runs/37726150384) 与 [PR 37726153483](https://github.com/nzkres/codex-desktop-remote/actions/runs/37726153483) 的Windows/Linux均成功，包含实际GUI/helper、cgroup升级与坏候选回退；Android [37726153367](https://github.com/nzkres/codex-desktop-remote/actions/runs/37726153367) 的单测/lint/APK编译和包内说明检查成功。各平台通过/跳过数量、CI构件与证据边界见[本次验证](docs/OTA-LOCAL-PREPARATION.md#推送与-ci-验证2026-10-08)。三个软件仍为alpha.4，没有新增Release、受控签名首装或真实设备OTA验收。

2026-10-08：已按用户确认补三端私有下载鉴权；未部署公网源、修改现用服务、迁移真实身份或安装手机新包。分支 `codex/ota-implementation` / PR #1，合并授权仍待确认。完整下一步见 [三阶段落地验收](docs/OTA-ACCEPTANCE.md)。

**最新推进：隔离部署预检已完成。** 用户选择在现有中继服务器新增隔离环境；已只读连接106.53.126.69，核对Python/cgroup、端口池、目录、防火墙和证书。建议独立HTTPS9443、FRPS17443、私有账号/数据/进程，候选Caddy配置经实际服务器独立adapt/validate通过；未安装或开放端口。现有IP证书到2026-10-13 22:07:54 UTC，新入口还需独立续证重载。入口、签名保管与具体部署待确认，详见[部署审查](docs/OTA-DEPLOYMENT-REVIEW.md)。

| 层次 | alpha.4 证据 |
| --- | --- |
| 已实现 | 元数据/包每次鉴权；账号三组件、活跃设备仅 Windows；手机复用加密登录、Windows 复用 DPAPI、中继管理员交互 login/logout；严格同 origin 与固定目录，私有文件权限、单链接检查与下载维护门禁 |
| 本机自动测试 | Node 33 项通过；Python 主套件 105 项通过、5 Linux 专属跳过；relay 77 项通过、4 Linux 专属跳过；最后私有下载维护测试修正定向 5/5 |
| Windows 隔离生命周期 | 首轮一次非预期回退和一次 CI 首响应超时；后续本机三次连续完整演练与最终 CI 升级/回退通过。增加固定阶段与脱敏 cause，不宣称已定位早期偶发原因；记录保留在 WORKLOG |
| 核心 CI | `c92ea98` [push 37657708244](https://github.com/nzkres/codex-desktop-remote/actions/runs/37657708244) 与 [PR 37657716116](https://github.com/nzkres/codex-desktop-remote/actions/runs/37657716116) Windows/Linux 全通过，包含实际 GUI/helper 与 cgroup 中继进程的升级/回退 |
| Android CI | `78ff65d` [37655844718](https://github.com/nzkres/codex-desktop-remote/actions/runs/37655844718)：94 JVM 测试、lint 0 errors / 45 warnings、主/测试 APK 构建通过；之后未改 Android 产品源码，最终 PR [37657716149](https://github.com/nzkres/codex-desktop-remote/actions/runs/37657716149) 也通过 |
| 可下载构件 | [私有 alpha.4 预览](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.4) 六文件已上传，服务端大小/SHA 与本地复验一致；Windows43/relay29 文件清单与 Git 输入匹配，APK/code4/内置说明核对通过 |
| 实机 / 部署 | 私有 HTTPS 源、发行信任、连续 APK 签名首装与真实三端 N→N+1 均未完成 |

| alpha.4 构件 | 字节数 | SHA-256 |
| --- | ---: | --- |
| desktop-remote-lite-0.1.0-alpha.4-debug.apk | 11,771,608 | `20d6b27875d70ee85b619cedaa5a18ac36750c7f9b2da001257add3d7d397a97` |
| desktop-remote-windows-0.1.0-alpha.4-source.zip | 91,938 | `300bfedd4dfb10fa3ea9a3c92bbc6b21d340093439a568c9fc49b84c7f62154e` |
| desktop-remote-relay-0.1.0-alpha.4-source.zip | 74,161 | `175265011dc214bb6abbd417738d978ac036df3a5dd07c3826350d74b14ba9e8` |

归档是开发预览：Android 为 debug/空 trust，Windows/relay 为需外部运行时的源码包。首次受控签名与真实 OTA 将使用新版本，不对本归档重签/换包。main 尚未合并，当前源码与文档在 [PR #1 分支](https://github.com/nzkres/codex-desktop-remote/tree/codex/ota-implementation)。

---

# alpha.3 验证归档

三端已加入 OTA 实现；现用手机、Windows 身份与中继没有切换。正式发布信任、长期 HTTPS 源和受控签名首次安装尚未配置，**不能把本轮代码与隔离测试说成真实用户已在线升级**。

| 层次 | 当前证据 |
| --- | --- |
| Android 实现 | 签名元数据、包大小/哈希、同包名/同 APK 证书与实际版本/minSdk 核对，系统安装确认；安装回执在重开后核验实际 APK 与新鲜任务证据 |
| Windows 实现 | 受管窗口检查/安装、独立 helper、原始进程句柄、Job 子树、稳定 data 与独立版本目录、事务 CAS、隔离验证和回退；未完成事务禁用新连接 |
| 中继实现 | 全新 Linux 受管实例、私有 Unix socket、独立宿主、实时空闲门禁、委派 cgroup v2、授权 DB 与配置摘要保全、候选验证和回退 |
| 更新源验证 | 本机临时 HTTPS/合成 CA 测试通过：TLS、重定向、损坏、大小、空间和禁用 TLS 拒绝；元数据 RSA3072 签名、平台/渠道/兼容/重放/换包测试通过 |
| Windows 生命周期 | 本机与最终 Windows CI 均通过真实 pythonw/Tk → 独立 helper → 新 GUI；成功 0.0.1→0.0.2，坏探针回退 0.0.1；全新临时 DPAPI unpaired，Desktop 探针为测试 fixture，不连接实际任务 |
| Linux 生命周期 | 最终 Core CI 通过真实中继监听/私有 socket/cgroup 两版提交与坏候选回退，4 棵自有树清理完成，数据库逻辑摘要不变 |
| 核心 CI | [169b449 / run 37646940566](https://github.com/nzkres/codex-desktop-remote/actions/runs/37646940566) Windows/Linux 均成功并产出源码 ZIP。Node 33 项；Python 主套件 89 项（Linux 跳过 13 项 Windows 专属）；relay 72 项（Windows 跳过 4 项 Linux 专属）；两平台真实进程演练也通过 |
| Android CI | [e7602ec / run 37644338199](https://github.com/nzkres/codex-desktop-remote/actions/runs/37644338199) 87 JVM 测试、lint 0 errors / 45 warnings、主 APK 与测试 APK 构建通过；Android 产品源码此后未变。未运行模拟器安装或手机真机 |
| 下载后复验 | Android 包名/versionCode/ZIP/包内说明与 Git 构建输入一致；Windows 41 文件、relay 26 文件的包清单和原始 SHA 全部通过，源码与 169b449 对应（Windows 仅两份 text=auto TXT 是 checkout CRLF） |
| 可下载构件 | [v0.1.0-alpha.3 开发预览](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.3)：APK、Windows/relay 源码 ZIP、Android 测试报告、artifact-verification.json 与 SHA256SUMS.txt。不是已签 OTA feed |
| 分发方式 | Windows/relay 为源码 ZIP，首次依赖 Python + cryptography，Windows 另需固定 Node/FRP/Desktop；bootstrap 新建受管目录。没有一体化 EXE 安装器或托盘 |
| 公网/真实设备 | 新 HTTPS feed 未部署、正式密钥未配置、现用身份未迁移；Android 覆盖安装与真实 Desktop/手机 N→N+1 尚未验收 |

详见 [三端版本历史](docs/versions/README.md)、[安装](docs/install/README.md)、[OTA 发布](docs/OTA-PUBLISH.md)、[故障恢复](docs/OTA-RECOVERY.md)和[跨电脑交接](docs/HANDOFF.md)。首装 trust、签名 APK 证书、独立源上线与真实设备切换需另行现场确认。CI 的 14 天 artifact 不是长期 OTA 源；下面旧版本证据不能替代 alpha.3 验收。

| alpha.3 构件 | 字节数 | SHA-256 |
| --- | ---: | --- |
| desktop-remote-lite-0.1.0-alpha.3-debug.apk | 11,771,316 | `d188ed0522f8fc36361565495536cdda141e36160443ededee314dbb843107f6` |
| desktop-remote-windows-0.1.0-alpha.3-source.zip | 86,838 | `b2370ea091c8c68e84ae6721275a8e01f8dd6c33de2f28d036940ae33bc8a5f6` |
| desktop-remote-relay-0.1.0-alpha.3-source.zip | 66,765 | `66e6b70ceb93c89dd129534720cc21b5a7d3fe8cbb833258557a75df0f219426` |

上述哈希用于核对归档文件完整性，不代替 OTA 发布者签名或手机 APK 连续签名。Android debug 包无更新公钥/source，应用内会说明尚未配置。下一步是审核独立测试更新源与受控首装签名，再用独立测试安装做真实 HTTPS N→N+1 与手机覆盖安装；现用服务切换仍须单独确认。

---

# alpha.2 验证归档

归档时仓库预览版本：手机、Windows、中继各为 `0.1.0-alpha.2`。各组件完整记录见 [手机](docs/versions/ANDROID.md)、[Windows](docs/versions/WINDOWS.md)、[中继](docs/versions/RELAY.md)。下表严格区分构建与安装/部署证据，不能从源码版本推定现用软件已升级。

| 层次 | 当前结果 |
| --- | --- |
| 原型与关系 | 手机 UI、Windows 连接器、中继三部分源码在同一仓库；原型说明与 Mermaid 关系/序列图已保存 |
| 安装说明 | [三端安装起点](docs/install/README.md)已提供 APK 手动安装、Windows 源码窗口与中继本地启动步骤；文档不代表完成新安装或公网部署 |
| UI 复用 | 保留 Android 原生对话/玻璃控件/草稿和 Windows 窗口；已保存旧风格规范与归属许可 |
| Node 自动测试 | 新目录本机 30/30 通过 |
| Python 连接器与版本校验测试 | Windows CI 42 项通过；本机 38 项套件及新增 4 项 APK 回归分别通过，覆盖缺字段/重复/倒序、版本码倒退、跨组件独立版本、文档过期和 APK 缺失/过期记录 |
| 中继自动测试 | 本机 49/49 通过；版本查询鉴权/启动快照/CLI 无数据库副作用也有合成测试 |
| Windows 说明窗口 | 合成 UI 冒烟通过，说明只读、保留新旧版本，打开/返回不触发断开；没有使用真实身份 |
| 核心 CI | alpha.2 Windows/Linux [run 37615952040](https://github.com/nzkres/codex-desktop-remote/actions/runs/37615952040) 均成功，源码 `27b07da`；Linux 跳过 Windows 专属测试 |
| Android 单元测试/编译 | [run 37615952157](https://github.com/nzkres/codex-desktop-remote/actions/runs/37615952157) 成功，源码 `27b07da`；72/72 JVM 测试通过，主/测试 APK 编译；未运行模拟器 instrumentation |
| Android 包内说明 | CI 与下载后双重复验：`assets/releases.json` 与源码逐字节一致，构建输出包名/版本名/版本码相符，ZIP 完整性通过；首轮缺失说明的包已拦截、未发布 |
| Android Lint | alpha.2 为 0 errors、40 warnings；报告随开发预览提供，未隐藏警告 |
| 可下载构件 | [v0.1.0-alpha.2](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.2) 提供验证后的 APK、报告与 SHA256SUMS；旧 alpha.1 保留 |
| Android 新包安装/真机 | 未安装、未验收；独立包名 `dev.codex.desktopremote.lite`，不会覆盖原 App |
| 公网中继部署 | 未部署新仓库中继，现用服务未变更 |
| 本仓库真实配对/手机验收 | 未进行；没有复制现用身份或运行配置 |
| 三端 OTA | 已按用户要求纳入必做范围，见 [OTA 要求](docs/OTA.md)；alpha.2 尚未实现，没有签名发布、在线升级或回退验收证据 |
| 安装器/托盘 | 当前未提供；OTA 所需的首次安装基线与稳定启动入口也待实现 |

参考基线：旧 Lite `c72d3c5` 曾由用户确认同一个 Desktop 任务的手机→Desktop→手机文字闭环；`2fcf9e2` 的正常断开重连曾通过两轮 Windows 隔离验证。这里只保留来源证据，**不是本仓库 Android 新包、真实配对或公网部署的验收**。

alpha.2 APK：`desktop-remote-lite-0.1.0-alpha.2-debug.apk`，11,720,835 字节，SHA-256 `1d6d76b4e6eed91ba670593743e393ca10c7e6b62b236246cc675896829b2d11`，versionCode 2。产品源码与 `27b07da` 相同，之后只补充文档证据。debug 签名没有稳定覆盖升级承诺；校验值用于核对完整性，不代替正式发布签名设计。

alpha.1 归档 APK：11,719,461 字节，SHA-256 `16fcc64a5df5310369c2e9eed8fb0f4fbf165e031e7b63c3bdc68e877e7aa2e9`；Android 产品源码 `ebdb6e4`，核心产品源码 `ae9ed7c`。历史构件和记录仍保留在对应标签。

下一次人工测试：先按 [TESTING.md](docs/TESTING.md) 运行合成自测，再以独立测试设备、专用 Desktop 任务逐项记录实际配对、手机发送、Desktop 同任务接续、手机显示、正常断开重连。未确认的发送不得重放；旧安装不能靠删除保护标志迁入。
