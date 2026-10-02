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

/* 把真实源码拼成一段可执行的脚本。`confirm` 是桩，由测试逐例设置。 */
const source = [
  extractFunction(html, "guardVersionTransition"),
  extractFunction(html, "relFields"),
  "return { guardVersionTransition, relFields };"
].join("\n\n");

let confirmAnswer = true;
let confirmMessages = [];
const factory = new Function("confirm", source);
const { guardVersionTransition, relFields } = factory(msg => {
  confirmMessages.push(msg);
  return confirmAnswer;
});

/* ---------------- 测试框架（10 行，不引依赖） ---------------- */
let pass = 0, fail = 0;
const results = [];
function check(name, fn) {
  confirmMessages = [];
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

/* ---------------- 守卫①：相等 → 硬拦 ---------------- */
console.log("守卫① versionCode 相等 → 硬拦（DFW-82 老病，不许静默发出）");
throws("publish 模式：新序号 == 当前序号 → 抛错", () => guardVersionTransition(CUR, CUR, "publish"));
throws("switch 模式（回滚）：目标序号 == 当前序号 → 抛错", () => guardVersionTransition(CUR, CUR, "switch"));
ok("editCurrent 模式：改的就是当前那条，序号不变属正常保存 → 放行", () => guardVersionTransition(CUR, CUR, "editCurrent"));
check("publish 被拦时的提示里点名「不会提示更新」", () => {
  try { guardVersionTransition(CUR, CUR, "publish"); } catch (e) {
    assertIncludes(e.message, "不会提示更新");
    return;
  }
  throw new Error("没有抛错");
});
check("相等被拦时**不弹 confirm**（是硬拦，不是询问）", () => {
  try { guardVersionTransition(CUR, CUR, "publish"); } catch (e) { /* 预期 */ }
  assertEq(confirmMessages.length, 0, "confirm 调用次数");
});

/* ---------------- 守卫②：变小 → confirm + 写清后果 ---------------- */
console.log("\n守卫② versionCode 变小 → confirm 确认，且必须写清后果");
throws("publish：变小 + 用户点「取消」→ 抛错（拦下）", () => {
  confirmAnswer = false;
  guardVersionTransition(10000, CUR, "publish");
});
ok("publish：变小 + 用户点「确定」→ 放行", () => {
  confirmAnswer = true;
  guardVersionTransition(10000, CUR, "publish");
});
throws("switch（回滚）：变小 + 取消 → 抛错", () => {
  confirmAnswer = false;
  guardVersionTransition(10000, CUR, "switch");
});
ok("switch（回滚）：变小 + 确定 → 放行", () => {
  confirmAnswer = true;
  guardVersionTransition(10000, CUR, "switch");
});
throws("editCurrent：变小 + 取消 → 抛错", () => {
  confirmAnswer = false;
  guardVersionTransition(10000, CUR, "editCurrent");
});
ok("editCurrent：变小 + 确定 → 放行", () => {
  confirmAnswer = true;
  guardVersionTransition(10000, CUR, "editCurrent");
});
check("变小时的 confirm 文案包含后果原话「必须手动卸载重装一次」", () => {
  confirmAnswer = true;
  guardVersionTransition(10000, CUR, "publish");
  assertEq(confirmMessages.length, 1, "confirm 调用次数");
  assertIncludes(confirmMessages[0], "必须手动卸载重装一次");
  assertIncludes(confirmMessages[0], "收不到更新提示");
  assertIncludes(confirmMessages[0], String(CUR));
  assertIncludes(confirmMessages[0], "10000");
});

/* ---------------- 正常路径 ---------------- */
console.log("\n正常路径：变大直接过，不打扰用户");
ok("publish：10024 > 10023 → 放行", () => { confirmAnswer = false; guardVersionTransition(10024, CUR, "publish"); });
check("变大时不弹 confirm", () => assertEq(confirmMessages.length, 0, "confirm 调用次数"));
ok("switch：10024 > 10023 → 放行", () => { confirmAnswer = false; guardVersionTransition(10024, CUR, "switch"); });
ok("当前还没有版本（currentCode=0）→ 任意正数放行", () => { confirmAnswer = false; guardVersionTransition(10000, 0, "publish"); });

/* ---------------- 非法输入 ---------------- */
console.log("\n非法输入");
throws("序号 0 → 抛错", () => guardVersionTransition(0, CUR, "publish"));
throws("序号负数 → 抛错", () => guardVersionTransition(-5, CUR, "publish"));
throws("序号非整数 → 抛错", () => guardVersionTransition(1.5, CUR, "publish"));
throws("序号 NaN → 抛错", () => guardVersionTransition(NaN, CUR, "publish"));

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
  for (const code of [10000, 1, 10023]) {
    const text = renderHint({ versionCode: code });
    assertIncludes(text, "必须比现在（" + code + "）大");
  }
  if (/必须比现在（\d{3,}）/.test(html)) throw new Error("提示里出现了写死的数字");
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

/* ---------------- 输出 ---------------- */
console.log(results.join("\n"));
console.log("\n文件：" + htmlPath);
console.log("结果：PASS=" + pass + "  FAIL=" + fail);
process.exit(fail === 0 ? 0 : 1);
