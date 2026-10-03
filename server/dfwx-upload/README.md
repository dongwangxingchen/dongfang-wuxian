# 后台一键上传安装包（2026-10-01 建）

## 为什么有这个东西

用户在后台点「选择安装包」时，希望**大小和校验值自动算好**，而不是手填。
要做到这一点，浏览器必须能：
1. 本地算出 sha256 与字节数（`crypto.subtle`，**要求 https 或 localhost**）；
2. 把文件传到服务器。

## 为什么不直接用 nginx 的 dav 模块

本机 nginx 确实编了 `--with-http_dav_module`（`nginx -V` 可见），配置也通过了 `nginx -t`，
但**同一个 location 里 `auth_basic` 与 `GET` 都正常、`PUT` 却一律被静态模块拦成 405**。
排查过：location 匹配正确（无认证 GET 返回 401）、没有重复 location、重载成功、
`dav_methods PUT DELETE` 语法有效。443 上的同一个 location 换成 `proxy_pass` 后**立刻正常**。
与其继续和 dav 纠缠，不如用一个十几行、行为完全可控的服务。

## 组成

| 文件 | 作用 |
|---|---|
| `server.py` | 监听 `127.0.0.1:8091`，**上传 / 列目录 / 详情 / 删除** 四个动作，流式落盘到 `/var/www/dfwx/apk/`，**顺手算 sha256** 并返回 JSON |
| `axml.py` | 从 APK 二进制里读 `versionCode` / `versionName`（不需要 aapt、不需要 java） |
| `dfwx-upload.service` | systemd 单元（`User=www-data`，崩溃自动重启） |
| `server_test.py` | 接口自测：临时目录起真服务，30 项断言（含防路径穿越、挂死回归） |
| `e2e_test.py` | 端到端：打真实 HTTPS 入口，走完整 nginx + basic auth + 真实文件链路 |

## 接口（[DFW-134 2026-10-03] 新增后三个）

nginx 的 `location /admin/apk-upload/` 是 `proxy_pass`，**不限制方法**，
所以 GET/DELETE 直接就能用，**加这三个接口没改一行 nginx 配置**。

| 方法 | 路径 | 作用 |
|---|---|---|
| `PUT` | `/<文件名>` | 上传。流式落盘 + 算 sha256 + 读包内版本号 |
| `GET` | `/` | 列目录。每条含 name / size / mtime / sha256 / versionCode / versionName |
| `GET` | `/<文件名>` | 单个文件详情。**没记过哈希的老文件在这一步现算并缓存** |
| `DELETE` | `/<文件名>` | 删除（连同元数据） |

### 元数据为什么单独存一份

`/opt/dfwx-upload/meta/<文件名>.json`。理由：站点目录现在 29 个包、合计约 1 GB，
**每打开一次「网盘」页就把 1 GB 读一遍**太蠢（这台机器 2 核、无 swap）。
上传时本来就在流式算 sha256，顺手写下来即可。
不放在 `APK_DIR` 里是因为那是 nginx 对外提供的公开目录，丢 json 进去会污染它。

网盘上线前的 29 个老文件用一次性脚本补算了哈希，**5.9 秒**跑完。
补算结果与线上版本记录逐字段一致（`v1.0.0` 的 sha256 = `e24feb77…`，size = 36840658）。

### 环境变量（为了能测）

`DFWX_APK_DIR` / `DFWX_META_DIR` / `DFWX_PORT` / `DFWX_READ_TIMEOUT` 都可覆盖，
默认值是生产值。自测就是靠这个在临时目录、临时端口上起真服务，不碰生产数据。

## 一个实测踩到的真 bug：客户端少发字节会把线程永久占住

自测里故意少发一个字节（声明 `Content-Length: 15`，实际只发 14），
服务端就卡在 `self.rfile.read()` 里**永远不出来** ——
`if not chunk: break` 只在连接被关掉时才触发，客户端不关连接就死等。
`ThreadingHTTPServer` 是「一个连接一个线程」，每个这种请求永久占一个线程。

修法：`Handler.timeout = 120`（**单次 recv** 的上限，不是整个上传的总时长上限，
300 MB 慢慢传不会被掐，只有「连续 120 秒一个字节都不来」才算死连接）。
`server_test.py` 第 10 组用 2 秒超时单独起一个服务专门测这个，
**撤掉超时会红**（反向探针验证过）。

