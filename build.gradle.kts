// v1.8.0 起全工程统一使用 RikkaHub 上游的版本目录（rikkahub/gradle/libs.versions.toml），
// AGP 8.11.0 → 9.4.0、Kotlin 2.1.0 → 2.4.10（见 settings.gradle 的 versionCatalogs 导入）。
// v1.9.0（问题表单#3）：摘除 paparazzi 插件——2.0.0-alpha02 依赖 AGP9 已移除的 BaseExtension，
// test/check/build 任务全废（实证）；快照测试停泊于 tools/parked-tests/ 待官方适配 AGP9 后恢复。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
}
