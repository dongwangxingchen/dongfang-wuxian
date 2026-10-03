#!/usr/bin/env python3
"""把 README.md 渲染成一张和 GitHub 上效果一致的预览页。

为什么自己写：这台机器没有 headless 浏览器，也没有 markdown 库
（`markdown` / `markdown-it` / `playwright` / `puppeteer` 全都没有），
只有 Safari。所以用最少的代码覆盖 README 实际用到的语法，
再套上 GitHub 官方的 `github-markdown.css`，让预览和线上尽量接近。

支持的语法（README 里实际用到的那些）：
  标题 / 粗体 / 行内代码 / 链接 / 图片 / 代码块 / 有序无序列表 /
  表格 / 引用 / 分隔线 / GitHub 的 alert 提示块 / 原样透传的 HTML 块
"""
import html
import re
import sys

# ── 行内元素 ──────────────────────────────────────────────
def inline(text, base=""):
    # 行内代码先占位，免得里面的符号被后面的规则误伤
    codes = []

    def stash(m):
        codes.append(m.group(1))
        return f"\x00{len(codes) - 1}\x00"

    text = re.sub(r"`([^`]+)`", stash, text)
    text = html.escape(text, quote=False)

    # 图片：![alt](src)
    text = re.sub(
        r"!\[([^\]]*)\]\(([^)\s]+)\)",
        lambda m: f'<img src="{m.group(2)}" alt="{m.group(1)}">',
        text,
    )
    # 链接：[text](url)
    text = re.sub(
        r"\[([^\]]+)\]\(([^)\s]+)\)",
        lambda m: f'<a href="{m.group(2)}">{m.group(1)}</a>',
        text,
    )
    text = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", text)

    for i, code in enumerate(codes):
        text = text.replace(f"\x00{i}\x00", f"<code>{html.escape(code)}</code>")
    return text


# ── 块级元素 ──────────────────────────────────────────────
ALERTS = {
    "NOTE": ("note", "Note"),
    "TIP": ("tip", "Tip"),
    "IMPORTANT": ("important", "Important"),
    "WARNING": ("warning", "Warning"),
    "CAUTION": ("caution", "Caution"),
}


