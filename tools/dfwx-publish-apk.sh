#!/usr/bin/env bash
# 把已构建好的 APK 推到自有服务器，并同步 PocketBase 的 release 记录。
#
# 为什么要有它：发版要动两处 —— 「APK 落到服务器」和「release 记录指向它」。
# 分开手工做，漏掉任何一步的后果都很隐蔽（DFW-82：只改了记录没换包 / 只换了包没改记录，
# 用户永远收不到更新）。这里把两件事绑成一次操作，并且**逐字节校验**。
#
# 用法：
#   bash tools/dfwx-publish-apk.sh <版本号 x.y.z> <apk 绝对路径>
#
# 注意：APK 路径由调用方传入，脚本里不写死任何本机目录。
#
# ─────────────────────────────────────────────────────────────────────────
# [DFW-115] 三道「静默失效」加固（审计：docs/plan/publish-chain-audit.md）
#
# ① 远程同步那步原来是 `python3 set-release.py; rm -f ...`。
#    ssh 的退出码 = 远程**最后一条命令**的退出码 = `rm -f` = 恒 0，
#    于是 set-release.py 失败（序号回退被拒 / 口令读不到 / PocketBase 5xx / 超时）
#    时本地的 `set -e` 不会触发，脚本照样打印「完成。」并退出 0。
#    实测复现：内层 python 返回 3，外层 ssh 返回 0。
#    **这不是假想**：docs/agents/lessons.md 十-4 记着它已经害过一次 ——
#    「APK 传上去了、release 记录没更新」，正是 DFW-82 的形态。
#    现在：透出真实退出码 **+ 回读记录断言**（退出码那条路已经被证伪过一次，不能只靠它）。
#
# ② 外网可达性检查原来只打印 %{http_code} 不判断，且最后一句是 `echo "完成。"`
#    → HTTP 404 也返回退出码 0。现在：断言 200，**并把包下回来重算 sha256**。
#    为什么要重算：HTTP 200 不等于内容完整 —— 审计当晚用 curl --max-time 截断，
#    拿到 200 却只有 23493953/36837310 字节，sha256 完全不同。
#
# ③ 原来完全相信命令行传进来的版本号，从不打开包看它是谁。
#    一份 v1.0.0 的包配上 VER=1.0.5 会让**所有检查通过**，服务器上出现
#    dongfang-wuxian-v1.0.5.apk 而装出来是 10000、记录里写着 10005
#    → App 判定"还有更新" → **更新死循环**（与 DFW-117 同源）。
#    现在：`aapt dump badging` 断言「包内 == 参数」。
# ─────────────────────────────────────────────────────────────────────────
set -euo pipefail

VER="${1:-}"
APK="${2:-}"
if [[ -z "$VER" || -z "$APK" ]]; then
  echo "用法: bash tools/dfwx-publish-apk.sh <版本号 x.y.z> <apk 绝对路径>" >&2
  exit 2
