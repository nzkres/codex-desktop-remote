# 中文注释阅读入口

本轮范围由用户确认为：Windows 连接器、Desktop 任务接口、OTA 核心源码。
38 个文件只新增中文注释，没有修改原有可执行行。Python 文件另核对 AST 和非注释 token 一致。
Android/中继业务、发布签名工具、历史专项维修器和测试源码未纳入本轮注释。

## 从三个入口开始

1. [windows/controller.py](../windows/controller.py)：connect → _runtime → LiteRuntime.reconcile → disconnect。
2. [src/service.js](../src/service.js)：target → read/send → follower → receipts。
3. [updates/engine.py](../updates/engine.py)：check → stage → Transaction.apply。

注释解释当前实现及限制，不把自动重连建议写成已完成能力。probe.mjs 带 --send-literal 会发送真实消息；学习用 demo.py 和隔离测试菜单。

## 完整覆盖清单

| 文件 | 注释内容 |
| --- | --- |
| [scripts/diagnose_desktop.mjs](../scripts/diagnose_desktop.mjs) | 入口/函数职责、关键状态和失败边界 |
| [scripts/probe.mjs](../scripts/probe.mjs) | 入口/函数职责、关键状态和失败边界 |
| [scripts/update_probe.mjs](../scripts/update_probe.mjs) | 入口/函数职责、关键状态和失败边界 |
| [src/compatibility.js](../src/compatibility.js) | 入口/函数职责、关键状态和失败边界 |
| [src/follower.js](../src/follower.js) | 入口/函数职责、关键状态和失败边界 |
| [src/main.js](../src/main.js) | 入口/函数职责、关键状态和失败边界 |
| [src/receipts.js](../src/receipts.js) | 入口/函数职责、关键状态和失败边界 |
| [src/service.js](../src/service.js) | 入口/函数职责、关键状态和失败边界 |
| [src/vendor/conversation-timing.js](../src/vendor/conversation-timing.js) | 入口/函数职责、关键状态和失败边界 |
| [src/vendor/desktop-ipc.js](../src/vendor/desktop-ipc.js) | 入口/函数职责、关键状态和失败边界 |
| [src/vendor/desktop-state.js](../src/vendor/desktop-state.js) | 入口/函数职责、关键状态和失败边界 |
| [src/vendor/mobile-sync.js](../src/vendor/mobile-sync.js) | 入口/函数职责、关键状态和失败边界 |
| [src/version.js](../src/version.js) | 入口/函数职责、关键状态和失败边界 |
| [updates/__init__.py](../updates/__init__.py) | 入口/函数职责、关键状态和失败边界 |
| [updates/authorization.py](../updates/authorization.py) | 入口/函数职责、关键状态和失败边界 |
| [updates/bootstrap.py](../updates/bootstrap.py) | 入口/函数职责、关键状态和失败边界 |
| [updates/child_gate.py](../updates/child_gate.py) | 入口/函数职责、关键状态和失败边界 |
| [updates/cli.py](../updates/cli.py) | 入口/函数职责、关键状态和失败边界 |
| [updates/credentials.py](../updates/credentials.py) | 入口/函数职责、关键状态和失败边界 |
| [updates/engine.py](../updates/engine.py) | 入口/函数职责、关键状态和失败边界 |
| [updates/launch.py](../updates/launch.py) | 入口/函数职责、关键状态和失败边界 |
| [updates/processes.py](../updates/processes.py) | 入口/函数职责、关键状态和失败边界 |
| [updates/security.py](../updates/security.py) | 入口/函数职责、关键状态和失败边界 |
| [updates/storage.py](../updates/storage.py) | 入口/函数职责、关键状态和失败边界 |
| [windows/controller.py](../windows/controller.py) | 入口/函数职责、关键状态和失败边界 |
| [windows/demo.py](../windows/demo.py) | 入口/函数职责、关键状态和失败边界 |
| [windows/gui.py](../windows/gui.py) | 入口/函数职责、关键状态和失败边界 |
| [windows/ota_entry.py](../windows/ota_entry.py) | 入口/函数职责、关键状态和失败边界 |
| [windows/release_notes.py](../windows/release_notes.py) | 入口/函数职责、关键状态和失败边界 |
| [windows/update_driver.py](../windows/update_driver.py) | 入口/函数职责、关键状态和失败边界 |
| [windows/update_helper.py](../windows/update_helper.py) | 入口/函数职责、关键状态和失败边界 |
| [windows/update_ui.py](../windows/update_ui.py) | 入口/函数职责、关键状态和失败边界 |
| [windows/vendor/connection_settings.py](../windows/vendor/connection_settings.py) | 入口/函数职责、关键状态和失败边界 |
| [windows/vendor/connector_core.py](../windows/vendor/connector_core.py) | 入口/函数职责、关键状态和失败边界 |
| [windows/vendor/frp_config.py](../windows/vendor/frp_config.py) | 入口/函数职责、关键状态和失败边界 |
| [windows/vendor/runtime_core.py](../windows/vendor/runtime_core.py) | 入口/函数职责、关键状态和失败边界 |
| [windows/vendor/transport_worker.py](../windows/vendor/transport_worker.py) | 入口/函数职责、关键状态和失败边界 |
| [windows/vendor/windows_job.py](../windows/vendor/windows_job.py) | 入口/函数职责、关键状态和失败边界 |

## 验证与安装边界

最终测试：Node 72/72；Python 152项中146通过、6跳过；版本校验通过。

本轮只修改开发仓库，不更新现用 alpha.8 安装或私有源。注释会改变源码文件哈希，发布时仍需重新构建签名，不能直接覆盖现用签名槽。
验证报告与测试日志位于本机忽略目录 artifacts/learning/all-comment-verification.json、comments-node.log、comments-python.log、comments-versions.log。
