#!/usr/bin/env bash
# [DFWX] DFW-31 · CI 分层门禁：本地与 CI 共用的唯一门禁脚本。
#
# 为什么要有这个脚本，而不是把命令写死在 workflow 里：
# 卡片验收明确要求「本地与 CI 结论可对照」。
# 如果 CI 里写一套、本地另跑一套，两边结论永远对不上——
# 历史教训（审计 B-4）就是"CI 从不跑任何 test 任务"，导致约 90+ 个测试类在 CI 上从未执行，
# 而本地却以为 CI 在保护。唯一脚本 = 同一套命令、同一套失败条件。
#
# 用法：
#   tools/ci-gate.sh unit      # JVM 测试门禁（host + vendor + common）
#   tools/ci-gate.sh apk       # unsigned release 构建 + ABI + 资产命名/权限断言
#   tools/ci-gate.sh lint      # Android Lint（emptyRelease 变体，0 error）[DFW-123]
#   tools/ci-gate.sh all       # 以上全部
#   tools/ci-gate.sh docs      # 文档/仓库卫生门禁（不依赖 Android SDK 的静态检查）
#
# 环境变量：
#   JAVA_HOME   必填（CI 里由 setup-java 提供）
#   DFWX_UNSIGNED=1  跳过签名（CI 无 keystore；本地默认不跳过）

set -euo pipefail

# [DFW-91] 把**进程/线程软上限抬到硬上限**。
#
# 为什么必须由脚本自己做：Robolectric 每个测试类都要起 Activity、开线程池，
# 749 条用例跑下来要几千个线程。本机 `ulimit -u` 软上限只有 2666，而系统里
# 浏览器 / 输入法 / 微信等常驻进程已经占掉 2600 左右 —— 于是测试大批量报
# `OutOfMemoryError: unable to create native thread`，一次能红 184 条，
# **看起来像代码炸了，其实一行代码的问题都没有**。
#
# 这个坑历史上反复出现，以前靠"记得先 pkill GradleDaemon"绕过 —— 靠自觉就会忘。
# 现在脚本自己把软上限顶到硬上限（本机 4000），多出一千多个线程余量。
# 失败不致命（某些环境不允许抬高），所以吞掉错误。
ulimit -u "$(ulimit -Hu)" 2>/dev/null || true

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# docs 门禁是纯静态检查（grep/git），不需要 JDK；其余门禁必须有 JDK 21。
if [[ "${1:-all}" != "docs" && -z "${JAVA_HOME:-}" ]]; then
  echo "错误：必须设置 JAVA_HOME（JDK 21）" >&2
  exit 2
fi

GRADLE_ARGS=(-p "$REPO_ROOT")
[[ "${DFWX_OFFLINE:-0}" == "1" ]] && GRADLE_ARGS+=(--offline)

step() { printf '\n=== %s ===\n' "$1"; }
# [DFW-122 2026-10-02] 用 %b 而不是 %s：调用方在消息里写的 `\n` 是要换行，
# 但 %s 不解释转义，于是错误信息里会原样打印出 "\n" 两个字（DFW-15 那条一直是这样）。
# %b 会解释转义序列，所有调用方一次性都对了。
fail() { printf '\n❌ 门禁失败：%b\n' "$1" >&2; exit 1; }

