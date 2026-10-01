#!/usr/bin/env bash
# 一处改版本号，所有地方跟着变。
#
# ## 为什么要有它
# 用户原话：「我主要是想减轻你的维护负担，**别忘记改某些地方的版本号**」。
# DFW-91 之后版本号已经只剩两个落点：
#   1. `app/build.gradle.kts` 的 `val buildCode`（唯一数字源，versionName 由它算出来）
#   2. `docs/plan/current-state.md` 的「- 版本号：…」那一行（接手者读的事实页）
#
# 但"两处"仍然是两处 —— 本次实际就漏过一次：改了 buildCode 没改事实页，
# 结果 `release.sh build` 跑第一遍被 `⑤ 工作区不干净` 拦下（verify 里的自动同步
# 把事实页改好了，但那次改动还没提交），**必须先提交再重跑一遍**，很别扭。
#
# 这个脚本把两处一次改完，于是"漏改"在物理上不存在，发版也不用跑两遍。
#
# ## 用法
#   bash tools/bump-version.sh 1.0.25
#
# 只改文件，**不提交**（提交由人来决定，commit message 才写得出上下文）。
set -euo pipefail

VER="${1:-}"
if [[ ! "$VER" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "用法: bash tools/bump-version.sh <x.y.z>（当前：$(grep -oE 'val buildCode = [0-9]+' app/build.gradle.kts | grep -oE '[0-9]+')）" >&2
  exit 2
fi

MAJOR="${VER%%.*}"
REST="${VER#*.}"
MINOR="${REST%%.*}"
PATCH="${REST#*.}"
CODE=$(( MAJOR * 10000 + MINOR * 100 + PATCH ))
if (( MAJOR > 99 || MINOR > 99 || PATCH > 99 )); then
  echo "每段最多两位（versionCode = major*10000 + minor*100 + patch）" >&2
  exit 2
fi

OLD_CODE="$(grep -oE 'val buildCode = [0-9]+' app/build.gradle.kts | head -1 | grep -oE '[0-9]+')"
[[ -n "$OLD_CODE" ]] || { echo "读不到 val buildCode" >&2; exit 1; }
if (( CODE <= OLD_CODE )); then
  echo "新版本序号必须更大：${OLD_CODE} -> ${CODE}（安卓靠它判断是不是升级）" >&2
  exit 2
fi

# ① 唯一数字源
python3 - "$CODE" <<'PY'
import re, sys
code = sys.argv[1]
path = "app/build.gradle.kts"
src = open(path, encoding="utf-8").read()
new, n = re.subn(r"val buildCode = \d+", "val buildCode = " + code, src, count=1)
assert n == 1, "app/build.gradle.kts 里找不到 val buildCode"
open(path, "w", encoding="utf-8").write(new)
PY

# ② 事实页那一行
if sed --version >/dev/null 2>&1; then SED=(sed -i -E); else SED=(sed -i '' -E); fi
"${SED[@]}" \
  "s/(- 版本号：versionCode \`)[0-9]+(\` \/ versionName \`)[0-9.]+(\`)/\1${CODE}\2${VER}\3/" \
  docs/plan/current-state.md
grep -q "versionName \`${VER}\`" docs/plan/current-state.md \
  || { echo "docs/plan/current-state.md 自动同步失败（那行格式被改过？）" >&2; exit 1; }

echo "✅ 版本 ${OLD_CODE} -> ${CODE}（${VER}）"
echo "   app/build.gradle.kts : val buildCode = $CODE"
echo "   docs/plan/current-state.md : versionName \`$VER\`"
echo
echo "下一步：提交后跑 bash tools/release.sh build"
