#!/usr/bin/env bash
# [DFWX] DFW-32 · 发版流程固化：把 `dfwx-release` 技能里的红线变成**可执行脚本**。
#
# 为什么要有这个脚本：
# 红线清单在技能文档里，靠人记。但 v1.18.0 的 ABI 翻转事故、v1.22.8 的中文资产名 404 事故，
# 都是"步骤记着、执行时漏了某一步"。把每一步写成断言，漏了就直接失败，比记在文档里可靠。
#
# 用法：
#   tools/release.sh verify              # 发版前全部门禁（不含构建）
#   tools/release.sh build               # 构建 arm64 release + 归档 + 打印交付信息
#   tools/release.sh publish <tag> <apk> # 上传 GitHub Release 并核对（默认 dry-run！）
#
#   RELEASE_DRY_RUN=0 tools/release.sh publish v1.2.3 <apk>   # 真正发布（需显式关闭 dry-run）
#
# 红线（脚本会强制，不要绕过）：
#   1. 任务名**严禁**含 "Debug"（v1.18.0 事故根因：ABI 被翻转成 x86_64-only）
#   2. 只构建 arm64-v8a；用 aapt 断言，不靠源码推断
#   3. Release 标题 = 纯 `vX.Y.Z`；资产名 = `dongfang-wuxian-vX.Y.Z.apk`（纯 ASCII）
#   4. 上传后必须核对：资产名 + sha256 digest + `curl -r 0-1023` 实拉拿到 `PK` 头
#   5. 不打印任何签名口令 / Token / Cookie（本脚本从不读取 local.properties）
#   6. 归档到 黑曜/03-构建产物/，**不删旧包**
#   7. 不自动安装到用户手机；发布默认 dry-run，需显式 RELEASE_DRY_RUN=0

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

GITHUB_REPO="dongwangxingchen/dongfang-wuxian"
ARCHIVE_DIR="${HOME}/heiyao/黑曜/03-构建产物"
RELEASE_TASK=":app:assembleEmptyRelease"   # 注意：绝不能出现 Debug

step() { printf '\n=== %s ===\n' "$1"; }
fail() { printf '\n❌ 失败：%s\n' "$1" >&2; exit 1; }
ok()   { printf '✅ %s\n' "$1"; }

require_java() {
  [[ -n "${JAVA_HOME:-}" ]] || fail "必须设置 JAVA_HOME（JDK 21）"
}

aapt_bin() {
  if command -v aapt >/dev/null 2>&1; then command -v aapt; return; fi
  local bt
  bt="$(ls -d "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"/build-tools/*/ 2>/dev/null | sort -V | tail -1 || true)"
  [[ -n "$bt" ]] || fail "找不到 aapt（需要 ANDROID_HOME/build-tools）"
  echo "${bt}aapt"
}

