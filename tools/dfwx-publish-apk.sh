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

LOCAL_SHA="$(shasum -a 256 "$APK" | cut -d' ' -f1)"
SIZE="$(stat -f%z "$APK")"
NAME="dongfang-wuxian-v${VER}.apk"

echo "版本 $VER / versionCode $CODE / $SIZE 字节"
echo "sha256 $LOCAL_SHA"

echo "→ 上传到服务器…"
scp -q "$APK" "dfwx:/tmp/${NAME}"
REMOTE_SHA="$(ssh dfwx "sudo mv /tmp/${NAME} /var/www/dfwx/apk/ && sudo chown www-data:www-data /var/www/dfwx/apk/${NAME} && sha256sum /var/www/dfwx/apk/${NAME} | cut -d' ' -f1")"
if [[ "$REMOTE_SHA" != "$LOCAL_SHA" ]]; then
  echo "❌ 服务器上的 sha256 与本机不一致（${REMOTE_SHA}）—— 中止，不写记录" >&2
  exit 1
fi
echo "✅ 服务器校验一致"

echo "→ 同步 release 记录…"
scp -q server/dfwx-pb/set-release.py dfwx:/tmp/set-release.py
ssh dfwx "sudo VER='${VER}' CODE='${CODE}' SHA='${LOCAL_SHA}' SIZE='${SIZE}' python3 /tmp/set-release.py; rm -f /tmp/set-release.py"

echo "→ 外网可达性检查…"
curl -s -o /dev/null -w "APK  http://39.106.33.135/apk/${NAME} -> %{http_code}\n" "http://39.106.33.135/apk/${NAME}"
echo "完成。"
