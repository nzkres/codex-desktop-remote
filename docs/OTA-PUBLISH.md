# OTA 打包、签名与发布交接

以下是面向 **0.1.0-alpha.4 私有 OTA 首发**的操作范式，**尚未构建、签发、发布、部署或完成实机验收**；alpha.3 等既有版本历史保留，不以同版本换包。当前 `updates.publish` 只在本地打包 Windows/relay 源码 ZIP、对已有 APK/ZIP 签发布元数据，**不构建 APK、不上传文件、不部署中继**。正式发布须有受控 RSA 3072 位以上私钥、经审核的公钥/trust、Android 连续 APK 签名证书，以及与登录/配对中继同源的受鉴权 HTTPS 下载入口。私钥路径、口令与凭据不得进入仓库、日志或命令示例。

先在仓库根目录更新发生变化组件的 `releases.json` 与版本说明，运行 `python scripts/check_versions.py --write` 及只读校验和相关测试。`pack` 从当前源码按固定白名单生成新文件，输出路径必须尚不存在。以下 PowerShell **占位符必须按已审核的真实资源替换**，`$PythonExe` 为带 `cryptography` 的 Python 3.12；本仓库不提供密钥或部署主机：

```powershell
$PythonExe = '<PYTHON_3_12_EXE>'
$OutputDir = '<EXISTING_EMPTY_LOCAL_RELEASE_DIRECTORY>'
$SigningKey = '<EXTERNAL_CONTROLLED_RSA_PRIVATE_KEY_PEM>'
$KeyId = '<REVIEWED_KEY_ID>'
$Sequence = [int](Read-Host '输入已审核的下一发布序号')
$Source = 'https://<REVIEWED_RELAY_HOST>/desktop-remote-updates/test/windows/latest.json'
$ArtifactUrl = 'https://<REVIEWED_RELAY_HOST>/desktop-remote-updates/test/windows/windows-0.1.0-alpha.4.zip'
$Zip = Join-Path $OutputDir 'windows-0.1.0-alpha.4.zip'
$Metadata = Join-Path $OutputDir 'windows-latest.json'
& $PythonExe -B -m updates.publish pack windows --output $Zip
& $PythonExe -B -m updates.publish sign windows --artifact $Zip --output $Metadata --private-key $SigningKey --key-id $KeyId --source $Source --url $ArtifactUrl --sequence $Sequence --channel test
```

Relay 同理，将 `windows` 换成 `relay`、使用独立的 relay `source`/ZIP URL、输出文件与该组件的递增序号。`sign` 要求 artifact URL 与 source 在同一 HTTPS 目录；默认元数据有效期 7 天，过期须**新序号、新签名**，不能擅自改已签字段。不同渠道的公钥、source 与序号须独立审查，`stable` 不接受预发布版本。签名后的 `latest.json` 不能人工编辑。

Android 先用**同一受控 APK 签名证书**构建候选，核对实际包名 `dev.codex.desktopremote.lite`、版本名、递增 `versionCode`、最低 API 26 和证书 SHA-256，再使用发布器签**另一层**元数据。`--apk-verified` 是发行者对上述独立核验的声明，代码不会代替证书检查。示例只列参数，不提供 APK 或证书：

```powershell
$Apk = '<INDEPENDENTLY_VERIFIED_SIGNED_APK>'
$AndroidVersionCode = [int](Read-Host '输入与 Android releases.json 一致的 versionCode')
$AndroidSource = 'https://<REVIEWED_RELAY_HOST>/desktop-remote-updates/test/android/latest.json'
$AndroidUrl = 'https://<REVIEWED_RELAY_HOST>/desktop-remote-updates/test/android/android-0.1.0-alpha.4.apk'
& $PythonExe -B -m updates.publish sign android --artifact $Apk --output (Join-Path $OutputDir 'android-latest.json') --private-key $SigningKey --key-id $KeyId --source $AndroidSource --url $AndroidUrl --sequence $Sequence --channel test --apk-version-code $AndroidVersionCode --apk-package-id dev.codex.desktopremote.lite --min-sdk 26 --apk-verified
```

Android 普通 push/PR CI 构建嵌入空 trust 且使用临时 debug 签名，只能测试，不能生产 OTA。`.github/workflows/android.yml` 的 `workflow_dispatch` 可选 `signed_ota=true`，仅 main 且所需 GitHub secrets 全部就绪时生成受控**测试**签名 APK；CI 会核对证书指纹，artifact 保留 14 天。当前不能假设 secrets 已配置、CI 已通过或该 artifact 已上线。首次发行还必须把审核后的 `update-trust.json` 从外部注入构建，后续证书保持连续；不能靠改包名或卸载原 App 解决签名不一致。

