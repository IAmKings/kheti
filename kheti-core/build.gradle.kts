import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    // AGP 9 起 KMP 的 Android 目标必须用官方 KMP 库插件；
    // 旧的 com.android.library + androidTarget() 组合已被 KGP 标记为 deprecated，
    // 且不会产出 Android publication（第三方 Android 使用方将无法解析构件）。
    alias(libs.plugins.androidKmpLibrary)
}

// 发布约定：坐标、POM 元数据、sources/javadoc 伴随件、按凭据启用远端仓库
apply(from = rootProject.file("gradle/publishing.gradle.kts"))

kotlin {
    android {
        namespace = "com.kheti.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        withHostTest {}
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

tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
    }
}
