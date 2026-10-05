# server/ · 东方无限后端（自建）

本目录存放**服务端**资产，**不参与 APK 构建**。
（曾短暂放到 `app/src/main/assets/`，但那会把后台界面打进用户安装包——无必要且有暴露面，已移出。）

后端是**可选的**：App 的软件库、下载、工具箱不依赖它；它只负责远程公告、版本清单、APK 分发与内置 AI 中转。
**你可以把它部署在自己的服务器上**，也可以完全不用——不部署时 App 会自动降级（公告不显示、更新检查走 GitHub）。

## 组成

| 路径 | 说明 |
|---|---|
| `dfwx-admin/index.html` | **中文管理控制台**（单文件、零依赖）—— 控制台的唯一真相 |
| `dfwx-upload/` | 安装包上传服务（控制台「一键上传」的后端） |
| `dfwx-pb/` | PocketBase 的集合定义与几个运维脚本 |
| `dfwx-ai-guard/` | 内置 AI 渠道的请求验签服务（见下） |

## 部署拓扑

整体是「一个 nginx 对外 + 几个只监听本机的后端服务」：

```
                        ┌──────────────────────────────┐
   浏览器 / App  ──────▶│  nginx  :80 / :443           │
                        └───┬───────────┬──────────┬───┘
                            │           │          │
              /pb/ ─────────┘   /admin/ │          └─── /apk/  静态分发
              (App 读数据)      (控制台)  │
                                        │
                    ┌───────────────────┴────────────────────┐
                    │                                        │
          127.0.0.1:8090                          127.0.0.1:8091
          PocketBase（数据 + 鉴权）                上传服务（流式收包 + 算 sha256）
```

**两个后端都只监听 `127.0.0.1`**，外部只能经 nginx 进来。这样做的原因：这些服务本身不带 TLS、也没有面向公网的加固，直接暴露等于把攻击面放大一圈。

## 配置项

部署前需要确定的几件事（都**不要**写进仓库）：

| 项 | 放在哪 | 说明 |
|---|---|---|
| 管理员邮箱 | 部署时设定 | 控制台与上传接口共用的账号 |
| 管理员密码 | 服务器上的凭据文件（权限 `600`） | **永不入 git** |
| 服务器地址 | 编译进 App | 见 `RemoteConfigClient.BASE`；换服务器只改这一处 |
| 内置 AI 的上游地址与 Key | 服务器上的 nginx 配置片段（`600`） | 见「内置 AI 中转」一节 |

> **为什么这些值不在仓库里**：本仓库是公开的。任何写进代码或文档的密钥，等于直接公开。
> App 侧的凭证通过构建期注入（见 `app/build.gradle.kts` 的 `dfwxSecret`），
> 服务端的值放服务器上的受限文件。仓库里只有**读取逻辑**，没有**值**。

## 访问入口

| 入口 | 用途 |
|---|---|
| `https://<你的服务器>/admin/` | 控制台（推荐走 HTTPS） |
| `https://<你的服务器>/pb/` | 302 到同一个控制台 |
| `https://<你的服务器>/pb/_/` | PocketBase 原版后台（备用，英文） |
| `http://<你的服务器>/pb/api/collections/<集合>/records` | **App 读数据的 API —— 命脉，任何 nginx 改动都不许碰** |
| `http://<你的服务器>/d` | 总下载链接：302 到当前默认安装包（见 `dfwx-upload/README.md`） |

> 为什么控制台必须走 HTTPS：现代浏览器会把 `http://` 自动升级为 `https://`，
> 走 80 会被升级后撞上 443 的其他服务（实测 `ERR_HTTP_RESPONSE_CODE_FAILURE`）。

## 两份控制台统一（2026-10-02）

**背景**：服务器上曾同时存在两份控制台，且**旧的那份是坏的**——页面上没有 versionCode 输入框，
保存时把旧 `versionCode` 原样回写。这就是"控制台改了版本号、用户却收不到更新"的直接原因。

