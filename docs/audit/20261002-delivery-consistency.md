# 2026-10-02 交付一致性与文档可信度审计

> **审计对象**：`/Users/<用户名><仓库根>`（分支 `test`，HEAD `485cff3`）
> **审计方式**：**只读**。全部结论由实际执行的命令产出，逐条附原始输出片段（不转述）。
> **审计时间**：2026-10-02 10:16–10:25 CST（服务器 UTC 02:16–02:23）
> **遵守的硬约束**：未跑 gradle；未提交、未 push；未修改服务器任何文件（只读命令）；
> 唯一写入的文件 = 本文件（`docs/audit/20261002-delivery-consistency.md`）。
> **背景问题**：用户要求「**一定要在 GitHub 上规范的做好备份，不要出什么问题**」。
> 本审计回答的是"到底有没有出问题"，不是"文档怎么写"。

---

## 0. 结论摘要

### 0.1 三件最需要处理的事

| # | 问题 | 严重度 | 一句话证据 |
|---|---|---|---|
| **1** | **CI 门禁在 GitHub 上从未绿过一次**：最近 100 次运行 **0 成功**（全仓 241 次仅 1 次成功，还是 09-21 手动跑的旧工作流）。根因是工作流里 SDK 包名写错——`platforms;android-37` 应为 `platforms;android-37.0`；冒烟工作流还额外踩了 `setup-android` 默认安装已下线的 `tools`。而 `risk-register.md` R-13 却写「已闭环」 | **P1** | `gh run list --limit 100` 计数 → `failure 96 / cancelled 4 / success 0`；日志 `Failed to find package 'platforms;android-37,build-tools;37.0.0'` |
| **2** | **交接唯一入口 `docs/handover/README.md` 停在 1.0.21，落后 4 个版本**；§4「下一步」表里 5 张卡（DFW-91/90/96/95/97）实际全部已发完。这已是**第二次**被审计点出（上一轮 20261001 审计第 7 条："§0 写 1.0.1，实际是 1.0.3"）——同一处连续腐烂 | **P1** | 文件 §0 原文 `版本 **1.0.21（versionCode 10021）**` vs `val buildCode = 10025` |
| **3** | **Release 呈现层三处不一致**：① README 的 Release 徽章在公网显示 **"no releases or repo not found"**（68 个 release **全是** prerelease，GitHub `/releases/latest` = 404）；② 1.0.x 全部以 `test-YYYYMMDD-N` 标签发布，**标签名与版本号完全脱钩**，最新 5 个 release 标题还丢了 `(vX.Y.Z)`；③ 被 commit message 判定「因门禁红**作废**」的 v1.0.15（`test-20261001-24`）在 Releases 页和服务器上**仍是正常可下载的预发布**，作废只存在于 commit message 里 | **P2** | `curl img.shields.io/...` → `<title>release: no releases or repo not found</title>` |

### 0.2 已核对一致的清单（逐条实测通过）

| 项 | 结论 | 证据位置 |
|---|---|---|
| 版本号五处一致 | `buildCode=10025` → versionName `1.0.25`；current-state 版本行 `10025/1.0.25`；GitHub 资产 `dongfang-wuxian-v1.0.25.apk`；服务器同名文件；PocketBase `versionCode:10025 / versionName:"1.0.25"` | §1 |
| **sha256 四方一致** | GitHub asset digest = 服务器 `sha256sum` = PocketBase `sha256` = 本机黑曜归档，全部 `85f0e02a1a23…4785` | §1.3 |
| size 三方一致 | `36830986` = GitHub asset size = 服务器文件大小 = PocketBase `size` | §1.3 |
| **服务器 27 个 APK 全量比对** | 26 个与 GitHub 同名资产 **sha256 完全相同，0 个不同**；剩下 1 个是 `v1.0.0.apk`（GitHub 无此名，见 §2.4） | §3.2 |
| GitHub 备份完整性 | 68 个 release ↔ 68 个远端 tag，**1:1 无缺**；每个 release **恰好 1 个** APK 资产且 `state=uploaded`（0 个缺失）；`test-20261001-1..34` 与 `test-20260930-1..4` **连续无缺号** | §2.1–2.2 |
| 没有"发了 release 但 commit 没 push" | 68 个 tag 的 commit **全部存在于本地且全部是 `test` 分支祖先**；`HEAD` = 远端 `test` = 最新 release 的 `targetCommitish` = `485cff3` | §2.3 |
| 远端 tag 与 release 目标一致 | 34 个以 SHA 作为 `targetCommitish` 的 release，**逐个与自身 tag 的 commit 完全相同**（`match=34 mismatch=0`）；其余 34 个以分支名（`test`/`main`）为目标，属正常 | §2.3 |
| 工作树零改动 | `git status --short` 仅 `?? docs/handover/`，且 `AGENTS.md` 明示该目录**故意不提交**，符合设计 | §2.5 |
| 服务器接口正常 | `/health` → `{"status":"ok"}`；`/pb/` → 302 `/admin/`；`/pb/api/...` **不跳转**（A6 的关键约束成立）；APK 头 `Cache-Control: public, max-age=3600` | §3.3 |
| 更新链路不会被旧版本线劫持 | GitHub 兜底对 `prerelease/draft` **硬拒绝**（`UpdateClient.java:52`），且 `/releases/latest` 实测 404 被当作"无正式版"而非故障 → **不会把 v1.22.19（versionCode 1039039）当成更新推给 1.0.25 用户** | §3.4 |
| 本地 docs 门禁可跑通 | `DFWX_OFFLINE=1 bash tools/ci-gate.sh docs` → `✅ 文档与卫生门禁通过`（exit 0）→ CI 的红**不是门禁脚本的问题**，是环境问题 | §5.3 |
| PocketBase 记录唯一性 | `totalItems: 1`，与"控制台只维护一条当前记录"的设计一致（历史归档靠 GitHub + 服务器文件） | §1.2 |

