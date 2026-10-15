# GitHub 任务仓库资料约定（alpha.13）

手机直接向 GitHub REST API 发起只读请求，不再要求导入JSON。公开仓库填写owner/repo；私有仓库在手机保存仅指定仓库的 fine-grained token，Repository permissions → Contents: Read-only（Metadata默认只读），加密按中继账号隔离。支持读取授权仓库列表及分页；不复用、导出电脑gh凭据，不写仓库、不执行文档指令，不自动创建Desktop任务。接口依据[GitHub Contents API](https://docs.github.com/en/rest/repos/contents?apiVersion=2022-11-28)及[仓库列表API](https://docs.github.com/en/rest/repos/repos#list-repositories-for-the-authenticated-user)。

关联时读取默认分支最新提交，再将所有目录和文件读取固定到该40位提交SHA，避免同次计划/进度混读。默认发现根目录PLAN.md、ROADMAP.md、PROGRESS.md及docs/PLAN.md、docs/PROGRESS.md（不区分文件名大小写，最多4个），否则README.md；自定义文档路径每行一个，最多4个，进度文件排在计划之后。关联后持续读取所选路径。每份文档128KiB、接口响应512KiB、任务200项、项目100个/总512KiB；只读普通UTF-8文件，拒绝非普通类型、路径穿越和跨域重定向。目录扫描不递归整个仓库。

页面前台每分钟检查一个到期项目；认证读取至少间隔5分钟，公开读取至少15分钟；后台停止。详情可手动同步。401、403/限流、404（也可能权限不足）、超时、声明冲突都保留上次成功记录；展示错误、原同步时间和提交，不重置成空项目。手机未配置私有凭据时不会把仓库当作不存在的构思。

## 推荐Markdown约定

无需专用JSON。PLAN.md存方案，PROGRESS.md更新任务；同一次提交内相同ID在后续进度文档覆盖前面记录。同一文档ID重复拒绝。推荐稳定任务ID，如T1、T2、T3-01；无ID按完整标题识别，跨文档修改标题可能变成两项，因此应使用ID。

```markdown
# 英文文档阅读
<!-- desktop-remote: state=active; plan=confirmed; acceptance=pending -->

- [x] T1 完成阅读页面
- [~] T2 接入真实词库
- [ ] T3 完成手机验收
```

只有行首Markdown任务列表为任务：`[ ]`=todo、`[~]`=doing、`[x]`/`[X]`=done。代码围栏中的示例不计入。README的清单仅在“项目计划/任务清单/开发进度/项目进度/Project Plan/Tasks/Progress/Roadmap”章节内计入，避免安装清单冒充开发进度。单纯叙述“已上线”、提交次数、发版数量和README文字都不自动算完成。

| state | 手机状态 | 数据约束 |
| --- | --- | --- |
| planning | 构思中 | 明确声明state=planning；没有任务；plan可为draft；无百分比 |
| ready | 待开始 | 存在任务、全部todo；方案视作confirmed；0% |
| active | 进行中 | 存在任务；done/全部任务派生进度；100%也可能等待验收 |
| completed | 已结束 | 明确state=completed、acceptance=accepted且全部任务done |
| review | 待核对 | 无可识别计划或存在不一致；不展示推测百分比 |

省略声明时：有清单且全部todo→ready；至少doing/done→active；无清单→review。不会自动completed或planning。声明中plan仅draft/confirmed，acceptance仅pending/accepted；多个文档声明冲突、未知值、不符合状态约束则本次同步失败，保留原记录。手机只读展示GitHub项目，不允许本地勾选冒充GitHub已更新；本机想法仍可独立编辑。

## 兼容已有阶段表

支持明确表头 `阶段 | 内容 | 状态`、数字阶段ID的Markdown表。todo/未开始/待开始→todo；doing/进行中（含后缀）/已接入（含后缀）→doing；done/已完成/已验收/“已完成，…”→done。不能唯一识别的状态标待核对，不擅自完成。复选清单存在时优先清单，不把同一项目不同粒度重复相加。

若有“总体进度：N / M”，与阶段表完成数及总数不一致则review，并保留原文进度与目标摘要。当前英文学习仓库PROGRESS.md就是阶段表，原描述9/11与部分阶段状态并不完全一致，本轮未改该仓库或替用户确认验收。源文档时间与GitHub采样时间不是同一种时间。

## 本机兼容与凭据

alpha.12的schemaVersion=1本机项目及JSON导入/导出保留兼容；新来源记录增加可选githubSource（commit、paths、syncedAt），不把解析进度手写回GitHub。首次关联按规范化仓库全名生成稳定ID，重复同步更新同项；已有本机项目不自动删除。卸载/清数据会丢失本机想法、凭据和关联；GitHub文档仍在仓库中。
