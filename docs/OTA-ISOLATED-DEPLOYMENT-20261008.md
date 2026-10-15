# alpha.5 隔离部署验收记录 — 2026-10-08

**当前：隔离服务部署、公网私有源与公网FRP夹具验收通过。** 用户放行云端TCP9443/17443后，主电脑正常TLS下载三端alpha.5包并通过RSA/大小/SHA验证；实际FRPC经公网FRPS回连主电脑HTTP夹具通过。手机安装、真实Windows接入、Desktop同任务及真实N→N+1仍未验收。

用户已明确批准在106.53.126.69建立隔离环境、开放TCP9443/17443、配置续证和验证鉴权/回退，并亲自完成云端端口放行。此次不包含手机安装、真实身份迁移或停止现用服务；不重复申请同一部署权限。

## 实际安装

程序输入为 `1a4b4c0ced91d1a384e723374a6141d84e43de9e` 的私有 [alpha.5 Release](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.5)。三组件包、bootstrap 和签名元数据均与 [STATUS](../STATUS.md) 指纹一致；没有重打包、重签或替换已发布资产。Python 3.12.3 / cryptography 41.0.7 / systemd 255 / cgroup v2 为目标服务器实测。

| 资源 | 现场状态 |
| --- | --- |
| 专用用户 | `cdrtest`，UID 996，无交互登录 |
| 受管 home | `/srv/codex-desktop-remote-test`，0700，独立 data/state/versions/staging；当前 relay alpha.5 |
| 工具与输入 | `/opt/codex-desktop-remote-test`，固定源码、已验证安装输入、FRPS；operator 子目录 root-only |
| 私有配置 | `/etc/codex-desktop-remote-test`，独立 FRPS CA、证书、token；秘密不进入仓库 |
| relay / FRPS | `codex-desktop-remote-test.service`、`codex-desktop-remote-test-frps.service` 已启用/运行；Restart=no |
| 内部端口 | auth 28181、API 28191、plugin 28192、Caddy admin 22019 均仅 127.0.0.1 |
| 新公网监听 | HTTPS9443、FRPS17443；UFW仅新增这两个IPv4 TCP规则，用户放行云端规则后外部连接通过 |
| 代理池 | `127.0.0.1:28200–28220`；测试用过的三个设备已撤销，保留墓碑/分配账本 |
| HTTPS | 独立 `codex-desktop-remote-test-caddy` 容器、Compose 项目与数据目录；固定镜像 digest，无静态文件下载 |
| 私有 feed | `data/update-feed/test/{android,windows,relay}`；目录0700/文件0600，先版本包后已签 latest.json，逐请求鉴权 |

`data/transport.json` 已填写独立 transport 的全部六字段，`config.json.transportConfig` 指向其绝对路径。FRPS 0.71.0 来自官方 Linux amd64 包，SHA-256 `84f27e39f11169f7adcef8e8b70c9329de17747b1f14dad9fb95eef5682ea716`；新 FRP TLS 服务名为 `cdr-test-frps.local`，客户端信任专用 CA。

测试管理员是新生成的隔离账号，凭据只保存于服务器 root-only 的 operator 目录；不是旧账号迁移。交付登录时应由用户经可信本机/SSH 交互接收或设置，不在聊天、Git、命令参数或报告中输出。RSA/APK 发布私钥未上传服务器。

## 实际验证及其边界

