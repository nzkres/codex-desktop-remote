# Windows alpha.6 → alpha.7 在线升级审查

2026-10-08。Windows alpha.7修正连接失败时的配对申请状态和恢复提示，版本说明见[WINDOWS](versions/WINDOWS.md)。**用户已批准源发布，本表原签名包已发布到Windows私有test源，sequence3；用户随后已在原GUI点击下载并安装，实际alpha.6→alpha.7事务committed/success，独立保全/新窗口/探针验收通过；用户已确认升级后手机显示两条新测试回复，同任务接续验收通过。** 服务器和公网验收证据见[STATUS](../STATUS.md)。Android、relay保持alpha.5；本次没有升级它们或重启服务。

## 基线与发行输入

- 当前测试电脑已通过维护升级安装 alpha.6，用户确认手机原任务两条回复都能看到；普通 HTTPS OTA 尚未验收。
- 原 alpha.6 ZIP 为 93,986 字节，SHA-256 `4df87881c5d0f704a4b2ac0a1a2d12e022f5b11062a8740d7ad43f96ca7811cf`，发布元数据 sequence 2。不得重新构建或替换此文件。
- alpha.7 已在本机使用同一 `cdr-ota-test-20261008-v1` 发布密钥签发 sequence **3**，test/windows-x64、数据格式 1，兼容档和外部 Python/Node/FRP/Desktop 不变。私钥继续只在本机 DPAPI 保护；签发后已按真实安装公钥及sequence2账本复验，未写入现用安装。发布时再次核对账本及最新源，序号有变化即停止重审。
- 固定源：`https://106.53.126.69:9443/desktop-remote-updates/test/windows/latest.json`。新包：同目录 `windows-0.1.0-alpha.7.zip`。两项请求均由已有设备身份鉴权，不添加公开文件路由，不嵌入 PAT。
- 已部署源已从alpha.5/sequence1切换至alpha.7/sequence3；本机已实际alpha.7/ledger3。此前检查旧源可能被防重放拒绝的停点已结束，仍不得删除账本或保护来更新。
- 下表为已发布的固定输入；本机真实安装事务也已独立验收；手机同任务接续结果另记STATUS，发布/探针不能代替它。

