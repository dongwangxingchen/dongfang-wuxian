$ErrorActionPreference = 'Stop'
$f = 'D:\heiyao\src\rikkahub\app\src\main\java\me\rerere\rikkahub\RouteActivity.kt'
$utf8 = [System.Text.UTF8Encoding]::new($false)
$c = [System.IO.File]::ReadAllText($f, $utf8)

$region = [System.IO.File]::ReadAllText('D:\heiyao\src\tools\route_region.txt', $utf8)

$anchor = '    @OptIn(ExperimentalComposeUiApi::class)'
$i1 = $c.IndexOf($anchor)
if ($i1 -lt 0) { throw 'anchor OptIn not found' }
$anchor2 = 'sealed interface Screen : NavKey {'
$i2 = $c.IndexOf($anchor2)
if ($i2 -lt 0) { throw 'sealed not found' }

$new = $c.Substring(0, $i1) + $region + $c.Substring($i2 + $anchor2.Length)
[System.IO.File]::WriteAllText($f, $new, $utf8)
Write-Output ('region replaced, length ' + $new.Length)
