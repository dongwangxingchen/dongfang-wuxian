# 安全策略

当前维护版本：`1.8.x`（分支 test，APK 经 GitHub Releases 分发，历史上经 gofile 过渡分发）。

## 报告安全问题

请不要在公开 issue 中提交密码、API Key、私有目录、登录态、签名材料或可直接利用的细节。
请通过仓库作者的私下渠道联系，附上：受影响版本（设置 → 关于）、复现条件、预期影响。

## 分发与校验

- 正式更新只认 GitHub Releases 发布的 `东方无限-vX.Y.Z-release-*.apk`；
- Release 说明中附带 SHA-256，可自行校验；
- 升级安装需与已装版本同一签名（v1.x 全系同一 keystore），来路不明的"更新包"请拒绝安装。

## 已知边界（透明披露）

- 内置默认 AI 渠道的 Key 以资源形式打包在 APK 内（服务端已按共享 Key 设定限额）；如需关闭请删除该渠道自行填 Key；
- 为支持用户自配 http 中转站，`usesCleartextTraffic=true`（对齐上游 RikkaHub 行为），请尽量使用 https 端点；
- v1.8.x 起最低系统为 Android 8.0（API 26），Android 7.x 用户请停留在 v1.7.7。
