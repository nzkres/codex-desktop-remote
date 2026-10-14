# Windows alpha.15 私有更新源发布审查

状态：2026-10-14北京时间15:53:48，经用户明确批准，Windows私有测试源已从alpha.13/sequence9切换到alpha.15/sequence11；15:54:58现装alpha.13实际鉴权下载及验包通过。现机仍alpha.13、账本9，尚未安装alpha.15或验收其手机功能。此次不包含Android/relay更新、PR合并、自动停止连接器或实际安装。

## 固定发行输入

| 项目 | 已核验值 |
| --- | --- |
| 源码 | `0dbf345bee1b54e593050a6016db84329bcdbce1` |
| Core CI | [38524365265](https://github.com/nzkres/codex-desktop-remote/actions/runs/38524365265)：Windows/Linux测试、适用GUI/助手/HTTPS/维护演练和打包通过；artifact上传受配额阻止，整轮failure |
| 签名包 | `artifacts/alpha15/release/windows-0.1.0-alpha.15.zip`，173385字节，51个清单文件 |
| ZIP SHA-256 | `98f707824659ad3c67a0384f8c5ba7b830040e93d82b140f7313e560ba08cb5e` |
| 元数据 | 同目录`latest.json`，sequence **11**；未发布alpha.14已占sequence10，保留记录不复用 |
| 元数据SHA-256 | `beb98d652ec155db0a1ed6d643f14661b7312161f696f578b050d89ef62dcd25` |
| 有效期 | 北京时间2026-10-14 15:45:00至2026-10-21 15:45:00 |
| 密钥 | 原`cdr-ota-test-20261008-v1`；SPKI SHA-256 `c3835e3e59b362e0b356d3a7b3589c255187ddce34692167f5160b0e5f5e3a4d`，匹配现用trust |
| 更新源 | `https://106.53.126.69:9443/desktop-remote-updates/test/windows/latest.json` |
| 包URL | 同目录`windows-0.1.0-alpha.15.zip` |
| 预期旧metadata摘要 | `d7e682bc7d06b0e3af81cc5c8c3df749e58c0c42ef037820ec39e4d63cfafeda` |

重新从固定Git提交导出源码并打包，51文件与Git归档逐字节一致，ZIP CRC及各文件SHA全部通过。此前173997字节的未签候选采用Windows换行，仅与本地源码字节一致；换行归一化后内容相同，保留为历史构建证据，不用于发布。这是首次alpha.15签名，不替换任何已发布同版本包。

原DPAPI密钥仅在签名进程内存中使用，没有新密钥/Secrets或明文私钥导出。用现装alpha.13本身的`updates.security`执行新包签名/序号验证和隔离解包、manifest校验，全部通过；现装版本树也通过验证。没有写入现用ledger/candidate/current/config/identity，没有向真实任务发送输入。安装期新版本启动与任务健康仍待实际OTA验证。

## 累计变化及手机体验边界

alpha.15包含alpha.14的手机显式点标题后请求Desktop加载原对话，以及alpha.15普通问答/选项/回执投影。相较实际alpha.13，归一换行后变化仅在Desktop任务相关源码、mobile-sync、错误目录和版本记录；`updates/`更新引擎内容不变，dataSchema仍1，不做数据库迁移或授权重绑定。

普通问答完整手机体验需要Android alpha.8；按需打开需要Android alpha.7或更高。异步选择框本版只能显示，仍需在电脑回答。不能只升级Windows就宣称手机所有交互已经修好。Android候选37项模拟器已通过，但APK构件上传/受控签名/手机安装另行推进。

## 发布执行与验收

1. 发布前再次读取现用trust/ledger及HTTPS源，并核对服务器`/srv/codex-desktop-remote-test/data/update-feed/test/windows/`的归属/权限、无链接和旧metadata原字节摘要。预期旧源为alpha.13/sequence9；有变化停止，不覆盖其他发布。
2. 先保留旧metadata原字节。在独立暂存位置上传固定新ZIP与metadata，重新验签、校验大小/SHA/清单；新版本同名路径若已有不同字节立即停止。固定包以不覆盖已有不同内容的方式落位，归cdrtest，文件0600，目录0700。
3. 仅在完整复核旧metadata未变化后原子替换Windows latest.json。保留alpha.13包和旧索引记录，不重启relay/FRPS/Caddy，不修改Android或relay源。
4. 正常TLS验证匿名metadata/ZIP拒绝（401），当前配对设备鉴权下载成功；本地实际下载字节的签名、版本、sequence11、大小和SHA全匹配。发布不直接安装，也不改本机防重放账本。
5. 用户在任务空闲时正常断开，再检查更新、阅读alpha.15说明并下载安装。若当前对话仍在运行，等待本轮结束；任何未确认操作/任务/进程清理仍按原规则延期，不能删除保护强行安装。
6. 安装完成以实际窗口、current/事务终态、身份/权限/任务保全及健康验证为依据。重新连接后由手机验证原对话和可用功能；alpha.15实际安装及手机功能结果目前均未执行。

## 故障处理与撤回限制

发布前失败：旧源继续服务；保留暂存与审查记录。替换结果未知先读回核对，不盲目重复发布。

发布后客户端可能已见sequence11，直接还原sequence9不能让其降级，也不能清账本绕过防回放。若需撤回安装，先阻止继续发行并审查更高序号/新版本修复方案，保留原包及记录。

安装新版本失败：沿用独立助手，证明新版本及子进程退出、数据保持且旧alpha.13实际健康后才能回退。否则保留可诊断状态，不覆盖更新期间的数据，不承诺所有失败均自动回退。

## 证据

- 本地审查：`artifacts/alpha15/signed-publish-review.json`、`old-client-verification.json`。
- 固定源码全套测试：Node176/176；Python166通过、6平台跳过；Core CI适用测试通过。
- 本轮固定Git导出源码问答专项18/18通过；签名包与源文件逐字核对及旧客户端验包通过。
- 私有源发布：**完成**；服务器复验发行签名、固定包大小/SHA及51清单文件后原子切换，原索引和旧包保留，权限0700/0600。
- 公网下载验收：**通过**；正常TLS，匿名metadata及ZIP均401，当前配对设备鉴权下载成功，原alpha.13更新代码验证sequence11、签名、实际173385字节/SHA及51文件通过。
- 现装install/current/trust/ledger/candidate/DPAPI身份前后摘要未变；relay/FRPS/Caddy进程/启动记录及其他feed、服务器程序current未变。
- 执行审计：`artifacts/alpha15/private-feed-prepared.json`、`private-feed-publish.json`、`private-feed-https-verification.json`；服务器`/opt/codex-desktop-remote-test/operator/windows-alpha15-feed-20261014-seq11`。
- 现机alpha.15安装、真实手机验收：**未执行**。
