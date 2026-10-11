# Desktop 26.1007 重连审查

2026-10-11：现用连接器仍为已配对 alpha.7，上次 OTA 为 committed/success；当前 runtimeNeedsReview=true、transportCrashContained=true。保存的 state=connected 不代表当前在线。旧26.930包文件已不存在，当前 Desktop 为26.1007.2314.0。

## 候选兼容证据

本机 AppxManifest 版本与两个文件 SHA-256：

| 文件 | SHA-256 |
| --- | --- |
| app/resources/app.asar | c5fdef328cfa2ad9095786f9c3a7aaa447e68834d31299d6ef2592da02029038 |
| app/resources/codex.exe | d13914ced6c7af174d8d638db938284231227312104b06a25e9608c632a7daec |

asar 的 src-BJv5Mk7n.js 协议表及处理包含 owner-discovery=1、start-turn=2、interrupt-turn=4、load-complete-history=1、stream-state=11、following=1，仍检查 supportsUntrustedAppInput。候选保留原26.930双指纹，新增精确26.1007双指纹；不接受版本区间或交叉指纹，不修改 Desktop 内部文件。

候选只读 IPC 已读取原专用任务和 DESKTOP_REMOTE_READY 标记，模型输入0；但原权限摘要不匹配，update-status仍拒绝 permissions_changed。当前观察为 on-request/auto_review、workspaceWrite、networkAccess=false，写入根与runtimeWorkspaceRoots均只含用户 Documents/Codex 目录，tmp排除开关均true。没有批准或写入新的权限基线。用户已打开原任务，等待其完成一次无工具标记回复后重新核对。

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
