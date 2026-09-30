#!/usr/bin/env python3
"""《东方无限》安装包上传服务（仅监听 127.0.0.1，由 nginx 代理并做 basic auth）。

为什么不让 nginx 直接用 dav 模块：本机 nginx 虽然编了 --with-http_dav_module，
但 dav_methods 在这个 location 上不生效（PUT 一律被静态模块拦成 405，而同一 location
的 basic auth 与 GET 都正常）。与其继续和它纠缠，不如用一个十几行、行为完全可控的服务。
"""
import hashlib, json, os, re, tempfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

APK_DIR = "/var/www/dfwx/apk"
NAME_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,120}$")
MAX_BYTES = 300 * 1024 * 1024


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "dfwx-upload/1"

    def _json(self, code, payload):
        body = json.dumps(payload, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        self._json(405, {"ok": False, "error": "只接受 PUT"})

    def do_PUT(self):
        name = os.path.basename(self.path.split("?")[0].lstrip("/"))
        if not NAME_RE.match(name):
            self._json(400, {"ok": False, "error": "文件名不合法"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            length = 0
        if length <= 0 or length > MAX_BYTES:
            self._json(400, {"ok": False, "error": "大小不合法（%d 字节）" % length})
            return

        os.makedirs(APK_DIR, exist_ok=True)
        digest = hashlib.sha256()
        written = 0
        tmp = tempfile.NamedTemporaryFile(dir=APK_DIR, prefix=".upload-", delete=False)
        try:
            remaining = length
            while remaining > 0:
                chunk = self.rfile.read(min(1 << 20, remaining))
                if not chunk:
                    break
                tmp.write(chunk)
                digest.update(chunk)
                written += len(chunk)
                remaining -= len(chunk)
            tmp.close()
            if written != length:
                raise IOError("收到 %d 字节，声明 %d" % (written, length))
            target = os.path.join(APK_DIR, name)
            os.replace(tmp.name, target)
            os.chmod(target, 0o644)
            self._json(200, {
                "ok": True, "name": name, "size": written,
                "sha256": digest.hexdigest(),
                "url": "http://39.106.33.135/apk/" + name,
            })
        except Exception as error:                      # noqa: BLE001
            try:
                os.unlink(tmp.name)
            except OSError:
                pass
            self._json(500, {"ok": False, "error": str(error)})

    def log_message(self, fmt, *args):
        print("%s - %s" % (self.address_string(), fmt % args), flush=True)


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 8091), Handler).serve_forever()
