Get-ChildItem '<仓库根>\app\src\main\java\cc\nkbr\lanzouplus' -Filter *.java | ForEach-Object {
  $c = [System.IO.File]::ReadAllText($_.FullName)
  $n = ([regex]::Matches($c, 'catch\s*\([^)]*\)\s*\{\s*\}')).Count
  if ($n -gt 0) { Write-Output ($_.Name + ': ' + $n) }
}
