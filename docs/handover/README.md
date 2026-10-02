# 换窗口 / 换会话：先读这一页

> **目标：30 秒接手。** 读完这一页就能开工，不用翻别的文档。
> **维护成本：每次收尾只改 §2 和 §4 的几行**（怎么改见文末）。
>
> 本目录**故意不提交 git**（见 `AGENTS.md`）——它是本机工作现场，不是仓库产物。

---

## 0. 一句话现状

《东方无限》Android 应用，版本 **1.0.21（versionCode 10021）**，分支 `test`。
五大板块（软件库 / AI 对话 / 下载 / 工具箱 / 设置）均已可用；远程后端（公告、更新、APK 分发、
内置 AI 渠道中转、网页控制台）**已完整接入并跑通**。当前在做用户 2026-10-01 提出的
一批体验与功能问题（见 §4）。

---

## 1. 开工三步

```bash
cd ~/heiyao/src
git log --oneline -5 && git status --short     # ① 看现场
```

```bash
# ② 构建 / 测试 / 发版（三个最常用的）
export JAVA_HOME=/Users/lishaowei/sdk/jdk-21.0.11.jdk/Contents/Home

DFWX_OFFLINE=1 bash tools/ci-gate.sh docs      # 文档与仓库卫生（秒级）
DFWX_OFFLINE=1 bash tools/ci-gate.sh unit      # 全量 JVM 测试（约 4 分钟）
DFWX_OFFLINE=1 bash tools/release.sh build     # 出 release APK（约 6 分钟）
```

- 只跑单个测试类：`./gradlew -p . :app:testEmptyDebugUnitTest --tests '*某个类名*'`
- **没有** `:app:testReleaseUnitTest` 这个任务，别试。
- 构建产物：`~/heiyao/黑曜/03-构建产物/dongfang-wuxian-v<版本>.apk`
- **Gradle 不许并发跑**：两个窗口同时跑会 `OutOfMemoryError: unable to create native thread`。
  卡住了就 `./gradlew --stop` + `pkill -f GradleWorkerMain`。

---

## 2. 进度快照 ← **每次收尾改这里**

- **日期**：2026-10-01
- **HEAD**：`git log --oneline -1`（不要在这里抄死哈希，会腐烂；看命令输出）
- **工作树**：见 `git status --short`。本目录 `docs/handover/` 永远显示 untracked，**这是故意的**。
- **刚做完（最近一批）**：
  0. **DFW-91** **版本号只保留一处**（用户：「我主要是想减轻你的维护负担，别忘记改某些地方的版本号」）。
     用户可见的版本号从 4 处收敛到 1 处（「检查更新」右侧）。更关键的是让"漏改"物理上不可能：
     ① 代码里只有一个数字 `val buildCode`，versionName 由它算出来；
     ② `release.sh` 从"不同步就报错"改成**自动写入** `current-state.md`；
     ③ **`release.sh build` 现在强制先跑 `verify`** —— 以前两者互不相干，
     **v1.0.15「带红门禁发布」就是这么发生的**。
     顺带修掉 3 个潜伏 bug：shell 里 `$变量` 紧跟中文全角字符会把中文字节吞进变量名
     （`release.sh:78` 本次实际踩到，另两处潜伏在"只在出错分支才执行"的代码里）。
  1. **DFW-102** **下载完成/失败通知**——DFW-94 删掉悬浮窗后下载完没有任何反馈，
     用户主动要求补上：「写明白啥东西下载好了，别只写个下载完成」。
     用已有的顶部通知条 `showTopBanner`，**带文件名**；批量合并成「已下载 N 个文件 · 最新：xxx」。
     单一收口点在 `drainDownloadUi` 的 `stateChanged` 上（七八个状态出口分散写必然漏）。
     守卫 `DownloadOutcomeNoticeJvmTest`（4 例，两条反向探针实测变红）。
  2. **DFW-93** 删除「下载完成后自动安装」设置项及其功能（ADB 静默安装**未动**）。
  2. **DFW-94** **删除下载悬浮窗**——用户嫌它"没用、丑、挡地方"。
     浮层底座（`toastLayer`/`toastPanel()`/`addToastPanel`/`dismissPanel`/`swipePanel`）**全部保留**，
     下载页、下载历史、顶部通知条一字未动。守卫 `DownloadToastRemovalJvmTest`（4 例，含反向探针）。
     ⚠️ **行为变化**：下载完成后不再有任何浮动提示（原来那张卡会显示"已完成"、5 秒后消失）；
     失败仍有提示。用户未要求补提示条，故未擅自新增。
  3. **DFW-99** `renderFolder` 空指针崩溃（用户真机日志）——`progress` 置空后仍有未判空的 `setVisibility`。
  4. **DFW-101** 解析看门狗：把"无限解析中"变成有明确原因的失败（25 秒）。
  5. **DFW-103/104/105/106/107** 下载链路一批加固：`ilanzou.com` 域名、http 回退放行、
     iPhone UA、域名优先排序、浏览器指纹请求头。
