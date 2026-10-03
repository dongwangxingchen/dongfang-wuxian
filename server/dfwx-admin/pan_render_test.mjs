/**
 * dfwx-admin / pan_render_test.mjs —— 「网盘」页渲染逻辑的单元测试
 *
 * 为什么要有这个：控制台是一整个 index.html 里的内联脚本，没有模块边界，
 * 平时只能靠"打开浏览器点一点"来验证。但渲染逻辑里最危险的东西 ——
 * HTML 转义、APK 判定、"线上正在用"判定 —— 恰恰是可以用纯函数测的。
 * 这里把那几个函数从 index.html 里抠出来，喂假数据，断言输出。
 *
 * 不测的部分（明确写出来，不假装覆盖了）：DOM 事件绑定、真实网络请求。
 * 那两块靠 server_test.py（接口）+ e2e_test.py（真实链路）覆盖。
 *
 * 跑法：node pan_render_test.mjs
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const HERE = dirname(fileURLToPath(import.meta.url));
const HTML = readFileSync(join(HERE, 'index.html'), 'utf8');

/** 从源码里按大括号配对抠出一个 `function name(...) {...}` 的完整文本。 */
function extractFunction(source, name) {
  const start = source.indexOf(`function ${name}(`);
  if (start < 0) throw new Error(`找不到函数 ${name}`);
  let depth = 0;
  let i = source.indexOf('{', start);
  const from = i;
  for (; i < source.length; i += 1) {
    if (source[i] === '{') depth += 1;
    else if (source[i] === '}') {
      depth -= 1;
      if (depth === 0) return source.slice(start, i + 1);
    }
  }
  throw new Error(`函数 ${name} 的大括号不配对`);
}

/** 抠出一个 `const NAME = ...;` 的单行常量。 */
function extractConst(source, name) {
  const m = new RegExp(`^const ${name} = (.+);$`, 'm').exec(source);
  if (!m) throw new Error(`找不到常量 ${name}`);
  return `const ${name} = ${m[1]};`;
}

/* 把要测的函数和它们依赖的常量拼成一小段可求值的代码。 */
const pieces = [
  extractConst(HTML, 'APK_ORIGIN'),
  'let cache = {};',
  extractFunction(HTML, 'esc'),
  extractFunction(HTML, 'mbText'),
  extractFunction(HTML, 'fmtTime'),
  extractFunction(HTML, 'fmtUnix'),
  extractFunction(HTML, 'isLiveApk'),
  extractFunction(HTML, 'panUploadWarning'),
  extractFunction(HTML, 'panFileHtml'),
  'return { panFileHtml, fmtUnix, isLiveApk, panUploadWarning, esc, setCache: v => { cache = v; } };',
];
const api = new Function(pieces.join('\n'))();

let failures = 0;
function check(label, ok, detail = '') {
  console.log(`  ${ok ? '✅' : '❌'} ${label}${ok || !detail ? '' : `  —— ${detail}`}`);
  if (!ok) failures += 1;
}

const APK = {
  name: 'dongfang-wuxian-v1.0.0.apk', size: 36840658, mtime: 1790999945,
  sha256: 'e24feb77e14a0da639cdc727bf8cbbb6f8104bb6ae4704d482c00877dd732730',
  versionCode: 10000, versionName: '1.0.0', uploadedAt: 1790999945,
};

console.log('【1】APK 条目的渲染');
{
  api.setCache({});
  const html = api.panFileHtml(APK);
  check('显示文件名', html.includes('dongfang-wuxian-v1.0.0.apk'));
  check('显示人话大小（35.13 MB 而不是一串数字）', html.includes('35.13 MB'), html.match(/[\d.]+ MB/));
  check('显示完整 sha256（用户要能核对）', html.includes(APK.sha256));
  check('显示包内版本名与序号', html.includes('1.0.0') && html.includes('10000'));
  check('APK 才有「填进版本记录」按钮', html.includes('pan-fill'));
  check('带 data-n 供事件委托取文件名', html.includes('data-n="dongfang-wuxian-v1.0.0.apk"'));
}

