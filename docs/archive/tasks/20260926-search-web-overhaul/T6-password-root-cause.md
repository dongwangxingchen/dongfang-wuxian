# T6 蓝奏云密码目录解析失败 —— 根因已锁定（实测证据）

> 2026-09-26。**这是本项目最高优先级的确定性 bug**：50 个带密码的内置源全部受影响。
> **2026-09-26 修订**：复测发现**两个互相独立**的根因，原文档只写了第①个。两处均已修复。

---

## 〇、修订摘要（重要）

| # | 根因 | 影响面 | 状态 |
|---|---|---|---|
| ① | 密码解锁请求**少发 `lx`/`fid`/`pg`** | 50 个带密码源 | ✅ 已修（`formValues` + `unlockPasswordSession`） |
| ② | 默认 UA 被蓝奏 **UA ACL 黑名单拦截**（403） | **全部 84 源**（不止带密码的） | ✅ 已修（`ANDROID_UA` 换新） |

**② 是复测时才发现的**：原文档只查了 POST 字段，没查"页面本身能不能取到"。
`LanzouCore.java:18` 的默认 UA 字符串被蓝奏 CDN 明确拒绝：

```
HTTP/2 403
x-tengine-error: denied by UA ACL = blacklist
```

触发条件是**UA 字符串精确匹配**（实测：改机型名或改 Chrome 版本任一即可绕过）：

| UA | 结果 |
|---|---|
| `Android 12; Mobile) … Chrome/126.0.0.0`（**原 App 默认**） | ❌ 403 blocked |
| `Android 12; SM-G991B) … Chrome/126.0.0.0` | ✅ 200 |
| `Android 12; K) … Chrome/126.0.0.0` | ✅ 200 |
| `Android 12; Mobile) … Chrome/138.0.0.0` | ✅ 200 |

交替 3 轮 100% 复现。**这解释了用户"一次都没加载成功"** —— 默认 UA 下连分享页 HTML 都拿不到（311 字节的 403 页），根本走不到密码那一步。

---

## 一、结论（一句话）

**两个独立原因叠加**：① App 提交密码时少发了 `lx`/`fid`/`pg`；② 默认 UA 被蓝奏 CDN 拉黑。
分别修复后，实测 `zt=1` 返回完整 33 个文件列表。

---

## 二、实测证据（交替对照，4 轮全中）

同一页面、同一份新鲜 token（`t`/`k`/`uid`/`puid`），只改字段集，交替发 4 次：

| 轮次 | 字段集 | 结果 |
|---|---|---|
| 1 | **App 实际字段**（uid, puid, rep, t, k, pwd） | ❌ `zt=4` `请刷新，重试0` |
| 2 | **补 lx + fid + pg** | ✅ `zt=1` **33 个文件** |
| 3 | App 实际字段 | ❌ `zt=4` `请刷新，重试0` |
| 4 | 补 lx + fid + pg | ✅ `zt=1` **33 个文件** |

**交替顺序 4 轮 100% 复现** → 彻底排除限流/网络抖动干扰，是字段集差异导致的确定性失败。

### 最小修复集探测（单变量隔离，各轮间隔 3s）
| 补的字段 | 结果 |
|---|---|
| 只补 `lx` | ❌ `zt=4` |
| 只补 `fid` | ❌ `zt=4` |
| 只补 `pg` | ❌ `zt=4` |
| **补 `lx` + `fid`** | ⚠️ `zt=2` `没有了`（部分成功） |
| **补 `lx` + `fid` + `pg`** | ✅ `zt=1` **33 项** |
| 再加 `up`/`ls`/`uid`/`puid`（页面完整集） | ✅ `zt=1` **33 项**（无额外增益） |

→ **最小充分修复 = `lx` + `fid` + `pg`**。`up`/`ls` 非必需但无害，一并补上以对齐页面 JS。

### 单字段缺失的反向验证（从完整集逐个拿掉）
| 拿掉 | 结果 |
|---|---|
| 拿掉 `lx` | `zt=null`（无 zt 字段，响应异常） |
| 拿掉 `fid` | ❌ `zt=4` |
| 拿掉 `pg` | ⚠️ `zt=2`（列表不完整） |

---

## 三、根因代码定位

### 3.1 `formValues` 提取不到 `lx` / `fid` / `pg`（三个独立原因）

`LanzouCore.java:1458`：
```java
private static Map<String,String> formValues(String h){
  Map<String,String> out=new HashMap<>(),vars=new HashMap<>();
  Matcher vm=parsePattern("(?:var\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*['\"]([^'\"]*)['\"]").matcher(h);
  while(vm.find())vars.put(vm.group(1),vm.group(2));
  Matcher fields=parsePattern("['\"](lx|uid|puid|rep|t|k|up|webfoldersign|ls|vip)['\"]\\s*:\\s*(?:['\"]([^'\"]*)['\"]|([A-Za-z_][A-Za-z0-9_]*))").matcher(h);
  while(fields.find())out.putIfAbsent(fields.group(1),fields.group(2)!=null?fields.group(2):vars.getOrDefault(fields.group(3),""));
  return out;
}
```

**原因①：`fid` / `pg` 不在字段白名单里。**
白名单是 `(lx|uid|puid|rep|t|k|up|webfoldersign|ls|vip)` —— **没有 `fid`、没有 `pg`**。页面里明明有，但正则根本不去匹配。

