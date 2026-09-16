import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
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

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val desktopTest by getting

        commonMain.dependencies {
            // kheti-layout / kheti-core 的类型出现在公共 API 上（KhetiAlignment、KhetiMetrics 等），
            // 必须是 api 才能被使用方看到
            api(project(":kheti-core"))
            api(project(":kheti-layout"))
            implementation(compose.ui)
            implementation(compose.foundation)
            implementation(compose.runtime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        desktopTest.dependencies {
            implementation(kotlin("test"))
            implementation(compose.desktop.currentOs)
        }
    }
}

android {
    namespace = "com.kheti.compose"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
    }
}