- **发布**：`test-20261001-29`（v1.0.20，DFW-93）→ 本批 `v1.0.21`（DFW-94）。
  服务器 `/var/www/dfwx/apk/` 与 PocketBase `release` 记录（id `kev9q0jrpyo1xy0`）随版本同步。

---

## 3. 铁律与红线（不会变；出事基本都是踩了这些）

**代码**
- 包名 `dfwx.dongdang`、宿主 Java 包 `cc.nkbr.lanzouplus` —— **永远不许改**。
- `rikkahub/` 是 vendor：改动必须同步记进 `rikkahub/PATCHES.md`。
- 改完必须**反向探针**：把修复撤掉 → 测试变红 → 还原 → 变绿。只跑绿不算验证。
- 源码级断言不要用裸 `contains`（会假绿）；要**花括号配对取方法体**，且 `assertFalse` 前面先
  断言切片非空。

**发版**
- `tools/release.sh publish` 只接受 `vX.Y.Z`；测试包一律用 `test-YYYYMMDD-N` + `--prerelease`，
  手动 `gh release create`。**官方 `vX.Y.Z` 不许带 `--prerelease`**。
- APK 上传服务器用**版本化文件名**（`dongfang-wuxian-v1.0.1.apk`），不要覆盖固定名。
- 控制台里 `apkUrl` 前缀**故意是 http** —— 改成 https 会因证书不被设备信任而下载失败。

**服务器**
- `ssh dfwx '<命令>'` 免密；`nginx -t` / `systemctl reload nginx` 必须 `sudo`。
- PocketBase 管理密码在 `/root/.dfwx-pb-admin-pass` —— **永远不要打印**。
- 不要 `rm -rf` 站点目录；不建议买域名/备案；非标端口外网不通。

**绝对不许动**
- `~/heiyao/_保险箱/`、`~/Desktop/东方无限/交接包zip`、`local.properties`（也不要读/打印）。

**文档**
- 注释与文档里不许出现 `黑曜`、`heiyao`（`BrandingCleanlinessJvmTest` 会红）。
- 改了版本号，**必须同步 `docs/plan/current-state.md`**（`DocTimelinessJvmTest` 会红）。

---

## 4. 下一步 ← **每次收尾改这里**

任务面板（`taskctl`，项目 `dfwx-android`）是唯一的任务队列。

> ⚠️ **卡的「标题编号」和「卡号」差一位**（历史遗留，别被绕晕）：
> 例如**卡号 `DFW-93`** 的标题写的是「**DFW-94** 删除下载悬浮窗」，
> **卡号 `DFW-92`** 的标题写的是「**DFW-93** 删除『下载完成后自动安装』」。
> 代码注释和文档里一直沿用**标题里的编号**，而 `taskctl` 命令要用**卡号**。
> 发评论/改状态前先 `taskctl issue get <卡号>` 核对标题，别操作错卡。

**用户 2026-10-01 一次性提的那批（"每个都当大任务做"，逐个推进）**：