### 0.3 两个遗留项确认：**都还在**

| 遗留项 | 结论 | 原始证据 |
|---|---|---|
| `/var/www/dfwx/apk/t2.txt` 是否还在 | **还在**，6 字节内容 `hello`，且**公网可读** | `-rw-r--r-- 1 www-data www-data 6 Oct  1 03:10 t2.txt`；`curl -sI http://39.106.33.135/apk/t2.txt` → `HTTP/1.1 200 OK`，body `hello` |
| 80 端口 `/admin/` 有没有 `Cache-Control` | **没有**。HTTP 只有 `ETag`/`Last-Modified`；HTTPS 才有 `Cache-Control` | `curl -sI http://39.106.33.135/admin/` 无该头；`curl -skI https://39.106.33.135/admin/` → `Cache-Control: no-store, no-cache, must-revalidate, max-age=0` |

---

## 1. 版本号一致性 ✅（五处全对）

### 1.1 唯一数字源：`app/build.gradle.kts`

```console
$ grep -n -E 'buildCode|versionName|versionCode' app/build.gradle.kts
56:  val buildCode = 10025
57:  versionCode = buildCode
58:  versionName = "${buildCode / 10000}.${(buildCode / 100) % 100}.${buildCode % 100}"
```

`10025` → `1.0.25`，与 DFW-91 的"只写一个数字"设计一致。

### 1.2 事实页 / Release / 服务器 / PocketBase

```console
$ grep -n '版本号：' docs/plan/current-state.md
21:  - 版本号：versionCode `10025` / versionName `1.0.25`（`app/build.gradle.kts`）

$ gh release view test-20261001-34 --json tagName,name,isPrerelease,targetCommitish,assets
{"assets":[{"digest":"sha256:85f0e02a1a2392ca70474ccb1d67e249da374366eb21cd18d3162f4db3904785",
"name":"dongfang-wuxian-v1.0.25.apk","size":36830986,"state":"uploaded", ...}],
"isPrerelease":true,"name":"test-20261001-34","tagName":"test-20261001-34",
"targetCommitish":"485cff3cd846c9615fa56d625693834bd0335d23"}

$ ssh dfwx 'ls -la /var/www/dfwx/apk/ | tail -3'
-rw-r--r-- 1 www-data www-data 36825361 Oct  2 03:11 dongfang-wuxian-v1.0.24.apk
-rw-r--r-- 1 www-data www-data 36830986 Oct  2 10:14 dongfang-wuxian-v1.0.25.apk
-rw-r--r-- 1 www-data www-data        6 Oct  1 03:10 t2.txt

$ curl -s 'http://39.106.33.135/pb/api/collections/release/records?perPage=3&sort=-id'
{"items":[{"apkUrl":"http://39.106.33.135/apk/dongfang-wuxian-v1.0.25.apk",
"collectionName":"release","id":"kev9q0jrpyo1xy0",
"sha256":"85f0e02a1a2392ca70474ccb1d67e249da374366eb21cd18d3162f4db3904785",
"size":36830986,"updateMode":"soft","versionCode":10025,"versionName":"1.0.25"}],
"page":1,"perPage":3,"totalItems":1,"totalPages":1}
```

> 注：`handover/README.md:73` 写的 PocketBase 记录 id `kev9q0jrpyo1xy0` **是对的**——只是同一行的版本号过期了。

### 1.3 sha256 四方一致 + size 三方一致

```console
$ ssh dfwx 'sha256sum /var/www/dfwx/apk/dongfang-wuxian-v1.0.25.apk'
85f0e02a1a2392ca70474ccb1d67e249da374366eb21cd18d3162f4db3904785  /var/www/dfwx/apk/dongfang-wuxian-v1.0.25.apk

$ shasum -a 256 <本地目录>/黑曜/03-构建产物/dongfang-wuxian-v1.0.25.apk
85f0e02a1a2392ca70474ccb1d67e249da374366eb21cd18d3162f4db3904785  .../dongfang-wuxian-v1.0.25.apk

$ curl -sI http://39.106.33.135/apk/dongfang-wuxian-v1.0.25.apk
HTTP/1.1 200 OK
Content-Type: application/vnd.android.package-archive
Content-Length: 36830986
Cache-Control: public, max-age=3600
X-Content-Type-Options: nosniff
```

