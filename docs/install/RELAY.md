# 服务器中继：本地隔离安装与验证

本指南用于在开发电脑上启动**独立的回环原型**，不接管现用中继。界面和版本说明见[安装指南索引](./README.md)与[中继版本历史](../versions/RELAY.md)；源码边界见[relay/README.md](../../relay/README.md)。当前没有公网部署证据，也没有完整的 HTTPS + FRPS 自动安装流程。只启动 `127.0.0.1` 服务，手机无法因此接入。

**中继自身 OTA 也属于必做范围：**目标由管理员工具在线检查、下载并验证签名包，在安全维护窗口切换服务，保留账号与设备数据并验证健康。它不同于服务器向手机分发 APK，当前 `--version` / `--release-notes` 不是更新命令；具体条件见 [OTA.md](../OTA.md)。

## 准备与自测

先按[下载说明](README.md)取得源码，使用 Python **3.12**，仅需标准库；没有 Python 的 Windows 电脑可参考[运行时准备](WINDOWS.md)。本页命令以 Windows PowerShell 为例，Linux 环境的 Python/服务管理需另行配置。在新仓库根目录打开 PowerShell，先确认实际调用的解释器：

```powershell
python --version
python -B relay/control_server.py --version
python -B relay/control_server.py --release-notes
python -B -m unittest discover -s relay -p 'test_*.py' -v
```

第一行应显示 `Python 3.12.x`。版本命令只读取本地 `relay/releases.json`，不需要数据库、不启动监听器；它不能证明现用服务器版本。测试使用临时数据库、随机合成凭据和回环端口，不连接真实账号服务或公网。若本机 `python` 不是 3.12，请在以下命令中统一替换为已核实的 Python 3.12 可执行文件。

## 可停止的本地隔离启动

先确认 `28181`（认证）、`28191`（控制 API）、`28192`（FRP plugin）未被其他进程占用；若占用，选另外三个位于 1024–65535 的空闲端口，并同步修改下方环境变量和命令参数。以下步骤只创建一个新的临时目录，不使用仓库或现有服务的数据目录。

```powershell
Get-NetTCPConnection -State Listen -ErrorAction Stop | Where-Object { $_.LocalPort -in 28181,28191,28192 }
```

无输出表示检查当时未发现这三个 TCP 监听器；若命令报错，先查明原因，不能把查询失败当作空闲。启动时若端口已被占用，保留占用者并换端口，不结束别人的进程。

**终端 A：**在新仓库根目录的 PowerShell 中运行。记下打印出的临时目录绝对路径，供终端 B 使用。

```powershell
$runRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("codex-desktop-remote-relay-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $runRoot -ErrorAction Stop | Out-Null
$runRoot
$env:CODEX_RELAY_AUTH_DB = Join-Path $runRoot 'auth.sqlite3'
$env:CODEX_RELAY_AUTH_PORT = '28181'
python -B relay/auth/auth_server.py set-account --username localtest
if ($LASTEXITCODE -ne 0) { throw '测试账号创建失败，停止启动认证服务' }
python -B relay/auth/auth_server.py serve
```

`set-account` 的密码只通过交互式 `getpass` 输入两遍，长度至少 12 个字符，不放在命令行或脚本里。**它会替换该数据库的唯一账号并清空所有 session**，因此绝不能把 `CODEX_RELAY_AUTH_DB` 指向现用或已有账号数据库。此处每次生成全新的目录。`serve` 显示 `Relay identity listening on 127.0.0.1:28181` 后保持前台运行；在终端 A 按 `Ctrl+C` 只停止这一轮认证进程。

**终端 B：**另开一个 PowerShell，也切到新仓库根目录。按提示粘贴终端 A 打印的临时目录绝对路径；不要粘贴账号密码或令牌。

```powershell
$runRoot = Read-Host '粘贴终端 A 打印的临时目录绝对路径'
if (-not (Test-Path -LiteralPath $runRoot -PathType Container)) { throw '临时目录不存在' }
$env:CODEX_RELAY_AUTH_DB = Join-Path $runRoot 'auth.sqlite3'
$env:CODEX_RELAY_AUTH_PORT = '28181'
python -B relay/control_server.py --db (Join-Path $runRoot 'terminals.sqlite3') --auth-port 28181 --port 28191 --plugin-port 28192
```

终端 B 的命令也保持前台，正常运行时不一定打印启动提示；按 `Ctrl+C` 停止本轮控制 API 和它创建的 plugin 监听器。没有传入 `--enable-terminal-forwarding` 或 `--transport-config`，所以此演示**没有任务转发通道**。

另开一个终端进行不含凭据的检查：

```powershell
Invoke-RestMethod 'http://127.0.0.1:28181/auth/status'
try { Invoke-WebRequest -UseBasicParsing 'http://127.0.0.1:28191/control-api/version' } catch {
    if ($null -eq $_.Exception.Response) { throw }
    [int]$_.Exception.Response.StatusCode
}
```

前者应显示 `configured: True`，后者应为 **401**；这分别说明测试账号已配置及控制 API 拒绝匿名查询，不是手机接入验收。不要为此把真实令牌放入命令行。完成后先停止两个前台进程，关闭这两个终端即可释放本轮进程环境变量；临时数据留在打印出的目录，确认不再需要后仅清理该目录，不要清理任何现有中继目录。

## 公网接入仍需单独完成

手机实际接入还需要经审核的公网 HTTPS 入口和证书、单账号登录服务、隔离的设备注册库、FRPS TLS 与可信 CA/传输密钥、服务器私有端口池，以及 Windows 连接器和 HTTP adapter 的端到端验证。入口需要按实际协议分别路由 `/auth/*` 到认证服务，`/control-api/*`、`/connector-api/*`、`/terminal-api/*` 到控制服务；`/internal/frp` 属于 FRPS 内部 plugin 回调，**不得映射到公网 HTTPS**。`/terminal-api/*` 还依赖显式启用转发、私有 `--transport-config`、已配对设备与健康探针；仅运行上面的 control 命令不会使它可用。

公网配置必须先核对现有 Caddy/FRPS 的端口、证书、路由、权限、备份和回退方案，不能直接复用上述开发端口或把本指南当部署脚本。只有实际部署并重启新版 relay 进程后，已认证的 `GET /control-api/version` 才能反映其启动时载入的版本记录；本地源码版本、测试通过或其他组件更新都不证明服务器已更新。
