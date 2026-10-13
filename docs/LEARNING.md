# 今晚的源码阅读与测试路线

核心源码现已补中文注释，先看 [注释阅读入口与覆盖清单](ANNOTATIONS.md)。

2026-10-12。阅读基线为 codex/desktop-1007-recovery（Windows alpha.8），Android OTA 后续开发另在 codex/android-alpha6 工作树。不要把当前分支当成全项目所有最新变更的合集。

先打开 [完整文件树](REPOSITORY-TREE.md)（266 个 Git 跟踪文件的快照）和 [代码导览](CODE-MAP.md)。本指南只增加教学资料，不改变产品代码或现用安装。README 中部分安装说明仍为早期记录；本次学习不按它重新配对或重装，实际状态看 STATUS.md 最新段。

## 今晚的目标

用约 2–3 小时掌握四条路径：连接、发消息与同步、断开重连、OTA。完成标准是能指出入口函数、数据归属、失败停点和对应测试，而不是今晚记住全部 266 个文件。底层 Windows API、完整私有 IPC 协议和 Android 全部 UI 后续逐项深入。

## 0. 先认目录（10 分钟）

| 目录 | 职责 | 今晚重点 |
| --- | --- | --- |
| windows/ | Python 窗口、连接控制、进程与 Windows 更新适配 | 必看 |
| src/ | Node 本机接口和 Desktop 原任务跟随 | 必看 |
| updates/ | 共享更新运行代码及发布维护工具 | 看 engine/security，其他按调用读 |
| relay/ | 服务器账户、设备与转发 | 理解请求经过哪里 |
| android-native/ | Android 界面与网络客户端 | 看 Gateway/TaskTarget/ChatScreen |
| test/ | 合成自动化测试与窗口测试 | 与代码一起读 |
| scripts/ | 配置、检查和隔离演练工具 | 先用学习菜单 |
| deploy/ | 测试环境部署模板 | 今晚不部署 |
| docs/ | 架构、安装、更新与交接 | 作为索引 |
| third-party/ | 复用代码许可证 | 保留 |
| .github/ | CI 和问题反馈模板 | 最后了解 |

画出：手机 → 中继 → Windows 本机接口 → Desktop 原任务。反向是结果同步。Desktop 才持有和执行任务。

## 1. 窗口如何发起连接（20 分钟）

打开 [windows/demo.py](../windows/demo.py)，只有 Demo.status/pair/connect 三个假方法。再打开 [windows/gui.py](../windows/gui.py)，按顺序找：

1. _Window.__init__：按钮绑定到哪个方法。
2. connect：设置 busy/connecting，把命令放入 commands 队列。
3. _work：工作线程调用 controller.connect，网络工作不阻塞界面。
4. _drain：主界面线程接收 events，更新文字和按钮。
5. disconnect/close：通过 stop_event 请求停止，而不是直接结束 Desktop。

实验：双击本机 artifacts/learning/Open-Learning.cmd，输入 1。窗口标题明确为“界面演示”。填入演示码 012345ABCDEF，点击配对，再连接、断开、再次连接、查看版本说明。最后关闭演示窗口返回菜单。

注意：Demo 不真正校验/持久化配对，也不模拟真实手机确认。连接时显示通道未实时检查是正确现象。该模式没有受管安装，所以 OTA 按钮不可用；OTA 实验见第 4 步。不要在此窗口填写真实配对码。

你应能回答：为什么点连接后窗口没有卡死？为何界面演示的“在线”不代表真连通？

## 2. 连接与断开由谁控制（25 分钟）

打开 [windows/controller.py](../windows/controller.py)：

- Controller.connect：先查询登记，再进入长期运行循环。
- Controller._runtime：取得通道配置、建立自有 Node/FRP 进程对象。
- LiteRuntime.reconcile：本机接口健康才启动传输；未获允许时停止传输。
- LiteRuntime.disconnect：先核对本机操作退出，再确认两个自有进程树退出。

接着打开 [connector_core.py](../windows/vendor/connector_core.py)：run → _owned_run → _run → _tick。_run 决定循环与退避，_tick 查询配对与心跳；正常在线间隔 15 秒，错误退避从 1 秒到最多 30 秒。

实验：菜单 2，阅读 20 项测试结果。对应 [test_controller.py](../test/test_controller.py)、[test_lifecycle.py](../test/test_lifecycle.py)、[test_gui_status.py](../test/test_gui_status.py)。先读 test_normal_stop_clears_only_guard_and_same_identity_can_reconnect，再读 test_abnormal_exception_keeps_guard_even_when_both_trees_stop。

当前限制：正常停止判断依赖 connected；异常退出保护不是自动恢复。原接口进程 max_restarts=0，任务 owner 变化也会拒绝。这里是在认识现有行为，没有实现上轮讨论的精简方案。

你应能回答：登记在线与任务可用有什么区别？断开会不会停止 Desktop 模型？哪些失败会保留 runtimeNeedsReview？

## 3. 手机如何接续同一个任务（35 分钟）

从 [src/main.js](../src/main.js) 的启动装配看起，然后看 [service.js](../src/service.js)：

- target：只接受配置中的任务 UUID。
- read：读取原任务实时状态、权限，再投影为手机数据。
- send：核对 clientMessageId、权限、空闲状态，写 pending 回执，再转发原生 start-turn，得到回复后写 accepted。
- prepareDisconnect：关闭新操作入口，等待已进入的操作结束，检查回执。

