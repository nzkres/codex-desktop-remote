# Android alpha.19 本地预览主文档显示修复

`0.1.0-alpha.19` / versionCode19 / 私有序号13已发布原更新源，正常TLS、账号鉴权实际完整APK下载及签名/大小/哈希通过。后续2026-10-16已在用户USB授权手机通过显示、完整播放和同APK刷新R3→R4，见[真机验收](PREVIEW-PHONE-ACCEPTANCE-20261016.md)。下文保留发布当时的证据与未执行项。

## 原因与修复

用户真机截图显示“已获取最新预览 · R3”，但WebView在本地 `data:text/html;charset=utf-8;base64,` 页面报告 `net::ERR_HTTP_RESPONSE_CODE_FAILURE`。获取、鉴权、验签已经结束，失败发生在显示阶段。alpha.18拦截器对所有资源统一返回403；Android的shouldInterceptRequest也包含本地data请求，因此正文被宿主误拦。

alpha.19仅让data方案交给WebView本地解码；其他资源继续403，blockNetworkLoads、文件/内容禁用、导航/下载/权限阻断、无原生JS桥与CSP保持。不会通过放开网络或任意本地文件绕过。显示状态分为内容已取到、正在显示、页面已显示、显示失败；只有onPageCommitVisible且无失败才提示已显示。主框架错误显示本机错误码及原生提示层，刷新成功后重建失败视图，旧视图回调不改新状态。相同内容正常刷新不重新加载。

依据：[Android WebViewClient官方文档](https://developer.android.com/reference/android/webkit/WebViewClient#shouldInterceptRequest(android.webkit.WebView,%20android.webkit.WebResourceRequest))。仅修复宿主与对应状态，没有改正式对话、身份、草稿、连接、额度、回执或保护标志。

## 手机更新与验收

保留应用数据，在我的 → 版本与更新说明检查并覆盖升级alpha.19，不卸载/清数据。进入界面预览并刷新，看到R3后点击播放完整动效，确认正文可见、启动渐强/加速及停止缓退。切换设备与五种样式仅演示，不操作真实任务。连接和原对话仍应正常。

这次缺陷在已安装宿主，需要本次一次升级；R3内容未重发，预览发布机制与后台路由未变。之后兼容HTML/CSS/JS预览仍只更新R修订，不必每次下载APK；新增原生能力或宿主修复仍需正常升级。

## 验证层级

[PR24](https://github.com/nzkres/codex-desktop-remote/pull/24)，原证书受控构建源码 `837315bf7ec430e68a28e7a5505ffa09bdb75c97`。新增4项本地data与外部资源隔离/获取和显示状态单测，以及真实Android WebView回归用例，覆盖本地中文正文、内联JS计时、CSP、无桥与网络/文件设置。

- Android157项单测通过，Lint错误0，App与UI测试APK编译。
- 本机adb列表为空，无模拟器/安卓设备；真实WebView回归用例仅编译，未执行。桌面浏览器验证不能替代真机显示，此前alpha.18缺口已记录，不声称alpha.19手机已经恢复。
- 核心Node182通过，Python208项（7跳过）、中继77项（4跳过）。均合成数据和隔离临时目录。
- GitHub Android38758420086/job116502932852、Core38758420037/job116502933709注释确认账号计费/消费限制导致未启动；push38758375104/38758375143亦未运行。未改计费、Secrets或保护，普通合并，无admin绕过，不宣称CI通过。
- 独立验包确认包名dev.codex.desktopremote.lite、versionCode19、minSdk26、单一原证书 `ffcb12d413ee58613576b7914350ad4df887bf29d50d97f2b20c4e17dcebcf1a`，内嵌原trust和版本说明字节一致。临时签名材料已清理。

| 项目 | 结果 |
| --- | --- |
| APK大小 | 40439252 bytes |
| APK SHA256 | `1d9f086d5a269cdd47ca71fe4ef12f7b9b11c241b518172e419da736c435fc8b` |
| 新索引 SHA256 | `03fc94960b085f440274710a002a477151dca54c3eef8b5adcc42dba321e8452` |
| 旧索引 SHA256 | `0f1cc88b285d8a2940407bea1c1b4b62e03e59314c50b8e33527ac84f853db04` |
| 发布UTC | 2026-10-15T15:22:13.823448+00:00 |
| HTTPS复验UTC | 2026-10-15T15:23:53.789098+00:00 |
| 匿名索引/APK | 401 / 401 |
| 鉴权索引/完整APK | 正常TLS、private no-store、大小/哈希/签名通过 |
| alpha.19手机安装/显示/播放 | 待验收 |

## 发布边界

原锁内复核旧索引/签名/防回退/大小/哈希，原子替换软件latest；alpha.18及更早APK/旧索引保留。服务器服务PID/启动时间、程序、Windows/relay更新源与预览R3保持，未改Caddy路由、重启连接器或中继，没有重放未知操作或替用户安装。验证只创建并注销本次临时登录，不影响手机会话。

审计 `/opt/codex-desktop-remote-test/operator/android-alpha19-feed-20261015-seq13`。私有APK只在原服务器，GitHub私有预发布只提供说明、无APK或凭据资产。修复后实际手机效果以用户验收为准。
