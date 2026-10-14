## Android alpha.7 候选：手机点开原对话

此功能需要Windows alpha.14与Android alpha.7配合。现用Windows仍alpha.13；本段不表示候选已经签名发布或安装。先完成CI、原签名包校验及受控升级，保留原配对，不重新配置身份。手机点击原电脑中的标题会请求Desktop加载同一个对话，可能切换电脑页面；后台刷新不自动打开。详细流程与验收见[按需打开](../DESKTOP-ON-DEMAND-OPEN.md)与[最新状态](../../STATUS.md)。

Android必须使用原受控证书连续升级；普通PR debug APK不能覆盖现用测试APP，不要卸载绕过。中继不需要随此次功能升级。下方旧版本安装记录仅供历史参考，以STATUS的实际交付记录为准。

# Android 手机端：安装与首次测试

返回[三个原型的安装起点](./README.md)；版本变化见 [Android 历史](../versions/ANDROID.md)。[私有 `0.1.0-alpha.5` prerelease](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.5) 已发布，七项资产的服务端大小与 SHA-256 和本地一致；逐件证据见 [STATUS](../../STATUS.md)。受控 Android CI 37736837251 已成功，APK 为 `versionCode` 5、包名 `dev.codex.desktopremote.lite` 的 **debuggable 受控测试签名构建**，证书 SHA-256 为 `ffcb12d413ee58613576b7914350ad4df887bf29d50d97f2b20c4e17dcebcf1a`。可先下载核对；隔离测试 HTTPS 源已从主电脑公网通过账号鉴权下载，真实手机安装、登录和在线升级仍待单独批准与验收，见[部署记录](../OTA-ISOLATED-DEPLOYMENT-20261008.md)。

第 1 步可现在用于本地下载与核对；第 2–6 步须待专用测试手机及用户对安装的单独授权后执行。主电脑公网连通测试不替代手机网络验收。

1. 用有权限的 GitHub 账号打开已发布的私有 [v0.1.0-alpha.5 Release](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.5)，下载 `android-0.1.0-alpha.5.apk`、`bootstrap-0.1.0-alpha.5.zip`、`artifact-verification.json` 和 `SHA256SUMS.txt`；`android-reports-0.1.0-alpha.5.zip` 是测试报告。解压 bootstrap，核对 `android/trust.json` 与 `android/latest.json`；trust 的公钥还须对照已认证仓库记录中的 `keyId=cdr-ota-test-20261008-v1`、SPKI SHA-256 `c3835e3e59b362e0b356d3a7b3589c255187ddce34692167f5160b0e5f5e3a4d`，不能只信同一下载页的哈希。下载成功不等于安装或连通。
2. 在专用测试手机上核对现有同包名 App 的签名及数据后，再打开已核验 APK，按系统提示允许这一次安装。不同设备的提示可能不同，参见 [Google 的 Android 应用安装帮助](https://support.google.com/android/answer/9457058?hl=zh-Hans)。本包 application ID 是 `dev.codex.desktopremote.lite`，可与**旧项目不同包名**的 App 并存；同包名的旧 debug 安装可能证书不同，**不要卸载绕过或承诺覆盖成功**。
3. 打开“Desktop Remote Lite”。登录页的“中继地址”默认留空；在测试 HTTPS 服务器经手机实际网络核验可达后，填写其地址、测试账号和密码并登录。安装 APK 不会部署中继，也不会自动导入原 App 的登录或配对身份。
4. 在“远程终端”点“添加电脑”，取得一次性配对码；在**新 Windows 连接器**中输入该码。手机刷新待确认列表，核对申请电脑的名称，再点“确认加入”。不要把配对码、账号密码或设备凭据写进仓库或反馈截图。
5. 只选择预先准备的一台测试电脑及一条专用 Desktop 原生任务：先核对手机读到该任务历史，再发送一次明确的测试文字；在 Desktop **同一任务**接续，最后确认手机能看到接续内容。连接状态、心跳或发送回执都不能单独证明模型任务完成；若发送结果未知，先核对原任务，不换消息编号重发。新建任务、审批和模式设置仍留在 Desktop。
6. 在“我的 → 版本与更新说明”核对实际显示 `0.1.0-alpha.5`（`versionCode` 5）及更早记录；说明随 APK 提供，离线也能查看。分别记录下载、安装、登录/配对、同任务文字闭环的实际结果，不把前一步成功算成后一步验收。

alpha.5 仍由系统手动确认安装，沿用“版本与更新说明”的检查、下载入口，没有新 UI。已发布的受控测试 APK 预置测试渠道 trust，元数据签名与 APK 证书是两道独立验证；普通 push/PR CI debug 包仍是空 trust 与临时签名，不能成为连续升级基线。后续 APK 必须保持同一个受控证书；遇到签名冲突先保留应用与数据、核对证书及方案，**不要卸载绕过**。首装元数据有效期仅 7 天，过期须由维护者重新受控签发，不能关闭验证。详见 [OTA 边界](../OTA.md) 与 [发布步骤](../OTA-PUBLISH.md)。

系统安装对话框被取消或只返回原应用时仍是“待安装/可重试”，不是成功。新版本重开后还须核对实际 version/versionCode、自身 APK 哈希和更新 ledger，再取得所选电脑的**新鲜**空闲/权限/回执证据；取不到时显示任务待核对。Android 不承诺静默安装或降级。普通 CI artifact 只保留 14 天，也不是更新服务器。

## 文件校验与常见问题

隔离受管私有源已安装并从主电脑公网验证账号鉴权；待带固定 trust 的受控 APK 实际首装与登录验收后，App 才能复用当前账号检查/下载，不另填下载密码。测试 origin 固定为 `https://106.53.126.69:9443`，源路径为 `/desktop-remote-updates/test/android/`；**主电脑公网通过仍不能证明手机实际可访问或已完成 App 内下载**。会话过期提示重新登录，账号切换后旧候选不能继续安装。后续真实覆盖安装顺序见 [验收计划](../OTA-ACCEPTANCE.md)。

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
