#!/usr/bin/env python3
"""dfwx-upload 自测：上传 / 列目录 / 单文件详情 / 删除 / 防路径穿越。

跑法（本机，不碰生产目录）：
    python3 server_test.py

它会在临时目录里起一个真的 HTTP 服务，用真的请求打它 —— 不是 mock。
所以「能跑通」等于「接口真的能用」。
"""
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
PORT = 18091
BASE = "http://127.0.0.1:%d" % PORT

failures = []


def check(label, ok, detail=""):
    print("  %s %s%s" % ("✅" if ok else "❌", label, "" if ok or not detail else "  —— " + detail))
    if not ok:
        failures.append(label)


def request(method, path, data=None, headers=None):
    url = BASE + path
    req = urllib.request.Request(url, data=data, method=method)
    for key, value in (headers or {}).items():
        req.add_header(key, value)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as error:
        body = error.read().decode()
        try:
            return error.code, json.loads(body)
        except ValueError:
            return error.code, {"raw": body}


def main():
    root = tempfile.mkdtemp(prefix="dfwx-upload-test-")
    apk_dir = os.path.join(root, "apk")
    meta_dir = os.path.join(root, "meta")
    os.makedirs(apk_dir)

    env = dict(os.environ, DFWX_APK_DIR=apk_dir, DFWX_META_DIR=meta_dir,
               DFWX_PORT=str(PORT))
    proc = subprocess.Popen(
        [sys.executable, os.path.join(HERE, "server.py")],
        env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    # 等端口起来
    for _ in range(50):
        try:
            urllib.request.urlopen(BASE + "/", timeout=1)
            break
        except urllib.error.HTTPError:
            break
        except OSError:
            time.sleep(0.1)

    try:
        payload = b"PK\x03\x04" + os.urandom(3000)
        digest = hashlib.sha256(payload).hexdigest()

        print("【1】上传（PUT）")
        status, body = request("PUT", "/%E4%B8%9C%E6%96%B9%E6%97%A0%E9%99%90-v1.0.0.apk",
                               data=payload, headers={"Content-Length": str(len(payload))})
        check("中文文件名上传成功", status == 200 and body.get("ok"), json.dumps(body)[:120])
        check("服务端返回的 sha256 正确", body.get("sha256") == digest)
        check("服务端返回的 size 正确", body.get("size") == len(payload))
        check("服务端返回可下载 url", isinstance(body.get("url"), str) and body["url"].startswith("http"))

        print()
        print("【2】列目录（GET /）")
        status, body = request("GET", "/")
        check("列目录成功", status == 200 and body.get("ok"), json.dumps(body)[:120])
        check("列出 1 个文件", body.get("count") == 1, str(body.get("count")))
        check("总大小正确", body.get("totalBytes") == len(payload))
        one = (body.get("files") or [{}])[0]
        check("列表里带 sha256（上传时记下来的，不是现算）", one.get("sha256") == digest)
        check("列表里带 mtime", isinstance(one.get("mtime"), int) and one["mtime"] > 0)
        check("列表里带 uploadedAt", isinstance(one.get("uploadedAt"), int))

        print()
        print("【3】上传中的临时文件不进列表")
        open(os.path.join(apk_dir, ".upload-abc123"), "wb").write(b"x" * 10)
        _, body = request("GET", "/")
        check("隐藏的 .upload- 临时文件被跳过", body.get("count") == 1, str(body.get("count")))
        os.unlink(os.path.join(apk_dir, ".upload-abc123"))

        print()
        print("【4】单文件详情（GET /<名字>）")
        name_q = urllib.parse.quote("东方无限-v1.0.0.apk")
        status, body = request("GET", "/" + name_q)
        check("详情成功", status == 200 and body.get("ok"))
        check("详情里 sha256 正确", body["file"]["sha256"] == digest)
        check("详情 404 对不存在的文件生效", request("GET", "/nope.apk")[0] == 404)

        print()
        print("【5】缺元数据时能现算（模拟升级前就存在的老文件）")
        old = os.path.join(apk_dir, "old-file.apk")
        old_payload = b"OLD" + os.urandom(2000)
        # 直接写进目录、不经过 PUT —— 这正是「网盘上线前就已存在的文件」的样子：
        # 没有 meta json，所以列表里算不出哈希。这里验证它不会让接口挂掉。
        open(old, "wb").write(old_payload)
        _, body = request("GET", "/")
        old_entry = [f for f in body["files"] if f["name"] == "old-file.apk"][0]
        check("列表里老文件的 sha256 是 None（不现算，省 IO）", old_entry["sha256"] is None)
        _, body = request("GET", "/old-file.apk")
        check("单独打开时会现算并返回正确哈希",
              body["file"]["sha256"] == hashlib.sha256(old_payload).hexdigest())
        _, body = request("GET", "/")
        old_entry = [f for f in body["files"] if f["name"] == "old-file.apk"][0]
        check("现算过的哈希被缓存下来（下次列表就有）", old_entry["sha256"] is not None)

        print()
        print("【6】防路径穿越")
        for evil in ["/../server.py", "/..%2Fserver.py", "/%2E%2E%2Fserver.py"]:
            code, _ = request("GET", evil)
            check("GET %s 被拒" % evil, code in (400, 404), "code=%s" % code)
        code, _ = request("DELETE", "/..%2Fserver.py")
        check("DELETE 路径穿越被拒", code in (400, 404), "code=%s" % code)

        print()
        print("【7】删除（DELETE）")
        status, body = request("DELETE", "/" + name_q)
        check("删除成功", status == 200 and body.get("deleted") == "东方无限-v1.0.0.apk",
              json.dumps(body, ensure_ascii=False)[:120])
        check("文件真的没了", not os.path.exists(os.path.join(apk_dir, "东方无限-v1.0.0.apk")))
        check("元数据也一起删了", not os.path.exists(os.path.join(meta_dir, "东方无限-v1.0.0.apk.json")))
        check("删不存在的返回 404", request("DELETE", "/nope.apk")[0] == 404)
        _, body = request("GET", "/")
        check("删完列表只剩 1 个", body.get("count") == 1, str(body.get("count")))

        print()
        print("【8】回归：上传仍然返回包内版本号字段")
        plain = b"not-a-real-apk"
        status, body = request("PUT", "/plain.apk", data=plain,
                               headers={"Content-Length": str(len(plain))})
        check("非 APK 内容也能上传（读不出版本号但不失败）", status == 200 and body.get("ok"),
              json.dumps(body, ensure_ascii=False)[:120])
        check("versionCode/versionName 字段存在（值可为 None）",
              "versionCode" in body and "versionName" in body)

        print()
        print("【9】坏输入")
        check("空大小被拒", request("PUT", "/empty.apk", data=b"",
                                    headers={"Content-Length": "0"})[0] == 400)
        check("非法文件名被拒（首字符是点）", request("PUT", "/.hidden", data=b"x",
                                                     headers={"Content-Length": "1"})[0] == 400)
    finally:
        proc.terminate()
        proc.wait(timeout=10)
        shutil.rmtree(root, ignore_errors=True)

    check_stalled_upload_does_not_hang()

    print()
    if failures:
        print("❌ %d 项未通过：%s" % (len(failures), "; ".join(failures)))
        return 1
    print("✅ 全部通过")
    return 0


def check_stalled_upload_does_not_hang():
    """【10】少发字节的客户端不许把服务端线程永久占住。

    这是实测踩到的真 bug：自测里少发一个字节，服务端就卡在 rfile.read() 里不出来。
    修法是给 Handler 加单次读超时。这里用一个**很短**的超时（2 秒）单独起一个服务，
    验证三件事：卡住的连接会在超时后被放弃、服务没崩、后续请求照常可用。
    """
    print()
    print("【10】客户端少发字节（真实踩到的挂死 bug）")
    root = tempfile.mkdtemp(prefix="dfwx-upload-stall-")
    port = PORT + 1
    env = dict(os.environ, DFWX_APK_DIR=os.path.join(root, "apk"),
               DFWX_META_DIR=os.path.join(root, "meta"), DFWX_PORT=str(port),
               DFWX_READ_TIMEOUT="2")
    os.makedirs(env["DFWX_APK_DIR"])
    proc = subprocess.Popen([sys.executable, os.path.join(HERE, "server.py")],
                            env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        for _ in range(50):
            try:
                urllib.request.urlopen("http://127.0.0.1:%d/" % port, timeout=1)
                break
            except urllib.error.HTTPError:
                break
            except OSError:
                time.sleep(0.1)

        # 手写一个原始请求：声明 100 字节，只发 10 字节，然后**不关连接**死等。
        import socket
        sock = socket.create_connection(("127.0.0.1", port), timeout=30)
        sock.sendall(b"PUT /stalled.apk HTTP/1.1\r\nHost: x\r\nContent-Length: 100\r\n\r\n" + b"x" * 10)
        started = time.time()
        sock.settimeout(20)
        try:
            data = sock.recv(4096)
        except (socket.timeout, ConnectionResetError):
            data = b""
        waited = time.time() - started
        sock.close()
        check("卡住的连接在超时后被服务端放弃（没有永久挂住）", waited < 15, "等了 %.1f 秒" % waited)
        check("服务端给了回应或直接断开（不是干等）", waited < 15)

        # 服务必须还活着
        try:
            with urllib.request.urlopen("http://127.0.0.1:%d/" % port, timeout=5) as resp:
                alive = json.loads(resp.read().decode()).get("ok") is True
        except Exception:                                # noqa: BLE001
            alive = False
        check("卡死之后服务仍然可用（没崩、没被线程占满）", alive)
    finally:
        proc.terminate()
        proc.wait(timeout=10)
        shutil.rmtree(root, ignore_errors=True)


if __name__ == "__main__":
    sys.exit(main())
