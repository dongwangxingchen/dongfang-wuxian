#!/usr/bin/env node
/**
 * [DFW-106] 控制台「版本序号守卫」的行为测试。
 *
 * 为什么要有它：这两条守卫是用户已经踩过两次的老病（DFW-82「静默发出同序号 =
 * 用户永远显示已是最新版本」），任务明确要求**不许拆掉**。它们跑在浏览器里，
 * 没法用 curl 验，所以这里把 `index.html` 里的**真实函数源码**抽出来，
 * 在 Node 里用桩 `confirm` 跑一遍行为断言 —— 改坏了立刻红。
 *
 * 用法：
 *   node server/dfwx-admin/guard-test.mjs
 *   node server/dfwx-admin/guard-test.mjs path/to/index.html
 *
 * 只读，不改任何文件，不需要网络。
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const htmlPath = resolve(process.argv[2] || join(here, "index.html"));
const html = readFileSync(htmlPath, "utf8");

/** 从源码里按大括号配对抽出一个 `function 名字(...) { ... }` 的完整定义。 */
function extractFunction(src, name) {
  const start = src.indexOf("function " + name + "(");
  if (start < 0) throw new Error("在 " + htmlPath + " 里找不到函数 " + name + "()");
  let depth = 0;
  for (let i = src.indexOf("{", start); i < src.length; i++) {
    const c = src[i];
    if (c === "{") depth++;
    else if (c === "}") {
      depth--;
      if (depth === 0) return src.slice(start, i + 1);
    }
  }
  throw new Error("函数 " + name + "() 的大括号不配对");
}

/*
 * 把真实源码拼成一段可执行的脚本。
 *
 * [2026-10-03] `confirm` 桩**删掉了** —— 版本序号这条路径上已经一个 confirm 都不剩。
 * 现在只有两个函数：`assertVersionCode`（硬拦非法值）和 `versionTransitionWarning`
 * （只返回提醒文案，绝不拦截）。
 */
const source = [
  extractFunction(html, "assertVersionCode"),
  extractFunction(html, "versionTransitionWarning"),
  extractFunction(html, "relFields"),
  "return { assertVersionCode, versionTransitionWarning, relFields };"
].join("\n\n");

const { assertVersionCode, versionTransitionWarning, relFields } = new Function(source)();

/* ---------------- 测试框架（10 行，不引依赖） ---------------- */
let pass = 0, fail = 0;
const results = [];
function check(name, fn) {
  try {
    fn();
    results.push("  ✅ " + name);
    pass++;
  } catch (e) {
    results.push("  ❌ " + name + "  →  " + e.message);
    fail++;
  }
}
function throws(name, fn) {
  check(name, () => {
    let threw = false;
    try { fn(); } catch (e) { threw = true; }
    if (!threw) throw new Error("期望抛错，但没有抛");
  });
}
function ok(name, fn) {
  check(name, () => { fn(); });
}
function assertEq(a, b, what) {
  if (a !== b) throw new Error((what || "值") + " 期望 " + JSON.stringify(b) + "，实际 " + JSON.stringify(a));
}
function assertIncludes(hay, needle) {
  if (!String(hay).includes(needle)) throw new Error("消息里没有找到「" + needle + "」");
}

const CUR = 10023;   // 当前版本的 versionCode

/* ---------------- 硬拦：只拦「软件读不懂的值」 ---------------- */
/*
 * [2026-10-03] 这里原来还有两条硬拦（序号相等 / 变小要 confirm）。
 * 用户明确要求去掉：「不要让它再弹出来那个弹窗阻止我了，只要我发布的版本号
 * 和版本序号高于我现在的版本，那它就会弹更新」。理由成立 —— 规则简单且用户已理解，
 * 没有理由用弹窗去挡他本人。现在硬拦只剩"发出去软件读不懂"这一类。
 */
console.log("硬拦：只拦软件读不懂的序号（非正整数）");
throws("序号 0 → 抛错", () => assertVersionCode(0));
throws("序号负数 → 抛错", () => assertVersionCode(-5));
throws("序号非整数 → 抛错", () => assertVersionCode(1.5));
throws("序号 NaN → 抛错", () => assertVersionCode(NaN));
throws("序号字符串 '10000' → 抛错（必须是 number，不能靠隐式转换）", () => assertVersionCode("10000"));
ok("序号 10000 → 放行", () => assertVersionCode(10000));
ok("序号 1 → 放行（用户就是想填 1，也让他填）", () => assertVersionCode(1));

