## alpha.14 更新说明

新版本采用原私有源、包名、签名与覆盖安装流程。进入“我的 → 版本与更新说明 → 检查更新”，安装alpha.14后在“对话与玻璃 → 终端皮肤 → 额度样式”选择五种样式，重开保留。输入框额度与实时背景预览已删除；各样式的数值都在环中央。套餐标签需Windows alpha.18同一任务额度事件提供；旧连接器仍可显示实际窗口。发布、下载与安装结果分开记入STATUS。

## alpha.13 GitHub与额度入口

沿用私有更新源、应用包与原签名，覆盖更新并保留应用数据。任务仓库 → GitHub：公开库可直接填写owner/repo；私有库在GitHub创建仅指定仓库 Contents: Read-only 的fine-grained令牌后，在手机保存凭据，再关联仓库。无需手工JSON；令牌仅在手机加密保存，只发送给api.github.com。移除凭据不会删除上次读取项目。文档与状态约定见[任务仓库资料约定](../PROJECT-REPOSITORY.md)。额度在对话页设备名称下显示；相同账号跨电脑共享额度，不代表每台电脑有独立配额。

发布与安装分开核验，当前交付状态见STATUS.md；不会自动修改原配对或重发旧消息。

## alpha.12 更新入口（2026-10-15）

原私有源已发布alpha.12。等当前任务结束，在“我的 → 版本与更新说明 → 检查更新”确认alpha.12，再按系统提示覆盖安装，保留原应用数据、账号和电脑配对。安装后底栏为“对话 / 任务仓库 / 我的”，设备管理从对话或我的进入；原发送、额度、皮肤与回执保护保留。

任务仓库是本机加密资料，支持人工核对后导入/导出JSON，不自动扫描GitHub。发布下载已验证，手机安装、真实UI与同任务接续待验收，见[alpha.12发布记录](../ANDROID-ALPHA12-PUBLICATION.md)。以下旧版与首装说明作为历史保留。


# Android 安装与更新：从这里开始

## 现用手机（2026-10-15）

**Desktop Remote Lite alpha.11 已发布到现有私有更新源，原证书及实际 HTTPS 完整下载已验证。** 手机实际覆盖安装仍需用户确认，见 [发布证据](../ANDROID-ALPHA11-PUBLICATION.md)。

1. 等当前任务结束，保留原 APP、账号与电脑配对。
2. “我的 → 版本与更新说明 → 检查更新”，确认 alpha.11；下载约 40.1 MB 安装包。
3. 按 Android 系统弹窗确认覆盖安装；不卸载、不清数据，不绕过签名或任务状态门禁。
4. 重开核对实际 alpha.11，检查原电脑、任务和草稿仍在。在“我的 → 对话与玻璃”切换皮肤、字体、背景动效与速度/亮度；返回对话体验多轮吸顶与 Working 计时。

手机仍使用原私有 HTTPS 地址与登录。普通问答沿用 alpha.8 链路；异步问题、模型修改及未开放模式控制仍在电脑处理。设置保存在手机本地，字体离线可用。Android 8–11 使用半透明材质，Android 12 以上支持背景模糊。

新版更新错误会分别提示暂停、离线、任务忙、权限未核验、网络失败和两端未确认发送回执；请按具体原因处理。

额度需要电脑连接器alpha.17；当前任务结束后，电脑先断开，再“检查更新 → 下载并安装”，更新完成重新连接。背景在“我的 → 对话与玻璃 → 终端皮肤”调节光格、网格线与核心流光独立亮度（0–400%），重开保留。

## 历史alpha.5首装资料

以下用于解释现有安装来源；已有配对手机不要照此重新首装。历史GitHub Release保留，新Android受控构建不再存入GitHub构件或Release。

# Android 手机端：安装与首次测试

返回[三个原型的安装起点](./README.md)；版本变化见 [Android 历史](../versions/ANDROID.md)。[私有 `0.1.0-alpha.5` prerelease](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.5) 已发布，七项资产的服务端大小与 SHA-256 和本地一致；逐件证据见 [STATUS](../../STATUS.md)。受控 Android CI 37736837251 已成功，APK 为 `versionCode` 5、包名 `dev.codex.desktopremote.lite` 的 **debuggable 受控测试签名构建**，证书 SHA-256 为 `ffcb12d413ee58613576b7914350ad4df887bf29d50d97f2b20c4e17dcebcf1a`。历史alpha.5首装和隔离9443私有源随后已完成部署，用户已在该环境登录并验证原任务；本次未重新核查手机当前版本。alpha.7的同证书覆盖升级及新版按需加载仍待验收，不能沿用alpha.5成功记录。

以下1–6步是alpha.5新设备首装历史步骤；已有配对用户不要重新安装首装包、重新配对或卸载APP，应使用本页当前alpha.8发布流程。

