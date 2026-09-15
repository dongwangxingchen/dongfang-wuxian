# 空 catch 第三批：小文件 7 个（Support/ThemeEngine/LanzouWebActivity/AdbShellManager/PremiumCloudClient/PremiumSaveCoordinator/ToolHost）
# MainActivity 61 处除外：draw 路径逐帧异常盲补会刷屏，留人工复核批
$ErrorActionPreference = 'Stop'
$targets = @('Support.java','ThemeEngine.java','LanzouWebActivity.java','AdbShellManager.java','PremiumCloudClient.java','PremiumSaveCoordinator.java','ToolHost.java')
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