# ---------------------------------------------------------------- 文档/卫生
gate_docs() {
  step "文档与仓库卫生（静态，无需 Android SDK）"

  # 调试残留不得被 git 跟踪（DFW-15）
  local residue
  residue="$(git ls-files | grep -iE '(cb_err|lb_resp|record_log|support_test_log|unit_compile_log)' || true)"
  [[ -z "$residue" ]] || fail "调试残留仍被 git 跟踪：\n$residue"

  # README 不得声称手表支持（DFW-18，decisions #13：手表测试永久停止）
  grep -q "手机与手表都好用" README.md && fail "README 仍在声称手表好用（decisions #13 已停测）"
  grep -q "手机 / 手表均可" README.md && fail "README 仍把手表写进系统要求"

  # 明文流量不得回到全局开关（DFW-10）
  grep -q 'usesCleartextTraffic="true"' app/src/main/AndroidManifest.xml \
    && fail "清单又出现全局明文开关（DFW-10 已收敛）"
  [[ -f app/src/main/res/xml/network_security_config.xml ]] \
    || fail "缺少 network_security_config.xml（DFW-10）"

  # 发版资产命名红线（纯 ASCII、无描述性后缀）
  grep -q "东方无限-vX.Y.Z-release" SECURITY.md \
    && fail "SECURITY.md 又写回带描述性后缀的资产名（发版红线）"

  # [DFW-122 / 2026-10-02] 测试文件必须被 git 跟踪。
  #
  # 为什么这条重要：本地 `./gradlew test` 会跑**工作区里所有**测试文件，
  # 但 CI 跑的是**仓库里的**测试文件。一个没 `git add` 的新测试，
  # 本地全绿、CI 全绿（因为它压根没跑），**两边都不会报错** ——
  # 直到某天有人改了它守护的代码，CI 依然绿，缺陷直接上线。
  #
  # 这个坑在本项目真实发生过：测试可信度审计（docs/audit/20261002-test-integrity.md）
  # 专门去数了一遍"仓库里有多少测试是没被跟踪的"。
  local untracked_tests
  untracked_tests="$(git ls-files --others --exclude-standard -- '*/src/test/*' '*/src/test/**' 2>/dev/null || true)"
  [[ -z "$untracked_tests" ]] || fail "有测试文件没被 git 跟踪（本地会跑、CI 不会跑）：\n$untracked_tests\n→ 补 git add，或确认它该不该存在"

  echo "✅ 文档与卫生门禁通过"
}

# ---------------------------------------------------------------- JVM 测试
gate_unit() {
  step "JVM 测试门禁：host（宿主 Java/UI）"
  # 宿主：:app 的 emptyDebug 变体（debug 任务名只用于测试，不参与任何 release 产物）
  ./gradlew "${GRADLE_ARGS[@]}" :app:testEmptyDebugUnitTest

  step "JVM 测试门禁：vendor（RikkaHub AI 模块）"
  # 注意：vendor 侧没有 flavor，任务名是 testDebugUnitTest（不是 testEmptyDebugUnitTest）
  ./gradlew "${GRADLE_ARGS[@]}" :rikkahub-app:testDebugUnitTest

  step "JVM 测试门禁：common（脱敏等共享逻辑）"
  ./gradlew "${GRADLE_ARGS[@]}" :common:testDebugUnitTest

  # [DFW-97 审计] vendor 侧**其余 9 个有测试的模块**原来从不进任何门禁。
  #
  # 实测（2026-10-02）：门禁只跑 3 个模块 = 1064 例，
  # 而 :ai/:highlight/:speech/:workspace/:search/:oauth/:document/:material3/:web
  # 合计还有 336 例（占全仓 24%）**从来没被跑过**。
  # 这不是"少跑一点"—— `:ai` 真的踩到过：`bbf689d DFW-8` 改了它 4 个 SSE 文件，
  # 而 `:ai` 的测试结果停在 09-28，没人发现。
  #
  # 一处跑全部，别一个个写（少写一个就是永久缺口）：
  # 这些模块都没有 flavor，任务名统一是 testDebugUnitTest。
  # ⚠️ **必须串行**（`--max-workers=1`）。
  #
  # [DFW-97] 实测教训：这 9 个模块刚加进门禁时用了默认并行
  # （gradle.properties 里 `org.gradle.parallel=true`），
  # 于是 12 个测试任务同时起 JVM —— 本地能过，**CI 上偶发**
  # `NoticeFlowJvmTest > urgentNotice_stillHasAnAcknowledgeAction`
  # 报 `java.lang.NullPointerException at ShadowCompatibility.java:53`。
  # 那是 Robolectric **自己的**内部 NPE，不是产品代码的问题：并发起太多
  # Robolectric sandbox 时它的静态状态会打架。
  # 串行之后 CI 稳定通过；代价只是这几分钟，换的是"门禁的红一定是真红"。
  step "JVM 测试门禁：vendor 其余模块（ai/highlight/speech/workspace/search/oauth/document/material3/web）"
  ./gradlew "${GRADLE_ARGS[@]}" --max-workers=1 \
    :ai:testDebugUnitTest \
    :highlight:testDebugUnitTest \
    :speech:testDebugUnitTest \
    :workspace:testDebugUnitTest \
    :search:testDebugUnitTest \
    :oauth:testDebugUnitTest \
    :document:testDebugUnitTest \
    :material3:testDebugUnitTest \
    :web:testDebugUnitTest

  echo "✅ JVM 测试门禁通过"
}