| 发行输入 | 已核验值 |
| --- | --- |
| 源码 | `c20c2036e6319f9dca1ef108f7e8830f893c7f89`，独立分支 `codex/windows-alpha7`；[草稿 PR #3](https://github.com/nzkres/codex-desktop-remote/pull/3) 叠加在 PR #2，未合并main |
| CI | [push37781824455](https://github.com/nzkres/codex-desktop-remote/actions/runs/37781824455) / [PR37781829558](https://github.com/nzkres/codex-desktop-remote/actions/runs/37781829558)，均Windows/Linux成功；采用push的Linux产Windows ZIP |
| ZIP | `windows-0.1.0-alpha.7.zip`，**95,085字节** |
| ZIP SHA-256 | `91676bd9b0e1526c5808fb6a8d8156c2511f38ea4f306b1069d20d95d0412266` |
| 元数据 SHA-256 | `aa6dbad8c3eb1e1bf941008cc31c5bcd46277af91059d6bdbcce9c7db3cec81c` |
| 有效期 | 北京时间 **2026-10-08 21:12:09 至 2026-10-15 21:12:09**；`issuedAt=1791465129`、`expiresAt=1792069929` |
| 本机固定候选 | 工作树忽略目录 `artifacts/alpha7-preparation-20261008/final/` 下的 ZIP、`windows-latest.json`、`candidate-verification.json`、`signed-candidate-verification.json` |

ZIP中43个源文件均逐字节匹配上述Git提交，完整清单校验通过。与实际已签alpha.6包相比，只有`windows/gui.py`和`windows/releases.json`两项源文件变化，更新引擎和独立助手不变。首次CI的未签候选保留作证据，未发布；本次只使用`final/`中的已签固定包，不能混用。CI artifact仅保留14天，不是长期更新源；固定包现已上传私有feed，尚未创建alpha.7 GitHub Release。

## 本地验收门槛

配对错误用合成响应验证：提交结果与重新打开显示一致、成功后错误消失、运行保护优先、未知异常不输出令牌，保留原配对请求。Tk 合成窗口检查可读性。

正常 OTA 使用临时 CA、合成 DPAPI 身份和回环 HTTPS 源，实际执行元数据/包鉴权下载、GUI worker、独立助手和 Windows Job。成功须新窗口与版本生效；坏候选须清空自有树并验证旧 alpha.6 健康后回退。旧 committed 维护记录及陈旧暂存不能导致错误重装。Desktop 和任务通道在此演练中为明确合成探针；它不代替公网源或真实手机任务验收。

本机Node64通过，Python152项中146通过/6平台跳过；原生Tk配对错误/状态重读/后续恢复、六种维护交接、HTTPS GUI提交/健康回退通过。最终两轮CI含上述演练均通过。首次PR CI出现旧维护夹具在原helper句柄退出后立即断言外层Job清空的时序问题；改为对同一Job有界等待，并新增残留超时/观测错误必须失败的测试。没有放宽产品清理门槛，失败记录见WORKLOG。

**当前停点：** 步骤1—3发布与公网账号下载验收完成；发布阶段设备入口因运行保护拒绝且未绕过。用户随后自行在原GUI完成下载并安装，步骤5实际事务committed/success、ledger3，新GUI及身份/权限/任务保持独立验收通过。步骤6用户已完成手机发送PHONE_ALPHA7_OK及Desktop同任务接续DESKTOP_ALPHA7_CONTINUE_OK，并明确确认手机两条均显示；Windows实际OTA闭环通过。不要重复安装或重发已完成消息。手机安装、中继程序升级、PR合并与其他实例均未操作。

## 获批后的发布和实机步骤

1. 只读复核服务器 `/srv/codex-desktop-remote-test/data/update-feed/test/windows` 的归属、权限、当前 metadata 哈希/签名/序号，以及客户端版本/账本。将当前元数据原字节留在私有操作记录中，其他组件目录不变；不同于预期即停止。
2. 用独立临时文件上传已核验的新 ZIP，校验字节数/SHA-256，确认不存在同名不同包，再以不覆盖已有版本包的方式落位。目录 0700、文件 0600、所有者 cdrtest。不重启 relay/FRPS/Caddy。
3. 新 metadata 在本机及服务端复验；完整核对旧 metadata 未变化，最后原子替换 `latest.json`。只发布 Windows，保留原包与原数据。
4. 公网验证：正常 TLS；匿名 metadata/ZIP 均 401；当前设备授权下载成功；公钥、签名、平台/兼容档、大小和 SHA 都匹配。下载验证不直接开始安装。
5. 用户确认专用任务空闲，正常断开 Windows 连接后在原 alpha.6 窗口检查更新、阅读 alpha.7 说明、点击下载并安装。原窗口退出，独立助手验证旧句柄/锁、权限/待审批/回执、身份/数据以及候选启动健康，终态才算成功。
6. 验证实际 alpha.7 GUI、主事务 committed、原身份/权限/配置/任务和回执保持，重新连接。由用户在同一 Desktop UUID 做一轮新的手机发送与 Desktop 接续，并确认手机同步；不能重发未知结果。

## 失败与撤回

发布前失败：旧 metadata 继续有效，新包保持隔离；保留文件和脱敏结果，不触碰安装。metadata 原子替换结果未知时先读回哈希，不能盲重传。

发布后撤回：已见 sequence 3 的客户端会拒绝旧 sequence 1/2，**直接还原旧 metadata 不能恢复这些客户端，也不能用 feed 强制降级**。先停止继续安装、保留现状；准备经审查的更高序号及适用版本修复方案。版本、来源或序号发生竞争时停止，不能回滚本机 ledger。

安装候选失败：仅当候选及后代退出已证明、身份/数据保持、旧 alpha.6 健康且激活确认后才记 rolled_back。清理、回退或激活结果不明时保留 needs_review，不删除保护、重配对或把备份覆盖到当前数据。原 alpha.6 已消除旧审批别名误报，但能否健康回退仍以当次检查为准。

这轮不包括手机安装、中继程序升级、数据库迁移、稳定渠道、EXE 安装器或 Desktop 修改。完整三端验收继续按 [OTA-ACCEPTANCE](OTA-ACCEPTANCE.md)逐端推进。
