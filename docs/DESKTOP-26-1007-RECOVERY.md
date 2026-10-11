# Desktop 26.1007 重连审查

## 2026-10-12 实机证据增量与简化范围

用户要求只修当前连接、保留原配对/原任务，停止新增通用恢复框架；未接入的recovery_evidence模块和测试已移除。仅保留精确版本识别、分项只读诊断以及本机一次性维护准备。用户允许准备完成后通知其手动重启；当前未要求其重启、未切换安装。

- 现用alpha.7包内清单验证通过，原更新事务committed/success；DPAPI身份存在，两个任务回执均accepted，核对前后安装/信任/配置/身份/账本/事务原字节一致。忽略目录`artifacts/desktop1007/install-audit-20261012.json`记录脱敏结果。
- 旧配置所指Desktop目录不存在；新版26.1007.2314.0双指纹通过。连接器GUI进程存在，65250无监听。进程枚举仅作现状证据，不证明旧树退出。
- Windows记录最近10月11日01:02:33休眠、14:25:13唤醒（北京时间）；最近完整启动为9月22日。缺少连接器当时退出回执，不能将睡眠事件直接认定为断线根因。
- 首次分项实测sameTask/idle/receipts通过、permissions_changed。随后一次性审查发现新鲜快照变为activePermissionProfile=null、runtimeWorkspaceRoots仅1项，因此以review_scope_changed停止，未写现用配置。历史中存在完成标记不等于当前拥有明确执行权限；已请用户在原任务完成一次REMOTE_RECONNECT_READY后再次只读核对。
- 新增诊断合成测试后Node72通过；移除未接入模块前Python157项151通过/6跳过，最终变更后须重跑。原alpha.8 ZIP为先前候选，不冒充最新构建或已安装版本。

本次维护只允许改连接器对已明确选定原任务的记录与Desktop包路径，不修改Desktop本身授权、网络策略、任务目录或手机身份；任何实时状态变化都停止。维护脚本和真实状态留在本机忽略目录，不作为通用产品功能发布。

2026-10-11：现用连接器仍为已配对 alpha.7，上次 OTA 为 committed/success；当前 runtimeNeedsReview=true、transportCrashContained=true。保存的 state=connected 不代表当前在线。旧26.930包文件已不存在，当前 Desktop 为26.1007.2314.0。

## 候选兼容证据

本机 AppxManifest 版本与两个文件 SHA-256：

| 文件 | SHA-256 |
| --- | --- |
| app/resources/app.asar | c5fdef328cfa2ad9095786f9c3a7aaa447e68834d31299d6ef2592da02029038 |
| app/resources/codex.exe | d13914ced6c7af174d8d638db938284231227312104b06a25e9608c632a7daec |

asar 的 src-BJv5Mk7n.js 协议表及处理包含 owner-discovery=1、start-turn=2、interrupt-turn=4、load-complete-history=1、stream-state=11、following=1，仍检查 supportsUntrustedAppInput。候选保留原26.930双指纹，新增精确26.1007双指纹；不接受版本区间或交叉指纹，不修改 Desktop 内部文件。

候选只读 IPC 已读取原专用任务，且用户执行后的 `DESKTOP_1007_READY` 已确认 completed，诊断模型输入0；原权限摘要仍不匹配，update-status拒绝 `permissions_changed`。没有批准或写入新的权限基线，不需要重发标记。

执行后快照为 on-request/auto_review、workspaceWrite、networkAccess=false；sandboxPolicy写入根1项，runtimeWorkspaceRoots恢复为3项，两个tmp排除开关均true。原任务10条turn_context（包含本次完成的回合）记录的sandbox策略写入根2项、两个tmp排除开关均false。仅在内存将快照的这三项换成各回合记录值，现有permissionsMatch均匹配旧绑定；没有将替换结果用于实际授权或写回。该实验定位了差异，**不证明历史记录可以代表下一次执行权限**。

本机asar只读源码核对显示具名`:workspace` profile与sandboxPolicy同时参与权限表达；部分目录计算在profile为`:workspace`时另行包含临时目录。由此怀疑是权限投影变化，但尚不足以证明完整有效权限等价。保留拒绝逻辑，不能忽略tmp字段、采用历史权限覆盖实时权限或直接重算保存摘要。

## CI 与产物状态

PR检查 [38124021866](https://github.com/nzkres/codex-desktop-remote/actions/runs/38124021866) 的Windows/Linux测试、打包及相应合成升级/回退步骤通过；最终actions/upload-artifact失败，明确原因是GitHub Actions存储配额已满。对应push检查38124018128也失败。**总体CI失败，没有本轮可下载CI产物**；不能把构建步骤通过写成CI成功。未删除既有产物、改配额或绕过上传失败。本地未签名ZIP仅用于候选检查。

## 只读诊断命令

在已审查仓库运行（维护工具，不是恢复按钮）：

```powershell
& '<PINNED_NODE_EXE>' scripts/diagnose_desktop.mjs '<INSTALL_HOME>\data\fixture.local.json' --desktop-root '<NEW_DESKTOP_PACKAGE_ROOT>'
```

只在内存覆盖desktopRoot，不写安装配置、不读取配对令牌、不改变权限、不开HTTP服务、不发送/停止任务。输出固定阶段/错误码及新鲜任务检查。taskReady=true也始终带reconnectAuthorized=false；不证明设备在线、手机完成或旧进程树退出。

## 恢复前仍需解决

1. 普通OTA旧版本前检无法验证已消失的Desktop文件；不得修改已签槽或清guard使其通过。
2. transportCrashContained是启动前记录，不是adapter与FRP两个原Job已空的事后证明。原句柄丢失时，PID消失、端口空闲、runtime.lock可取均不足。原alpha5→6专用维护器不适用于此次故障。
3. 冷恢复还须独立实现并验证：持久启动屏障、审查绑定完整身份/配置/回执/可信槽、原树退出证据；没有原Job证据时须设计受审查的系统重启边界与启动前屏障，不能追溯伪造证明。持有应用锁和运行锁、完整状态CAS，意外变化停止。
4. 权限若确实变化，先核对并确认具体授权范围；不以改摘要代替确认。活跃任务、待审批及未知回执阻断，绝不重放未知消息。
5. 候选启动、身份权限与任务健康全部通过后才记录恢复。失败保留现场，不回写旧数据库或把不兼容旧版称为健康回退。

本轮只准备兼容候选和诊断。**未执行冷恢复、重启、维护安装、签名或公网发布；手机重连未验收。** 先完成恢复实现/隔离验证与具体切换清单，再确认实际切换。