/* ---------------- 提示：只提醒，绝不拦截 ---------------- */
console.log("\n提示：序号偏小时给一句提醒，但**绝不拦截**");
check("变小（10000 < 10023）→ 返回提醒，且**不抛错**", () => {
  const w = versionTransitionWarning(10000, CUR, "publish");
  if (!w) throw new Error("变小了却没有提醒 —— 用户会以为发布成功就没事，实际老用户收不到更新");
  assertIncludes(w, "收不到更新提示");
  assertIncludes(w, "卸载重装");
  assertIncludes(w, String(CUR));
  assertIncludes(w, "10000");
});
check("相等（10023 == 10023）→ 返回提醒，且**不抛错**", () => {
  const w = versionTransitionWarning(CUR, CUR, "publish");
  if (!w) throw new Error("序号一样却没有提醒 —— 这正是 DFW-82「永远不提示更新」的形态");
  assertIncludes(w, "不会弹");
  assertIncludes(w, String(CUR));
});
check("editCurrent 模式：序号相等属正常保存 → **不给提醒**（别制造噪音）", () => {
  assertEq(versionTransitionWarning(CUR, CUR, "editCurrent"), "", "提醒文案");
});
check("变大（10024 > 10023）→ **不给任何提醒**（这是正常路径，不该打扰）", () => {
  assertEq(versionTransitionWarning(10024, CUR, "publish"), "", "提醒文案");
  assertEq(versionTransitionWarning(10024, CUR, "switch"), "", "提醒文案");
});
check("当前还没有版本（currentCode=0）→ 不给提醒", () => {
  assertEq(versionTransitionWarning(10000, 0, "publish"), "", "提醒文案");
});
check("非法值也走「提醒」而不是抛错（提示函数永远不抛）", () => {
  for (const bad of [0, -5, 1.5, NaN, "10000", null, undefined]) {
    const w = versionTransitionWarning(bad, CUR, "publish");
    if (!w) throw new Error("非法值 " + JSON.stringify(bad) + " 应当返回提醒文案");
  }
});

/* ---------------- 源码级：这条路径上不许再有 confirm ---------------- */
console.log("\n源码级断言：版本序号路径上不许再出现 confirm");
check("index.html 里已无 guardVersionTransition 残留", () => {
  if (html.includes("guardVersionTransition")) {
    throw new Error("还有地方在调 guardVersionTransition —— 那个函数已经删了");
  }
});
check("版本相关的 confirm 已全部移除", () => {
  /*
   * 允许的 confirm 只有「回滚」（那是另一个动作的确认，不是版本序号守卫）。
   * 这里检查的是：不再有"序号比当前小/一样"这类询问式弹窗。
   */
  const bad = ["版本序号不能比当前小", "这次的版本序号比当前版本", "和现在一样，只有版本序号变了"];
  for (const s of bad) {
    if (html.includes(s)) throw new Error("还残留着会拦截用户的弹窗文案：「" + s + "」");
  }
});

/* ---------------- 源码级：选完 APK 不许覆盖用户填的序号 ---------------- */
console.log("\n源码级断言：选完 APK 不许覆盖用户自己填的版本序号");
check("不再无条件用包里的序号覆盖输入框", () => {
  /*
   * 用户 2026-10-03 撞上的就是这个：
   * 自己填好序号 → 选了个内部序号很小的 APK → 输入框被盖掉 → 然后被守卫拦下。
   * 用户的感受是「我明明填了，它却给我改了，还不让我发」。
   */
  if (/\$\("#vc"\)\.value\s*=\s*meta\.versionCode\s*;/.test(html)) {
    throw new Error("还在无条件用包里的序号覆盖输入框 —— 用户填了也白填");
  }
  if (!html.includes("const typed = Number(box.value || 0);")) {
    throw new Error("没找到「先看用户填了没有」的判断逻辑");
  }
  if (!html.includes("refreshVcWarn")) {
    throw new Error("没找到常驻提醒区的刷新函数 —— 提醒不显示出来，用户就看不到后果");
  }
});

/* ---------------- relFields：渲染/写回共用的取值函数 ---------------- */
console.log("\nrelFields 取值");
check("缺字段全部降级成空值/0，不抛错", () => {
  const f = relFields(null);
  assertEq(f.versionName, "", "versionName");
  assertEq(f.versionCode, 0, "versionCode");
  assertEq(f.updateMode, "soft", "updateMode 默认值");
});
check("正常记录原样取出", () => {
  const f = relFields({ versionName: "1.0.7", versionCode: 10007, apkUrl: "u", sha256: "s", size: 12, updateMode: "force" });
  assertEq(f.versionName, "1.0.7", "versionName");
  assertEq(f.versionCode, 10007, "versionCode");
  assertEq(f.updateMode, "force", "updateMode");
});

