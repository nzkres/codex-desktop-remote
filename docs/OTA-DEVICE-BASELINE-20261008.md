# alpha.5 真实设备基线记录（进行中）

这一步承接[隔离服务与公网验收](OTA-ISOLATED-DEPLOYMENT-20261008.md)，目标是先在测试手机和主电脑建立 alpha.5 的同任务交互基线，再逐组件测试 alpha.6 OTA。服务器合成探针、安装成功和手机真实任务完成分别记录。

## 已完成与当前停点

**最新结果：** 用户已经完成手机发送与 Desktop 接续，两条测试回复都在原任务完成，但手机截图显示“Desktop 任务权限已变化”，任务闭环未通过。原因与 alpha.6 源码修复见本页末尾；前面的成功探针是接续发生前的历史证据。

| 项目 | 实际证据 |
| --- | --- |
| Windows 全新安装 | 使用原始签名 alpha.5 Windows 包与 bootstrap，安装到主电脑 `%LOCALAPPDATA%\CodexDesktopRemoteTest`；包签名、大小、SHA、清单、current 和 ledger 已验证，未复制旧身份或配置 |
| 外部依赖 | Python 3.12.14 / Tk 8.6 / cryptography 50.0.1、Node 20.19.0、FRP 0.71.0；Desktop 安装包为 26.930.7945.0，app.asar 与内置 codex.exe 两项固定指纹通过 |
| 实际 Desktop 进程 | 只读进程查询确认运行中的主进程是该包内的 `app/ChatGPT.exe`；没有仅凭安装包存在推定实际运行版本 |
| 专用任务 | 继续使用用户此前选定的「回复 DESKTOP_REMOTE_READY」，UUID `01a1158c-72ea-7db3-84d1-b47471220252`；新配置使用独立 loopback 端口，未替换旧连接器状态 |
| 只读任务探针 | 初次 `owner_unavailable`，打开历史页后仍未通过；IPC initialize 成功、owner discovery 立即返回远端 no-handler，应用查询为 notLoaded。用户在原任务完成 `OTA_ALPHA5_READY` 后，同 UUID 变为 idle，安装槽 probe 通过、HTTP 历史验证 true；自动发送模型输入0 |
| 权限与空闲 | 安装槽 update_probe 返回 idle=true、version=0.1.0-alpha.5、permissionsVerified=true、receiptsCertain=true；新配置权限摘要经真实只读探针建立，未改 Desktop 权限 |
| 图形启动 | 初次逻辑路径启动报 `managed_data_directory_required`；确认 AppX 重定向前后六项文件 samefile 且已签槽完整后，以真实物理路径启动成功。自有 GUI 子进程存在、窗口标题 `Desktop 手机联调 · 0.1.0-alpha.5`、主窗口句柄非0、stderr为空 |
| 测试账号交接 | 仅将新隔离环境的测试账号通过内存封装为本机 CurrentUser DPAPI，在独立 operator 私有目录保存；本机辅助窗口按需短时显示口令。无明文文件、聊天口令、命令行口令或旧账号迁移 |
| 手机与真实 OTA | 用户报告已安装本次 alpha.5 APK，停在登录页；尚未独立读取手机版本/证书。已提供登录和配对步骤；新身份配对、真实任务闭环及 alpha.6 OTA 尚未验收 |

账号显示窗口是本次维护辅助工具，位于版本目录之外，不是签名客户端的新功能。它不进入程序包，不改变 alpha.5 文件或软件版本。

## 从当前现场继续

1. 原任务已由用户手动完成 `请只回复 OTA_ALPHA5_READY，不调用工具、不读写文件。`，只读探针通过。保持该任务可用；不要自动代发或重放未知消息。
2. **不要重复运行 configure.py 或 bootstrap**，已有安装、配置与窗口保留。当前已经用真实物理路径成功启动；以后统一沿用经核验的物理 home，不改配置使其迁就逻辑别名。需要再验时只运行 probe，不使用 `--send-literal`。
3. 用户已报告手机安装完成；服务器填 `https://106.53.126.69:9443`，账号从本机「隔离测试账号」窗口取得，不发到聊天。后续核对 App 内版本与实际登录状态；保留旧 App，不默认卸载。
4. 手机生成一次性配对码，用户输入新 Windows 窗口并在手机确认。只为新安装生成身份，不复制旧 DPAPI 身份；分别验收登记、通道和测试任务真实完成。
5. 完成手机发送 → Desktop 同一 UUID 接续 → 手机显示的 alpha.5 基线，记录实际版本与权限保持，再按[落地验收](OTA-ACCEPTANCE.md)准备 alpha.6。当前不能写成 OTA 已成功。

维护者重跑只读探针的命令（使用已核验的 Node 20.19.0 绝对路径）：

