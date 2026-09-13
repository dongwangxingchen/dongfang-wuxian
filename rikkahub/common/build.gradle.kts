import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("rikkahub.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "me.rerere.common"
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.optIn.add("kotlin.uuid.ExperimentalUuidApi")
        compilerOptions.optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    // okhttp
    api(libs.okhttp)
    api(libs.okhttp.sse)
    api(libs.okhttp.logging)

    // kotlinx
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.datetime)

    // apache commons
    api(libs.commons.text)

    // floating
    // https://github.com/Petterpx/FloatingX
    // [DFWX PATCH] master 的版本目录已把 floatingx 改为版本号条目+floatingx-app/floatingx-compose 两个库，
    // 快照里本文件还是旧写法 libs.floatingx，对齐为 floatingx.app。同步上游时如上游已改则覆盖即可。
    api(libs.floatingx.app)
    api(libs.floatingx.compose)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // quickjs
    api(libs.quickjs)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