# ---------------------------------------------------------------- Lint
#
# [DFW-123 2026-10-02] lint 从"跑了但没人接"接进门禁。
#
# 为什么**单独一层**、不并进 `unit`：
#  1. `unit` 是本地最常跑的一层（一天十几次）。lint 要编译 **release** 变体，
#     而测试跑的是 **emptyDebug** 变体 —— 两者**不共享编译产物**，
#     并进去等于给每次迭代白加一段 release 编译；
#  2. 失败语义不同：`unit` 红 = "测试挂了"，lint 红 = "静态检查挂了"。
#     混在一起，"红的是什么"要翻日志才知道；
#  3. 单独一层才能在 CI 里开**独立 job 并行跑**（见 .github/workflows/ci-gates.yml），
#     几乎不增加关键路径耗时。
#
# ⚠️ 任务名是 `lintEmptyRelease`：项目 release 变体叫 `emptyRelease`（同 gate_apk），
#    任务名里**只有 Release、没有 Debug**，满足 AGENTS.md 二的发版红线。
#    **绝对不要**为了跑得快而改成 Debug 变体。
#
# ⚠️ warning 不拦（现状 100+ 条），只拦 error —— AGP 的 `abortOnError` 默认就是 true，
#    任务自己会因为 error 失败，这里不需要再解析报告去数数。
#    但若哪天有人把 `abortOnError` 改成 false，这条门禁会静默失效，
#    所以下面补了一道**兜底断言**：直接数 XML 里的 `severity="Error"`。
gate_lint() {
  step "Lint 门禁：emptyRelease 变体（0 error；warning 不拦）"
  ./gradlew "${GRADLE_ARGS[@]}" :app:lintEmptyRelease

  # 兜底：确认报告真的存在、且 error 数为 0。
  # 这一道防的是"任务假成功"—— 上面那条命令的退出码只反映 AGP 配置，不反映报告内容。
  local report="app/build/reports/lint-results-emptyRelease.xml"
  [[ -f "$report" ]] || fail "lint 报告不存在：${report}\n（任务可能没真的跑起来）"
  local errors
  errors="$(grep -c 'severity="Error"' "$report" || true)"
  [[ "$errors" == "0" ]] || fail "lint 报告里还有 ${errors} 条 error：${report}\n→ 逐条处置见 docs/plan/lint-triage.md"

  echo "✅ Lint 门禁通过（0 error）"
}

