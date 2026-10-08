# OTA 本地准备记录

本页为签名获批前的阶段归档。后续用户已明确批准合并、专用测试签名和alpha.5构建；这些已执行，当前证据见[STATUS](../STATUS.md)。公网部署仍未批准或执行，以下“暂缓签名/合并”不再是当前决定。

2026-10-08当时。用户明确选择“先完成本地准备，暂不合并或配置签名”。本阶段完成源码、审核文件、本机合成测试及开发分支CI；未生成项目真实发行密钥、写 GitHub Secrets、合并 main、部署服务器或变更现用身份。当时手机仍按原安装使用；GitHub alpha.4 预览资产未替换。

## 已完成的本地工作

1. [隔离部署文件](../deploy/test/README.md)：专用用户/home、受管 systemd 入口、独立 FRPS、独立 Caddy 容器及证书续期钩子。`Restart=no`，relay 使用 `RefuseManualStop=yes`，计划停服走受管 CLI 的任务空闲门禁。文件均以 `.review` 标识，不能当作已安装服务。
2. 发布器拒绝不能由私有服务提供的 URL：签名前检查准确的 `desktop-remote-updates/{test|stable}/{component}/latest.json`、组件/版本/扩展名，拒绝时不落元数据。保留原有密钥长度、签名、包完整性、目标和版本校验。未来生成 feed 时仍须实际按该名称放置文件。
3. 更新合成首装和 Windows 生命周期夹具使用相同规范路径。只有临时、不可发行的合成密钥；不读取原有项目签名或真实身份。

以上未改变三端分发的应用源码，发布器和部署模板均不在 `updates.publish` 的运行包白名单中。因此不编造软件版本变化，不以相同版本重新发布包；alpha.5 首装和 alpha.6 在线候选仍是后续计划。

## 本次实际验证

| 验证 | 结果与范围 |
| --- | --- |
| Node 20.19 回归 | 33/33 通过；首轮沙箱在加载测试前报目录EPERM，获准以本机合成范围重跑成功 |
| Windows Python 3.12 主套件 | 共107项：102通过、5项Linux专属跳过；包含新增发布路径拒绝与自定义HTTPS端口用例 |
| Windows relay 套件 | 共77项：73通过、4项Linux专属跳过 |
| Windows 真GUI/独立助手 | `isolated_gui_helper_committed 0.0.2 pythonw`；坏探针`isolated_gui_helper_rolled_back 0.0.1 pythonw`；仅临时身份/目录，探针不访问真实Desktop |
| 本机 WSL 中继生命周期 | Ubuntu Python3.14.4 / systemd259，非root sheng用户，真实Delegate=yes cgroup；升级0.0.2，坏0.0.3回退0.0.2，4棵自有树退出，瞬态单元已回收 |
| systemd手动停止保护 | 独立12秒sleep夹具设置RefuseManualStop=yes；root请求stop得到Operation refused，夹具保持active直到自然结束；不用于停止真实服务 |
| 续证钩子 | WSL 3项合成测试通过：无关lineage零调用、错误ID/标签/镜像/状态拒绝、成功只exec固定ID与22019；Windows显式跳过这3项，不计为通过 |
| 配置静态检查 | 最终Compose `config --quiet`、shell `sh -n`、FRPS TOML解析、版本文档一致性与git diff检查通过；不代表容器或FRPS已启动 |
| FRPS真实二进制配置检查 | 官方0.71.0 Linux amd64包完整大小/摘要匹配；以临时合成TLS/token替换路径后执行`frps verify -c`返回0；未启动FRPS |

WSL 使用临时账号库和loopback监听，网络下载由生命周期夹具注入；上述测试**不是**公网HTTPS下载、正式签名首装或手机验收。WSL的Python3.14/systemd259也不能代替目标服务器Python3.12/systemd255的部署验证。此前Caddy候选在实际服务器独立adapt/validate通过的证据保留，不能扩张为本次新容器已启动或证书已自动续期。

本机 Windows 命令（PythonExe/NodeExe 指向本机已有受支持解释器，不需在命令中传秘密）：

```powershell
& $NodeExe --test
& $PythonExe -B -m unittest discover -s test -p 'test_*.py'
& $PythonExe -B -m unittest discover -s relay -p 'test_*.py'
& $PythonExe -B scripts/windows_ota_smoke.py
& $PythonExe -B scripts/check_versions.py
& $PythonExe -B -m unittest discover -s deploy/test -p 'test_*.py'
git diff --check
```