然后看 [follower.js](../src/follower.js)：readFresh 发现任务 owner；receive 接收同一任务的实时快照；forward 只允许限定原生操作。最底层 desktop-ipc.js 此时只需理解“命名管道上的请求/响应”，不用先读全部实现。

最后看 [receipts.js](../src/receipts.js) 的 begin/accept/get/assertCertain：为什么 pending 不能自动重发？

实验：菜单 3。41 项测试使用假 Desktop 和临时回执目录；其中 HTTP 用本机随机端口，不经过公网。阅读 [follower.test.mjs](../test/follower.test.mjs) 和 [service.test.mjs](../test/service.test.mjs)。找到 owner change、unknown send、prepare-disconnect 等测试，先预测结果再看断言。

你应能回答：任务 UUID 在哪里限制？“服务器返回成功”是否代表模型完成？为什么网络超时后不能换一个消息编号再发？

## 4. OTA 为什么要有多个步骤（30 分钟）

阅读顺序：

1. [windows/gui.py](../windows/gui.py) 的 update/_work：检查与下载入口。
2. [windows/update_ui.py](../windows/update_ui.py)、[update_helper.py](../windows/update_helper.py)：把安装交给独立助手。
3. [updates/engine.py](../updates/engine.py) 的 check/stage/Transaction.apply：检查、暂存、切换、健康确认、提交或回退。
4. [windows/update_driver.py](../windows/update_driver.py)：把通用事务接到 Windows 启停与健康检查。
5. [updates/security.py](../updates/security.py)：发布签名、平台/版本/完整性校验。

主线：检查版本 → 下载暂存并验签 → 预检任务 → 旧实例退出 → 切换版本指针 → 新版隔离验证 → 开放使用 → committed。失败回退必须先确认新实例清理完成；不能只恢复一个旧数据库覆盖新数据。

实验：菜单 4。临时签名/包/目录是真实的，下载响应和程序启停 driver 是模拟的，不连接私有更新源，不安装任何真实版本。阅读 [test_update_engine.py](../test/test_update_engine.py) 中 success、candidate_failure、unproven_cleanup、power_loss、activate_failure 五个测试。总计与安全校验一起 20 项。

你应能回答：下载完成为什么不算成功？新版本已接受操作以后为什么不能盲目回退？旧身份放在哪里，为什么不放版本目录？

## 5. 把手机和服务器接回地图（20 分钟）

Android 源码根 android-native/app/src/main/java/dev/codex/remote/：Gateway.kt 查请求地址和解析；TaskTarget.kt 查选中的电脑；TerminalControl.kt 查配对；ChatScreen.kt 查聊天展示。当前 Windows 工作树里不是 Android OTA 最新开发分支。

服务器阅读 relay/control_server.py → registry.py → forwarder.py：分别查 HTTP 入口、配对/设备状态和按目标转发。无需今晚部署服务器或编译 APK。

验收自己的理解：任选一次手机发消息，依次说出“手机哪个模块发、中继哪个模块转、电脑哪个函数查权限、哪个函数交给 Desktop、回执哪里存”。然后用同样方法复述一次更新。

## 工具与本轮验证

本机菜单：artifacts/learning/Open-Learning.cmd；脚本 Learning.ps1 固定本机 Python/Node 路径，不含凭据。日志写入同目录，已被 Git 忽略。打开 cmd 的 ExecutionPolicy Bypass 只作用于这次 PowerShell 进程，不更改系统策略。

2026-10-12 正常用户环境实际通过：连接 20/20、任务 41/41、OTA 20/20，共 81 项。第一次菜单运行遇到 Windows PowerShell 将 unittest 标准错误输出视为失败，已让 unittest 输出到 stdout 修正；沙箱中任务测试曾有 9 项失败，正常用户环境重跑 41 项全部通过，不隐藏失败记录。以上只验证选定教学测试，不声称本轮完整 CI、Android 编译或真实 OTA 通过。

本机 GUI 演示复用现成代码；不要把“模拟窗口”“自动化测试”“真机验收”混为一谈。学习时不用运行 configure、controller、maintenance、publish 命令或删除安装 data/state。已有连接继续正常使用。

## service.js 逐步 HTTP 实验

双击 artifacts/learning/Open-Service-Lesson.cmd，每步先预测再按回车。真实运行 src/service.js 的 HTTP 接口和 Receipts 文件读写；Desktop 用内存对象替代，不访问现用身份、任务或公网。这不是手机/模型实机验收。

对应场景：read 读历史不执行模型；target 拒绝其他任务；send 接收新消息；重复编号返回旧回执而不再转发；任务忙或权限变更拒绝；updateStatus 检查空闲；响应丢失保留未知并阻止换号重发；未知回执阻止更新。9/9 自动模式已验证。脚本退出清理自己创建的临时目录，重复运行从新合成状态开始。

深入函数时按“调用前状态 → 输入 → 分支判断 → 发出了什么/写了什么 → 返回结果”五项记录，不必逐字背语法。比如重复发送实验：已有 accepted 回执 → 相同编号和内容 → get 返回旧回执 → 没有调用 follower.forward → 返回 replayed=true。
