package dev.codex.remote

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

fun networkError(exception: Exception, sending: Boolean = false): String = when (exception) {
    is GatewayException -> when (exception.message) {
        "terminal_not_found" -> "这台电脑已移除或不属于当前账号，请到远程终端重新选择。"
        "terminal_paused" -> "这台电脑已暂停远程访问；不会切到其他电脑执行。"
        "terminal_offline" -> "这台电脑的连接器离线，请启动连接器；不会切到其他电脑。"
        "terminal_credential_unavailable", "terminal_forwarding_unavailable" -> "这台电脑尚未启用任务通道，请按接入指南启动。"
        "terminal_gateway_auth_failed" -> "这台电脑的任务通道认证异常，请检查连接器；无需重新登录手机。"
        "terminal_connection_lost", "terminal_response_timeout", "terminal_request_timeout", "terminal_unreachable" ->
            if (sending) "当前电脑的发送结果未确认。请先核对对话，草稿已保留，不会自动重发。"
            else "当前电脑任务通道暂不可达，正在重试；不代表任务停止。"
        "login_required" -> "登录已过期，请重新登录。"
        else -> exception.message ?: "当前电脑暂不可用，请稍后重试。"
    }
    is SocketTimeoutException -> if (sending) "发送结果暂未确认，请先查看对话记录，避免重复发送。草稿已保留。"
        else "连接超时，正在重试；不代表电脑任务停止。"
    is UnknownHostException -> "暂时无法找到中继服务器，请检查网络和地址。"
    is IOException -> if (sending) "连接中断，发送结果暂未确认，请先核对对话记录。" else "网络连接中断，正在重试。"
    else -> exception.message ?: "暂时无法连接，请稍后重试。"
}
