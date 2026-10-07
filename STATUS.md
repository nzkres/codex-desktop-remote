# 当前验证状态

当前仓库预览版本：手机、Windows、中继各为 `0.1.0-alpha.2`。各组件完整记录见 [手机](docs/versions/ANDROID.md)、[Windows](docs/versions/WINDOWS.md)、[中继](docs/versions/RELAY.md)。下表严格区分构建与安装/部署证据，不能从源码版本推定现用软件已升级。

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
| 安装器/托盘/在线更新 | 未提供，未纳入本轮范围 |

参考基线：旧 Lite `c72d3c5` 曾由用户确认同一个 Desktop 任务的手机→Desktop→手机文字闭环；`2fcf9e2` 的正常断开重连曾通过两轮 Windows 隔离验证。这里只保留来源证据，**不是本仓库 Android 新包、真实配对或公网部署的验收**。

alpha.2 APK：`desktop-remote-lite-0.1.0-alpha.2-debug.apk`，11,720,835 字节，SHA-256 `1d6d76b4e6eed91ba670593743e393ca10c7e6b62b236246cc675896829b2d11`，versionCode 2。产品源码与 `27b07da` 相同，之后只补充文档证据。debug 签名没有稳定覆盖升级承诺；校验值用于核对完整性，不代替正式发布签名设计。

alpha.1 归档 APK：11,719,461 字节，SHA-256 `16fcc64a5df5310369c2e9eed8fb0f4fbf165e031e7b63c3bdc68e877e7aa2e9`；Android 产品源码 `ebdb6e4`，核心产品源码 `ae9ed7c`。历史构件和记录仍保留在对应标签。

下一次人工测试：先按 [TESTING.md](docs/TESTING.md) 运行合成自测，再以独立测试设备、专用 Desktop 任务逐项记录实际配对、手机发送、Desktop 同任务接续、手机显示、正常断开重连。未确认的发送不得重放；旧安装不能靠删除保护标志迁入。
