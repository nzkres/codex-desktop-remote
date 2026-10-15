# 受信任的启动门：父进程先把本进程放入 Job，再通过标准输入发送 G + 模式。
# B 表示还要接收启动数据，N 表示没有启动数据；未收到正确指令就退出。
# Node/FRP 子进程继承 Job 约束，因此父连接器可以核对整棵树的退出。

"""Trusted gate: launch only after parent has assigned this worker to its Job."""
import subprocess
import sys


if __name__ == "__main__":
    if sys.stdin.buffer.read(1) != b"G" or len(sys.argv) < 2:
        sys.exit(1)
    mode = sys.stdin.buffer.read(1)
    payload = sys.stdin.buffer.read(1025) if mode == b"B" else None
    if mode not in (b"B", b"N") or (payload is not None and len(payload) > 1024):
        sys.exit(1)
    child = subprocess.Popen(sys.argv[1:], stdin=subprocess.PIPE if payload is not None else subprocess.DEVNULL, close_fds=True,
                             creationflags=subprocess.CREATE_NO_WINDOW)
    if payload is not None:
        child.stdin.write(payload)
        child.stdin.close()
    sys.exit(child.wait())
