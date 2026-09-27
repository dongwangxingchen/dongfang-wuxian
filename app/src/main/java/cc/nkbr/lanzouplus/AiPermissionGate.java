package cc.nkbr.lanzouplus;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * v1.22.0 AI 首启权限一次性引导（用户 2026-09-25 要求：进 AI 页前把软件必需权限一次授完，之后满血使用）。
 *
 * 背景：vendor（RikkaHub）各功能入口按需弹窗——麦克风（ChatInput/VoiceMode）、相机（ChatAttachmentPicker）、
 * 通知（33+）；用户痛点是被"挨个弹"打断。这里在宿主侧做一条串行引导链：进 AI 页前把未授的必需权限
 * 逐个走系统弹窗，已授的直接跳过，全部处理完（无论同意或拒绝）才放行进 AI 页，且只引导一次。
 *
 * 权限清单（与 AndroidManifest 实际声明一致）：
 *  ① 麦克风 RECORD_AUDIO —— 语音输入、语音消息
 *  ② 相机 CAMERA —— 拍照发图、图片附件
 *  ③ 通知 POST_NOTIFICATIONS（Android 13+）—— 后台回复完成提醒
 *
 * 红线：不阻断使用（拒绝也放行，功能入口再提示）；已全部授权则永不打扰；一次性标记持久化。
 */
final class AiPermissionGate {
  private static final String PREFS = "ai_permission_gate";
  private static final String KEY_DONE = "guided";

  private AiPermissionGate() {}

  /** 需要引导的权限（已授的已剔除）；空列表=无需引导 */
  static List<String> pending(Activity activity) {
    List<String> out = new ArrayList<>();
    if (!granted(activity, Manifest.permission.RECORD_AUDIO)) out.add(Manifest.permission.RECORD_AUDIO);
    if (!granted(activity, Manifest.permission.CAMERA)) out.add(Manifest.permission.CAMERA);
    if (Build.VERSION.SDK_INT >= 33 && !granted(activity, Manifest.permission.POST_NOTIFICATIONS)) out.add(Manifest.permission.POST_NOTIFICATIONS);
    return out;
  }

  static boolean granted(Context context, String permission) {
    try {
      return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    } catch (Throwable t) {
      return false;
    }
  }

  static boolean guided(Activity activity) {
    return activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false);
  }

  static void markGuided(Activity activity) {
    activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply();
  }

  /**
   * 引导文案（每个权限一句白话，说明"为什么需要"，不是技术描述）。
   * 返回 {标题, 说明} 两行。
   */
  static String[] rationale(String permission) {
    if (Manifest.permission.RECORD_AUDIO.equals(permission)) {
      return new String[]{"麦克风", "用于语音输入和语音消息，让你不用打字也能提问"};
    }
    if (Manifest.permission.CAMERA.equals(permission)) {
      return new String[]{"相机", "用于拍照发图，把图片直接发给 AI 分析"};
    }
    if (Build.VERSION.SDK_INT >= 33 && Manifest.permission.POST_NOTIFICATIONS.equals(permission)) {
      return new String[]{"通知", "AI 回复完成时提醒你，切到别的页面也不会错过"};
    }
    return new String[]{"权限", "软件需要此权限才能完整使用相关功能"};
  }

  /** 权限在设置里的名字（用于"去设置"引导） */
  static String settingsLabel(String permission) {
    return rationale(permission)[0];
  }

  /** 打开本应用的系统设置页（用户拒绝后引导手动开启的唯一正路） */
  static void openAppSettings(Activity activity) {
    try {
      Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.getPackageName(), null));
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      activity.startActivity(intent);
    } catch (Throwable ignored) {
    }
  }
}
