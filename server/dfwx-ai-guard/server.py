#!/usr/bin/env python3
"""《东方无限》内置 AI 渠道的请求验签服务（仅监听 127.0.0.1，由 nginx auth_request 调用）。

## 为什么需要它

内置渠道的令牌必须烧进 APK，而 **APK 里任何密钥都能被反编译扒出来** ——
这是客户端凭据的固有性质。所以真正的防线不是"扒不出来"，而是：

1. **光有令牌没用** —— 必须能算出签名（本文件负责这一条）；
2. **泄露一个不牵连别人** —— 每台设备独立凭据；
3. **就算被用也有上限** —— 配额 + 限流。

## 它怎么被调用

nginx 在 `/ai/v1/chat/completions` 与 `/ai/v1/models` 两个 location 上挂：

    auth_request /_ai_verify;

nginx 会把**原请求的头**转发到这个内部子请求，所以这里能读到
`X-DFWX-Ts` / `X-DFWX-Nonce` / `X-DFWX-Sig`。返回 2xx = 放行，401 = 拒绝。

## 两种运行模式（重要）

- `DFWX_AI_GUARD_MODE=observe`（默认）：**只校验、只记录，永不拦截。**
- `DFWX_AI_GUARD_MODE=enforce`：校验不过返回 401。

**上线必须先跑 observe。** 签名一旦有 bug，enforce 会让所有用户直接不能聊天 ——
observe 模式让问题先暴露在日志里，而不是暴露在用户脸上。

## 签名算法（App 侧必须一字不差地实现）

    payload = ts + "\n" + nonce + "\n" + method + "\n" + path
    sig     = HMAC-SHA256(sign_key, payload)  的十六进制小写

- `ts`：Unix 秒（十进制字符串）
- `nonce`：每次请求都不同的随机十六进制串（长度 >= 16）
- `method`：大写 HTTP 方法，如 `POST`
- `path`：请求路径，如 `/ai/v1/chat/completions`（不含查询串）

## 校验规则

1. `ts` 与服务器时间相差不超过 `DFWX_AI_GUARD_SKEW`（默认 120 秒）—— 防重放；
2. `nonce` 在该时间窗内**没被用过** —— 防同窗内重放；
3. 签名正确 —— 防伪造。

## 配额

按**设备标识**（`X-DFWX-Device`，没有就退化成来源 IP）统计每日请求数，
超过 `DFWX_AI_GUARD_DAILY`（默认 300）就拒绝。计数落 `/opt/dfwx-ai-guard/quota.json`，
跨重启保留，每天按 UTC 日期滚动。
"""
import hashlib
import hmac
import json
import os
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = os.environ.get("DFWX_AI_GUARD_HOST", "127.0.0.1")
PORT = int(os.environ.get("DFWX_AI_GUARD_PORT", "8092"))

STATE_DIR = os.environ.get("DFWX_AI_GUARD_STATE", "/opt/dfwx-ai-guard")
KEY_FILE = os.environ.get("DFWX_AI_GUARD_KEYFILE", os.path.join(STATE_DIR, "sign.key"))
QUOTA_FILE = os.path.join(STATE_DIR, "quota.json")
LOG_FILE = os.environ.get("DFWX_AI_GUARD_LOG", "/var/log/dfwx-ai-guard.log")

MODE = os.environ.get("DFWX_AI_GUARD_MODE", "observe").strip().lower()
SKEW_SECONDS = int(os.environ.get("DFWX_AI_GUARD_SKEW", "120"))
DAILY_LIMIT = int(os.environ.get("DFWX_AI_GUARD_DAILY", "300"))

_lock = threading.Lock()
_seen_nonces = {}   # nonce -> 过期时间戳
_quota = {}         # (日期, 设备) -> 次数
_quota_day = ""


def log(message):
    line = "%s %s\n" % (time.strftime("%Y-%m-%d %H:%M:%S"), message)
    try:
        with open(LOG_FILE, "a", encoding="utf-8") as handle:
            handle.write(line)
    except OSError:
        sys.stderr.write(line)


def read_sign_key():
    """读签名密钥。读不到返回 None —— 此时**必须放行**（fail-open）。

    理由：密钥文件缺失是部署问题，不是攻击。把它变成"所有人都不能聊天"
    是拿用户当人质，比放行更糟。真出事时日志里会有明确记录。
    """
    try:
        with open(KEY_FILE, encoding="utf-8") as handle:
            return handle.read().strip() or None
    except OSError:
        return None


