// v1.8.0 起全工程统一使用 RikkaHub 上游的版本目录（rikkahub/gradle/libs.versions.toml），
// AGP 8.11.0 → 9.3.1、Kotlin 2.1.0 → 2.4.10（见 settings.gradle 的 versionCatalogs 导入）。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
    id("app.cash.paparazzi") version "2.0.0-alpha02" apply false
}