```powershell
$InstallRoot = '<VERIFIED_PHYSICAL_INSTALL_HOME>'
$Pointer = Get-Content -Raw -Encoding utf8 (Join-Path $InstallRoot 'current.json') | ConvertFrom-Json
$VersionRoot = Join-Path $InstallRoot $Pointer.path
& '<VERIFIED_NODE_20_19_EXE>' (Join-Path $VersionRoot 'scripts/probe.mjs') (Join-Path $InstallRoot 'data/fixture.local.json')
```

此命令只用于已经核验、没有发生事务切换的本次安装；诊断安装槽内 Python 模块须使用 `-B` 或 `sys.dont_write_bytecode=True`，避免产生额外 pyc 而触发完整性保护。只保存脱敏结果，运行数据不入 Git。当前本机安装/探针摘要在忽略目录 `artifacts/isolated-deploy-20261008/windows-first-install.json`。

## 已发现的启动缺陷与本次处理

`scripts/configure.py` 将 home 经 `resolve(strict=True)` 写入配置，而 `windows/update_driver.py` 只对启动 home 做 absolute 后检查 dataDir 的词法包含关系。从打包的 Codex 环境访问 `%LOCALAPPDATA%` 时，Windows 将目录重定向到 `%LOCALAPPDATA%\Packages\OpenAI.Codex_2p2nqsd0c76g0\LocalCache\Local\CodexDesktopRemoteTest`；启动器仍收到逻辑别名，因此拒绝启动。不是版本指纹失败，也不能仅以主窗口句柄0推定进程已退出。

本次以 Python `Path.resolve(strict=True)` 取得物理 home，逐项核对 `Start.pyw`、current、install、trust、ledger、fixture 的 `samefile`，通过原有拒链接与全槽校验，再从物理 home 启动**未改动的签名入口**。没有移动目录、复制身份、编辑签名文件或放宽保护；错误启动未持有应用锁，新 GUI 通过自身独占锁后打开。原错误弹窗由用户正常关闭，不按 PID/名称杀进程。

后续 alpha.6 候选修复：配置、稳定启动器和更新助手统一检查并使用物理 home，补真实入口与 AppX 重定向回归。现有生命周期 smoke 使用临时规范路径，未覆盖本次组合；不能只靠文档宣称缺陷已在产品修复。当前是可用的受控启动方法，alpha.5 发布包保持不变。

本轮未改产品代码、未新增软件版本；既有 CI 结果仍是先前提交的证据。原任务恢复与只读探针通过不等于手机真实任务完成，更不等于 OTA 升级成功。

## 手机首次登录后顶部 HTTP 404（已定位，未修包）

用户确认手机已登录 `https://106.53.126.69:9443`，首页顶部出现 HTTP 404。Android 的 `TaskTarget()` 默认仍是空 ID 的旧通道；`MainActivity` 登录后虽进入远程终端页，但后台 refresh 不检查是否已选择电脑，先请求 `/api/projects`。新中继只按 `/terminal-api/{deviceId}/api/...` 转发任务，不提供无电脑 ID 的旧任务接口，因此产生全局 syncError。

主电脑正常 TLS 只读复核：`/auth/status` 200；`/api/projects`、`/api/sessions`、`/api/activity` 均404；未带登录凭据的 `/control-api/terminals` 为401（路由存在、要求登录）。这不是手机抓包，具体失败请求由客户端代码顺序和服务器响应共同定位；没有输出凭据或读取手机私有日志。

当前继续步骤：在远程终端完成新电脑配对，显式选择该电脑的任务入口；选择目标会清空旧 syncError，并切到该设备路由。不能只关闭报错条就声称修复，也不把未选择目标时的404当作电脑离线。下一版候选：未选择电脑时不轮询旧任务接口，显示添加/选择电脑引导；保留登录失效、真实404和已选电脑不可用的错误区分，不自动改选执行目标。实际配对和手机任务成功仍待用户操作及验收，alpha.5原包保持不变。

## 配对申请停在提交中：电脑端口误填（恢复进行中）

用户手机画面已生成一次性码并等待电脑申请，电脑则一直显示“正在提交配对”。只读检查本轮新安装的DPAPI状态，仅输出白名单字段：`state=claim_pending`、`lastError=connection_unavailable`、runtimeNeedsReview未启用；公开地址解析为 `https://106.53.126.69:944`，与正确的 `:9443` 不同。没有输出配对码、令牌或完整身份。顶部旧任务404与本次错误端口是两个问题。

alpha.5 在发送前持久化申请，已有pending身份时再次点击配对会拒绝；仅编辑地址框不能修改已保存的申请。界面未呈现网络失败原因，也没有取消失败申请后重新配对的受控入口。这两项属于后续版本待修缺口，不能通过删除身份或保护标志临时绕过。