| 验证 | 结果 | 能证明什么 |
| --- | --- | --- |
| systemd / FRPS / Caddy | `systemd-analyze verify`、真实 FRPS `verify -c`、固定镜像 adapt/validate、实际启动通过 | 当前目标环境配置可运行 |
| 正常 TLS 的服务器内 HTTPS | 连接目标为 loopback，Host/证书校验仍使用 106.53.126.69；无跳过 TLS | 新 IP 入口证书与路由正确，不证明公网可达 |
| 私有下载 | 三组件 metadata/包共6匿名请求401；账号三包下载成功，逐个 RSA、大小、SHA 与 Release一致；退出登录后401 | 服务器实际私有源鉴权及内容正确 |
| 设备授权 | 合成设备仅 Windows200，Android/relay403；暂停/撤销后403；恢复后Windows200 | 当前设备权限门禁生效，不是真实身份接入 |
| FRP链路 | 实际 FRPC0.71→FRPS0.71，专用CA/TLS/设备插件→无任务HTTP夹具，返回 `CDR_ISOLATED_FRP_OK` | 服务器内新 transport 可工作，不是 Desktop 任务成功 |
| 不明活动状态 | 合成设备未证实空闲时受管 stop 拒绝，服务仍健康 | 不以断线或字段猜测空闲 |
| 目标服务器升级/回退 | 临时委派 systemd 单元执行 `scripts/relay_ota_smoke.py`：committed0.0.2、坏候选回退0.0.2、4棵自有树全部退出 | 目标 Python3.12/systemd255 上的合成事务成功，不是 alpha.5→alpha.6 |
| alpha.5 停启 | 撤销夹具、无活动设备/配对后，经受管CLI stop；整个服务cgroup及ready/socket消失后再启动；两库逻辑摘要保持，phase ready/healthy true | 新服务实际受控重启通过；没有覆盖旧数据库 |
| 续证 reload | 新 hook 以匹配 lineage 执行成功，核对固定容器身份；旧配置哈希不变 | 新入口重载路径可用；没有强制续证或验证新证书轮换 |
| 外部私有下载 | Windows正常TLS；三组件六项匿名401；账号实际下载三包并用产品fetch/download/verify验签、大小和SHA一致；跨origin发凭据前拒绝；logout后401 | 公网私有源可用，不是手机安装或OTA切换成功 |
| 外部FRP | Windows合成设备/FRPC0.71→公网FRPS→Windows本机无任务HTTP夹具，服务器读到 `CDR_EXTERNAL_FRP_OK`；CA/TLS/插件和权限通过 | 新公网transport真实链路通过，不连接Desktop |
| 外部夹具清理 | Windows原始句柄和独立Job证明整树退出；合成设备暂停/撤销后拒绝，账号退出，无剩余活动设备 | 无遗留夹具进程或有效夹具身份 |
| 中继实际OTA检查 | 新服务cdrtest UID以产品login→受管check→logout，经公网origin；返回 `no_update_available`，relay持续healthy | 当前同版本正确无更新，不是已升级 |

夹具账号会话已退出，三个合成设备都已撤销，没有待配对或活动设备；保留数据库历史，不删除账本来伪造全新环境。FRPC原进程/Job整树已退出，临时HTTP服务已停止，合成升级单元已回收。服务器脱敏报告在 `/opt/codex-desktop-remote-test/operator/` 的 `internal-acceptance.json` 和 `restart-acceptance.json`；主电脑脱敏外部报告在ignored `artifacts/isolated-deploy-20261008/external-acceptance.json`、`external-frp-acceptance.json`。含凭据的其他文件禁止打印或提交。

## 本轮发现与修正

