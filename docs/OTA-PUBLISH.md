# OTA 打包、签名与发布交接

2026-10-08 当前状态：PR #1已合并，专用测试签名及六项Secrets已配置，alpha.5 main受控构建、实际包复验和隔离服务器部署已完成。手机现为alpha.5；Windows已用同一已签alpha.6包维护升级，用户确认手机原任务同步恢复。**Windows alpha.6→alpha.7普通HTTPS GUI OTA及升级后手机同任务接续已实际验收通过；Android/relay的真实N→N+1尚未完成。**Windows alpha.7私有源已获批发布sequence3，正常TLS公网账号下载/签名/哈希及匿名401通过；用户随后经原GUI完成真实alpha.6→alpha.7下载/安装，保全与新窗口通过，并明确确认手机两条新测试回复均显示，发布证据与后续边界见[alpha.7审查](WINDOWS-ALPHA7-OTA-REVIEW.md)；公开指纹与分层结果见[STATUS](../STATUS.md)。原alpha.5/alpha.6包保持不变。

2026-10-08 本地准备增量：`sign` 现在强制 source 为 `/desktop-remote-updates/{channel}/{component}/latest.json`，包 URL 必须是同目录的 `component-{catalog.version}.apk|zip`，渠道/组件/版本/扩展名须一致；拒绝时不生成元数据，避免签出私有服务无法提供的地址。该发行工具不随三端运行包分发，本次不重发 alpha.4、不递增三个软件版本。该段为前期准备记录；暂缓签名/合并已由后续明确批准替代，见[当前状态](../STATUS.md)。

以下是 **alpha.4 起支持的私有 OTA** 操作范式，构建与归档证据见 [STATUS](../STATUS.md)。[验收计划](OTA-ACCEPTANCE.md)最初采用alpha.5→alpha.6；Windows现在按实际安装基线改为alpha.6→alpha.7，Android/relay仍从alpha.5逐端推进。下面是历史首装工具调用范式，alpha.5成品已存在，不能重跑来覆盖旧包；本轮签发以alpha.7审查中的固定CI输入、版本和sequence为准。`updates.publish` 本身只在本地打包 Windows/relay 源码 ZIP、对已有 APK/ZIP 签发布元数据，**不构建 APK、不上传文件、不部署中继**。正式发布须有受控 RSA 3072 位以上私钥、经审核的公钥/trust、Android 连续 APK 签名证书，以及与登录/配对中继同源的受鉴权 HTTPS 下载入口。私钥路径、口令与凭据不得进入仓库、日志或命令示例。

先在仓库根目录更新发生变化组件的 `releases.json` 与版本说明，运行 `python scripts/check_versions.py --write` 及只读校验和相关测试。`pack` 从当前源码按固定白名单生成新文件，输出路径必须尚不存在。以下 PowerShell **占位符必须按已审核的真实资源替换**，`$PythonExe` 为带 `cryptography` 的 Python 3.12；本仓库不提供密钥或部署主机：

```powershell
$PythonExe = '<PYTHON_3_12_EXE>'
$OutputDir = '<EXISTING_EMPTY_LOCAL_RELEASE_DIRECTORY>'
$SigningHome = '<EXTERNAL_WINDOWS_DPAPI_SIGNING_DIRECTORY>'
$KeyId = '<REVIEWED_KEY_ID>'
$Sequence = [int](Read-Host '输入已审核的下一发布序号')
$Source = 'https://<REVIEWED_RELAY_HOST>/desktop-remote-updates/test/windows/latest.json'
$ArtifactUrl = 'https://<REVIEWED_RELAY_HOST>/desktop-remote-updates/test/windows/windows-0.1.0-alpha.5.zip'
$Zip = Join-Path $OutputDir 'windows-0.1.0-alpha.5.zip'
$Metadata = Join-Path $OutputDir 'windows-latest.json'
& $PythonExe -B -m updates.publish pack windows --output $Zip
& $PythonExe -B -m updates.publish sign windows --artifact $Zip --output $Metadata --signing-home $SigningHome --key-id $KeyId --source $Source --url $ArtifactUrl --sequence $Sequence --channel test
```