本轮保留 `CodexDesktopRemoteTest` 整个失败安装，不重置或迁移其中状态，另在同一已核验物理父目录创建 `CodexDesktopRemoteTest9443`。使用同一个原始签名alpha.5包/bootstrap，全新安装与只读任务probe通过，新权限摘要与先前选定任务一致；没有复制身份、回执或发送模型输入。用户已确认正常关闭旧电脑窗口并在手机取消当前配对；随后取得原应用锁、运行锁和操作锁，核对错误端点及未确认状态未变、无pairingId/terminalId/runtime绑定。新窗口已实际打开，标题alpha.5、主窗口句柄非0、stderr为空。等待用户手动输入正确地址和新配对码、手机确认后连接；账号仍用原本机DPAPI显示窗口，手机不重装。

新的脱敏安装记录在忽略目录 `artifacts/isolated-deploy-20261008/windows-retry-install.json`。首次失败目录继续保留供复核；随后新配对验收结果如下。

## 正确端点的新配对与实时通道已通过

用户报告手机已确认新电脑、电脑已点击连接。独立脱敏核验：身份存在且服务器精确匹配9443，state=connected、lastError=null、terminal已分配、adapterBinding存在。本机LoopbackHealthProbe验证实例身份通过；使用现有幂等tunnel_probe经正常HTTPS令中继探测既定私有端口，publicTunnelProbe=true。活动适配器GET update-status为200，返回alpha.5、idle=true、permissionsVerified=true、receiptsCertain=true；诊断前后relay/deviceToken/terminalId/pairingId/adapterBinding完全相同，没有自动发送模型输入。

运行中的runtimeNeedsReview为true是连接器持锁启动前写下的保护，不等于这次探针失败；不能为消除提示删除它。没有断开、重启或迁移这条新连接。脱敏证据在忽略目录 `artifacts/isolated-deploy-20261008/windows-live-connection.json`。

随后用户完成了这两个操作，但手机未同步；故障和修复证据如下，不能再重发这两条测试消息。

## Desktop 接续后手机权限误报（alpha.6 源码已修，未安装）

用户截图明确显示“Desktop 任务权限已变化，请在电脑端重新核对”。原 UUID 的手机回合与 Desktop 接续回合均 completed，分别包含 PHONE_ALPHA5_OK、DESKTOP_ALPHA5_CONTINUE_OK。活动 alpha.5 的登记仍 connected、health200，但 update-status 和 mobile-sync 都返回409/permissions_changed。这次不是配对、TLS或转发断线。

接入就绪回合记录 approvalsReviewer=guardian_subagent，后续手机与 Desktop 回合为 auto_review。对最新只读快照仅替换这一字段，完整 permissionDigest 恰好等于原配置保存值；其余权限无需改动。官方 [Codex 协议类型](https://github.com/openai/codex/blob/main/codex-rs/protocol/src/config_types.rs)用 `serde(rename = "auto_review", alias = "guardian_subagent")` 定义兼容；本机固定26.930.7945.0的 app.asar 中 `app-shared-e32c0e36d554.js` 也通过 SM 同时接受两者。外部源码作为含义佐证，实机差异判断来自固定安装和精确旧摘要匹配；未修改 Desktop 文件。

Windows alpha.6 的 permissionsMatch 先按原算法比较，失败后只尝试这一顶层值的另一兼容名称并重新比较所有权限；没有重建或改写 saved hash。read/updateStatus/send/stop 和 probe 统一使用；真正的 reviewer、审批方式、网络、目录、profile 或嵌套扩展差异仍拒绝，不能全局替换字符串或忽略审批字段。

验证：Node45/45（含旧摘要正反方向及10类差异拒绝、实际快照与完整配置不变），Python124项118通过/6跳过。新源码只读跟随真实原任务，用同一 mobile-sync 转换取得两条精确回复；原始摘要仍不同而 aliasMatch=true，实时 idle/permissionsVerified/receiptsCertain=true，模型输入0。current/install/trust/ledger/fixture/加密身份及回执文件摘要前后相同。脱敏报告在忽略目录 reviewer-alias-source-verification.json；它是候选源码验证，不是运行安装升级或手机屏幕验收。

当前不重复配对、不重发消息，也不改已签 alpha.5。普通 OTA 的旧 GUI/helper 会先执行旧版本 update_probe，仍因同一缺陷拒绝；回退旧版也无法保证其权限健康检查通过。维护切换必须单独验证签名候选、原进程正常退出、独占锁、原身份/配置/回执保持和失败停点，再由用户确认实际切换；不能用修改 permissionsHash、删除保护或强停进程绕开。