**做法**：控制台只保留一份，对外 `/pb/` 自动跳 `/admin/`，旧位置换成一张跳转占位页。

### ⚠️ 铁律：只能精确匹配，**绝不能把 `/pb/` 整个前缀重定向掉**

App 读后台数据的地址写死在 `RemoteConfigClient.java`（`BASE`），四个集合（`control` / `release` /
`notice` / `changelog`）全走 `/pb/api/collections/<集合>/records?perPage=200&sort=-id`。

如果写成前缀匹配 `location /pb/ { return 302 /admin/; }`，那么 `/pb/api/...` 和 `/pb/_/` 会**一起被跳掉**，
App 立刻读不到公告/更新/更新记录（fail-open 会让它静默当作"一切正常"，最难排查）。
正确写法是 nginx 的**精确匹配 `location =`**（优先级最高，压过所有正则与前缀 location）：

```nginx
location = /pb/            { return 302 /admin/; }
location = /pb/index.html  { return 302 /admin/; }

# 下面这行原样保留，一行都不能少 —— 它是 App 的命脉
location /pb/ { ... proxy_pass http://127.0.0.1:8090; rewrite /pb/(.*) /$1 break; }
```

两个 server 块（80 与 443）**都要加**，否则从 80 进来的用户还是看到旧页面。
`/pb`（无尾斜杠）由 nginx 自身 301 到 `/pb/`，再落到 302，无需额外配置。

## 四个数据集合