GitHub digest、服务器、PocketBase、本机归档**四份 sha256 完全相同**；size 三处相同。
这一条是用户最关心的"备份没出问题"的**核心正面结论**。

---

## 2. GitHub 备份完整性

### 2.1 总量：68 release ↔ 68 tag，1:1

```console
$ git ls-remote --tags backup | wc -l
      68
$ gh release list --limit 100 | wc -l
      68

$ gh api 'repos/dongwangxingchen/dongfang-wuxian/releases?per_page=100' --paginate \
    --jq '.[] | [.tag_name, (.assets|length), (.assets[0].name // "NO-ASSET"), .target_commitish, .prerelease] | @tsv'
--- releases with 0 assets ---            (无输出)
--- asset-count distribution ---
  68 1
--- asset states distribution ---
  68 uploaded
--- releases with NO-ASSET or draft/prerelease=false ---   (无输出)
```

**每个 release 都恰好有 1 个 APK 资产，且状态全是 `uploaded`**；没有空 release、没有 draft。
同时注意：**68 个 release 全部 `prerelease=true`**，没有一个正式版（见 §4.3）。

### 2.2 缺号检查

```console
$ git ls-remote --tags backup | awk '{print $2}' | sed 's#refs/tags/##' | sort -V
test-20260930-1 … test-20260930-4          ← 4 个，连续
test-20261001-1 … test-20261001-34         ← 34 个，连续
v1.19.1–v1.19.9, v1.20.0, v1.21.0, v1.21.2, v1.21.3, v1.21.4, v1.21.7, v1.21.8,
v1.21.9, v1.22.0–v1.22.11, v1.22.19

$ ... | grep -o 'test-20261001-[0-9]*' | sed 's/.*-//' | sort -n | tr '\n' ' '
1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34
```

**近期序列连续无缺号**（`test-20261001-1..34` 一个不缺）。
但**历史序列有真实缺号**（旧版本线）：

| 缺号 | 数量 | 有没有本地归档兜底 |
|---|---|---|
| `v1.21.1`、`v1.21.5`、`v1.21.6` | 3 | 未查（不在本轮范围） |
| **`v1.22.12` – `v1.22.18`** | **7** | **没有**。`<本地目录>/黑曜/03-构建产物/` 里也没有这几个版本的 APK |

```console
$ for v in 1.22.12 … 1.22.18; do ls <本地目录>/黑曜/03-构建产物/dongfang-wuxian-v$v.apk; \
    gh release view "v$v" --json tagName; done
v1.22.12  localArchive=MISSING  ghRelease=NONE
v1.22.13  localArchive=MISSING  ghRelease=NONE
v1.22.14  localArchive=MISSING  ghRelease=NONE
v1.22.15  localArchive=MISSING  ghRelease=NONE
v1.22.16  localArchive=MISSING  ghRelease=NONE
v1.22.17  localArchive=MISSING  ghRelease=NONE
v1.22.18  localArchive=MISSING  ghRelease=NONE
```

**这 7 个版本号已被消耗，但产物在 GitHub 和本机归档里都不存在**——而 `current-state.md` §1.5 明确
记载 DFW-12/13/14/16/17 分别对应 v1.22.14–v1.22.18。也就是说那批工作的**代码在 git 里、APK 没有备份**。
按用户的"规范备份"要求，这是 §2 里唯一的实质缺口（属历史，不影响当前 1.0.x 线）。

### 2.3 tag ↔ commit 对齐：没有"发了 release 但 commit 没 push"

```console
$ git ls-remote backup
485cff3cd846c9615fa56d625693834bd0335d23	HEAD
e36da3817a49b2f22d51170eb5826362235a762d	refs/heads/main
485cff3cd846c9615fa56d625693834bd0335d23	refs/heads/test
...
485cff3cd846c9615fa56d625693834bd0335d23	refs/tags/test-20261001-34

$ git rev-parse HEAD
485cff3cd846c9615fa56d625693834bd0335d23

# 68 个 tag 的 commit：本地是否齐、是否是 test 分支祖先
$ while IFS=$'\t' read -r t sha; do
    git cat-file -e "${sha}^{commit}" || echo "LOCAL-MISSING $t";
    git merge-base --is-ancestor "$sha" test || echo "NOT-IN-test $t";
  done < <(git ls-remote --tags backup | awk '{print $2"\t"$1}' | sed 's#refs/tags/##')
local-missing=0  not-in-test-branch=0

# release 的 targetCommitish 是否等于它自己 tag 的 commit
$ join ... | awk '{ if ($2==$5) ok++; else print "MISMATCH " $1 } END { print "match=" ok " mismatch=" bad+0 }'
match=34 mismatch=0
```

结论：**HEAD = 远端 test = 最新 release 的 targetCommitish = `485cff3`**，
68 个 tag 的 commit 全部在本地且全部是 `test` 分支祖先。**不存在"release 发了但代码没 push"的情况。**

### 2.4 v1.0.0：GitHub 上没有，服务器上那个是**改名件**

