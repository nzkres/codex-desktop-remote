# OTA 交接

下一阶段工具已补：`updates.signing`提供仅本机CurrentUser DPAPI的RSA库，发布器通过`--signing-home`直接内存签名；Android受控构建增加trust预检/包内比对及PKCS12格式。先读[发行工具与待确认执行范围](OTA-PUBLISH.md#windows发行密钥与受控android构建)，不要把临时密钥测试算作真实签名已配置；密钥备份/恢复与实际首装仍待处理。本次CI结果见STATUS，旧be5a01c仅代表上一阶段。

**最新用户边界：先完成本地准备，暂不合并或配置签名。** 部署草稿、发布路径校验和合成测试已随`be5a01c`推送至`codex/ota-implementation`。Core push37726150384/PR37726153483与Android37726153367全成功；后续仅补文档，软件版本与alpha.4资产不变。下一位先读[本地准备记录](OTA-LOCAL-PREPARATION.md)，再核对最新head、未提交改动与PR状态，不要把“继续下一步”理解成合并main、创建真实发行密钥/Secrets或公网部署授权。

2026-10-08增量：用户已选定**现有中继服务器新增隔离环境**。已只读核对106.53.126.69及候选资源，独立HTTPS9443配置通过实际Caddy校验；没有部署。后续按[部署审查](OTA-DEPLOYMENT-REVIEW.md)确认入口、签名保管与最终安装清单，处理IP短期证书续期，不再重新询问环境选项。具体origin和公网变更授权仍待确认。

alpha.3 已完成私有开发预览归档；alpha.4 新增私有下载鉴权。实际代码/CI/下载/安装/真机层级以根目录 [STATUS](../STATUS.md) 为准。PR #1 仍需核对合并授权与远端状态，不能直接推 main。

下一位维护者先读 [落地验收](OTA-ACCEPTANCE.md)、[OTA 边界](OTA.md)、[发布步骤](OTA-PUBLISH.md)、[安装起点](install/README.md)和[恢复说明](OTA-RECOVERY.md)，再核对分支、未提交改动、CI 与发布资产。用户已经确定“保持私有，先补下载鉴权”，不要重新提议公开源码包。Android 使用既有登录、Windows 使用本机配对身份，relay 管理员交互 login/logout；仅限同 HTTPS origin 固定组件目录，元数据和包均鉴权。先核验匿名/过期/撤销/暂停/跨源拒绝，再审查服务器部署。服务器已选定；尚需确定最终 HTTPS origin、受控 RSA 发布密钥保管和连续 Android 证书。普通 CI debug APK 的空 trust 和临时签名不能成为 OTA 首装基线；14 天 Actions artifact 也不能成为 feed。

隔离验收按组件独立做两个连续构件：签名与 served hash、全新 bootstrap/首装、在线 N→N+1、版本/数据/任务证据、失败与中断恢复。Windows 还须固定 Python 3.12 + cryptography、Node 20.19.0、FRP 0.71.0 和 Desktop 26.930.7945.0 精确 pin；新 `$InstallRoot` 不迁旧身份。Relay 只在专用 Linux 受管目录与显式委派 cgroup v2 上验；现用服务不自动接管。Android 的系统安装回执须在重开后核对实际 APK 与新鲜任务状态，取消安装不报成功。

任何公网源上线、现用服务切换和真实用户身份迁移都需要独立现场审查。测试时只用合成身份/临时数据库与专用 Desktop 任务，记录失败码和证据层级，勿复制凭据或私密任务内容。
