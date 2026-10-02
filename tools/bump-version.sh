#!/usr/bin/env bash
# 一处改版本号，所有地方跟着变。
#
# ## 为什么要有它
# 用户原话：「我主要是想减轻你的维护负担，**别忘记改某些地方的版本号**」。
# DFW-91 之后版本号已经只剩两个落点：
#   1. `app/build.gradle.kts` 的 `val buildCode`（唯一数字源 = versionCode）
#   2. `docs/plan/current-state.md` 的「- 版本号：…」那一行（接手者读的事实页）
#
# ## [DFW-97] 参数从「x.y.z」改成了「内部序号」
# 用户 2026-10-02：「改成 1.0.0 版本啊，我还没发布正式版呢，咱们做的一直是测试版，
# 所以一直改为 1.0.0 好吧？」—— 于是测试期 `versionName` **固定 1.0.0**，
# 真正需要动的只有内部序号 `versionCode`（安卓靠它判断能不能覆盖安装）。
# 所以现在这个脚本做的是：**把内部序号加一**，显示名字保持 1.0.0 不动。
#
# 但"两处"仍然是两处 —— 本次实际就漏过一次：改了 buildCode 没改事实页，
# 结果 `release.sh build` 跑第一遍被 `⑤ 工作区不干净` 拦下（verify 里的自动同步
# 把事实页改好了，但那次改动还没提交），**必须先提交再重跑一遍**，很别扭。
#
# 这个脚本把两处一次改完，于是"漏改"在物理上不存在，发版也不用跑两遍。
#
# ## 用法
#   bash tools/bump-version.sh          # 内部序号 +1（最常用）
#   bash tools/bump-version.sh 10030    # 直接指定新序号
#
# 只改文件，**不提交**（提交由人来决定，commit message 才写得出上下文）。
set -euo pipefail

OLD_CODE_NOW="$(grep -oE 'val buildCode = [0-9]+' app/build.gradle.kts | head -1 | grep -oE '[0-9]+')"
[[ -n "${OLD_CODE_NOW}" ]] || { echo "读不到 val buildCode" >&2; exit 1; }

# 不带参数 = 内部序号 +1（最常用）；带了就按给的数字走。
ARG="${1:-}"
if [[ -z "${ARG}" ]]; then
  CODE=$(( OLD_CODE_NOW + 1 ))
elif [[ "${ARG}" =~ ^[0-9]+$ ]]; then
  CODE="${ARG}"
else
  echo "用法: bash tools/bump-version.sh [新内部序号]" >&2
  echo "  不带参数 = 当前 ${OLD_CODE_NOW} 加一" >&2
  echo "  例：bash tools/bump-version.sh 10030" >&2
  exit 2
fi

VER="$(grep -oE 'versionName = "[0-9.]+"' app/build.gradle.kts | head -1 | grep -oE '[0-9.]+')"
[[ -n "${VER}" ]] || { echo "读不到 versionName" >&2; exit 1; }

OLD_CODE="$(grep -oE 'val buildCode = [0-9]+' app/build.gradle.kts | head -1 | grep -oE '[0-9]+')"
[[ -n "$OLD_CODE" ]] || { echo "读不到 val buildCode" >&2; exit 1; }
if (( CODE <= OLD_CODE )); then
  echo "新版本序号必须更大：${OLD_CODE} -> ${CODE}（安卓靠它判断是不是升级；调小会让用户装不上）" >&2
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

echo "✅ 内部序号 ${OLD_CODE} -> ${CODE}（显示版本名保持 ${VER} 不变）"
echo "   app/build.gradle.kts : val buildCode = ${CODE}"
echo "   docs/plan/current-state.md : versionCode \`${CODE}\` / versionName \`${VER}\`"
echo
echo "下一步：提交后跑 bash tools/release.sh build"
