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

# [DFW-91] 与 ci-gate.sh 同款：把线程软上限顶到硬上限。
# 本机软上限 2666、常驻进程已占 ~2600，构建/测试会报
# `OutOfMemoryError: unable to create native thread`（看起来像代码炸了，其实不是）。
ulimit -u "$(ulimit -Hu)" 2>/dev/null || true

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

GITHUB_REPO="dongwangxingchen/dongfang-wuxian"
# 构建产物归档目录。默认放在家目录下的通用位置；
# 需要自定义（例如放到别处的归档盘）就设 DFWX_ARCHIVE_DIR。
ARCHIVE_DIR="${DFWX_ARCHIVE_DIR:-${HOME}/dfwx-builds}"
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

  step "版本号一致性：build.gradle.kts / current-state.md 同步"
  # [DFW-91 + DFW-118 路线 B] 版本有**两个值、一个来源**：
  #   · `val appVersionName = "x.y.z"`（**唯一可改的数字**）
  #   · versionName ← 直接用它；versionCode ← 由它推导（major*10000+minor*100+patch）
  # 这里把两个值都从那一行算出来，**不读第二处**（读了就会出现"两处不一致"）。
  #
  # 旧方案（DFW-97）是 `val buildCode` 手写计数器 + 固定 versionName "1.0.0"，
  # 已作废：计数器靠人记得加，DFW-117 就是这么出的（后台填 10028、手机装着 10034，
  # 软件判定"这更新比我还旧"，更新弹窗永远不出现）。
  local code name
  name="$(grep -oE 'val appVersionName = "[0-9]+\.[0-9]+\.[0-9]+"' app/build.gradle.kts | head -1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')"
  [[ -n "${name}" ]] || fail "读不到 val appVersionName = \"x.y.z\"（DFW-118 路线 B 后版本号的唯一来源）"
  code="$(awk -F. '{ printf "%d", $1 * 10000 + $2 * 100 + $3 }' <<<"$name")"
  [[ -n "${code}" ]] || fail "versionCode 推导失败：${name}"
  ok "build.gradle.kts: versionCode=$code versionName=$name"

  # 事实页必须跟上版本号（v1.22.9 曾出现"版本号改了、事实页没改"，导致接手者读到旧状态）。
  # [DFW-91] 从「不同步就报错」改成「**自动改好**」：靠人记得改文档不可靠 ——
  # 上一个窗口就真的漏过一次，是 DocTimelinessJvmTest 变红才发现的。现在想漏都漏不掉。
  local state_file="docs/plan/current-state.md"
  if [[ -f "$state_file" ]]; then
    if sed --version >/dev/null 2>&1; then SED_INPLACE=(sed -i -E); else SED_INPLACE=(sed -i '' -E); fi
    "${SED_INPLACE[@]}" \
      "s/(- 版本号：versionCode \`)[0-9]+(\` \/ versionName \`)[0-9.]+(\`)/\1${code}\2${name}\3/" \
      "$state_file"
    grep -q "versionName \`$name\`" "$state_file" \
      || fail "$state_file 自动同步失败（该行格式被改过，请检查「- 版本号：versionCode ...」这一行）"
    ok "current-state.md 已同步到 ${name}（自动写入）"
  fi

  step "跑 CI 门禁（层 1+2：静态 + 三套 JVM 测试）"
  JAVA_HOME="$JAVA_HOME" DFWX_OFFLINE="${DFWX_OFFLINE:-1}" bash tools/ci-gate.sh docs
  JAVA_HOME="$JAVA_HOME" DFWX_OFFLINE="${DFWX_OFFLINE:-1}" bash tools/ci-gate.sh unit

  # [DFW-91] 顺序很重要：**自动同步事实页必须在跑门禁之前**。
  # 否则 DocTimelinessJvmTest 会拿旧文档去比新版本号，先红一次、同步完再绿——
  # 那不是"门禁发现问题"，是"门禁自己制造的假故障"。

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

  # [DFW-91] **出包前强制先过 verify**。
  # 以前 verify 和 build 是两个互不相干的子命令，只跑 build 就直接出包了 ——
  # v1.0.15 那次"带红门禁发布"就是这么来的（门禁红着，包照样发出去）。
  # 现在 build 自己先把 verify 跑一遍，想跳过都难。
  cmd_verify

  [[ "$RELEASE_TASK" != *"Debug"* && "$RELEASE_TASK" != *"debug"* ]] \
    || fail "release 任务名含 Debug：$RELEASE_TASK"

  step "构建 arm64 release（${RELEASE_TASK}）"
  ./gradlew -p "$REPO_ROOT" "$RELEASE_TASK"

  # [DFW-115] 原来这里是 `ls app/build/outputs/apk/empty/release/*.apk | head -1`。
  # 目录里多于一个 APK 时会**静默取字典序最小的那个**，可能把一个旧包归档并发布出去。
  # 现在改成「必须恰好一个，否则停下来」。刻意不用数组：macOS 自带的 bash 3.2 在
  # `set -u` 下展开空数组会报 unbound variable。
  local apk="" count=0 listing="" f
  for f in app/build/outputs/apk/empty/release/*.apk; do
    if [[ -f "$f" ]]; then
      count=$(( count + 1 ))
      listing="${listing}${listing:+ }$f"
      if [[ "$count" -eq 1 ]]; then apk="$f"; fi
    fi
  done
  [[ "$count" -eq 1 ]] \
    || fail "release 目录下应恰好有 1 个 APK，实际 ${count} 个：${listing:-无}（不许任取一个）"

  local aapt; aapt="$(aapt_bin)"

  step "断言 ABI = arm64-v8a（发版红线）"
  "$aapt" dump badging "$apk" > /tmp/dfwx-rel-badging.txt
  grep -q "native-code: 'arm64-v8a'" /tmp/dfwx-rel-badging.txt \
    || fail "ABI 不是 arm64-v8a：\n$(grep native-code /tmp/dfwx-rel-badging.txt || true)"

  local name code
  name="$(grep -oE "versionName='[^']+'" /tmp/dfwx-rel-badging.txt | head -1 | sed "s/versionName='\(.*\)'/\1/")"
  code="$(grep -oE "versionCode='[^']+'" /tmp/dfwx-rel-badging.txt | head -1 | sed "s/versionCode='\(.*\)'/\1/")"
  [[ -n "$name" ]] || fail "读不到 versionName"

  # [DFW-115] 与「唯一数字源」对账。
  # 上面 name/code 读的是**盘上的那个包**；cmd_verify 读的是 app/build.gradle.kts
  # 里**应该发什么**。这两处以前从不比对 —— 于是「gradle 没吃到新版本号」
  # （构建复用旧产物、bump-version.sh 只改了一半…）会静默按**旧版本**归档，
  # 并提示「下一步 publish v旧版本」，全程不报错。
  local src_name
  src_name="$(grep -oE 'val appVersionName = "[0-9]+\.[0-9]+\.[0-9]+"' app/build.gradle.kts | head -1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')"
  [[ -n "$src_name" ]] || fail "读不到 app/build.gradle.kts 里的 val appVersionName"
  [[ "$name" == "$src_name" ]] \
    || fail "盘上这个包的 versionName=${name}，但 app/build.gradle.kts 写的是 ${src_name} —— 构建没有产出新版本，拒绝按旧包归档/发布"
  ok "包内版本与 build.gradle.kts 一致：${name} (${code})"

  step "归档到 黑曜/03-构建产物/（不删旧包）"
  mkdir -p "$ARCHIVE_DIR"
  local target="$ARCHIVE_DIR/dongfang-wuxian-v${name}.apk"
  # [DFW-115] 铁律是「不删旧包」。原来直接 cp 会**静默覆盖**同名归档，
  # 如果那份正是已经发出去的字节，就再也无法比对复现了。
  #
  # [2026-10-03] **不 fail，改成自动加时间戳后缀。**
  # 原因：测试期版本号固定是 1.0.0（用户明确要求「不要改成新版本」），
  # 但会发很多个 tag（test-20261003-xx），每次重建的产物名都一样。
  # 这里如果直接 fail，就变成「每次发版都要先手工给旧包改名」——
  # 那种摩擦最终一定会被人用裸 `cp` 绕过去，**保护反而失效**。
  # 自动改名同时满足两件事：一个字节都不丢，且不需要人工介入。
  # 注意：后缀只加在**本地归档名**上；GitHub 资产名仍是纯
  # `dongfang-wuxian-v${name}.apk`（发版红线：资产名必须 ASCII 且不带描述性后缀）。
  if [[ -e "$target" ]]; then
    local stamp; stamp="$(date +%Y%m%d-%H%M%S)"
    local stamped="$ARCHIVE_DIR/dongfang-wuxian-v${name}-${stamp}.apk"
    printf 'ℹ️  同名归档已存在，改存为带时间戳的名字（旧包原样保留）：\n    %s\n' "$stamped"
    target="$stamped"
  fi
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

  # [DFW-115] 断言「包内真实版本」==「tag 里的版本」。
  #
  # 原来的三道核对全是**自洽性**检查：本机 sha ↔ 远端同一个文件的 digest、
  # 直链 200 + PK 头、AGPL 署名 —— 没有一道能发现「要发的这个包是旧的」。
  # `release.sh publish v1.0.5 <v1.0.0 的包>` 会一路打印「✅ 发布完成并通过全部核对」，
  # 而资产 dongfang-wuxian-v1.0.5.apk 装出来是 1.0.0 → 用户装完仍被提示更新（更新死循环）。
  # 放在 dry-run 之前，这样干跑就能发现。
  local aapt badging real_name real_code want_code
  aapt="$(aapt_bin)"
  badging="$("$aapt" dump badging "$apk" 2>/dev/null || true)"
  [[ -n "$badging" ]] || fail "aapt 读不出这个包（不是合法 APK？）：${apk}"
  real_name="$(grep -oE "versionName='[^']+'" <<<"$badging" | head -1 | sed "s/versionName='\(.*\)'/\1/")"
  real_code="$(grep -oE "versionCode='[^']+'" <<<"$badging" | head -1 | sed "s/versionCode='\(.*\)'/\1/")"
  want_code="$(awk -F. '{ printf "%d", $1 * 10000 + $2 * 100 + $3 }' <<<"$version")"
  [[ "$real_name" == "$version" ]] \
    || fail "包内 versionName=${real_name}，与 tag v${version} 不一致 —— 拒绝把旧包当新版本发出去"
  [[ "$real_code" == "$want_code" ]] \
    || fail "包内 versionCode=${real_code}，由 ${version} 推导应为 ${want_code}"
  ok "包内版本与 tag 一致：${real_name} (${real_code})"

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
