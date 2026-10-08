# Windows 连接器：源码预览与全新受管安装

返回[安装起点](README.md)，版本变化见 [Windows 历史](../versions/WINDOWS.md)。[alpha.5 私有 prerelease](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.5) 已发布 `windows-0.1.0-alpha.5.zip` 和独立的 `bootstrap-0.1.0-alpha.5.zip`；服务端大小/SHA 与本地一致，详见 [STATUS](../../STATUS.md)。它是受控测试渠道的**源码运行包**，不是 EXE 或一键安装器。测试 origin `https://106.53.126.69:9443` 已从主电脑公网通过正常 TLS、账号鉴权和完整包校验；公网 FRPS `17443` 的合成 FRPC 夹具也已连通。alpha.6 已在测试电脑经受控维护安装，用户确认手机看到同一 Desktop 任务的两条接续回复；这不证明普通 GUI OTA 或 alpha.7 已验收，最新证据见[STATUS](../../STATUS.md)。全新受管安装不读取、迁移或停用旧连接器、旧 Gateway/FRP 和现用身份。

## 前置条件

**当前测试边界：** alpha.6 已通过限定维护路径安装，`guardian_subagent`/`auto_review` 别名造成的手机权限误报在同一任务接续后恢复；这条维护路径不是普通 GUI OTA，历史故障与保护见[设备基线记录](../OTA-DEVICE-BASELINE-20261008.md)和[维护说明](../WINDOWS-ALPHA5-MAINTENANCE.md)。alpha.7 已实现连接窗口的配对失败提示修正：提交结果或重开状态若为 `claim_pending` 且 `lastError=connection_unavailable`，应明确显示“申请尚未确认”，提示核对网络、HTTPS 地址、端口和证书；原申请保留，编辑输入框不能改变已提交申请，也不自动重新配对或删除身份。固定输入与服务器响应错误给出可理解的原因，未知错误不回显原始内容。AppX 路径问题和取消失败申请仍未解决。

alpha.7 的目标验收是从**已实际安装的 alpha.6** 在连接窗口走正常 HTTPS 检查、下载和安装，再核对真实运行版本、原身份/任务数据及手机同任务显示。候选现已通过两平台CI、43文件匹配及本机原密钥签名/验签；固定包、有效期和操作顺序见[alpha.7审查](../WINDOWS-ALPHA7-OTA-REVIEW.md)。**尚未发布到feed、未在线安装或手机验收，先等待维护者完成获批后的更新源核验。** Android 与中继仍为 alpha.5，Windows 仍需外部运行时，未提供一体化 EXE 或托盘。

- 同一个完整的 **Python 3.12 + Tkinter + `cryptography`** 环境用于 bootstrap、配置和启动；源码根目录的 `updates/requirements.txt` 声明 `cryptography>=41,<51`。本源码包不带 Python。
- Windows x64 **Node 20.19.0** 和 **FRP 0.71.0** 的独立可执行文件；脚本固定其 SHA-256，不能用新文件悄悄替换。它们不随源码 ZIP 自动安装。
- 原生 Codex Desktop **26.930.7945.0**，并匹配 [兼容检查](../../src/compatibility.js)内固定的 `app.asar` 与 `app/resources/codex.exe` 指纹；Desktop 是外部执行器，本项目不升级它。还需一条已完成 `DESKTOP_REMOTE_READY` 的专用测试任务及其 UUID、项目路径、Desktop 包根目录。
- 从已核验 bootstrap ZIP 的 `windows/trust.json`、`windows/latest.json` 取得测试渠道首装输入，并与 `windows-0.1.0-alpha.5.zip` 配套。先核对发行公钥的 `keyId=cdr-ota-test-20261008-v1`、DER SPKI SHA-256 `c3835e3e59b362e0b356d3a7b3589c255187ddce34692167f5160b0e5f5e3a4d` 与**已认证仓库记录**一致；同页 `SHA256SUMS.txt` 或 `artifact-verification.json` 不能独自证明来源。元数据 sequence 1，有效至 2026-10-15 06:33:03 UTC（北京时间 14:33:03）；过期需维护者重新受控签发，不能关闭验证。发布顺序见 [OTA 发布](../OTA-PUBLISH.md)。

若只想看界面，在源码根目录执行 `& $PythonExe windows/demo.py`；预览仅使用合成数据，不配对、不保存身份、不连接服务器。

## 全新受管目录

以下可用于**本地隔离临时目录**复验；目标测试电脑的真实连接仍须新身份配对与专用任务另行验收。下载两个 ZIP 并分别解到不同新目录；bootstrap 根目录已核对有 `README.txt`，`windows/` 下有 `trust.json`、`latest.json`。在 PowerShell 中填写绝对路径；`$InstallRoot` 必须尚不存在，不填旧安装或已配对 `data`。签名与整个 ZIP 清单由 bootstrap 核对。Windows Python 3.12.14 临时首装已成功，重复首装被拒且原目录未变；没有启动真实任务。

