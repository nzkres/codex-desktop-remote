# Linux 中继：全新受管安装

返回[安装起点](README.md)，版本变化见 [中继历史](../versions/RELAY.md)。[alpha.5 私有 prerelease](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.5) 已发布 `relay-0.1.0-alpha.5.zip` 与独立的 `bootstrap-0.1.0-alpha.5.zip`；服务端大小/SHA 与本地一致，详见 [STATUS](../../STATUS.md)。它是需外部依赖的源码运行包，不是一键安装器。测试 origin `https://106.53.126.69:9443` 已在同一服务器的独立隔离环境安装，并通过主电脑公网 HTTPS/鉴权下载；FRPS `17443` 经独立 Windows Job 中合成 FRPC/TLS 夹具验收。新受管安装**不接管现用服务、账号库、端口或身份**；详情见[隔离部署记录](../OTA-ISOLATED-DEPLOYMENT-20261008.md)。旧中继没有私有下载路由，也不对已归档 alpha.4 换签或换包。

受管宿主只支持 Linux，目标环境需 Python 3.12 与 `updates/requirements.txt` 中的 `cryptography`。首装输入来自已核验 bootstrap ZIP 的 `relay/trust.json`、`relay/latest.json`，与 `relay-0.1.0-alpha.5.zip` 配套。公钥须对照**已认证仓库记录**：`keyId=cdr-ota-test-20261008-v1`、DER SPKI SHA-256 `c3835e3e59b362e0b356d3a7b3589c255187ddce34692167f5160b0e5f5e3a4d`；同一下载页的哈希表只能辅助检查传输完整性。元数据 sequence 1，有效至 2026-10-15 06:33:03 UTC（北京时间 14:33:03）；过期由维护者重新受控签发，不能关闭验证。其后私有 OTA 下载走与中继账号登录和配对**同一 HTTPS origin**。宿主必须得到**显式委派的 cgroup v2 子树**，能创建子组并使用 `cgroup.kill`；`--cgroup-root` 不能用任意目录替代，也不能从 PID 空闲推断整树退出。目标服务器 Python 3.12.3、systemd 255 和 cgroup v2 的独立实例已完成受管 stop/start、树清空及 DB 摘要保持验收；这不等于公网任务可达。

以下可用于**另一个本地隔离临时目录**复验；目标服务器已有经授权的独立实例，勿把示例命令用于现用服务。将两个 ZIP 分别解到新目录；bootstrap 根目录已核对有 `README.txt`，`relay/` 下有 `trust.json`、`latest.json`。在 Linux shell 使用绝对路径和专门的新目录；`HOME_DIR` 必须尚不存在，不指向现用中继或复制旧数据库、token。WSL Linux Python 3.14.4 临时首装已成功，重复首装被拒且原目录未变；服务器 Python 3.12.3 的安装和合成验收另见部署记录。下列端口为隔离实例示例，执行前核对占用与范围：

```sh
PYTHON=/absolute/path/to/python3.12
SOURCE_ROOT=/absolute/path/to/extracted-relay-zip
BOOTSTRAP_ROOT=/absolute/path/to/extracted-bootstrap-zip
HOME_DIR=/absolute/path/to/new-managed-relay
TRUST="$BOOTSTRAP_ROOT/relay/trust.json"
METADATA="$BOOTSTRAP_ROOT/relay/latest.json"
PACKAGE=/absolute/path/to/relay-0.1.0-alpha.5.zip
"$PYTHON" -B -c 'import cryptography; print("dependency available")'
"$PYTHON" -B "$SOURCE_ROOT/updates/bootstrap.py" --home "$HOME_DIR" --trust "$TRUST" --metadata "$METADATA" --package "$PACKAGE" --component relay
"$PYTHON" -B "$SOURCE_ROOT/relay/configure_managed.py" --home "$HOME_DIR" --username admin --auth-port 28181 --api-port 28191 --plugin-port 28192 --port-start 28200 --port-end 28220
```

配置命令交互读取新测试账号密码，创建私有 `data/auth.sqlite3`、`data/registry.sqlite3` 和本机控制 token；它拒绝覆盖已有身份。示例端口仅用于新隔离实例，先核对没有冲突，不可直接套用到现用服务。`transportConfig` 初始为空，因而**只配置完账号与监听不等于手机任务转发可用**；真实 HTTPS、FRPS、转发配置及设备配对须另外审查。

