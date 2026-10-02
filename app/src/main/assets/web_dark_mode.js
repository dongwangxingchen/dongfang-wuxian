/* 《东方无限》网页暗夜模式（所有内置网页统一注入）。
 *
 * ## 用户要求（2026-10-02）
 * > 「我们软件所有网站都打开默认暗夜模式。」
 *
 * ## 为什么是"按计算样式做明度重映射"，而不是一套 CSS 类名选择器
 * 第一版用的是 `[class*="flowus"]` 那一套猜法，实测直接翻车：
 * 把 body 背景刷黑、文字刷成浅色，但**真正承载白底的是内层容器** ——
 * 结果变成"白底浅字"，比不换肤还糟（Playwright 真实渲染截图取证）。
 * 现在改成遍历 DOM 读每个元素**真实的** background-color / color / border-color，
 * 按明度重映射。与类名无关，第三方站点改版也不会失效。
 *
 * ## 三条铁律
 * 1. **只改颜色，绝不改布局** —— 改布局会让站点自己的逻辑错位。
 * 2. **绝不碰 pointer-events** —— 碰了用户就点不动页面。
 * 3. 脚本失败只是"没变暗"，页面照样能用（全部包在 try 里）。
 */

(function () {
  var BG = "#000000", SURFACE = "#14121A", TEXT = "#E8E6EF", MUTED = "#8A85A0",
      BORDER = "#3A3350", PRIMARY = "#7C4DFF", PRIMARY_TEXT = "#FFFFFF";

  function parse(c) {
    var m = /rgba?\(([^)]+)\)/.exec(c || "");
    if (!m) return null;
    var p = m[1].split(",").map(function (s) { return parseFloat(s); });
    if (p.length < 3) return null;
    var a = p.length > 3 ? p[3] : 1;
    if (a === 0) return null;
    return { r: p[0], g: p[1], b: p[2], a: a };
  }
  function lum(c) { return (0.2126 * c.r + 0.7152 * c.g + 0.0722 * c.b) / 255; }
  function sat(c) {
    var mx = Math.max(c.r, c.g, c.b), mn = Math.min(c.r, c.g, c.b);
    return mx === 0 ? 0 : (mx - mn) / mx;
  }

  function reskinElement(el) {
    if (!el || el.nodeType !== 1) return;
    if (el.dataset && el.dataset.dfwxSkinned === "1") return;
    var cs = getComputedStyle(el);
    if (cs.display === "none" || cs.visibility === "hidden") return;

    var bg = parse(cs.backgroundColor);
    if (bg && bg.a > 0.5 && lum(bg) > 0.82) {
      // 白底 → 深色。嵌套越深用越亮的"表面色"，保留层次感
      el.style.setProperty("background-color", el === document.body ? BG : SURFACE, "important");
    } else if (bg && bg.a > 0.5 && lum(bg) < 0.30 && sat(bg) > 0.45) {
      // 饱和的深蓝/紫 → 品牌紫（FlowUs 的主按钮就是这种）
      el.style.setProperty("background-color", PRIMARY, "important");
    } else if (bg && bg.a > 0.5 && lum(bg) > 0.55 && lum(bg) < 0.82) {
      // 浅灰底 → 深表面色
      el.style.setProperty("background-color", SURFACE, "important");
    }

    // 饱和的亮蓝（FlowUs 的预览横条 / 提交按钮）→ 品牌紫
    if (bg && bg.a > 0.5 && sat(bg) > 0.5 && bg.b > 150 && bg.r < 120) {
      el.style.setProperty("background-color", PRIMARY, "important");
      el.style.setProperty("color", PRIMARY_TEXT, "important");
    }

    var fg = parse(cs.color);
    if (fg && fg.a > 0.5) {
      if (lum(fg) < 0.35) el.style.setProperty("color", TEXT, "important");
      else if (lum(fg) > 0.35 && lum(fg) < 0.62) el.style.setProperty("color", MUTED, "important");
    }

    ["borderTopColor", "borderRightColor", "borderBottomColor", "borderLeftColor"].forEach(function (k) {
      var bc = parse(cs[k]);
      if (bc && bc.a > 0.5 && lum(bc) > 0.72) el.style.setProperty(
        k.replace(/[A-Z]/g, function (m) { return "-" + m.toLowerCase(); }), BORDER, "important");
    });

    if (el.tagName === "TEXTAREA" || el.tagName === "INPUT" || el.isContentEditable) {
      el.style.setProperty("background-color", SURFACE, "important");
      el.style.setProperty("color", TEXT, "important");
      el.style.setProperty("border-color", BORDER, "important");
    }
    if (el.dataset) el.dataset.dfwxSkinned = "1";
  }

  function walk(root) {
    reskinElement(root);
    var all = root.querySelectorAll ? root.querySelectorAll("*") : [];
    for (var i = 0; i < all.length; i++) reskinElement(all[i]);
  }

  walk(document.body);
  if (!window.__dfwxSkinObserver) {
    var pending = false;
    window.__dfwxSkinObserver = new MutationObserver(function () {
      if (pending) return;
      pending = true;
      setTimeout(function () { pending = false; walk(document.body); }, 120);
    });
    window.__dfwxSkinObserver.observe(document.body, { childList: true, subtree: true });
  }
  document.documentElement.style.setProperty("background-color", BG, "important");
  document.body.style.setProperty("background-color", BG, "important");
  return "skinned";
})();

/* ── 提交检测（**事件驱动，绝不轮询**）───────────────────────────────
 *
 * [DFW-97 修正] 这里原来是 `setInterval(detectSubmitted, 1200)`。
 * 那是个性能灾难：`document.body.innerText` **会强制整页同步重排（reflow）**，
 * 每 1.2 秒来一次、永远不停 —— 用户反馈的"页面又卡又慢、图片加载很慢"
 * 有它一份。SPA 越大越明显。
 *
 * 改成：只在用户**点了页面**之后查一次（提交必然伴随点击）。
 * 代价是从"点完立刻弹"变成"点完最多等 1 秒"，收益是页面不再被持续拖慢。
 * 而且加了次数上限，绝不可能变成事实上的轮询。
 */
function detectSubmitted() {
  if (window.__dfwxSubmitted) return;
  var body = (document.body && document.body.innerText) || "";
  var ok = /提交成功|已提交|感谢(你|您)?的反馈|提交完成|success(fully)?\s*(submitted|sent)|thank you for your (feedback|submission)/i.test(body);
  if (!ok) return;
  window.__dfwxSubmitted = true;
  try { if (window.DFWX && window.DFWX.onSubmitted) window.DFWX.onSubmitted(); } catch (e) {}
}

(function () {
  var tries = 0;
  document.addEventListener("click", function () {
    // 一次点击最多查 3 次（1s / 2s / 3s），查完就彻底安静
    if (window.__dfwxSubmitted || tries >= 3) return;
    tries++;
    setTimeout(detectSubmitted, 1000 * tries);
  }, true);
})();

/* 给注入方一个可断言的返回值（evaluateJavascript 能拿到），便于排查"到底跑没跑"。 */
"dfwx-dark-mode-ok";