WSL演练采用**本机**一次性单元`cdr-local-smoke-20261008-01.service`，经`systemd-run --wait --pipe --collect`创建，属性为`User=sheng`、`Group=sheng`、`Delegate=yes`、`RefuseManualStop=yes`、`Restart=no`、`NoNewPrivileges=yes`、`UMask=0077`。将`DESKTOP_REMOTE_TEST_CGROUP`明确指向这个单元的cgroup，执行本仓库`scripts/relay_ota_smoke.py`。使用新的唯一单元名复跑，结束后检查`LoadState=not-found`、`ActiveState=inactive`；不借用现用服务或未明确委派的cgroup。

Linux钩子回归可用`python3 -B -m unittest discover -s deploy/test -p 'test_*.py'`运行，测试以临时假docker执行，不接触容器。Core工作流已加入此命令与deploy/test路径触发条件，随`be5a01c`推送后已在Linux CI通过3项，Windows显式跳过3项。Linux审核文件固定LF，避免Windows检出换行使shell脚本不能运行。

## 推送与 CI 验证（2026-10-08）

本地准备提交`be5a01c0c0940450699ca8ad3e85cf6031d512a2`已推送到`codex/ota-implementation`，PR #1仍为OPEN、目标main。Core [push 37726150384](https://github.com/nzkres/codex-desktop-remote/actions/runs/37726150384) 与 [PR 37726153483](https://github.com/nzkres/codex-desktop-remote/actions/runs/37726153483) 的Windows/Linux均成功。下表数量从PR运行日志核对，跳过项不计为通过。

| 检查 | Windows CI | Linux CI |
| --- | --- | --- |
| Node | 33通过 | 31通过、2跳过 |
| Python主套件（107项） | 102通过、5跳过 | 94通过、13跳过 |
| relay（77项） | 73通过、4跳过 | 77通过 |
| 续证钩子（3项） | 3跳过 | 3通过 |
| 真实进程演练 | pythonw/Tk与独立助手：升级0.0.2、坏候选回退0.0.1 | cgroup中继：升级0.0.2、坏0.0.3回退0.0.2，4棵自有树清理 |

两个平台的源码ZIP构件已上传，Actions API确认未过期，保留至2026-10-22 UTC。它们是14天CI构件，不是长期feed；本轮不重新发布或覆盖alpha.4。三端应用分发输入未改变，新增的是发布工具、部署审核文件和测试。

Android [37726153367](https://github.com/nzkres/codex-desktop-remote/actions/runs/37726153367) 已成功：JVM单元测试、lint、主/测试APK编译及包内版本说明校验通过。CI核对主APK为alpha.4/code4、11,771,608字节、包内说明与源码一致；debug APK与报告已上传，保留至2026-10-22 UTC。普通PR任务不启用受控签名、不配置Secrets，未运行模拟器或真实手机安装。本次仅核对CI日志和构件API，没有将新调试包作为Release或OTA候选；同版本CI重编不代表允许替换已发布资产。

## 尚未执行与继续条件

FRPS工具来自[官方v0.71.0发布](https://github.com/fatedier/frp/releases/tag/v0.71.0)，归档13,932,092字节，SHA-256为`84f27e39f11169f7adcef8e8b70c9329de17747b1f14dad9fb95eef5682ea716`。初次下载停滞已取消，其后有界续传完成，再核对官方API摘要才解出单一frps文件。`verify`仅验证配置，不能证明TLS隧道、插件或手机连通；下载该开发工具也不是应用OTA下载验收。

- PR仍未合并；本轮CI证据与提交对应关系见上节，没有新增软件发布。此前3277a8a的成功记录是旧提交证据。
- 未生成、登记或备份真实RSA/APK签名；没有已签alpha.5安装包、alpha.6更新包或长期私有feed。Android源码本轮未改，仅CI调试构建，未受控重签或安装APK。
- 新部署单元、FRPS0.71.0配置、容器实际证书挂载、续证事件和云安全组尚未在目标环境验收。不能将模板解析或本地合成验证当作部署成功。
- 首装运行时、干净电脑安装、真实DPAPI身份保持和手机↔Desktop同任务接续，仍按[三端验收](OTA-ACCEPTANCE.md)单独执行。

以后用户决定开始受控签名时，再核对本地提交/CI与[部署审查](OTA-DEPLOYMENT-REVIEW.md)，固定HTTPS origin、RSA公钥和连续APK证书，完成首装构件后审查具体公网变更。后续签名工具增量已新增`--signing-home`内存解封入口，使用说明与尚未完成的密钥备份边界见[发行说明](OTA-PUBLISH.md#windows发行密钥与受控android构建)。旧`--private-key`仍读取外部PEM，不能把DPAPI blob直接传给它。不要为了继续而绕过main-only构建约束或使用临时debug签名。

连续APK签名的理由与证书要求见[Android官方签名说明](https://developer.android.com/studio/publish/app-signing)。密钥、密码和实际账号信息不进入本文或Git。
