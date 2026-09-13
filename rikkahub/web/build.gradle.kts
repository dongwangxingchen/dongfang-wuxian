import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    id("rikkahub.android.library")
}

val webUiDir = rootProject.layout.projectDirectory.dir("web-ui")
val webStaticResourcesDir = layout.projectDirectory.dir("src/main/resources/static")
// [DFWX PATCH] configuration cache 兼容：把脚本级对象在配置期收敛为可序列化的普通值，
// 任务闭包（onlyIf）不再捕获 Gradle 脚本引用。同步上游时需重放。
val webUiDirFile: java.io.File = webUiDir.asFile
val hasWebUiSources: Boolean = webUiDirFile.exists()

val buildWebUi = tasks.register<Exec>("buildWebUi") {
    group = "build"
    description = "Build web-ui and copy its static output into the web module resources."

    // [DFWX PATCH] 本工程未 vendor web-ui 前端（无 Node/pnpm 工具链）：目录不存在时跳过
    // 前端构建，Web 局域网控制台降级为占位页（聊天主功能不受影响）。装好 Node22+pnpm11
    // 并放回 web-ui 后自动恢复完整构建。同步上游时需重放本改动。
    // 注意用 enabled= 而非 onlyIf{}：.kts 顶层 val 属脚本对象属性，闭包引用它会被
    // configuration cache 判为"脚本对象引用"（2026-09-14 实测）；enabled 是纯布尔字段可序列化。
    enabled = webUiDirFile.exists()

    workingDir = webUiDirFile
    when {
        Os.isFamily(Os.FAMILY_MAC) -> commandLine("zsh", "-ic", "pnpm run build")
        Os.isFamily(Os.FAMILY_WINDOWS) -> commandLine("cmd", "/c", "pnpm run build")
        else -> commandLine("pnpm", "run", "build")
    }

    inputs.files(
        webUiDir.file("package.json"),
        webUiDir.file("pnpm-lock.yaml"),
        webUiDir.file("components.json"),
        webUiDir.file("copy.ts"),
        webUiDir.file("react-router.config.ts"),
        webUiDir.file("tsconfig.json"),
        webUiDir.file("vite.config.ts"),
        webUiDir.file("vite-env.d.ts")
    )
    inputs.dir(webUiDir.dir("app"))
    inputs.dir(webUiDir.dir("public"))
    outputs.dir(webStaticResourcesDir)
}

android {
    namespace = "me.rerere.rikkahub.web"

    defaultConfig {
        minSdk = 24
    }
}

tasks.named("preBuild") {
    dependsOn(buildWebUi)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // ktor server
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.conditional.headers)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.cors)
    api(libs.ktor.server.auth)
    api(libs.ktor.server.auth.jwt)
    api(libs.ktor.server.core)
    implementation(libs.ktor.server.host.common)
    api(libs.ktor.server.content.negotiation)
    api(libs.ktor.server.status.pages)
    api(libs.ktor.server.sse)
    api(libs.ktor.server.cio)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
