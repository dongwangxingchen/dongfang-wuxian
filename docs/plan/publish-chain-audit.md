# 发布脚本链静默失效点审计（DFW-115 / DFW-104 · 服务端半边）

> 起因：v1.0.15 出过一次「门禁红了还照样发布」，根因是发布流程用 `;` 而不是 `&&`
> 串联「跑门禁」和「构建/发布」。本文件排查**同类问题还有几处**。
>
> 审计对象：`tools/release.sh`、`tools/dfwx-publish-apk.sh`、`tools/bump-version.sh`（只读）、
> `server/dfwx-pb/set-release.py`。
> 全部结论都带复现命令或 `文件:行号`。**本文件只报告，不改任何脚本**（改动由 Lead 决定）。

审计时间：2026-10-03。审计时线上版本 `1.0.0 / versionCode 10000`。

---

## 结论速览（按严重度排序）

| # | 严重度 | 位置 | 一句话 |
|---|---|---|---|
| 1 | **P0** | `tools/dfwx-publish-apk.sh:49` | `;` 把 `set-release.py` 的退出码吃掉了 —— **APK 换了、记录没改，脚本仍报「完成。」退出 0** |
| 2 | **P1** | `tools/dfwx-publish-apk.sh:52-53` | 外网可达性检查只看不判 + 结尾是 `echo` → **HTTP 404 也返回退出码 0** |
| 3 | **P1** | `tools/dfwx-publish-apk.sh` / `tools/release.sh cmd_publish` | 两个脚本都**不校验「APK 里的真实版本号 == 命令行/tag 的版本号」** → 可以把旧包当新版本发出去 |
| 4 | P2 | 整条链 | **没有编排**：GitHub 与自有服务器是两次独立手工操作，跑一半就是 DFW-82 的原形 |
| 5 | P2 | `tools/release.sh:177-178` vs `:73-75` | `cmd_build` 不比对「APK 的 versionName」与 `val appVersionName`（唯一数字源） |
| 6 | P2 | `tools/release.sh:166` | `ls ... \| head -1` 多包时任取其一（今天只有 1 个，属潜在） |
| 7 | P3 | `tools/release.sh:184` | `cp` 会静默覆盖同名归档，丢掉已发布的那份字节 |
| 8 | P3 | `server/dfwx-pb/set-release.py:55` | 守卫是 `<` 不是 `<=`：CLI 路径允许重复发同一 versionCode，与控制台「相等硬拦」不一致 |

---

## P0 · `dfwx-publish-apk.sh:49` 的 `;` 吃掉退出码

**原文（`tools/dfwx-publish-apk.sh:48-49`）：**

```bash
scp -q server/dfwx-pb/set-release.py dfwx:/tmp/set-release.py
ssh dfwx "sudo ALLOW_DOWNGRADE='${ALLOW_DOWNGRADE:-0}' VER='${VER}' CODE='${CODE}' SHA='${LOCAL_SHA}' SIZE='${SIZE}' python3 /tmp/set-release.py; rm -f /tmp/set-release.py"
```

问题在最后那个 `;`：`ssh` 的退出码 = 远程 shell 里**最后一条命令**的退出码，
而最后一条是 `rm -f` —— **它永远成功**。于是 `python3 set-release.py` 无论怎么失败，
`ssh` 都返回 0，脚本第 12 行的 `set -euo pipefail` 不会触发，继续往下打印「完成。」并退出 0。

**实测复现（用脚本里一模一样的那条 ssh 命令形状）：**

```
$ ssh dfwx "sudo ALLOW_DOWNGRADE='0' VER='0.9.9' CODE='9999' SHA='<64个a>' SIZE='123' python3 /tmp/set-release.py; echo \"  ↳ python 退出码=\$?\""
拒绝：版本序号回退（记录 10000，本次 9999）
如果这是「换了版本编号方案」的一次性过渡，加 ALLOW_DOWNGRADE=1 重跑。
  ↳ python 退出码=3

$ ssh dfwx "sudo ALLOW_DOWNGRADE='0' VER='0.9.9' CODE='9999' SHA='<64个a>' SIZE='123' python3 /tmp/set-release.py; rm -f /tmp/set-release.py"
拒绝：版本序号回退（记录 10000，本次 9999）
如果这是「换了版本编号方案」的一次性过渡，加 ALLOW_DOWNGRADE=1 重跑。
  ↳ 这条 ssh 的退出码 = 0        ← ★ 内层 3，外层 0
```