| 集合 | 用途 | 条数 |
|---|---|---|
| `control` | 总控：维护/停用开关 + 标题/正文/维护时间 | 1 行 |
| `release` | 当前版本：版本号 / 下载地址 / sha256 / 大小 / **更新方式**（软/强制/不提示） | 1 行 |
| `notice` | 公告：可叠加；每条可设 等级/置顶/**是否弹窗**，可绑定版本号只给特定版本看 | 多条 |
| `changelog` | 更新记录：可叠加；供"更新完成弹窗"与常驻历史页 | 多条 |

权限：**四个集合均公开只读**（App 匿名可读），**写需超管令牌**；
nginx 侧对非 `/pb/` 路径仍严格只读（405）。

> 公开只读是**有意**的：App 没有登录体系，公告和版本清单必须匿名可读。
> 代价是**这四个集合里不能放任何机密**——包括 AI 令牌。这条踩过一次，见
> `docs/plan/dfw147-ai-protection.md`。

## 部署控制台（改动后）

```bash
scp server/dfwx-admin/index.html <你的服务器>:/tmp/admin.html
ssh <你的服务器> 'sudo install -o www-data -g www-data -m 644 /tmp/admin.html /var/www/dfwx/admin/index.html && rm -f /tmp/admin.html'
# 验证：外网取回的 md5 应与仓库文件一致
curl -sk https://<你的服务器>/admin/ | md5
md5 server/dfwx-admin/index.html
```

`location /admin/`（在 **443** 的 server 块里）已带 `Cache-Control: no-store`，刷新即生效，**不需要 reload nginx**。
控制台请一律用 `https://` 入口——80 端口上 `/admin/` 是走 `location /` 兜底静态服务的（会带 `ETag`、没有 `no-store`）。
部署完请按上面两条 md5 命令核对，**不要只看"页面能打开"**。

## 发版时同步 APK

**服务器直连 GitHub 下大文件极慢（实测 36MB / 16 分钟未完成），所以用 scp 直推：**

```bash
scp <构建产物目录>/dongfang-wuxian-vX.Y.Z.apk <你的服务器>:/tmp/apk.apk
ssh <你的服务器> 'sudo mv /tmp/apk.apk /var/www/dfwx/apk/dongfang-wuxian-vX.Y.Z.apk && \
                  sudo chown www-data:www-data /var/www/dfwx/apk/dongfang-wuxian-vX.Y.Z.apk'
```
然后更新 `release` 集合（控制台「版本」页可手改，或由自动同步脚本兜底）。

## 控制台「一键上传安装包」链路

控制台选 APK → 浏览器本地算 sha256/大小 → 保存时先 PUT，再写 `release` 记录：

```
控制台 ──PUT /admin/apk-upload/<文件名>──▶ nginx(443)
   ├─ auth_basic   （复用后台管理员口令）
   ├─ client_max_body_size 200m / proxy_request_buffering off
   └─ proxy_pass http://127.0.0.1:8091/  ──▶ dfwx-upload（python3）
                                              └─ 流式收流 + 边收边算 sha256
                                                 └─ os.replace 落到 /var/www/dfwx/apk/<文件名>，chmod 644
```

- 为什么不用 nginx 自带 `dav_methods`：本机 nginx 虽编了 `--with-http_dav_module`，
  但该 location 上 PUT 一律被静态模块拦成 405，改用自建服务后行为完全可控
- **上传只能走 443**：80 端口 server 块顶部有方法守卫（只对 `/pb/` 放行写），所以 PUT 走 80 是 405
- 端到端实测（1 MiB 随机文件）：无认证 401 / 错误口令 401 / 正确口令 **200**，
  落盘属主与权限正确，**sha256 与大小和本地完全一致**；非法文件名被服务以 400 拒绝；
  `%2e%2e%2f` 穿越尝试被 nginx 规范化挡在 location 之外

## 内置 AI 中转

App 里有一个内置 AI 渠道，走**自建服务器中转**，而不是把上游密钥塞进 APK。原因很直接：
APK 是公开的，里面的任何密钥都能被反编译出来。

链路：`App → nginx(/ai/v1/...) → 上游中转站`，上游地址与真 Key 只在服务器上。

**两道门**：

1. **应用令牌**（nginx `if` 判断）—— App 侧凭证，构建期注入；
2. **请求签名 + 配额**（`dfwx-ai-guard/`）—— 光有令牌不够，还得能算出 HMAC 签名。

签名服务有**两种模式**：`observe`（只记录不拦截）与 `enforce`（拦截）。
**上线必须先跑 observe** —— 签名有 bug 时让它暴露在日志里，而不是暴露在用户脸上。

完整设计、调研过的替代方案（以及为什么没用它们）见 `docs/plan/dfw147-ai-protection.md`。

## 踩坑记录

1. **PocketBase 0.40 的 `created` 不是可排序字段**：`sort=-created` 返回 **400**，
   必须用 `sort=-id`。坑在"单条写入 200 成功、列表查询静默失败"，表现为
   「提示发布成功但看不到」——App 端 `RemoteConfigClient` 与控制台都已修正。
2. **PocketBase 自身不对错误密码限流**（实测连打 8 次全 400），
   已在 nginx 加登录端点限流（6 次/分钟，实测第 4 次起 429）。
3. **`org.json` 与 `android.util.Log` 在纯 JVM 单测里是空桩**，
   任何用到它们的类测试必须挂 Robolectric。
4. **改 `/pb/` 相关 nginx 配置前先想清楚"会不会连累 App"**：
   `/pb/api/` 是 App 的命脉，任何针对 `/pb/` 的 `return` / `rewrite` **必须用 `location =` 精确匹配**，
   前缀匹配等于把 App 的数据通道一起切断。改完必须复测四个集合：
   `curl -s 'http://<你的服务器>/pb/api/collections/release/records?perPage=1&sort=-id'` 应返回 JSON（不是 302/HTML）。
5. **同一份东西在两处部署 = 迟早有一处是旧的**。控制台曾同时放在两个位置，
   结果用户打开的是旧的那份、症状却表现为"App 更新功能坏了"，排查绕了一大圈。
6. **公开只读的集合不能放机密**。`control` 集合是匿名可读的（App 需要），
   曾经打算把 AI 令牌放进去做"远程下发"——那样等于**换了个地方公开**。
   实测 `curl` 匿名就能读到整条记录。机密只能走构建期注入或服务器上的受限文件。
