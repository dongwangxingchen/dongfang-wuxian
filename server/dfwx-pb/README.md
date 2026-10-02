# 发布记录的服务端兜底（versionCode 对账）

## 问题

控制台页面（`server/dfwx-admin/index.html`）的**旧版本没有 versionCode 输入框**，
保存时会把旧值原样写回。用户浏览器把那个旧页面**缓存死了**
（服务端后来补了 `no-store` 也作废不了**已经缓存过**的响应），于是：

```
用户在控制台把「版本号」改成 1.0.6
  → 记录里 versionName=1.0.6 但 versionCode 仍是 10005
  → App 比的是 versionCode（10005 > 10005 为假）
  → 永远显示「已是最新版本」，收不到更新
```

**客户端修不了这件事**（用户拿不到新页面），所以必须在服务端兜底。

## 方案：外部对账器 + systemd 定时器

`reconcile-release.py` 每分钟跑一次，只对 **App 实际会读的那一条**
（`sort=-id` 的第一条）做对账：

```
versionCode := max( 由 versionName 推导的值 , 其它记录的最大 versionCode )
```

- 由 `x.y.z` 推导：`major*10000 + minor*100 + patch`（与 `app/build.gradle.kts` 的编号规则一致）
- **只增不减**，绝不让 versionCode 回退
- 版本名解析不出来就**什么都不做**
- 幂等：值已经对了就不发请求

部署位置：`/opt/dfwx-pb/reconcile-release.py`（700 root）+ `dfwx-reconcile.timer`（每 60 秒）。

## ⚠️ 为什么不用 PocketBase 的 `pb_hooks`（试过，不行，别再试）

`main.pb.js.disabled` 是**试过并放弃**的版本，留着是为了防止以后有人重蹈覆辙。

在 `onRecordUpdateRequest` 里调 `$app.findRecordsByFilter` 查同一张表，
会让**整个更新变成静默空操作**：接口返回 **HTTP 200、响应体为空、记录纹丝不动**。
（request 钩子跑在事务里，嵌套查同表出问题。）

实测：摘掉钩子，同样的 PATCH 立刻恢复正常 → 确认是钩子造成的。

另外两个坑也记一下：

1. **钩子名不带 `Before`**：这个版本是 `onRecordCreateRequest` / `onRecordUpdateRequest`；
   写 `onRecordBeforeCreateRequest` 会 `ReferenceError`
   （PocketBase 只会记一行日志并跳过钩子，**不会崩**，所以很容易被忽略）。
2. **顶层函数在 handler 里看不见**：JSVM 把 handler 放进独立运行时执行，
   文件顶层声明的 `function foo()` 在 handler 内部是 `undefined`。
   逻辑必须全部内联，或者用 `require(__hooks + "/xxx.js")`。

---

# 发布历史集合 `release_history`（DFW-106）

控制台要能「看到每一次发布、编辑、删除、回滚」，历史**必须另存一个集合**，
不能塞进 `release`。三条都是实测出来的：

## 1. `release` 的 id 是随机的，`sort=-id` ≠ 创建时间

App 读的就是 `release` 的 `perPage=1&sort=-id` 第一条。而 `release` 的 `id` 字段是
PocketBase 自动生成的 `[a-z0-9]{15}` 随机串，**字符串排序和创建时间毫无关系**。

实测（临时集合，按 1→2→3 的顺序插入三条）：

```
第 1 条 id = ygsoo26ouat0b1t     ← 最先建的
第 2 条 id = sx2f47ka2003zda
第 3 条 id = yalum1ewwq0e9gz     ← 最后建的

perPage=1&sort=-id 返回的第一条 -> n=1（最旧的那条）
全部按 sort=-id 排 -> 1、3、2
```

所以 `release` 里**只要出现第二条记录**，App 就可能读到随机一条 ——
表现正是 DFW-82 那个「改了版本号但用户收不到更新」的静默故障。

## 2. `set-release.py` 会原地覆盖

它是直接 `PATCH /api/collections/release/records/<sort=-id 第一条>` 的。
历史混在 `release` 里，会被它**原地改掉**（老版本直接从历史里消失）。

## 3. `reconcile-release.py` 会把回滚改回去

它每 60 秒把「**其它记录里最大的 versionCode**」抬到当前记录上（`prev_max`）。
历史混在 `release` 里，**回滚会在 60 秒内被它悄悄撤销**。

## 结论与实现

**`release` 永远只保留一条（= 用户正在拿到的版本），历史另存 `release_history`。**
App 的命脉查询、`set-release.py` 的对账语义、`reconcile-release.py` 的「只增不减」规则，
一个字都不用改。

`release_history` 的字段（控制台里的 `HIST_FIELDS` 与这里必须一致）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `versionName` / `versionCode` / `apkUrl` / `sha256` / `size` / `updateMode` | 同 `release` | 那一次发布的内容快照 |
| `action` | select `publish` / `rollback` / `edit` / `cli` | 这条是怎么来的 |
| `note` | text | 例如「回滚自 1.0.23（序号 10023）」 |
| `created` / `updated` | autodate | **必须显式声明**，见下 |

> ⚠️ **PocketBase 建集合时不会自动加 `created` / `updated`**（`release` 就没有，
> 所以那边 `sort=-created` 一直返回 400）。历史要按时间排、要分页，靠的就是 `created`，
> 建集合时必须显式写进去。字段 `id` 可以不写，PocketBase 会自己补。

权限：`listRule` / `viewRule` / `createRule` / `updateRule` / `deleteRule` **全部为 null**
（只有超管能读写）—— 历史不给 App 读，也不给匿名读。

集合的创建有两条路，**两条都幂等**：

1. 控制台「版本」页：集合不存在时页面会提示，点「创建发布历史集合」即可（用页面里的 `HIST_FIELDS`）；
2. 命令行：用同一份字段定义 `POST /api/collections`。

## 命令行发版也会留历史

`set-release.py` 在 PATCH 完 `release` 之后，会往 `release_history` 追一条 `action=cli`。
**写历史失败只打一行提示，不影响退出码** —— APK 已经传上去、`release` 也已经同步好了，
那才是要紧的事。

## 回滚是怎么做的

回滚 = 把选中的历史条目**写回 `release` 那一条**（`PATCH`，不是新建），再往历史里追一条
`action=rollback`。所以 `release` 始终只有一条，App 的查询结果始终确定。

回滚到更小的 `versionCode` 时，`reconcile-release.py` 不会把它改回去：
`release` 只有一条记录 → `others` 为空 → `prev_max = 0` → 只按 `versionName` 推导，
而推导值就等于回滚后的值 → 无操作。
