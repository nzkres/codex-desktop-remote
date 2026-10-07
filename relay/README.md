# 独立 relay 原型

首次使用请从[中继本地安装与启动指南](../docs/install/RELAY.md)开始，包含独立数据库、认证服务、控制服务的具体步骤，以及公网接入尚需完成的条件。

本目录从旧 `windows-client` 仓库的 `f8350e4` 提取协议实现，供新项目在本机用合成身份和临时 SQLite 数据库迭代。它**没有接管或修改现用中继**。生产公网 HTTPS、Caddy、FRP 服务端安装及迁移均不在此原型内；不能把本地测试通过当作公网可用。

```text
手机 ──账号 Bearer──> HTTPS 入口（此原型尚未部署）
                         │
                         ▼
             control_server.py（仅 127.0.0.1）
                 ├─ RelayAccount ──> 单账号 /auth/check
                 ├─ Registry ──> 独立 SQLite：配对、设备、撤销纪元
                 ├─ FRP plugin ──> frp_policy.py：设备与私有端口授权
                 └─ Forwarder ──> 分配的回环端口 ──> 电脑 HTTP adapter
                       ▲                 （由独立 FRP 隧道提供）
                       └─ GatewayLinks：先验证 adapter 的专用健康接口
电脑连接器 ──设备 Bearer──> 配对、心跳、连接参数、健康注册
```

`control_server.py`、`registry.py`、`frp_policy.py`、`forwarder.py`、`gateway_link.py`、`transport_settings.py` 均为旧 `relay-control/` 同名源码。`auth/auth_server.py` 是旧 `relay-auth/auth_server.py` 的精确单账号认证依赖，不新造手机登录协议。控制面只认其 `/auth/check` 的 204 结果；401/403 拒绝，其他状态或无法连接时关闭访问。`test_http.py` 仅改了指向本目录认证源码的测试路径；`test_gateway_link.py` 改用本地 HTTP 合成客户端，避免引入旧 Windows connector 和 Gateway 测试。

服务本身只绑定 `127.0.0.1`；`make_server` 的端口 `0` 用于测试时由系统分配空闲端口。FRP plugin 是单独的回环监听器。服务器返回的远端私有端口来自登记表，客户端不得指定。HTTP 转发只访问登记表中的 `127.0.0.1:port`，由注册过且仍有效的 adapter 凭据授权。`dataChannel: verified` 只表示隧道和健康探针通过，不表示 Desktop 任务执行成功。

认证源码保留旧默认数据库路径以维持协议兼容；本目录的测试在导入后把 `DB_PATH` 指向临时目录。不要用真实账号数据库、现用端口或现用 FRP 配置启动原型。测试使用随机合成 token、临时数据库与回环 HTTP 服务；不含私钥、真实账号或设备凭据，也不会访问公网。未包含旧部署脚本、更新器、旧 Gateway、Windows 连接器及真实 FRP 二进制集成测试。

在新仓库根目录运行（Python 3.11+，仅标准库）：

```powershell
python -B -m unittest discover -s relay -p 'test_*.py' -v
```

现用中继仍由旧仓库及其部署配置管理；本目录是独立开发副本。后续若要发布，需单独审核 HTTPS 入口、单账号认证服务的连接、FRP TLS/端口设置、数据迁移与回退方案，再做隔离环境验收。这里的测试仅覆盖本地协议行为和合成转发。

## 版本与体验记录

`releases.json` 是本组件唯一版本记录。运行 `python -B relay/control_server.py --version` 可只打印当前版本；`--release-notes` 会列出每版改动、体验方式、验证步骤和限制。这两个命令不需要 `--db`，不创建数据库或监听端口。运行中的服务提供 `GET /control-api/version`，返回当前版本和完整历史；它位于现有单账号 Bearer 验证之后，FRP plugin 端口没有此接口。

该 HTTP 接口返回**本进程启动时**从已安装的 `releases.json` 读取的快照；运行期间仅替换磁盘文件不会改变结果。只有部署并启动含新版记录的 relay 服务，服务器返回值才会更新；Windows、Android 或其他组件更新不代表服务器已更新。当前目录的本地版本只能说明开发副本状态，不能证明现用公网中继的版本。