> 注：这次复现是安全的 —— `set-release.py` 的回退守卫在**任何写库动作之前**就 `return 3` 了
> （`server/dfwx-pb/set-release.py:60-63`），线上 `release` 记录一个字节都没动。

**什么情况下会静默走错**（任一条命中即可）：

| 触发 | `set-release.py` 行为 |
|---|---|
| 版本序号回退且没开 `ALLOW_DOWNGRADE` | `return 3`（`:63`） |
| 读不到超管口令 / 口令错 | 异常 → traceback → 退出 1 |
| PocketBase 5xx、超时、连接失败 | `urllib` 抛异常 → traceback → 退出 1 |
| 传参不完整（SHA 不是 64 位等） | `return 2`（`:47`） |

**后果**：APK 已经落到 `/var/www/dfwx/apk/`、但 `release` 记录还指向上一版。
这正是 `dfwx-publish-apk.sh` 文件头（`:4-6`）自己写着要避免的「只换了包没改记录」，
也正是 DFW-82 的形态 —— **用户永远收不到更新，且没有任何报错**。
脚本最后还会打印「完成。」，人会以为发好了。

### ⚠️ 这条**不是假设**：它已经真实发生过一次

`docs/agents/lessons.md:475-486`（十-4「服务器上的口令副本会静默过期」）记的就是这条路径：

> `<凭据文件>` 存的是 PocketBase 超级管理员口令的副本。
> 用户"找不到用户名"那次重置过口令，**但没人想到去同步这个文件** ——
> 于是 `tools/dfwx-publish-apk.sh` 的同步步骤一直以 `HTTP 400` 失败：
> **APK 传上去了、release 记录没更新**。
> 这正是 DFW-82 的形态：用户能下到新包，但软件永远不提示更新，
> 而日志里只有一段看不懂的 `urllib` 堆栈，很容易被当成"网络抖动"忽略。

当时归因为「口令副本过期」，并写了规则「重置口令时把副本列进清单一起改」（那条规则是对的）。
但**根因只修了一半**：口令同步只是「这一次为什么失败」，而**「失败了脚本却报成功」
才是那次事故能一路瞒过去的原因** —— 也就是本节的 `;`。

换句话说：**即使口令再对，下一次任何原因导致 `set-release.py` 失败，
症状仍会和那次一模一样，并且仍然没人会发现。** 这条必须修。

**建议改法**（保留清理、同时把退出码透出来）：

```bash
ssh dfwx "sudo ALLOW_DOWNGRADE='${ALLOW_DOWNGRADE:-0}' VER='${VER}' CODE='${CODE}' SHA='${LOCAL_SHA}' SIZE='${SIZE}' python3 /tmp/set-release.py; rc=\$?; rm -f /tmp/set-release.py; exit \$rc"
```

更稳的是**再加一条回读断言**（不依赖任何退出码）：把 `release` 记录读回来，
断言 `versionCode == CODE` 且 `sha256 == LOCAL_SHA`，不等就 `exit 1`。
这样即使以后有人把退出码又弄丢了，也拦得住。

---

## P1 · `dfwx-publish-apk.sh:51-53` 结尾恒返回 0

**原文：**

```bash
echo "→ 外网可达性检查…"
curl -s -o /dev/null -w "APK  http://39.106.33.135/apk/${NAME} -> %{http_code}\n" "http://39.106.33.135/apk/${NAME}"
echo "完成。"
```

`curl` 的退出码是 `-s` 下的成功退出（除非网络层失败），**HTTP 状态码只被打印、从不判断**；
紧接着的 `echo "完成。"` 是脚本最后一条命令 → 脚本退出码恒为 0。

**实测复现**（用一个确定不存在的文件名，走完脚本最后两行的形状）：

```
$ bash -c 'set -euo pipefail; echo "→ 外网可达性检查…"; \
    curl -s -o /dev/null -w "APK  http://39.106.33.135/apk/${0} -> %{http_code}\n" \
    "http://39.106.33.135/apk/根本不存在-v9.9.9.apk"; echo "完成。"' "根本不存在-v9.9.9.apk"
→ 外网可达性检查…
APK  http://39.106.33.135/apk/根本不存在-v9.9.9.apk -> 404
完成。
  ↳ 退出码 = 0        ← ★ 404 却返回成功
```