## 部署

```bash
sudo mkdir -p /opt/dfwx-upload
sudo cp server.py axml.py /opt/dfwx-upload/
# [DFW-134] 元数据目录必须存在且可写（服务以 www-data 身份跑）
sudo install -d -o www-data -g www-data -m 755 /opt/dfwx-upload/meta
sudo cp dfwx-upload.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now dfwx-upload

# 密码文件（复用后台管理员口令，全程不打印）
printf '<管理员账号>:%s\n' "$(openssl passwd -apr1 "$(sudo cat <凭据文件>)")" \
  | sudo tee /etc/nginx/.dfwx-upload.htpasswd >/dev/null
sudo chown root:www-data /etc/nginx/.dfwx-upload.htpasswd && sudo chmod 640 /etc/nginx/.dfwx-upload.htpasswd
```

## 自测

```bash
cd server/dfwx-upload

# 接口自测（本地临时目录起真服务，不碰生产；30 项）
python3 server_test.py

# 端到端（打真实 HTTPS 入口，需要 <本地目录>/<凭据文件> 里的密码）
python3 e2e_test.py
```

控制台「网盘」页的渲染逻辑另有单测（在 `server/dfwx-admin/`）：

```bash
cd server/dfwx-admin && node pan_render_test.mjs
```

nginx 侧在**每个对外 server 块**（80 的 `sites-enabled/dfwx` 与 443 的 `sites-enabled/dsh-remote`）都要加：

```nginx
location /admin/apk-upload/ {
    auth_basic "dfwx upload";
    auth_basic_user_file /etc/nginx/.dfwx-upload.htpasswd;
    client_max_body_size 200m;
    proxy_pass http://127.0.0.1:8091/;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_request_buffering off;
    proxy_read_timeout 600s;
    proxy_send_timeout 600s;
}
```

## 两个必须同时存在的坑

1. **`/admin/` 与 `/apk/` 必须在 443 上也挂着**。
   浏览器输入裸 IP 会先试 https，只挂在 80 的话后台打开是 **401**（落到 DSH 上了）。
   这正是上个会话把 PocketBase 后台挪到 443 的同一个原因，新控制台当时漏了。
2. **`apkUrl` 现在用 `https://`**（前端 `APK_ORIGIN` 常量，`index.html`）。
   ⚠️ 这里和本文件 2026-10-01 的旧结论**相反**，2026-10-03 重新核对过：

   | | 旧结论（10-01） | 实测（10-03） |
   |---|---|---|
   | `APK_ORIGIN` | 说"故意用 http" | 代码里是 **`https://39.106.33.135`** |
   | https 能不能下 | 说"证书不被设备信任，会直接失败" | **能下**（`curl` 206；证书链完整） |

   为什么现在 https 可用：
   - 证书是 Let's Encrypt 签的 **IP 证书**，链是 `叶证书 → YE1 → ISRG Root YE → ISRG Root X2`，
     用系统信任库验证 `Verify return code: 0 (ok)`；Android 16 信任 ISRG Root X2。
   - `app/src/main/res/xml/network_security_config.xml` 里 `39.106.33.135` 那条
     `cleartextTrafficPermitted="true"` **只放行明文，不禁止 https**；
     base-config 的信任锚是「系统 + 用户」，所以 https 与「用户装了 VPN 证书」两种情况都能过。

   ⚠️ **仍然存在的脆弱点（未修，需用户决策）**：这张 IP 证书**只有 6 天有效期**
   （实测 `notBefore=Oct 3 04:41` / `notAfter=Oct 9 20:41`）。`certbot.timer` 在跑、
   今天也续过，所以正常情况没问题；但**一旦 certbot 连续失败两天，更新下载就会断**，
   而 App 端没有 http 回退。
   要彻底消除这个依赖，可以把 `apkUrl` 换回 `http://` ——
   该 IP 在 `network_security_config.xml` 里已显式允许明文，
   且下载后有 sha256 + 包名 + 签名三重校验，明文不构成实际风险。
   **这是产品取舍，等用户拍板，不擅自改。**

## 已知遗留

80 端口上的 `/admin/apk-upload/` 仍然返回 405（443 正常）。
不影响使用——浏览器实际走 443；App 只读 `/apk/`（80 与 443 都正常）。
原因未查明（location 匹配、无认证 GET 401、无重复块、重载成功，都已核对过）。
