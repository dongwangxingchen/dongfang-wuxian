#!/usr/bin/env python3
"""把 PocketBase 的 `release` 记录同步到指定版本。

## 为什么单独有这个脚本
App 的更新源就是 `release` 集合里 `sort=-id` 的第一条（见 `RemoteConfigClient.BASE`）。
发版时如果只传了 APK、没改这条记录，用户就永远收不到更新 —— DFW-82 踩过一次。
所以「传包」和「改记录」必须**一起做、且校验一致**，由 `tools/dfwx-publish-apk.sh` 串起来。

## 用法（在服务器上，需要 root 读管理员口令）
```
sudo VER=1.0.22 CODE=10022 SHA=<64位十六进制> SIZE=36824453 python3 set-release.py
```

## 铁律
- **口令永不打印**（只从 `<凭据文件>` 读）
- 版本序号**只增不减**：传进来的值比记录里小就拒绝，避免把用户锁在"已是最新"
- `apkUrl` 前缀**故意是 http**（https 会因证书不被设备信任而下载失败）
"""
import json
import os
import sys
import urllib.request

PB = "http://127.0.0.1:8090"
PASS_FILE = "<凭据文件>"
IDENTITY = "<管理员账号>"


def req(method, path, token=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(PB + path, data=data, method=method)
    request.add_header("Content-Type", "application/json")
    if token:
        request.add_header("Authorization", token)
    with urllib.request.urlopen(request, timeout=20) as response:
        raw = response.read()
        return json.loads(raw) if raw else {}


def main():
    ver = os.environ.get("VER", "").strip()
    code = int(os.environ.get("CODE", "0"))
    sha = os.environ.get("SHA", "").strip().lower()
    size = int(os.environ.get("SIZE", "0"))
    if not ver or code <= 0 or len(sha) != 64 or size <= 0:
        print("参数不完整：需要 VER / CODE / SHA(64位) / SIZE")
        return 2

    password = open(PASS_FILE).read().strip()
    token = req("POST", "/api/collections/_superusers/auth-with-password",
                body={"identity": IDENTITY, "password": password})["token"]

    latest = req("GET", "/api/collections/release/records?perPage=1&sort=-id", token)["items"][0]
    current = int(latest.get("versionCode") or 0)
    if code < current:
        # [DFW-118 路线 B 过渡] 默认仍然拒绝回退 —— 这是对的，回退会让用户收不到更新。
        # 但**换版本编号方案那一次必须放行**：手机上是旧计数器的 10034，
        # 新方案 1.0.0 算出来是 10000。堵死就只能去手改 PocketBase，那更危险。
        # 所以留一个显式开关，并且**把后果打在日志里**（不静默）。
        if os.environ.get("ALLOW_DOWNGRADE") != "1":
            print("拒绝：版本序号回退（记录 %d，本次 %d）" % (current, code))
            print("如果这是「换了版本编号方案」的一次性过渡，加 ALLOW_DOWNGRADE=1 重跑。")
            return 3
        print("⚠️ 允许序号回退（ALLOW_DOWNGRADE=1）：记录 %d -> 本次 %d" % (current, code))
        print("⚠️ 后果：装了旧版的用户收不到更新提示，必须手动卸载重装一次。")

    body = {
        "versionName": ver,
        "versionCode": code,
        "apkUrl": "http://39.106.33.135/apk/dongfang-wuxian-v%s.apk" % ver,
        "sha256": sha,
        "size": size,
    }
    out = req("PATCH", "/api/collections/release/records/" + latest["id"], token, body)
    print("release 记录已同步：%s / %s / %s"
          % (out.get("versionName"), out.get("versionCode"), out.get("apkUrl")))
    return 0


if __name__ == "__main__":
    sys.exit(main())