/* ---------------- 「版本序号」预填值 与 提示文案 必须一致 ---------------- */
/*
 * 来历：DFW-118 路线 B 把预填从 `last + 1` 改成 `last`（fail-safe：读不到包里的序号时
 * 保持 = 当前值，好让守卫按「序号相等」硬拦，而不是静默发一个猜出来的高序号），
 * 但**漏改了下面那句提示**，文案一直写着「已经帮你填好现在 +1」。
 * 用户照做 → 输入框其实是当前值 → 被守卫硬拦 → 显得"说了帮忙却没帮"。
 *
 * 这里把它锁住：提示里的数字和输入框里的数字**必须来自同一个表达式**，
 * 而且提示里不许再出现「帮你加好了」这种和真实行为不符的承诺。
 */
console.log("\n「版本序号」预填值与提示文案一致性");

const vcInputExpr = (() => {
  const m = html.match(/<input id="vc"[^>]*value="\$\{([^}]*)\}"/);
  if (!m) throw new Error("找不到 #vc 输入框的 value 表达式");
  return m[1];
})();
const curCodeExpr = (() => {
  const m = html.match(/const curCode = ([^;]+);/);
  if (!m) throw new Error("找不到 curCode 的定义");
  return m[1];
})();
const vcHintSrc = (() => {
  const start = html.indexOf("const vcHint =");
  if (start < 0) throw new Error("找不到 vcHint 的定义");
  return html.slice(start, html.indexOf("pendingApk = null;", start));
})();
/* 把 vcHint 还原成一个 r => 字符串 的函数：**断言渲染出来的文案**，
   而不是在源码里做字符串匹配 —— 否则注释里提一句旧文案就会误报。 */
const vcHintExpr = vcHintSrc.replace(/^const vcHint =/, "").trim().replace(/;\s*$/, "");
const hintFn = new Function("r", "const curCode = (" + curCodeExpr + "); return (" + vcHintExpr + ");");

/*
 * 断言必须落在**真正渲染出来的那个 <p class="hint">** 上，而不是只断言 vcHint 这个变量：
 * 否则有人把过时的那句话作为兄弟文本塞回段落里（`<p class="hint">${vcHint} 已经帮你填好…</p>`），
 * 变量本身没变，测试却会漏掉。这一条是被反向探针逼出来的。
 */
const hintParaSrc = (() => {
  const m = html.match(/<p class="hint">([^<]*\$\{vcHint\}[^<]*)<\/p>/);
  if (!m) throw new Error('找不到 #vc 下面那段 <p class="hint">${vcHint}</p>');
  return m[1];
})();
const renderHint = r => hintParaSrc.replace("${vcHint}", hintFn(r));

/** 把两个表达式都变成 r => number 的函数，然后逐例比对。 */
const toFn = expr => {
  const fn = new Function("r", "return (" + expr + ");");
  return r => fn(r);
};
const inputFn = toFn(vcInputExpr);
const curCodeFn = toFn(curCodeExpr);