**后果**：文件没落盘、nginx 挂了、权限不对、文件名写错 —— 任何一种都仍然报「完成。」，
下游（人 / CI / 后续脚本）无法感知。

**建议改法**：

```bash
code="$(curl -s -o /dev/null -w '%{http_code}' "http://39.106.33.135/apk/${NAME}")"
[[ "$code" == "200" ]] || { echo "❌ 外网取不到 ${NAME}（HTTP $code）" >&2; exit 1; }
```

顺带建议把这一条从「只看 200」升级成「下载回来重算 sha256 与本地一致」——
因为 200 也可能返回一个**截断的**body（本审计就踩到过：见文末「审计过程中的自证」）。

---

## P1 · 两个脚本都不校验「APK 的真实版本号 == 参数里的版本号」

### 3a. `tools/dfwx-publish-apk.sh`

脚本对 `VER` 只做**格式**校验，从不打开 APK 看它到底是什么：

- `:21` `[[ "$VER" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]` —— 只验形状
- `:20` `[[ -f "$APK" ]]` —— 只验文件在不在
- `:29` 算 sha256、`:30` 取大小 —— 只描述这个文件，不判断它是谁
- 全文没有 `aapt`，没有读取包内 `versionName` / `versionCode`（grep 确认，唯一的
  `versionCode` 命中在 `:33` 的 `echo` 里，打印的是**由 VER 推出来的值**）

**实测**：同一份 `v1.0.0` 的包，脚本的全部检查对 `VER=1.0.5` 一样全部通过：

```
$ aapt dump badging <本地目录>/黑曜/03-构建产物/dongfang-wuxian-v1.0.0.apk | grep ^package:
package: name='dfwx.dongdang' versionCode='10000' versionName='1.0.0'

  VER=1.0.0 → 脚本全部检查通过，按 dongfang-wuxian-v1.0.0.apk 上传，序号 10000
  VER=1.0.5 → 脚本全部检查通过，按 dongfang-wuxian-v1.0.5.apk 上传，序号 10005   ← ★ 包其实还是 1.0.0
```

**后果**（比 P0 更隐蔽）：记录里写 `versionName=1.0.5 / versionCode=10005`，
用户下载装上去，系统里实际是 **10000**。App 比 `10000 > 10005` 为假 → 又提示更新 →
**更新死循环**，而且资产名（`v1.0.5.apk`）与包内容不符，事后极难判断。
形态与 DFW-117（后台填 10028、手机装 10034）完全同类。

### 3b. `tools/release.sh` 的 `cmd_publish`

同理：`version="${tag#v}"`（`:210`）、`asset="dongfang-wuxian-v${version}.apk"`（`:211`）
**全部只从 tag 推导**，`cmd_publish` 段（`:201-265`）里没有任何 `aapt`/`versionName` 读取
（aapt 只在 `cmd_build:172` 用于 ABI 断言）。

它的三道核对全部是**自洽性**检查，不是**身份**检查：

| 核对 | 实际比的是 | 能不能发现「包是旧的」 |
|---|---|---|
| 1/3 `:247` | 本机文件的 sha256 ↔ GitHub 上同一个文件的 digest | ❌ 两边是同一个错文件 |
| 2/3 `:251-254` | 直链可访问 + 前 2 字节是 `PK` | ❌ 旧包也是合法 APK |
| 3/3 `:257-258` | README 署名 / 第三方清单 | ❌ 与包无关 |

于是 `release.sh publish v1.0.5 <v1.0.0 的包>` 会打印
**「✅ 发布完成并通过全部核对」**，而资产 `dongfang-wuxian-v1.0.5.apk` 里装出来是 1.0.0。

**建议改法**（两个脚本各加一处断言；这是本次审计里性价比最高的一条）：

```bash
# 取包内真实身份，断言与参数一致
badging="$("$aapt_bin" dump badging "$APK")"
real_name="$(sed -n "s/^package:.*versionName='\([^']*\)'.*/\1/p" <<<"$badging" | head -1)"
real_code="$(sed -n "s/^package:.*versionCode='\([^']*\)'.*/\1/p" <<<"$badging" | head -1)"
[[ "$real_name" == "$VER" ]] || { echo "❌ 包内 versionName=$real_name，参数是 $VER" >&2; exit 1; }
[[ "$real_code" == "$CODE" ]] || { echo "❌ 包内 versionCode=$real_code，推导值是 $CODE" >&2; exit 1; }
```