取得已明确委派且权限正确的 cgroup v2 绝对路径后，用受管固定入口前台启动；它校验当前版本树，再启动独立宿主与自有服务树：

```sh
CGROUP_ROOT=/sys/fs/cgroup/EXPLICITLY_DELEGATED_SUBTREE
"$PYTHON" -B "$HOME_DIR/start.py" --cgroup-root "$CGROUP_ROOT"
```

宿主运行时，由**同一所有者**在 `current.json` 指向的**当前已验证版本目录**运行该版本自带的 CLI；不要拿旧仓库源码或另一个版本目录的 CLI 控制这个安装。将下面的 `<CURRENT_VERIFIED_SLOT_NAME>` 替换为 `current.json.path` 的 `versions/` 后半段，先核对它与实际已签版本目录一致：

```sh
CURRENT_SLOT="$HOME_DIR/versions/<CURRENT_VERIFIED_SLOT_NAME>"
"$PYTHON" -B "$CURRENT_SLOT/updates/cli.py" --home "$HOME_DIR" status
"$PYTHON" -B "$CURRENT_SLOT/updates/cli.py" --home "$HOME_DIR" login
"$PYTHON" -B "$CURRENT_SLOT/updates/cli.py" --home "$HOME_DIR" check
"$PYTHON" -B "$CURRENT_SLOT/updates/cli.py" --home "$HOME_DIR" stage
"$PYTHON" -B "$CURRENT_SLOT/updates/cli.py" --home "$HOME_DIR" apply
"$PYTHON" -B "$CURRENT_SLOT/updates/cli.py" --home "$HOME_DIR" logout
```

`login` 交互读取**原有单账号**用户名/密码，通过已审核 HTTPS origin 的 `/auth/login` 获取短期会话；密码不会成为命令行参数或写入磁盘。受管 home 的 `state/update-login.local.json` 以 `0600` 保存到期 token，不存密码；会话过期或要切换账号时先运行 `logout`，再运行 `login`，不要通过 CLI 参数传 token，也不要内置 GitHub PAT。`check` 与 `stage` 每次携带该账号会话去私有目录，服务端分别重新鉴权；`check` 仍验证签名和到期时间，`stage` 仍验证包哈希及已签目标。下载授权**不能代替发布签名或首次安装时固定的公钥**。`apply` 才尝试维护门禁、整树清理和版本切换。受管操作经同所有者私有 socket；没有运行宿主会失败。`stop` 是显式停服操作，`recover` 只将中断事务标为需复核，**不会自动修复或重启**。不要对现用中继运行这些命令。真实任务、审批、配对或未知操作时应延期；连接器实时状态不足时不得绕过门禁。失败保留数据与 `state/transaction.json`，见 [恢复说明](../OTA-RECOVERY.md)。

私有 feed 应经独立的 HTTPS、登录、配对和 FRPS 路由审查。反向代理只将 `/desktop-remote-updates/*` 原路径送到本受管中继的 API loopback 端口（示例配置中的 `--api-port`），**不能**把目录交给静态 `file_server`，也不能用只接受账号的前置鉴权挡住设备凭据。保留 `Authorization`、`X-Desktop-Remote-Principal`、`X-Desktop-Remote-Device`，不记录请求头或下载内容。文件固定在 `home/data/update-feed/{test|stable}/{android|windows|relay}/`，`home/data` 与 feed 各级目录同所有者 `0700`，`latest.json` 和版本包同所有者 `0600`、普通单链接文件；先放不可变版本包，再原子替换 `latest.json`。隔离服务器和主电脑公网均已用正常 IP 证书完成三组件账号下载、RSA/哈希/大小验证，匿名六项请求均获 401；主电脑合成设备 Windows 下载及 Android/relay、暂停、撤销拒绝已验证，跨 origin 凭据在发请求前拒绝，logout 后请求获 401。FRPC/FRPS 0.71 TLS 与设备插件经公网测试通过，服务器 loopback 读到无任务 HTTP 夹具应答 `CDR_EXTERNAL_FRP_OK`；原始句柄与独立 Windows Job 整树退出已确认，夹具凭据已撤销。独立 Caddy reload hook 已验，certbot 现有 timer active，尚未实际续出新证。手机/真实 Desktop 任务与N→N+1仍未验收；具体证据见[部署记录](../OTA-ISOLATED-DEPLOYMENT-20261008.md)。
