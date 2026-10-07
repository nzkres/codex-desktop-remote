# OTA 交接

当前三端更新源码与发布器已加入工作树，实际代码/CI/安装/真机层级以根目录 [STATUS](../STATUS.md) 为准。不要把 alpha.3 目录或版本记录当作已发布、已部署；已发布 alpha.2 的手动安装说明仍有效。

下一位维护者先读 [OTA 边界](OTA.md)、[发布步骤](OTA-PUBLISH.md)、[安装起点](install/README.md)和[恢复说明](OTA-RECOVERY.md)，再核对仓库实际 diff 与测试。第一件需要落实的外部资源是受控 RSA 发布密钥与经审查的 test/stable 公钥、Android 连续 APK 签名证书、独立长期 HTTPS 静态源；目前不能假设它们存在。普通 CI debug APK 的空 trust 和临时签名不能成为 OTA 首装基线；14 天 Actions artifact 也不能成为 feed。

隔离验收按组件独立做两个连续构件：签名与 served hash、全新 bootstrap/首装、在线 N→N+1、版本/数据/任务证据、失败与中断恢复。Windows 还须固定 Python 3.12 + cryptography、Node 20.19.0、FRP 0.71.0 和 Desktop 26.930.7945.0 精确 pin；新 `$Home` 不迁旧身份。Relay 只在专用 Linux 受管目录与显式委派 cgroup v2 上验；现用服务不自动接管。Android 的系统安装回执须在重开后核对实际 APK 与新鲜任务状态，取消安装不报成功。

任何公网源上线、现用服务切换和真实用户身份迁移都需要独立现场审查。测试时只用合成身份/临时数据库与专用 Desktop 任务，记录失败码和证据层级，勿复制凭据或私密任务内容。