**原因②：页面值是裸数字，正则要求带引号。**
页面实际形态（实测原文）：
```javascript
'lx':2,            // 裸数字，无引号
'fid':5436127,     // 裸数字，无引号
'pg':pgs,          // 变量引用
```
而正则的捕获分支是 `['\"]([^'\"]*)['\"]`（值必须被引号包住）或 `([A-Za-z_][A-Za-z0-9_]*)`（标识符，不能是纯数字）。**裸数字两条都不匹配** → `lx` 也提取不到。

**原因③：变量赋值正则漏掉无 `var` 的裸数字赋值。**
页面写的是 `pgs =1;`（**没有 `var` 关键字**），而变量正则的第二个分支要求值带引号 → `pgs` 拿不到 → `'pg':pgs` 解析为空。

实测提取结果（复刻该正则）：
```
修复前 App 能提取到: uid, puid, rep, t, k
  缺: lx, fid, pg, up, ls
```

### 3.2 密码解锁链路只发提取到的字段

`LanzouCore.java:1158`（`unlockPasswordSession`）：
```java
Map<String,String> form=new LinkedHashMap<>();
form.put("pwd",pwd);
for(Map.Entry<String,String> entry:passwordUnlockFields(session.page.html).entrySet())
    form.putIfAbsent(entry.getKey(),entry.getValue());
```
→ `passwordUnlockFields` = `formValues`，拿不到 `lx`/`fid`/`pg`，所以表单里**永远没有这三个字段**。

对比：`primeListingSession`（普通目录接口）路径在 `LanzouCore.java:1182` 手工补了 `lx`/`fid`：
```java
session.form=formValues(session.page.html);
session.form.put("fid", uid.isEmpty()?session.fid:"1");
session.form.put("lx", firstNonEmpty(session.form.get("lx"), uid.isEmpty()?"2":"1"));
session.form.put("rep",...); session.form.put("up",...); session.form.put("vip",...);
```
**但密码解锁路径没有这段补齐逻辑** → 这就是两处路径不一致导致的 bug。

---

## 四、影响面

- **内置 84 个源里，50 个带密码**（实测统计）→ 全部受根因①影响。
- **根因②（UA 黑名单）影响全部 84 个源**，不分是否带密码。
- 用户截图里的「安全·360工具」（密码 `busr`）同时踩中两个根因。
- **不是"分享已失效"**——源完全正常，是 App 自己少发字段 + UA 被拉黑。

---

## 五、修复方案（已实施）

### 5.1 核心修复（`LanzouCore.formValues`）

三条都改了：
1. **字段白名单加 `fid`、`pg`**：`(lx|fid|pg|uid|puid|rep|t|k|up|webfoldersign|ls|vip)`
2. **支持裸数字值**：新增 `(\d+)` 捕获分支
3. **变量赋值支持裸数字**：变量正则加 `|(\d+)` 分支，使 `pgs =1;` 可解析

### 5.2 兜底修复（`unlockPasswordSession`）

对齐 `browseSession` 的补齐逻辑，即使 `formValues` 漏了也能补上：
```java
Map<String,String> form=new LinkedHashMap<>(passwordUnlockFields(session.page.html));
form.put("fid",firstNonEmpty(form.get("fid"),session.fid,"1"));
form.put("lx",firstNonEmpty(form.get("lx"),"2"));
form.put("pg",firstNonEmpty(form.get("pg"),"1"));
form.put("rep",firstNonEmpty(form.get("rep"),"0"));
form.put("up",firstNonEmpty(form.get("up"),"1"));
form.put("vip",firstNonEmpty(form.get("vip"),"0"));
form.put("ls","1");
form.put("pwd",pwd);
```
**注意**：故意不把 `session.form` 整体拷进来——那会把 `folder_id` 带进解锁请求，而页面自身的解锁 JS 从不发这个字段。

### 5.3 UA 修复（`LanzouCore.ANDROID_UA`）

```java
// 旧（被蓝奏 UA ACL 黑名单 → 403）
"Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
// 新（实测 200）
"Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Mobile Safari/537.36"
```

### 5.4 验证（已执行）
- **JVM 单测**：`LanzouUnlockFieldsJvmTest`（5 例）用页面原文片段覆盖裸数字/无 var 变量/引号/标识符四种形态。
  **负向验证**：把 `formValues` 临时还原成旧版后 **4/5 失败**，证明用例真的能抓到该 bug。
- **真机验证**：待装机后打开「安全·360工具」源，应能看到 33 个文件。

---

## 六、教训

1. **"源失效"的结论必须先排除自身 bug。** 我第一次测试用了截图里被截断的 URL（`/b0` 而非 `/b01dk67eh`），得出"分享者已删除"的错误结论——**差点让用户删掉 50 个能用的源**。
2. **排查"解析失败"要分层：先证明请求能到达，再看字段对不对。** 原文档只查 POST 字段就收工，漏掉了"UA 被 403"这一层——**而这一层才是"一次都没成功"的真正原因**。
3. **正则匹配页面 JS 时，值形态（引号/裸数字/变量引用）必须都覆盖**，变量赋值也要覆盖有/无 `var` 两种写法。蓝奏页面变量名随机生成，但**值形态稳定**。
4. **两条代码路径做同一件事时要对齐。** `primeListingSession` 补了字段、`unlockPasswordSession` 没补——这种不一致是 bug 的高发区。
5. **UA 黑名单是"精确字符串匹配"**，不是按浏览器家族封杀。换版本号即可绕过——意味着**这类拦截会随蓝奏规则更新而反复出现**，UA 需要可远程更新（见 `backend-plan.md`）。

