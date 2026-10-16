> Windows alpha.21已发布并在本机实际升级、自动连接通过，修复alpha.20安装激活故障；[发布和运行证据](docs/WINDOWS-ALPHA21-PUBLICATION.md)。源码修复分支待合并，未宣称main已更新。

> Windows alpha.20修复包已发布：已配对打开后自动连接，保留候选健康失败原因。原alpha.18连接保持，升级需任务空闲、安全断开；[发布证据及安装边界](docs/WINDOWS-ALPHA20-PUBLICATION.md)。

> 最新发布：Android alpha.20 / Windows alpha.19，正式额度动效、运行与未读、纯Loading和紧凑首页。原私有源可更新，安装与真实任务验收另记录，见[发布证据](docs/ANDROID-ALPHA20-PUBLICATION.md)。

> 2026-10-16：原手机已通过预览显示/完整播放与R3→R4独立刷新，APK版本、安装时间和哈希未变。后续兼容预览改稿只刷新R修订，见[真机验收](docs/PREVIEW-PHONE-ACCEPTANCE-20261016.md)。

> Android alpha.19：修复预览本地data正文被误拦截而无法显示；宿主本次覆盖升级，后续兼容R预览仍独立刷新。发布与手机恢复分别见[STATUS](STATUS.md)。

> Android alpha.18：我的预览支持独立R修订和刷新；一次升级宿主后，兼容演示不再每次发APK。正式功能未接入，实际构建/发布/手机层级见[STATUS](STATUS.md)。

> Android alpha.17：我的 → 界面预览 · 额度与交互，新增同账号任务运行时额度渐强呼吸与加速光环。仅离线演示，正式任务/额度未接入；发布和手机验证见[STATUS](STATUS.md)。

> Android alpha.16：新增“我的 → 界面预览 · 未读与发送按钮”，直接在手机内看离线交互。当前构建/发布/安装层级见 [STATUS](STATUS.md)。正式连接和回执协议不变。

> 最终更新：Android alpha.15与Windows alpha.18已发布原私有HTTPS更新源，原签名和实际鉴权完整下载核验通过。五种可保存的中心光格数字额度皮肤，Plus实际双窗口/Pro周单环；手机在“我的 → 版本与更新说明”检查更新。安装与真机效果另验收，见[最终发布记录](docs/ANDROID-ALPHA15-PUBLICATION.md)。

# Codex Desktop Remote

这个独立项目包含三个可迭代原型：沿用原界面的 Android 遥控 App、Windows 连接器、服务器中继。目标是让手机在**用户明确选择的一条 Codex Desktop 原生任务**中看文字、发文字，并看到 Desktop 后续在同一任务中的内容。Desktop 仍拥有任务和执行权限。源码布局及各原型边界见 [PROJECT.md](PROJECT.md) 和 [原型说明](docs/PROTOTYPES.md)；实际通过与未验收项以 [STATUS.md](STATUS.md) 为准。

Android构件保存在自有服务器，手机更新走私有HTTPS。以下alpha.9为历史交付记录：**alpha.9 已发布并完成独立验包和实际鉴权完整下载**，手机可直接 OTA，实际安装待确认。新界面与验证记录见 [alpha.9 发布记录](docs/ANDROID-ALPHA9-PUBLICATION.md)。

## 安装与开始使用

第一次使用请从 **[三个原型的安装起点](docs/install/README.md)** 开始：包含下载位置、依赖、操作步骤、成功标志和故障处理。

| 手机 App | Windows 连接器 | 服务器中继 |
| --- | --- | --- |
| [下载 APK、安装、登录与配对](docs/install/ANDROID.md) | [准备 Python、打开窗口、连接测试任务](docs/install/WINDOWS.md) | [本地隔离启动、认证与公网前置条件](docs/install/RELAY.md) |

alpha.5 已在现有服务器的独立隔离环境完成中继、FRPS、HTTPS 与私有 OTA 的安装，并从主电脑公网验收 `9443` HTTPS 与 `17443` FRPS。手机安装和真实设备联调尚未验收。Windows是源码窗口，中继是源码服务，尚无一键安装包；公网测试夹具连通不等于手机或真实任务已接通。

## 版本更新记录

**alpha.5受控测试首装包已构建并复验。** PR #1已合并，专用RSA/APK签名和六项Android CI Secrets已配置；main受控构建通过，Windows/WSL新临时目录bootstrap通过。独立服务器环境已完成 loopback 与主电脑公网 HTTPS 鉴权下载、FRPS/TLS 夹具连接、受管启停等验收。实际指纹、测试与归档证据见[STATUS](STATUS.md)，隔离部署证据见[本轮记录](docs/OTA-ISOLATED-DEPLOYMENT-20261008.md)。

三端沿用alpha.4的私有下载鉴权：手机复用账号登录，Windows复用配对身份，中继管理员交互登录后更新。元数据与包都受控，仅向同源HTTPS固定目录发送凭据，发布签名独立验证。alpha.5有受控**测试**签名；私有源已从主电脑公网完成合成账号/设备验收，但真实设备首装及alpha.5→alpha.6仍待验收。普通CI包不能直接作为OTA基线。按[三阶段验收](docs/OTA-ACCEPTANCE.md)、[实现边界](docs/OTA.md)、[发布](docs/OTA-PUBLISH.md)和[恢复](docs/OTA-RECOVERY.md)推进。

**仓库文档是查看每次更新的主要入口。** 三个软件分别保存完整历史，按版本从新到旧排列：

| 软件 | 版本说明文档 | 可以查到什么 |
| --- | --- | --- |
| Android 手机 App | [手机端版本更新记录](docs/versions/ANDROID.md) | 每版日期、功能变化、体验步骤、验证方法与限制 |
| Windows 连接器 | [电脑端版本更新记录](docs/versions/WINDOWS.md) | 每版连接器变化、体验步骤、验证方法与限制 |
| 服务器中继 | [中继服务版本更新记录](docs/versions/RELAY.md) | 每版服务变化、验证方法与部署限制 |

[全部版本索引](docs/versions/README.md) · [仓库更新总览](CHANGELOG.md) · [后续维护规则](docs/ITERATION.md)

以后每次软件版本更新，都必须把更新内容追加到对应文档并随代码提交；已发布记录持续保留。目前三份文档包含alpha.5受控首装、alpha.4私有鉴权及更早历史。

受控测试归档：[v0.1.0-alpha.5](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.5)。七项附件已发布且服务端大小/哈希核对一致；有权限的GitHub账号可下载三端程序、bootstrap公开信任/已签元数据、校验值和报告。旧alpha.4及更早版本保留。Android受控包已内置test信任，仍为debuggable测试构建；Windows/relay需要外部运行时，尚无一体化EXE或托盘。建议先看[原型关系图](docs/ARCHITECTURE.md)，再运行合成自测/窗口预览，最后依[测试指南](docs/TESTING.md)做专用任务联调。问题按[测试反馈](https://github.com/nzkres/codex-desktop-remote/issues/new?template=test-feedback.yml)记录实际版本与结果。

手机选择框、审批、Plan及其他特殊操作的当前支持情况与手机操作设计，见[特殊交互清单](docs/MOBILE-SPECIAL-INTERACTIONS.md)。该清单区分现有能力和待实现功能。

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