1. 用有权限的 GitHub 账号打开已发布的私有 [v0.1.0-alpha.5 Release](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.5)，下载 `android-0.1.0-alpha.5.apk`、`bootstrap-0.1.0-alpha.5.zip`、`artifact-verification.json` 和 `SHA256SUMS.txt`；`android-reports-0.1.0-alpha.5.zip` 是测试报告。解压 bootstrap，核对 `android/trust.json` 与 `android/latest.json`；trust 的公钥还须对照已认证仓库记录中的 `keyId=cdr-ota-test-20261008-v1`、SPKI SHA-256 `c3835e3e59b362e0b356d3a7b3589c255187ddce34692167f5160b0e5f5e3a4d`，不能只信同一下载页的哈希。下载成功不等于安装或连通。
2. 在专用测试手机上核对现有同包名 App 的签名及数据后，再打开已核验 APK，按系统提示允许这一次安装。不同设备的提示可能不同，参见 [Google 的 Android 应用安装帮助](https://support.google.com/android/answer/9457058?hl=zh-Hans)。本包 application ID 是 `dev.codex.desktopremote.lite`，可与**旧项目不同包名**的 App 并存；同包名的旧 debug 安装可能证书不同，**不要卸载绕过或承诺覆盖成功**。
3. 打开“Desktop Remote Lite”。登录页的“中继地址”默认留空；只有在测试 HTTPS 服务器已单独配置并核验后，才填写其地址、测试账号和密码并登录。安装 APK 不会部署中继，也不会自动导入原 App 的登录或配对身份。
4. 在“远程终端”点“添加电脑”，取得一次性配对码；在**新 Windows 连接器**中输入该码。手机刷新待确认列表，核对申请电脑的名称，再点“确认加入”。不要把配对码、账号密码或设备凭据写进仓库或反馈截图。
5. 只选择预先准备的一台测试电脑及一条专用 Desktop 原生任务：先核对手机读到该任务历史，再发送一次明确的测试文字；在 Desktop **同一任务**接续，最后确认手机能看到接续内容。连接状态、心跳或发送回执都不能单独证明模型任务完成；若发送结果未知，先核对原任务，不换消息编号重发。新建任务、审批和模式设置仍留在 Desktop。
6. 在“我的 → 版本与更新说明”核对实际显示 `0.1.0-alpha.5`（`versionCode` 5）及更早记录；说明随 APK 提供，离线也能查看。分别记录下载、安装、登录/配对、同任务文字闭环的实际结果，不把前一步成功算成后一步验收。

alpha.5 仍由系统手动确认安装，沿用“版本与更新说明”的检查、下载入口，没有新 UI。已发布的受控测试 APK 预置测试渠道 trust，元数据签名与 APK 证书是两道独立验证；普通 push/PR CI debug 包仍是空 trust 与临时签名，不能成为连续升级基线。后续 APK 必须保持同一个受控证书；遇到签名冲突先保留应用与数据、核对证书及方案，**不要卸载绕过**。首装元数据有效期仅 7 天，过期须由维护者重新受控签发，不能关闭验证。详见 [OTA 边界](../OTA.md) 与 [发布步骤](../OTA-PUBLISH.md)。

系统安装对话框被取消或只返回原应用时仍是“待安装/可重试”，不是成功。新版本重开后还须核对实际 version/versionCode、自身 APK 哈希和更新 ledger，再取得所选电脑的**新鲜**空闲/权限/回执证据；取不到时显示任务待核对。Android 不承诺静默安装或降级。普通 CI artifact 只保留 14 天，也不是更新服务器。

## 文件校验与常见问题

部署受管私有源并安装带固定 trust 的受控首装后，App 才能复用当前账号检查/下载，不另填下载密码。测试 origin 固定为 `https://106.53.126.69:9443`，源路径为 `/desktop-remote-updates/test/android/`；**该隔离入口已部署；alpha.8已发布到正式测试更新索引，旧元数据可能过期，不能绕过有效期检查**。会话过期提示重新登录，账号切换后旧候选不能继续安装。后续真实覆盖安装顺序见 [验收计划](../OTA-ACCEPTANCE.md)。

如先下载到 Windows 再传到手机，可在 APK 所在目录打开 PowerShell，核对完整性：

```powershell
Get-FileHash -Algorithm SHA256 -LiteralPath '.\android-0.1.0-alpha.5.apk'
```

alpha.5 的准确文件大小和 SHA-256 已在 [STATUS](../../STATUS.md) 记录，服务端与本地核对一致；不要拿旧 alpha.4 哈希比较。APK 签名证书须与本页指纹和后续升级包一致，签名元数据须由上述固定公钥验证；同一 Release 的哈希表只辅助检查传输完整性。元数据 sequence 1，有效至 **2026-10-15 06:33:03 UTC（北京时间 14:33:03）**，过期须维护者重新受控签发。alpha.4 的原 debug 构件仍保留在[历史 Release](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.4)，不重签或替换。安装现成 APK 不需要 Git、JDK 或 Android SDK。

| 现象 | 从哪里排查 |
| --- | --- |
| 发布页 404 或没有下载权限 | 登录有本私有仓库权限的 GitHub 账号 |
| 系统要求允许安装来源 | 只按系统提示给当前浏览器或文件管理器安装权限，完成后可收回；不需要关闭全局安全保护 |
| “应用未安装”或签名冲突 | 核对 Android 版本、文件完整性和已有测试包签名，保留现有应用及数据 |
| 能打开但无法登录 | 核对真实 HTTPS 地址、证书、账号以及认证服务；没有自助注册，安装 App 不会生成服务器账号 |
| 登录后没有新电脑 | 新项目不会导入旧 Windows 身份；按 [Windows 指南](WINDOWS.md)完成新测试设备的申请与手机确认 |
| 看见电脑但打不开任务 | 核对转发通道与专用 Desktop 任务；在线状态不保证任务可用，按 [测试指南](../TESTING.md)逐项记录 |
