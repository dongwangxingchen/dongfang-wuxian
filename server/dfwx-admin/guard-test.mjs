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

/* ---------------- 输出 ---------------- */
console.log(results.join("\n"));
console.log("\n文件：" + htmlPath);
console.log("结果：PASS=" + pass + "  FAIL=" + fail);
process.exit(fail === 0 ? 0 : 1);
