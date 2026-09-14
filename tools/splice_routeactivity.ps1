$ErrorActionPreference = 'Stop'
$f = 'D:\heiyao\src\rikkahub\app\src\main\java\me\rerere\rikkahub\RouteActivity.kt'
$c = [System.IO.File]::ReadAllText($f)

$markerFun = 'fun AppRoutes()'
$i1 = $c.IndexOf($markerFun)
if ($i1 -lt 0) { throw 'AppRoutes not found' }
# 回退到该行的 @Composable 注解行首
$lineStart = $c.LastIndexOf("@Composable", $i1)
if ($lineStart -lt 0) { throw 'annotation not found' }

$i2 = $c.IndexOf('sealed interface Screen')
if ($i2 -lt 0) { throw 'sealed interface not found' }

$newBlock = @'
@Composable
    fun AppRoutes() {
        // [DFWX PATCH P16] 界面整体迁往 dfwx/RikkaHubEmbed.kt（东方无限宿主把本界面内嵌进
        // 主界面底栏"AI"页，不再跳转独立 Activity）。上游 AppRoutes 更新时，对照上游重放
        // RikkaHubEmbed 的对应增删；此处仅保留：导航栈回调（供分享/快捷入口 push）与
        // 无障碍设置桥接。音量键滚动功能内嵌态暂不可用（登记于 PATCHES P16）。
        RikkaHubEmbed(
            activity = this,
            onBackStackReady = {
                navStack = it
                while (pendingIntents.isNotEmpty()) {
                    handleIntent(pendingIntents.removeFirst())
                }
            },
            onOpenUsageAccessSettings = { openUsageAccessSettings() },
        )
    }
}

'@

$new = $c.Substring(0, $lineStart) + $newBlock + $c.Substring($i2)
[System.IO.File]::WriteAllText($f, $new, [System.Text.UTF8Encoding]::new($false))
Write-Output ('spliced ok, new length ' + $new.Length)
