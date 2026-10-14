# Codex Desktop Remote

这个独立项目包含三个可迭代原型：沿用原界面的 Android 遥控 App、Windows 连接器、服务器中继。目标是让手机在**用户明确选择的一条 Codex Desktop 原生任务**中看文字、发文字，并看到 Desktop 后续在同一任务中的内容。Desktop 仍拥有任务和执行权限。源码布局及各原型边界见 [PROJECT.md](PROJECT.md) 和 [原型说明](docs/PROTOTYPES.md)；实际通过与未验收项以 [STATUS.md](STATUS.md) 为准。

Android构件保存在自有服务器，手机更新走私有HTTPS；**alpha.9 已发布并完成独立验包和实际鉴权完整下载**，手机可直接 OTA，实际安装待确认。新界面与验证记录见 [alpha.9 发布记录](docs/ANDROID-ALPHA9-PUBLICATION.md)。

## 安装与开始使用

第一次使用请从 **[三个原型的安装起点](docs/install/README.md)** 开始：包含下载位置、依赖、操作步骤、成功标志和故障处理。

| 手机 App | Windows 连接器 | 服务器中继 |
| --- | --- | --- |
| [下载 APK、安装、登录与配对](docs/install/ANDROID.md) | [准备 Python、打开窗口、连接测试任务](docs/install/WINDOWS.md) | [本地隔离启动、认证与公网前置条件](docs/install/RELAY.md) |

当前先核对首装资料、预览合成窗口；隔离HTTPS中继部署和真实设备安装需分别确认后联调。Windows是源码窗口，中继是源码服务，尚无一键安装包；中继本地启动不等于手机可从公网连接。

## 版本更新记录

**alpha.5受控测试首装候选已构建并复验。** PR #1已合并，专用RSA/APK签名和六项Android CI Secrets已配置；main受控构建通过，Windows/WSL新临时目录bootstrap通过。实际指纹、测试与归档证据见[STATUS](STATUS.md)。服务器预检、固定测试origin和撤回步骤见[部署审查清单](docs/OTA-DEPLOYMENT-REVIEW.md)，尚未部署。

三端沿用alpha.4的私有下载鉴权：手机复用账号登录，Windows复用配对身份，中继管理员交互登录后更新。元数据与包都受控，仅向同源HTTPS固定目录发送凭据，发布签名独立验证。alpha.5有受控**测试**签名；公网源、真实设备首装及alpha.5→alpha.6仍待验收。普通CI包不能直接作为OTA基线。按[三阶段验收](docs/OTA-ACCEPTANCE.md)、[实现边界](docs/OTA.md)、[发布](docs/OTA-PUBLISH.md)和[恢复](docs/OTA-RECOVERY.md)推进。

**仓库文档是查看每次更新的主要入口。** 三个软件分别保存完整历史，按版本从新到旧排列：

| 软件 | 版本说明文档 | 可以查到什么 |
| --- | --- | --- |
| Android 手机 App | [手机端版本更新记录](docs/versions/ANDROID.md) | 每版日期、功能变化、体验步骤、验证方法与限制 |
| Windows 连接器 | [电脑端版本更新记录](docs/versions/WINDOWS.md) | 每版连接器变化、体验步骤、验证方法与限制 |
| 服务器中继 | [中继服务版本更新记录](docs/versions/RELAY.md) | 每版服务变化、验证方法与部署限制 |

[全部版本索引](docs/versions/README.md) · [仓库更新总览](CHANGELOG.md) · [后续维护规则](docs/ITERATION.md)

以后每次软件版本更新，都必须把更新内容追加到对应文档并随代码提交；已发布记录持续保留。目前三份文档包含alpha.5受控首装、alpha.4私有鉴权及更早历史。

