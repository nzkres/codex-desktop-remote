# 隔离测试入口：部署模板与维护边界

用户已批准按本目录在 `https://106.53.126.69:9443` 建立隔离测试环境。alpha.5中继、FRPS、独立Caddy、私有feed和续证hook已安装；服务器内验收通过，**外部9443/17443仍超时**，云防火墙/上游待核对。详见[实际部署记录](../../docs/OTA-ISOLATED-DEPLOYMENT-20261008.md)。`.review`保留为需核对现场后使用的模板命名，不表示未执行。既有测试目录不可重复首装。

这些配置只属于新隔离资源，不替换旧443 Caddy、Gateway、FRPS、认证库或设备身份。后续维护先核对端口、证书、归属、权限、cgroup和空闲状态；不得把已批准的隔离部署扩大为现用环境迁移。

## 隔离边界与文件

| 草稿 | 安装后预期位置与作用 |
| --- | --- |
| `codex-desktop-remote-test.service.review` | `/etc/systemd/system/codex-desktop-remote-test.service`；固定 `/usr/bin/python3.12` 启动安装 home 内的 `start.py`，显式委派 cgroup，`Restart=no`。 |
| `codex-desktop-remote-test-frps.service.review` | 独立 FRPS 0.71.0 单元；二进制位于 `/opt/codex-desktop-remote-test/frps`。 |
| `frps.toml.review` | `/etc/codex-desktop-remote-test/frps.toml`；TCP `17443`，代理只绑定 `127.0.0.1:28200–28220`，插件走 `127.0.0.1:28192/internal/frp`。 |
| `Caddyfile.review`、`compose.caddy.yaml.review` | 固定 Compose 项目 `codex-desktop-remote-test` 与独立容器 `codex-desktop-remote-test-caddy`，host network 上的 `:9443`，本机管理端口 `22019`。Caddy 仅反向代理 `/auth/*` 到 `127.0.0.1:28181`，以及 `/control-api/*`、`/connector-api/*`、`/terminal-api/*`、`/desktop-remote-updates/*` 到 `127.0.0.1:28191`；其他 404。 |
| `renew-caddy.sh.review` | 仅针对 `codex-remote-ip` 证书 lineage 的 Certbot 风格 deploy hook；固定完整容器 ID 并核对 Compose 项目、服务、镜像和运行状态后，只向 `127.0.0.1:22019` 重载该容器。 |

受管 home 为 `/srv/codex-desktop-remote-test`，归专用 `cdrtest:cdrtest`，权限 `0700`。`data/`、`state/`、`versions/`、`staging/` 与 `start.py` 只属于这套新安装。`/etc/codex-desktop-remote-test` 私有配置目录归 `cdrtest:cdrtest`、权限 `0700`，FRPS配置/token/私钥也归该UID、权限 `0600`；其中不含秘密的 `Caddyfile` 需单独设为 `root:root`、`0644`，使只保留NET_BIND_SERVICE的root容器能读取其只读单文件挂载；**此仓库不含这些值**。Caddy 容器数据目录 `/var/lib/codex-desktop-remote-test-caddy/{data,config}` 由root拥有且仅供新容器使用，与旧容器隔离；证书私钥的容器内UID/权限须实际验证。证书只挂载 `live/codex-remote-ip` 和对应 `archive/codex-remote-ip`，保留证书符号链接的相对目标；容器不能写证书。

私有 OTA feed 在 home 的 `data/update-feed/{test|stable}/{android|windows|relay}/`，目录 `0700`、文件 `0600`，不允许链接；先原子放版本包，再原子替换 `latest.json`。下载入口必须与 `/auth/*`、配对入口共用此 HTTPS origin，保留 Authorization、principal、device 三种原始鉴权头，不在 Caddy 加静态文件服务或账号专用前置鉴权。本轮已将alpha.5原包及已签元数据部署到test feed；本目录不含签名私钥或私有配置。

## 首装与维护核对顺序

以下首装步骤已执行，不可直接再运行；用于复核或其他全新隔离环境。当前只继续公网连通性验收。

