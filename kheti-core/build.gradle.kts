import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop")

    // CMP 1.11 起不再发布 iosX64 构件（与 songci 一致）
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val desktopTest by getting

        commonMain.dependencies {
            // kheti-core 是纯规则层：不依赖 Compose，保证 100% 可在 commonTest 覆盖
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        desktopTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.kheti.core"
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
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
    }
}