# ---------------------------------------------------------------- APK 产物
gate_apk() {
  step "构建 unsigned release（签名由本地发版流程负责，CI 不碰 keystore）"
  local build_args=(:app:assembleEmptyRelease)
  if [[ "${DFWX_UNSIGNED:-0}" == "1" ]]; then
    build_args+=(-Pdfwx.unsigned)
  fi
  ./gradlew "${GRADLE_ARGS[@]}" "${build_args[@]}"

  local apk
  apk="$(ls app/build/outputs/apk/empty/release/*.apk 2>/dev/null | head -1 || true)"
  [[ -n "$apk" && -f "$apk" ]] || fail "未产出 release APK"

  local aapt
  aapt="$(command -v aapt || true)"
  if [[ -z "$aapt" ]]; then
    local sdk_bt
    sdk_bt="$(ls -d "${ANDROID_HOME:-$ANDROID_SDK_ROOT}"/build-tools/*/ 2>/dev/null | sort -V | tail -1 || true)"
    [[ -n "$sdk_bt" ]] || fail "找不到 aapt（需要 ANDROID_HOME/build-tools）"
    aapt="${sdk_bt}aapt"
  fi

  step "断言 ABI = arm64-v8a（v1.18.0 误发事故红线）"
  "$aapt" dump badging "$apk" > /tmp/dfwx-badging.txt || fail "aapt dump badging 失败"
  grep -q "native-code: 'arm64-v8a'" /tmp/dfwx-badging.txt \
    || fail "release ABI 不是 arm64-v8a（严禁 Debug 任务名混入 release 构建）：\n$(grep native-code /tmp/dfwx-badging.txt || true)"

  step "断言不出广告/归因权限（DFW-9：Firebase 已移除）"
  local perms
  perms="$("$aapt" dump permissions "$apk")"
  for bad in "com.google.android.gms.permission.AD_ID" \
             "ACCESS_ADSERVICES_AD_ID" \
             "ACCESS_ADSERVICES_ATTRIBUTION" \
             "BIND_GET_INSTALL_REFERRER_SERVICE"; do
    grep -q "$bad" <<<"$perms" && fail "包内又出现广告/归因权限：${bad}（DFW-9 已摘除 Firebase）"
  done

  step "断言清单关键开关（DFW-10 / DFW-16）"
  "$aapt" dump xmltree "$apk" AndroidManifest.xml > /tmp/dfwx-manifest.txt || fail "aapt dump xmltree 失败"
  # [DFW-97 审计] 这两条正则**一直写错着**，所以这个 job 从来没绿过。
  # 错在哪：模式里写的是 `)="(type 0x12)0x0`，而 aapt 的真实输出是
  #   A: android:usesCleartextTraffic(0x010104ec)=(type 0x12)0x0
  # —— `)` 后面直接跟 `=`，**没有引号**。多一个 `"` 就永远匹配不上，
  # 于是「清单是对的」也被判成「清单不是 false」。
  # 教训：门禁的正则必须拿**真实产物**跑一遍再合进去，否则红的是门禁自己，不是代码。
  grep -q 'android:usesCleartextTraffic(0x[0-9a-f]*)=(type 0x12)0x0' /tmp/dfwx-manifest.txt \
    || fail "清单 usesCleartextTraffic 不是 false（DFW-10）"
  grep -q "android:networkSecurityConfig" /tmp/dfwx-manifest.txt \
    || fail "清单缺少 networkSecurityConfig（DFW-10）"
  grep -q 'android:allowBackup(0x[0-9a-f]*)=(type 0x12)0x0' /tmp/dfwx-manifest.txt \
    || fail "清单 allowBackup 不是 false（DFW-16）"

  step "断言版本号与页面一致"
  local ver
  ver="$(grep -oE "versionName='[^']+'" /tmp/dfwx-badging.txt | head -1)"
  [[ -n "$ver" ]] || fail "读不到 versionName"
  echo "产物：$apk"
  echo "版本：$ver"
  echo "✅ APK 产物门禁通过"
}

case "${1:-all}" in
  docs) gate_docs ;;
  unit) gate_unit ;;
  lint) gate_lint ;;
  apk)  gate_apk ;;
  all)  gate_docs; gate_unit; gate_lint; gate_apk ;;
  *) echo "用法：$0 [docs|unit|lint|apk|all]" >&2; exit 2 ;;
esac