Windows/relay 首次安装的 `trust.json` 需由受控流程从**已审核的发布公钥**制作，字段为 `schema:1`、`product:codex-desktop-remote`、`channel:test|stable`、`keys:{keyId:PEM_PUBLIC_KEY}` 和 `sources:{windows|relay|android:HTTPS_LATEST_JSON}`；只配置该安装需要的组件 source。`sign` 不替操作者生成、安装或轮换 trust。首次 bootstrap 接收本地 trust、已签元数据和对应 ZIP，随后固定在新目录；旧实例不能通过下载更新自己的信任根。发布签名和首次安装的固定公钥验证仍是内容真实性边界，**登录或配对只授予下载权限，不能代替签名**。不在客户端嵌入 GitHub PAT。

私有下载文件只放在**新受管中继**的 `home/data/update-feed/{test|stable}/{android|windows|relay}/`。`home`、`data`、`update-feed`、渠道和组件目录均须由运行中继的同一用户持有，权限 `0700`；`latest.json` 和不可变版本包须为同一用户的普通单链接文件，权限 `0600`。只允许 `latest.json` 和与组件/版本相符的 `component-x.y.z[-alpha.N].apk|zip`；不能用软链接、硬链接、重解析点或目录浏览暴露其他文件。先把**版本化包**放到目标组件目录，核对实际字节数/SHA-256、签名元数据的 URL/有效期与 HTTPS 证书；最后以同目录临时文件和原子替换发布 `latest.json`。不要原地改已有版本包，也不要先让元数据指向尚未到位的包。仓库没有通用上传命令或已批准的服务器目标；私有 GitHub Release 可作归档，14 天 Actions artifact 不能作长期 feed。

HTTPS 必须使用与账号登录、Windows 配对相同的已审核 origin。反向代理把 `/desktop-remote-updates/*` **原路径**送往新受管中继的 API loopback 监听，不使用 `file_server` 或独立公开静态目录，也不能套用只认账号的前置 `forward_auth` 遮住设备凭据。代理须保留 `Authorization`、`X-Desktop-Remote-Principal`、`X-Desktop-Remote-Device`，且不记录这些请求头、下载内容或令牌。每次 `GET` 元数据和包都由中继重新鉴权：账号会话可下载三组件；已配对且活跃的设备凭据仅可下载 Windows，暂停或撤销后新请求被拒绝；FRP plugin 监听不提供该路径。已开始的流式下载属于已准入请求，维护门禁会等它结束，不能声称撤销能追回已发出的字节。空目录、未配置 feed、过期会话及旧中继都不能作为可用发布源。

**首发顺序**：先在隔离环境核验 alpha.4 中继签名 ZIP/trust，用本地 bootstrap 建立新的受管 home 并启动该版中继；旧服务没有私有 OTA 路由，不能只靠上传文件启用它。再审核同源 HTTPS 路由和身份转发，在新受管目录放入 alpha.4 各组件构件与 `latest.json`，从对应账号/设备分别验证两次授权下载，最后才执行客户端 `check`。这仍是待执行流程，不代表当前服务器、手机或电脑已更新。发布证据至少记录源码版本、构件哈希、签名 keyId/公钥指纹、已服务文件哈希、元数据有效期、鉴权拒绝结果、安装和真实任务验收层级，勿记录私钥或账号令牌。


## 固定引导器与运行时

Windows/relay ZIP 更新的是该原型的应用源码及下次可用的更新工具。Python/cryptography，以及 Windows 的 Node/FRP 为首次安装准备的外部运行时，不在此版 OTA 中更换；当前只接受数据格式 1 与相同兼容协议，不支持自动依赖或数据库迁移。

受管 home 的固定启动脚本是首次验证包建立的信任入口，不在事务中自改。Linux 独立更新宿主在本次服务切换后继续保留原始子进程句柄，其自身 Python 模块不会热替换；正常管理员 stop 后再由固定入口 start，才加载 current 指向版本的宿主代码。若发行内容修复宿主本身，更新说明必须写明这个维护重启要求，不能把服务版本变化当作宿主代码已经重载。中继仍只通过 OTA 下载程序，不需要重新上传源码。
