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
