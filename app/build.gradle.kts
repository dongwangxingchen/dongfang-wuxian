plugins { id("com.android.application"); id("app.cash.paparazzi") }

tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("-g:none") }

android {
 namespace = "cc.nkbr.lanzouplus"
 compileSdk = 36
 buildFeatures { buildConfig = true; aidl = true }
 androidResources { additionalParameters += listOf("--no-xml-namespaces", "--no-compile-sdk-metadata") }
 defaultConfig { applicationId = "dfwx.dongdang"; minSdk = 24; targetSdk = 36; versionCode = 1030003; versionName = "1.3.3" }
 flavorDimensions += "catalog"
 productFlavors {
  create("empty") {
   dimension = "catalog"
   isDefault = true
   applicationId = "dfwx.dongdang"
   buildConfigField("boolean", "IS_FULL", "false")
   buildConfigField("String", "OFFICIAL_URL", "\"https://github.com/nekobyran/lanzouplus\"")
   resValue("string", "app_name", "东方无限")
  }
 }
 buildTypes {
  getByName("release") {
   isMinifyEnabled = true; isShrinkResources = true
   proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
   signingConfigs {
    create("heiyao") {
     storeFile = rootProject.file("../heiyao.keystore")
     storePassword = "heiyao2026"
     keyAlias = "heiyao"
     keyPassword = "heiyao2026"
    }
   }
   signingConfig = signingConfigs.getByName("heiyao")
  }
 }
}

dependencies {
 implementation("dev.rikka.shizuku:api:13.1.5")
 implementation("dev.rikka.shizuku:provider:13.1.5")
 compileOnly("androidx.annotation:annotation:1.3.0")
}