1. 重新核对 `/usr/bin/python3.12`、FRPS **0.71.0** 二进制来源及校验值、Caddy 镜像 digest、Docker Compose、cgroup v2 委派能力、`17443/9443/22019/28181/28191/28192/28200–28220` 端口、证书 IP SAN/有效期以及防火墙；端口曾空闲不等于已保留。用确切的 FRPS 0.71.0 执行 `frps verify -c /etc/codex-desktop-remote-test/frps.toml`。若配置字段与该版本不兼容，应停在审核，不猜测调整正在运行的服务。
2. 仅为**全新**安装创建 `cdrtest` 和私有路径，放入已核验的 relay 版本包及 `start.py`。从当前已验证版本槽使用 `relay/configure_managed.py --home /srv/codex-desktop-remote-test --auth-port 28181 --api-port 28191 --plugin-port 28192 --port-start 28200 --port-end 28220`，账号密码只经交互输入。它拒绝复用现有 DB/token/config；不能拿现用账号库试跑。FRPS token、服务器 TLS 私钥和客户端信任材料需另行安全配置，且与旧服务隔离。`data/transport.json` 应由受管配置要求填写新 FRPS 地址/端口、服务名、CA 及 transport token，不得复制旧身份。
3. 安装审定后的两个单元文件，运行 `systemd-analyze verify` 并在受管服务启动后核对 `systemctl show codex-desktop-remote-test.service -p ControlGroup -p Delegate`。此草稿将 cgroup 路径固定为 `system.slice/codex-desktop-remote-test.service`，只有该路径确实可由 `cdrtest` 创建子组且具有预期 `cgroup.kill` 时才能继续。先对 Caddyfile 在目标 Caddy 镜像执行 `caddy adapt --validate`；本轮真实容器adapt/validate与启动已通过；模板变更后仍须复验。新容器必须保持独立，证书文件可读性需在容器内核验。
4. 单独验证认证、设备配对、FRP 插件与代理端口、私有 feed 的匿名拒绝/有效账号和设备授权、更新维护 gate、证书续期 hook 及 HTTPS 握手。检查证书续期机制是否真提供 `RENEWED_LINEAGE`；若不是 Certbot 风格，就需适配钩子，不能宣称已自动续期。云防火墙针对已获批的 `9443`、`17443` 独立审核；FRP 代理端口始终仅 loopback。

## 停止与未知状态

受管 relay 单元设置 `RefuseManualStop=yes`、`Restart=no`，**没有 `ExecStop`**。计划内停止必须由 `cdrtest` 运行当前已验证槽中的 `updates/cli.py --home /srv/codex-desktop-remote-test stop`，让维护 gate 检查活动请求、配对和设备状态；先查看 `status` 回执并确认进程树退出。不得使用按名称杀进程、旧 PID 重开、`systemctl restart` 或通过停止 FRPS 间接断开会话。FRPS 仅在 relay 已安全停止、确认没有设备依赖后单独维护。

`RefuseManualStop=yes` 只能拒绝**手动** stop/restart 请求，不能保证主机重启、依赖事务、管理员 kill 或意外退出时服务树不被终止。`ExecStop` 即使返回拒绝，systemd 仍可能继续发送终止信号；因此本草稿不把它当准入屏障。若 CLI stop 回执不明、systemd 状态与更新 journal 不符，或 cgroup 仍有进程，保留 home/data/state 和现场证据，暂停重启/恢复，人工核对；`Restart=no` 避免自动拉起不明状态，但不等于事务已安全完成。

## 自动测试与此前准备历史

以下为部署前的本地证据；目标服务器的安装、真实FRP/鉴权/cgroup/停启和hook结果见顶部部署记录。尚未通过的是外部连接/下载、真实手机和Windows接入、alpha.5→alpha.6以及实际新证书轮换。

本地静态验证：最终Compose配置解析、shell `sh -n`、FRPS TOML解析通过；Linux `python3 -B -m unittest discover -s deploy/test -p 'test_*.py'` 3项通过，使用临时假docker验证lineage/身份拒绝及固定容器ID+22019调用。该合成测试不读取证书、不重载容器，不能算真实续证事件验收。

本机 Ubuntu WSL 已以非 root 用户 `sheng`、systemd `Delegate=yes`/`RefuseManualStop=yes`/`Restart=no`/`NoNewPrivileges=yes` 对受管 relay 运行合成更新 smoke：升级至 `0.0.2`，坏候选 `0.0.3` 回退到 `0.0.2`，四个进程树退出；测试用瞬态单元已回收。这仅验证本机机制，WSL 为 Python 3.14.4/systemd 259，不能替代目标 Python 3.12/systemd 255，也不是本目录单元的安装验收。

另已核对官方包摘要并以FRPS 0.71.0真实二进制、临时合成TLS/token执行`verify -c`通过，配置字段可被该版本接受。本机尚未启动此草稿的Docker/FRPS服务、处理真实证书续期事件或验收公网访问；没有为本草稿创建真实密钥/账号、安装服务或部署。独立 Caddy 的证书私钥读取权限、实际容器镜像、FRPS实际TLS/插件通信、目标受管 cgroup 写权限与防火墙策略，都必须在获批后的隔离环境逐项验证。当前 `Caddyfile.review` 使用的 IP 证书到期时间短，现场必须重新检查，而不能沿用旧审核结果。
