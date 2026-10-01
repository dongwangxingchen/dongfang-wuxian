# server/ · 东方无限后端（自建）

本目录存放**服务端**资产，**不参与 APK 构建**。
（曾短暂放到 `app/src/main/assets/`，但那会把后台界面打进用户安装包——无必要且有暴露面，已移出。）

## 组成

| 路径 | 说明 |
|---|---|
| `dfwx-admin/index.html` | **中文管理控制台**（单文件，无依赖）—— 控制台的**唯一真相**，部署到 `/var/www/dfwx/admin/index.html` |

## 部署位置（阿里云轻量 `39.106.33.135`）

| 项 | 值 |
|---|---|
| 后台程序 | PocketBase 0.40.4，`/opt/dfwx-pb/pocketbase`（systemd: `dfwx-pb`，仅监听 `127.0.0.1:8090`） |
| 数据目录 | `/opt/dfwx-pb/pb_data`（SQLite + 上传文件） |
| **控制台（唯一）** | `/var/www/dfwx/admin/index.html`（nginx `location /admin/` 直接读这个文件，`Cache-Control: no-store`） |
| 控制台旧路径 | `/opt/dfwx-pb/pb_public/index.html` —— **已废弃**，2026-10-02 起降级为一张跳转占位页（见「两份控制台统一」） |
| APK 分发 | `/var/www/dfwx/apk/`（nginx，支持断点续传） |
| nginx | `:80`（`sites-enabled/dfwx`）与 `:443`（`sites-enabled/dsh-remote`）各有一段 `location /pb/` 反代；两处都加了 `/pb/`、`/pb/index.html` 的精确匹配 302 → `/admin/` |
| 管理账号 | `<管理员账号>`，密码在 `<凭据文件>`（600，**永不入 git**） |

## 访问入口

- **控制台（用户使用）**：`https://39.106.33.135/admin/`（推荐）；`https://39.106.33.135/pb/` 会 302 到同一个页面
- PocketBase 原版后台（备用，英文）：`https://39.106.33.135/pb/_/`
- App 读数据的 API：`http://39.106.33.135/pb/api/collections/<集合>/records` ← **App 命脉，任何 nginx 改动都不许碰**

> 为什么控制台必须走 HTTPS：现代浏览器会把 `http://` 自动升级为 `https://`，
> 走 80 会被升级后撞上 443 的其他服务（实测 `ERR_HTTP_RESPONSE_CODE_FAILURE`）。

## 两份控制台统一（2026-10-02，卡 A6）

**背景**：服务器上曾同时存在两份控制台，且**旧的那份是坏的**：

| 位置 | 大小 | 状态 |
|---|---|---|
| `/var/www/dfwx/admin/index.html` | 39 KB | ✅ 最新版，与仓库 `server/dfwx-admin/index.html` md5 一致（`248ee929664d9288c563bbbb01d72b7e`） |
| `/opt/dfwx-pb/pb_public/index.html` | 22 KB | ❌ 9-30 旧版：**页面上没有 versionCode 输入框**，保存时把旧 `versionCode` 原样回写 —— 这就是"控制台改了版本号、用户却收不到更新"的直接原因 |

**做法**：控制台只保留一份（`/var/www/dfwx/admin/index.html`），对外 `/pb/` 自动跳 `/admin/`。

### ⚠️ 铁律：只能精确匹配，**绝不能把 `/pb/` 整个前缀重定向掉**

App 读后台数据的地址写死在 `RemoteConfigClient.java`：`BASE = "http://39.106.33.135/pb"`，
四个集合（`control` / `release` / `notice` / `changelog`）全走
`/pb/api/collections/<集合>/records?perPage=200&sort=-id`。

如果写成前缀匹配 `location /pb/ { return 302 /admin/; }`，
那么 `/pb/api/...` 和 `/pb/_/` 会**一起被跳掉**，App 立刻读不到公告/更新/更新记录（fail-open 会让它静默当作"一切正常"）。正确写法是 nginx 的**精确匹配 `location =`**（优先级最高，压过所有正则与前缀 location），只影响两个确切 URL：

```nginx
location = /pb/            { return 302 /admin/; }
location = /pb/index.html  { return 302 /admin/; }

# 下面这行原样保留，一行都不能少 —— 它是 App 的命脉
location /pb/ { ... proxy_pass http://127.0.0.1:8090; rewrite /pb/(.*) /$1 break; }
```

两个 server 块（`:80` 的 `sites-enabled/dfwx`、`:443` 的 `sites-enabled/dsh-remote`）**都要加**，
否则从 80 进来的用户还是看到旧页面。`/pb`（无尾斜杠）由 nginx 自身 301 到 `/pb/`，再落到 302，无需额外配置。

**兜底**：`/opt/dfwx-pb/pb_public/index.html` 已换成一张 1.3 KB 的跳转占位页（meta refresh + `location.replace('/admin/')`）。
万一将来 nginx 上的 302 被误删，这里也只会跳到正确的新控制台，绝不会再吐旧版。
该文件**不再是控制台**，不要再往里部署任何东西。

