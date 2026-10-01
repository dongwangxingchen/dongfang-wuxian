#!/usr/bin/env python3
"""
[DFW-86 后续] release 记录的 versionCode 对账器。

## 为什么需要它
控制台页面旧版**没有 versionCode 输入框**，保存时把旧值原样写回。用户的浏览器把那个旧页面
缓存死了（服务端补 no-store 也作废不了已缓存的响应），于是出现：
  版本号改成 1.0.6 → 记录里 versionName=1.0.6 但 versionCode 仍是 10005
  → App 比的是 versionCode（10005 > 10005 为假）→ 永远「已是最新」，用户收不到更新。

客户端修不了（用户拿不到新页面），所以服务端兜底。

## 为什么不用 PocketBase 的 pb_hooks（试过，不行）
在 `onRecordUpdateRequest` 里调 `$app.findRecordsByFilter` 查同一张表，
会让**整个更新变成静默空操作**：接口返回 HTTP 200、响应体为空、记录纹丝不动
（PocketBase 的 request 钩子跑在事务里，嵌套查同表出问题）。
实测：摘掉钩子，同样的 PATCH 立刻恢复正常。所以改用外部对账，不碰它的内部机制。

## 规则
只对 **App 实际会读的那一条**（`sort=-id` 的第一条）做对账：
  versionCode := max( 由 versionName 推导的值 , 其它记录的最大 versionCode )
- 由 `x.y.z` 推导：`major*10000 + minor*100 + patch`（与 app/build.gradle.kts 的编号规则一致）
- **只增不减**：绝不让 versionCode 回退
- 版本名解析不出来就**什么都不做**（宁可不改，也不要写坏）

幂等：值已经对了就不发请求。
"""
import json
import re
import sys
import urllib.request

PB = "http://127.0.0.1:8090"
PASS_FILE = "/root/.dfwx-pb-admin-pass"


def req(method, path, token=None, body=None):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(PB + path, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", token)
    with urllib.request.urlopen(r, timeout=20) as resp:
        raw = resp.read()
        return json.loads(raw) if raw else {}


def main():
    pw = open(PASS_FILE).read().strip()
    tok = req("POST", "/api/collections/_superusers/auth-with-password",
              body={"identity": "dongfang@dfwx.local", "password": pw})["token"]

    # App 用的就是这个查询（MainActivity 侧按 sort=-id 取第一条）
    latest = req("GET", "/api/collections/release/records?perPage=1&sort=-id", tok)["items"][0]

    name = str(latest.get("versionName") or "").strip()
    m = re.match(r"^(\d+)\.(\d+)\.(\d+)$", name)
    if not m:
        return 0  # 解析不了就不动

    from_name = int(m.group(1)) * 10000 + int(m.group(2)) * 100 + int(m.group(3))

    allrec = req("GET", "/api/collections/release/records?perPage=200", tok)["items"]
    others = [int(r.get("versionCode") or 0) for r in allrec if r["id"] != latest["id"]]
    prev_max = max(others) if others else 0

    want = max(from_name, prev_max)
    cur = int(latest.get("versionCode") or 0)

    if want > cur:
        req("PATCH", "/api/collections/release/records/" + latest["id"], tok, {"versionCode": want})
        print("versionCode %d -> %d (versionName=%s)" % (cur, want, name))
    return 0


if __name__ == "__main__":
    sys.exit(main())
