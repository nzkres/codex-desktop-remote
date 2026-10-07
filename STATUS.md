# 当前验证状态：0.1.0-alpha.4

2026-10-08：已按用户确认补三端私有下载鉴权；未部署公网源、修改现用服务、迁移真实身份或安装手机新包。分支 `codex/ota-implementation` / PR #1，合并授权仍待确认。完整下一步见 [三阶段落地验收](docs/OTA-ACCEPTANCE.md)。

| 层次 | alpha.4 证据 |
| --- | --- |
| 已实现 | 元数据/包每次鉴权；账号三组件、活跃设备仅 Windows；手机复用加密登录、Windows 复用 DPAPI、中继管理员交互 login/logout；严格同 origin 与固定目录，私有文件权限、单链接检查与下载维护门禁 |
| 本机自动测试 | Node 33 项通过；Python 主套件 104 项通过、5 Linux 专属跳过；relay 77 项通过、4 Linux 专属跳过。随后新增回退原因脱敏用例及凭据套件定向 21 项通过（5 Linux 跳过） |
| Windows 隔离生命周期 | 首轮候选发生一次非预期 rolled_back，未触及现用实例；后续两轮成功 committed 0.0.2 / rolled_back 0.0.1。原日志缺首因，现新增仅保留有界固定错误码的 cause；继续由 CI 复验，不宣称已定位该偶发原因 |
| CI / 可下载构件 | 本版待 CI 与下载后校验；已发布 alpha.3 归档保持原样，见下表 |
| 实机 / 部署 | 私有 HTTPS 源、发行信任、连续 APK 签名首装与真实三端 N→N+1 均未完成 |

---

# alpha.3 验证归档

三端已加入 OTA 实现；现用手机、Windows 身份与中继没有切换。正式发布信任、长期 HTTPS 源和受控签名首次安装尚未配置，**不能把本轮代码与隔离测试说成真实用户已在线升级**。