| # | 用户原话要点 | 卡 | 状态 |
|---|---|---|---|
| 1 | 删掉首启引导页 | — | ✅ 已删 |
| 2 | 删「下载完成后自动安装」按钮+功能 | DFW-93 | ✅ v1.0.20 |
| 3 | 删下载悬浮窗（**保留下载页与全部记录**） | DFW-94 | ✅ v1.0.21 |
| 4 | 版本号只留一处 = 「检查更新」右侧 | DFW-92 | ⏭ 下一个 |
| 5 | 不要"必须出个 1.0.6 的包才能更新" | DFW-91 | 待做 |
| 6 | 后台上传安装包按钮，自动解析版本/哈希/大小/链接 | DFW-90 | 待做 |
| 7 | 「检查更新」**上方**加按钮跳 FlowUs；站内打开、配色同风格、能顺畅返回 | DFW-96 | 待做 |
| 8 | 搜索页底部黑色留白 | DFW-95 | 待做 |
| 9 | 搜索界面安卓返回没有动画（要渐隐） | DFW-95 | 待做 |
| 10 | 公告弹窗弹得太慢 | — | 未查 |
| 11 | 搜索太慢 | DFW-97 | 未测（**先测各阶段耗时，别凭感觉优化**） |
| 12 | 图3 一直"解析中"不下载 / 图4 下载失败 | DFW-108 | 🅿️ **用户明确暂停**："留到最后，等我拍板" |

DFW-108 的设计与三条未验证假设写在 `docs/plan/dfw-108-waf-challenge.md`。

**其余存量卡**：

| 卡 | 内容 | 状态 |
|---|---|---|
| DFW-83 | 仓库瘦身 + 跨窗口交接机制（本文件就是它的产物之一） | 进行中 |
| DFW-85 | 控制台缓存导致用户看不到新功能 | 待收尾 |
| DFW-29 / 33 / 35 / 36 / 37 / 38 / 42 / 54 | 早期队列里的存量卡 | 待逐个确认 |
| DFW-34 / 26 | 通知卡统一语言 / 全站弹窗治理的收尾 | 剩盘点 |
| DFW-64 | 官方 `v1.0.0` GitHub release 未发布 | **等用户拍板** |

---

## 5. 只有用户能做的事

1. **轮换上游 AI Key**：旧 Key 在公开 APK 里泄露过，建议在服务器换掉（改
   `/etc/nginx/dfwx-ai-secret.conf` + `nginx -s reload`，用户不用更新软件）。
2. 阿里云控制台开启 MFA。
3. 真机验收：装机看新动画、控制台硬刷新一次看「AI 渠道」标签页。
4. 官方正式版（非 prerelease）要不要发。

---

## 6. 更深的文档在哪

**不要一次全读。** 按需要跳：

| 你要做什么 | 读哪个 |
|---|---|
| 当前事实（版本、红线、环境） | `docs/plan/current-state.md` |
| 已经拍板、不许再翻案的决定 | `docs/plan/decisions.md` |
| UI 设计 token（动效/形状/字阶） | `docs/design/wear-ui-system.md` |
| 踩过的坑与教训 | `docs/agents/lessons.md` |
| 发版流程细节 | 技能 `dfwx-release` |
| 服务器运维细节 | 技能 `dfwx-server` |
| 历史计划与研究（**只能当背景**） | `docs/archive/` |

---

## 怎么保持新鲜（重要）

这套机制唯一的失败模式是"没人更新它"。所以**收尾时只改三处，每处一两行**：

1. **§2 的「日期」** → 改成今天。
2. **§2 的「刚做完」** → 用 3–5 条替换掉旧的（最新的在最上面）。
3. **§4 的表格** → 划掉做完的，加上新开的。

> 规矩：**这一页只写"现在在哪、下一步去哪"。** 任何需要展开解释的东西，
> 写成 `docs/plan/` 或 `docs/agents/lessons.md` 里的一段，然后在这里放一行链接。
> 一旦你发现自己在往这一页里抄第二段细节，就该停下来 —— 那是在制造下一份会腐烂的文档。
