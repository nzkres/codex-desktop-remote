# 现有服务器上的隔离 OTA 环境：部署审查

最新决定：用户已批准在106.53.126.69执行本页隔离部署，包括TCP9443/17443、续证与鉴权回退，不含手机安装、真实身份迁移或停止现用服务。**新服务已安装、服务器内验收通过；Windows访问两个公网端口仍超时**，等待云防火墙/上游核对。实际资源、验证、修正和下一接手点见[部署记录](OTA-ISOLATED-DEPLOYMENT-20261008.md)。不要重复首装或覆盖已有测试目录。

以下保留2026-10-08部署前的审查依据，并补执行状态。新环境与旧环境不共享账号、数据库、设备身份或任务发送账本；一台电脑、一部手机、一条Desktop原生测试任务仍是后续验收范围。

## 部署前现场历史

下表为授权前只读预检，不能当作部署后的当前状态。部署前已重新检查，安装后端口/账号等变化见本页顶部链接；后续维护也要重新检查现场。

| 项目 | 实际观察 | 含义 |
| --- | --- | --- |
| 基础环境 | Python 3.12.3、cryptography 41.0.7、systemd 255；cgroup v2，存在 cgroup.kill | 依赖满足当前范围；尚未建立或验证新服务的 cgroup 委派 |
| 磁盘 | 根分区可用 38,912,464 KiB，使用率 22% | 本次观察足够存放候选和上一版本，上传前仍需复核空间 |
| HTTPS | Caddy 2.10.2；现用 443；Windows 正常证书校验请求成功收到 HTTP 404 | 证明现有 TLS 握手可用；404 不代表登录、私有 feed 或任务健康 |
| 证书 | SAN 为 IP 106.53.126.69；有效期 2026-10-07 06:07:55 至 2026-10-13 22:07:54 UTC | 短期证书；新入口必须接入独立续证重载钩子，不能只做一次安装 |
| 现用服务 | auth 18081、API 18091、plugin 18092；FRPS 7443；旧 FRPS 7000 | 都保留；不借用现用实例、数据库或锁 |
| FRP 端口池 | 7443 实例分配 21000–21999；7000 实例仅 18080；均绑定 loopback 代理端口 | 候选 28200–28220 与已配置池不重叠 |
| FRPS | 现用多电脑实例为 0.71.0、强制 TLS，插件指向 18092 | 新实例固定 0.71.0，并使用自己的 TLS/认证/插件配置 |
| 候选资源 | 9443、17443、22019、28181、28191、28192、28200–28220 无 TCP 监听；cdrtest 用户与候选目录不存在 | 只代表检查时未占用；不等于已预留，也未验证云安全组 |
| 防火墙 | UFW active；尚无候选 9443/17443 规则 | 新公网端口需在批准后配置并从外部实测；内部端口保持 loopback |

现有 `/opt/codex-remote-caddy/Caddyfile` SHA-256 为 `82969cafcaf3eb56cc3c061d6186462d1e688e798a2ff1b955367c68135590e3`。候选不修改该文件；部署前后核对摘要及现用服务。服务器现有 Caddy 镜像 digest 为 `caddy@sha256:4c6e91c6ed0e2fa03efd5b44747b625fec79bc9cd06ac5235a779726618e530d`，新实例可复用该已存在镜像，不跟随浮动 latest。

## 最小部署候选

已使用 **`https://106.53.126.69:9443`** 作为新环境唯一 origin，复用有效IP证书，不要求新增DNS；服务器内通过而外部访问尚未打通。若更换域名或有效端口，必须先更新候选、公钥trust和新首装构件，不能在已经签发的同版本包中换源。

| 资源 | 候选值 | 管理方式 |
| --- | --- | --- |
| Linux 用户 | `cdrtest` | 无交互登录的专用服务用户，操作经管理员 sudo 到该 UID |
| 受管 home | `/srv/codex-desktop-remote-test` | bootstrap 创建的新目录；0700；data/state/versions/staging 分离，私有文件0600 |
| 外部运行时及运维文件 | `/opt/codex-desktop-remote-test` | 固定 FRPS 副本、审核过的启动配置；不从现用路径热加载程序 |
| TLS/代理配置 | `/etc/codex-desktop-remote-test` | 新 FRPS TLS/私有认证文件与新 Caddyfile；私钥不进入 Git |
| relay 服务 | `codex-desktop-remote-test.service` | 专属 systemd 单元，显式 Delegate=yes 与专属子树；固定 start.py 入口，初期不自动重启 |
| auth / API / plugin | `127.0.0.1:28181 / 28191 / 28192` | 只有本机可访问，plugin 不经 HTTPS 暴露 |
| FRPS | `codex-desktop-remote-test-frps.service`，TCP 17443 | 新服务、独立 TLS CA/证书与 token；plugin 指向 `127.0.0.1:28192/internal/frp` |
| FRP 代理池 | `127.0.0.1:28200–28220` | 精确 allowPorts；不允许客户端另选公网端口 |
| HTTPS | 新容器 `codex-desktop-remote-test-caddy`，TCP 9443 | 独立容器及配置；宿主网络；只复用指定 IP 证书的 live/archive 只读挂载 |
| Caddy 管理口 | `127.0.0.1:22019` | 与现用2019分开，不公网开放 |
| 证书更新 | 独立 `reload-codex-desktop-remote-test` deploy hook | 只 reload 新容器；核对新入口实际 served 证书，无须修改旧重载钩子 |

