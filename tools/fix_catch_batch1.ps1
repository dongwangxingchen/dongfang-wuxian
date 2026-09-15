# B1 穿插任务：下载链路空 catch 补日志（DirectLinkResolver / SegmentDownloader / DirectCookiePool）
# 策略：InterruptedException→Log.i（正常打断不算错）；其余→Log.w 带异常堆栈；tag=文件名，栈里自带行号
$ErrorActionPreference = 'Stop'
$targets = @('DirectLinkResolver.java','SegmentDownloader.java','DirectCookiePool.java')
foreach ($name in $targets) {
  $f = "D:\heiyao\src\app\src\main\java\cc\nkbr\lanzouplus\$name"
  $c = [System.IO.File]::ReadAllText($f)
  $before = ([regex]::Matches($c, 'catch\s*\([^)]*\)\s*\{\s*\}')).Count
  $c = [regex]::Replace($c, 'catch\((\w+)\s+(\w+)\)\s*\{\s*\}', {
    param($m)
    $exType = $m.Groups[1].Value; $exVar = $m.Groups[2].Value
    if ($exType -eq 'InterruptedException') {
      return "catch(" + $exType + " " + $exVar + "){android.util.Log.i(""" + $name + """, """ + $name + " interrupted: ""+" + $exVar + ".getMessage());}"
    }
    return "catch(" + $exType + " " + $exVar + "){android.util.Log.w(""" + $name + """, """ + $name + " " + $exType + ": ""+" + $exVar + ".getMessage(), " + $exVar + ");}"
  })
  [System.IO.File]::WriteAllText($f, $c)
  $after = ([regex]::Matches($c, 'catch\s*\([^)]*\)\s*\{\s*\}')).Count
  Write-Output ($name + " : " + $before + " -> " + $after)
}