`release.sh` 里把 `$VER` 换成 `${version}`、`$CODE` 换成由 `version` 推导的值即可。
（注意 `release.sh` 已有 `aapt_bin()` 辅助函数，`dfwx-publish-apk.sh` 需要新增。）

---

## P2 · 发布链没有编排，跑一半就是 DFW-82 的原形

实测调用关系（`grep -rn` 全仓库）：

- `tools/release.sh publish` —— **只**发 GitHub Release，不碰自有服务器
- `tools/dfwx-publish-apk.sh` —— **只**发自有服务器 + PocketBase，不碰 GitHub
- 两者**互不调用**；也没有第三个脚本把它们串起来（`tools/ci-gate.sh` 里没有任何 `publish` 字样）

所以一次完整发版是**三个手工命令、两个脚本**：

```
tools/bump-version.sh              # 改版本号
tools/release.sh build             # 构建 + 归档
tools/release.sh publish   vX.Y.Z  # → GitHub
tools/dfwx-publish-apk.sh  X.Y.Z   # → 自有服务器 + PocketBase
```

漏掉最后一条 = 服务器上还是旧包、App 收不到更新；
漏掉倒数第二条 = GitHub 上没有这一版。
`dfwx-publish-apk.sh:4-6` 的注释说它存在的意义就是"把传包和改记录绑成一次操作"，
但**GitHub 这一路仍然游离在外**。

**建议**：加一个 `tools/release-all.sh`（或在 `release.sh publish` 成功末尾直接调用
`dfwx-publish-apk.sh`），并让它在任一步失败时立即 `exit 1`。三条分发路径要么全绿、要么全红。

---

## P2 · `cmd_build` 不比对「APK 的版本」与「`val appVersionName`」

- `cmd_verify` 从 `app/build.gradle.kts:73-75` 算出 `name` / `code`（唯一数字源），
  但这两个是**局部变量**，只用于同步 `current-state.md`（`:85-90`）。
- `cmd_build` 随后**重新**从 aapt 读 `name` / `code`（`:177-178`），
  归档名（`:183`）和「下一步发什么版本」的提示（`:196`）全用这一份。
- 两处**从不比对**。

**后果**：如果 gradle 因为某种原因没吃到新的版本号（构建复用了旧产物、
`bump-version.sh` 只改了一半、手动改了 `build.gradle.kts` 又改回去），
`cmd_build` 会把**旧包**按**旧名**归档并提示「下一步 publish v旧版本」——全程不报错。

**建议**：`cmd_build` 拿到 aapt 的 `name`/`code` 后，再从 `app/build.gradle.kts`
读一次 `val appVersionName` 并断言相等，不等就 `fail`。

---

## P2 · `release.sh:166` 的 `ls | head -1`

```bash
apk="$(ls app/build/outputs/apk/empty/release/*.apk 2>/dev/null | head -1 || true)"
```

`ls` 按字典序输出，`head -1` 取第一个。**当前该目录只有一个文件**
（`app-empty-release.apk`），所以今天不会出错；但一旦出现第二个 `.apk`
（换 ABI/产物名、上一次构建的残留），脚本会**静默挑字典序最小的那个**。

已有两道部分兜底：`:173-174` 会断言 ABI 是 arm64-v8a（v1.18.0 事故的守卫），
`:177` 从选中的包读 versionName。所以最坏情况是"归档并发布一个版本号看起来正常的旧包"。

**建议**：断言恰好一个，否则 `fail`：

```bash
mapfile -t apks < <(ls app/build/outputs/apk/empty/release/*.apk 2>/dev/null || true)
[[ "${#apks[@]}" -eq 1 ]] || fail "release 目录下有 ${#apks[@]} 个 APK，无法确定发哪个：${apks[*]}"
apk="${apks[0]}"
```

---

## P3 · `release.sh:184` 归档会被静默覆盖

```bash
local target="$ARCHIVE_DIR/dongfang-wuxian-v${name}.apk"
cp "$apk" "$target"
```

同一版本重跑 `build`，会用新的字节**覆盖**同名归档。
脚本遵守了「不删旧包」（不删别的版本），但**同一个版本**的旧字节会无声消失 ——
如果那份正是已经发出去的包，就失去了可比对的存档。

