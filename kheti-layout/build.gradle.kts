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
            implementation(project(":kheti-core"))
            implementation(compose.ui)
            implementation(compose.runtime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        desktopTest.dependencies {
            implementation(kotlin("test"))
            // 桌面端文本测量/绘制需要 Skiko 原生运行时
            implementation(compose.desktop.currentOs)
        }
    }
}

dependencies {
    // 真机上的文本引擎是 Minikin/StaticLayout，必须在设备上跑才有证据。
    // 源码位于 src/androidInstrumentedTest/kotlin（KMP 约定目录）。
    add("androidTestImplementation", "androidx.test:core:1.6.1")
    add("androidTestImplementation", "androidx.test:runner:1.6.2")
    add("androidTestImplementation", "androidx.test.ext:junit:1.2.1")
    add("androidTestImplementation", kotlin("test"))
}

android {
    namespace = "com.kheti.layout"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// 文本测量与离屏绘制在无头环境即可完成（Phase 0/CI 需要）
tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
