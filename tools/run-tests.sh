#!/usr/bin/env bash
#
# [DFW-124 2026-10-02] 跑测试的**唯一推荐入口**。
#
# ## 为什么需要这个脚本，而不是直接 ./gradlew test
#
# 这个项目踩过一次「一次红 184 条」的坑（lessons.md 第九节-4）：
# 761 条 Robolectric 用例跑下来，测试 JVM 里的线程**只增不减**；
# 顶到 `ulimit -u` 之后，后面所有用例成批报
# `OutOfMemoryError: unable to create native thread` ——
# **看起来像代码全炸了，其实一行代码的问题都没有。**
#
# `tools/ci-gate.sh` 和 `tools/release.sh` 里已经有这三层保护了。
# 但问题在于：**直接敲 `./gradlew :app:testEmptyDebugUnitTest` 会绕过它们。**
# 2026-10-02 晚上就是这么翻的车 —— 我直接跑 gradle 连跑几轮，
# 同一份代码跑出「2 条失败 → 3 条失败（换成别的）→ 0 条失败」三种结果，
# 一度以为是测试之间有污染，白查了一轮。
#
# 所以这里把三层保护抽成一个入口，让"直接跑"也能拿到同样的环境。
#
# ## 用法
#
#   bash tools/run-tests.sh                    # 全量 :app
#   bash tools/run-tests.sh --rerun            # 强制重跑（不信 Gradle 的 up-to-date）
#   bash tools/run-tests.sh --class DfLogJvmTest
#   bash tools/run-tests.sh --vendor           # vendor 区（:rikkahub-app）
#
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# ── 第 1 层：把线程软上限顶到硬上限 ────────────────────────────────────
# 本机软上限 2666、硬上限 4000；测试期间线程数会冲到几千。
_soft="$(ulimit -u 2>/dev/null || echo '')"
_hard="$(ulimit -Hu 2>/dev/null || echo '')"
if [[ -n "$_hard" ]]; then
  ulimit -u "$_hard" 2>/dev/null && echo "🔧 线程上限：${_soft:-?} → ${_hard}"
fi

# ── 第 2 层：清掉上一轮残留的 worker 和守护进程 ────────────────────────
# 被 kill 的测试任务会把 worker JVM 留在后台，它们占着线程不放。
# ⚠️ 如果此刻有别人在跑 gradle，这里会误伤 —— 所以只有你确定没人在跑时才用。
if pgrep -f GradleWorkerMain >/dev/null 2>&1; then
  echo "🔧 清理残留 Gradle worker：$(pgrep -f GradleWorkerMain | tr '\n' ' ')"
  pkill -f GradleWorkerMain 2>/dev/null || true
  sleep 2
fi

# ── 参数解析 ──────────────────────────────────────────────────────────
TASK=":app:testEmptyDebugUnitTest"
GRADLE_EXTRA=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --rerun)  GRADLE_EXTRA+=(--rerun); shift ;;
    --vendor) TASK=":rikkahub-app:testDebugUnitTest"; shift ;;
    # 用单条通配 `*.类名`：两个区（宿主 cc.nkbr.lanzouplus / vendor me.rerere.rikkahub.dfwx）
    # 都可能命中。**不要写成两条 --tests** —— 其中一条匹配不到任何测试时，
    # Gradle 会直接报 "No tests found for given includes" 而不是忽略它。
    --class)  shift; GRADLE_EXTRA+=(--tests "*.$1"); shift ;;
    -h|--help) sed -n '3,25p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "未知参数：$1（-h 看用法）" >&2; exit 2 ;;
  esac
done

# ── 跑 ────────────────────────────────────────────────────────────────
echo "▶ $TASK ${GRADLE_EXTRA[*]:-}"
#
# ⚠️ `${GRADLE_EXTRA[@]}` **必须**写成 `${GRADLE_EXTRA[@]+"${GRADLE_EXTRA[@]}"}`。
#
# macOS 自带的是 **bash 3.2**（/bin/bash），它有个老 bug：脚本里开了 `set -u` 时，
# 展开一个**空数组**会报 `GRADLE_EXTRA[@]: unbound variable` 并直接退出。
# 这个脚本第 28 行正好有 `set -uo pipefail`。
#
# 后果（2026-10-03 实际撞上）：**不带任何参数跑全量测试会立刻失败**，
# 而带 `--class Xxx` 时数组非空、一切正常 —— 所以这个 bug 藏了很久没暴露，
# 表现是"我只想跑个全量，结果脚本自己死了"，很容易被误判成代码或环境问题。
# `${A[@]+...}` 的写法在数组为空时展开成空，非空时正常展开，两种 bash 都对。
DFWX_OFFLINE=1 JAVA_HOME="${JAVA_HOME:-$JAVA_HOME}" \
  ./gradlew -p "$REPO_ROOT" "$TASK" ${GRADLE_EXTRA[@]+"${GRADLE_EXTRA[@]}"}
status=$?

if [[ $status -ne 0 ]]; then
  cat <<'EOF'

❌ 测试没通过。在怀疑代码之前，先按顺序排除环境问题
（lessons.md 第九节「构建/测试环境坑」—— 这一节几乎覆盖了所有假失败）：

  1. 失败信息里有 `OutOfMemoryError: unable to create native thread`？
     → 残留 worker。跑 `./gradlew --stop && pkill -f GradleWorkerMain` 再重来。
  2. 失败面很宽、而且是**每次不一样**的几条？
     → 先怀疑环境，不要先改代码。同一条测试单独跑是绿的，就基本可以确定是环境。
  3. 确实要怀疑代码时，再做反向探针（撤掉改动看它会不会变红）。
EOF
fi
exit $status
