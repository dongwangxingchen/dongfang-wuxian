/* 《东方无限》反馈页换肤（WebView 注入版）。
 *
 * 为什么**不**用类名选择器：FlowUs 是第三方 SPA，类名是构建产物（带哈希、会变），
 * 靠 `[class*="banner"]` 这种猜法迟早失效。实测第一版就是这么翻车的：
 * 把 body 背景刷成黑、文字刷成浅色，但真正承载白底的是内层容器 ——
 * 结果变成"白底浅字"，**比不换肤还糟**。
 *
 * 改成**按计算样式做明度重映射**：遍历 DOM，读每个元素真实的 background-color / color /
 * border-color，接近白的刷成深色、接近黑的刷成浅色、饱和蓝刷成品牌紫。
 * 这样与类名无关，FlowUs 改版也不会失效。
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

/* ── 提交检测 ─────────────────────────────────────────────────────────
   FlowUs 提交成功后会给出提示或清空表单。这里两种信号都认，
   一旦命中就调 Android 侧的回调，由 App 弹出**我们自己的**紫色提示
   （而不是把 FlowUs 那句英文 toast 直接甩给用户）。只触发一次。 */
function detectSubmitted() {
  if (window.__dfwxSubmitted) return;
  var body = (document.body && document.body.innerText) || "";
  var ok = /提交成功|已提交|感谢(你|您)?的反馈|提交完成|success(fully)?\s*(submitted|sent)|thank you for your (feedback|submission)/i.test(body);
  if (!ok) return;
  window.__dfwxSubmitted = true;
  try { if (window.DFWX && window.DFWX.onSubmitted) window.DFWX.onSubmitted(); } catch (e) {}
}
setInterval(detectSubmitted, 1200);

/* ── 预览模式自动放行 ────────────────────────────────────────────────
   FlowUs 把分享页默认开在 Preview mode（一条蓝色横条写着 click here to submit），
   不点它就没有提交按钮 —— 普通用户根本不知道要点。这里自动点掉。
   只在"确实存在这条横条"时才动手，找不到就算了（不猜、不乱点）。 */
function skipPreviewMode() {
  var all = document.querySelectorAll('div,button,a,span');
  for (var i = 0; i < all.length; i++) {
    var el = all[i];
    var t = (el.textContent || "").trim();
    if (t.length > 60) continue;
    if (!/preview mode|click here to submit/i.test(t)) continue;
    var r = el.getBoundingClientRect();
    if (r.width <= 0 || r.height <= 0) continue;
    try { el.click(); return true; } catch (e) { return false; }
  }
  return false;
}
setTimeout(skipPreviewMode, 1500);
setTimeout(skipPreviewMode, 4000);
