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
- **口令永不打印**（只从凭据文件读；路径见 `DFWX_PB_PASS_FILE` 环境变量）
- 版本序号**只增不减**：传进来的值比记录里小就拒绝，避免把用户锁在"已是最新"
- `apkUrl` 用 **http**（[2026-10-03 用户拍板]）

  ⚠️ 这一条**来回翻过三次**，把最终依据记全，别再翻案：

  | 时间 | 结论 | 依据 |
  |---|---|---|
  | 早期 | http | 注释写的，但**代码从 `e989868` 起就是 https**，注释和代码矛盾了几个月 |
  | 10-03 白天 | https | 真机实测**确认 https 能下**：那次「更新包校验未通过」是**下载成功之后**的校验环节报的，说明 TLS 这一关过了 |
  | 10-03 晚 | **http（定案）** | 见下 |

  **为什么最终选 http —— 不是因为 https 不能下，而是因为它有一个会过期的东西：**

  那张证书是 Let's Encrypt 签的 **IP 证书**（SAN 是 `IP Address:39.106.33.135`，不是域名），
  实测有效期**只有 6 天**（域名证书是 90 天）。它必须一天不停地自动续期，
  **一天没续上，用户就下不了更新**。http 不需要证书，没有「到期」这回事。

  而且 https 在这个场景下基本是白挂的：**软件读「版本信息 + 校验值」那一步走的是
  `http://39.106.33.135/pb`**（`RemoteConfigClient.BASE`，本来就是明文）。
  能篡改下载地址的人，同时就能把校验值一起改掉。真正拦住坏包的是**签名校验**，与传输层无关。

  **代价是零**：`UpdateClient.isAllowedDownloadUrl`（`UpdateClient.java:203-212`）
  对自有服务器 **http 与 https 都收**，所以切换**不需要重新出包**，
  已经装在用户手机上的旧版照样能下。
"""
import json
import os
import sys
import urllib.request

PB = "http://127.0.0.1:8090"
# 凭据文件路径与管理员账号都从环境变量读，默认值只是本机部署时的常见位置。
# 写死在脚本里等于把「密码放在哪、账号叫什么」一起公开 —— 公开仓库里不该有这些。
PASS_FILE = os.environ.get("DFWX_PB_PASS_FILE", "")
if not PASS_FILE:
    sys.exit("请先设置 DFWX_PB_PASS_FILE 指向存放后台口令的文件（权限应为 600）")
IDENTITY = os.environ.get("DFWX_PB_IDENTITY", "")
# 安装包对外的地址前缀。**http，不是 https** —— 完整理由见文件头「铁律」第二条。
# 这里抽成常量是为了**能被跨文件检查盯住**：`server/dfwx-admin/pan_render_test.mjs`
# 会把它和控制台里的 `APK_ORIGIN`、上传服务返回的 `"url"` 三处放在一起比，
# 不一致就红。当初就是这几处漂移了（控制台 https、服务端 http），谁都没发现。
APK_ORIGIN = "http://39.106.33.135"


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
    # [DFW-115] 注意这里比较用的是 `<`，**相等（code == current）是刻意放行的，不要改成 `<=`。**
    #
    # 原因：测试期会用不同的 GitHub tag 重复发**同一个 versionCode**
    # （tag 是 test-YYYYMMDD-N，版本号一直是 1.0.0 / 10000），改成 `<=` 会把这条工作流直接堵死。
    #
    # 与 App 的状态无关：App 比的是「服务器记录 vs 手机已装」，两条都是 10000 时它判定
    # "已是最新版本" —— 这正是想要的（用户装的确实是同一版）。
    #
    # [2026-10-03 更正] 这一段原来写的是「与控制台的差异不矛盾：控制台的『相等硬拦』
    # （index.html 的 guardVersionTransition，publish 模式）……」
    # **那个函数已经被删掉了**（用户 2026-10-03 要求"别拦我，我自己填"），
    # 现在控制台对"相等"和"变小"都**只警告、不拦**（见 index.html 的 versionTransitionWarning）。
    #
    # 所以两条路现在的差异是**反过来的**：
    #   · CLI（本文件）：相等放行、**变小硬拒**（除 ALLOW_DOWNGRADE=1）；
    #   · 控制台：相等和变小都只给一句警告，点了就发。
    #
    # 这个不对称是**有意留下的**：用户明确要求控制台别拦他（他要自己测后台推送能力），
    # 而 CLI 是发版流程、本来就该更严。但它意味着**网页上可以静默发出降级版本** ——
    # 这条风险已登记在 `docs/plan/publish-chain-audit.md`。
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
        "apkUrl": APK_ORIGIN + "/apk/dongfang-wuxian-v%s.apk" % ver,
        "sha256": sha,
        "size": size,
    }
    out = req("PATCH", "/api/collections/release/records/" + latest["id"], token, body)
    print("release 记录已同步：%s / %s / %s"
          % (out.get("versionName"), out.get("versionCode"), out.get("apkUrl")))

    snapshot(token, out)
    return 0


def snapshot(token, rec):
    """[DFW-106] 往 `release_history` 追一条，让控制台的「发布历史」也能看到命令行发的版。

    ## 为什么历史不放在 release 集合里
    `release` 的 id 是 PocketBase 随机生成的 `[a-z0-9]{15}`，**字符串排序与创建时间无关**。
    实测：按 1→2→3 的顺序插入三条记录，App 用的 `perPage=1&sort=-id` 返回的第一条是**最旧的 1 号**。
    也就是说 `release` 一旦出现第二条记录，App 就可能读到随机一条 —— 那正是 DFW-82
    那个「改了版本号但用户收不到更新」的静默故障。所以 `release` 永远只保留一条（当前版本），
    历史另存一个集合。

    ## 失败不许影响发版
    这条只是日志。集合还没建、网络抖了、字段校验没过 —— 一律只打一行警告，
    **绝不改变退出码**：APK 已经传上去、release 记录也已经同步好了，那才是要紧的事。
    """
    try:
        req("POST", "/api/collections/release_history/records", token, {
            "versionName": rec.get("versionName") or "",
            "versionCode": int(rec.get("versionCode") or 0),
            "apkUrl": rec.get("apkUrl") or "",
            "sha256": rec.get("sha256") or "",
            "size": int(rec.get("size") or 0),
            # updateMode 这次没改，用 PATCH 回来的当前值，保证历史里的这一条是完整的
            "updateMode": rec.get("updateMode") or "soft",
            "action": "cli",
            "note": "命令行发布（tools/dfwx-publish-apk.sh）",
        })
        print("发布历史已记录一条（action=cli）")
    except Exception as error:                       # noqa: BLE001
        print("提示：发布历史没记上（不影响本次发版）：%s" % error)


if __name__ == "__main__":
    sys.exit(main())
