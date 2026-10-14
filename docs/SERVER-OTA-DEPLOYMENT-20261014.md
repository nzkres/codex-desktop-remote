# Android OTA 服务器交付实录（2026-10-14）

## 批准与代码

用户明确批准在106.53.126.69创建受限上传账号、配置两项CI Secrets，检查后合并PR #11并使用原证书构建alpha.8。本次不切换Android正式更新索引，不自动安装手机。前一轮自动审批要求具体账号授权；获得本次明确批准后才实际部署，没有绕过拒绝。

- [PR #11](https://github.com/nzkres/codex-desktop-remote/pull/11)已合并，head为`e02bce81e7253bc58f3e484fe7bae70dc1feb084`；main为`348fb2d9a68dff5d5ad55888f458c37e0bcd9800`，合并后完整文件树相同。
- [PR Android CI](https://github.com/nzkres/codex-desktop-remote/actions/runs/38537322078)整轮成功，包含传输协议、版本、编译、单元、lint和模拟器检查。
- [Core CI](https://github.com/nzkres/codex-desktop-remote/actions/runs/38537322133)两平台适用测试、升级/回退演练和源码打包通过；唯一失败步骤为原GitHub构件上传配额。未伪称整轮Core成功，未修改保护规则或付费设置。
- 本次只移除Android的GitHub构件保存。Core工作流的源码ZIP上传没有修改；GitHub仍保存源码、CI日志及已有历史资产。

## 真实服务器验收

账号`cdr-ota-upload`，UID995/GID985，只有自己的组，密码锁定且无sudo。SSH公钥设置`restrict`与固定接收命令；root持有授权文件和接收脚本。没有增加公网端口、修改sshd配置或重启现用服务。

| 项目 | 实际路径及权限 |
| --- | --- |
| 接收器 | `/opt/codex-desktop-remote-upload/receive_android_build.py`，root:root 0644，父目录0755 |
| SSH授权 | `/var/lib/cdr-ota-upload/.ssh/authorized_keys`，root:root 0644，父目录0755 |
| 私有暂存 | `/srv/codex-desktop-remote-incoming/android/`，cdr-ota-upload所有、0700 |
| 正式Android源 | `/srv/codex-desktop-remote-test/data/update-feed/test/android/`，上传账号不能读取或写入 |

部署接收脚本SHA256：`8fa53d0db5759e87109cd2e7e9aa96943bb8dbf9728543c2a423f5654fd608f8`。

真实SSH合成包验收成功，专用测试目录`90000000000020261014-1`内的app.apk只是明确标记的不可安装测试文本，不是发行包。4份文件总计228字节，接收收据、逐件大小/SHA256、目录0700和文件0600一致。任意`id`命令、重复run/attempt以及错误服务器公钥均被拒绝；本机临时明文上传密钥已经移除。上传私钥只留仓库外DPAPI加密库和专用CI Secret，不在Git、APK或命令行参数。

两项CI Secret已实际配置：`OTA_UPLOAD_SSH_KEY`和`OTA_UPLOAD_KNOWN_HOSTS`，北京时间17:00:10/17:00:12。原六项Android签名配置未更换。

部署前后relay PID2718122、FRPS PID2707211及systemd启动时间完全一致。真正的HTTPS服务是Docker容器`codex-desktop-remote-test-caddy`，核对为running、PID2712564、启动时间2026-10-08T07:14:47.790447065Z；主机`caddy.service`本来就是inactive，不把它当HTTPS故障。

Android现用latest.json SHA256仍为`0e46801282e1ed0e153bd585b9bf9216379b86607b313998072437077189fbf5`。原配对/数据库/Windows更新源没有操作。

## 受控构建与验包

[首轮受控签名构建38539346508](https://github.com/nzkres/codex-desktop-remote/actions/runs/38539346508)输入signed_ota=true，固定main提交348fb2d。编译、单元、lint、版本、原APK证书和内置trust检查全部通过，最后上传失败，未产生服务器发行目录，不能报告可交付APK。

故障证据：服务器17:06:05只记录该CI客户端在认证前断开；专用暂存只有先前合成目录，没有run38539346508。GitHub CLI官方`pkg/cmd/secret/set/set.go`的getBody对stdin执行`bytes.TrimRight(body, "\\r\\n")`；本机原密钥含末尾LF，而Linux临时合成密钥实测去掉LF时ssh-keygen报libcrypto，恢复LF后成功。上传脚本现将临时key/known_hosts规范为LF并补末尾LF，不换密钥或权限、不输出秘密，增加缺LF/CRLF回归。GPT-6 Sol medium只读复核无阻断；真实CI重试及取回验包待完成。首次失败已确定未接收，修复后才允许一次新构建。本机修复后Node36/36、Python139项中132通过7平台跳过，版本记录和git diff --check通过。签名合成测试需仓库外TEMP；首次受限沙箱的回环/链接错误及仓库内TEMP触发external_signing_directory_required已通过隔离目录修正，未修改真实签名库。

## 维护与后续发布

后续使用同一个受控工作流，在更新版本记录并审查main后执行：

```text
gh workflow run android.yml --repo nzkres/codex-desktop-remote --ref main -f signed_ota=true
```

同一run/attempt不会覆盖已接收包。收到未知上传结果时，先由管理员核对收据和完整性；不得盲目重跑或发布。暂存满1GiB会拒绝新上传；须核对发布/备份状态后清理，不自动删除上一版。重跑可能产生另一构建，不能把同版本不同哈希的APK替换到已发布源。

发布前独立核对原APK证书、实际Manifest、包内版本说明及trust，再签新序号元数据、保留旧索引与APK，批准后使用旧索引SHA作CAS并原子切换。匿名访问应401、账号下载应成功且哈希一致；Windows设备身份不能冒充Android账号。用户再在手机“我的→版本与更新说明”检查、下载并由系统确认覆盖，保留登录/配对/任务记录。发布、实际安装及同任务/问答验收分别记录。

停止接收只需撤销该账号的authorized_keys及两项上传Secrets，保留暂存审计；不会撤销APK证书或影响现有手机下载。已被手机接受的新序号不能倒退，升级后不能靠卸载/降级恢复，需更高versionCode修复。

本机详细收据位于当前工作树忽略的`artifacts/server-ota/`，不含明文凭据；源码和维护规范见[服务器交付说明](SERVER-OTA-DELIVERY.md)。
