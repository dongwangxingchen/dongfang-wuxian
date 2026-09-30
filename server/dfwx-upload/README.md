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
| `server.py` | 监听 `127.0.0.1:8091`，处理 `PUT /<文件名>`，流式落盘到 `/var/www/dfwx/apk/`，**顺手算 sha256** 并返回 JSON |
| `dfwx-upload.service` | systemd 单元（`User=www-data`，崩溃自动重启） |

## 部署

```bash
sudo mkdir -p /opt/dfwx-upload
sudo cp server.py /opt/dfwx-upload/server.py
sudo cp dfwx-upload.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now dfwx-upload

# 密码文件（复用后台管理员口令，全程不打印）
printf 'dongfang@dfwx.local:%s\n' "$(openssl passwd -apr1 "$(sudo cat /root/.dfwx-pb-admin-pass)")" \
  | sudo tee /etc/nginx/.dfwx-upload.htpasswd >/dev/null
sudo chown root:www-data /etc/nginx/.dfwx-upload.htpasswd && sudo chmod 640 /etc/nginx/.dfwx-upload.htpasswd
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
2. **`apkUrl` 故意用 `http://`**（前端 `APK_ORIGIN` 常量）。
   App 走明文下载；改成 https 会因为那张证书不被设备信任而直接下载失败。

## 已知遗留

80 端口上的 `/admin/apk-upload/` 仍然返回 405（443 正常）。
不影响使用——浏览器实际走 443；App 只读 `/apk/`（80 与 443 都正常）。
原因未查明（location 匹配、无认证 GET 401、无重复块、重载成功，都已核对过）。