```console
$ gh api '.../releases?per_page=100' --paginate \
    --jq '.[] | select(.assets[0].name=="dongfang-wuxian-v1.0.0.apk") | .tag_name'
(无输出)

# 服务器 v1.0.0 的哈希 vs GitHub 上 test-20261001-9 资产的哈希
$ ssh dfwx 'sha256sum /var/www/dfwx/apk/dongfang-wuxian-v1.0.0.apk'
124140b22e773ed6808043e5d580863d603bc2d4ca3ab42cd8177221a13be825  /var/www/dfwx/apk/dongfang-wuxian-v1.0.0.apk

$ gh api '.../releases/tags/test-20261001-9' --jq '.assets[0] | [.name,.size,.digest]'
dongfang-wuxian-test-20261001-9.apk  36665209  sha256:124140b22e773ed6808043e5d580863d603bc2d4ca3ab42cd8177221a13be825
```

**两者 sha256 与大小完全相同**（36665209）。即：服务器上名为 `dongfang-wuxian-v1.0.0.apk` 的文件
**不是 v1.0.0 的构建产物**，而是 GitHub 上 `test-20261001-9` 那个包的改名副本。
GitHub 上 1.0.x 命名资产共 **25 个（v1.0.1 … v1.0.25）**，**没有 v1.0.0**。
这与 `handover/README.md` 的存量卡记载一致（"DFW-64 官方 `v1.0.0` GitHub release 未发布 —— 等用户拍板"），
但**文件名会误导**：看到 `v1.0.0.apk` 会以为那是基线包。

### 2.5 工作树状态（符合设计，不算问题）

```console
$ git status -sb
## test
?? docs/handover/

$ git branch -vv
  main fded1f7 v1.2.1 三重复审修复（接手 ZCode 会话后按用户 91 条历史消息逐条复核）
* test 485cff3 版本号 1.0.24 -> 1.0.25（tools/bump-version.sh 写入）
```

`docs/handover/` 是 untracked，但 `AGENTS.md` 明写"**该目录故意不提交、不 push，是本机本地文件**"，
`handover/README.md:6` 也自述"本目录故意不提交 git"。**这是设计，不是备份事故。**
但请留意其副作用：**这个"30 秒接手唯一入口"不在 GitHub 备份范围内**，换机器就没了
（`AGENTS.md` 已给出降级路径：从 `docs/plan/` 读起）。

> 另注：本地只有 9 个 tag（`v1.19.1–v1.20.0`、`dfwx-pre-255-import`），1.22.x 与 `test-*` 系列 tag
> 只在远端。不影响备份（tag 在远端就是备份），但**本地 `git tag -l` 不能用来盘点发布历史**，
> 必须用 `git ls-remote --tags backup` 或 `gh release list`。

---

## 3. 服务器状态（只读核查）

### 3.1 资产盘点

```console
$ ssh dfwx 'ls -la /var/www/dfwx/apk/; ls -1 /var/www/dfwx/apk/*.apk | wc -l'
... dongfang-wuxian-v1.0.0.apk … dongfang-wuxian-v1.0.25.apk …
-rw-r--r-- 1 www-data www-data 36633009 Sep 30 13:54 dongfang-wuxian-v1.22.19.apk
-rw-r--r-- 1 www-data www-data        6 Oct  1 03:10 t2.txt
27
```

27 个 APK = `v1.0.0`–`v1.0.25`（26 个）+ `v1.22.19`（旧线存档）。**最新的是 v1.0.25，正确。**
本机归档也齐：`v1.0.1`–`v1.0.25` 全在（仅 `v1.0.0` 无同名归档，原因见 §2.4）。

### 3.2 全量哈希比对：26/26 一致，0 不一致

```console
$ ssh dfwx 'cd /var/www/dfwx/apk && sha256sum *.apk'      # 27 行
$ gh api '.../releases?per_page=100' --paginate --jq '.[] | [.assets[0].name, .assets[0].digest] | @tsv'   # 68 行

$ python3 <按文件名比对>
server files=27  identical=26  differing=0  no-same-name-on-github=1
no-same-name-on-github: ['dongfang-wuxian-v1.0.0.apk']
```

**服务器上每一个 APK 都与 GitHub 同名资产的 sha256 完全一致，没有一个对不上。**
唯一"对不上名字"的就是 §2.4 的 `v1.0.0.apk`（GitHub 无此名，内容等于 `test-20261001-9`）。

### 3.3 接口与遗留项

```console
$ curl -s http://39.106.33.135/health
{"status":"ok","service":"dfwx-backend"}

$ curl -sI http://39.106.33.135/pb/
HTTP/1.1 302 Moved Temporarily
Location: http://39.106.33.135/admin/

$ curl -sI 'http://39.106.33.135/pb/api/collections/release/records?perPage=1'
HTTP/1.1 200 OK                      ← 关键：/pb/ 精确匹配 302 没有误伤 App 数据路径
Content-Type: application/json

$ curl -sI http://39.106.33.135/admin/          ← 80 端口：无 Cache-Control
HTTP/1.1 200 OK
Last-Modified: Thu, 01 Oct 2026 10:30:36 GMT
ETag: "6abe364c-9979"
X-Loc: root

$ curl -skI https://39.106.33.135/admin/        ← 443 端口：有
HTTP/1.1 200 OK
Cache-Control: no-cache
Cache-Control: no-store, no-cache, must-revalidate, max-age=0

$ curl -sI http://39.106.33.135/apk/t2.txt ; curl -s http://39.106.33.135/apk/t2.txt
HTTP/1.1 200 OK
Content-Type: text/plain
hello
```