# ---------------------------------------------------------------- 发版前门禁
cmd_verify() {
  require_java

  step "红线自检：构建任务名不得含 Debug"
  [[ "$RELEASE_TASK" != *"Debug"* && "$RELEASE_TASK" != *"debug"* ]] \
    || fail "release 任务名含 Debug：${RELEASE_TASK}（v1.18.0 ABI 翻转事故根因）"
  ok "任务名 $RELEASE_TASK"

  step "跑 CI 门禁（层 1+2：静态 + 三套 JVM 测试）"
  JAVA_HOME="$JAVA_HOME" DFWX_OFFLINE="${DFWX_OFFLINE:-1}" bash tools/ci-gate.sh docs
  JAVA_HOME="$JAVA_HOME" DFWX_OFFLINE="${DFWX_OFFLINE:-1}" bash tools/ci-gate.sh unit

  step "版本号一致性：build.gradle.kts / current-state.md / CHANGELOG 同步"
  local code name
  code="$(grep -oE 'versionCode = [0-9]+' app/build.gradle.kts | head -1 | grep -oE '[0-9]+')"
  name="$(grep -oE 'versionName = "[^"]+"' app/build.gradle.kts | head -1 | sed 's/.*"\(.*\)"/\1/')"
  [[ -n "$code" && -n "$name" ]] || fail "读不到版本号"
  ok "build.gradle.kts: versionCode=$code versionName=$name"

  # 事实页必须跟上版本号（v1.22.9 曾出现"版本号改了、事实页没改"，导致接手者读到旧状态）
  local state_file="docs/plan/current-state.md"
  if [[ -f "$state_file" ]]; then
    grep -q "versionName \`$name\`" "$state_file" \
      || fail "$state_file 未同步到当前版本 ${name}（接手者会读到旧状态）"
    ok "current-state.md 已同步到 $name"
  fi

  step "AGPL / 上游署名义务仍在"
  grep -q "AGPL-3.0" README.md || fail "README 缺少 AGPL-3.0 署名"
  grep -q "RikkaHub" README.md || fail "README 缺少 RikkaHub 上游署名（AGPL 义务）"
  [[ -f docs/THIRD-PARTY-NOTICES.md ]] || fail "缺少第三方组件清单（DFW-30）"
  ok "许可与署名义务完整"

  step "签名配置不泄露：确认未读取 local.properties"
  ok "本脚本从不读 local.properties（签名由 gradle 内部读取）"

  # [DFW-52] 发布前检查清单：这些项以前散在文档/技能里，靠人记；这里逐条程序化检查。
  step "发布前检查清单（DFW-52）"

  # ① 调试残留不得被跟踪（DFW-15）
  local residue
  residue="$(git ls-files | grep -iE '(cb_err|lb_resp|record_log|support_test_log|unit_compile_log)' || true)"
  [[ -z "$residue" ]] || fail "调试残留仍被 git 跟踪：\n$residue"
  ok "① 无调试残留被跟踪"

  # ② 清单不得回到全局明文（DFW-10）
  grep -q 'usesCleartextTraffic="true"' app/src/main/AndroidManifest.xml \
    && fail "② 清单又出现全局明文开关"
  [[ -f app/src/main/res/xml/network_security_config.xml ]] || fail "② 缺少网络安全配置"
  ok "② 明文流量已收敛且配置存在"

  # ③ 资产命名红线：不得出现带描述后缀的历史写法（DFW-18）
  grep -q "东方无限-vX.Y.Z-release" SECURITY.md \
    && fail "③ SECURITY.md 又写回带描述后缀的资产名"
  ok "③ 资产命名描述与实际一致"

  # ④ CI 的 unsigned 产物必须明确标注不可分发（DFW-52）
  grep -q "UNSIGNED-do-not-distribute" .github/workflows/ci-gates.yml \
    || fail "④ CI 的 unsigned 产物未明确标注'不可分发'（易被误当发布物）"
  ok "④ CI unsigned 产物已标注'不可分发'"

  # ⑤ 未提交改动：发版必须建立在已提交的代码上（否则 tag 与源码不一致）。
  # 注意排除**故意不提交**的路径——`docs/handover/` 是 decisions #29 明确要求留在本机、
  # 不 push 的（交接文档）。不排除它的话，这个检查会永远失败、变成噪音而被忽略。
  local dirty
  dirty="$(git status --porcelain | grep -vE '^\?\? docs/handover/' || true)"
  if [[ -n "$dirty" ]]; then
    printf '⚠️  ⑤ 工作区有未提交改动，发版前请先提交（否则 Release 与源码不一致）：\n%s\n' "$(echo "$dirty" | head -20)"
    fail "⑤ 工作区不干净"
  fi
  ok "⑤ 工作区干净（已排除故意不提交的 docs/handover/）"

  printf '\n✅ 发版前门禁全部通过。下一步：tools/release.sh build\n'
}

# ---------------------------------------------------------------- 构建 + 归档
cmd_build() {
  require_java

  [[ "$RELEASE_TASK" != *"Debug"* && "$RELEASE_TASK" != *"debug"* ]] \
    || fail "release 任务名含 Debug：$RELEASE_TASK"

  step "构建 arm64 release（${RELEASE_TASK}）"
  ./gradlew -p "$REPO_ROOT" "$RELEASE_TASK"

  local apk
  apk="$(ls app/build/outputs/apk/empty/release/*.apk 2>/dev/null | head -1 || true)"
  [[ -n "$apk" && -f "$apk" ]] || fail "未产出 release APK"

  local aapt; aapt="$(aapt_bin)"

  step "断言 ABI = arm64-v8a（发版红线）"
  "$aapt" dump badging "$apk" > /tmp/dfwx-rel-badging.txt
  grep -q "native-code: 'arm64-v8a'" /tmp/dfwx-rel-badging.txt \
    || fail "ABI 不是 arm64-v8a：\n$(grep native-code /tmp/dfwx-rel-badging.txt || true)"

  local name code
  name="$(grep -oE "versionName='[^']+'" /tmp/dfwx-rel-badging.txt | head -1 | sed "s/versionName='\(.*\)'/\1/")"
  code="$(grep -oE "versionCode='[^']+'" /tmp/dfwx-rel-badging.txt | head -1 | sed "s/versionCode='\(.*\)'/\1/")"
  [[ -n "$name" ]] || fail "读不到 versionName"

  step "归档到 黑曜/03-构建产物/（不删旧包）"
  mkdir -p "$ARCHIVE_DIR"
  local target="$ARCHIVE_DIR/dongfang-wuxian-v${name}.apk"
  cp "$apk" "$target"
  local sha; sha="$(shasum -a 256 "$target" | awk '{print $1}')"
  local size; size="$(stat -f%z "$target" 2>/dev/null || stat -c%s "$target")"

  printf '\n================ 交付信息 ================\n'
  printf '版本        : v%s (versionCode %s)\n' "$name" "$code"
  printf '本地产物    : %s\n' "$apk"
  printf '归档        : %s\n' "$target"
  printf '大小        : %s 字节\n' "$size"
  printf 'sha256      : %s\n' "$sha"
  printf '资产名(ASCII): dongfang-wuxian-v%s.apk\n' "$name"
  printf '==========================================\n'
  ok "构建完成。下一步：tools/release.sh publish v$name \"$target\""
  printf '(发布默认 dry-run；确认无误后加 RELEASE_DRY_RUN=0)\n'
}

