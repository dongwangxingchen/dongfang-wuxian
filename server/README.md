# server/ · 东方无限后端（自建）

本目录存放**服务端**资产，**不参与 APK 构建**。
（曾短暂放到 `app/src/main/assets/`，但那会把后台界面打进用户安装包——无必要且有暴露面，已移出。）

## 组成

| 路径 | 说明 |
|---|---|
| `dfwx-admin/index.html` | **中文管理控制台**（单文件，无依赖） |

## 部署位置（阿里云轻量 `39.106.33.135`）

| 项 | 值 |
|---|---|
| 后台程序 | PocketBase 0.40.4，`/opt/dfwx-pb/pocketbase`（systemd: `dfwx-pb`，仅监听 `127.0.0.1:8090`） |
| 数据目录 | `/opt/dfwx-pb/pb_data`（SQLite + 上传文件） |
| **控制台** | `/opt/dfwx-pb/pb_public/index.html`（PocketBase 自动托管根路径） |
| APK 分发 | `/var/www/dfwx/apk/`（nginx，支持断点续传） |
| nginx | `:80` 与 `:443` 各有一段 `location /pb/` 反代 |
| 管理账号 | `dongfang@dfwx.local`，密码在 `/root/.dfwx-pb-admin-pass`（600，**永不入 git**） |

## 访问入口

- **控制台（用户使用）**：`https://39.106.33.135/pb/`
- PocketBase 原版后台（备用，英文）：`https://39.106.33.135/pb/_/`
- App 读数据的 API：`http://39.106.33.135/pb/api/collections/<集合>/records`

> 为什么控制台必须走 HTTPS：现代浏览器会把 `http://` 自动升级为 `https://`，
> 走 80 会被升级后撞上 443 的其他服务（实测 `ERR_HTTP_RESPONSE_CODE_FAILURE`）。

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

```bash
scp server/dfwx-admin/index.html dfwx:/tmp/ai.html
ssh dfwx 'sudo cp /tmp/ai.html /opt/dfwx-pb/pb_public/index.html && \
          sudo chown www-data:www-data /opt/dfwx-pb/pb_public/index.html && rm -f /tmp/ai.html'
```

## 发版时同步 APK

**服务器直连 GitHub 下大文件极慢（实测 36MB / 16 分钟未完成），所以用 scp 直推：**

```bash
scp ~/heiyao/黑曜/03-构建产物/dongfang-wuxian-vX.Y.Z.apk dfwx:/tmp/apk.apk
ssh dfwx 'sudo mv /tmp/apk.apk /var/www/dfwx/apk/dongfang-wuxian-vX.Y.Z.apk && \
          sudo chown www-data:www-data /var/www/dfwx/apk/dongfang-wuxian-vX.Y.Z.apk'
```
然后更新 `release` 集合（控制台「版本」页可手改，或由自动同步脚本 `dfwx-sync.sh` 兜底）。

## 踩坑记录

1. **PocketBase 0.40 的 `created` 不是可排序字段**：`sort=-created` 返回 **400**，
   必须用 `sort=-id`。坑在"单条写入 200 成功、列表查询静默失败"，表现为
   「提示发布成功但看不到」——App 端 `RemoteConfigClient` 与控制台都已修正。
2. **PocketBase 自身不对错误密码限流**（实测连打 8 次全 400），
   已在 nginx 加登录端点限流（6 次/分钟，实测第 4 次起 429）。
3. **`org.json` 与 `android.util.Log` 在纯 JVM 单测里是空桩**，
   任何用到它们的类测试必须挂 Robolectric。