nginx 配置侧证据（`grep -rn Cache-Control /etc/nginx/`）：`Cache-Control` 只出现在
`dfwx:44`（`location ~ ^/api/.*\.json$`）、`dfwx:54`（`location /apk/`）、`dsh-remote:89`——
**80 端口的 `/admin/` 落在"其他静态文件"里，没有任何 `Cache-Control`**。遗留项确认仍在。

### 3.4 更新链路：不会被旧版本线劫持 ✅（这条是"差点出事但没出"）

风险假设：GitHub 上最新**正式版**是 `v1.22.19`（versionCode 1039039），比 1.0.25（10025）**数值更大**，
若更新检查去读 GitHub 的 latest，可能把用户"升级"回旧版本线。**实测不会**：

```console
$ gh api 'repos/dongwangxingchen/dongfang-wuxian/releases/latest'
{"message":"Not Found", ... "status":"404"}

# app/src/main/java/cc/nkbr/lanzouplus/UpdateClient.java
42:           我们把全部旧版本标成预发布之后，GitHub 的 /releases/latest 就是 404；
52:    if(release.optBoolean("draft")||release.optBoolean("prerelease"))throw new IOException("更新信息不是正式版本");
```

代码对 `prerelease/draft` **硬拒绝**，且把 404 当"这个源上没有正式版本"（`isNoReleaseError`）→ 返回"已是最新"。
另外 `MainActivity.java:1220-1235`：后台可达时以后台为准、**不再打 GitHub 兜底**。
**结论：当前不存在"把用户升级回 1.22.19"的路径。**

---

## 4. 文档可信度（逐条抽查）

抽查方法：文档写的每个数字，用命令实测；对不上的原样记录。

### 4.1 抽查结果表

| 文档 / 行 | 文档写的 | 实测 | 判定 |
|---|---|---|---|
| `handover/README.md:12` | 版本 **1.0.21（versionCode 10021）** | `val buildCode = 10025` → **1.0.25** | ❌ **落后 4 个版本** |
| `handover/README.md:72` | 发布 `test-20261001-29`（v1.0.20）→ 本批 v1.0.21 | 最新是 `test-20261001-34`（v1.0.25） | ❌ 过期 |
| `handover/README.md:123-131` | §4 表：DFW-92/91/90/96/95/97 = 「待做 / 下一个 / 未查」 | 全部已发版：`f32ca6` DFW-91、`02506e5` DFW-90/95/96/103、`ea2585b` DFW-97 | ❌ **5 张卡实际已完成** |
| `handover/README.md:73` | PB 记录 id `kev9q0jrpyo1xy0` | `curl` 实测 id 正是 `kev9q0jrpyo1xy0` | ✅ 对 |
| `current-state.md:21` | versionCode `10025` / versionName `1.0.25` | 一致 | ✅ |
| `current-state.md:4` | 更新日期：2026-10-01（**v1.0.8**：修下载失败真根因…） | 正文 §1 已写到 1.0.25 | ❌ **抬头停在 v1.0.8** |
| `current-state.md:14` | "v1.22.10 改动尚未提交，见 §1.2" | 早已提交（09-29 的事） | ❌ 过期 |
| `current-state.md:15` | 工作树源码零改动；`docs/handover/` untracked 且故意不提交 | `git status --short` → `?? docs/handover/`，源码确实零改动 | ✅ |
| `current-state.md:130` | 测试规模：宿主 51 → **160+ 例**；vendor 240 → **252 例**；common **14 例** | 静态 `@Test` 计数：宿主 **757**（89 文件）、vendor **639**（97 文件）、common **14** | ⚠️ common 对；宿主/vendor 是 09-30 的旧数字（`@Test` 数 ≠ 执行用例数，但差距过大） |
| `risk-register.md:4` | 更新日期 2026-10-01（**v1.0.3**） | 当前 1.0.25 | ❌ 抬头过期 |
| `risk-register.md:8` | 测量基线 HEAD `185e747` | 当前 `485cff3` | ⚠️ 已声明为历史基线，可接受 |
| `risk-register.md` **R-10** | `MainActivity.java` `185e747`=4242 行、`5d9dd40`=4267 行 | `git show 185e747:…\|wc -l` → **4242**；`5d9dd40` → **4267** | ✅ **两个历史值都精确对** |
| `risk-register.md` **R-10**（时效） | （同上，最新值 4267） | 当前文件 **4527** 行（比台账记录又涨 260 行） | ❌ 台账已过期，**风险继续上升** |
| `risk-register.md` **R-13** | 「CI 分层门禁已建成…**已闭环**」；残留注"CI 是否真的在 GitHub 上跑过、跑绿过，本轮未核实" | **实测 100 次 0 成功**，根因已定位（§5） | ❌ **"已闭环"不成立**，必须重开 |
| `risk-register.md:17` R-04 | 12 例（11+1） | `grep -c '@Test'` → `SettingsDataGuardTest` 11 + `SettingsDataGuardChatHistoryTest` 1 = **12** | ✅ |
| `risk-register.md:22` R-09 | 7 个类共 65 例 | 11+3+10+7+7+8+19 = **65** | ✅ **精确对** |
| `risk-register.md:29` R-16 | `AiInsetsContractJvmTest` 5 例 | `grep -c '@Test'` → **5** | ✅ |
| `risk-register.md:33` R-20 | `NetworkSecurityConfigJvmTest` **8 例**（"不是 6 例"） | `grep -n '@Test'` → **10 处**（第 38/55/65/104/113/122/138/148/176/189 行） | ❌ **实际 10 例，不是 8 例** |