| 层次 | 当前证据 |
| --- | --- |
| Android 实现 | 签名元数据、包大小/哈希、同包名/同 APK 证书与实际版本/minSdk 核对，系统安装确认；安装回执在重开后核验实际 APK 与新鲜任务证据 |
| Windows 实现 | 受管窗口检查/安装、独立 helper、原始进程句柄、Job 子树、稳定 data 与独立版本目录、事务 CAS、隔离验证和回退；未完成事务禁用新连接 |
| 中继实现 | 全新 Linux 受管实例、私有 Unix socket、独立宿主、实时空闲门禁、委派 cgroup v2、授权 DB 与配置摘要保全、候选验证和回退 |
| 更新源验证 | 本机临时 HTTPS/合成 CA 测试通过：TLS、重定向、损坏、大小、空间和禁用 TLS 拒绝；元数据 RSA3072 签名、平台/渠道/兼容/重放/换包测试通过 |
| Windows 生命周期 | 本机与最终 Windows CI 均通过真实 pythonw/Tk → 独立 helper → 新 GUI；成功 0.0.1→0.0.2，坏探针回退 0.0.1；全新临时 DPAPI unpaired，Desktop 探针为测试 fixture，不连接实际任务 |
| Linux 生命周期 | 最终 Core CI 通过真实中继监听/私有 socket/cgroup 两版提交与坏候选回退，4 棵自有树清理完成，数据库逻辑摘要不变 |
| 核心 CI | [169b449 / run 37646940566](https://github.com/nzkres/codex-desktop-remote/actions/runs/37646940566) Windows/Linux 均成功并产出源码 ZIP。Node 33 项；Python 主套件 89 项（Linux 跳过 13 项 Windows 专属）；relay 72 项（Windows 跳过 4 项 Linux 专属）；两平台真实进程演练也通过 |
| Android CI | [e7602ec / run 37644338199](https://github.com/nzkres/codex-desktop-remote/actions/runs/37644338199) 87 JVM 测试、lint 0 errors / 45 warnings、主 APK 与测试 APK 构建通过；Android 产品源码此后未变。未运行模拟器安装或手机真机 |
| 下载后复验 | Android 包名/versionCode/ZIP/包内说明与 Git 构建输入一致；Windows 41 文件、relay 26 文件的包清单和原始 SHA 全部通过，源码与 169b449 对应（Windows 仅两份 text=auto TXT 是 checkout CRLF） |
| 可下载构件 | [v0.1.0-alpha.3 开发预览](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.3)：APK、Windows/relay 源码 ZIP、Android 测试报告、artifact-verification.json 与 SHA256SUMS.txt。不是已签 OTA feed |
| 分发方式 | Windows/relay 为源码 ZIP，首次依赖 Python + cryptography，Windows 另需固定 Node/FRP/Desktop；bootstrap 新建受管目录。没有一体化 EXE 安装器或托盘 |
| 公网/真实设备 | 新 HTTPS feed 未部署、正式密钥未配置、现用身份未迁移；Android 覆盖安装与真实 Desktop/手机 N→N+1 尚未验收 |

详见 [三端版本历史](docs/versions/README.md)、[安装](docs/install/README.md)、[OTA 发布](docs/OTA-PUBLISH.md)、[故障恢复](docs/OTA-RECOVERY.md)和[跨电脑交接](docs/HANDOFF.md)。首装 trust、签名 APK 证书、独立源上线与真实设备切换需另行现场确认。CI 的 14 天 artifact 不是长期 OTA 源；下面旧版本证据不能替代 alpha.3 验收。

| alpha.3 构件 | 字节数 | SHA-256 |
| --- | ---: | --- |
| desktop-remote-lite-0.1.0-alpha.3-debug.apk | 11,771,316 | `d188ed0522f8fc36361565495536cdda141e36160443ededee314dbb843107f6` |
| desktop-remote-windows-0.1.0-alpha.3-source.zip | 86,838 | `b2370ea091c8c68e84ae6721275a8e01f8dd6c33de2f28d036940ae33bc8a5f6` |
| desktop-remote-relay-0.1.0-alpha.3-source.zip | 66,765 | `66e6b70ceb93c89dd129534720cc21b5a7d3fe8cbb833258557a75df0f219426` |

上述哈希用于核对归档文件完整性，不代替 OTA 发布者签名或手机 APK 连续签名。Android debug 包无更新公钥/source，应用内会说明尚未配置。下一步是审核独立测试更新源与受控首装签名，再用独立测试安装做真实 HTTPS N→N+1 与手机覆盖安装；现用服务切换仍须单独确认。

---

# alpha.2 验证归档

归档时仓库预览版本：手机、Windows、中继各为 `0.1.0-alpha.2`。各组件完整记录见 [手机](docs/versions/ANDROID.md)、[Windows](docs/versions/WINDOWS.md)、[中继](docs/versions/RELAY.md)。下表严格区分构建与安装/部署证据，不能从源码版本推定现用软件已升级。

| 层次 | 当前结果 |
| --- | --- |
| 原型与关系 | 手机 UI、Windows 连接器、中继三部分源码在同一仓库；原型说明与 Mermaid 关系/序列图已保存 |
| 安装说明 | [三端安装起点](docs/install/README.md)已提供 APK 手动安装、Windows 源码窗口与中继本地启动步骤；文档不代表完成新安装或公网部署 |
| UI 复用 | 保留 Android 原生对话/玻璃控件/草稿和 Windows 窗口；已保存旧风格规范与归属许可 |
| Node 自动测试 | 新目录本机 30/30 通过 |
| Python 连接器与版本校验测试 | Windows CI 42 项通过；本机 38 项套件及新增 4 项 APK 回归分别通过，覆盖缺字段/重复/倒序、版本码倒退、跨组件独立版本、文档过期和 APK 缺失/过期记录 |
| 中继自动测试 | 本机 49/49 通过；版本查询鉴权/启动快照/CLI 无数据库副作用也有合成测试 |
| Windows 说明窗口 | 合成 UI 冒烟通过，说明只读、保留新旧版本，打开/返回不触发断开；没有使用真实身份 |
| 核心 CI | alpha.2 Windows/Linux [run 37615952040](https://github.com/nzkres/codex-desktop-remote/actions/runs/37615952040) 均成功，源码 `27b07da`；Linux 跳过 Windows 专属测试 |
| Android 单元测试/编译 | [run 37615952157](https://github.com/nzkres/codex-desktop-remote/actions/runs/37615952157) 成功，源码 `27b07da`；72/72 JVM 测试通过，主/测试 APK 编译；未运行模拟器 instrumentation |
| Android 包内说明 | CI 与下载后双重复验：`assets/releases.json` 与源码逐字节一致，构建输出包名/版本名/版本码相符，ZIP 完整性通过；首轮缺失说明的包已拦截、未发布 |
| Android Lint | alpha.2 为 0 errors、40 warnings；报告随开发预览提供，未隐藏警告 |
| 可下载构件 | [v0.1.0-alpha.2](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.2) 提供验证后的 APK、报告与 SHA256SUMS；旧 alpha.1 保留 |
| Android 新包安装/真机 | 未安装、未验收；独立包名 `dev.codex.desktopremote.lite`，不会覆盖原 App |
| 公网中继部署 | 未部署新仓库中继，现用服务未变更 |
| 本仓库真实配对/手机验收 | 未进行；没有复制现用身份或运行配置 |
| 三端 OTA | 已按用户要求纳入必做范围，见 [OTA 要求](docs/OTA.md)；alpha.2 尚未实现，没有签名发布、在线升级或回退验收证据 |
| 安装器/托盘 | 当前未提供；OTA 所需的首次安装基线与稳定启动入口也待实现 |

参考基线：旧 Lite `c72d3c5` 曾由用户确认同一个 Desktop 任务的手机→Desktop→手机文字闭环；`2fcf9e2` 的正常断开重连曾通过两轮 Windows 隔离验证。这里只保留来源证据，**不是本仓库 Android 新包、真实配对或公网部署的验收**。

alpha.2 APK：`desktop-remote-lite-0.1.0-alpha.2-debug.apk`，11,720,835 字节，SHA-256 `1d6d76b4e6eed91ba670593743e393ca10c7e6b62b236246cc675896829b2d11`，versionCode 2。产品源码与 `27b07da` 相同，之后只补充文档证据。debug 签名没有稳定覆盖升级承诺；校验值用于核对完整性，不代替正式发布签名设计。

alpha.1 归档 APK：11,719,461 字节，SHA-256 `16fcc64a5df5310369c2e9eed8fb0f4fbf165e031e7b63c3bdc68e877e7aa2e9`；Android 产品源码 `ebdb6e4`，核心产品源码 `ae9ed7c`。历史构件和记录仍保留在对应标签。

下一次人工测试：先按 [TESTING.md](docs/TESTING.md) 运行合成自测，再以独立测试设备、专用 Desktop 任务逐项记录实际配对、手机发送、Desktop 同任务接续、手机显示、正常断开重连。未确认的发送不得重放；旧安装不能靠删除保护标志迁入。