1. 固定 Caddy 镜像的二进制带 `cap_net_bind_service=ep`，删除全部 capability 后实际启动报 `operation not permitted`。Compose 保留 `cap_drop: ALL`，仅补 `NET_BIND_SERVICE`，保留只读根目录和 no-new-privileges，实际运行通过。无任务状态的代理使用 `unless-stopped`；任务宿主仍为 Restart=no 和恢复门禁。
2. 直接访问 IP 的客户端可能不发送 SNI，原草稿握手失败。新 Caddy 加 `default_sni 106.53.126.69` 后正常 IP 证书验证通过；没有绕过证书验证。参见 [Caddy 官方说明](https://caddyserver.com/docs/caddyfile/options#default-sni)。只重建了本轮新容器，没有重载旧443入口。
3. 首次诊断脚本未使用 `-B`，导入已安装代码产生两个 root-owned `.pyc`，完整性门禁正确报告 `package_file_set_changed`。核对29个签名源文件未变、额外文件仅为该诊断产生的两个确切字节码、路径/所有者/格式/无链接均匹配后，仅清除此二文件及空目录。保留 `probe-bytecode-repair.json`；不删保护标志或数据。后续诊断统一 `python3 -B` 并设 `sys.dont_write_bytecode=True`，复验通过。
4. 最初新端口外网超时，18秒仅SYN诊断中443到达且连接成功，新端口均未到达；不通过修改旧443或降低TLS来绕过。用户完成云端放行后，正常HTTPS/私有包下载及真实公网FRP夹具全部通过。云规则备注建议为“Codex Desktop Remote 测试 HTTPS 与私有 OTA”（9443）、“Codex Desktop Remote 测试 FRP 连接通道”（17443）。

部署时旧 `/opt/codex-remote-caddy/Caddyfile` 的 SHA-256 前后均为 `82969cafcaf3eb56cc3c061d6186462d1e688e798a2ff1b955367c68135590e3`；旧443/7443/7000监听仍在。最终复核旧443与新9443的 `/auth/status` 均为200/configured，relay为alpha.5/ready/healthy，安装完整性通过且临时更新登录文件已移除。未停止或更改旧Gateway、FRPS、身份或数据库。

## 续证与时效

使用已有 IP 证书的只读 live/archive 挂载；当前证书有效至 **2026-10-13 22:07:54 UTC（北京时间10月14日06:07:54）**。现有实际调度器是 `codex-remote-certbot-renew.timer`（active），不是通用 `certbot.timer`；固定 certbot renew 使用 webroot、未禁用 deploy hooks。新增 `/etc/letsencrypt/renewal-hooks/deploy/reload-codex-desktop-remote-test` 只核验/重载新容器，不改旧hooks。最近观察的调度时间为北京时间10月8日15:03:01；仍需等待并验证实际新证书生效。

三端 alpha.5 test 元数据 sequence1 有效至 **2026-10-15 06:33:03 UTC（北京时间14:33:03）**。过期后用原包/原信任和更高序号重新签发，不能改系统时钟、关闭验证或同版本换包。独立签名密钥备份/跨电脑恢复尚未验收。

## 下一步及停止条件

1. 公网两端口、私有下载和FRP验收已完成；保留规则和内部loopback边界，不重复安装。不把云控制台规则的用户说明当成功证据：本次另有实际外部请求验证。
2. 安排用户手机首装和全新Windows测试身份；用户完成安装确认、隔离测试账号登录、配对及选定原生测试任务。此阶段尚未执行，不以历史旧项目手机联调代替，不复制旧身份。
3. N基线稳定后才构建alpha.6，逐组件执行真实N→N+1和失败恢复，结果逐项写入 [OTA验收](OTA-ACCEPTANCE.md)。alpha.6当前不存在。

## 本轮仓库验证

部署修正提交 `b107726` 的 Core [push37744798752](https://github.com/nzkres/codex-desktop-remote/actions/runs/37744798752) 与 [PR37744901531](https://github.com/nzkres/codex-desktop-remote/actions/runs/37744901531) Windows/Linux全部通过。Node Windows33、Linux31通过/2跳过；主Python Windows118通过/6跳过、Linux104通过/20跳过；relay Windows73通过/4跳过、Linux77通过；hook Linux3通过/Windows3跳过，真实GUI/helper和cgroup升级/回退步骤通过。新CI临时源码包不是alpha.5发布资产，不替换已部署签名包。后续仅更新外网证据的Markdown提交不冒称重新构建程序。

隔离环境撤回/维护仍遵循 [部署审查的回退边界](OTA-DEPLOYMENT-REVIEW.md#撤回与失败停点)：先检查空闲，受管CLI stop核对退出，后维护新FRPS/新Caddy/仅本轮两条规则；未知回执、残留树或DB变化则停止。保留 home 和证据，不以备份覆盖新数据，不按名称批量杀进程。已经存在的目录不是可再次运行的首装目标。