### 4.2 为什么抬头会烂：守卫只查"存在"，不查"时效"

```console
$ sed -n '84,93p' app/src/test/java/cc/nkbr/lanzouplus/DocTimelinessJvmTest.kt
    /** 事实页必须带上当前版本号，接手者据此核时效。 */
    @Test
    fun currentStateDoc_carriesCurrentVersion() {
        val state = read("docs/plan/current-state.md")
        assertTrue(
            "current-state.md 必须写明当前 versionName（接手者据此判断时效）",
            state.contains("versionName") && state.contains(BuildConfig.VERSION_NAME),
        )
    }
```

守卫只断言"**文件里某处出现过**当前 versionName"。所以：
- `- 版本号：versionCode \`10025\` / versionName \`1.0.25\`` 这行被 `tools/bump-version.sh` 自动同步 ✅
- 而**抬头那句"更新日期：2026-10-01（v1.0.8：…）"永远没人管** ❌

`release.sh:75-83` 也只 sed 那一行版本号。**这就是"版本行对、抬头烂"的机制性原因**——
不是谁忘了改，是守卫的形状决定了它测不到。

### 4.3 Release 呈现层：徽章是坏的，标签名与版本号脱钩

```console
$ curl https://img.shields.io/github/v/release/dongwangxingchen/dongfang-wuxian
<title>release: no releases or repo not found</title>

$ gh release list --limit 20
test-20261001-34	Pre-release	test-20261001-34	2026-10-02T02:13:27Z
test-20261001-33	Pre-release	test-20261001-33	2026-10-01T19:12:05Z
test-20261001-32	Pre-release	test-20261001-32	2026-10-01T18:26:34Z
test-20261001-31	Pre-release	test-20261001-31	2026-10-01T18:02:39Z
test-20261001-30	Pre-release	test-20261001-30	2026-10-01T17:14:15Z
test-20261001-29 (v1.0.20)	Pre-release	test-20261001-29	2026-10-01T16:50:10Z
...
```

三点：
1. **README 的 Release 徽章在公网是坏的**（红底 "no releases or repo not found"）。原因：68 个 release
   **全部** `prerelease=true`，`/releases/latest` 返回 404（已实测）。徽章 URL 是
   `https://img.shields.io/github/v/release/dongwangxingchen/dongfang-wuxian`（`README.md` 第 5 行）。
2. **标签名与版本号完全脱钩**：`test-20261001-34` ↔ `v1.0.25` 只能靠**资产名**反推。
   更麻烦的是 `test-20261001-30..34` 这 5 个的**标题丢了 `(vX.Y.Z)` 后缀**（29 及以前都有），
   所以最新 5 个版本在 Releases 列表里**看不出是哪个版本**。
3. **被"作废"的包仍公开可下载**：

```console
$ git log --oneline --all --grep='作废' | head -2
7044bff DFW-103 放行 api.ilanzou.com（真根因）+ 修正测试白名单；v1.0.15 因门禁红作废

$ gh release view test-20261001-24 --json name,isPrerelease,assets
{"assets":[{"digest":"sha256:4048e0f1…cdfe","name":"dongfang-wuxian-v1.0.15.apk","size":36827197}],
 "isPrerelease":true,"name":"test-20261001-24 (v1.0.15)"}

$ curl -sI http://39.106.33.135/apk/dongfang-wuxian-v1.0.15.apk
HTTP/1.1 200 OK
```

「v1.0.15 因门禁红作废」只写在 **commit message** 里；**release 本体没有任何作废标记**，
服务器也照样提供下载。`dfw-91-version-number-single-place.md:50` 与 `handover/README.md:54`
引用了这次事故，但同样没有指向一个"已作废"的可见标记。

### 4.4 死掉的公开接口：`/api/version.json` 停在 1.0.21

```console
$ curl -s http://39.106.33.135/api/version.json
{
  "version": 1,
  "deprecated": true,
  "channel": "stable",
  "latest": {
    "versionName": "1.0.21",
    "versionCode": 10021,
    "apkUrl": "http://39.106.33.135/apk/dongfang-wuxian-v1.0.21.apk",
    "sha256": "9d560b3adcebe263c7d2c2bf12eb594a3c8afed326185a01160bac07f6d8a732",
    ...
  },
  "notes": [
    "⚠️ 本文件已废弃，没有任何代码读它，保留仅为兼容旧链接。",
    "App 真正的更新源是 PocketBase：/api/collections/release/records?perPage=1&sort=-id",
    "发版时请改 PocketBase 的 release 记录，不要改这里。"
  ]
}
```

