# alpha.5 真实设备基线记录（进行中）

这一步承接[隔离服务与公网验收](OTA-ISOLATED-DEPLOYMENT-20261008.md)，目标是先在测试手机和主电脑建立 alpha.5 的同任务交互基线，再逐组件测试 alpha.6 OTA。服务器合成探针、安装成功和手机真实任务完成分别记录。

## 已完成与当前停点

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
