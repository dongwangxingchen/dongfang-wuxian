#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""v1.21.4 字体重建管线（任务0 根因修复）。

修复三处实锤根因：
  1. 行高度量膨胀：思源 hhea 1160/-288 → 行高 1.448×字号（主流 UI 字体 1.15~1.3）。
     统一归一到 964/-236/0 = 1.20×，hhea + OS/2 typo + usWin 三处一致，并置 USE_TYPO_METRICS。
  2. 缺字混排：旧子集 cmap 缺「¥」等 → 支付页中西混拼。字符集扩为
     ASCII + GB2312 符号区(区1-9) + GB2312 一级常用字(3755) + 源码全扫(Java/Kotlin/XML) + 显式符号表。
  3. 默认字重=100：任何未显式设 wght 的消费方渲染极细。instancer 三元组重建默认实例=400。

产出（写入 app/src/main/assets/fonts/）：
  - NotoSansSC-VF.ttf   思源黑体可变子集（wght 100-900，默认 400）
  - SpaceGrotesk-VF.ttf Space Grotesk 可变子集（wght 300-700，默认 400；Latin 层，Styrene B 开源最近似平替）
  - 两份 OFL 许可证随字体入库
"""
import os
import re
import sys
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = Path(__file__).resolve().parent.parent          # ~/heiyao/src
SRC = ROOT / "tools" / "fontsrc"                        # 源 VF 与 OFL 许可证
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "fonts"
TMP = ROOT / "tools" / "fontbuild"

EXTRA_SYMBOLS = (
    "¥￥€£‰§©®™°±×÷≤≥≠≈∞←→↑↓←→•·…—―"
    "‘’“”「」『』《》〈〉【】〔〕（）［］｛｝、。，：；！？～"
    "％＋－＝｜＼／＿　　√☆★○●◎◇◆□■△▲"
)


def gb2312_rows(rows):
    """按 GB2312 区位码生成字符（行=区，0xA1..0xFE=位）。"""
    chars = []
    for row in rows:
        hi = 0xA0 + row
        for lo in range(0xA1, 0xFF):
            try:
                chars.append(bytes([hi, lo]).decode("gb2312"))
            except UnicodeDecodeError:
                pass
    return chars


def scan_source_chars():
    """扫应用源码（宿主 + rikkahub 含 vendor）里出现过的全部 CJK/全角字符。"""
    pat = re.compile(r"[\u2E80-\u9FFF\uF900-\uFAFF\uFF01-\uFF60\u3000-\u303F\u2460-\u24FF\u2600-\u27BF]")
    out = set()
    bases = [ROOT / "app" / "src" / "main", ROOT / "rikkahub" / "app" / "src" / "main"]
    for base in bases:
        if not base.exists():
            continue
        for p in base.rglob("*"):
            if not p.is_file() or p.suffix not in {".java", ".kt", ".kts", ".xml"}:
                continue
            try:
                out |= set(pat.findall(p.read_text(encoding="utf-8", errors="ignore")))
            except OSError:
                continue
    return out


def subset_to_file(src, dst, text):
    opts = subset.Options()
    opts.layout_features = ["*"]
    opts.name_IDs = ["*"]
    opts.name_legacy = True
    opts.notdef_outline = True
    opts.recalc_bounds = True
    opts.drop_tables += ["vhea", "vmtx"]   # 无竖排需求，省体积
    font = subset.load_font(str(src), opts)
    ss = subset.Subsetter(options=opts)
    ss.populate(text=text)
    ss.subset(font)
    subset.save_font(font, str(dst), opts)


def normalize(src, dst, wmin, wnew_default, wmax):
    """改 fvar 默认实例 + 三表度量归一到 1.20 行高。"""
    f = TTFont(str(src))
    instancer.instantiateVariableFont(
        f, {"wght": (wmin, wnew_default, wmax)}, inplace=True
    )
    upm = f["head"].unitsPerEm
    asc = round(upm * 0.964)
    desc = round(upm * 0.236)
    f["hhea"].ascent, f["hhea"].descent, f["hhea"].lineGap = asc, -desc, 0
    os2 = f["OS/2"]
    os2.sTypoAscender, os2.sTypoDescender, os2.sTypoLineGap = asc, -desc, 0
    os2.usWinAscent, os2.usWinDescent = asc, desc
    os2.fsSelection |= 0x80  # USE_TYPO_METRICS：任何读取路径都拿到同一行高
    f.save(str(dst))


def verify(path, must_have, wmin, wdef, wmax, ratio_target=1.20):
    f = TTFont(str(path))
    cmap = f.getBestCmap()
    # dingbat 区（✕✗❤✨ 等）思源本体不含，链条末端回退系统字体渲染，不做硬断言
    hard = sorted({c for c in must_have
                   if ord(c) not in cmap and c.strip()
                   and not (0x2600 <= ord(c) <= 0x27BF)})
    assert not hard, f"{path.name} 缺字: {hard[:30]}"
    w = [a for a in f["fvar"].axes if a.axisTag == "wght"][0]
    assert (w.minValue, w.defaultValue, w.maxValue) == (wmin, wdef, wmax), \
        f"{path.name} wght 轴异常: {w}"
    upm = f["head"].unitsPerEm
    ratio = (f["hhea"].ascent - f["hhea"].descent + f["hhea"].lineGap) / upm
    assert abs(ratio - ratio_target) < 0.01, f"{path.name} 行高比 {ratio:.3f}"
    size_mb = os.path.getsize(path) / 1e6
    print(f"  ✓ {path.name}: {size_mb:.2f}MB | glyphs {f['maxp'].numGlyphs} | "
          f"wght {w.minValue}-{w.defaultValue}-{w.maxValue} | 行高比 {ratio:.3f}")
    return size_mb


def main():
    TMP.mkdir(parents=True, exist_ok=True)
    ASSETS.mkdir(parents=True, exist_ok=True)

    # ---------- 1. 思源黑体 ----------
    print("[1/2] Noto Sans SC 子集化…")
    charset = set(chr(c) for c in range(0x20, 0x7F))          # ASCII 可打印
    charset |= set(gb2312_rows(range(1, 10)))                  # 符号区：标点/带圈数字/全角/拼音/制表
    charset |= set(gb2312_rows(range(16, 56)))                 # 一级常用字 3755
    charset |= scan_source_chars()                              # 源码全扫
    charset |= set(EXTRA_SYMBOLS)
    text = "".join(sorted(c for c in charset if not c.isspace() or c == "　"))
    print(f"  字符集 {len(text)} 字符")
    raw = TMP / "noto.subset.ttf"
    subset_to_file(SRC / "NotoSansSC-VF-src.ttf", raw, text)
    out_noto = ASSETS / "NotoSansSC-VF.ttf"
    normalize(raw, out_noto, 100, 400, 900)
    verify(out_noto, text, 100, 400, 900)

    # ---------- 2. Space Grotesk ----------
    print("[2/2] Space Grotesk 子集化…")
    latin = set(chr(c) for c in range(0x20, 0x7F)) | set(EXTRA_SYMBOLS)
    latin_text = "".join(sorted(latin))
    raw2 = TMP / "grotesk.subset.ttf"
    subset_to_file(SRC / "SpaceGrotesk-VF-src.ttf", raw2, latin_text)
    out_sg = ASSETS / "SpaceGrotesk-VF.ttf"
    normalize(raw2, out_sg, 300, 400, 700)
    verify(out_sg, (chr(c) for c in range(0x20, 0x7F)), 300, 400, 700)

    # ---------- 3. 许可证随字体入库 ----------
    import shutil
    shutil.copy(SRC / "NotoSansSC-OFL.txt", ASSETS / "NotoSansSC-OFL.txt")
    shutil.copy(SRC / "SpaceGrotesk-OFL.txt", ASSETS / "SpaceGrotesk-OFL.txt")

    print("完成。行高 1.20、默认字重 400、字符集扩容均已通过断言。")


if __name__ == "__main__":
    sys.exit(main())
