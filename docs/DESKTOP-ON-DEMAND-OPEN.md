# 手机点击标题，加载 Desktop 原对话

状态：Windows alpha.14、Android alpha.7候选代码完成。真实Desktop隔离接口验证通过；现用Windows仍alpha.13，候选签名发布和手机验收未完成。版本与测试的最新结果以 STATUS.md 为准。

## 用户会看到什么

手机先列出这台电脑的对话标题。点击一条已有对话时，连接器检查Desktop能否读取；若明确没有加载，就让Desktop自己打开同一个对话，再读历史。电脑可能切换到这条对话页面。不会给模型发送唤醒文字，不复制线程，不创建独立执行器。超过等待时间仍未就绪，会显示“电脑仍在打开这条对话，请稍后点击重试”。

Android alpha.7 与Windows alpha.14需配合使用；旧APP不会发出打开请求。旧连接器会忽略新APP的open参数，不能因此宣称旧版本已经支持。现有中继转发查询参数，不需新部署。原身份、Desktop授权和未知发送保护不变。

## 从点击到历史的代码

1. `android-native/.../DesktopOpenIntent.kt`：将一次用户点击绑定到确切Gateway实例和任务ID，在发请求前消费，避免轮询重放。
2. `Gateway.kt`：仅显式读取带 `GET /api/sessions/<id>/mobile-sync?open=1`。`MainActivity.kt`收到明确desktop_open_pending后，有限等待并用普通请求读取，切换目标取消等待（底层网络读取超时最长25秒，协程取消可能等该读取结束，不承诺严格15秒墙钟）；通知、后台、分页、发送后刷新都不附open。
3. `src/service.js`：鉴权后只允许mobile-sync的一次有效open参数，拒绝分页混用。
4. `src/tasks.js`：任务须仍在本机目录，相同目标共享打开请求；另一目标、断开、更新隔离、写操作或未知回执不导航。即将导航时写既有活动记录；未证实空闲不能升级。
5. `src/desktop-open.js`：发现已有owner就直接读取；仅remote no-client-found才校验兼容性、再查目录、核对固定启动文件并打开。异步步骤后和spawn前重新检查断开/更新及IPC代数。
6. 原 `Service.read` 和Follower再取同一UUID的实时快照、核对目录/权限和历史。启动程序成功不等于读取成功；发送仍沿用原门禁。

## 本机入口证据

只读核对已固定 Desktop 26.1007.2314.0 的安装清单及app.asar，未修改内部文件。AppxManifest.xml的协议所属Application Executable为 `app/ChatGPT.exe`。其SHA-256为 `13d7ee588d7ae20f04f7aa25a65ae82917ada81501304ac8f5e679d0b3306601`，Authenticode Valid，发布者OpenAI OpCo, LLC。现有asar/CLI兼容检查仍保留；其他版本不猜兼容。

原生URI为 `codex://threads/<UUID>`。bootstrap解析threads路由，单实例转发argv，主程序消费deep link。最初尝试同目录Codex.exe未使目标owner就绪，因此未采用该程序；改用安装清单的ChatGPT.exe后实测成功。产品按固定程序及唯一URI参数启动，不用shell、不杀Desktop进程、不传模型输入。

## 已验证及尚待验证

- 默认只读探针 `scripts/desktop_open_probe.mjs <本机配置文件> <已有任务UUID>`；显式 `--open` 才导航，输出固定布尔值/状态/数量，不输出正文或凭据。
- 已有纯口令测试：ownerBefore=false，打开后sameTask/historyReadable=true、idle，模型输入0。
- 第二次完整隔离HTTP路径：初次503 desktop_open_pending；后续不带open的读取200、sameTask=true、itemCount=2、opens=1、模型输入0。使用临时数据/回执、随机内存token、隔离端口。测试记录保存在本机ignored artifacts/alpha14，不进入Git。
- Node242项通过，Python172项中166通过、6平台跳过。覆盖已有owner、明确缺失、超时不导航、二次目录变化、断开竞态、错误代数、去重、鉴权参数、未完成打开阻止OTA等。
- Android意图与HTTP测试已加入；实际编译、模拟器、签名APK、真实手机点开旧对话与发送接续须分别记录，不能用本机HTTP通过替代。

## 手机验收顺序

完成受控同签名APK与Windows包校验和升级后：保留原配对，进入原电脑；选一条已有未加载对话，点击标题，等待历史。核对仍是原任务；若要验发送，由用户发送一次测试文字并在Desktop原对话接续，最后手机核对新回复。结果不明时不重发，不新建替代任务。旧版本和真实数据保持可恢复。
