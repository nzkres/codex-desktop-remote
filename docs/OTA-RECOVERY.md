# OTA 中断与恢复边界

更新失败时先区分下载、验证、切换、激活四个阶段；**保留 `data/`、`state/`、ledger、事务记录与旧版本目录**。不复制旧身份、不删 `runtimeNeedsReview` 或 `transaction.json`、不按进程名/PID 批量结束服务、不用旧数据库备份覆盖可能已有新写入的数据。恢复前先读 [STATUS](../STATUS.md) 的实际部署层级；本仓库尚无生产 OTA 现场。

| 现场 | 安全含义与下一步 |
| --- | --- |
| 更新源、公钥、签名、兼容、空间或下载失败 | 没有获得可安装构件；保留原版，核对独立 HTTPS 源和发行材料。不要关闭校验或改 trust 绕过。 |
| Android 已唤起系统安装 | 系统确认前不是完成。取消后原版本仍在，可保留待安装回执重试；重开后实际 version/versionCode、当前 APK 哈希和 ledger 都匹配，且重新获得选定连接器的实时任务证据，才可显示已核验。无法证明时显示任务待核对。 |
| Windows 候选验证失败且自有 Job 树已证明退出 | 事务可指回已验证旧版本；本机 `data/` 不回滚。新树未证实退出、身份摘要变化或激活回执不明时停在 `needs_review`，不能抢启旧版。 |
| Relay 维护或候选失败 | 先看受管宿主门禁、原 OwnedChild/cgroup 整树、认证和设备库逻辑摘要。未知操作、清理失败或激活歧义时保持关闭准入并人工复核；不能直接启动另一份服务。 |

维护者在**源码根目录**使用同一 Python 3.12 查看受管目录；`recover` 只把未完成日志标记为 `needs_review`，不是修复、重放或重启命令：

```powershell
$PythonExe = '<PYTHON_3_12_EXE>'
$Home = '<EXISTING_MANAGED_HOME>'
& $PythonExe -B -m updates.cli --home $Home status
& $PythonExe -B -m updates.cli --home $Home recover
```

Windows `status` 可在窗口退出后读，`apply` 必须走 GUI 的独立助手。Linux relay 的 `status/check/stage/apply/stop` 发往**已运行、同所有者**的受管宿主 socket；宿主或 cgroup 归属丢失时失败闭锁。普通用户不要把 `recover` 当作“继续安装”，也不要对现用旧中继执行受管命令。

复核材料应只包含脱敏的组件/版本、阶段码、构件哈希、进程树和数据摘要是否相同、实际版本与健康结果；不要上传身份文件、账号库、任务内容、完整配置、私钥或回执原文。明确修复方案应由现场所有者审查后再执行。
