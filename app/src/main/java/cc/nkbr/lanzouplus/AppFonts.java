package cc.nkbr.lanzouplus;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * v1.21.3 全站字体切换（设置·外观）：系统字体 / 思源黑体。
 * 思源黑体 = Noto Sans SC 可变字体子集（assets/fonts/NotoSansSC-VF.ttf，3500 常用字+源码全字符，约 1.9MB，
 * wght 轴 100-900 全保留），用 Typeface.Builder 按 wght 实例化，层级靠字重不靠字号（wear-ui-system §6）。
 * 字重档：normal 440 / medium 540 / bold 700（比系统 400/500/700 更细腻拉开层级）。
 * 决策单一来源：View 层 text() 工厂与全部显式 setTypeface 都经此取 Typeface；
 * AI 内嵌页（Compose）读同一偏好键（ui_prefs_v1/font_choice）自行加载同一份 assets 字体，见 AiPageHost.kt。
 * 任何加载失败回退系统字体（同源 fallback，视觉无割裂），绝不让字体问题崩 app。
 * 选择存 ui_prefs_v1（与 ThemeEngine 同文件，外观偏好集中）。
 */
final class AppFonts {
    static final int CHOICE_SYSTEM = 0, CHOICE_NOTO = 1;
    private static final String PREF_FILE = "ui_prefs_v1";
    private static final String KEY_FONT = "font_choice";
    private static final String ASSET = "fonts/NotoSansSC-VF.ttf";
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
                Typeface built = new Typeface.Builder(c.getAssets(), ASSET)
                        .setFontVariationSettings("'wght' " + wght).build();
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
}
