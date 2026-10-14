# Windows alpha.12：标题目录、按需历史与维护恢复

## 范围和体验

用户已批准这台已配对电脑的全部本机 Desktop 对话供手机查看和接续，新对话无需逐条添加。列表只取原生 SQLite 目录的标题/ID/本机目录/时间；按需 follower 读取同一个原任务。发送使用 inheritThreadSettings，不创建另一套执行器，不改 Desktop 权限。

真实空闲且确实没有历史才显示暂无记录。执行端不可用、历史未取完、权限未核验、待审批均显示各自原因；既有 Android 重试入口继续使用。列表出现不代表此刻已获得历史或执行能力。

标题刷新本身不建立正在运行的任务绑定。原安装两条任务保留为升级基线；手机实际触及的非空闲任务、发送、停止有持久交接记录。未知发送/停止不会重放；补历史失败、审批错误和断开竞争不会抹掉记录。升级期间检查基线和这些实际交接任务；不要求每条从未打开的历史标题都提供实时空闲证明，不停止 Desktop。

旧安装缺省 selected；本次只为用户明确批准的安装切换 taskScope=all-local。原任务ID、旧权限摘要、额外任务记录和配对身份均保留。远端路径、子代理及内部审查不作为本机任务；历史未标 originator 的 vscode 记录只是候选，点开必须通过真实本机 owner/ID/目录校验。

## 当前交付状态

