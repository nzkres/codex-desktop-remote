# Android 手机端：安装与首次测试

返回[三个原型的安装起点](./README.md)；版本变化见 [Android 历史](../versions/ANDROID.md)。下载起点是** `0.1.0-alpha.4` 开发预览**，适用于 Android 8.0 及以上。Release 提供经过 CI 与下载后复验的构件，不等于手机已安装或完成 OTA。实际安装与真机证据以 [STATUS](../../STATUS.md) 为准。alpha.4 普通 debug 包仍无受控签名首装信任和长期 HTTPS 源。

1. 用有权限的 GitHub 账号打开私有 [v0.1.0-alpha.4 Release](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.4)，展开 Assets，下载 `desktop-remote-lite-0.1.0-alpha.4-debug.apk`；Windows/relay 的 source ZIP 和测试报告都不能当 APK 安装。下载成功只说明文件已到手，不说明已安装或能连接服务。此前 [v0.1.0-alpha.2 Release](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.2) 保留归档。
2. 在手机上打开下载的 APK，按系统提示允许这一次安装并完成安装。不同 Android 设备的提示可能不同，参见 [Google 的 Android 应用安装帮助](https://support.google.com/android/answer/9457058?hl=zh-Hans)。此包的 application ID 是 `dev.codex.desktopremote.lite`，可与旧 App 并存；**不必卸载旧 App**。
3. 打开“Desktop Remote Lite”。登录页的“中继地址”默认留空；只有在测试 HTTPS 服务器已单独配置并核验后，才填写其地址、测试账号和密码并登录。安装 APK 不会部署中继，也不会自动导入原 App 的登录或配对身份。
4. 在“远程终端”点“添加电脑”，取得一次性配对码；在**新 Windows 连接器**中输入该码。手机刷新待确认列表，核对申请电脑的名称，再点“确认加入”。不要把配对码、账号密码或设备凭据写进仓库或反馈截图。
5. 只选择预先准备的一台测试电脑及一条专用 Desktop 原生任务：先核对手机读到该任务历史，再发送一次明确的测试文字；在 Desktop **同一任务**接续，最后确认手机能看到接续内容。连接状态、心跳或发送回执都不能单独证明模型任务完成；若发送结果未知，先核对原任务，不换消息编号重发。新建任务、审批和模式设置仍留在 Desktop。
6. 在“我的 → 版本与更新说明”确认实际显示 `0.1.0-alpha.4`（`versionCode` 4），可阅读 alpha.4 及更早记录；说明随 APK 提供，离线也能查看。记录下载、安装、登录/配对、同任务文字闭环各自的实际结果，不把前一步成功算成后一步验收。

alpha.4 包仍须手动安装。源码中已有“我的 → 版本与更新说明”的检查、下载和系统安装入口，但默认 CI/debug 包的 trust 为空，**检查更新会提示未配置**，不能用于生产 OTA。首次 OTA 安装包必须包含审核过的渠道公钥和 source，并与后续 APK 使用**同一个受控 Android 签名证书**；元数据签名与 APK 证书是两道独立验证。换一次 CI debug 签名不能保证覆盖已装 alpha.2 或其他构建。遇到签名冲突先保留应用与数据、核对签名及迁移方案，**不要卸载绕过**。本项目包名不同，不会覆盖旧 App。详见 [OTA 边界](../OTA.md) 与 [发布步骤](../OTA-PUBLISH.md)。

系统安装对话框被取消或只返回原应用时仍是“待安装/可重试”，不是成功。新版本重开后还须核对实际 version/versionCode、自身 APK 哈希和更新 ledger，再取得所选电脑的**新鲜**空闲/权限/回执证据；取不到时显示任务待核对。Android 不承诺静默安装或降级。普通 CI artifact 只保留 14 天，也不是更新服务器。

## 文件校验与常见问题

alpha.4 增加私有 OTA 登录鉴权。部署受管私有源并安装带固定 trust 的受控首装后，App 复用当前账号检查/下载，不需要另填下载密码。源须与登录服务器同 HTTPS origin，固定 `/desktop-remote-updates/test/android/`（stable 独立配置）；会话过期提示重新登录，账号切换后旧候选不能继续安装。普通预览包仍为空 trust；其构建/发布状态以 STATUS 为准。后续真实覆盖安装顺序见 [验收计划](../OTA-ACCEPTANCE.md)。

如先下载到 Windows 再传到手机，可在 APK 所在目录打开 PowerShell，核对完整性：

```powershell
Get-FileHash -Algorithm SHA256 -LiteralPath '.\desktop-remote-lite-0.1.0-alpha.4-debug.apk'
```

归档 APK 已实际下载复验：**11,771,608 字节**，SHA-256 为 `20d6b27875d70ee85b619cedaa5a18ac36750c7f9b2da001257add3d7d397a97`，`versionCode` 4，包内版本说明与 Git `78ff65d` 内容字节一致；JVM 测试 94/94，lint 0 错误、45 警告。这些是构件与检查证据，**不是手机已安装或真机 OTA 成功**。正式下载后仍须按上述哈希核对文件；哈希不代替签名。安装现成 APK 不需要 Git、JDK、Android SDK 或自行编译。

| 现象 | 从哪里排查 |
| --- | --- |
| 发布页 404 或没有下载权限 | 登录有本私有仓库权限的 GitHub 账号 |
| 系统要求允许安装来源 | 只按系统提示给当前浏览器或文件管理器安装权限，完成后可收回；不需要关闭全局安全保护 |
| “应用未安装”或签名冲突 | 核对 Android 版本、文件完整性和已有测试包签名，保留现有应用及数据 |
| 能打开但无法登录 | 核对真实 HTTPS 地址、证书、账号以及认证服务；没有自助注册，安装 App 不会生成服务器账号 |
| 登录后没有新电脑 | 新项目不会导入旧 Windows 身份；按 [Windows 指南](WINDOWS.md)完成新测试设备的申请与手机确认 |
| 看见电脑但打不开任务 | 核对转发通道与专用 Desktop 任务；在线状态不保证任务可用，按 [测试指南](../TESTING.md)逐项记录 |
