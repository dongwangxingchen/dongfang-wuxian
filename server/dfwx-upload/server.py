#!/usr/bin/env python3
"""《东方无限》安装包上传服务（仅监听 127.0.0.1，由 nginx 代理并做 basic auth）。

为什么不让 nginx 直接用 dav 模块：本机 nginx 虽然编了 --with-http_dav_module，
但 dav_methods 在这个 location 上不生效（PUT 一律被静态模块拦成 405，而同一 location
的 basic auth 与 GET 都正常）。与其继续和它纠缠，不如用一个十几行、行为完全可控的服务。
"""
import hashlib, json, os, re, sys, tempfile, time
import urllib.parse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from axml import read_version
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

APK_DIR = os.environ.get("DFWX_APK_DIR", "/var/www/dfwx/apk")
# [DFW-134 2026-10-03] 上传时算好的元数据（sha256 / 大小 / 版本号）落在这里。
#
# 为什么不每次列目录都现算 sha256：站点目录现在有 30 个包、合计约 1 GB，
# 每打开一次「网盘」就把 1 GB 全读一遍 —— 这台机器只有 2 核、无 swap，
# 纯属自找麻烦。上传时本来就在流式算 sha256（见 do_PUT），顺手写下来即可。
#
# 为什么不放进 APK_DIR：那个目录是 nginx 直接对外提供下载的，
# 丢一堆 .json 进去会污染公开目录，也会混进网盘自己的文件列表。
META_DIR = os.environ.get("DFWX_META_DIR", "/opt/dfwx-upload/meta")
# [DFW-109 2026-10-02] 允许中文文件名。
# 用户上传「东方寻界-1.0.2.apk」被拒 400 —— 原白名单只允许 ASCII，
# 而中文 App 名是常态（用户要传的就不止东方无限一个包）。
# 仍然禁止 / 与开头是点（防路径穿越）：首字符必须是字母/数字/中文。
NAME_RE = re.compile(r"^[A-Za-z0-9一-鿿][A-Za-z0-9._\-一-鿿]{0,120}$")
MAX_BYTES = 300 * 1024 * 1024


def meta_path(name):
    return os.path.join(META_DIR, name + ".json")


def read_meta(name):
    """读上传时留下的元数据。读不到就返回空字典 —— 元数据缺失不该让接口失败。"""
    try:
        with open(meta_path(name), encoding="utf-8") as handle:
            value = json.load(handle)
        return value if isinstance(value, dict) else {}
    except (OSError, ValueError):
        return {}


def write_meta(name, value):
    """原子写元数据。写不进去只影响「列表里能不能显示哈希」，不该让上传本身失败。"""
    try:
        os.makedirs(META_DIR, exist_ok=True)
        tmp = meta_path(name) + ".tmp"
        with open(tmp, "w", encoding="utf-8") as handle:
            json.dump(value, handle, ensure_ascii=False)
        os.replace(tmp, meta_path(name))
    except OSError:
        pass