systemd单元、cgroup实际委派和受管stop/start已在新安装验证；这不保证后续现场没有变化。管理停止必须先经过当前slot的 `updates/cli.py ... stop` 空闲检查；未知状态保留现场，不能用 `systemctl restart` 当作恢复按钮。FRPS独立于本版relay应用OTA，只有确认没有依赖且属于已批准维护范围时才停止。

HTTPS模板见 [Caddyfile.review](../deploy/test/Caddyfile.review)。它保留路径及鉴权头；`/auth/*`转28181，其余四类API转28191；覆盖X-Real-IP，未知路径404。无静态下载、目录浏览或access log，不向旧Android OTA转发。新容器已监听9443并完成服务器内正常TLS/鉴权下载与独立reload；**外部鉴权下载尚未通过**。

Windows/手机只能把新地址用于新隔离安装；不能把现用账号 token 带到新 origin。新 home 内 `data/transport.json` 必须包含 `server/serverPort/serverName/caPem/caSha256/transportToken`，`config.json.transportConfig` 指向它的绝对路径；新端口、CA 与 FRPS 配置匹配，秘密本机生成、保护和传递。仅执行 configure_managed 得到的 null transport 不具备转任务能力。

## 签名和执行顺序

1. 先确定入口与密钥保管。建议本机受控目录保存专用 RSA 发布密钥和 Android 连续签名证书，并做独立加密备份；Android CI 仅通过仓库 Secrets 注入证书/口令/trust，RSA 私钥不上传中继。发布器已新增Windows DPAPI RSA库与内存解封入口，外部PEM入口仍保留，详见[发行工具](OTA-PUBLISH.md#windows发行密钥与受控android构建)。本项目真实测试签名及六项Secrets已创建，准确指纹见STATUS；跨电脑备份/恢复尚未实现或验收。不得重复运行create/provision覆盖现有签名。
2. 已按用户批准合并 [PR #1](https://github.com/nzkres/codex-desktop-remote/pull/1)。受控 Android 工作流要求 main；不绕过该限制。私有 alpha.4 归档保留不变。alpha.5受控首装已构建复验，alpha.6在线候选待真实N基线就绪后准备；各版独立核验提交、catalog、证书、签名、大小/哈希和有效期。alpha.5当前元数据2026-10-15 06:33:03 UTC过期，部署前须重查。
3. 已获批准并创建上述新资源；UFW已增加IPv4 TCP9443/17443，云安全组尚待核对。新版本目录/账号/库由首次bootstrap/configure创建，不从旧库恢复，不挪用旧FRP token。不得对已有home重复首装。
4. 在新服务 UID 下验证真实 cgroup 归属、控制 socket、候选树清理/回退、持久重启与保护标志。验证证书续期钩子只影响新容器，再通过正常校验的外部 HTTPS 检查匿名拒绝、账号允许三组件、设备仅Windows、暂停/撤销拒绝新请求和跨源不转发凭据。
5. 私有版本包先入 `data/update-feed/test/{android,windows,relay}` 并核验，最后原子发布已签 latest.json。建立新手机/Windows安装基线，用户手动完成测试账号登录、配对、系统安装和专用任务确认；密码/配对码不进入聊天或命令行参数。现用测试包若签名冲突，先检查手机实际安装情况，不默认卸载。
6. 按 [三端验收顺序](OTA-ACCEPTANCE.md)先完成 N 的同任务交互，再逐一升级 Windows、Android、relay 至 N+1；每次记录真实版本、身份/权限/数据库证据与手机→Desktop→手机结果。维护者签名首装与普通用户EXE安装是不同交付层次。

入口和签名/合并已获批准；实际密钥配置与受控构件证据见STATUS。部署侧仍缺最终服务启动/续证验证、云端安全组权限与外部连通测试。**本页不是对这些未完成事项的通过声明。**

## 撤回与失败停点

- 对外开放前失败：不开放候选端口，保留新目录内的脱敏诊断和事务；旧环境继续使用。
- 已开放但尚未绑定任务：先关闭新入口准入，确认新环境无在途请求，再停止明确归属的新容器/单元；仅撤销本次新增的防火墙/安全组规则及证书钩子。旧配置不需要回灌。
- 已绑定任务：先检查专用任务、审批、未知发送回执和下载；空闲检查通过后正常断开客户端并以受管 stop 停服。未知状态不批量杀进程、不直接停止FRPS来强迫空闲、不重放消息。
- 升级失败：依受管 journal、原始句柄/cgroup 及数据检查回退；无法证明清理完成、数据变化或回退失败则 needs_review。保留上一已验证版本，绝不把旧数据库备份覆盖到新数据上。
- 新环境撤回时不递归删除数据和证据；不终止旧的 Codex、Node、Python、FRP 或旧 Caddy。若外部修改了任何候选配置，停止自动撤回并先复核。

## 部署预检历史和下一接手点

以下为签名获批前的部署预检历史，当时未部署；已被顶部的具体部署授权和执行记录推进。最新程序构件为1a4b4c0。当时检查基线为 `7a33337e8f3ec6b84020389683c3de8bf6f1dc37`，PR检查成功，alpha.4仍为预览；主代理完成服务器预检，GPT-6 Sol medium只读复核托管路径、路由、FRPS字段和cgroup前提。

本轮修正部署模板并记录实际结果，不增加软件版本。恢复工作先读STATUS与部署记录；入口/签名/合并/本次隔离部署均已批准，不重复询问。当前先完成公网端口定位；后续维护前重查证书、资源归属、包和配置摘要，现场意外变化时停止。
