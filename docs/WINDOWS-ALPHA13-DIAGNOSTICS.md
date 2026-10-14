# Windows alpha.13：错误清单与证书断连修复

## 当前结果

代码已实现；Python 172项（166通过、6平台跳过）、Node 221项通过，定向诊断32项通过；合成Tk真实控件点击及高DPI布局通过。签名包/CI/现用安装分别在STATUS记录。当前真实连接器仍是alpha.12，不能把源码或演示窗口当作现用升级。

## 在窗口里怎样用

主窗口下方“错误诊断”显示当前或最近报告的固定代码。点击“错误清单与代码说明”：

1. “本次窗口的错误记录”保留最近100条状态变化。连续重复不刷屏，恢复后历史保留，但不再列为当前连接错误。关闭窗口后此内存历史不保留；原保护记录仍按原规则保留。
2. “全部错误代码手册”收录211个连接、身份、通道和更新/维护代码。它是帮助目录，不能据此认定所有错误都发生过。可按代码、中文原因或分类搜索。
3. 点击一行看具体原因、处理步骤和源码文件/函数/行号。行号来自本版本实际源码的只读解析；安装包不含发布/维护工具时，显示仓库文件和函数，明确不提供虚构行号。分类入口与原始抛错点有文字区分。
4. “复制安全说明”只复制界面固定说明；不包含令牌、原始异常、服务器响应、身份内容或任务正文。未知异常一律归为operation_failed，不猜原因。

只读快照中的unverified不是故障；活跃连接持有runtimeNeedsReview是正常保护标记，不能当成前次退出失败。登记connected、探针通过和手机任务实际完成仍分别验收。手机任务接口的错误不会假装已经在Windows窗口观察到。

## 本次自动重试的证据链

2026-10-14主电脑读取原配对状态：reconnecting / connection_unavailable。携原身份执行只读配对查询，Python标准TLS验证返回SSLCertVerificationError，verify_code=10（证书过期）。没有忽略证书验证，也没有重配对。

服务器磁盘证书已续期至2026-10-17 10:13:34 UTC，但9443实际提供的旧证书在2026-10-13 22:07:54 UTC过期。certbot定时器active、最近执行success。专用hook调用caddy reload但未加--force；本机Caddy帮助明确说明force用于配置内容相同时强制重载。

修复只改专用测试入口hook为caddy reload --force，保留原容器身份、镜像、Compose标签校验。先在服务器临时目录跑3项合成hook测试，再对原hook内容精确比较、保留备份、原子替换并执行。没有停止Desktop、Gateway或FRPS，没有改旧443入口、身份、授权或数据库。

11:16北京时间正常TLS复核：9443实际证书SHA256为996d809e08bdca22f116cef671f37c89b34a460c99e2e6271cbd709c61084cca，有效至上述新日期；/auth/status HTTP200。现用alpha.12自行恢复connected、lastError=null。运行保护标记为true，表示正在持有连接，不是本轮退出故障。手机新对话验收仍待用户完成。

本地脱敏证据：artifacts/alpha13/certificate-hook-repair.jsonl、live-certificate-recovery.json；不提交运行身份、原始日志或证书私钥。

## 看哪里

- windows/gui.py：主窗口接收安全状态、错误记录、筛选、详情与复制。
- windows/error_catalog.py：代码→中文原因/动作/源码位置，未知数据安全降级。
- windows/vendor/connector_core.py：network_error_code、RelayClient.failure_code及原配对/心跳状态机。区分证书过期、不可信、TLS握手、DNS、超时、拒绝连接和HTTP状态。
- windows/controller.py：组装通道前获取连接参数失败时保留分类代码。
- deploy/test/renew-caddy.sh.review：专用HTTPS入口续证后的强制重载。

没有新增配对协议、任务数据库、后台遥测或自动发送。现有独占锁、完整身份比较、安全退出、更新事务不放宽。

## 纯演示与验证

在仓库根目录运行（需要已具备Python和Tk）：

```powershell
python -B windows/demo.py
python -B test/gui_smoke.py
python -B -m unittest discover -s test -p "test_*.py"
node --test
python -B scripts/check_versions.py
```

演示按钮可模拟证书过期、HTTP404和登记恢复，不创建身份、不连接网络、不接触Desktop。窗口标题明确为合成演示。截图只捕获测试窗口句柄，不依赖用户当前前台应用。

## 故障恢复与发布边界

HTTPS修复不要求换客户端；原连接继续重试即可。不要重配对、删除runtimeNeedsReview或禁用TLS。若新版客户端候选未通过健康验证，沿用现有独立更新助手和回退保护，不直接覆盖运行文件。新错误界面只有实际升级到alpha.13后才出现在现用窗口；源码演示、签名包和实机安装分别记录。
