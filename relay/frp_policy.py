"""FRP 0.71 plugin policy. Private RPC only; never expose through public Caddy."""

from registry import ControlError


OPS = ("Login", "NewProxy", "Ping", "NewWorkConn", "NewUserConn", "CloseProxy")


def evaluate(registry, operation, content):
    try:
        if operation not in OPS or not isinstance(content, dict):
            raise ControlError(403, "unsupported_operation")
        user = content if operation == "Login" else content.get("user", {})
        if not isinstance(user, dict) or not isinstance(user.get("metas"), dict):
            raise ControlError(403, "invalid_identity")
        identity = registry.device(user.get("user"), user["metas"].get("device_token"))
        if operation == "Login":
            # FRP uses run_id to replace/reconnect a control session. Never let a
            # valid device choose another device's session identity.
            return {"reject": False, "unchange": False, "content": {**content, "run_id": identity["id"]}}
        if operation in ("NewProxy", "NewUserConn", "CloseProxy"):
            if content.get("proxy_name") != identity["proxyName"]:
                raise ControlError(403, "wrong_proxy")
        if operation in ("NewProxy", "NewUserConn") and content.get("proxy_type") != "tcp":
            raise ControlError(403, "unsupported_proxy_type")
        if operation == "NewProxy":
            # No random ports, groups, alternate protocols or client-selected routes.
            if type(content.get("remote_port")) is not int or content["remote_port"] != identity["port"]:
                raise ControlError(403, "wrong_port")
            if any(content.get(key) for key in ("group", "group_key", "custom_domains", "subdomain", "locations", "multiplexer", "sk")):
                raise ControlError(403, "unsupported_proxy_options")
        return {"reject": False, "unchange": True}
    except (ControlError, TypeError, ValueError):
        # Do not echo request metadata, credentials or detailed auth failures.
        return {"reject": True, "reject_reason": "Terminal operation denied"}