## 四个数据集合

| 集合 | 用途 | 条数 |
|---|---|---|
| `control` | 总控：维护/停用开关 + 标题/正文/维护时间（**全由用户填**） | 1 行 |
| `release` | 当前版本：版本号 / 下载地址 / sha256 / 大小 / **更新方式**（软/强制/不提示） | 1 行 |
| `notice` | 公告：可叠加；每条可设 等级/置顶/**是否弹窗** | 多条 |
| `changelog` | 更新记录：可叠加；供"更新完成弹窗"与常驻历史页 | 多条 |

权限：**四个集合均公开只读**（App 匿名可读），**写需超管令牌**；
nginx 侧对非 `/pb/` 路径仍严格只读（405）。

## 部署控制台（改动后）

改的是**唯一那一份** `/var/www/dfwx/admin/index.html`（`pb_public/` 已废弃，不要再往那儿传）：

```bash
scp server/dfwx-admin/index.html dfwx:/tmp/admin.html
ssh dfwx 'sudo install -o www-data -g www-data -m 644 /tmp/admin.html /var/www/dfwx/admin/index.html && rm -f /tmp/admin.html'
# 验证：外网取回的 md5 应与仓库文件一致
curl -sk https://39.106.33.135/admin/ | md5
md5 server/dfwx-admin/index.html
```

`location /admin/`（在 **443** 的 server 块里）已带 `Cache-Control: no-store`，刷新即生效，**不需要 reload nginx**。
控制台请一律用 `https://` 入口 —— 80 端口上 `/admin/` 是走 `location /` 兜底静态服务的（会带 `ETag`、没有 `no-store`），
浏览器又把 `http://` 自动升级成 `https://`，所以 80 不是控制台的正常入口。部署完请按上面两条 md5 命令核对，不要只看"页面能打开"。

## 发版时同步 APK

**服务器直连 GitHub 下大文件极慢（实测 36MB / 16 分钟未完成），所以用 scp 直推：**

```bash
scp <本地目录>/黑曜/03-构建产物/dongfang-wuxian-vX.Y.Z.apk dfwx:/tmp/apk.apk
ssh dfwx 'sudo mv /tmp/apk.apk /var/www/dfwx/apk/dongfang-wuxian-vX.Y.Z.apk && \
          sudo chown www-data:www-data /var/www/dfwx/apk/dongfang-wuxian-vX.Y.Z.apk'
```
然后更新 `release` 集合（控制台「版本」页可手改，或由自动同步脚本 `dfwx-sync.sh` 兜底）。

## 控制台「一键上传安装包」链路（2026-10-02 端到端实测通过）

控制台选 APK → 浏览器本地算 sha256/大小 → 保存时先 PUT，再写 `release` 记录：

```
控制台 ──PUT /admin/apk-upload/<文件名>──▶ nginx(443)
   ├─ auth_basic  /etc/nginx/.dfwx-upload.htpasswd   （用户 <管理员账号>，复用后台管理员口令）
   ├─ client_max_body_size 200m / proxy_request_buffering off
   └─ proxy_pass http://127.0.0.1:8091/  ──▶ dfwx-upload.service（python3，User=www-data）
                                              └─ 流式收流 + 边收边算 sha256
                                                 └─ os.replace 落到 /var/www/dfwx/apk/<文件名>，chmod 644
```

- 上传服务代码：`/opt/dfwx-upload/server.py`；unit：`/etc/systemd/system/dfwx-upload.service`（`127.0.0.1:8091`）
- 为什么不用 nginx 自带 `dav_methods`：本机 nginx 虽编了 `--with-http_dav_module`，但该 location 上 PUT 一律被静态模块拦成 405，改用自建服务后行为完全可控
- **上传只能走 443**：80 端口 server 块顶部有 `if ($request_method !~ ^(GET|HEAD)$) { ... return 405; }` 的方法守卫（只对 `/pb/` 放行写），所以 PUT 走 80 是 405
- 端到端实测（1 MiB 随机文件）：无认证 401 / 错误口令 401 / 正确口令 **200**，落盘属主 `www-data:www-data` 权限 `644`，**sha256 与大小和本地完全一致**，`http://39.106.33.135/apk/<文件名>` 下载回来 sha256 仍一致；非法文件名（含空格）被服务以 400 拒绝；`%2e%2e%2f` 穿越尝试被 nginx 规范化挡在 location 之外。测试文件已删除，`/apk/` 清单前后指纹一致

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
   `curl -s 'http://39.106.33.135/pb/api/collections/release/records?perPage=1&sort=-id'` 应返回 JSON（不是 302/HTML）。
5. **同一份东西在两处部署 = 迟早有一处是旧的**。控制台曾同时放在 `/var/www/dfwx/admin/` 与
   `pb_public/`，结果用户打开的是旧的那份、症状却表现为"App 更新功能坏了"，排查绕了一大圈。
   现在控制台只有一份，另一处是跳转占位页。
