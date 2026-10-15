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

| 层次 | alpha.5 当前证据 |
| --- | --- |
| 核心 CI | [分支37736389583](https://github.com/nzkres/codex-desktop-remote/actions/runs/37736389583)与[main37736836192](https://github.com/nzkres/codex-desktop-remote/actions/runs/37736836192)成功。Node Windows33通过、Linux31通过/2跳过；Python124项：Windows118通过/6跳过、Linux104通过/20跳过；relay77项：Windows73通过/4跳过、Linux77通过；续证钩子Linux3通过、Windows3跳过 |
| 隔离运行事务 CI | 真实Windows GUI/pythonw独立helper升级0.0.2及坏候选回退0.0.1；Linux委派cgroup服务升级0.0.2、坏候选回退0.0.2及4棵自有树清理通过。合成数据/探针，不等于真实Desktop任务验收 |
| 受控 Android CI | main [37736837251](https://github.com/nzkres/codex-desktop-remote/actions/runs/37736837251)，94 JVM测试通过、lint 0 errors / 45 warnings、编译、apksigner单签名者/证书校验、APK内trust逐字节比对全部成功；清理签名输入成功 |
| 下载后复验 | APK SHA与CI准确一致；Google官方AAPT2核对真实包名`dev.codex.desktopremote.lite`、versionCode5、minSdk26/targetSdk36；包内说明与Git原始字节、trust与受控输入相同。APK仍为debuggable受控测试构建，无手机/模拟器安装 |
| 源码运行包 | 使用Linux CI构件：Windows43/relay29源码文件、完整manifest、逐文件SHA与Git `1a4b4c0`原始字节全部一致；没有运行时或发行私钥 |
| 已签首装资料 | 三组件test元数据均使用专用RSA签名并复验，sequence=1；`bootstrap-0.1.0-alpha.5.zip`含各组件`trust.json`/`latest.json`及README。有效至2026-10-15 06:33:03 UTC（北京时间14:33:03）；过期需新序号重新签发相同包，不能关闭验证 |
| 实际临时首装 | Windows Python3.12.14、WSL Linux Python3.14.4新临时home：实际alpha.5签名/清单/ledger/current通过；重复安装拒绝且原文件未变。未配置账号/任务、未启动运行服务；WSL不是目标服务器Python3.12验收 |
| 私有归档 | [v0.1.0-alpha.5](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.5)已发布为私有prerelease，7个附件全部uploaded，服务端大小/SHA与本地一致；标签输入1a4b4c0。GitHub归档不是已部署的私有HTTPS更新源 |
| 尚未执行 | 公网源部署、手机首装、真实Windows连接/身份迁移、真实三端alpha.5→alpha.6与失败演练。没有EXE安装器或托盘，Windows/relay仍需外部依赖；独立密钥恢复未验收 |

| alpha.5 程序包 | 字节数 | SHA-256 |
| --- | ---: | --- |
| android-0.1.0-alpha.5.apk | 11,772,524 | `4fc366cd4580930ca020c5535d1f2798de7991fbad214e81c0f4ae822c111d3f` |
| windows-0.1.0-alpha.5.zip | 92,070 | `572c81a414b14f920c48e01007b85f73e29403e75ab25b97fdedcd7d95b1bb17` |
| relay-0.1.0-alpha.5.zip | 74,465 | `21b970e91df34bac9e74737b749f082cfacc379771db6e15d9d8caefc7795fe0` |

下一阶段按[部署审查](docs/OTA-DEPLOYMENT-REVIEW.md)重新核对现场与短期TLS证书，确认具体公网部署后才建立隔离环境。alpha.6尚未构建；先完成真实N基线，再逐组件升级。安装输入与步骤见[安装起点](docs/install/README.md)。

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
