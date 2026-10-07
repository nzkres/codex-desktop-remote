# Android 手机端：安装与首次测试

返回[三个原型的安装起点](./README.md)；本版变更和限制见 [Android 版本历史](../versions/ANDROID.md)。这是 `0.1.0-alpha.2` 开发预览，适用于 Android 8.0 及以上。仓库已核对可下载 APK 及包内版本记录，**尚无这个新 APK 的手机安装或真机联调验收**；最新证据以根目录 [STATUS.md](../../STATUS.md) 为准。

1. 用有权限的 GitHub 账号打开私有 [v0.1.0-alpha.2 Release](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.2)，展开 Assets，下载 `desktop-remote-lite-0.1.0-alpha.2-debug.apk`；校验文件是同页的 `SHA256SUMS.txt`，不是源码 ZIP 或测试报告。下载成功只说明文件已到手，不说明已安装或能连接服务。
2. 在手机上打开下载的 APK，按系统提示允许这一次安装并完成安装。不同 Android 设备的提示可能不同，参见 [Google 的 Android 应用安装帮助](https://support.google.com/android/answer/9457058?hl=zh-Hans)。此包的 application ID 是 `dev.codex.desktopremote.lite`，可与旧 App 并存；**不必卸载旧 App**。
3. 打开“Desktop Remote Lite”。登录页的“中继地址”默认留空；只有在测试 HTTPS 服务器已单独配置并核验后，才填写其地址、测试账号和密码并登录。安装 APK 不会部署中继，也不会自动导入原 App 的登录或配对身份。
4. 在“远程终端”点“添加电脑”，取得一次性配对码；在**新 Windows 连接器**中输入该码。手机刷新待确认列表，核对申请电脑的名称，再点“确认加入”。不要把配对码、账号密码或设备凭据写进仓库或反馈截图。
5. 只选择预先准备的一台测试电脑及一条专用 Desktop 原生任务：先核对手机读到该任务历史，再发送一次明确的测试文字；在 Desktop **同一任务**接续，最后确认手机能看到接续内容。连接状态、心跳或发送回执都不能单独证明模型任务完成；若发送结果未知，先核对原任务，不换消息编号重发。新建任务、审批和模式设置仍留在 Desktop。
6. 在“我的 → 版本与更新说明”确认显示 `0.1.0-alpha.2`，可阅读 alpha.2 与 alpha.1 记录；说明随已安装包提供，离线也能查看。记录下载、安装、登录/配对、同任务文字闭环各自的实际结果，不把前一步成功算成后一步验收。

本包没有旧 OTA，在新 OTA 实现前仍须手动安装。**后续必做方式**是“我的 → 版本与更新说明”检查更新、下载并验证、交由 Android 系统确认覆盖安装，见 [OTA 要求](../OTA.md)。CI/debug APK 的签名可能随构建环境改变，不能保证覆盖安装旧的**本项目**测试包；遇到签名冲突先保留现有应用及数据、核对签名与版本，再决定迁移方法，**不要默认卸载**。它也不会覆盖包名不同的旧 App。

## 文件校验与常见问题

如先下载到 Windows 再传到手机，可在 APK 所在目录打开 PowerShell，核对完整性：

```powershell
Get-FileHash -Algorithm SHA256 -LiteralPath '.\desktop-remote-lite-0.1.0-alpha.2-debug.apk'
```

结果应与 `SHA256SUMS.txt` 一致。本版大小为 11,720,835 字节，SHA-256 为 `1d6d76b4e6eed91ba670593743e393ca10c7e6b62b236246cc675896829b2d11`；哈希用于发现损坏，不代替发布签名。安装现成 APK 不需要 Git、JDK、Android SDK 或自行编译。

| 现象 | 从哪里排查 |
| --- | --- |
| 发布页 404 或没有下载权限 | 登录有本私有仓库权限的 GitHub 账号 |
| 系统要求允许安装来源 | 只按系统提示给当前浏览器或文件管理器安装权限，完成后可收回；不需要关闭全局安全保护 |
| “应用未安装”或签名冲突 | 核对 Android 版本、文件完整性和已有测试包签名，保留现有应用及数据 |
| 能打开但无法登录 | 核对真实 HTTPS 地址、证书、账号以及认证服务；没有自助注册，安装 App 不会生成服务器账号 |
| 登录后没有新电脑 | 新项目不会导入旧 Windows 身份；按 [Windows 指南](WINDOWS.md)完成新测试设备的申请与手机确认 |
| 看见电脑但打不开任务 | 核对转发通道与专用 Desktop 任务；在线状态不保证任务可用，按 [测试指南](../TESTING.md)逐项记录 |
