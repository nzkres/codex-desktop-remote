# 自测与验收

在仓库根目录、Windows 上使用 Python 3.12、Node 20.19 运行。合成自测不需要 FRP、Desktop 或账号；实际连接才需要另行提供 FRP 0.71。项目不下载运行工具，也不复制旧 DPAPI 身份、真实任务日志或账号数据。

```powershell
.\scripts\SelfTest.ps1 -Node '<NODE_EXE_PATH>' -Python '<PYTHON_EXE_PATH>'
```

脚本依次运行 `node --test`、`python -B -m unittest discover -s test -p 'test_*.py' -v`、`python -B -m unittest discover -s relay -p 'test_*.py' -v`。它覆盖合成数据下的适配器、Windows 控制与 relay 行为；不证明手机、Desktop、公网或安装包通过。Windows 窗口可用 `python windows/demo.py` 只看纯合成预览，不产生身份或网络连接。

Android 源码测试与构建另需 JDK 17/Android SDK 36，在 `android-native/` 运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

构建成功只说明源代码与构件通过相应检查，不是已安装手机验收。新 App ID 可与旧 App 并存；本包没有旧 OTA。debug APK 的签名随构建环境变化，跨环境覆盖安装需要相同的私有签名，不以 CI artifact 充当 OTA。

真实联调先在 Desktop 建专用任务并取得 `DESKTOP_REMOTE_READY` 回复。从该任务链接读取线程 UUID，独立核实工作目录与所需本机可执行文件路径；执行 README 中的 `scripts/configure.py` 命令。它创建忽略的 `fixture.local.json`，对 Desktop 只读核对 marker、固定版本/两文件哈希和权限摘要，并写入本地配置；不发模型请求，不覆盖已有文件。失败会保留配置，不能改 pin 绕过。新测试设备必须手动配对；旧配对状态不可迁入。

在已确认不会接管旧实例后，用 `pythonw windows/controller.py fixture.local.json` 打开连接窗口，手动填写新服务器地址。只在明确的测试范围内依次核对：目标电脑与任务匹配、手机可读原生历史、一次文字发送得到明确回执、Desktop 在同任务接续、手机读到该接续。发送结果未知时停止，不换消息 ID 重发。连接/探针成功与模型执行成功分别记录；审批、提问、新建任务和模式设置仍在 Desktop 操作。实测结果写入根 [STATUS.md](../STATUS.md) 与 [WORKLOG.md](../WORKLOG.md)。
