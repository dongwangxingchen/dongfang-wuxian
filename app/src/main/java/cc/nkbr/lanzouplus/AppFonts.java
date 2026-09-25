package cc.nkbr.lanzouplus;

import android.content.Context;
import android.graphics.Typeface;

/**
 * 全站 Typeface 工厂（唯一取字入口）：统一返回系统字体档位，层级靠字重不靠字号（wear-ui-system §6）。
 * 字重档：normal = DEFAULT / medium = sans-serif-medium / bold = DEFAULT+BOLD / light = sans-serif-light。
 * v1.21.5：字体切换功能（思源黑体/Space Grotesk，v1.21.3–v1.21.4）整体移除，本类收敛为系统字体工厂，
 * Context 参数保留以维持全部调用点签名不变；AI 内嵌页聊天字体的存量自定义设置由 AiPageHost 一次性迁移回 DEFAULT。
 */
final class AppFonts {
    private AppFonts() {}
    private static Typeface sMediumSys, sBoldSys, sLightSys;
    static Typeface light(Context c) { Typeface t = sLightSys; if (t == null) sLightSys = t = Typeface.create("sans-serif-light", Typeface.NORMAL); return t; }
    static Typeface normal(Context c) { return Typeface.DEFAULT; }
    static Typeface medium(Context c) { Typeface t = sMediumSys; if (t == null) sMediumSys = t = Typeface.create("sans-serif-medium", Typeface.NORMAL); return t; }
    static Typeface bold(Context c) { Typeface t = sBoldSys; if (t == null) sBoldSys = t = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); return t; }
}