fi
[[ -f "$APK" ]] || { echo "找不到 APK：$APK" >&2; exit 2; }
[[ "$VER" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "版本号必须是 x.y.z：$VER" >&2; exit 2; }

MAJOR="${VER%%.*}"
REST="${VER#*.}"
MINOR="${REST%%.*}"
PATCH="${REST#*.}"
CODE=$(( MAJOR * 10000 + MINOR * 100 + PATCH ))

# >>> DFWX-PROBE:assert_apk_identity
# [DFW-115 ③] 断言「包内真实版本」==「命令行参数」。
#
# 这一整块可以被反向探针单独抽出来跑（命令写在 docs/plan/publish-chain-audit.md）：
#   awk '/^# >>> DFWX-PROBE:assert_apk_identity$/,/^# <<< DFWX-PROBE:assert_apk_identity$/' \
#     tools/dfwx-publish-apk.sh > /tmp/probe_identity.sh
#   ( source /tmp/probe_identity.sh; assert_apk_identity <apk> 1.0.5 10005 ); echo $?

# 找 aapt（与 tools/release.sh 的 aapt_bin 同款：PATH 优先，其次 ANDROID_HOME）
aapt_bin() {
  if command -v aapt >/dev/null 2>&1; then command -v aapt; return 0; fi
  local bt
  bt="$(ls -d "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"/build-tools/*/ 2>/dev/null | sort -V | tail -1 || true)"
  if [[ -z "$bt" ]]; then
    echo "找不到 aapt（需要 ANDROID_HOME，或 ~/Library/Android/sdk/build-tools）" >&2
    return 1
  fi
  echo "${bt}aapt"
}

assert_apk_identity() {
  local apk="$1" want_name="$2" want_code="$3"
  local aapt badging real_name real_code
  aapt="$(aapt_bin)" || return 1
  badging="$("$aapt" dump badging "$apk" 2>/dev/null || true)"
  if [[ -z "$badging" ]]; then
    echo "❌ aapt 读不出这个包的信息（不是合法 APK？）：$apk" >&2
    return 1
  fi
  real_name="$(sed -n "s/^package:.*versionName='\([^']*\)'.*/\1/p" <<<"$badging" | head -1)"
  real_code="$(sed -n "s/^package:.*versionCode='\([^']*\)'.*/\1/p" <<<"$badging" | head -1)"
  if [[ -z "$real_name" || -z "$real_code" ]]; then
    echo "❌ 读不出包内 versionName/versionCode：$apk" >&2
    return 1
  fi
  if [[ "$real_name" != "$want_name" ]]; then
    echo "❌ 包的版本号与参数不一致：包内 versionName=${real_name}，参数是 $want_name" >&2
    echo "   拒绝发布 —— 把旧包当新版本发出去，会让用户陷入『装完还提示更新』的死循环。" >&2
    return 1
  fi
  if [[ "$real_code" != "$want_code" ]]; then
    echo "❌ 包的版本序号与参数不一致：包内 versionCode=${real_code}，由 $want_name 推导应为 $want_code" >&2
    return 1
  fi
  echo "✅ 包内版本核对一致：versionName=$real_name versionCode=$real_code"
  return 0
}
# <<< DFWX-PROBE:assert_apk_identity

LOCAL_SHA="$(shasum -a 256 "$APK" | cut -d' ' -f1)"
SIZE="$(stat -f%z "$APK")"
NAME="dongfang-wuxian-v${VER}.apk"

echo "版本 $VER / versionCode $CODE / $SIZE 字节"
echo "sha256 $LOCAL_SHA"

echo "→ 核对包内真实版本号（不信命令行参数，信包本身）…"
assert_apk_identity "$APK" "$VER" "$CODE"

echo "→ 上传到服务器…"
scp -q "$APK" "dfwx:/tmp/${NAME}"
REMOTE_SHA="$(ssh dfwx "sudo mv /tmp/${NAME} /var/www/dfwx/apk/ && sudo chown www-data:www-data /var/www/dfwx/apk/${NAME} && sha256sum /var/www/dfwx/apk/${NAME} | cut -d' ' -f1")"
if [[ "$REMOTE_SHA" != "$LOCAL_SHA" ]]; then
  echo "❌ 服务器上的 sha256 与本机不一致（${REMOTE_SHA}）—— 中止，不写记录" >&2
  exit 1
fi
echo "✅ 服务器校验一致"

# >>> DFWX-PROBE:sync_release_record
# [DFW-115 ①] 同步 release 记录，并**断言它真的同步成功了**。
#
# 这一整块可以被反向探针单独抽出来跑：
#   awk '/^# >>> DFWX-PROBE:sync_release_record$/,/^# <<< DFWX-PROBE:sync_release_record$/' \
#     tools/dfwx-publish-apk.sh > /tmp/probe_sync.sh
#   ( source /tmp/probe_sync.sh; sync_release_record 0.9.9 9999 <sha> 123 0 ); echo $?

# 透出 set-release.py 的真实退出码。
# 清理和退出码两件事都要，所以不能写成 `cmd && rm`（失败时不清临时文件），
# 也不能写成 `cmd; rm`（退出码被 rm 顶成 0 —— 那正是原来那个 bug）。
sync_release_record() {
  local ver="$1" code="$2" sha="$3" size="$4" allow_downgrade="$5"
  local rc=0
  ssh dfwx "sudo ALLOW_DOWNGRADE='${allow_downgrade}' VER='${ver}' CODE='${code}' SHA='${sha}' SIZE='${size}' python3 /tmp/set-release.py; rc=\$?; rm -f /tmp/set-release.py; exit \$rc" || rc=$?
  if [[ "$rc" -ne 0 ]]; then
    echo "❌ set-release.py 失败（退出码 ${rc}）—— release 记录**没有**同步，用户收不到更新" >&2
    return "$rc"
  fi
  echo "✅ release 记录同步命令返回 0"
  return 0
}

