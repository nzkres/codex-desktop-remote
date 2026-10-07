# OTA 打包、签名与发布交接

以下是**尚待配置发行资源**的操作范式，不表示已经签发或上线。当前 `updates.publish` 只在本地打包 Windows/relay 源码 ZIP、对已有 APK/ZIP 签发布元数据，**不构建 APK、不上传文件、不部署中继**。正式发布须有受控 RSA 3072 位以上私钥、经审核的公钥/trust、Android 连续 APK 签名证书、长期独立 HTTPS 静态目录和实际发布权限。私钥路径、口令与凭据不得进入仓库、日志或命令示例。

先在仓库根目录更新发生变化组件的 `releases.json` 与版本说明，运行 `python scripts/check_versions.py --write` 及只读校验和相关测试。`pack` 从当前源码按固定白名单生成新文件，输出路径必须尚不存在。以下 PowerShell **占位符必须按已审核的真实资源替换**，`$PythonExe` 为带 `cryptography` 的 Python 3.12；本仓库不提供密钥或部署主机：

```powershell
$PythonExe = '<PYTHON_3_12_EXE>'
$OutputDir = '<EXISTING_EMPTY_LOCAL_RELEASE_DIRECTORY>'
$SigningKey = '<EXTERNAL_CONTROLLED_RSA_PRIVATE_KEY_PEM>'
$KeyId = '<REVIEWED_KEY_ID>'
$Sequence = [int](Read-Host '输入已审核的下一发布序号')
$Source = 'https://<REVIEWED_HOST>/desktop-remote-updates/test/windows/latest.json'
$ArtifactUrl = 'https://<REVIEWED_HOST>/desktop-remote-updates/test/windows/windows.zip'
$Zip = Join-Path $OutputDir 'windows.zip'
$Metadata = Join-Path $OutputDir 'windows-latest.json'
& $PythonExe -B -m updates.publish pack windows --output $Zip
& $PythonExe -B -m updates.publish sign windows --artifact $Zip --output $Metadata --private-key $SigningKey --key-id $KeyId --source $Source --url $ArtifactUrl --sequence $Sequence --channel test
```

Relay 同理，将 `windows` 换成 `relay`、使用独立的 relay `source`/ZIP URL、输出文件与该组件的递增序号。`sign` 要求 artifact URL 与 source 在同一 HTTPS 目录；默认元数据有效期 7 天，过期须**新序号、新签名**，不能擅自改已签字段。不同渠道的公钥、source 与序号须独立审查，`stable` 不接受预发布版本。签名后的 `latest.json` 不能人工编辑。

Android 先用**同一受控 APK 签名证书**构建候选，核对实际包名 `dev.codex.desktopremote.lite`、版本名、递增 `versionCode`、最低 API 26 和证书 SHA-256，再使用发布器签**另一层**元数据。`--apk-verified` 是发行者对上述独立核验的声明，代码不会代替证书检查。示例只列参数，不提供 APK 或证书：

```powershell
$Apk = '<INDEPENDENTLY_VERIFIED_SIGNED_APK>'
$AndroidVersionCode = [int](Read-Host '输入与 Android releases.json 一致的 versionCode')
$AndroidSource = 'https://<REVIEWED_HOST>/desktop-remote-updates/test/android/latest.json'
$AndroidUrl = 'https://<REVIEWED_HOST>/desktop-remote-updates/test/android/android.apk'
& $PythonExe -B -m updates.publish sign android --artifact $Apk --output (Join-Path $OutputDir 'android-latest.json') --private-key $SigningKey --key-id $KeyId --source $AndroidSource --url $AndroidUrl --sequence $Sequence --channel test --apk-version-code $AndroidVersionCode --apk-package-id dev.codex.desktopremote.lite --min-sdk 26 --apk-verified
```

Android 普通 push/PR CI 构建嵌入空 trust 且使用临时 debug 签名，只能测试，不能生产 OTA。`.github/workflows/android.yml` 的 `workflow_dispatch` 可选 `signed_ota=true`，仅 main 且所需 GitHub secrets 全部就绪时生成受控**测试**签名 APK；CI 会核对证书指纹，artifact 保留 14 天。当前不能假设 secrets 已配置、CI 已通过或该 artifact 已上线。首次发行还必须把审核后的 `update-trust.json` 从外部注入构建，后续证书保持连续；不能靠改包名或卸载原 App 解决签名不一致。

Windows/relay 首次安装的 `trust.json` 需由受控流程从**已审核的发布公钥**制作，字段为 `schema:1`、`product:codex-desktop-remote`、`channel:test|stable`、`keys:{keyId:PEM_PUBLIC_KEY}` 和 `sources:{windows|relay|android:HTTPS_LATEST_JSON}`；只配置该安装需要的组件 source。`sign` 不替操作者生成、安装或轮换 trust。首次 bootstrap 接收本地 trust、已签元数据和对应 ZIP，随后固定在新目录；旧实例不能通过下载更新自己的信任根。

将**版本化构件先上传**独立 HTTPS 目录，核对服务端实际字节数/SHA-256、证书与访问控制；最后原子更新该目录的 `latest.json`，再从客户端执行 `check`。仓库没有通用上传命令或已批准的服务器目标。私有 GitHub Release 可作归档，14 天 Actions artifact 不能作长期 feed，更不能在客户端内置 GitHub PAT。发布证据至少记录源码版本、构件哈希、签名 keyId/公钥指纹、已服务文件哈希、元数据有效期、安装和真实任务验收层级，勿记录私钥或账号令牌。
