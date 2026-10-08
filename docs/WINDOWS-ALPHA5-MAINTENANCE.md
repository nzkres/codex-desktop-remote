# Windows alpha.5→alpha.6 审核模式别名维护切换

当前运行的 alpha.5 会把 `guardian_subagent` 与 `auto_review` 的名称变化误判为权限变化，导致手机同步和旧版更新预检返回 `permissions_changed`。alpha.6 源码仅兼容这两个审核模式别名，原权限摘要、配置、身份和发送回执不改；真实 reviewer、目录、网络、审批或 profile 变化仍应阻断。实际发行、签名、安装和手机显示验收以 [STATUS](../STATUS.md) 为准，不能从本页或源码推定已完成。

`updates/windows_maintenance.py` 是**仓库中的维护者工具**，不在已安装的 alpha.5 或 Windows 发行 ZIP 内，不是通用安装入口。它只接受 test 渠道、当前 alpha.5 到精确 alpha.6 的已签元数据与对应本地 ZIP；普通旧版 GUI OTA 仍可能被旧权限比较阻断。实际停用当前连接须另经用户确认，先保留正在运行的窗口、现用身份和所有原始数据，不能通过改 `permissionsHash`、清保护标志、复制 DPAPI 文件或替换旧签名槽绕过。

## 准备与审查

在**受审核的仓库 checkout**和当前用户的 Windows Python 3.12 环境运行工具；先独立确认包及元数据来源、签名公钥、版本/渠道、清单和有效期。CI 未签的 ZIP、GitHub artifact 或同名文件不能单独作为可安装候选。`$ConnectorHome` 是已核实的**物理**受管安装目录，不是 AppX 逻辑别名；`$GuiPid` 必须是这套安装当前 GUI 的原始 PID。工具会再固定原始进程句柄，并核对同一 Windows 用户、会话、Python 可执行文件以及 `ota_entry.py --home` 命令行。示例变量均为占位符：

```powershell
$PythonExe = '<PYTHON_3_12_EXE>'
$RepoRoot = '<REVIEWED_REPOSITORY_ROOT>'
$ConnectorHome = '<EXISTING_PHYSICAL_ALPHA5_MANAGED_HOME>'
$GuiPid = [int](Read-Host '输入已独立核实的原 GUI PID')
$Metadata = '<LOCAL_SIGNED_WINDOWS_ALPHA6_LATEST_JSON>'
$Package = '<LOCAL_WINDOWS_ALPHA6_ZIP_MATCHING_METADATA>'
$Review = '<NEW_REVIEW_JSON_OUTSIDE_MANAGED_HOME>'
Set-Location -LiteralPath $RepoRoot
& $PythonExe -B -m updates.windows_maintenance review --home $ConnectorHome --gui-pid $GuiPid --metadata $Metadata --package $Package --output $Review
```

`review` 不暂存候选、关闭窗口或切换版本，但会使用安装锁并以独占创建方式写 `$Review`。它核对已连接身份、完整稳定身份与任务数据文件、配置、安装清单、trust、ledger、启动器、当前版本，以及候选签名/ZIP；在临时目录运行仅针对审核模式别名的候选只读探针，要求任务空闲、权限已验证且回执确定。结果给出 `reviewHash` 和 `expiresAt`；审查仅有效 **30 分钟**，文件放在受控的安装目录外，不提交仓库或发送给他人。任何审查失败都停下核对原因，不放宽权限比较。

## 经确认后的切换

仅在候选已受控签发、审查结果可复核且用户同意让该测试连接正常退出时，使用同一元数据和 ZIP：

```powershell
$ReviewHash = '<EXACT_REVIEW_HASH_FROM_REVIEW_OUTPUT>'
& $PythonExe -B -m updates.windows_maintenance apply --review $Review --expect-review $ReviewHash --metadata $Metadata --package $Package
```

`apply` 再查 30 分钟期限、审查哈希、当前完整绑定和同一原 GUI 句柄/用户/会话，重新验证签名包并做候选别名探针。它将候选写入独立槽和账本、记录事务，**先持久化旧启动器拒绝再次冷启动的主屏障**，然后报告 `waitingNormalExit`，最多等待原 GUI **120 秒正常退出**；工具不会替用户关闭窗口、停止 Desktop 或按 PID 杀进程。维护者应按窗口正常断开并退出，保持原任务与数据；不要在等待时重新启动旧窗口。

原句柄确认正常退出后，工具在独占应用/运行锁下再次核对身份与任务数据、候选空闲/权限/回执，并用事务 CAS 切换当前指针。候选先隔离启动和验证健康，再开放服务；只有记录 `phase=committed`、`success=true` 且实际运行版本为 alpha.6，才可开始后续手机同步验收。连接或探针成功本身不等于用户手机已看到原 Desktop 两条回复。

## 失败与恢复边界

审查过期、GUI/身份/数据变化、任务不空闲、签名或候选验证失败，都应停止切换并保留证据。设置主启动屏障后的异常、进程退出不明、候选树清理或激活结果不明会留下 `needs_review`；不要删除 `state/transaction.json`、`state/maintenance-transaction.json`、`state/maintenance.json`、账本、候选槽或任务数据，也不要人工清屏障来重试。旧 alpha.5 本身仍有审核别名探针缺陷：即使候选失败后指针恢复到旧槽，也**不能据此宣称旧版已健康回退或重新启动它**。中断事务只供人工联合核对两份日志和原始进程/数据状态，不自动恢复或重放请求。一般恢复原则见 [OTA 恢复](OTA-RECOVERY.md)。

完成后分开记录：签名包与元数据、审查哈希/有效期、原 GUI 正常退出、主/次事务阶段、当前槽与实际运行版本、身份/配置/回执摘要保持、同一原生任务的手机显示。只保存脱敏结论，不把身份、token、原始任务或审查文件入库。
