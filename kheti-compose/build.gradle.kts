import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    // 见 kheti-core：AGP 9 下必须用官方 KMP 库插件，否则不产出 Android publication
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// 发布约定：坐标、POM 元数据、sources/javadoc 伴随件、按凭据启用远端仓库
apply(from = rootProject.file("gradle/publishing.gradle.kts"))

kotlin {
    android {
        namespace = "com.kheti.compose"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        withHostTest {}
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
            // 公共 composable 签名暴露 Modifier / Color / FontFamily / TextStyle（compose.ui）
            // 与 @Composable 注解（compose.runtime），使用方必须能在自己的编译类路径上看到它们
            api(compose.ui)
            api(compose.runtime)
            implementation(compose.foundation)
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

tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
    }
}