**建议**：`[[ -e "$target" ]]` 时改存 `-prev-<时间戳>` 或直接 `fail` 让人确认。

---

## P3 · `set-release.py:55` 用 `<` 而不是 `<=`

```python
if code < current:      # 相等时不拦
```

CLI 路径允许**重复发布同一个 versionCode**（会原地覆盖记录），
而控制台（`server/dfwx-admin/index.html` 的 `guardVersionTransition`，publish 模式）
对相等是**硬拦**的。两条路径规则不一致。

CLI 显式传 `CODE`，相等很可能是"重发一个修过的包"的有意行为，所以定级 P3 而不是 P0。
但建议要么统一，要么在卡/文档里写明「CLI 允许同序号重发，控制台不允许」——
否则下次有人按控制台的规则去理解 CLI，会以为这条守卫存在。

---

## 已经做对的部分（不要改坏）

审计同时确认下面这些**是对的**，改动时别把它们弄丢：

1. `tools/release.sh:24` `set -euo pipefail` —— 整个脚本的失败是可见的
   （唯一的例外就是上面 P0，那条把失败挡在了 `ssh` 里面）。
2. `tools/release.sh:157` `cmd_build` **强制先跑 `cmd_verify`** —— 这正是 DFW-91 对
   v1.0.15「门禁红了还照样发布」的修复，目前已生效（`:93-95` 的 ci-gate 调用是裸命令，
   在 `set -e` 下失败即中止，等价于 `&&`）。
3. `tools/release.sh:230` 发布**默认 dry-run**，必须显式 `RELEASE_DRY_RUN=0` 才真发。
4. `tools/release.sh:241-254` 上传后核对资产名 / sha256 digest / 直链可取且带 `PK` 头 ——
   三道都对**自洽性**有效（只是对"包身份"无效，见 P1-3）。
5. `tools/dfwx-publish-apk.sh:38-42` 远端 sha256 与本地不一致时**中止且不写记录** —— 这条是对的。
6. `tools/release.sh:120-121,126` 的 `grep -q ... && fail ...` 在 `set -e` 下**语义正确**
   （grep 不在 `&&` 列表末尾，它失败不会触发 `set -e`）—— 不是 bug，别"修"。
7. `tools/bump-version.sh` 全程 `set -euo pipefail`，且 `code_of` 用 `return 1` +
   显式 `|| exit 2`，改不动就报错退出；`:78-82` 拒绝序号不增。没找到静默失效点。

---

## 附：一次差点被我误报成事故的截断（并且它正好证明了 P1-2）

用 `curl` 验证 GitHub 资产时，我第一次加了 `--max-time 300`：

```
HTTP 200  size=23493953  time=300.009771s
本地重算 sha256: f2b2f709ecc82c6d297b401f6144cc8e3fd9fb5d77ca47a9fbb156791f7f7f00
期望        : f3dfe0ec8123f65869a9e38057808c013e145b91c286d214402844c41031e042
```

看起来像「GitHub 上的包 sha256 对不上」——**实际是我自己的超时把下载截断了**
（只拿到 23493953 / 36837310 字节）。直链下载约 95 KB/s，36.8 MB 要 6 分多钟。

这条正好印证上面 **P1 第 2 条**的建议：**光看 `HTTP 200` 不足以证明「能下到包」**，
必须把字节数和 sha256 也断言上 —— 我这一条命令就是活证据：状态码 200，内容却是半截。

---

# 二、线上状态四方一致（独立重算，未采信任何转述数字）

全部由本机/服务器**重新计算**，没有引用任何人给的数值。

| 来源 | 取法 | size | sha256 |
|---|---|---|---|
| 本地归档 | `shasum -a 256 <本地目录>/黑曜/03-构建产物/dongfang-wuxian-v1.0.0.apk` | 36837310 | `f3dfe0ec8123f65869a9e38057808c013e145b91c286d214402844c41031e042` |
| 自有服务器 | `curl -o /tmp/srv.apk http://39.106.33.135/apk/dongfang-wuxian-v1.0.0.apk` 后重算 | 36837310 | `f3dfe0ec…` |
| GitHub Release | 完整下载 `test-20261002-44` 的资产后重算 | 36837310 | `f3dfe0ec…` |
| PocketBase | `release` 集合记录的 `size` / `sha256` 字段 | 36837310 | `f3dfe0ec…` |

