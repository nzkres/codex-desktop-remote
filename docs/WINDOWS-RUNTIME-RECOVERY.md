# Windows 异常运行核验恢复（alpha.16）

用户明确启动“核验并恢复”或维护 CLI 才执行，不自动接管。保留现有 DPAPI 身份、设备令牌、任务权限与所有回执；不重配、不重发、不停止 Desktop 任务。

1. 持有更新锁、恢复锁和原身份的独占运行锁。完整验证现装版本槽、配置路径与两个旧 Job/worker 实现文件；旧周期必须保存 `transportCrashContained=true`。
2. 完整读取当前 Windows 进程清单，拒绝仍属于此安装/任务目录的 Node、FRP 或 worker。空命令行只在内核句柄证明进程已退出或 PID 不存在时忽略；查询失败继续阻断。无关服务不停止。保留的旧 GUI 只允许在运行锁持有期间保持空闲。
3. 独占原端口，核验固定 Desktop 清单、asar/CLI 指纹。对两条原配置锚点及手机持久化接入记录中的任务读取新快照，核对任务、权限、已接受发送回执及无未知停止操作。正在运行的任务允许继续执行；不能把此证明当作安装手机更新的空闲证明。
4. 再次核对进程、完整身份、配置、当前槽及任务数据摘要。先落盘 prepared 记录，只更新已经核验的 Desktop 安装路径，再以完整身份 CAS 仅移除 `runtimeNeedsReview`，保留其余字段。前后数据相同才记录 committed。
5. 恢复自身中断时，正常启动继续阻断。再次显式恢复只接受记录匹配的当前部分提交状态，重新执行全部实时核验；不恢复任何旧身份/回执快照。

受管安装窗口点击“核验并恢复”，成功后点击连接。CLI 默认只核验，不写配置/身份，不打开窗口：

```powershell
python -B windows/runtime_recovery.py --home '<安装目录>' --desktop-root '<已验证的Desktop目录>'
```

加 `--apply` 才提交。确需加载未打开任务时，维护者可逐条列出 `--open-task <UUID>`；最多八条，只能来自原锚点/接入记录，不会批量打开标题目录。默认 UI 不自动打开任务。`Start.pyw --connect` 是显式启动后连接原身份的入口。

旧周期的 Job 为匿名对象，恢复没有重建历史 Job 句柄。依据是已核验的 kill-on-close 实现、保存的崩溃收容标志、独占运行锁、当前完整进程/端口核验与新鲜任务证据。任一项不足均保持阻断，不声称“端口空闲就证明旧树安全退出”。

本次核验 Desktop 26.1007.9446.0：asar SHA256 `356c13dc715816436fa1cf31e9f77863f62ea7b7cacc465b397313246eb29325`，CLI SHA256 `e98c516f1c8935789aabe9122ee7e05ec4c3efd1ba6acf179242e031c495b99e`。原生入口 SHA256 `ee785aec9ff3058afb01069a8b455fb17e46f3c33611a867efd3db37de1f05f3`，Authenticode Valid，发布者 OpenAI OpCo, LLC。包内协议表 owner discovery 1、state changed 11、submit user input 1；未以此开放新的控制 API。
