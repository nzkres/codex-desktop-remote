# 当前验证状态

当前开发版本：手机、Windows、中继各为 `0.1.0-alpha.2`。各组件完整记录见 [手机](docs/versions/ANDROID.md)、[Windows](docs/versions/WINDOWS.md)、[中继](docs/versions/RELAY.md)。下表严格区分本版与 alpha.1 证据，不能从旧项目或旧包直接推定安装、部署或手机验收。

| 层次 | 当前结果 |
| --- | --- |
| 原型与关系 | 手机 UI、Windows 连接器、中继三部分源码在同一仓库；原型说明与 Mermaid 关系/序列图已保存 |
| UI 复用 | 保留 Android 原生对话/玻璃控件/草稿和 Windows 窗口；已保存旧风格规范与归属许可 |
| Node 自动测试 | 新目录本机 30/30 通过 |
| Python 连接器与版本校验测试 | 本机 38/38 通过；含版本记录缺字段/重复/倒序、版本码倒退、跨组件独立版本及生成文档过期检查 |
| 中继自动测试 | 本机 49/49 通过；版本查询鉴权/启动快照/CLI 无数据库副作用也有合成测试 |
| Windows 说明窗口 | 合成 UI 冒烟通过，说明只读、保留新旧版本，打开/返回不触发断开；没有使用真实身份 |
| 核心 CI | alpha.2 Windows/Linux [run 37614853486](https://github.com/nzkres/codex-desktop-remote/actions/runs/37614853486) 均成功，源码 `8b5d982`；Linux 跳过 Windows 专属测试 |
| Android 单元测试/编译 | alpha.2 新增 2 项解析测试，等待 CI；alpha.1 [run 37608678246](https://github.com/nzkres/codex-desktop-remote/actions/runs/37608678246) 曾有 70 项单测和主/测试 APK 编译通过 |
| Android Lint | alpha.2 待运行；alpha.1 为 0 errors、39 warnings |
| 可下载构件 | 当前已发布 [v0.1.0-alpha.1](https://github.com/nzkres/codex-desktop-remote/releases/tag/v0.1.0-alpha.1)；alpha.2 构件待 CI，未覆盖旧版本 |
| Android 新包安装/真机 | 未安装、未验收；独立包名 `dev.codex.desktopremote.lite`，不会覆盖原 App |
| 公网中继部署 | 未部署新仓库中继，现用服务未变更 |
| 本仓库真实配对/手机验收 | 未进行；没有复制现用身份或运行配置 |
| 安装器/托盘/在线更新 | 未提供，未纳入本轮范围 |

参考基线：旧 Lite `c72d3c5` 曾由用户确认同一个 Desktop 任务的手机→Desktop→手机文字闭环；`2fcf9e2` 的正常断开重连曾通过两轮 Windows 隔离验证。这里只保留来源证据，**不是本仓库 Android 新包、真实配对或公网部署的验收**。

alpha.1 归档 APK：`desktop-remote-lite-0.1.0-alpha.1-debug.apk`，11,719,461 字节，SHA-256 `16fcc64a5df5310369c2e9eed8fb0f4fbf165e031e7b63c3bdc68e877e7aa2e9`。这是 CI 生成的 debug 签名构件，没有稳定覆盖升级承诺；校验值用于核对下载完整性，不代替正式发布签名设计。alpha.1 发布时 Android 产品源码与 `ebdb6e4` 相同，核心产品源码与 `ae9ed7c` 相同；本版会另记构建证据。

下一次人工测试：先按 [TESTING.md](docs/TESTING.md) 运行合成自测，再以独立测试设备、专用 Desktop 任务逐项记录实际配对、手机发送、Desktop 同任务接续、手机显示、正常断开重连。未确认的发送不得重放；旧安装不能靠删除保护标志迁入。
