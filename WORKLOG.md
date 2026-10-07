# 工作记录

## 2026-10-07 — 独立仓库与首个自测版本

- 用户要求新仓库保存原型说明、关系图并复用 UI，作为自己测试和后续版本迭代的起点。新仓库命名 `codex-desktop-remote`，私有，默认分支 main；不覆盖旧工作区。
- Windows/Node 来源为独立 Lite 提交 `2fcf9e22ba28923cbbd36afd56ac34d0d0ee7652`；Android 与 relay 的已提交来源为旧仓库 `f8350e400cf6e7420d8283e03eb6efa37104ca79`。只迁源码、测试与必要许可，未迁真实身份、数据库、安装产物、旧签名私钥或本机配置。
- GPT-6 Sol medium 分担 Android 独立构建迁入、中继最小提取和文档；主代理核对边界、整合、测试和发布证据。
- 保留根 `src/`、`windows/` 避免为目录整理改动核心接口；新增 `android-native/` 与最小 `relay/`。未迁旧 app-server、Gateway、安装器、升级事务或大规模历史文档。
- 新版本统一为 `0.1.0-alpha.1`。Android 使用独立应用ID与默认 debug 签名，禁止复用旧 OTA；Windows 服务器默认留空，由用户填写。现用手机 APP/旧连接仍保持原状。
- 新增原型/架构图、测试/迭代指南、合成窗口演示、SelfTest.ps1 和只读测试配置准备脚本。原风格规范保留为 UI-BASELINE，原型功能支持与界面控件是否存在分别说明。
- 本机命令：`node --test` → 30 通过；`python -B -m unittest discover -s test -p test_*.py -q` → 28 通过；`python -B -m unittest discover -s relay -p test_*.py -q` → 45 通过。SelfTest.ps1 组合入口也已跑通；命令均在新仓库执行，未连接现用公网服务。
- GitHub CI、APK 下载以及后续人工验证结果继续追加；当前证据以 STATUS.md 为准，不把旧 APK 或旧部署当成本仓库的新验收。