```powershell
$PythonExe = '<PYTHON_3_12_EXE>'
$SourceRoot = '<EXTRACTED_WINDOWS_ZIP_ROOT>'
$BootstrapRoot = '<EXTRACTED_BOOTSTRAP_ZIP_ROOT>'
$Package = '<DOWNLOADED_WINDOWS_0.1.0_ALPHA.5_ZIP>'
$InstallRoot = '<NEW_MANAGED_HOME>'
$Trust = Join-Path $BootstrapRoot 'windows/trust.json'
$Metadata = Join-Path $BootstrapRoot 'windows/latest.json'
& $PythonExe -B -c "import tkinter, cryptography; print('dependencies available')"
& $PythonExe -B (Join-Path $SourceRoot 'updates/bootstrap.py') --home $InstallRoot --trust $Trust --metadata $Metadata --package $Package --component windows
```

Bootstrap 验证签名、平台、版本和完整 ZIP 后创建 `data/`、`state/`、`versions/`、`staging/`、`trust.json` 与固定入口 `Start.pyw`；失败不会把旧目录变成新安装。**这不是迁移工具。**随后从同一已核验的 Windows 源码目录配置新实例，`--home` 会把全新 `fixture.local.json` 和任务数据放进受管 `data/`：

```powershell
$NodeExe = '<NODE_20_19_0_EXE>'
$FrpcExe = '<FRPC_0_71_0_EXE>'
& $PythonExe -B (Join-Path $SourceRoot 'scripts/configure.py') --home $InstallRoot --thread-id '<TEST_TASK_UUID>' --project '<TEST_PROJECT_PATH>' --desktop-root '<DESKTOP_PACKAGE_ROOT>' --node $NodeExe --frpc $FrpcExe
```

配置脚本只读探测专用 Desktop 任务的 marker、兼容文件与权限，**不发模型输入、不配对**；已有配置不会覆盖。只有它报告成功，且测试 HTTPS 中继与手机账号另行就绪，才启动：

```powershell
$PythonwExe = Join-Path (Split-Path -Parent $PythonExe) 'pythonw.exe'
& $PythonwExe (Join-Path $InstallRoot 'Start.pyw')
```

在窗口填经核验的 HTTPS 中继和设备名，手机生成一次性配对码、核对电脑申请并确认，然后电脑点连接。仅在预先选定的一条 Desktop 原生任务上验收读取、手机文字发送、Desktop 同任务接续及正常断开重连。`connected` 与通道探针不能代替任务完成证据。旧窗口和其身份目录保持原样；不能复制 `identity`、回执或旧 `fixture.local.json` 进入 `$InstallRoot`。

**alpha.5 实机启动注意：** 2026-10-08 已在主电脑新目录完成签名安装、原任务只读探针与实际窗口启动。由 Microsoft Store 打包的 Codex 环境操作 `%LOCALAPPDATA%` 时，可能发生 AppX 路径重定向：配置记录物理路径，但启动器收到逻辑别名，报 `managed_data_directory_required`。保留现场，由维护者核对两路径同一文件、拒链接及完整安装清单后，统一从已核验的**物理安装目录**调用原 `Start.pyw`；不要重装、重复配置或改保护。具体证据、恢复命令及后续版本修复项见[设备基线记录](../OTA-DEVICE-BASELINE-20261008.md)。真实手机任务与 OTA 升级仍需另行验收。

受管窗口底部可检查和下载签名更新。**本轮alpha.7私有源发布并验证前，请先不要点“检查更新”**：已安装alpha.6账本为sequence2，旧公网源仍可能为alpha.5/sequence1，防重放拒绝属预期保护，不能删除账本。发布就绪后，先正常断开，再“检查更新”并阅读说明，最后“下载并安装”；下载校验与助手交接成功后旧窗口才退出。弹窗中的“将验证并重开”描述成功路径，候选或回退失败仍可能停在需维护者复核状态，不能以窗口关闭证明升级成功。维护者可在源码根目录只读调用 `& $PythonExe -B -m updates.cli --home $InstallRoot status`，`check` 和 `stage` 会访问已配置的 HTTPS source，**普通 Windows OTA 的 `apply`/`stop` 命令会拒绝，应使用窗口安装按钮**。alpha.5 的旧审核别名误报曾使普通窗口预检拒绝，因此 alpha.5→alpha.6 使用仓库专用的[维护切换路径](../WINDOWS-ALPHA5-MAINTENANCE.md)；alpha.6→alpha.7 仍须以实际 GUI 在线事务验收，不可从此前维护切换推定成功。发生 `needs_review` 时保留 `data/`、`state/` 与版本目录，见 [恢复说明](../OTA-RECOVERY.md)。

alpha.4 增加私有下载：首次应先在手机完成新设备确认，再检查更新。它读取本安装已有 DPAPI 身份，不新增更新账号或复制其他电脑身份；缺失身份只提示未配对。更新 source 必须位于已配对 relay 的同 HTTPS origin 的 `/desktop-remote-updates/test/windows/`（stable 独立配置），否则在发凭据前拒绝。设备暂停、撤销或凭据无效时服务器拒绝下载。旧 alpha.3 中继不能提供此私有路由；新的受管 relay 已在隔离环境安装，主电脑公网合成设备 Windows 下载及权限/暂停/撤销拒绝已验证，真实电脑配对和 App 内下载仍待验收。
