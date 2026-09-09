buildscript {
 repositories { google(); mavenCentral() }
 dependencies { classpath("com.android.tools.build:gradle:8.11.0"); classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0") }
}
plugins { id("app.cash.paparazzi") version "2.0.0-alpha02" apply false }
allprojects { repositories { google(); mavenCentral() } }
