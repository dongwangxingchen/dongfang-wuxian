package cc.nkbr.lanzouplus;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Typeface;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * v1.21.4 全站字体切换（设置·外观）：系统字体 / 思源黑体。
 * 思源模式 = 双字体链（API 28+ Typeface.CustomFallbackBuilder）：
 *   Space Grotesk 打头（拉丁/数字——Claude UI 字体 Styrene B 的开源最近似平替，OFL 可随 APK 分发）
 *   → Noto Sans SC 子集（中文）→ 系统字体兜底生僻字/dingbat；API 26/27 无该 API，纯思源。
 * 两份字体均为 tools/build_font_subset.py 重做子集：
 *   行高归一 1.20×（旧版 1.448 =「切换后比例变大」根因）、wght 默认实例 400（旧版停在 100，
 *   未显式设字重的消费方渲染极细）、字符集扩容含 ¥（支付页中西混拼根因）。
 * 子集约 3.7MB（GB2312 一级 3755 字+符号区+源码全扫），wght 轴 100-900 保留，按字重实例化，
 * 层级靠字重不靠字号（wear-ui-system §6）。字重档：normal 440 / medium 540 / bold 700。
 * 决策单一来源：View 层 text() 工厂与全部显式 setTypeface 都经此取 Typeface；
 * AI 内嵌页（Compose）读同一偏好键（ui_prefs_v1/font_choice）自行加载同两份 assets 字体，见 AiPageHost.kt。
 * 任何加载失败回退系统字体（同源 fallback，视觉无割裂），绝不让字体问题崩 app。
 * 选择存 ui_prefs_v1（与 ThemeEngine 同文件，外观偏好集中）。
 */
final class AppFonts {
    static final int CHOICE_SYSTEM = 0, CHOICE_NOTO = 1;
    private static final String PREF_FILE = "ui_prefs_v1";
    private static final String KEY_FONT = "font_choice";
    private static final String ASSET = "fonts/NotoSansSC-VF.ttf";
    private static final String ASSET_LATIN = "fonts/SpaceGrotesk-VF.ttf";
    private static volatile int choiceCache = -1;
    private static volatile Typeface notoLight, notoNormal, notoMedium, notoBold;
    private AppFonts() {}

    static int choice(Context c) {
        int hit = choiceCache;
        if (hit >= 0) return hit;
        int v;
        try { v = c.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).getInt(KEY_FONT, CHOICE_SYSTEM); }
        catch (Throwable t) { v = CHOICE_SYSTEM; }
        if (v != CHOICE_SYSTEM && v != CHOICE_NOTO) v = CHOICE_SYSTEM;
        choiceCache = v;
        return v;
    }

    static void setChoice(Context c, int v) {
        if (v != CHOICE_SYSTEM && v != CHOICE_NOTO) v = CHOICE_SYSTEM;
        try { c.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit().putInt(KEY_FONT, v).apply(); } catch (Throwable ignored) {}
        choiceCache = v;
    }

    /** 系统模式：Typeface 与 v1.21.2 行为逐点一致（DEFAULT / sans-serif-medium / DEFAULT+BOLD 等）。
     *  create 结果做实例缓存：既省重复 native 调用，也让 applyDeep 能用 == 识别「已接管」的字体身份。 */
    private static Typeface sMediumSys, sBoldSys, sLightSys;
    static Typeface lightSys() { Typeface t = sLightSys; if (t == null) sLightSys = t = Typeface.create("sans-serif-light", Typeface.NORMAL); return t; }
    static Typeface normalSys() { return Typeface.DEFAULT; }
    static Typeface mediumSys() { Typeface t = sMediumSys; if (t == null) sMediumSys = t = Typeface.create("sans-serif-medium", Typeface.NORMAL); return t; }
    static Typeface boldSys() { Typeface t = sBoldSys; if (t == null) sBoldSys = t = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); return t; }

    /** 视图树兜底（dialog 的 framework 控件等非工厂产物）：按「当前字体身份」映射到对应档；
     *  等宽（MONOSPACE）与已是思源实例的不动。null/DEFAULT→normal，DEFAULT_BOLD→bold，medium/light 同理。 */
    static void applyDeep(View root, Context c) {
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0, n = g.getChildCount(); i < n; i++) applyDeep(g.getChildAt(i), c);
            return;
        }
        if (!(root instanceof TextView)) return;
        TextView tv = (TextView) root;
        Typeface cur = tv.getTypeface();
        if (cur == Typeface.MONOSPACE || cur == notoLight || cur == notoNormal || cur == notoMedium || cur == notoBold) return;
        if (cur == null || cur == normalSys()) tv.setTypeface(normal(c));
        else if (cur == Typeface.DEFAULT_BOLD || cur == boldSys()) tv.setTypeface(bold(c));
        else if (cur == mediumSys()) tv.setTypeface(medium(c));
        else if (cur == lightSys()) tv.setTypeface(light(c));
    }

    static Typeface light(Context c) { return choice(c) == CHOICE_NOTO ? instance(c, 300) : lightSys(); }
    static Typeface normal(Context c) { return choice(c) == CHOICE_NOTO ? instance(c, 440) : normalSys(); }
    static Typeface medium(Context c) { return choice(c) == CHOICE_NOTO ? instance(c, 540) : mediumSys(); }
    static Typeface bold(Context c) { return choice(c) == CHOICE_NOTO ? instance(c, 700) : boldSys(); }

    private static Typeface cached(int wght) {
        if (wght == 700) return notoBold;
        if (wght == 540) return notoMedium;
        if (wght == 440) return notoNormal;
        if (wght == 300) return notoLight;
        return null;
    }

    private static Typeface instance(Context c, int wght) {
        Typeface hit = cached(wght);
        if (hit != null) return hit;
        synchronized (AppFonts.class) {
            try {
                Typeface built = buildChained(c.getAssets(), wght);
                if (built == null) throw new IllegalStateException("Typeface build returned null");
                if (wght == 700) notoBold = built; else if (wght == 540) notoMedium = built;
                else if (wght == 440) notoNormal = built; else if (wght == 300) notoLight = built;
                return built;
            } catch (Throwable t) {
                android.util.Log.w("AppFonts", "思源黑体加载失败，回退系统字体: " + t);
                if (wght == 700) return boldSys();
                if (wght == 540) return mediumSys();
                if (wght == 440) return normalSys();
                return lightSys();
            }
        }
    }

    /** v1.21.4 字体链：Space Grotesk（拉丁/数字）→ 思源（中文）→ 系统兜底。
     *  两份子集行高同为 1.20，链内行高不因组链变化；API 26/27 无 CustomFallbackBuilder，纯思源。 */
    private static Typeface buildChained(AssetManager am, int wght) throws Throwable {
        if (Build.VERSION.SDK_INT >= 28) {
            android.graphics.fonts.Font latin = new android.graphics.fonts.Font.Builder(am, ASSET_LATIN)
                    .setFontVariationSettings("'wght' " + wght).build();
            android.graphics.fonts.Font cjk = new android.graphics.fonts.Font.Builder(am, ASSET)
                    .setFontVariationSettings("'wght' " + wght).build();
            return new Typeface.CustomFallbackBuilder(
                    new android.graphics.fonts.FontFamily.Builder(latin).build())
                    .addCustomFallback(new android.graphics.fonts.FontFamily.Builder(cjk).build())
                    .build();
        }
        return new Typeface.Builder(am, ASSET).setFontVariationSettings("'wght' " + wght).build();
    }
}