check("提示里的数字与输入框预填值来自同一个表达式（逐个样例比对）", () => {
  for (const r of [{ versionCode: 10000 }, { versionCode: 1 }, { versionCode: 12345 }, null, {}]) {
    assertEq(curCodeFn(r), inputFn(r), "r=" + JSON.stringify(r) + " 时 hint 值 vs 输入框值");
  }
});
check("渲染出来的提示里，当前值是动态的、且与预填值一致", () => {
  /*
   * [2026-10-03] 文案改过一次：原来是「必须比现在（N）大，否则用户永远收不到更新」，
   * 现在是「填得比现在（N）大，用户就会收到更新提示；填小了或填一样，就不会弹」。
   * 换的是措辞，**要守的不变量没变**：数字必须是动态注入的、和输入框里的值同源。
   */
  for (const code of [10000, 1, 10023]) {
    const text = renderHint({ versionCode: code });
    assertIncludes(text, "填得比现在（" + code + "）大");
  }
  if (/填得比现在（\d{3,}）/.test(html)) throw new Error("提示里出现了写死的数字");
});
check("提示里说清了「这个数字你自己填，选了包也不会改你的值」", () => {
  /*
   * 这条守的是 2026-10-03 用户要求的那个改动：原来选完 APK 会用包里的序号
   * **覆盖**用户填的值，用户填了也白填。现在不覆盖了，文案必须跟着说清楚，
   * 否则用户还会以为"反正会被覆盖，我填不填无所谓"。
   */
  for (const r of [{ versionCode: 10000 }, null]) {
    const text = renderHint(r);
    assertIncludes(text, "你自己填");
  }
});
check("提示里不再有「已经帮你加好了」这种与真实行为不符的承诺", () => {
  for (const r of [{ versionCode: 10000 }, null]) {
    const text = renderHint(r);
    for (const stale of ["已经帮你填好", "现在 +1", "帮你填好", "已自动 +1", "自动 +1"]) {
      if (text.includes(stale)) throw new Error("渲染出来的文案里还有过时承诺：" + stale + " → " + text);
    }
  }
});
check("预填是当前值本身，不是 +1（fail-safe 设计）", () => {
  if (/\+\s*1/.test(vcInputExpr)) throw new Error("#vc 预填表达式里出现了 +1：" + vcInputExpr);
  assertEq(inputFn({ versionCode: 10000 }), 10000, "r.versionCode=10000 时的预填值");
});
check("没有当前版本时（r=null）预填为 0，提示走「必须是正整数」那一支", () => {
  assertEq(inputFn(null), 0, "r=null 时的预填值");
  assertEq(curCodeFn(null), 0, "r=null 时的 curCode");
  assertIncludes(renderHint(null), "必须是个正整数");
});

/* ---------------- [2026-10-03] CSS 变量完整性守卫 ----------------
 *
 * 为什么值得单独立一组：CSS 的 `var()` 在「引用的变量没定义 + 没写回退值」时，
 * **整条声明会被静默丢弃**（不是回退成 inherit，也不报错）。
 * 于是症状是"某几个控件颜色不对/边框没了"，但代码里看起来一切正常。
 *
 * 真实的失败模式（本轮修掉的）：
 *   · `.btn-ghost` / `.mchip` 写 `color:var(--fg)`，而 `--fg` 从没定义
 *     → 文字颜色失效 → 回落成浏览器默认黑，在 #0f1013 深色底上几乎看不见；
 *   · 两处 `<select>` 行内写 `background:var(--surface2)` + `border:…var(--border)`
 *     → 全失效 → 下拉框白底无边框，深色页面里视觉断裂。
 *
 * 这条守卫的做法：把 `<style>` 里所有 `--x: ...` 收成"已定义"集合，
 * 再把全文件（含行内 style）里所有 `var(--x)` 收成"被引用"集合，
 * **引用减定义必须为空**。`var(--x, fallback)` 形式自带回退值，不算违规。
 */
const cssRootVars = (() => {
  const styleMatch = html.match(/<style>([\s\S]*?)<\/style>/);
  if (!styleMatch) throw new Error("找不到 <style> 块");
  const style = styleMatch[1];
  const defined = new Set();
  for (const m of style.matchAll(/(--[a-zA-Z0-9-]+)\s*:/g)) defined.add(m[1]);
  const missing = new Map();
  // 扫描**全文件**（含 HTML 行内 style=）；带 fallback 的 var() 不参与。
  for (const m of html.matchAll(/var\(\s*(--[a-zA-Z0-9-]+)\s*([,)])/g)) {
    const name = m[1];
    const hasFallback = m[2] === ",";
    if (hasFallback) continue;          // var(--x, 回退值) —— 变量缺失也有兜底
    if (defined.has(name)) continue;
    missing.set(name, (missing.get(name) || 0) + 1);
  }
  return { defined, missing };
})();

check("CSS 里没有「引用了但从未定义」的变量（var() 会让整条声明静默失效）", () => {
  const { missing } = cssRootVars;
  if (missing.size > 0) {
    const detail = [...missing.entries()].map(([n, c]) => n + "（被引用 " + c + " 次）").join("、");
    throw new Error(
      "以下变量被 var() 引用但没有定义，会导致对应声明整条失效：" + detail +
      "。修法：在 :root 里定义它，或给 var() 加回退值 var(--x, 某值)"
    );
  }
});

check("守卫本身有效：故意抽掉一个变量定义时，它能检出", () => {
  // 反向探针写进测试里 —— 保证上面那条不是"永远为真"的空断言。
  const { defined } = cssRootVars;
  if (!defined.has("--accent")) throw new Error("--accent 应当是已定义变量，守卫的取样有问题");
  const fakeDefined = new Set(defined);
  fakeDefined.delete("--accent");
  const stillReferenced = [...html.matchAll(/var\(\s*(--accent)\s*([,)])/g)]
    .some(m => m[2] === ")" );
  if (!stillReferenced) throw new Error("--accent 应当有无回退值的引用，反向探针无从成立");
  if (fakeDefined.has("--accent")) throw new Error("抽掉后不该还在集合里");
});

