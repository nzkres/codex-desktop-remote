# 变更记录

## 0.1.0-alpha.1 — 2026-10-07（开发预览）

- 建立独立仓库，导入 Lite Windows/Desktop 跟随代码、原 Android UI 源码和最小 relay 原型；保留各自的授权与任务归属边界。
- Android 使用独立 application ID，关闭旧 OTA，保留原视觉基线；Windows 加入不连接服务的合成窗口预览与新测试任务的只读配置探针。
- 增加统一合成自测入口与原型/架构/验收文档。新仓库 CI、APK 安装、真实手机/桌面和公网部署结果以 [STATUS.md](STATUS.md) 为准，当前不能从旧仓库或 Lite 的验收推定通过。
- 修正独立 CI 的 Android SDK 安装包选择，并让 Windows 配置测试兼容临时目录的 8.3 路径别名。Windows/Linux 核心 CI 已通过。
- Android 70 项 JVM 单元测试通过，Lint 0 errors / 39 warnings，主 APK 与 instrumentation APK 已编译；私有开发预览提供主 debug APK、校验值与报告。未进行新包安装、模拟器 UI 执行或真实手机联调。