def hash_file(path):
    """流式算 sha256。按块读，300 MB 的包也不会把内存顶起来。"""
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        while True:
            chunk = handle.read(1 << 20)
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest()


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "dfwx-upload/1"
    # [DFW-134 2026-10-03] **单次 socket 读的超时**，实测缺了它会永久挂住线程。
    #
    # 怎么发现的：自测里故意少发一个字节（声明 Content-Length: 15，实际只发 14），
    # 服务端就卡在 self.rfile.read() 里**永远不出来** —— do_PUT 那段
    # `if not chunk: break` 只在连接被关掉时才触发，客户端不关连接就死等。
    # ThreadingHTTPServer 是「一个连接一个线程」，所以每个这种请求都会永久占一个线程。
    #
    # 这个超时是**每次 recv** 的上限，不是整个上传的总时长上限：
    # 300 MB 的包慢慢传也不会被掐，只有「连续 120 秒一个字节都不来」才算死连接。
    # 超时后 BaseHTTPRequestHandler 会记一条日志并关掉连接，不会崩服务。
    timeout = int(os.environ.get("DFWX_READ_TIMEOUT", "120"))

    def _json(self, code, payload):
        body = json.dumps(payload, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _describe(self, name, path, with_hash=False):
        """把一个文件说清楚：大小、上传时间、哈希、以及包内的版本号（如果是 APK）。

        [DFW-134] with_hash 默认关：列表页不为了显示哈希把整个目录读一遍。
        只有「单个文件详情」这条路才会现算，并顺手缓存进元数据。
        """
        stat = os.stat(path)
        meta = read_meta(name)
        digest = meta.get("sha256")
        if with_hash and not digest:
            digest = hash_file(path)
            meta["sha256"] = digest
            meta.setdefault("size", stat.st_size)
            meta.setdefault("uploadedAt", int(stat.st_mtime))
            write_meta(name, meta)
        return {
            "name": name,
            "size": stat.st_size,
            "mtime": int(stat.st_mtime),
            "sha256": digest,
            "versionCode": meta.get("versionCode"),
            "versionName": meta.get("versionName"),
            "uploadedAt": meta.get("uploadedAt"),
        }

    def do_GET(self):
        """[DFW-134] GET / 列目录；GET /<文件名> 看单个文件（缺哈希就现算）。"""
        raw = urllib.parse.unquote(self.path.split("?")[0])
        name = os.path.basename(raw.lstrip("/"))
        if not name:
            files = []
            try:
                entries = sorted(os.listdir(APK_DIR))
            except OSError:
                entries = []
            for entry in entries:
                # 跳过上传中的临时文件（.upload-xxx）和一切隐藏文件
                if entry.startswith("."):
                    continue
                path = os.path.join(APK_DIR, entry)
                if not os.path.isfile(path):
                    continue
                try:
                    files.append(self._describe(entry, path))
                except OSError:
                    continue
            files.sort(key=lambda item: item["mtime"], reverse=True)
            self._json(200, {
                "ok": True,
                "count": len(files),
                "totalBytes": sum(item["size"] for item in files),
                "files": files,
            })
            return
        if not NAME_RE.match(name):
            self._json(400, {"ok": False, "error": "文件名不合法"})
            return
        path = os.path.join(APK_DIR, name)
        if not os.path.isfile(path):
            self._json(404, {"ok": False, "error": "没有这个文件"})
            return
        try:
            self._json(200, {"ok": True, "file": self._describe(name, path, with_hash=True)})
        except OSError as error:
            self._json(500, {"ok": False, "error": str(error)})

    def do_DELETE(self):
        """[DFW-134] 删除一个文件（连同它的元数据）。防路径穿越同 do_PUT。"""
        raw = urllib.parse.unquote(self.path.split("?")[0])
        name = os.path.basename(raw.lstrip("/"))
        if not name or not NAME_RE.match(name):
            self._json(400, {"ok": False, "error": "文件名不合法"})
            return
        path = os.path.join(APK_DIR, name)
        # 再确认一次解析出来的路径确实在 APK_DIR 里（双保险，不只靠正则）
        if os.path.dirname(os.path.realpath(path)) != os.path.realpath(APK_DIR):
            self._json(400, {"ok": False, "error": "路径不合法"})
            return
        if not os.path.isfile(path):
            self._json(404, {"ok": False, "error": "没有这个文件"})
            return
        try:
            os.unlink(path)
        except OSError as error:
            self._json(500, {"ok": False, "error": str(error)})
            return
        try:
            os.unlink(meta_path(name))
        except OSError:
            pass
        self._json(200, {"ok": True, "deleted": name})

    def do_PUT(self):
        # [DFW-109 2026-10-02] **必须先 URL 解码。**
        # self.path 是**原始请求行**里的路径，中文在 HTTP 里是百分号编码
        # （「东方寻界-1.0.2.apk」实际是 %E4%B8%9C%E6%96%B9...），
        # 直接拿去匹配白名单只会匹配到 %E4 开头的乱码 → 用户看到 400「文件名不合法」。
        raw = urllib.parse.unquote(self.path.split("?")[0])
        name = os.path.basename(raw.lstrip("/"))
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
            # [DFW-117 2026-10-02] **读出安装包自己的 versionCode / versionName 一并返回。**
            #
            # 为什么必须这么做：用户上传 1.0.1 后手机收不到更新，根因是控制台那个
            # 「已经帮你填好现在 +1」算的是**上一条记录的 versionCode + 1**，
            # 它根本不知道用户手机上装的是哪一版 —— 上一条是 10027 就填 10028，
            # 而用户装的是 10034 → 10028 < 10034 → 软件判定「这更新比我还旧」，不弹窗。
            #
            # 正解：**上传的这个 APK 本身就是新版本**，它的 versionCode 才是真值。
            # 读不出来就返回 None，前端会提示用户手填 —— 绝不让上传因此失败。
            vcode, vname = read_version(target)
            # [DFW-134 2026-10-03] 把这次上传的哈希/大小/版本号顺手记下来。
            # 网盘列表页直接读它，不用为了显示哈希把 1 GB 目录整个读一遍。
            write_meta(name, {
                "sha256": digest.hexdigest(),
                "size": written,
                "versionCode": vcode,
                "versionName": vname,
                "uploadedAt": int(time.time()),
            })
            self._json(200, {
                "ok": True, "name": name, "size": written,
                "sha256": digest.hexdigest(),
                "url": "http://39.106.33.135/apk/" + name,
                "versionCode": vcode,
                "versionName": vname,
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
    # 端口可覆盖：自测要在一个临时端口上起一个真服务，不能占用生产端口。
    port = int(os.environ.get("DFWX_PORT", "8091"))
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
