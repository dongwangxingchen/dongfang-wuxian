#!/usr/bin/env python3
"""dfwx-upload 端到端联调测试（打真实 HTTPS 入口，走完整 nginx + basic auth 链路）。

和 server_test.py 的分工：
  - server_test.py 在本地临时目录起一个真服务，测**接口本身**（快、可离线、进 CI）
  - 本脚本打**线上真实地址**，测**整条链路**（nginx 路由、basic auth、真实文件、真实哈希）

为什么要两个都留：本地全绿但线上挂掉的形态是真实存在的 ——
比如 nginx 只放行 PUT 不放行 GET/DELETE，本地测试根本发现不了。

凭据来源：仓库外的凭据文件（用户名与路径见 `DFWX_ADMIN_USER` / `DFWX_ADMIN_PASS_FILE`）。
**凭据只从本地文件读，不写进本脚本，也不打印。**

跑法：
    python3 e2e_test.py
"""
import base64
import json
import os
import re
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = "https://39.106.33.135/admin/apk-upload"
USER = os.environ.get("DFWX_ADMIN_USER", "")
SERVER_TXT = os.path.expanduser("<仓库外凭据文件>")
# 线上版本记录指向的那个包。这三个值是**硬事实**（见 release 集合），
# 网盘算出来的必须和它们一模一样，否则「自动填校验值」就是填错值。
LIVE = {
    "name": "dongfang-wuxian-v1.0.0.apk",
    "sha256": "e24feb77e14a0da639cdc727bf8cbbb6f8104bb6ae4704d482c00877dd732730",
    "size": 36840658,
    "versionName": "1.0.0",
    "versionCode": 10000,
}

fails = []


def check(label, ok, detail=""):
    print("  %s %s%s" % ("✅" if ok else "❌", label, "" if ok else "  —— " + str(detail)[:170]))
    if not ok:
        fails.append(label)


def load_auth():
    if not os.path.exists(SERVER_TXT):
        print("找不到 %s，无法取凭据" % SERVER_TXT)
        sys.exit(2)
    text = open(SERVER_TXT, encoding="utf-8").read()
    match = re.search(r"^密码=(\S+)", text, re.M)
    if not match:
        print("凭据文件里没有 `密码=` 这一行")
        sys.exit(2)
    return "Basic " + base64.b64encode((USER + ":" + match.group(1)).encode()).decode()


AUTH = load_auth()
CTX = ssl.create_default_context()


def req(method, path="", data=None):
    request = urllib.request.Request(BASE + path, data=data, method=method)
    request.add_header("Authorization", AUTH)
    try:
        with urllib.request.urlopen(request, timeout=90, context=CTX) as response:
            return response.status, json.loads(response.read().decode())
    except urllib.error.HTTPError as error:
        body = error.read().decode()
        try:
            return error.code, json.loads(body)
        except ValueError:
            return error.code, {"raw": body[:120]}


def main():
    print("【1】认证确实在生效")
    anonymous = urllib.request.Request(BASE + "/", method="GET")
    try:
        urllib.request.urlopen(anonymous, timeout=30, context=CTX)
        check("无凭据被拒", False, "竟然成功了")
    except urllib.error.HTTPError as error:
        check("无凭据被拒 401", error.code == 401, error.code)
    code, _ = req("GET", "/")
    check("带正确凭据能访问", code == 200, "HTTP %s" % code)
    if code != 200:
        print("  凭据不对，后面没法继续")
        return 1

    print()
    print("【2】GET 列目录 —— 控制台「网盘」页加载时发的就是这个请求")
    code, data = req("GET", "/")
    check("列目录成功", code == 200 and data.get("ok"), data)
    check("文件数 > 0", (data.get("count") or 0) > 0, data.get("count"))
    check("每条都带 size / mtime",
          all(isinstance(f.get("size"), int) and isinstance(f.get("mtime"), int)
              for f in data["files"]))
    missing = [f["name"] for f in data["files"] if not f.get("sha256")]
    check("每条都带 sha256（老文件已补算）", not missing, missing[:3])
    print("     共 %d 个文件，合计 %.1f MB" % (data["count"], data["totalBytes"] / 1048576))

    print()
    print("【3】GET 单个文件 —— 点「填进版本记录」时发的请求")
    code, data = req("GET", "/" + urllib.parse.quote(LIVE["name"]))
    check("详情成功", code == 200 and data.get("ok"), data)
    one = data.get("file", {})
    check("sha256 与线上版本记录一致", one.get("sha256") == LIVE["sha256"], one.get("sha256"))
    check("size 与线上版本记录一致", one.get("size") == LIVE["size"], one.get("size"))
    check("包内版本名解析正确", one.get("versionName") == LIVE["versionName"], one.get("versionName"))
    check("包内版本序号解析正确", one.get("versionCode") == LIVE["versionCode"], one.get("versionCode"))

    print()
    print("【4】中文文件名的 URL 编码不炸")
    code, _ = req("GET", "/" + urllib.parse.quote("东方寻界-1.0.apk"))
    check("中文名请求不返回 5xx", code < 500, code)

    print()
    print("【5】防路径穿越（走公网 nginx 入口）")
    # 400/404 = 服务端拒了；405 = nginx 把 ..%2F 规范化到别的 location、
    # 那个 location 不允许 DELETE/GET 所以直接挡掉 —— 同样是「没到服务端」，也是安全结果。
    for evil in ["/../server.py", "/..%2Fserver.py", "/%2E%2E%2Fserver.py", "/....//server.py"]:
        code, _ = req("GET", evil)
        check("GET %-20s 被挡" % evil, code in (400, 404, 405), "HTTP %d" % code)
    code, _ = req("DELETE", "/..%2Fserver.py")
    check("DELETE 路径穿越被挡", code in (400, 404, 405), code)

    print()
    print("【6】删除保护")
    code, _ = req("DELETE", "/definitely-not-here-xyz.apk")
    check("删不存在的返回 404（不误删）", code == 404, code)

    print()
    print("【7】回归：上传路由与校验仍然在")
    code, _ = req("PUT", "/_probe_.apk", data=b"")
    check("0 字节上传被拒 400（PUT 路由在、校验在）", code == 400, code)

    print()
    if fails:
        print("❌ %d 项未通过：%s" % (len(fails), "; ".join(fails)))
        return 1
    print("✅ 端到端全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
