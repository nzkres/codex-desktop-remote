# Windows 连接器：准备、打开窗口与首次连接

返回[安装起点](README.md)；每版变化见 [Windows 版本历史](../versions/WINDOWS.md)。本指南对应 `0.1.0-alpha.2`。当前提供 Python 图形窗口源码，尚无 exe 安装器、托盘或在线更新；首次准备仍需 PowerShell。已有安装与运行状态不能直接从旧项目复制进来。

## 1. 取得源码与 Python，先打开窗口

按[下载说明](README.md)取得 alpha.2 源码并解压。在仓库根目录空白处打开终端（PowerShell）；命令中的 `<...>` 是要替换的本机路径，不能原样粘贴。用当前 Windows 用户运行，不需要管理员权限。

窗口需要 **Python 3.12 + Tkinter**。已经有完整的 Python 3.12 时直接复用，核对实际路径即可；仓库本机验证使用 3.12.14，CI 使用 3.12 系列。没有时，从 [Python 官方 Windows 下载页](https://www.python.org/downloads/windows/)取得 Python install manager，然后安装 3.12 完整运行时：

```powershell
pymanager install 3.12
pymanager list --format=exe 3.12
```

把打印的 `python.exe` 完整路径填入下一段。该获取方式依据 [Python 官方 Windows 说明](https://docs.python.org/3/using/windows.html)，本仓库未对全新电脑执行安装验收；取得的具体补丁版需记录并运行自测。不要选择 `-embed` 包：它不带窗口需要的 Tcl/Tk。无需安装 pip 包或执行 `npm ci`。

```powershell
$PythonExe = '<PYTHON_EXE_PATH>'
& $PythonExe --version
& $PythonExe -c "import tkinter; print('Tk', tkinter.TkVersion)"
& $PythonExe windows/demo.py
```

预期：Python 为 `3.12.x`，Tk 导入成功，打开标题包含“界面演示 · 合成数据”的窗口。底部显示连接器版本，点击“版本与更新说明”能读到 alpha.2、alpha.1。这个窗口不保存身份、不连接服务器或 Desktop；即使点连接后状态变化，也只是演示。看完正常关窗即可。到这里已完成**界面预览**，还没有建立真实连接。

## 2. 实际连接前准备四项条件

| 条件 | 怎样准备与确认 |
| --- | --- |
| Node `20.19.0` | 从 [Node 官方版本页](https://nodejs.org/en/blog/release/v20.19.0)下载 Windows x64 ZIP 并单独解压，记下 `node.exe` 路径；不用替换系统其他 Node |
| FRP `0.71.0` | 从 [FRP 官方 Release](https://github.com/fatedier/frp/releases/tag/v0.71.0)取得 Windows amd64 包并单独解压，记下 `frpc.exe` 路径；不用启动 frps 或安装 Windows 服务 |
| 原生 Codex Desktop | 当前只接受 **`26.930.7945.0`** 与源码固定的两项文件哈希；保留现用版本，若不匹配就停在预览阶段，交由后续兼容验证处理 |
| 测试 HTTPS 中继与手机 | 使用维护者核验过的地址及账号，在手机安装本项目 APK 并登录；本地 relay 启动不等于完成此条件，见[中继指南](RELAY.md) |

以上为当前 x64 测试组合，不代表其他架构或“更新版本”已经验证。下载工具时核对官方来源及其校验信息；配置脚本计算本地文件指纹只能防止后续换包，不能证明下载来源可信。Python/Node/FRP 不随源码提供，Desktop 是外部依赖，连接器不修改它的安装文件或伪装版本。

在同一 PowerShell 中填写并检查：

```powershell
$NodeExe = '<NODE_EXE_PATH>'
$FrpcExe = '<FRPC_EXE_PATH>'
& $NodeExe --version
& $FrpcExe --version
.\scripts\SelfTest.ps1 -Node $NodeExe -Python $PythonExe
```

前两行应为 `v20.19.0`、`0.71.0`，自测末尾应为 `Core self-tests passed`。自测仅使用合成身份和隔离端口，不需要 FRP 正在运行，也不访问真实任务。PowerShell 若受组织脚本策略限制，可按 [测试指南](../TESTING.md)逐条执行 Python/Node 命令；不要关闭整机脚本安全策略。

## 3. 准备一条原生 Desktop 测试任务

在 Desktop 中为一个独立测试目录新建专用任务，发送：

> 请只回复 DESKTOP_REMOTE_READY，不调用工具、不读写文件。

等待明确回复 `DESKTOP_REMOTE_READY`，保持 Desktop 开启，任务没有活动回合或待处理审批。记录该任务链接中的线程 UUID、实际工作目录及当前授权范围。这里沿用该原生任务的权限，不提供扩大权限的开关。

还要取得 Desktop **安装包根目录**，其下应有 `AppxManifest.xml`、`app/resources/app.asar`、`app/resources/codex.exe`。这是应用包目录，不是项目目录或 `.codex` 用户数据目录。任务 UUID 或包路径拿不准时先核对，不猜值、不修改 Desktop 数据库。兼容检查依据为 [src/compatibility.js](../../src/compatibility.js)。

```powershell
& $PythonExe scripts/configure.py --thread-id '<TASK_UUID>' --project '<PROJECT_PATH>' --desktop-root '<PACKAGE_ROOT>' --node $NodeExe --frpc $FrpcExe
```

成功应显示 `Test configuration prepared. No model input sent and no device paired.`。脚本新建本地 `fixture.local.json`，核对原生任务 marker、版本/文件哈希并记录权限摘要；会写入本地配置和数据目录、临时监听回环端口，但不发模型消息、不配对。失败会保留配置，不会覆盖已有文件；不要通过清空权限摘要或改 pin 绕过。

若是这一次**尚未配对的新测试配置**探测失败，先核对原因（例如 marker 尚未回复、Desktop 没有开启）。维护者确认配置仍指向同一专用任务、路径正确、没有现用实例后，可对保留的配置重跑无发送探针，无需再次运行会拒绝覆盖的 `configure.py`：

```powershell
& $NodeExe scripts/probe.mjs fixture.local.json
```

不加 `--send-literal`。只有退出码为 0，报告含 `result: passed`、`httpHistoryVerified: true`、`modelTurnsStarted: 0` 后，才继续下一步；权限摘要未初始化时由探针写入，已有摘要不匹配仍会阻断。配置路径填错或再次失败时交维护者核对，不删除身份、不清空摘要，也不能把此步骤用于解除已运行实例的复核保护。

如果已有现用连接器或此前配对的目录，先读[恢复说明](../../RECOVERY.md)。新配置不是接管工具，也不授权结束现用 Gateway/FRP、复制旧身份或切换当前任务。

## 4. 打开真实窗口，完成手机确认

从所选 Python 的同一目录使用 `pythonw.exe`，不要混用另一个 Python：

```powershell
$PythonwExe = Join-Path (Split-Path -Parent $PythonExe) 'pythonw.exe'
& $PythonwExe windows/controller.py fixture.local.json
```

1. 在窗口填“服务器 HTTPS”和容易辨认的测试“设备名”。地址应与手机一致，不能填写示例地址、GitHub 地址或手机无法访问的 `127.0.0.1`。
2. 手机“远程终端 → 添加电脑”生成一次性配对码；在电脑窗口输入，点击“配对”。
3. 手机刷新待确认项，核对电脑名，点击“确认加入”并完成确认；回到电脑点“连接”。密码、配对码和令牌不要放进命令行或截图。
4. 本目录曾成功配对且正常断开时，再次打开使用“连接”，复用同一 Windows 用户的本机身份，不重复配对。本项目身份保存在忽略的 `data/identity`，任务记录在 `data/fixture-...`；不要删掉它们排错或带去另一台电脑。

真实连接可能显示“转发通道尚未实时检查”，它表示保存的快照尚未做本轮验证，不单独等于通道故障；“转发通道探针通过；手机任务待验证”也不等于模型已完成手机请求。

## 5. 验收、日常打开和关闭

只操作上一步的专用任务：手机读取 `DESKTOP_REMOTE_READY` 历史；发送一次“请只回复 PHONE_REMOTE_OK，不调用工具、不读写文件。”并等到明确完成；在 Desktop **同一任务**发送“请只回复 DESKTOP_CONTINUE_OK，不调用工具、不读写文件。”，最后在手机看到该回复。发送结果未知时先在原任务核对，不能重复发送来猜测是否成功。详细记录按 [TESTING](../TESTING.md)填写。

首次配置完成后，可用 Windows“新建 → 快捷方式”：目标填写带引号的 `pythonw.exe` 完整路径、`windows/controller.py` 的完整路径、`fixture.local.json` 的完整路径（依次空格分隔），起始位置填仓库根目录。此后双击快捷方式打开窗口、点“连接”；快捷方式不包含凭据。不要移动正在使用的源码、配置或运行时。

点击“断开”并等待“已安全断开”才能做正常重连。关闭窗口同样先断开并退出，**不会缩到托盘，也不会停止 Desktop 的模型任务**；手机会失去此连接。没有开机自启或无人值守崩溃恢复承诺。

## 遇到阻断

| 现象 | 下一步 |
| --- | --- |
| `tkinter` 导入失败 | 核对正在使用的 Python 完整路径和 Tcl/Tk 安装，别用 embeddable 包 |
| `configuration_already_exists` | 使用本目录原配置，核对是否属于这条测试任务；不要覆盖或删身份重来 |
| `desktop_fixture_unverified_config_retained` | 保留配置，核对 Desktop 版本/文件、任务 UUID、目录、marker 与权限；普通用户错误不含私密 Desktop 输出 |
| “启动配置无法核对” | 确认完整源码、Python 与配置路径；只记录脱敏错误，不上传整个配置/日志 |
| 已有实例、端口占用或旧连接器状态 | 使用原窗口或交维护者核对归属，不按进程名称批量终止 |
| 前次退出需复核／`runtimeNeedsReview` | 保留身份、回执与现场，按[恢复说明](../../RECOVERY.md)处理，不能删除保护标志 |
| 设备在线但不能发任务 | 分别核对登记、转发探针和 Desktop 任务状态；不要把在线当可执行，不扩大权限 |

本仓库已有自动测试和合成窗口验证；新仓库的真实配对、手机闭环及新安装验收仍按 [STATUS](../../STATUS.md)独立记录。