Relay 同理，将 `windows` 换成 `relay`、使用独立的 relay `source`/ZIP URL、输出文件与该组件的递增序号。`sign` 要求 artifact URL 与 source 在同一 HTTPS 目录；默认元数据有效期 7 天，过期须**新序号、新签名**，不能擅自改已签字段。不同渠道的公钥、source 与序号须独立审查，`stable` 不接受预发布版本。签名后的 `latest.json` 不能人工编辑。

Android 先用**同一受控 APK 签名证书**构建候选，核对实际包名 `dev.codex.desktopremote.lite`、版本名、递增 `versionCode`、最低 API 26 和证书 SHA-256，再使用发布器签**另一层**元数据。`--apk-verified` 是发行者对上述独立核验的声明，代码不会代替证书检查。示例只列参数，不提供 APK 或证书：

```powershell
$Apk = '<INDEPENDENTLY_VERIFIED_SIGNED_APK>'
$AndroidVersionCode = [int](Read-Host '输入与 Android releases.json 一致的 versionCode')
$AndroidSource = 'https://<REVIEWED_RELAY_HOST>/desktop-remote-updates/test/android/latest.json'
$AndroidUrl = 'https://<REVIEWED_RELAY_HOST>/desktop-remote-updates/test/android/android-0.1.0-alpha.5.apk'
& $PythonExe -B -m updates.publish sign android --artifact $Apk --output (Join-Path $OutputDir 'android-latest.json') --signing-home $SigningHome --key-id $KeyId --source $AndroidSource --url $AndroidUrl --sequence $Sequence --channel test --apk-version-code $AndroidVersionCode --apk-package-id dev.codex.desktopremote.lite --min-sdk 26 --apk-verified
```

Android 普通 push/PR CI 构建嵌入空 trust 且使用临时 debug 签名，只能测试，不能生产 OTA。`.github/workflows/android.yml` 的 `workflow_dispatch` 可选 `signed_ota=true`，仅 main 且所需 GitHub secrets 全部就绪时生成受控**测试**签名 APK；CI 会核对证书指纹，artifact 保留 14 天。本项目六项Secrets已配置，受控run37736837251已通过；独立服务器测试feed已从主电脑公网完成账号鉴权下载，手机 App 内下载未验收。后续构建仍须独立核对输入与运行结果。首次发行还必须把审核后的 `update-trust.json` 从外部注入构建，后续证书保持连续；不能靠改包名或卸载原 App 解决签名不一致。

Windows/relay 首次安装的 `trust.json` 需由受控流程从**已审核的发布公钥**制作，字段为 `schema:1`、`product:codex-desktop-remote`、`channel:test|stable`、`keys:{keyId:PEM_PUBLIC_KEY}` 和 `sources:{windows|relay|android:HTTPS_LATEST_JSON}`；只配置该安装需要的组件 source。`sign` 不替操作者生成、安装或轮换 trust。首次 bootstrap 接收本地 trust、已签元数据和对应 ZIP，随后固定在新目录；旧实例不能通过下载更新自己的信任根。发布签名和首次安装的固定公钥验证仍是内容真实性边界，**登录或配对只授予下载权限，不能代替签名**。不在客户端嵌入 GitHub PAT。

## Windows发行密钥与受控Android构建

以下`create`仅在确认开始配置真实签名后执行，不能对既有目录反复运行。目录必须为仓库外的绝对路径，父目录已存在；工具独占创建新目录并限制为当前用户/SYSTEM，不改变父目录ACL。仅落盘`publisher.dpapi`与公开的`public.json`，不写明文PEM或导出连接器身份。

```powershell
& $PythonExe -B -m updates.signing create --directory $SigningHome --key-id $KeyId
& $PythonExe -B -m updates.signing inspect --directory $SigningHome --key-id $KeyId
```

