#!/usr/bin/env bash
# 一处改版本号，所有地方跟着变。
#
# ## 为什么要有它
# 用户原话：「我主要是想减轻你的维护负担，**别忘记改某些地方的版本号**」。
# 版本号只剩两个落点：
#   1. `app/build.gradle.kts` 的 `val appVersionName`（**唯一数字源**）
#   2. `docs/plan/current-state.md` 的「- 版本号：…」那一行（接手者读的事实页）
#
# ## [DFW-118 2026-10-02] 改成「只写版本名，序号自动算」（用户选路线 B）
# 用户原话：
#   > 「版本序号能不能和版本号一样？都是 1.0.0，然后 1.0.1，这样不断叠加下去？」
#   > 「路线 B 吧，底子打得好是很好的。反正你都能可以给我新包，我完全不怕重新安装一个呀。」
#
# 旧方案是 `val buildCode = 10034` 一个手写计数器 + 固定 versionName "1.0.0"。
# 它必然出错，而且已经出过一次：后台填了 10028 而手机装着 10034，
# 软件判定"这更新比我还旧"，更新弹窗永远不出现（DFW-117 排查现场）。
#
# 现在 `versionCode` 由 `appVersionName` 推导：
#   versionCode = major*10000 + minor*100 + patch
#   1.0.0 → 10000   1.0.1 → 10001   1.1.0 → 10100   2.0.0 → 20000
# **改不出错**：只有一个数字要动，序号不可能忘。
#
# ⚠️ 从旧方案切过来的**第一版必须卸载重装**：手机上装着旧的 10034，
# 而 1.0.0 算出来是 10000，安卓会当降级拒绝安装。用户已知情并同意。
#
# ## 用法
#   bash tools/bump-version.sh            # 修订号 +1（最常用）：1.0.0 -> 1.0.1
#   bash tools/bump-version.sh --minor    # 次版本 +1：1.0.1 -> 1.1.0
#   bash tools/bump-version.sh --major    # 主版本 +1：1.1.0 -> 2.0.0
#   bash tools/bump-version.sh 1.2.3      # 直接指定
#
# 只改文件，**不提交**（提交由人来决定，commit message 才写得出上下文）。
set -euo pipefail

GRADLE="app/build.gradle.kts"
STATE="docs/plan/current-state.md"

OLD_NAME="$(grep -oE 'val appVersionName = "[0-9]+\.[0-9]+\.[0-9]+"' "${GRADLE}" | head -1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')"
[[ -n "${OLD_NAME}" ]] || { echo "读不到 val appVersionName（格式：val appVersionName = \"1.0.0\"）" >&2; exit 1; }

# 按规则把 x.y.z 换成内部序号；不合法就报错退出。
code_of() {
  local name="$1"
  [[ "${name}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "版本名必须是 x.y.z 三段：${name}" >&2; return 1; }
  local major minor patch
  IFS=. read -r major minor patch <<<"${name}"
  (( minor < 100 && patch < 100 )) || {
    echo "minor/patch 必须小于 100，否则会进位串号：${name}" >&2; return 1; }
  echo $(( major * 10000 + minor * 100 + patch ))
}

IFS=. read -r O_MAJOR O_MINOR O_PATCH <<<"${OLD_NAME}"
OLD_CODE="$(code_of "${OLD_NAME}")"

# 不带参数 = 修订号 +1（最常用）；--minor / --major / 显式 x.y.z 也可以。
ARG="${1:-}"
case "${ARG}" in
  "")
    NEW_NAME="${O_MAJOR}.${O_MINOR}.$(( O_PATCH + 1 ))"
    ;;
  --minor)
    NEW_NAME="${O_MAJOR}.$(( O_MINOR + 1 )).0"
    ;;
  --major)
    NEW_NAME="$(( O_MAJOR + 1 )).0.0"
    ;;
  -h|--help)
    sed -n '25,33p' "$0" | sed 's/^# \{0,1\}//'
    exit 0
    ;;
  *)
    NEW_NAME="${ARG}"
    ;;
esac

NEW_CODE="$(code_of "${NEW_NAME}")" || exit 2
if (( NEW_CODE <= OLD_CODE )); then
  echo "新版本必须更大：${OLD_NAME} (${OLD_CODE}) -> ${NEW_NAME} (${NEW_CODE})" >&2
  echo "安卓靠内部序号判断是不是升级；调小会让用户装不上（提示「应用未安装」）。" >&2
  exit 2
fi

# ① 唯一数字源
python3 - "${NEW_NAME}" <<'PY'
import re, sys
name = sys.argv[1]
path = "app/build.gradle.kts"
src = open(path, encoding="utf-8").read()
new, n = re.subn(r'val appVersionName = "[0-9]+\.[0-9]+\.[0-9]+"',
                 'val appVersionName = "%s"' % name, src, count=1)
assert n == 1, "app/build.gradle.kts 里找不到 val appVersionName"
open(path, "w", encoding="utf-8").write(new)
PY

# ② 事实页那一行
if sed --version >/dev/null 2>&1; then SED=(sed -i -E); else SED=(sed -i '' -E); fi
"${SED[@]}" \
  "s/(- 版本号：versionCode \`)[0-9]+(\` \/ versionName \`)[0-9.]+(\`)/\1${NEW_CODE}\2${NEW_NAME}\3/" \
  "${STATE}"
grep -q "versionName \`${NEW_NAME}\`" "${STATE}" \
  || { echo "${STATE} 自动同步失败（那行格式被改过？）" >&2; exit 1; }

echo "✅ ${OLD_NAME} -> ${NEW_NAME}（内部序号 ${OLD_CODE} -> ${NEW_CODE}）"
echo "   ${GRADLE} : val appVersionName = \"${NEW_NAME}\""
echo "   ${STATE} : versionCode \`${NEW_CODE}\` / versionName \`${NEW_NAME}\`"
echo
echo "下一步：提交后跑 bash tools/release.sh build"
