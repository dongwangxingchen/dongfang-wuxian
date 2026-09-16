# 列出 MainActivity 全部空 catch 及前后各一行上下文，供人工复核
$c = [System.IO.File]::ReadAllText('D:\heiyao\src\app\src\main\java\cc\nkbr\lanzouplus\MainActivity.java')
$lines = $c -split "`n"
$rx = [regex]'catch\s*\((\w+)\s+(\w+)\)\s*\{\s*\}'
$n = 0
for ($i = 0; $i -lt $lines.Count; $i++) {
  foreach ($m in $rx.Matches($lines[$i])) {
    $n++
    $ctxStart = [Math]::Max(0, $i - 1)
    $prev = $lines[$ctxStart]
    $prevShort = $prev.Substring([Math]::Max(0, $prev.Length - 150))
    Write-Output ("#$n L" + ($i+1) + " [" + $m.Groups[1].Value + " " + $m.Groups[2].Value + "]")
    Write-Output ("   ctx: ..." + $prevShort)
    Write-Output ("   hit-pos: " + $m.Index)
  }
}
Write-Output ("TOTAL " + $n)
