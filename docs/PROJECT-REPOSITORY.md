# 任务仓库资料约定

Android alpha.12 的任务仓库按登录账号和中继地址隔离，使用手机原有加密存储机制。本版不自动连接GitHub，也不以仓库存在、README文字或对话数量判定开发状态。已有英文文档阅读等项目可先核对进度，再用以下JSON导入；手机导出相同格式。覆盖同编号项目前会显示核对确认。

```json
{
  "schemaVersion": 1,
  "id": "english-doc-reader",
  "title": "英文文档阅读",
  "goal": "此处填写确认后的目标与范围",
  "repository": {"provider": "github", "fullName": "owner/repo"},
  "state": "ready",
  "plan": {"state": "confirmed"},
  "tasks": [
    {"id": "scope", "title": "确认第一版验收标准", "status": "todo"},
    {"id": "build", "title": "实现已确认的功能", "status": "todo"}
  ],
  "acceptance": {"state": "pending"}
}
```

示例不是实际仓库进度。任务状态仅为todo、doing、done，按done/总项数派生百分比，不重复存储手填百分比。

- planning：构思中，尚未生成任务；不展示百分比。
- ready：方案confirmed且有任务，所有任务todo；进度0%。
- active：已开始，可标记任务完成；全部完成仍等待验收。
- completed：必须所有任务done且acceptance.state=accepted；保留任务与验收记录。

导入限制：schemaVersion=1、128KiB以内、任务编号唯一且不超过200项、仓库全名符合owner/repo；手机总记录最多100个项目且加密内容不超过512KiB。非法资料不会部分覆盖已有项目。导出由用户选择文件位置，可能是设备本地或用户选定的文档提供者。

任务清单与Desktop会话分别保存，本版编辑任务不会调用模型、创建Desktop对话或修改GitHub文件。卸载/清数据将丢失本机项目；重要记录请主动导出。它不是跨设备同步或云端备份。
