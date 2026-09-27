# T6-B 更新检查永久失效 —— 根因已锁定（实测证据）

> 2026-09-26。**严重**：所有用户永远收不到更新提示。

---

## 一、结论

`UpdateClient` 指向的是**上游仓库** `nekobyran/lanzouplus`（最新 v1.6.4）和自建端点（返回 v1.4.8），而本 App 版本号是 **1.21.7**。版本比较 `compare(latest, current) <= 0` **恒成立** → `check()` **永远返回 null** → **更新提示永远不会出现**。

---

## 二、实测证据

| 来源 | 最新版本 | 资产名 |
|---|---|---|
| `api.github.com/repos/nekobyran/lanzouplus/releases/latest` | **v1.6.4** | `LanzouMax.apk`, `LanzouPlus.apk` |
| `lanzouplus.nkbr.cc/latest.json` | **v1.4.8** | `LanzouPlus.apk`（296KB） |
| **本 App**（`app/build.gradle.kts:31-32`） | **1.21.7**（versionCode 1039017） | — |
| 用户自己的仓库 `dongwangxingchen/dongfang-wuxian` | **v1.21.4** | `dongfang-wuxian-v1.21.4.apk`（39MB） |

代码逻辑（`UpdateClient.java:29-33`）：
```java
static UpdateInfo check(String currentVersion)throws IOException{
  long[] current=parseVersion(currentVersion);
  ...
  for(String endpoint:endpoints)try{return parse(fetch(endpoint),current,SITE_LATEST.equals(endpoint));}...
}
```
`parse()` 里（`:41`）：
```java
long[] latest=parseVersion(rawTag);
if(compare(latest,current)<=0)return null;   // 1.6.4 <= 1.21.7 → 恒 true → 返回 null
```

**结论**：无论网络多正常，`check()` 都返回 null，更新提示永远不出现。

---

## 三、附带发现（同源问题）

1. **资产名不匹配**：`ASSET_NAME="LanzouPlus.apk"`（`UpdateClient.java:11`），而用户自己仓库的资产名是 `dongfang-wuxian-v1.21.4.apk`。即使改了仓库地址，也会因 `更新信息缺少指定安装包` 失败。
2. **host 白名单**：`requireGithubAsset`（`:107`）硬编码 `/nekobyran/lanzouplus/releases/download/`；`requireMirrorAsset`（`:114`）硬编码 `lanzouplus.nkbr.cc`。改仓库必须同步改这两处，否则报"GitHub 安装包地址不受信任"。
3. **镜像下载地址**：`SITE_APK="https://lanzouplus.nkbr.cc/download/LanzouPlus.apk"` —— 这是上游的安装包（296KB），不是本 App（39MB）。

---

## 四、修复方案（三处必须同改）

```java
// 1. 仓库地址（UpdateClient.java:12）
private static final String GITHUB_LATEST=
    "https://api.github.com/repos/dongwangxingchen/dongfang-wuxian/releases/latest";

// 2. 资产名（:11）—— 匹配实际发布名
static final String ASSET_NAME="dongfang-wuxian-v1.21.7.apk";   // 注意：版本号会变，需动态匹配

// 3. host 白名单（:107）—— 同步改仓库路径
String expected="/dongwangxingchen/dongfang-wuxian/releases/download/"+tag+"/"+ASSET_NAME;
```

**关于资产名**：项目红线是"Release 标题=纯 vX.Y.Z，资产=dongfang-wuxian-vX.Y.Z.apk"（无描述性后缀）。资产名含版本号，硬编码会随发版失效 → 建议改成**前缀匹配**：
```java
if(!candidate.optString("name").startsWith("dongfang-wuxian-v"))continue;
```
并保留"不唯一则报错"的校验。

**镜像端点**：`lanzouplus.nkbr.cc/latest.json` 是上游的，需要改成自己的（或指向自己的 Cloudflare 端点，见 `backend-plan.md`）。

---

## 五、验证方式

1. **真机**：装一个旧版本（如 v1.21.4），点"检查更新"，应提示有 1.21.7。
2. **单元测试**：`UpdateClient.parse()` 传入模拟的 release JSON（tag=v1.21.8）与 current=1.21.7，应返回非 null。
3. **注意**：修完后**必须真机验证一次完整更新流程**（下载→校验→安装），因为 host 白名单和摘要校验是硬校验，任何一处不匹配都会中断。
