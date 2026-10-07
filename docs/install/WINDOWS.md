# Windows 连接器：源码预览与全新受管安装

返回[安装起点](README.md)，版本变化见 [Windows 历史](../versions/WINDOWS.md)。alpha.3 开发预览提供源码 ZIP，可先手动预览窗口；下面的受管 OTA 命令对应同一版代码，**尚无长期 HTTPS 更新源、正式签名包或真实升级验收**。它只允许创建新安装目录，不读取、迁移或停用旧连接器、旧 Gateway/FRP 和现用身份。

## 前置条件

- 同一个完整的 **Python 3.12 + Tkinter + `cryptography`** 环境用于 bootstrap、配置和启动；源码根目录的 `updates/requirements.txt` 声明 `cryptography>=41,<51`。本源码包不带 Python。
- Windows x64 **Node 20.19.0** 和 **FRP 0.71.0** 的独立可执行文件；脚本固定其 SHA-256，不能用新文件悄悄替换。它们不随源码 ZIP 自动安装。
- 原生 Codex Desktop **26.930.7945.0**，并匹配 [兼容检查](../../src/compatibility.js)内固定的 `app.asar` 与 `app/resources/codex.exe` 指纹；Desktop 是外部执行器，本项目不升级它。还需一条已完成 `DESKTOP_REMOTE_READY` 的专用测试任务及其 UUID、项目路径、Desktop 包根目录。
- 经独立核验的测试渠道 `trust.json`、该渠道已签名 `latest.json` 和与之绑定的 Windows ZIP。公钥、source URL 由首次安装固定；**当前仓库没有可直接使用的正式密钥或公网源**。如何准备见 [OTA 发布](../OTA-PUBLISH.md)。

若只想看界面，在源码根目录执行 `& $PythonExe windows/demo.py`；预览仅使用合成数据，不配对、不保存身份、不连接服务器。

## 全新受管目录

在**源码根目录**的 PowerShell 中填写自己的绝对路径。`$InstallRoot` 必须是尚不存在的新路径，输入文件先单独核对来源和签名；不要填旧安装、已配对 `data` 或现用服务目录。

```powershell
$PythonExe = '<PYTHON_3_12_EXE>'
$InstallRoot = '<NEW_MANAGED_HOME>'
$Trust = '<REVIEWED_TEST_TRUST_JSON>'
$Metadata = '<SIGNED_WINDOWS_LATEST_JSON>'
$Package = '<MATCHING_WINDOWS_ZIP>'
& $PythonExe -B -c "import tkinter, cryptography; print('dependencies available')"
& $PythonExe -B updates/bootstrap.py --home $InstallRoot --trust $Trust --metadata $Metadata --package $Package --component windows
```

Bootstrap 验证签名、平台、版本和完整 ZIP 后创建 `data/`、`state/`、`versions/`、`staging/`、`trust.json` 与固定入口 `Start.pyw`；失败不会把旧目录变成新安装。**这不是迁移工具。**在同一源码根目录继续配置新实例，`--home` 会把全新 `fixture.local.json` 和任务数据放进受管 `data/`：

```powershell
$NodeExe = '<NODE_20_19_0_EXE>'
$FrpcExe = '<FRPC_0_71_0_EXE>'
& $PythonExe -B scripts/configure.py --home $InstallRoot --thread-id '<TEST_TASK_UUID>' --project '<TEST_PROJECT_PATH>' --desktop-root '<DESKTOP_PACKAGE_ROOT>' --node $NodeExe --frpc $FrpcExe
```

配置脚本只读探测专用 Desktop 任务的 marker、兼容文件与权限，**不发模型输入、不配对**；已有配置不会覆盖。只有它报告成功，且测试 HTTPS 中继与手机账号另行就绪，才启动：

```powershell
$PythonwExe = Join-Path (Split-Path -Parent $PythonExe) 'pythonw.exe'
& $PythonwExe (Join-Path $InstallRoot 'Start.pyw')
```

在窗口填经核验的 HTTPS 中继和设备名，手机生成一次性配对码、核对电脑申请并确认，然后电脑点连接。仅在预先选定的一条 Desktop 原生任务上验收读取、手机文字发送、Desktop 同任务接续及正常断开重连。`connected` 与通道探针不能代替任务完成证据。旧窗口和其身份目录保持原样；不能复制 `identity`、回执或旧 `fixture.local.json` 进入 `$InstallRoot`。

受管窗口底部可检查和下载签名更新；先正常断开，助手只在旧窗口实际退出后继续。维护者可在源码根目录只读调用 `& $PythonExe -B -m updates.cli --home $InstallRoot status`，`check` 和 `stage` 会访问已配置的 HTTPS source，**Windows 的 `apply`/`stop` 命令会拒绝，应使用窗口安装按钮**。发生 `needs_review` 时保留 `data/`、`state/` 与版本目录，见 [恢复说明](../OTA-RECOVERY.md)。

alpha.4 增加私有下载：首次应先在手机完成新设备确认，再检查更新。它读取本安装已有 DPAPI 身份，不新增更新账号或复制其他电脑身份；缺失身份只提示未配对。更新 source 必须位于已配对 relay 的同 HTTPS origin 的 `/desktop-remote-updates/test/windows/`（stable 独立配置），否则在发凭据前拒绝。设备暂停、撤销或凭据无效时服务器拒绝下载。旧 alpha.3 中继不能提供此私有路由；新的受管 relay 及签名基线须按 [验收计划](../OTA-ACCEPTANCE.md)另行部署。
