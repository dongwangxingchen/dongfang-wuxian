# DFWX-SEC-001｜外部下载与安装边界

**状态**：实现已接线，聚焦 JVM 测试通过，手机 release 负向验证未完成。  
**范围**：外部网页/WebView 下载 → 下载条目 → APK 安装；不包含内置 AI、签名、ADB 生命周期和 UI 动画。

## 已确认根因

此前 `MainActivity.startWebDirectDownload()` 把外部 URL 同时写入 `item.url`、`item.shareUrl`，并调用 `directResolver.remember(value,value,...)`，随后进入普通 `beginDownload()`。因此全局“下载完成后自动安装”和静默安装偏好可能影响外部 APK。

## 本轮已落地

- 新增 `DownloadSourcePolicy`：`lanzou`、`external`、`update`、未知/旧记录 `legacy`；旧记录按未知来源保守处理。
- 新增 `DownloadUrlPolicy`：外部下载只接受 HTTPS；拒绝 userinfo、localhost、`.local`/`.internal`/`home.arpa`、loopback、私网、链路本地、多播、共享地址段、IPv4-mapped IPv6 私网地址；下载器每次重定向重新校验并在连接前解析主机。
- `startWebDirectDownload()` 不再写入 `DirectLinkResolver`，改为 `newExternalDownloadEntry()` + `SegmentDownloader.startExternal()`。
- `DownloadEntry.source` 写入下载历史；旧历史缺失来源时归一化为 `legacy`，不再恢复为可信直链缓存。
- 外部/未知来源 APK：不受自动安装偏好影响；禁止 Shell/Shizuku 静默安装；安装器前弹出来源确认；“更多方式”入口也回到安装确认。
- 官方更新条目标记为 `update`，保留原有 SHA-256、包名、签名和版本校验链。

## 自动化测试

- `app/src/test/java/cc/nkbr/lanzouplus/DownloadPolicyTest.java`
  - HTTP/FTP/空地址/userinfo 拒绝；
  - localhost、IPv4/IPv6 私网、IPv4-mapped IPv6、共享地址段拒绝；
  - DNS 解析失败 fail-closed；
  - 外部和 legacy 必须确认且不能自动/静默安装；
  - Lanzou/update 仍保留原安装策略。

## 验收命令

```bash
./gradlew :app:compileEmptyDebugJavaWithJavac --no-daemon
./gradlew :app:testEmptyDebugUnitTest --tests cc.nkbr.lanzouplus.DownloadPolicyTest --no-daemon
./gradlew :app:testEmptyDebugUnitTest --no-daemon
```

## 尚未完成

1. 重跑本卡新增测试并处理真实失败；
2. 用 fake Intent/Activity 测试证明外部入口不会自动/静默安装；
3. 手机 release 包做一次不清数据、不卸载的负向验证：HTTPS 外部 APK 下载完成后不自动弹安装器，手动安装必须确认；
4. 记录真实重定向链和 DNS/私网测试结果；
5. 修订 `SECURITY.md` 的旧版本、旧资产命名、内置 AI 和 HTTP 描述；
6. 完成后再进入 SEC-002/SEC-003/SEC-004，不要把签名、AI、日志和生命周期混进本任务。

## 禁止事项

- 不把外部 URL 再写入 `DirectLinkResolver.remember()`；
- 不给外部/legacy 条目设置 `autoInstall=true`；
- 不让外部/legacy 条目进入 `silentInstallEntries()`；
- 不用手机清数据、卸载、删除文件或 `pm clear` 作为验证手段；
- 不把当前静态测试结果表述为“已证明不存在所有 SSRF”或“手机已完成验收”。
