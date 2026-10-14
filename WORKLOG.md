## 2026-10-14 — Android alpha.7独立发布候选（未合并、未受控签名）

本分支只从已测试功能提交e75a209提取Android代码、测试、版本目录及Android CI，不合入Windows连接器或中继代码。包含此前alpha.6的明确选择电脑/通知目标隔离，以及alpha.7的手机点击标题按需加载原对话。Windows alpha.14候选与实机验证记录在PR #8；本分支的Windows/relay版本仍为main基线，不能当成当前主电脑安装版本。

手机显式点标题或重试附open=1；待加载后只用普通读取等待，后台、分页和通知不导航。不发送唤醒消息，不复制对话，不改Desktop权限。需要Windows alpha.14配合；可能切换电脑Desktop页面。旧连接器忽略参数，仍须在电脑打开对话。

源提交Android CI 38513358589编译、单元测试、lint、APK版本记录检查与19项模拟器测试均通过；artifact上传因存储额度失败。本分支提取后Node33/33、Python118通过/6跳过；沙箱权限失败后正常用户权限复跑通过。独立分支CI另记。受控签名工作流要求main，须先审查并批准合并本PR才能构建同证书APK。普通debug构件不能覆盖现用测试APP，不卸载绕过；当前未发布、未安装、未修改Secrets或私有源。

# 工作记录

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
