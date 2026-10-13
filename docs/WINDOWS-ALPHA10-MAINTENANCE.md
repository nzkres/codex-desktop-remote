# alpha.10：原任务权限切换导致手机读取中断的维护记录

## 已确认的故障

alpha.9 已实际安装，手机初次读取通过。但同一任务的 PHONE_ALPHA9_OK 和 DESKTOP_ALPHA9_CONTINUE_OK 均在 Desktop 完成后，本机认证 health 为200，mobile-sync 返回409/permissions_changed。任务空闲、回执确定；当前完整权限摘要等于用户此前明确批准的第二套设置。重启后的原配置对应第一套设置。不能把两套范围擅自归类为同一个别名，也不能继续要求用户反复重启或发相同消息。

## 最小产品修复与证据

alpha.10 在现有 permissionsHash 外支持唯一可选 approvedAlternatePermissionsHash，两者须为不同的完整 SHA-256 摘要。不会从任务自动学习新权限。服务读取、发送、停止、正常 OTA 和诊断共用校验；历史 alias-only 维护入口存在备用摘要时提前拒绝。原任务、目录、忙闲、回执和既有审批者别名检查保留。

源码输入：11bbbb3a8e98ae041306af8f939a1858940ca696。源 Node 98/98；Python157项中151通过、6平台跳过；版本目录与生成历史检查通过。14项新增回归覆盖两套设置交替读写/OTA/诊断、同步游标显示 Desktop 新回复、第三种权限与网络/目录变化拒绝、非法/重复摘要、未知回执和历史加载竞态。候选在内存使用两份已批准记录只读投影原任务，两条已有回复都能输出，未改现用配置或发送消息。

## 固定签名候选

- 文件：artifacts/alpha10/release/windows-0.1.0-alpha.10.zip，133579字节。
- SHA256：0a2660f390e5a3289630f8b7e4e209a9949e15386a4f57983c8eff017afbaed3。
- windows-latest.json SHA256：a1ed90dde948b73562033a7d0cb2ba99bfe64b3c15e920ffe9bf12096a03b748。
- test渠道、sequence6、现有keyId cdr-ota-test-20261008-v1；43个包内文件逐字节匹配Git blob，并通过现用信任与ledger5只读验签。
- 包未发布到公网feed；本机维护安装不作为在线OTA验收。

## 本次正常退出交接

用户已明确批准alpha.10及追加既有获批第二摘要，手机/服务器不在本次切换范围。独立助手先固定旧GUI句柄、创建时间、映像、用户/会话及完整启动路径，再通知用户正常断开并关闭窗口。只等待同一原始句柄exit0，不终止旧PID、不关闭Desktop、不要求再次重启。

必须由现用GUI自身核验回执与两棵自有进程树退出，并正常移除本轮runtimeNeedsReview；维护器不含清除该标志的能力。退出后同时持有application/update/runtime锁，再核对原完整稳定身份、任务数据、配置、旧staged、历次维护记录和签名。只追加已批准备用摘要，原permissionsHash保持；原配置与旧staged精确字节归档。先写启动围栏再暂存切换，独立事务验证真实候选启动/认证通道/原任务及数据保全后才激活窗口。

候选失败必须先清理整棵自有树，再恢复旧指针并停在needs_review；由于alpha.9已确认对第二套权限存在读取故障，不自动启动它或声称健康回退。若激活结果不明，保留候选和现场，不终止或重放潜在工作。

只在本机忽略目录使用 artifacts/alpha10/upgrade.local.py；不随产品分发，也不能跨电脑运行。原review保留不用，最终文件为 upgrade-review-v2.local.json。

- 操作器 SHA256：6155b4838fae1089e3e29375fbab7c92f32589cb3de2f208b184db660194026c。
- v2审查 SHA256：7180f54b0358bdc21951884ffaba45844a20e4ea8cb4c14582f19f962ab5dbce。
- 审查有效一小时；到期或现场变化停止，不能改已冻结记录后直接执行。

11项合成维护回归通过；新增固定ZIP锚点测试及两个相关路径最终复验3项通过。每次探针前完整验证候选树与固定已签ZIP清单，前后复核ZIP哈希和批准提案。独立GPT-6 Sol medium审查并复审通过。真实隔离GUI演练：原始句柄正常退出→新窗口committed；坏候选→整树清理→旧指针/needs_review且没有启动旧版；外层Job确认所有测试进程退出。

## 当前执行状态

alpha.10 已实际安装并 committed/success、sequence6；原身份/任务保全、主摘要保持且只追加获批备用摘要、旧配置与 staged 精确归档、双事务与完整保全摘要均独立通过。真实候选通道健康和原任务探针通过，新 GUI 已激活并持有应用锁。手机读取两条已有回复待用户确认，本次未发布在线 feed 或修改 Android/relay。

原助手等待600秒超时且未修改安装；用户随后确认正常断开关闭。复用既有 verify_clean_exit 检查旧签名控制器自身清理记录、两把独占锁及旧PID缺席，完整 binding/proof/prior/priorState 与原审查一致。仅在本机新增 continue.local.py，原脚本/审查未改，先验原助手字节再执行相同字节，三次完整复查及新鲜探针后调用原 apply_after_exit。6/6 续接回归和独立复审通过。不宣称保留了已关闭的原句柄或观察到退出码，不重新打开旧PID，不删除 runtimeNeedsReview。

续接 SHA256：ee43bf5b9f2d8bf80c31b10448b6e108e3e7f3799d375fd51906c142960b4e71。审计 normal-close-continuation.local.json 明确 originalHandleExitObserved=false，installed-alpha10-audit.json 记录脱敏安装验收结果。不要重复 apply/continue，不重发 PHONE_ALPHA9_OK / DESKTOP_ALPHA9_CONTINUE_OK。

Core CI 38368132481 两平台测试、隔离演练和打包通过，artifact 上传因账号存储配额失败，整体失败；没有本轮 CI 可下载包。源码、本地签名包、实际安装、手机验收及在线 OTA 分别记录。
