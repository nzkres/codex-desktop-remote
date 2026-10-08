# 自测与验收

从 [三阶段 OTA 落地验收](OTA-ACCEPTANCE.md)查看资源准备、三端逐一 N→N+1 和失败演练。alpha.4 新增私有 HTTPS 客户端、真实账号过期/登出和设备暂停/撤销测试；Windows 身份缺失不创建，Linux 会话文件保护与登出 CAS、Android 登录切换与严格同源目录均有覆盖。所有这些仍是合成验证，不能替代部署和手机安装。

新增三端 OTA 的专项验收清单见 [OTA.md](OTA.md)：连续两个构件、签名/平台/渠道拒绝、活跃与未知操作延期、进程树清理、更新中断、启动/探针失败和数据保留均需覆盖。alpha.3 新增 test/test_update_*.py、test/test_windows_update_driver.py、relay 的维护/宿主测试与 Android Update*Test；各层结果见 STATUS，不能用旧 alpha.2 结果代替。

在仓库根目录、Windows 上使用 Python 3.12 + cryptography、Node 20.19 运行。合成自测不需要 FRP、Desktop 或账号；实际连接才需要另行提供 FRP 0.71。项目不下载运行工具，也不复制旧 DPAPI 身份、真实任务日志或账号数据。

```powershell
.\scripts\SelfTest.ps1 -Node '<NODE_EXE_PATH>' -Python '<PYTHON_EXE_PATH>'
```

脚本先检查三份版本记录与生成文档，再运行 `node --test`、`python -B -m unittest discover -s test -p 'test_*.py' -v`、`python -B -m unittest discover -s relay -p 'test_*.py' -v`。它覆盖合成数据下的适配器、Windows 控制与 relay 行为；不证明手机、Desktop、公网或安装包通过。Windows 窗口可用 `python windows/demo.py` 只看纯合成预览，不产生身份或网络连接。

本次版本说明的体验验收：

| 软件 | 操作 | 预期 |
| --- | --- | --- |
| 手机 | 我的 → 版本与更新说明，阅读/滚动后返回；进入我的后断网重试 | 版本与实际 APK 相符，alpha.2/alpha.1 都可读，每项有体验和验证文字；返回不改变草稿，不自动下载 |
| Windows | `python windows/demo.py`，点底部版本说明，滚动并返回 | 只读文本包含新旧版本；原连接窗口保留，查看说明不设置断开信号 |
| 中继 | `python relay/control_server.py --version` 和 `--release-notes` | 无需 `--db`，直接显示版本/历史并退出，不新建数据库或监听器 |
| 中继接口 | 合成 HTTP 测试请求 `/control-api/version` | 合法账号得到启动时版本快照；匿名/错误令牌拒绝，plugin 端口 404；无需使用真实令牌 |

只改仓库文件不等于升级软件。反馈时分别写明手机、Windows 与实际中继版本；旧中继没有版本接口就记“未知”，不要把源码版本填进去。

Android 源码测试与构建另需 JDK 17/Android SDK 36，在 `android-native/` 运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

构建成功只说明源代码与构件通过相应检查，不是已安装手机验收。新 App ID 可与旧 App 并存；本包没有旧 OTA。debug APK 的签名随构建环境变化，跨环境覆盖安装需要相同的私有签名，不以 CI artifact 充当 OTA。

真实联调先在 Desktop 建专用任务并取得 `DESKTOP_REMOTE_READY` 回复。从该任务链接读取线程 UUID，独立核实工作目录与所需本机可执行文件路径；执行 README 中的 `scripts/configure.py` 命令。它创建忽略的 `fixture.local.json`，对 Desktop 只读核对 marker、固定版本/两文件哈希和权限摘要，并写入本地配置；不发模型请求，不覆盖已有文件。失败会保留配置，不能改 pin 绕过。新测试设备必须手动配对；旧配对状态不可迁入。

在已确认不会接管旧实例后，用 `pythonw windows/controller.py fixture.local.json` 打开连接窗口，手动填写拟用于测试的 HTTPS 中继地址。已有中继只有在协议匹配且允许新增独立测试设备时才能使用；此步骤不要求替换现用服务器，也不代表本仓库已经部署中继。只在明确的测试范围内依次核对：目标电脑与任务匹配、手机可读原生历史、一次文字发送得到明确回执、Desktop 在同任务接续、手机读到该接续。发送结果未知时停止，不换消息 ID 重发。连接/探针成功与模型执行成功分别记录；审批、提问、新建任务和模式设置仍在 Desktop 操作。实测结果写入根 [STATUS.md](../STATUS.md) 与 [WORKLOG.md](../WORKLOG.md)。

## OTA 隔离验证命令

先在独立 Python 环境安装 `python -m pip install -r updates/requirements.txt`，再运行上述全套自测。TLS 测试仅在临时 loopback HTTPS 监听和合成 CA 上运行，未关闭 TLS 校验。Windows 用 `python -B scripts/windows_ota_smoke.py` 验证真实 pythonw/Tk 窗口、独立 helper、原始句柄与 Job 的升级/回退；它只创建临时 unpaired DPAPI 身份，Desktop 探针使用签名 fixture，不访问实际任务。

Linux 用 `DESKTOP_REMOTE_TEST_CGROUP=/sys/fs/cgroup/<专用委派目录> python -B scripts/relay_ota_smoke.py` 验证真实 relay 监听、账号/登记库、私有 socket、cgroup 树退出、提交与坏候选回退。没有该环境变量时明确跳过，跳过不算通过。只在隔离机器或 CI 使用；不要借用现用服务 cgroup。两种生命周期演练用本地签名 fixture 包，真实 TLS 下载另由 test_update_https.py 覆盖，两者不能合称生产网络端到端验收。