受控测试归档：[v0.1.0-alpha.5](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.5)。七项附件已发布且服务端大小/哈希核对一致；有权限的GitHub账号可下载三端程序、bootstrap公开信任/已签元数据、校验值和报告。旧alpha.4及更早版本保留。Android受控包已内置test信任，仍为debuggable测试构建；Windows/relay需要外部运行时，尚无一体化EXE或托盘。建议先看[原型关系图](docs/ARCHITECTURE.md)，再运行合成自测/窗口预览，最后依[测试指南](docs/TESTING.md)做专用任务联调。问题按[测试反馈](https://github.com/nzkres/codex-desktop-remote/issues/new?template=test-feedback.yml)记录实际版本与结果。

## 先安全自测

软件内也显示同一份说明：手机“我的 → 版本与更新说明”、Windows 窗口底部“版本与更新说明”、中继 `python relay/control_server.py --release-notes`。实际安装或部署版本需单独核对，不能从仓库版本推定。

合成自测需要 Python 3.12、`updates/requirements.txt` 中的 cryptography、Node 20.19；Windows 上会额外验证真实 Job 进程树，窗口演示还需 Tkinter。只有实际连接才需要 FRP 0.71 和指定版本 Desktop。工具须由使用者自行可靠取得；仓库不会自动下载安装。Node 无外部 npm 依赖；Android 构建另需 JDK 17、Android SDK 36 与 Gradle/Maven 依赖。

```powershell
.\scripts\SelfTest.ps1 -Node '<NODE_EXE_PATH>' -Python '<PYTHON_EXE_PATH>'
python windows/demo.py
```

前一命令运行 Node、Windows Python 和 relay 的合成测试；后一命令只预览 Windows 窗口，使用纯合成状态，不保存身份、不连接服务器或 Desktop。具体测试与失败边界见 [docs/TESTING.md](docs/TESTING.md)。当前代码的核心 CI、Android 编译和隔离 OTA 演练结果见 [STATUS](STATUS.md)；新包安装和真机联调仍待独立验收，不能把原仓库或 Lite 的通过记录算作本仓库通过。

## 准备一条真实测试任务

先在 Desktop 建立**专用测试任务**，让它明确回复 `DESKTOP_REMOTE_READY`；再从该任务链接取线程 UUID，并核实该任务的工作目录、Desktop 安装包根目录、Node 与 frpc 可执行文件路径。不要从旧配置复制真实 DPAPI 身份、配对资料或发送账本。项目只接受已验证的 Desktop `26.930.7945.0` 及固定文件哈希；未知安装包不能绕过兼容门槛。

```powershell
python scripts/configure.py --thread-id '<TASK_UUID>' --project '<PROJECT_PATH>' --desktop-root '<PACKAGE_ROOT>' --node '<NODE_EXE_PATH>' --frpc '<FRPC_EXE_PATH>'
```

脚本新建被 Git 忽略的 `fixture.local.json`，随后对 Desktop 只读探测该任务 marker 和权限摘要，并写入本地配置；不发模型消息、不配对、不覆盖已有配置。探测失败时配置会保留，需先查明原因，不要改 pin 或重放未知操作。通过准备检查并确认不会接管旧运行实例后，打开连接窗口，填写服务器地址并与手机**重新手动配对**：

```powershell
pythonw windows/controller.py fixture.local.json
```

连接窗口的服务器地址默认留空，由用户填写。当前现用旧实例不能由本命令直接接管，见 [RECOVERY.md](RECOVERY.md)。本仓库不修改原 APK 安装或公网服务。

Android 包的 application ID 独立，可与旧 App 并存，**不用卸载旧 App**；本包关闭旧 OTA。CI/debug 构件没有稳定的跨机器签名，后续覆盖安装必须使用同一私有签名，不能承诺 debug APK 可连续升级。手机 UI 沿用 [原视觉基线](docs/UI-BASELINE.md)，但原界面里保留的“新建任务、审批、模式”等入口并不代表新连接器已经接通这些能力；这些操作目前留在 Desktop。版本计划见 [docs/ITERATION.md](docs/ITERATION.md)。