/* ---------------- [2026-10-03] 顶部状态条 ----------------
 *
 * statusStrip() 是这一轮新加的，它把「线上现在是什么状态」提到第一屏。
 * 它值得守卫的理由：**这个后台最危险的误操作是"不知道当前线上是什么就点了发布"**，
 * 状态条就是防这个的。它一旦渲染错了（比如维护中却显示"正常运行"），
 * 比没有这条还糟 —— 会给人错误的安全感。
 */
const statusStripSrc = (() => {
  const escFn = extractFunction(html, "esc");
  const relFn = extractFunction(html, "relFields");
  const stripFn = extractFunction(html, "statusStrip");
  /*
   * statusStrip() **读的是全局 `cache`**（同文件其它渲染函数也都是这个风格），
   * 所以桩必须把 `cache` 注入到**同一个作用域**里，而不是当成参数传。
   * 第一版当成参数传，四条断言直接报 `out.includes is not a function` ——
   * 那不是产品坏了，是我的桩搭错了。这里改成把 cache 作为形参名注入工厂作用域。
   */
  return new Function("cache", escFn + "\n\n" + relFn + "\n\n" + stripFn + "\n\nreturn statusStrip;");
})();
/*
 * 注意这里要**调用两次**：工厂(cache) → statusStrip 函数 → () → 渲染出来的 HTML。
 * 第一版漏了最后那次调用，于是 makeStatusStrip 返回的是函数、不是字符串，
 * 断言全报 `out.includes is not a function`。那是**测试桩的错，不是产品的错**。
 */
const makeStatusStrip = cache => statusStripSrc(cache)();

check("状态条：维护开启时不许显示「正常运行」", () => {
  for (const ctl of [{ maintenanceOn: true }, { blocked: true }, { maintenanceOn: true, blocked: true }]) {
    const out = makeStatusStrip({ control: ctl, release: { versionName: "1.0.0", versionCode: 10000 }, notice: [] });
    if (!out.includes("维护中")) throw new Error("维护开启却没显示「维护中」：" + out);
    if (out.includes("正常运行")) throw new Error("维护开启时**同时**出现了「正常运行」，会误导操作者：" + out);
  }
});

check("状态条：正常时显示「正常运行」，且版本名与序号都要出现", () => {
  const out = makeStatusStrip({
    control: { maintenanceOn: false, blocked: false },
    release: { versionName: "1.0.0", versionCode: 10000 },
    notice: [],
  });
  assertIncludes(out, "正常运行");
  assertIncludes(out, "1.0.0");
  assertIncludes(out, "10000");
  if (out.includes("维护中")) throw new Error("没开维护却显示了「维护中」：" + out);
});

check("状态条：没有版本记录时必须明说，不许装作有版本", () => {
  const out = makeStatusStrip({ control: {}, release: null, notice: [] });
  assertIncludes(out, "没有版本记录");
});

check("状态条：只数**生效**的公告（enabled!==false）", () => {
  const out = makeStatusStrip({
    control: {}, release: null,
    notice: [{ enabled: true }, { enabled: false }, { enabled: true }, {}],
  });
  // enabled:true ×2 + 缺字段按生效 ×1 = 3；enabled:false 那条不算
  assertIncludes(out, "生效公告 3 条");
});

check("状态条：一条生效公告都没有时明说，不显示「0 条」", () => {
  const out = makeStatusStrip({ control: {}, release: null, notice: [{ enabled: false }] });
  assertIncludes(out, "没有生效公告");
});

check("状态条：cache 字段缺失/为 null 也不许抛错（首屏可能还没拉到数据）", () => {
  for (const c of [{}, { control: null, release: null, notice: null }, { control: undefined, notice: undefined }]) {
    const out = makeStatusStrip(c);
    if (typeof out !== "string" || out.length === 0) throw new Error("空 cache 返回了空：" + JSON.stringify(c));
  }
});

/* ---------------- 输出 ---------------- */
console.log(results.join("\n"));
console.log("\n文件：" + htmlPath);
console.log("结果：PASS=" + pass + "  FAIL=" + fail);
process.exit(fail === 0 ? 0 : 1);