"没有任何代码读它"这句**已核实为真**：

```console
$ git log --oneline -S'api/version' --name-only
edead6a …  docs/audit/20261001-docs-consistency.md
e2b1e3a …  docs/plan/current-state.md
37fa3b5 …  docs/plan/remote-control-plan.md
b0ddbbd …  docs/plan/current-state.md
$ grep -rn 'version\.json' app/src rikkahub    → 无命中
```

历史提交里 `api/version` 只出现在 **docs**，源码从未引用 → 确属死接口。
但它**仍然公网可读、且内容停在 1.0.21**（比当前落后 4 个版本）。风险低（无人读），
但它是"看起来像官方更新清单"的公开文件，建议清理或改成 301 指向 PocketBase。
（上一轮审计 `20261001-docs-consistency.md:576` 已记录此文件"仍返回 1.22.x"，**一个月内第二次被点出，仍未处理**。）

---

## 5. CI 实测：最近 100 次 0 成功，根因已定位

### 5.1 成功率实测

```console
$ gh run list --limit 100 --json conclusion,workflowName | python3 -c "统计"
total rows: 100
[(('Smoke build (empty debug)', 'failure'), 48),
 (('ci-gates', 'failure'), 48),
 (('Smoke build (empty debug)', 'cancelled'), 2),
 (('ci-gates', 'cancelled'), 2)]
```

**最近 100 次：failure 96 / cancelled 4 / success 0。**（"100 次 0 成功"的说法**成立**。）

```console
$ gh api 'repos/…/actions/runs?per_page=1' --jq '.total_count'
241
$ gh run list --limit 1000 --json conclusion,workflowName | python3 -c "统计"
rows: 241
[(('Smoke build (empty debug)', 'failure'), 155),
 (('ci-gates', 'failure'), 80),
 (('Smoke build (empty debug)', 'cancelled'), 2),
 (('ci-gates', 'cancelled'), 2),
 (('android-build', 'success'), 1),
 (('android-build', 'failure'), 1)]
```

全仓 241 次里**仅 1 次成功**，是 2026-09-21 手动 `workflow_dispatch` 跑的旧 `android-build`
（12m50s），**早于 `ci-gates` 工作流存在**：

```console
$ gh run list --workflow=android-build --limit 10
completed	success	android-build	main	workflow_dispatch	35565354310	12m50s	2026-09-21T05:39:29Z
completed	failure	android-build	main	workflow_dispatch	35564552333	5m27s	2026-09-21T05:26:27Z
```

**即：`ci-gates` 建立以来从未绿过；JVM 测试在 GitHub 上一次都没真正跑起来。**

### 5.2 根因（两个工作流各一个，都已定位到命令行）

```console
$ gh run view 36914255458 --log | grep -oE '\[command\][^ ]*sdkmanager[^$]*|Failed to find package [^ ]*'
[command]/usr/local/lib/android/sdk/cmdline-tools/16.0/bin/sdkmanager --licenses
[command]/usr/local/lib/android/sdk/cmdline-tools/16.0/bin/sdkmanager platforms;android-37,build-tools;37.0.0
Failed to find package 'platforms;android-37,build-tools;37.0.0'
Error: The process '…/sdkmanager' failed with exit code 1
Wrong version in preinstalled sdkmanager

$ gh run view 36914255509 --log | grep -oE '\[command\][^ ]*sdkmanager[^$]*|Failed to find package [^ ]*'
[command]/usr/local/lib/android/sdk/cmdline-tools/16.0/bin/sdkmanager --licenses
[command]/usr/local/lib/android/sdk/cmdline-tools/16.0/bin/sdkmanager tools
Failed to find package 'tools'
```

**根因 A（ci-gates）**：包名写错。本机 `sdkmanager --list` 实测：

```console
$ sdkmanager --list | grep -E 'platforms;android-37|build-tools;37'
  build-tools;37.0.0     | 37.0.0        | Android SDK Build-Tools 37       | build-tools/37.0.0
  platforms;android-37.0 | 2             | Android SDK Platform 37.0        | platforms/android-37.0
```

**平台包叫 `platforms;android-37.0`，不叫 `platforms;android-37`**（本机 SDK 目录也是 `platforms/android-37.0`）。
`sdkmanager` 把逗号列表当一次安装请求，一个 ID 不存在就整体失败 →
`.github/workflows/ci-gates.yml:67` 和 `:107` 的
`packages: 'platforms;android-37,build-tools;37.0.0'` 必然失败——失败的是 **"Set up Android SDK" 这一步**
（在 checkout / setup-java 之后），所以 **unit / apk 两个 job 根本没走到跑测试那一步**。