- 已实现、已本地测试、已固定源码、已本机签名验签。
- [CI38420879205](https://github.com/nzkres/codex-desktop-remote/actions/runs/38420879205) Windows/Linux测试、隔离演练、打包上传全部成功，临时源码产物可下载。
- 2026-10-14已完成实际签名维护安装并激活alpha.12窗口；独立签名、版本槽、双事务日志及身份/任务保全审计通过，获批配置仅增加all-local。
- 首次候选激活前健康拒绝已保留；隔离传输复验通过，独立受控续接提交成功。首次具体诱因仍未证实。
- 手机新标题自动显示及点进历史读取尚待用户验收。详见STATUS.md最新增量及artifacts/alpha12/installed-alpha12-audit.json。
- Android、relay和公网更新源本次未更新。
- 源码输入：`a3b85a9e294db22b8bd40da524729d2437ffb4e9`；草稿PR [#8](https://github.com/nzkres/codex-desktop-remote/pull/8)，堆叠在PR #7上，未合并。
- Node 当前源137/137；默认发现221/221，其中84条属于忽略目录历史副本。
- Python161项，155通过、6平台跳过；版本目录检查通过。
- 通用真实隔离Windows GUI/独立助手升级提交和回退通过。
- 本次恢复助手18项合成测试通过；真实隔离GUI健康候选committed、故障候选清理后needs_review且保留旧指针通过。故障分支计数2表示一次新候选启动和一次被拒绝的旧槽启动尝试；旧GUI没有实际启动。

## 签名候选（本机，不是已发布feed）

文件：`artifacts/alpha12/release/windows-0.1.0-alpha.12.zip`，147470字节，sequence8，48个文件逐一匹配Git blob。

SHA-256：`219fad777ae3ef58e145a6dc6563f16181fff665ac608ecdff804b0f0b753188`。
元数据：`artifacts/alpha12/release/windows-latest.json`，SHA-256 `dcbdcb57284314aec6cb6da705f59219ee31552850811a21085c2e254d690c56`。

用既有DPAPI发布者签名，本机现用trust与ledger7验签通过。源码CI产物是未签名临时包，不能代替此安装候选。CI状态看对应运行，不把测试通过等同安装完成。

## “前次连接未能确认安全退出”是什么意思

连接开始前先保留未清理标记；只有消息交接明确、自己的adapter/FRP进程树已通过原句柄和Job确认退出、完整状态未变化，正常断开才解除。窗口退出、PID消失或端口空闲不能独自证明所有后代已退出。标记存在本身也不能证明消息丢失或仍有残留进程。

本次只读证据：alpha.11已提交，身份已配对；5条已接受回执、0条未知发送；runtime锁可取得，窗口尚在。状态含runtimeNeedsReview与transportCrashContained，但缺失上次匿名Job原始退出凭据。后者布尔值不是进程树退出证明。准确的异常退出诱因未从现有证据还原，不能归咎网络、Desktop升级或用户关窗。

维护者负责检查：未确认操作、原进程归属/树退出证据、独占锁、完整身份与任务数据CAS、签名包和配置。用户无需查PID、删目录或重新配对。本次原始退出证明不可恢复，因此采用完整Windows重启建立跨启动退出边界。

## 本机一次性恢复步骤

此助手不属于通用OTA恢复功能，不可复制到另一台电脑运行。

1. 正常关闭alpha.11窗口；不强杀、不结束Desktop。取得application/update/runtime三把锁后才能prepare。
2. `artifacts/alpha12/repair.local.py prepare`：验签、核对批准scope/旧配置hash，记录完整解密身份摘要、受保护文件hash、启动时间及助手/依赖指纹；只把主事务改成needs_review围栏，保留runtimeNeedsReview，真实配置不变。当前开发回合可继续，prepare不声称任务空闲。
3. 确认prepare成功后，用户保存其他工作并从Windows菜单选择“重启”；休眠和关闭窗口不算。
4. 重启后保持连接器关闭，恢复Desktop两条原对话。维护者复核相同安装、严格更新的boot时间、三把锁、全部CAS和签名；候选探针确认原两任务及持久交接任务空闲后才执行resume。当前维护对话在运行时不能切换，必须等其结束后再执行。
5. `repair.local.py resume`只归档旧配置/旧staged的精确字节，增加taskScope=all-local，然后在重启证明和CAS下解除旧guard，使用现有事务隔离启动、验证原身份/数据及真实通道，再激活GUI。
6. 新候选不健康时先确认其自有Job树退出，再恢复旧指针并停needs_review；旧alpha.11无法完整理解all-local，因此不以新配置启动旧版，不声称自动回退成功。不用旧数据库备份覆盖任何新数据。

批准和review在仓库外 `%LOCALAPPDATA%/CodexDesktopRemoteMaintenance/alpha12`。批准文件仅含范围与hash；review含旧事务/指针及摘要。resume探针短暂使用该目录下的私密临时配置，正常结束会删除；异常退出残留也属于本机私密运行数据，禁止上传。原始身份、日志和配置不入Git。

助手SHA-256 `4316f8d8c116678d4a8139154d3fae2e1bf55fadc1b765bed7ab5b4d47aeffea`；合成测试 `e1f1e634d7cff16570dfa5438b4308081d6e70c37e77c9fbe98d9743c20f2ac5`；GUI演练 `5dee7c8e329ffa653e03461fb0721b440f0b8c26e2f1864ea1092e2ddbc80d0b`。固定后若任一依赖/现场变化，停止并重新审查；不自动改审批记录。未安装前可以重新制备，但不得复用已经部分执行的事务。

## 升级后手机验收

1. alpha.12窗口点击连接，手机仍使用原电脑，不重新配对。
2. 手机刷新任务列表：原对话保留，多条本机标题出现；点进已有任务，确认原历史可读。
3. 在Desktop新建专用测试对话并发送一次“请只回复 AUTO_CONVERSATION_READY，不调用工具、不读写文件。”。
4. 手机刷新列表，新标题应自动出现；点开后应看到同一条回复，无需添加任务。
5. 选一条暂时不可读的历史，提示应区分执行端未就绪和空历史；电脑恢复该原对话后手机重试，不生成替代任务。
6. 如继续验证发送，每条只发一次；结果不明先查看现象，不重复发送。最后分别记录历史读取、新标题出现及双向接续的实机结果。

## 可重复的开发验证

```powershell
node --test
python -B -m unittest discover -s test -p "test_*.py"
python -B scripts/check_versions.py
python -B scripts/windows_ota_smoke.py
python -B artifacts/alpha12/test-repair.local.py
python -B artifacts/alpha12/repair-gui-smoke.local.py
```

最后两项只使用合成安装。维护prepare/resume不是测试命令，须先核对本机审查记录和当前阶段。详见STATUS.md最新增量。