console.log();
console.log('【2】非 APK 条目');
{
  api.setCache({});
  const html = api.panFileHtml({ name: 'readme.pdf', size: 2048, mtime: 1790999945, sha256: 'a'.repeat(64) });
  check('非 APK 不给「填进版本记录」（填进去会把更新推给错的文件）', !html.includes('pan-fill'));
  check('非 APK 的版本列显示破折号', html.includes('—'));
  check('仍然可以复制链接、可以删除',
    html.includes('pan-copy') && html.includes('pan-del'));
}

console.log();
console.log('【3】没记过哈希的老文件');
{
  api.setCache({});
  const html = api.panFileHtml({ name: 'old.apk', size: 1024, mtime: 1790999945, sha256: null });
  check('提示「未记录」而不是显示空白', html.includes('未记录'));
  check('告诉用户点按钮会现算', html.includes('现算'));
  check('读不出版本号时如实说「需手填」', html.includes('需手填'), html.slice(0, 200));
}

console.log();
console.log('【4】XSS：文件名里的尖括号必须被转义');
{
  api.setCache({});
  const evil = api.panFileHtml({
    name: '<img src=x onerror=alert(1)>.apk', size: 1, mtime: 1, sha256: null,
  });
  check('文件名里的 < > 被转义', !evil.includes('<img src=x'), evil.slice(0, 160));
  check('转义成了实体', evil.includes('&lt;img'));
}

console.log();
console.log('【5】「线上正在用」标记');
{
  api.setCache({ release: { apkUrl: 'https://39.106.33.135/apk/dongfang-wuxian-v1.0.0.apk' } });
  check('当前版本指向的包被标出来', api.isLiveApk('dongfang-wuxian-v1.0.0.apk'));
  check('别的包不会被标', !api.isLiveApk('dongfang-wuxian-v1.0.1.apk'));
  const html = api.panFileHtml(APK);
  check('标记出现在 HTML 里', html.includes('线上正在用'));

  api.setCache({ release: { apkUrl: 'https://39.106.33.135/apk/dongfang-wuxian-v1.0.0.apk?x=1' } });
  check('带查询串的地址也能认出来', api.isLiveApk('dongfang-wuxian-v1.0.0.apk'));

  api.setCache({});
  check('没有版本记录时不误标', !api.isLiveApk('dongfang-wuxian-v1.0.0.apk'));
}

console.log();
console.log('【6】时间格式化');
{
  const text = api.fmtUnix(1790999945);
  check('Unix 秒能格式化成可读时间', /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}$/.test(text), text);
  check('0 / 空值返回空串而不是 Invalid Date', api.fmtUnix(0) === '' && api.fmtUnix(null) === '');
}

console.log();
console.log('【7】上传重名防呆（服务端是 os.replace，同名是静默覆盖）');
{
  const existing = [APK, { name: 'other.zip', size: 1024 }];

  api.setCache({ release: { apkUrl: 'https://39.106.33.135/apk/dongfang-wuxian-v1.0.0.apk' } });
  const liveWarn = api.panUploadWarning('dongfang-wuxian-v1.0.0.apk', existing);
  check('覆盖线上正在用的包 → 必须警告', typeof liveWarn === 'string' && liveWarn.length > 0);
  check('警告里说清后果（校验值对不上、装不上）',
    /校验值|校验/.test(liveWarn) && /装不上|失败/.test(liveWarn), liveWarn);
  check('警告里给出正确做法（换文件名）', /换一个新文件名|文件名/.test(liveWarn));

  const plainWarn = api.panUploadWarning('other.zip', existing);
  check('覆盖普通文件 → 也警告，但不吓唬人', typeof plainWarn === 'string' && !/线上正在用/.test(plainWarn), plainWarn);

  check('全新文件名 → 不警告，直接传', api.panUploadWarning('brand-new.apk', existing) === null);
  check('列表为空 → 不警告', api.panUploadWarning('anything.apk', []) === null);
  check('列表是 undefined 也不炸', api.panUploadWarning('anything.apk', undefined) === null);
}

console.log();
console.log(failures === 0 ? '✅ 全部通过' : `❌ ${failures} 项未通过`);
process.exit(failures === 0 ? 0 : 1);
