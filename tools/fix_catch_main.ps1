# MainActivity 空 catch 人工复核批：59 处全部补日志（复核结论：无逐帧 draw 路径；
# 全部为用户动作/IO/资源释放/JSON 回退，异常触发时补日志不影响行为）
$ErrorActionPreference = 'Stop'
$f = 'D:\heiyao\src\app\src\main\java\cc\nkbr\lanzouplus\MainActivity.java'
$c = [System.IO.File]::ReadAllText($f)
$before = ([regex]::Matches($c, 'catch\s*\((\w+)\s+(\w+)\)\s*\{\s*\}')).Count
$c = [regex]::Replace($c, 'catch\((\w+)\s+(\w+)\)\s*\{\s*\}', {
  param($m)
  $exType = $m.Groups[1].Value; $exVar = $m.Groups[2].Value
  if ($exType -eq 'InterruptedException') {
    return "catch(" + $exType + " " + $exVar + "){android.util.Log.i(""MainActivity"", ""MainActivity interrupted: ""+" + $exVar + ".getMessage());}"
  }
  return "catch(" + $exType + " " + $exVar + "){android.util.Log.w(""MainActivity"", ""MainActivity " + $exType + ": ""+" + $exVar + ".getMessage(), " + $exVar + ");}"
})
[System.IO.File]::WriteAllText($f, $c)
$after = ([regex]::Matches($c, 'catch\s*\([^)]*\)\s*\{\s*\}')).Count
Write-Output ("MainActivity : " + $before + " -> " + $after)
