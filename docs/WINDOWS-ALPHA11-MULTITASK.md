# alpha.11：将当前开发对话加入手机

## 本次范围

用户明确选择“也要能发送消息继续对话”。只追加当前“开发 Windows 连接器客户端”原生对话，原“回复 DESKTOP_REMOTE_READY”任务保留；两条任务各自保存真实目录和完整权限摘要。复用当前手机UI、中继和本机DPAPI身份，不扫描全部对话、不复制线程、不新建执行器。

源码输入 `204426b3c81fa6fbf2169b5ab3009c3ff3216af7`，分支 `codex/phone-current-conversation` 基于 `codex/simple-reconnect-alpha9`。主实现 `src/tasks.js` 复用原 Service/Follower/Receipts/MobileSync；追加任务不继承主任务权限摘要。HTTP按ID核对白名单；项目映射、同编号回执、游标、停止回合均防串线。全局写操作串行；更新检查所有任务，断开先整体停止接纳新操作。诊断导出函数和CLI均聚合，旧单任务验收与alias工具拒绝多任务配置。

## 已验证

- 当前源 Node 112/112，包括14项多任务回归；默认命令早一轮195通过，其中84为忽略目录历史副本。
- Python157项中151通过、6跳过；打包白名单变更后8项发布器测试通过；版本目录检查通过。
- 实际隔离Windows GUI/独立助手提交与健康回退通过。此次专用维护器16项合成测试通过；真实隔离原句柄退出→候选GUI提交、坏候选→完整树清理→needs_review两例通过。没有拿合成任务冒充手机验收。
- GPT-6 Sol medium独立审查产品和本机切换方案，无阻断。最后在诊断前后追加完整proposal/proof复核，真实review也通过。
- 真实只读隔离投影：原测试任务COMPLETED、当前开发对话RUNNING；各24条最近可见条目，可向前分页，当前对话单页56187字节。首次联合读取一项UNKNOWN，随后逐项读取均成功。未发模型输入或写现用配置。
- 签名包中的旧配置OTA探针通过；两任务诊断确认原任务可更新，当前开发对话权限/回执可核对，但返回thread_busy。这是必须等待的真实门禁。

## 固定候选

- Windows `0.1.0-alpha.11`，test渠道、sequence7，既有keyId `cdr-ota-test-20261008-v1`。
- `artifacts/alpha11/release/windows-0.1.0-alpha.11.zip`：139045字节，SHA256 `44af80bf8fcb8ed87856625558a2ea6edd7ddb60b509764bc7e08a5a8d423978`。
- `windows-latest.json` SHA256 `f4410cedf861042ed1efb626e40035f3bdd4ed1aacd095c9b03d3944810a262e`。
- 45个源码文件逐字节匹配Git原始blob；包包含新的任务路由与全任务诊断；使用现用信任与账本6验签通过。私钥仅在本机DPAPI解密后内存使用。
- 本地构建记录 `build-verification.json`；没有发布公网feed或安装到现用目录。

Core CI [38376943673](https://github.com/nzkres/codex-desktop-remote/actions/runs/38376943673) Windows/Linux测试、真实隔离演练和打包均通过；两端上传都因Artifact storage quota失败，整体失败，没有本次CI可下载包。

## 可审查的本机切换

只在忽略目录中准备 `artifacts/alpha11/upgrade.local.py`，不随产品分发、不能跨电脑运行。原真实配置和提案保存在本机；提案必须精确等于旧JSON追加唯一获批additionalTask，不能改原任务、主权限、备用权限、数据目录或二进制路径。

- 助手SHA256 `3ab0680ad5dd16d89d857320318f2b39a4dc5e3713bcca0cace62fed14844f18`。
- 只读 `upgrade-review.local.json` SHA256 `7306e8366dd7b1f893d53fc4cc3bcd8dd9d10c772c3a97fca58f19ecc98d2a83`。审查有效一小时，过期须保留旧证据并重新审查，不改日期绕过。
- 用户批准实际维护后，apply复核完整绑定并固定旧GUI原始句柄，然后才通知正常断开/关闭。最多等600秒，必须同一原句柄exit0，不终止旧PID或Desktop。
- 随后独立助手等待全部任务空闲，最多600秒。只允许明确thread_busy短暂等待；任何权限/身份/包/账本/未知回执变化立即停止，写安装之前持续CAS。由于新增的是本维护对话，需本轮回复结束后它才会空闲。
- 真正切换时持application/update/runtime锁，确认旧控制器自身清洁退出记录和完整旧绑定，严格核验两任务idle后才立围栏、暂存签名包、归档原配置和staged、唯一追加任务，再调用既有独立事务。新版本必须通过原身份/任务数据保全、实际认证通道及两任务探针才能激活。

失败恢复边界：新候选未激活时确认整棵自有进程树退出，再恢复旧版本指针并停needs_review；原配置/旧槽精确保留。不会启动忽略新增任务的alpha.10并声称双任务健康回退。维护者须核验候选树退出、当前配置仍等于获批提案及归档原配置后，才能另行恢复原任务范围；不得用旧数据库覆盖新数据，不得直接清保护。激活结果不明则保留现场，不终止潜在任务或重复发送。

## 当前停点

功能、测试、签名候选和只读维护审查已完成。现用仍alpha.10，新增对话尚未在手机验收；等待用户确认短暂断开并维护升级。未执行apply，不更新Android/relay或公网feed。安装后先在手机查看本对话，再在空闲状态用唯一标记验证同一线程发送/电脑接续。
