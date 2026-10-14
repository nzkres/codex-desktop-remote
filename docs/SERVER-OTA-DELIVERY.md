> 最新：alpha.9 已沿用本交付流程发布，实际 HTTPS 完整下载已验证；见 [alpha.9 发布记录](ANDROID-ALPHA9-PUBLICATION.md)。

# Android OTA构件改由自有服务器保存

## 用户要求与当前状态

用户明确要求安装包不再放GitHub构件存储，改用106.53.126.69服务器完成OTA。源码继续在GitHub；受控main构建和原APK证书不变。本改动只替换Android构件保存，不修改APK业务代码或递增应用版本；仍为alpha.8。现有Windows/Android HTTPS下载鉴权继续复用。

用户随后明确批准专用账号、两项CI配置及合并构建。账号/目录和Secrets已部署，真实SSH合成验收通过，PR #11已合并。首轮上传的密钥末尾换行问题已修复；受控CI38540921047成功直接上传alpha.8，取回独立验包通过。Android feed已获用户批准切换至alpha.8/sequence2，并完成实际HTTPS鉴权下载；手机安装待验收。最新证据见[部署实录](SERVER-OTA-DEPLOYMENT-20261014.md)。

## 路径与权限

main显式signed_ota构建 → APK版本/证书/内置trust校验 → SSH强制命令接收 → 私有暂存 → 管理端独立验签及签元数据 → 原HTTPS私有OTA目录 → 手机系统确认覆盖安装。

- 接收账号：cdr-ota-upload；仅一把专用Ed25519上传公钥，authorized_keys设restrict与固定command。禁止终端、端口/代理转发及用户rc；没有sudo，不共享管理员密钥。
- 接收命令：/usr/bin/python3 -B /opt/codex-desktop-remote-upload/receive_android_build.py，脚本和SSH授权文件由root持有。
- 暂存：/srv/codex-desktop-remote-incoming/android/<runId>-<attempt>/，接收用户拥有，仅0700目录/0600文件，最多1GiB，不自动删除旧包。
- 既有正式源：/srv/codex-desktop-remote-test/data/update-feed/test/android/，保持cdrtest所有和原私有鉴权。上传账号没有读取或写入正式feed的权限，不能自行替换latest.json。
- HTTPS路径保持/desktop-remote-updates/test/android/latest.json；手机只下载正式签名元数据引用的包，不浏览暂存目录。
- 不开新公网端口、不改sshd配置、不重启relay/FRPS/Caddy、保留原身份和手机登录。

## 传输边界

输入为最多8192字节JSON头和依次排列的4份文件：app.apk（最多48MiB）、output-metadata.json（64KiB）、reports.zip（16MiB）、verification.json（64KiB）。没有调用方路径、shell或解包操作。校验文件名白名单、整数大小、SHA256、run/attempt/commit/version格式及EOF；拒绝损坏、截断、尾随字节、重复键、重复发布、链接和空间不足。单进程锁、600秒期限、文件/目录fsync后改名可见，失败删除仅本次临时目录；断电/kill残留.partial由维护者核对，仍计入配额，不当作完成。

CI仅在main+workflow_dispatch+signed_ota且前序检查全部成功时上传。SSH严格固定主机公钥，私钥只在受控RUNNER_TEMP短期文件中使用并清理，不写参数、日志或APK。CI只写公有版本/hash/接收摘要。普通PR/debug构建不上传服务器，也不再保存在GitHub Actions artifacts；诊断保留CI测试结果/日志。既有旧构件和Release不因本改动删除。

新增两项仓库Secrets：OTA_UPLOAD_SSH_KEY、OTA_UPLOAD_KNOWN_HOSTS；只保存专用受限传输密钥和固定公有主机键，不存服务器管理员密钥。现有六项Android签名Secrets不变。RSA发布私钥仍仅本机DPAPI。专用密钥在本机仓库外以server-upload-v1/upload-key.dpapi加密保存，已写入专用CI上传Secret。上传时补齐配置工具可能裁掉的末尾LF；不更换密钥、关闭主机验证或导出管理员凭据。

## 部署、验收和撤回

1. 先明确批准上述持久上传账号和两项CI配置；拒绝或未批准时不配置，不换其他路径绕过审批。
2. 核对账号/目录不存在、服务器36.88GiB可用空间与服务PID，部署root所有脚本、强制命令公钥与独立目录，验证账号不能读正式feed。
3. 用合成包完成真实SSH传输；任意命令拒绝、相同run重传拒绝、错误主机键拒绝、文件hash/权限验证；当前服务PID与feed摘要应不变。
4. 本PR本机/CI及独立审查通过后合入main，配置限定Secrets，沿用原证书触发一次alpha.8签名构建。安装包只进入服务器暂存。无需等待GitHub artifact配额恢复。
5. 管理端取回包，独立验证apksigner、真实Manifest包名/versionCode/minSdk、内置trust、版本记录及大小/SHA；原APK证书必须保持ffcb12d413ee58613576b7914350ad4df887bf29d50d97f2b20c4e17dcebcf1a。
6. 固定包后签新的Android元数据序号/有效期；保留旧索引/包，先完整包落位再CAS核对旧索引后原子切换；账号鉴权真实HTTPS下载和匿名401验证。手机安装仍由用户系统确认，安装及同任务/普通问答需实机验收。

撤回接收入口：撤销专用authorized_keys和对应两项CI Secrets即可阻止新上传；保留暂存审计，不动正式feed和签名证书。CI传输失败不会发布Android源；若客户端已见新序号，不能倒退元数据或让手机降版本，需更高版本修复。接收目录配额满时明确报错，由维护者核对已发布/已备份包再清理。

## 验证

- 接收器合成测试：Windows9通过1真实链接权限跳过；服务器Linux10/10通过（包含真实符号链接和fsync）。
- GPT-6 Sol复核上传门禁、凭据和收据；建议的bytes核对已补。
- 本机Node36/36，Python完整134项中127通过7平台跳过；新增发送边界4/4通过（非main拒绝、SSH失败、字节收据不符、私钥临时文件清理及成功摘要）。
- PR Android CI及真实SSH部署已完成；受控构建、直传服务器及取回核验均已完成，首轮失败和修复证据见部署实录。私有源切换与实际下载已完成，详见[发布实录](ANDROID-ALPHA8-PUBLICATION.md)；手机安装待验收。

## 2026-10-15 传输期限调整

alpha.9 内置离线字体后 APK 约 40 MB，仍低于 48 MiB 上限。受控构建 38609926825 两次在原 180 秒接收期限内未能传完；暂存文件正常增长（第二次观测约 15.6 MB），退出后未生成最终构件，正式更新源未切换。接收期限改为 600 秒、发送方等待改为 630 秒；其他大小、摘要、账号、目录、单进程锁和配额限制不变。

本机上传边界测试 14 通过、1 平台跳过；部署前 Linux 接收器 10/10 通过。持原上传锁、核对旧脚本、备份后原子替换；服务 PID/启动时间和三端更新源摘要均未变化。旧脚本备份位于 /opt/codex-desktop-remote-upload/receive_android_build.py.before-20261015-deadline。新脚本 SHA256 为 5e7d6b8b5bd5544368ded885e204e22b8b81ad6b1b4397985fe1216b11312cba。此为交付工具修复，不改变 alpha.9 APP 代码。
