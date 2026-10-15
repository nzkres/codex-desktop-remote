# 手机预览内容独立更新

用户保留“我的 → 预览”，但不希望每次看设计都下载APK。alpha.18新增一次安装的预览宿主，兼容内容用独立修订R更新。**UI草案、布局、颜色和动效演示可以单独发布；新增原生能力、协议或宿主修复仍需更新APP。**

## alpha.19 本地显示修复

用户手机已取到R3，但alpha.18统一403拦截也作用于WebView本地data主文档，导致ERR_HTTP_RESPONSE_CODE_FAILURE。alpha.19只允许data本地解码，其他资源继续403；网络、文件、导航、CSP和真实任务隔离不变。获取成功不再等同显示成功，页面可见提交/失败分别提示，失败刷新重建；此宿主修复需要一次APK升级，不更改后续内容独立刷新机制。

依据：[WebViewClient.shouldInterceptRequest](https://developer.android.com/reference/android/webkit/WebViewClient#shouldInterceptRequest(android.webkit.WebView,%20android.webkit.WebResourceRequest))明确包含data等本地方案，而非仅网络请求。新增真实WebView回归用例，本机无安卓设备时只记录编译，不能以桌面浏览器替代执行。

## 手机流程

我的 → 界面预览 · 可独立刷新。打开自动取最新签名内容，右上“刷新预览”只发GET、不运行安装器。页面显示预览R修订，未过期且有效的上次预览可在网络失败时保留；登录失效、未发布、过期、来源/校验异常分别提示。支持“内置示例”回看原生演示。

预览缓存按原服务器和当前登录哈希隔离，不是对话离线缓存；退出登录或更换来源不沿用别的登录。相同修订/绑定重新刷新不重载正在演示的页面，不把网络错误写成已更新。

## 内容格式与发布

`docs/prototypes/quota-live-preview.html`是首个合成演示。片段不超过256KiB，CSS/脚本和小资源内联；禁止依赖外网、宿主widget接口、真实账户/任务或私密资料。签名封套总大小不超过512KiB，明确用途`desktop-remote-ui-preview`、renderer=1、单调sequence、source、有效期、标题和HTML SHA256。使用既有受保护发行密钥，私钥不进入预览或仓库。

在仓库根运行，路径参数使用仓库外的新输出文件，签名库是已审核的现有外部库：

```powershell
python -B -m scripts.preview_feed --html docs/prototypes/quota-live-preview.html --output <NEW_ENVELOPE_FILE> --sequence <NEXT_PREVIEW_REVISION> --title <PREVIEW_TITLE> --source https://106.53.126.69:9443/desktop-remote-previews/test/latest.json --signing-home <EXISTING_PROTECTED_SIGNING_HOME> --key-id cdr-ota-test-20261008-v1
```

此命令只签名，不上传、不更新APP版本。发布前需在隔离浏览器验证首屏、交互、手机宽度和网络隔离。管理员复用`scripts/preview_feed_server.py`的publish模式：计划包含新公共封套、公钥与预期旧文件哈希；原锁内验签/期限/递增修订，保存旧封套，原子替换独立`latest.json`，最后经真实HTTPS鉴权复验。错误或未知先核对服务端记录，不能盲重放。恢复使用更高R修订重发旧设计，不降修订、不覆盖已记录历史。

**普通预览更新只改独立内容文件**，不改APP目录、APK源或代理配置，也不重启服务。R修订不是软件版本，不为一次HTML改稿虚增Android版本；内容修订记在对应发布证据中。

## 内容修订 R3（2026-10-15）

额度动效增加“播放完整动效”：先空闲，约0.9秒后模拟家用电脑启动，同账号额度渐强呼吸、外环连续加速，运行约10秒后全部停止并缓慢淡出。手动切换设备或任务开关取消自动演示，后台不继续触发演示阶段；五种额度样式与账号隔离保留。数字仅为合成演示，真实额度不扣减。

源片段 `docs/prototypes/quota-live-preview-r3.html`，签名内容29955 bytes，SHA256 `57dc0d774d8acc8db4ad503bbae81f410842c9fa698b1bc4bf004778193e1127`。服务器2026-10-15T14:45:53.528124+00:00原子发布R3，14:46:11.003117+00:00实际HTTPS鉴权读取、正常TLS、用途签名、匿名/错误令牌401通过，临时验收会话已注销。服务、代理配置、Android/Windows/relay更新索引未变，没有新APK。

浏览器验证渐进启动/运行/停止、账号隔离、五种样式、320/390手机宽度及CSP阻止fetch通过，无脚本错误和外部请求。手机实际刷新与视觉仍待用户验收。审计沿用operator/preview-feed-bootstrap-20261015的r3.json与r3-published.json，回退设计需更高R修订。

## 一次性服务入口

新入口固定在原独立:9443代理：`GET /desktop-remote-previews/test/latest.json`。账号鉴权通过原中继只读`/control-api/version`完成；未登录或错误令牌不能取文件。唯一公开匹配名重写为`/config/ui-preview-test/latest.json`，无目录浏览、其他文件和上传接口；`private, no-store`、nosniff。原APK/Windows/relay鉴权路由不变，原443 Gateway不动。

prepare先核对固定容器、镜像、挂载和原配置哈希，在原挂载配置目录准备候选并执行Caddy validate；activate保留原文件inode（单文件绑定挂载），通过既有本机管理端口热加载，并核对原服务PID/启动时间、各软件更新源、原443配置和中继程序保持不变。备份及审计在原私有operator目录。不会新开端口、重启中继或建立新服务器。

## 宿主边界

原生下载器才持有登录令牌，只发送到安装trust约束的同源HTTPS；拒绝重定向。页面只收到已核验片段，使用独立虚拟origin；无`addJavascriptInterface`，网络、文件/内容访问、外跳、下载、定位、权限申请和多窗口禁用。CSP再次限制网络、frame/worker/object/media/form/base；JavaScript仅用于本地动画与模拟操作。后台暂停，关闭销毁视图；不读取真实额度、草稿或回执。

本机制不是APP远程原生代码更新，也不是任意网页浏览器。检查安装包、服务发布、浏览器演示、Android编译和真实手机体验分别记录。

参考：[Android WebView原生桥风险说明](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges)、[Caddy forward_auth](https://caddyserver.com/docs/caddyfile/directives/forward_auth)、[file_server](https://caddyserver.com/docs/caddyfile/directives/file_server)、[配置校验与重载](https://caddyserver.com/docs/command-line)。
