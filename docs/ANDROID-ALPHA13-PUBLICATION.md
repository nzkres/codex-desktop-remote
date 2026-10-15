# Android alpha.13 私有更新发布记录

`0.1.0-alpha.13` / versionCode13 / 更新序号7 已发布原HTTPS私有源。实际鉴权完整APK下载、索引签名、原证书与包内trust已核验；手机安装与真机体验仍待用户验收。

## 使用

保留原应用和数据，在“我的 → 版本与更新说明 → 检查更新”覆盖安装alpha.13。

- 任务仓库 → GitHub：公开库输入owner/repo；私有库先在手机保存仅指定仓库 Contents: Read-only 的fine-grained令牌，再从仓库列表选择或输入全名。无需人工导入JSON。自动发现/自定义计划进度文档；来源固定同一提交。不会搬运电脑gh凭据，不写GitHub仓库。详见[数据约定](PROJECT-REPOSITORY.md)。
- Markdown复选清单或明确阶段表提供进度。构思不显示百分比；全部任务完成仍需明确验收声明才结束。无文档、未知状态或文档冲突显示待核对；同步失败保留上次资料。英文学习仓库实际使用PROGRESS.md阶段表，本轮未改其文档或替用户验收。
- 对话页每台电脑名下是额度数字与淡背景光，切换选择展示该电脑的真实窗口及采样时间。前台依次刷新，真实用量变化时背景变化；不按本地时间虚构消费。相同账号额度共享；离线/过期/失败显示上次与待核对，不能取到快照显示未同步。

## 构建和交付证据

[PR18](https://github.com/nzkres/codex-desktop-remote/pull/18)，签名源码`2f8e139f8f31eee94022d7aa7dbae8c13cf55619`，合并源码`1861c209217ebbee03002ba0d065bdcd23582f5c`，android-native逐文件一致。

- 原受保护签名本机受控debug构建；Android单元测试131项通过，Lint错误0，APP和UI测试APK编译成功。没有模拟器或真机UI执行证据，仍为测试预发布。
- Node182/182、Python199项（7跳过）通过。Windows及中继没有代码变更，原连接、配对身份、草稿和发送/问答回执路径未修改；未发起真实任务发送。
- GitHub Actions 38710296752 / 38710296657因计费或消费上限作业未启动；没有改变计费、Secrets、分支保护或密钥。使用原本机受控签名流程，不冒充CI通过。
- Manifest `dev.codex.desktopremote.lite` / versionCode13 / minSdk26；单签名证书SHA256 `ffcb12d413ee58613576b7914350ad4df887bf29d50d97f2b20c4e17dcebcf1a`；原trust字节一致。坏签名及旧序号拒绝通过。

| 项目 | 结果 |
| --- | --- |
| 包大小 | 40288600 bytes |
| APK SHA256 | `11cb0194eaaf286bd97e3279b652c6b88e4f5f8a855672f92908dee939c6c47c` |
| 索引 SHA256 | `1493fbafb4dadd3155d149fd1bb8dcbdd423d303591a06eb28c1cca9fbd8b9c3` |
| 旧索引 SHA256 | `06e1227873db2fb71faa594efcc955d2bb95d7abb6c7c384f077e05b059c45d9` |
| HTTPS复验 | 2026-10-15T09:15:19.072143+00:00 |
| 匿名索引/包 | 401/401 |
| 鉴权索引/完整包 | 成功，大小/哈希/签名一致 |
| 手机安装与私有GitHub授权 | 尚未实机验收 |

只发布Android包与索引，原发布锁内检查旧索引后原子切换。旧包及旧索引保留；服务PID/启动时间、中继程序和其他组件更新源保持一致。没有停止或重启连接器、重新配对或清理身份/回执；未知发送不能自动重试。

原受保护签名临时文件已删除。审计目录 `/opt/codex-desktop-remote-test/operator/android-alpha13-feed-20261015-seq7`；同版本包不可覆盖，修复应递增版本和序号。私有包只在原服务器发布，GitHub Release仅说明，不上传APK。