# 回读断言：**不依赖任何退出码**，直接把 release 记录读回来比对。
# 退出码那条路已经被证伪过一次（被 `;` 顶掉），所以这道保险必须独立存在。
verify_release_record() {
  local want_code="$1" want_sha="$2"
  local json real_code real_sha
  json="$(ssh dfwx "curl -s 'http://127.0.0.1:8090/api/collections/release/records?perPage=1&sort=-id'" || true)"
  real_code="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["items"][0]["versionCode"])' <<<"$json" 2>/dev/null || true)"
  real_sha="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["items"][0]["sha256"])' <<<"$json" 2>/dev/null || true)"
  if [[ "$real_code" != "$want_code" ]]; then
    echo "❌ 回读失败：release 记录的 versionCode 是「${real_code:-读不到}」，本次要发 $want_code" >&2
    return 1
  fi
  if [[ "$real_sha" != "$want_sha" ]]; then
    echo "❌ 回读失败：release 记录的 sha256 是「${real_sha:-读不到}」，本次要发 $want_sha" >&2
    return 1
  fi
  echo "✅ 回读确认：release 记录已指向 versionCode=${real_code}，且 sha256 一致"
  return 0
}
# <<< DFWX-PROBE:sync_release_record

# >>> DFWX-PROBE:verify_public_apk
# [DFW-115 ②] 外网可达性：断言 200，并且把包**下回来重算 sha256**。
#
# 这一整块可以被反向探针单独抽出来跑：
#   awk '/^# >>> DFWX-PROBE:verify_public_apk$/,/^# <<< DFWX-PROBE:verify_public_apk$/' \
#     tools/dfwx-publish-apk.sh > /tmp/probe_public.sh
#   ( source /tmp/probe_public.sh; verify_public_apk 不存在.apk <sha> 1 ); echo $?
verify_public_apk() {
  local name="$1" want_sha="$2" want_size="$3"
  local url="https://39.106.33.135/apk/${name}"
  local code
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 30 "$url" || true)"
  if [[ "$code" != "200" ]]; then
    echo "❌ 外网取不到 ${name}（HTTP ${code:-无响应}）：$url" >&2
    return 1
  fi
  local tmp got_sha got_size dl_code
  tmp="$(mktemp)"
  dl_code="$(curl -s -o "$tmp" -w '%{http_code}' --max-time 300 "$url" || true)"
  got_sha="$(shasum -a 256 "$tmp" | cut -d' ' -f1)"
  got_size="$(stat -f%z "$tmp" 2>/dev/null || stat -c%s "$tmp")"
  if [[ "$dl_code" != "200" || "$got_size" != "$want_size" || "$got_sha" != "$want_sha" ]]; then
    echo "❌ 外网下载回来的内容与本地不一致：HTTP ${dl_code} 大小 ${got_size}（期望 ${want_size}）sha256 ${got_sha}（期望 ${want_sha}）" >&2
    rm -f "$tmp"
    return 1
  fi
  rm -f "$tmp"
  echo "✅ 外网下载并重算一致：${got_size} 字节 / ${got_sha}"
  return 0
}
# <<< DFWX-PROBE:verify_public_apk

echo "→ 同步 release 记录…"
# [DFW-118 路线 B 过渡] ALLOW_DOWNGRADE=1 才放行"序号回退"这一次（换编号方案专用）。
# 默认不带，回退仍会被 set-release.py 拒绝 —— 那是对的，回退会让用户收不到更新。
scp -q server/dfwx-pb/set-release.py dfwx:/tmp/set-release.py
sync_release_record "$VER" "$CODE" "$LOCAL_SHA" "$SIZE" "${ALLOW_DOWNGRADE:-0}"
verify_release_record "$CODE" "$LOCAL_SHA"

echo "→ 外网可达性检查（下载回来重算，不只看 200）…"
verify_public_apk "$NAME" "$LOCAL_SHA" "$SIZE"

echo "完成。"
