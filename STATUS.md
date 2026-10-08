# 当前验证状态：Windows alpha.6 本地签名候选与维护审查通过，等待实际切换确认

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

以下记录 alpha.5 发布时的授权和构件证据，不代表当前部署状态；最新隔离服务、公网验收与实机待测项以本页顶部为准。当时用户已明确批准：合并PR #1，固定测试origin `https://106.53.126.69:9443`，创建本项目专用RSA/APK测试签名并加密保存，配置本私有仓库六项Android Secrets，构建并验证alpha.5首装包。**当时授权不包含隔离服务器部署、停止现用服务、真实身份迁移或手机安装；隔离部署后来另获批准并已完成。** PR #1已合并为`1e405ad`；受控构件输入为`1a4b4c0ced91d1a384e723374a6141d84e43de9e`，经分支CI通过后main非强制快进到同一提交。

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
