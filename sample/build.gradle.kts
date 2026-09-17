import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(project(":kheti-core"))
            implementation(project(":kheti-compose"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.kheti.sample.resources"
}

android {
    namespace = "com.kheti.sample"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "com.kheti.sample"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // 版本与库同源（gradle.properties 的 kheti.version）：
        // GitHub Release 的标签 sample-v<x.y.z> 与 APK 版本因此始终一致，
        // 且 versionCode 随语义化版本递增，用户可直接覆盖安装新版而无需先卸载。
        val sampleVersion = providers.gradleProperty("kheti.version").get()
        val versionParts = sampleVersion.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        versionName = sampleVersion
        versionCode = (versionParts.getOrNull(0) ?: 0) * 10000 +
            (versionParts.getOrNull(1) ?: 0) * 100 + (versionParts.getOrNull(2) ?: 0)
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        getByName("release") { isMinifyEnabled = false }
    }
}

compose.desktop {
    application {
        mainClass = "com.kheti.sample.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "KhetiSample"
            packageVersion = "1.0.0"
        }
    }
}
