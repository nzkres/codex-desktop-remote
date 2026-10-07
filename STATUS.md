# 当前验证状态

版本：`0.1.0-alpha.1`。2026-10-07 创建独立仓库；这里的结果只属于本仓库中相应代码/构建层，不能从旧项目直接推定安装或手机验收。

| 层次 | 当前结果 |
| --- | --- |
| 原型与关系 | 手机 UI、Windows 连接器、中继三部分源码在同一仓库；原型说明与 Mermaid 关系/序列图已保存 |
| UI 复用 | 保留 Android 原生对话/玻璃控件/草稿和 Windows 窗口；已保存旧风格规范与归属许可 |
| Node 自动测试 | 新目录本机 30/30 通过 |
| Python 连接器自动测试 | 新目录本机 28/28 通过，含新建 Windows Job 进程树和本地配置生成边界测试 |
| 中继自动测试 | 新目录本机 45/45 通过；均为合成身份、临时数据库、回环服务 |
| 核心 CI | Windows、Linux 均通过：[run 37608868813](https://github.com/nzkres/codex-desktop-remote/actions/runs/37608868813)，源码 `ae9ed7c`；Linux 跳过 Windows 专属测试 |
| Android 单元测试/编译 | [run 37608678246](https://github.com/nzkres/codex-desktop-remote/actions/runs/37608678246) 成功，源码 `ebdb6e4`；70/70 JVM 测试通过，主 APK 与 instrumentation APK 编译成功；未运行模拟器 instrumentation |
| Android Lint | 0 errors、39 warnings；警告包括依赖版本、SharedPreferences/KTX 建议、备份规则、剪贴板常量和应用图标；未把警告隐藏或当作全清零 |
| 可下载构件 | 主 debug APK 已从成功 CI 下载，ZIP 完整性通过；[v0.1.0-alpha.1](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.1) 保存 APK、SHA256SUMS 与报告；不是 OTA |
| Android 新包安装/真机 | 未安装、未验收；独立包名 `dev.codex.desktopremote.lite`，不会覆盖原 App |
| 公网中继部署 | 未部署新仓库中继，现用服务未变更 |
| 本仓库真实配对/手机验收 | 未进行；没有复制现用身份或运行配置 |
| 安装器/托盘/在线更新 | 未提供，未纳入本轮范围 |

参考基线：旧 Lite `c72d3c5` 曾由用户确认同一个 Desktop 任务的手机→Desktop→手机文字闭环；`2fcf9e2` 的正常断开重连曾通过两轮 Windows 隔离验证。这里只保留来源证据，**不是本仓库 Android 新包、真实配对或公网部署的验收**。

APK：`desktop-remote-lite-0.1.0-alpha.1-debug.apk`，11,719,461 字节，SHA-256 `16fcc64a5df5310369c2e9eed8fb0f4fbf165e031e7b63c3bdc68e877e7aa2e9`。这是 CI 生成的 debug 签名构件，没有稳定覆盖升级承诺；校验值用于核对下载完整性，不代替正式发布签名设计。发布时 Android 产品源码与 `ebdb6e4` 相同，核心产品源码与 `ae9ed7c` 相同，其后只整理文档。

下一次人工测试：先按 [TESTING.md](docs/TESTING.md) 运行合成自测，再以独立测试设备、专用 Desktop 任务逐项记录实际配对、手机发送、Desktop 同任务接续、手机显示、正常断开重连。未确认的发送不得重放；旧安装不能靠删除保护标志迁入。