`inspect`核对解密、公钥与keyId绑定后只显示公开字段。公钥指纹为DER SubjectPublicKeyInfo的SHA-256；用于Android的公钥PEM也必须是规范SPKI，不能用PKCS#1或拼接多个PEM。`sign`只接受`--signing-home`与旧`--private-key`之一，后者仍为外部受控PEM的离线兼容入口，没有自动解密落盘降级。错误仅显示固定文案，失败目录保留供复核；损坏、错keyId、链接、多硬链接或超大记录都拒绝。

DPAPI使用现有CurrentUser实现，不启用LOCAL_MACHINE；它绑定Windows用户的保护上下文，**不是跨电脑密钥备份功能**。本工具没有私钥导出/恢复命令；独立备份和恢复能力尚未验收，不应把复制DPAPI文件视为完整备份。Python签名进程会在内存持有私钥，本工具不承诺内存副本的可靠擦除。实现依据见[Microsoft DPAPI说明](https://learn.microsoft.com/en-us/windows/win32/api/dpapi/nf-dpapi-cryptprotectdata)。

Android使用另一份连续APK签名证书，构建格式固定为[PKCS12](https://developer.android.com/reference/tools/gradle-api/8.11/com/android/build/api/dsl/SigningConfig)。六个仓库Secrets分别为`ANDROID_UPDATE_TRUST_JSON`、`ANDROID_SIGNING_KEYSTORE_BASE64`、`ANDROID_SIGNING_STORE_PASSWORD`、`ANDROID_SIGNING_KEY_ALIAS`、`ANDROID_SIGNING_KEY_PASSWORD`、`ANDROID_SIGNING_CERT_SHA256`；RSA发行私钥不上传GitHub或中继。受控构建仍只能在main显式触发，不通过分支调试构建绕行。

构建前执行`python -B -m scripts.check_android_trust --trust <EXTERNAL_TRUST_JSON>`，只接受test渠道、一个RSA>=3072公钥、一个android source及准确的私有HTTPS路径。构建后加`--apk <APK_PATH>`逐字节比对`assets/update-trust.json`，缺失、重复或内容变化都失败；APK证书仍由独立apksigner步骤核对。普通PR只测试这套工具和编译调试APK，不证明main的真实签名步骤已执行。

### 已批准的实际执行范围（2026-10-08）

准备完成后的受控首装操作限定为：核对并合并PR #1；固定测试origin为`https://106.53.126.69:9443`；在本机`%LOCALAPPDATA%\CodexDesktopRemoteSigning\test-v1`下建立新的专用签名材料（不复用旧项目密钥），RSA使用DPAPI，Android使用加密PKCS12及受控本地保护；只向本私有仓库配置上述六项Secrets；将三端首装版本更新为alpha.5并运行main受控构建、验签和包内校验。该批准范围已执行至真实alpha.5构建、签名、下载和临时首装复验；私有归档及准确哈希见STATUS。

用户先批准上述签名与合并范围，PR #1已合并为`1e405ad`；随后又批准在现有服务器以独立账号、目录、数据库和端口部署测试 relay/FRPS/HTTPS/私有OTA及续证hook。隔离安装、服务器内及主电脑公网的合成验收已执行，现用服务和身份保持原样。**手机安装、真实身份迁移和真实任务N→N+1不在本次授权范围，也未执行。**旧alpha.4资产保留，前期“暂缓合并/签名/隔离部署”的决定已由后续授权替代。

`updates.android_publisher create --directory $SigningHome --key-id $KeyId --origin $Origin`在已创建的RSA库内新建专用APK签名，私钥/口令只在内存处理并写入`android-signing.dpapi`，同时输出公开`android-public.json`及固定`android-trust.json`；底层PKCS12也使用强随机口令加密。不会复用旧项目证书、写明文p12或口令文件。

`updates.android_publisher provision-github`使用同样三个参数，将六项Android构建输入逐一经`gh secret set`的标准输入提交到固定的`nzkres/codex-desktop-remote`。先检查这些Secret名称尚不存在，不读取秘密值；本地`github-provision.json`只记录阶段和名称。失败或未知结果标为`needs_review`，重复执行拒绝，不能自动覆盖、删除远端Secrets或清除记录重试。RSA私钥不在这六项中。此工具仍不提供跨电脑密钥恢复，Git仓库/CI成功不等于备份完成。

私有下载文件只放在**新受管中继**的 `home/data/update-feed/{test|stable}/{android|windows|relay}/`。`home`、`data`、`update-feed`、渠道和组件目录均须由运行中继的同一用户持有，权限 `0700`；`latest.json` 和不可变版本包须为同一用户的普通单链接文件，权限 `0600`。只允许 `latest.json` 和与组件/版本相符的 `component-x.y.z[-alpha.N].apk|zip`；不能用软链接、硬链接、重解析点或目录浏览暴露其他文件。先把**版本化包**放到目标组件目录，核对实际字节数/SHA-256、签名元数据的 URL/有效期与 HTTPS 证书；最后以同目录临时文件和原子替换发布 `latest.json`。不要原地改已有版本包，也不要先让元数据指向尚未到位的包。仓库没有通用上传命令；测试服务器origin已在独立隔离环境使用，主电脑公网经正常IP证书验证三组件账号下载和RSA/哈希/大小。私有GitHub Release可作归档，14天Actions artifact不能作长期feed。

HTTPS 必须使用与账号登录、Windows 配对相同的已审核 origin。反向代理把 `/desktop-remote-updates/*` **原路径**送往新受管中继的 API loopback 监听，不使用 `file_server` 或独立公开静态目录，也不能套用只认账号的前置 `forward_auth` 遮住设备凭据。代理须保留 `Authorization`、`X-Desktop-Remote-Principal`、`X-Desktop-Remote-Device`，且不记录这些请求头、下载内容或令牌。每次 `GET` 元数据和包都由中继重新鉴权：账号会话可下载三组件；已配对且活跃的设备凭据仅可下载 Windows，暂停或撤销后新请求被拒绝；FRP plugin 监听不提供该路径。已开始的流式下载属于已准入请求，维护门禁会等它结束，不能声称撤销能追回已发出的字节。空目录、未配置 feed、过期会话及旧中继都不能作为可用发布源。

**首发顺序与当前进度**：隔离环境已核验 alpha.5 受控中继签名ZIP/trust，以 bootstrap 建立新受管 home 并启动中继；旧服务没有私有 OTA 路由，也未被替换。新实例的同源 HTTPS、身份转发和已签构件/`latest.json` 已在服务器内及主电脑公网验收：三组件账号完整下载、匿名六项401、合成设备Windows200及其他组件/暂停撤销403；主电脑 `updates.security` 验证正常TLS、RSA/哈希/大小匹配，跨origin凭据在发请求前拒绝，logout后401。实际FRPC/FRPS 0.71 TLS和设备插件经独立Windows Job中的无任务HTTP夹具通过公网转发；受管stop/start与cgroup清空/DB摘要保持已验收。独立Caddy reload hook通过，certbot timer active但未实际续出新证。真实客户端 `check`、手机/电脑首装和下一版本N→N+1仍待测。发布证据至少记录源码版本、构件哈希、签名 keyId/公钥指纹、已服务文件哈希、元数据有效期、鉴权拒绝结果、安装和真实任务验收层级，勿记录私钥或账号令牌；本轮细节见[部署记录](OTA-ISOLATED-DEPLOYMENT-20261008.md)。


## 固定引导器与运行时

Windows/relay ZIP 更新的是该原型的应用源码及下次可用的更新工具。Python/cryptography，以及 Windows 的 Node/FRP 为首次安装准备的外部运行时，不在此版 OTA 中更换；当前只接受数据格式 1 与相同兼容协议，不支持自动依赖或数据库迁移。

受管 home 的固定启动脚本是首次验证包建立的信任入口，不在事务中自改。Linux 独立更新宿主在本次服务切换后继续保留原始子进程句柄，其自身 Python 模块不会热替换；正常管理员 stop 后再由固定入口 start，才加载 current 指向版本的宿主代码。若发行内容修复宿主本身，更新说明必须写明这个维护重启要求，不能把服务版本变化当作宿主代码已经重载。中继仍只通过 OTA 下载程序，不需要重新上传源码。
