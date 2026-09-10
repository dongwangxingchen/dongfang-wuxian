function L($hex){ $r=($hex -shr 16) -band 255; $g=($hex -shr 8) -band 255; $b=$hex -band 255; function ch($v){ $s=$v/255.0; if($s -le 0.03928){ return $s/12.92 } else { return [math]::Pow(($s+0.055)/1.055,2.4) } }; return 0.2126*(ch $r)+0.7152*(ch $g)+0.0722*(ch $b) }
function CR($a,$bb){ $l1=L $a; $l2=L $bb; if($l1 -lt $l2){ $t=$l1;$l1=$l2;$l2=$t }; return [math]::Round((($l1+0.05)/($l2+0.05)),2) }
$bg=0x141413; $sf=0x1D1C1A; $s2=0x26241F; $bd=0x2E2C28; $pr=0xD97757; $tx=0xEAE7DF; $mu=0xA9A39A; $er=0xD47563; $sec=0x61AAF2; $hi=0xE89A86;
Write-Output ("text/bg           = "+(CR $tx $bg))
Write-Output ("text/surface      = "+(CR $tx $sf))
Write-Output ("text/surface2     = "+(CR $tx $s2))
Write-Output ("muted/bg          = "+(CR $mu $bg))
Write-Output ("muted/surface     = "+(CR $mu $sf))
Write-Output ("muted/surface2    = "+(CR $mu $s2))
Write-Output ("primary/bg        = "+(CR $pr $bg))
Write-Output ("primary/surface   = "+(CR $pr $sf))
Write-Output ("primary/surface2  = "+(CR $pr $s2))
Write-Output ("bgText/primary(=) = "+(CR $bg $pr))
Write-Output ("error/bg          = "+(CR $er $bg))
Write-Output ("secondary/bg      = "+(CR $sec $bg))
Write-Output ("border/bg         = "+(CR $bd $bg))
Write-Output ("border/surface    = "+(CR $bd $sf))
Write-Output ("primaryHi/bg      = "+(CR $hi $bg))
Write-Output "--- luminance ---"
Write-Output ("legacyBG(0B0A12)  = {0:P1}" -f (L 0x0B0A12))
Write-Output ("v124BG(0F0D16)    = {0:P1}" -f (L 0x0F0D16))
Write-Output ("newBG(141413)     = {0:P1}" -f (L $bg))
Write-Output ("newSURFACE(1D1C1A)= {0:P1}" -f (L $sf))
Write-Output ("newSURFACE2(26241F)= {0:P1}" -f (L $s2))
Write-Output ("newBORDER(2E2C28) = {0:P1}" -f (L $bd))
$n=L $bg; $o=L 0x0F0D16; $l=L 0x0B0A12
Write-Output ("v124bg -> newbg 亮度增幅 = {0:P0}%" -f (($n/$o-1)*100))
Write-Output ("legacybg-> newbg 亮度增幅 = {0:P0}%" -f (($n/$l-1)*100))