# ---------------------------------------------------------------- 发布 + 核对
cmd_publish() {
  local tag="${1:-}" apk="${2:-}"
  [[ -n "$tag" && -n "$apk" ]] || fail "用法：tools/release.sh publish <tag> <apk>"
  [[ -f "$apk" ]] || fail "找不到 APK：$apk"

  # 标题必须是纯 vX.Y.Z
  [[ "$tag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] \
    || fail "Release 标题必须是纯 vX.Y.Z（禁描述性后缀，用户红线）：$tag"

  local version="${tag#v}"
  local asset="dongfang-wuxian-v${version}.apk"
  # 资产名必须纯 ASCII（v1.22.8 事故：中文名上传时被剥离，资产变成 "-vX.Y.Z.apk" → 用户 404）
  # 用 LC_ALL=C 下的字节长度比较，而不是 grep -P（macOS 的 BSD grep 没有 -P）
  local bytes chars
  bytes="$(LC_ALL=C printf '%s' "$asset" | wc -c | tr -d ' ')"
  chars="$(printf '%s' "$asset" | wc -m | tr -d ' ')"
  [[ "$bytes" == "$chars" ]] || fail "资产名必须纯 ASCII（字节数 $bytes ≠ 字符数 ${chars}）：$asset"

  # 上传前先复制成 ASCII 名（v1.22.8 事故：中文名被剥离导致 404）
  local upload_src="/tmp/${asset}"
  cp "$apk" "$upload_src"
  local local_sha; local_sha="$(shasum -a 256 "$upload_src" | awk '{print $1}')"

  step "发布计划"
  printf '  仓库    : %s\n' "$GITHUB_REPO"
  printf '  标题    : %s\n' "$tag"
  printf '  资产    : %s\n' "$asset"
  printf '  sha256  : %s\n' "$local_sha"

  if [[ "${RELEASE_DRY_RUN:-1}" != "0" ]]; then
    printf '\n⚠️  DRY-RUN（未发布）。确认无误后执行：\n'
    printf '   RELEASE_DRY_RUN=0 tools/release.sh publish %s "%s"\n' "$tag" "$apk"
    exit 0
  fi

  step "创建 GitHub Release"
  gh release create "$tag" "$upload_src" \
    --repo "$GITHUB_REPO" --title "$tag" \
    --notes "东方无限 $tag"

  step "核对 1/3：资产名与 sha256 digest"
  local remote
  remote="$(gh release view "$tag" --repo "$GITHUB_REPO" --json assets \
    --jq ".assets[] | select(.name==\"$asset\") | \"\(.name) \(.size) \(.digest)\"")"
  [[ -n "$remote" ]] || fail "Release 上找不到资产 ${asset}（中文名剥离同源事故）"
  echo "  远端: $remote"
  grep -q "$local_sha" <<<"$remote" || fail "sha256 不一致！本地 ${local_sha}，远端 $remote"

  step "核对 2/3：下载直链可取且是真实 APK"
  local url="https://github.com/${GITHUB_REPO}/releases/download/${tag}/${asset}"
  local http; http="$(curl -sL -r 0-1023 -o /tmp/dfwx-head.bin -w '%{http_code}' "$url")"
  [[ "$http" == "206" || "$http" == "200" ]] || fail "下载直链返回 ${http}：$url"
  local magic; magic="$(head -c 2 /tmp/dfwx-head.bin)"
  [[ "$magic" == "PK" ]] || fail "下载内容不是 ZIP/APK（缺 PK 头）：$magic"

  step "核对 3/3：源码与许可证义务"
  grep -q "AGPL-3.0" README.md || fail "缺少 AGPL-3.0 署名"
  [[ -f docs/THIRD-PARTY-NOTICES.md ]] || fail "缺少第三方清单"

  printf '\n✅ 发布完成并通过全部核对\n'
  printf '   Release : https://github.com/%s/releases/tag/%s\n' "$GITHUB_REPO" "$tag"
  printf '   资产    : %s\n' "$asset"
  printf '   sha256  : %s\n' "$local_sha"
  printf '\n提醒：不要把链接直接给用户装，先给下载页；真机验收由用户执行。\n'
}

case "${1:-verify}" in
  verify)  cmd_verify ;;
  build)   cmd_build ;;
  publish) shift; cmd_publish "$@" ;;
  *) echo "用法：$0 [verify|build|publish <tag> <apk>]" >&2; exit 2 ;;
esac