def load_quota():
    global _quota, _quota_day
    try:
        with open(QUOTA_FILE, encoding="utf-8") as handle:
            data = json.load(handle)
        _quota_day = data.get("day", "")
        _quota = {tuple(k.split("|", 1)): int(v) for k, v in data.get("counts", {}).items()}
    except (OSError, ValueError):
        _quota, _quota_day = {}, ""


def save_quota():
    try:
        os.makedirs(STATE_DIR, exist_ok=True)
        tmp = QUOTA_FILE + ".tmp"
        with open(tmp, "w", encoding="utf-8") as handle:
            json.dump({
                "day": _quota_day,
                "counts": {"|".join(k): v for k, v in _quota.items()},
            }, handle)
        os.replace(tmp, QUOTA_FILE)
    except OSError:
        pass


def prune_nonces(now):
    for nonce in [n for n, exp in _seen_nonces.items() if exp < now]:
        del _seen_nonces[nonce]


def bump_quota(device):
    """返回今天这个设备已经用掉的次数（含本次）。跨天自动清零。"""
    global _quota_day
    day = time.strftime("%Y-%m-%d", time.gmtime())
    if day != _quota_day:
        _quota.clear()
        _quota_day = day
    key = (day, device)
    _quota[key] = _quota.get(key, 0) + 1
    save_quota()
    return _quota[key]


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "dfwx-ai-guard"
    sys_version = ""

    def log_message(self, *args):
        pass    # 自己记日志，不要 stderr 噪音

    def _reply(self, code, reason):
        body = json.dumps({"ok": code == 200, "reason": reason}).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = self.path.split("?")[0]
        if path == "/health":
            self._reply(200, "ok")
            return
        if path != "/verify":
            self._reply(404, "not found")
            return

        now = int(time.time())
        ts_raw = self.headers.get("X-DFWX-Ts", "")
        nonce = self.headers.get("X-DFWX-Nonce", "")
        sig = self.headers.get("X-DFWX-Sig", "").lower()
        # auth_request 子请求里拿不到原始 method/path，用 nginx 传过来的这两个头
        method = self.headers.get("X-DFWX-Method", "POST").upper()
        # nginx 传的是 $request_uri（原请求的 URI，子请求里 $uri 会变成 /_ai_verify，
        # 实测踩到过）。它可能带查询串，这里剥掉 —— App 签名时签的也是不带查询串的路径。
        req_path = self.headers.get("X-DFWX-Path", "/ai/v1/chat/completions").split("?")[0]
        device = self.headers.get("X-DFWX-Device", "") or (self.client_address[0] if self.client_address else "?")

        def deny(reason):
            log("DENY device=%s reason=%s ts=%s" % (device, reason, ts_raw))
            if MODE == "enforce":
                self._reply(401, reason)
            else:
                # observe：记下来但放行，让问题先暴露在日志里
                self._reply(200, "observe:" + reason)

        key = read_sign_key()
        if not key:
            log("ALLOW device=%s reason=no-sign-key (fail-open，密钥文件缺失是部署问题)" % device)
            self._reply(200, "no-key")
            return

        try:
            ts = int(ts_raw)
        except ValueError:
            deny("bad-ts")
            return

        if abs(now - ts) > SKEW_SECONDS:
            deny("clock-skew")
            return

        if len(nonce) < 16:
            deny("bad-nonce")
            return

        payload = "%s\n%s\n%s\n%s" % (ts_raw, nonce, method, req_path)
        expect = hmac.new(key.encode("utf-8"), payload.encode("utf-8"), hashlib.sha256).hexdigest()
        if not hmac.compare_digest(expect, sig):
            deny("bad-signature")
            return

        with _lock:
            prune_nonces(now)
            if nonce in _seen_nonces:
                deny("replayed-nonce")
                return
            _seen_nonces[nonce] = now + SKEW_SECONDS
            used = bump_quota(device)

        if used > DAILY_LIMIT:
            deny("daily-quota-exceeded(%d/%d)" % (used, DAILY_LIMIT))
            return

        self._reply(200, "ok")


def main():
    load_quota()
    log("guard started mode=%s port=%d daily=%d skew=%ds keyfile=%s"
        % (MODE, PORT, DAILY_LIMIT, SKEW_SECONDS, KEY_FILE))
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    server.daemon_threads = True
    server.serve_forever()


if __name__ == "__main__":
    main()
