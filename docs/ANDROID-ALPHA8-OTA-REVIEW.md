# Android alpha.8 OTA发布审查

## 当前状态

2026-10-14：本分支基于main的7f4d80d，只提取已经测试的0dbf345bee1b54e593050a6016db84329bcdbce1中的android-native及Android CI文件；不包含Windows/relay业务代码，不变更签名Secrets或受控签名main-only条件。此文不代表APK已经可下载或已发布。

线上Android测试源实查仍为alpha.5、versionCode5、sequence1，只有android-0.1.0-alpha.5.apk，11772524字节，原签名元数据有效且包SHA匹配。元数据北京时间2026-10-15 14:33:03到期，不能靠改本机时间/忽略过期继续使用。Windows alpha.15已经独立发布；其发布不等于Android发布。

## 第一版用户体验

- 保留alpha.7手机点标题请求Desktop加载原对话的能力，Windows需alpha.14或更高。
- alpha.8配合Windows alpha.15显示普通问答选项说明、自由输入及多题；按原题版本提交，Desktop明确记录后显示结果。
- 账号/电脑/任务/问题分别保存加密草稿；不确定提交只刷新核对，不自动重发。
- 异步选择框显示题目及选项，但当前需要电脑回答。命令审批、权限、登录验证、模式切换等未接通动作仍明确引导电脑操作。

## 固定升级边界

包名dev.codex.desktopremote.lite、versionCode8；沿用现有APK证书SHA256 ffcb12d413ee58613576b7914350ad4df887bf29d50d97f2b20c4e17dcebcf1a及原OTA发布公钥。签名构建仅允许main。不得用普通PR debug包覆盖现用手机，不卸载、不清登录和草稿、不重新配对。不把APK签名私钥或RSA私钥写入Git、日志、客户端或下载源。

本PR只调整手机功能、相应测试与文档；Android更新器及登录/身份存储实现不变。发布前必须复核实际APK证书、Manifest版本、包内版本说明、内置trust及大小/SHA，不以CI源码版本代替安装包验收。

## 检查与发布顺序

1. 本分支提取完整性检查、版本文档检查、现有Node/Python测试；独立Android CI编译、JVM、lint、APK版本检查及37项模拟器测试。
2. 用户审阅此独立PR后批准合并及原证书签名构建。此前PR #9批准只覆盖alpha.7；不能据此自动合并alpha.8或整批PR #8。
3. GitHub构件保存目前仍受配额阻止：已按用户授权保留当前仓库同类最新构件，2026-10-14 15:57:55的Android run38530732869仍报Artifact storage quota has been hit。等待额度实际恢复后执行一次受控构建；不把清理成功当额度恢复，不循环重建、不修改付费、不删除其他仓库或Release、不借日志传APK。
4. 取得受控签名APK及报告后本地独立复验，准备新的Android签名元数据（新sequence、新有效期、固定APK哈希；检查全体既有签发记录避免序号复用），保存旧feed及审批清单。
5. APK和元数据准备完整且验收后，请用户单独批准Android私有源切换。只发布Android，不重启连接器/中继，不更新Windows源。原子落位、私有鉴权和正常TLS实际下载核对；Android下载用账号身份，不拿仅限Windows的设备身份冒充账号。
6. 用户在APP“我的→版本与更新说明”检查alpha.8、下载安装并在Android系统确认覆盖；重新打开确认实际版本、登录/配对/草稿保留，再验收原任务及普通问答。发布成功不等于手机安装成功。

## 故障与回退

上传/签名/包校验失败：停止，不发布未核验包。切换结果未知先读回；源序号不倒退。手机取消安装时旧版继续使用；已升级后Android通常不允许低versionCode覆盖，不承诺自动回退，发布更高版本修复且保留用户数据，不卸载绕过。已提交但结果未知的问题只能核对，不重放。

## 证据分层

- 原完整源码0dbf345：Android run38524365342编译、单元、lint、APK核对及37项模拟器通过；上传被配额阻止，整轮failure。
- 独立分支：提取完整性、本机检查和新CI结果另记STATUS/WORKLOG。
- alpha.8原证书签名包、本地取回验包、私有源发布、手机覆盖安装与功能实测：均未完成。