**四方两两一致 —— 没有事故。**

关键原始输出：

```
$ shasum -a 256 <本地目录>/黑曜/03-构建产物/dongfang-wuxian-v1.0.0.apk
f3dfe0ec8123f65869a9e38057808c013e145b91c286d214402844c41031e042
$ stat -f%z …/dongfang-wuxian-v1.0.0.apk
36837310

$ ssh dfwx 'sha256sum /var/www/dfwx/apk/dongfang-wuxian-v1.0.0.apk; stat -c "%s bytes" …'
f3dfe0ec8123f65869a9e38057808c013e145b91c286d214402844c41031e042
36837310 bytes

$ curl -sL -o /tmp/gh-full2.apk -w "HTTP %{http_code} size=%{size_download} speed=%{speed_download}B/s\n" \
    https://github.com/dongwangxingchen/dongfang-wuxian/releases/download/test-20261002-44/dongfang-wuxian-v1.0.0.apk
HTTP 200 size=36837310 time=377.234966s speed=97650B/s
$ shasum -a 256 /tmp/gh-full2.apk
f3dfe0ec8123f65869a9e38057808c013e145b91c286d214402844c41031e042

$ gh release view test-20261002-44 --repo dongwangxingchen/dongfang-wuxian --json assets
"name":"dongfang-wuxian-v1.0.0.apk","size":36837310,
"digest":"sha256:f3dfe0ec8123f65869a9e38057808c013e145b91c286d214402844c41031e042",
"downloadCount":2,"state":"uploaded"
```

顺带核验「用户装上去的确实是个合法包」：

```
$ aapt dump badging /tmp/srv.apk | grep -E "^package:|native-code"
package: name='dfwx.dongdang' versionCode='10000' versionName='1.0.0'
native-code: 'arm64-v8a'

$ apksigner verify --print-certs /tmp/srv.apk
V2 Signer: certificate DN: CN=Dongfang Wuxian, OU=Android, O=Dongfang Wuxian, C=CN
V2 Signer: certificate SHA-256 digest: 93898e05e64f294c0c105b73898e0dd4cf24e12ff4d17912f0d67d9145eefc2e
```

包名 / versionCode / versionName / ABI 与 PocketBase 记录、与 GitHub 资产**全部吻合**，
签名有效（V2）。

---

# 三、三条分发路径到底能不能下到包

先说一个**纠正**：卡里写的第三条「网页控制台 `https://39.106.33.135/admin/` 的下载页」
**不存在**。`/admin/` 是**发布用的管理后台**，里面没有任何面向用户的下载入口
（全文只有两处 `/apk/`，都是内部拼 `apkUrl` 用的：`index.html:227` 的注释、
`:819` 的 `APK_ORIGIN + "/apk/" + f.name`）。
`current-state.md:37` 提到的「下载页」指的是**App 内的下载页**（UI-003），不是网页。

实测的公开入口全景：

```
$ for u in / /health /api/notice.json /api/version.json /apk/ /apk/<包> /admin/ /pb/; do curl -o /dev/null -w "%{http_code}" $u; done
http://39.106.33.135/                                HTTP 403   ← 站点根没有索引页
http://39.106.33.135/health                          HTTP 200   application/json
http://39.106.33.135/api/notice.json                 HTTP 200   application/json
http://39.106.33.135/api/version.json                HTTP 200   application/json（废弃，见第四节）
http://39.106.33.135/apk/                            HTTP 404   ← 目录**没有** autoindex
http://39.106.33.135/apk/dongfang-wuxian-v1.0.0.apk  HTTP 200   application/vnd.android.package-archive
https://39.106.33.135/admin/                         HTTP 200   管理后台（不是下载页）
https://39.106.33.135/pb/                            HTTP 302   → /admin/
```

nginx 配置印证：80 端口的 `/apk/` 是 `try_files $uri =404;`（`sites-available/dfwx:52-59`），
**没有 `autoindex on`**，所以列目录 404、但具体文件正常。

逐条结论：