**根因 B（Smoke build）**：`lanzouplus-empty.yml:43` 用了 `android-actions/setup-android@v3` **但没给 `packages:`**，
该 action 默认去装早已从 SDK 仓库下线的 `tools` 包 → 在 action 内部就 `exit 1`，
**根本走不到下面第 48 行手动 `sdkmanager` 那一行**。

**根因 C（环境侧，非工作流问题）**：日志里还有 `Wrong version in preinstalled sdkmanager` +
`Node.js 20 is deprecated … actions/checkout@v4, setup-java@v4, setup-android@v3`，
说明 runner 镜像已换代（cmdline-tools 16.0 / Node 24），而工作流仍用旧 action 版本。修好包名后
若仍红，这里是第二个要看的地方。

### 5.3 job 级颗粒度：docs 门禁**是好的**，坏的只有 unit/apk

```console
$ gh run view 36914255458 --json jobs --jq '.jobs[] | [.name,.conclusion] | @tsv'
静态门禁（文档 / 卫生 / 清单开关）	success        ← 5 秒通过
JVM 测试门禁（host + vendor + common）	failure        ← 卡在 Set up Android SDK
release 产物门禁（unsigned + ABI/权限/清单断言）	skipped  ← needs: unit
```

```console
$ DFWX_OFFLINE=1 bash tools/ci-gate.sh docs
=== 文档与仓库卫生（静态，无需 Android SDK） ===
✅ 文档与卫生门禁通过
（exit 0）
```

**结论**：门禁脚本本身没问题（本地秒级通过），CI 红**纯粹是 Android SDK 安装环节**。
所以这不是"CI 没价值"，而是"三层里最贵的两层从未启动"。

### 5.4 建议：修 / 降级 / 删（按优先级）

**① 修（首选，改动极小，但必须实跑验证）**
- `ci-gates.yml` 两处（unit job `:67`、apk job `:107`）：
  `packages: 'platforms;android-37,build-tools;37.0.0'` → **`'platforms;android-37.0,build-tools;37.0.0'`**
- `lanzouplus-empty.yml`：给 `setup-android`（`:43`）补上 `with: packages: 'platforms;android-37.0,build-tools;37.0.0'`
  （否则 action 默认装 `tools` 必挂），并把 `:48` 的手动 `sdkmanager "platforms;android-37" "build-tools;37.0.0"`
  改成 `platforms;android-37.0`；若包名修好仍报 `tools` 失败，考虑该 action 直接不用、改为在已有
  `ANDROID_HOME` 上跑 `sdkmanager`。
- 证据：本机 `sdkmanager --list` 输出（§5.2）。**注意：包名正确性由本机 SDK 仓库实测，
  仍须在 GitHub runner 上实跑一次才算闭环**（本轮不允许 push，故未验证）。

**② 降级（如果暂时不想修 CI）**
- 让 `docs` 层继续当唯一阻塞门禁（它 5 秒能过、有真实价值），把 `unit` / `apk` 两层加
  `continue-on-error: true` 或 `if: github.event_name == 'workflow_dispatch'`。
  理由：现在每次 push 都是红的，**红成了背景噪声**，真出问题时没人会看——
  这比"没有 CI"更糟。
- 但注意：这会让"CI 从不跑测试"这个 R-13 想解决的问题**回到原点**，只是不再有假绿。

**③ 删（针对 Smoke build）**
- `lanzouplus-empty.yml` 与 `ci-gates` 的 unit 层职责重叠，且它当前 100% 死在 setup 步骤。
  建议**删掉它**，或降为 `workflow_dispatch` only。保留两套必然同时红的流程没有收益。

> **R-13 必须重开**：它现在写"已闭环"，残留注还自认"CI 是否真的跑过、跑绿过，本轮未核实"。
> 实测答案是**从没绿过**。这条不是"门禁没建成"，而是"**建成的门禁一次都没运行过**"，
> 对"规范备份/规范发版"的保障是 0。

---

## 6. 本轮未核对 / 无法核对的部分（诚实声明）

1. **未跑 gradle**（遵守 lead 约束，另有 agent 在用机器）→ "当前 HEAD 全量 JVM 测试是否全绿"**未验证**。
   本审计里所有测试"例数"均为**静态 `@Test` 计数**，不等于实际执行用例数（参数化测试会 >1）。
2. **未在 GitHub runner 上实跑**修正后的工作流（不允许 push）→ §5.4 的"修"法**未经端到端验证**。
3. **未验证 APK 内容**（未解包、未跑 aapt）→ 版本号一致性只到"文件名/sha256/记录"层面，
   未核对 APK 内 `versionCode/versionName/native-code`。`current-state.md` 里那些 badging 断言本轮未复核。
4. `v1.21.1 / v1.21.5 / v1.21.6` 三个历史缺号未追查是否有本地归档（不在本轮范围，且不影响当前线）。
5. 服务器**只读**：未查 PocketBase 后台管理界面、未读 nginx 之外的任何服务端文件，
   未验证 `dfwx-upload` 服务与其日志。
6. `rikkahub/PATCHES.md:98` 仍写着 `-SNAPSHOT` 风险（R-12 已注为 vendor 区未改）——本轮未复核对错。
