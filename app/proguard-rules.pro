# Activity/Provider entry points are retained by the manifest-generated rules.
# 放宽访问修饰以便 R8 合并类（v1.9.0 问题表单#18：原 -repackageclasses 在 -dontobfuscate 下
# 为死指令已删——混淆关闭时不存在重命名打包；勿在保留 -dontobfuscate 的情况下加回）。
-allowaccessmodification

# Shizuku instantiates this UserService by class name in a shell/root process.
-keep class cc.nkbr.lanzouplus.AdbShellService { public <init>(); public <init>(android.content.Context); *; }

# ---- v1.8.0 起并入 RikkaHub 上游 keep 规则（rikkahub/app/src/main/keepRules/rikkahub.keep 原文） ----
# Release builds may shrink and optimize code, but must not rename symbols.
-dontobfuscate

# Kotlin reflection is used to instantiate serializable settings classes at runtime.
-keep @kotlinx.serialization.Serializable class * { *; }

# Keep source locations so release stack traces retain useful line information.
-keepattributes SourceFile,LineNumberTable

# Ktor references JVM-only management APIs from its debug detector.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

# Jackson references these JVM-only java.beans annotations.
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient

# re2j has optional references that are unavailable on Android.
-dontwarn com.google.re2j.**

# Jackson TypeReference reads its anonymous subclass's generic superclass at runtime.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keep,allowobfuscation class * extends com.fasterxml.jackson.core.type.TypeReference

# The embedded Ktor server signs and verifies JWTs through Auth0/Jackson at runtime.
# Keep their implementation intact because R8 optimization can break JWT payload parsing.
-keep class com.fasterxml.jackson.** { *; }
-keep class com.auth0.jwt.** { *; }

# Keep JlatexMath
-keep class org.scilab.forge.jlatexmath.** { *; }