| 分发路径 | 状态 | 证据 |
|---|---|---|
| **GitHub Release 资产** | ✅ 能下 | 完整下载 36837310 字节，sha256 完全吻合；`gh release view` 的 digest 也一致；`-r 0-1023` 返回 206 且前两字节是 `PK` |
| **`http://39.106.33.135/apk/<文件名>`** | ✅ 能下（**但必须知道确切文件名**） | 完整下载 36837310 字节 + sha256 吻合 + aapt/apksigner 核验通过。目录本身 404 |
| ~~网页控制台下载页~~ | ❌ **该路径不存在** | `/admin/` 是发布后台；`/` 是 403；全站没有面向用户的下载页 |
| App 内下载页（真实路径） | ✅ 数据源可用 | App 从 PocketBase 取 `apkUrl`（`RemoteConfigClient.java:37-38`，`BASE="http://39.106.33.135/pb"`），该 URL 就是上面第二个 ✅ |

> 也就是说：**目前真正可用的分发面是「GitHub + 自有服务器直链」两条**，
> 第三条在 App 里（读的是第二条的地址）。想给用户一个网页下载页，得**新建**，
> 不是"核实一下就能用"。

---

# 四、DFW-122 第 5 条：`/api/version.json` 该退役还是该标注

**结论：该退役（至少必须删掉里面的 `latest` 块）。现状「标注 deprecated」不够。**

### 1）它还在，而且内容已经和事实相反

```
$ curl -s http://39.106.33.135/api/version.json | head -12
{ "version": 1, "deprecated": true, "channel": "stable",
  "latest": {
    "versionName": "1.0.21",  "versionCode": 10021,
    "apkUrl": "http://39.106.33.135/apk/dongfang-wuxian-v1.0.21.apk",
    "size": 36824033,
    "publishedAt": "2026-10-01T17:10:00+08:00", … },
  "notes": ["⚠️ 本文件已废弃，没有任何代码读它，保留仅为兼容旧链接。", …] }
```

线上真实版本是 **1.0.0 / 10000**，而它说 **1.0.21 / 10021**。
`/var/www/dfwx/api/version.json`，786 字节，mtime 2026-10-02 01:16。

### 2）"已无调用方"——核实结果：**源码从未引用过它**

```
$ git log --oneline --all -S 'version.json' -- '*.java' '*.kt' '*.kts'
（空）
$ git log --oneline --all -S 'notice.json' -- '*.java' '*.kt' '*.kts'
（空）
$ grep -rn 'version\.json' <本地目录>/src --exclude-dir=.git --exclude-dir=build
只有 4 个文档提到（全是审计/计划/交接类），**没有任何代码**
```

当前 App 的更新源是**唯一**的（`RemoteConfigClient.java:37-38`）：

```java
static final String BASE = "http://39.106.33.135/pb";
private static final String API = BASE + "/api/collections/";
```

### 3）为什么"标注"不够：`deprecated` 是给人看的，`latest` 是给机器读的

任何按字段读的客户端只会看 `latest.versionCode = 10021`，
**不会去读兄弟节点里的 `notes`**。而 10021 > 10000，所以：

- 如果哪个环节（旧链接、第三方脚本、将来某个 agent、用户拿浏览器打开）拿它当事实源，
  得到的结论是「有更新，去装 1.0.21」；
- 而 `apkUrl` 指向的 `dongfang-wuxian-v1.0.21.apk` **确实还在服务器上、确实能下**
  （`/var/www/dfwx/apk/` 下 30 个包里就有它）。
  于是这条废弃链路会**把一个更老的构建当成升级包发出去**——不是 404 那种一眼可见的坏，
  而是 DFW-117 那种"装完还提示更新 / 版本对不上"的隐蔽坏。

`deprecated: true` 只在一处生效：**人打开 JSON 看**。这正是"标注"与"退役"的差别。

### 4）建议（由 Lead 执行；本卡不动服务器）

优先做 A：

- **A（推荐，一行改动，不破坏旧链接）**：删掉 `latest` 块，只留 `deprecated` + `notes`。
  这样任何机器读它都拿不到版本号，旧链接也不会 404。
- **B（更彻底）**：直接删 `/var/www/dfwx/api/version.json`。
  旧链接变 404 —— 比返回错误数据好，但会丢掉"这个端点曾经存在"的线索。
- **C（不推荐）**：维持现状。只要 `latest` 还在，就始终留着一颗
  "把旧包当新版发出去"的雷。

顺带：`/api/*.json` 的 nginx 块已经带了 `Cache-Control: no-store`（`sites-available/dfwx:41-50`），
所以**没有**"客户端缓存了旧数据"这一层额外风险 —— 退役时不用考虑缓存失效。

