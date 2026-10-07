# Linux 中继：全新受管安装

返回[安装起点](README.md)，版本变化见 [中继历史](../versions/RELAY.md)。alpha.3 开发预览提供源码 ZIP；此处受管 OTA 对应同一版代码，**不接管现用服务器、账号库、端口或公网入口**。尚无公网 OTA 源和实际生产部署证据。

受管宿主只支持 Linux，需 Python 3.12 与 `updates/requirements.txt` 中的 `cryptography`。还需要管理员预先提供独立、经过审核的测试渠道发布公钥与 HTTPS 静态源、已签名 relay 元数据和 ZIP。宿主必须得到**显式委派的 cgroup v2 子树**，能创建子组并使用 `cgroup.kill`；`--cgroup-root` 不能用任意目录替代，也不能从 PID 空闲推断整树退出。本页不猜测 systemd 委派配置或宣称服务器已满足它。

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

宿主运行时，在同一源码根目录由**同一所有者**本机执行固定操作：

```sh
"$PYTHON" -B -m updates.cli --home "$HOME_DIR" status
"$PYTHON" -B -m updates.cli --home "$HOME_DIR" check
"$PYTHON" -B -m updates.cli --home "$HOME_DIR" stage
"$PYTHON" -B -m updates.cli --home "$HOME_DIR" apply
```

`check` 读取签名元数据，`stage` 下载并验证，`apply` 才尝试维护门禁、整树清理和版本切换。命令只发给受管目录的同所有者私有 socket；没有运行宿主会失败。`stop` 是显式停服操作，`recover` 只将中断事务标为需复核，**不会自动修复或重启**。不要对现用中继运行这些命令。真实任务、审批、配对或未知操作时应延期；连接器实时状态不足时不得绕过门禁。失败保留数据与 `state/transaction.json`，见 [恢复说明](../OTA-RECOVERY.md)。
