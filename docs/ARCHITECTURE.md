# 原型关系与数据归属

三个原型串接已有 Desktop 原生任务；身份、路由和任务数据各有权威来源。当前实测范围与待验收项见根目录 [STATUS.md](../STATUS.md)。

```mermaid
flowchart LR
    U[用户] --> A[手机遥控 UI]
    A -->|授权请求 / 目标电脑| R[服务器中继]
    R -->|已有隧道内按电脑转发| W[Windows 连接器]
    W -->|主动登记 / 心跳 / TLS隧道| R
    W -->|本机 IPC / 同一任务| D[Codex Desktop<br/>外部执行器和任务拥有者]
    D -->|原生任务结果| W
    W --> R --> A
```

- **身份与授权：**服务器中继校验账户、设备和目标电脑路由；Windows 连接器保存本机 CurrentUser DPAPI 保护的配对身份及明确的任务绑定。手机只使用获授权的目标，不因能连通就获得其他电脑或任务权限。
- **任务数据：**Desktop 持有原生任务、执行与权限状态；连接器只维护必要的同步游标、操作回执和本机运行状态；中继转发请求，不成为原生对话数据库。手机展示同步投影与本地草稿。
- **故障边界：**发送回执、网络连通和 `connected` 不能证明任务完成。未知发送结果保留为未知并阻断重复提交；断线重连须通过连接器的状态核对。旧 Gateway 迁移另有前置条件，不属于这条原型链路。

```mermaid
sequenceDiagram
    actor User as 用户
    participant Phone as 手机 UI
    participant Relay as 中继
    participant Win as Windows 连接器
    participant Desktop as Desktop 原生任务
    User->>Phone: 生成一次性配对码
    Phone->>Relay: 创建配对申请
    Relay-->>Phone: 配对码（仅页面内存）
    User->>Win: 填服务器和配对码
    Win->>Relay: 用本机新设备凭据认领
    User->>Phone: 确认这台电脑
    Phone->>Relay: 确认设备绑定
    Win->>Relay: 查询确认并主动建立TLS任务通道
    User->>Phone: 选择这台电脑和专用任务
    User->>Phone: 发送文字
    Phone->>Relay: 发送到所选任务
    Relay->>Win: 转发请求
    Win->>Desktop: 核对同一任务并 start-turn
    Desktop-->>Win: 原生操作回执
    Win-->>Relay: 原生回执（不等于任务完成）
    Relay-->>Phone: 发送结果
    User->>Desktop: 在桌面接着处理同一任务
    Phone->>Relay: 拉取同一任务的增量
    Relay->>Win: 转发同步请求
    Win->>Desktop: 读取原生任务状态
    Desktop-->>Win: 同一任务最新状态
    Win-->>Relay: 增量投影
    Relay-->>Phone: 显示Desktop接续回复
```

多电脑路由使用目标电脑标识隔离请求，但当前只验收一台电脑的一条任务；不得据此声称所有电脑、Android 新包或新提取中继已通过。原公网服务和用户现装 APK 不在本原型变更范围。
