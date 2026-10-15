# 更新器专用启动门：等待管理端把本进程加入 Job/cgroup 后才执行候选程序。
# 这与连接器传输 worker 作用类似，但属于 OTA 进程管理链，不要随意互换。

"""Trusted pre-exec gate: containment is installed before any candidate code runs."""
import os
import subprocess
import sys

# 父进程先把本脚本放进 Job/cgroup；只有收到单字节 G 才启动候选程序。
# 若进程归属失败或管道提前关闭，候选代码不会运行。
if os.read(sys.stdin.fileno(), 1) != b'G':
    raise SystemExit(2)
child = subprocess.Popen(sys.argv[1:], stdin=sys.stdin, stdout=sys.stdout, stderr=sys.stderr,
                         close_fds=True)
# 将候选程序退出状态传回父进程；进程树是否清空仍由 Job/cgroup 证明。
raise SystemExit(child.wait())
