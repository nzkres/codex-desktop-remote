# Linux 中继：全新受管安装

返回[安装起点](README.md)，版本变化见 [中继历史](../versions/RELAY.md)。本页说明 **alpha.4 起支持的私有 OTA**；构建/归档证据见 [STATUS](../../STATUS.md)，公网部署与实机验收尚未完成。新受管安装**不接管现用服务器、账号库、端口或公网入口**。旧中继没有 `/desktop-remote-updates/*` 私有下载路由，必须先用包含新实现的已签中继 ZIP/trust 在新目录本地 bootstrap，不能靠上传 feed 把旧服务变为新服务。受控首装计划使用未发布新版本，见 [验收计划](../OTA-ACCEPTANCE.md)；不对已归档预览换签或换包。

受管宿主只支持 Linux，需 Python 3.12 与 `updates/requirements.txt` 中的 `cryptography`。还需要管理员预先提供经过审核的测试渠道发布公钥、已签名 relay 元数据和 ZIP；其后私有 OTA 下载走与中继账号登录和配对**同一 HTTPS origin**。宿主必须得到**显式委派的 cgroup v2 子树**，能创建子组并使用 `cgroup.kill`；`--cgroup-root` 不能用任意目录替代，也不能从 PID 空闲推断整树退出。本页不猜测 systemd 委派配置或宣称服务器已满足它。

以下在**源码根目录**的 Linux shell 运行，只使用专门的新目录和未被占用的测试端口。`HOME_DIR` 必须是尚不存在的新路径；不要指向现用中继或复制旧数据库、token。每一项输入需先核对来源，示例占位符须替换：

```sh
PYTHON=/absolute/path/to/python3.12
HOME_DIR=/absolute/path/to/new-managed-relay
TRUST=/absolute/path/to/reviewed-test-trust.json
METADATA=/absolute/path/to/signed-relay-latest.json
PACKAGE=/absolute/path/to/matching-relay.zip
"$PYTHON" -B -c 'import cryptography; print("dependency available")'
"$PYTHON" -B updates/bootstrap.py --home "$HOME_DIR" --trust "$TRUST" --metadata "$METADATA" --package "$PACKAGE" --component relay
"$PYTHON" -B relay/configure_managed.py --home "$HOME_DIR" --username admin --auth-port 28181 --api-port 28191 --plugin-port 28192 --port-start 28200 --port-end 28220
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

在启用私有 feed 前，先完成独立的 HTTPS、登录、配对和 FRPS 路由审查。反向代理只将 `/desktop-remote-updates/*` 原路径送到本受管中继的 API loopback 端口（示例配置中的 `--api-port`），**不能**把目录交给静态 `file_server`，也不能用只接受账号的前置鉴权挡住设备凭据。保留 `Authorization`、`X-Desktop-Remote-Principal`、`X-Desktop-Remote-Device`，不记录请求头或下载内容。文件固定在 `home/data/update-feed/{test|stable}/{android|windows|relay}/`，`home/data` 与 feed 各级目录同所有者 `0700`，`latest.json` 和版本包同所有者 `0600`、普通单链接文件；先放不可变版本包，再原子替换 `latest.json`。账号会话可下载三组件；已配对且活跃的设备仅可下载 Windows；FRP plugin 端口不提供 feed，暂停/撤销后新请求拒绝。具体签名与发布顺序见[发布交接](../OTA-PUBLISH.md)；这里只规定新安装步骤，**没有进行公网部署**。