def render(md: str) -> str:
    # 去掉 HTML 注释（GitHub 也不显示）
    md = re.sub(r"<!--.*?-->", "", md, flags=re.S)
    lines = md.split("\n")
    out, i = [], 0

    while i < len(lines):
        line = lines[i]

        # 代码块
        if line.startswith("```"):
            lang = line[3:].strip()
            i += 1
            buf = []
            while i < len(lines) and not lines[i].startswith("```"):
                buf.append(lines[i])
                i += 1
            i += 1
            body = html.escape("\n".join(buf))
            cls = f' class="language-{lang}"' if lang else ""
            out.append(f"<pre><code{cls}>{body}</code></pre>")
            continue

        # 原样透传的单行 HTML（<img …>、<sub>…</sub>、<br>）
        if re.match(r"^\s*</?(img|sub|br)\b", line, re.I):
            out.append(line)
            i += 1
            continue

        # <div align="center"> 块：**标签保留，内部内容仍按 markdown 渲染**。
        # GitHub 就是这么干的 —— 徽章 [![x](img)](link) 写在这个块里照样变成可点的图。
        # 一开始我把整块原样透传，结果徽章直接显示成一行方括号源码，这次修正过来。
        if re.match(r"^\s*<div\b", line, re.I):
            open_tag = line
            i += 1
            inner = []
            depth = 1
            while i < len(lines):
                if re.match(r"^\s*<div\b", lines[i], re.I):
                    depth += 1
                elif re.match(r"^\s*</div>", lines[i], re.I):
                    depth -= 1
                    if depth == 0:
                        break
                inner.append(lines[i])
                i += 1
            i += 1
            out.append(open_tag + "\n" + render("\n".join(inner)) + "\n</div>")
            continue

        # 其余块级 HTML（<p>、<table>）原样透传
        if re.match(r"^\s*</?(p|table|thead|tbody|tr|td|th)\b", line, re.I):
            out.append(line)
            i += 1
            continue

        # GitHub alert 提示块：> [!IMPORTANT]
        m = re.match(r"^>\s*\[!(\w+)\]\s*$", line)
        if m:
            kind, label = ALERTS.get(m.group(1).upper(), ("note", m.group(1)))
            i += 1
            buf = []
            while i < len(lines) and lines[i].startswith(">"):
                buf.append(re.sub(r"^>\s?", "", lines[i]))
                i += 1
            inner = "\n".join(buf).strip()
            paras = [p.strip() for p in inner.split("\n") if p.strip()]
            first, rest = paras[0], paras[1:]
            body = f"<p>{inline(first)}</p>"
            if rest:
                body += "<ul>" + "".join(f"<li>{inline(p)}</li>" for p in rest) + "</ul>"
            out.append(
                f'<div class="markdown-alert markdown-alert-{kind}">'
                f'<p class="markdown-alert-title">{label}</p>{body}</div>'
            )
            continue

        # 引用
        if line.startswith(">"):
            buf = []
            while i < len(lines) and lines[i].startswith(">"):
                buf.append(re.sub(r"^>\s?", "", lines[i]))
                i += 1
            out.append("<blockquote><p>" + inline("\n".join(buf)) + "</p></blockquote>")
            continue

        # 表格
        if "|" in line and i + 1 < len(lines) and re.match(r"^\s*\|?[\s:|-]+\|", lines[i + 1]):
            def cells(row):
                row = row.strip()
                row = row[1:] if row.startswith("|") else row
                row = row[:-1] if row.endswith("|") else row
                return [c.strip() for c in row.split("|")]

            head = cells(line)
            i += 2
            rows = []
            while i < len(lines) and "|" in lines[i]:
                rows.append(cells(lines[i]))
                i += 1
            th = "".join(f"<th>{inline(c)}</th>" for c in head)
            tr = "".join(
                "<tr>" + "".join(f"<td>{inline(c)}</td>" for c in r) + "</tr>" for r in rows
            )
            out.append(f"<table><thead><tr>{th}</tr></thead><tbody>{tr}</tbody></table>")
            continue

        # 分隔线
        if re.match(r"^\s*---\s*$", line):
            out.append("<hr>")
            i += 1
            continue

        # 标题
        m = re.match(r"^(#{1,6})\s+(.*)$", line)
        if m:
            lvl = len(m.group(1))
            out.append(f"<h{lvl}>{inline(m.group(2))}</h{lvl}>")
            i += 1
            continue

        # 列表
        if re.match(r"^\s*[-*]\s+", line):
            buf = []
            while i < len(lines) and re.match(r"^\s*[-*]\s+", lines[i]):
                buf.append(re.sub(r"^\s*[-*]\s+", "", lines[i]))
                i += 1
            out.append("<ul>" + "".join(f"<li>{inline(x)}</li>" for x in buf) + "</ul>")
            continue

        if re.match(r"^\s*\d+\.\s+", line):
            buf = []
            while i < len(lines) and re.match(r"^\s*\d+\.\s+", lines[i]):
                buf.append(re.sub(r"^\s*\d+\.\s+", "", lines[i]))
                i += 1
            out.append("<ol>" + "".join(f"<li>{inline(x)}</li>" for x in buf) + "</ol>")
            continue

        # 空行 / 普通段落
        if not line.strip():
            i += 1
            continue
        buf = []
        while i < len(lines) and lines[i].strip() and not re.match(
            r"^(#{1,6}\s|>|```|\s*[-*]\s|\s*\d+\.\s|\s*---\s*$)", lines[i]
        ):
            buf.append(lines[i])
            i += 1
        out.append("<p>" + inline(" ".join(buf)) + "</p>")

    return "\n".join(out)


def main():
    md = open(sys.argv[1], encoding="utf-8").read()
    css = open(sys.argv[2], encoding="utf-8").read()
    body = render(md)

    page = f"""<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8">
<title>README 预览</title>
<style>
{css}
/* 强制浅色：本机是深色系统，不锁的话渲染出来是黑底，看不到访客的真实观感 */
:root {{ color-scheme: light; }}
body {{ margin: 0; background: #ffffff; }}
.markdown-body {{
  box-sizing: border-box; min-width: 200px; max-width: 980px;
  margin: 0 auto; padding: 45px;
}}
.markdown-body img {{ background: transparent; }}
/* GitHub 上徽章是并排的，居中块里也让它横排 */
.markdown-body div[align="center"] img {{ display: inline-block; }}
</style></head>
<body><article class="markdown-body">
{body}
</article></body></html>"""
    open(sys.argv[3], "w", encoding="utf-8").write(page)
    print(f"已生成 {sys.argv[3]}（{len(page)} 字符）")


if __name__ == "__main__":
    main()
