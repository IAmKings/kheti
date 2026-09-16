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
        namespace = "com.kheti.layout"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        // 真机上的文本引擎是 Minikin/StaticLayout，必须在设备上跑才有证据。
        // 源码位于 src/androidDeviceTest/kotlin。
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    jvm("desktop")

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val desktopTest by getting

        commonMain.dependencies {
            // kheti-core 的类型出现在公共 API 上（KhetiMetrics.Size / KhetiMetrics.Heading
            // 是 KhetiTextStyle 的参数），必须是 api：否则只依赖 kheti-layout 的使用方
            // 拿不到这些类型，编译会失败。
            api(project(":kheti-core"))
            // 公共 API 暴露 DrawScope / Color / IntSize，同属 compose.ui
            api(compose.ui)
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
        // 用 named() 延迟解析：设备测试源集由上面的 withDeviceTest {} 创建
        named("androidDeviceTest") {
            dependencies {
                implementation("androidx.test:core:1.6.1")
                implementation("androidx.test:runner:1.6.2")
                implementation("androidx.test.ext:junit:1.2.1")
                implementation(kotlin("test"))
            }
        }
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

// CMP 1.11.1 的 resources 插件尚未适配 AGP 9 的 KMP 设备测试变体：它为该变体创建了
// 资产拷贝任务，却没有设置 outputDirectory，导致配置期校验直接失败。
// kheti-layout 不包含 composeResources，该拷贝实质为空，补一个目录即可解除阻塞。
// 该任务类型在 CMP 插件中是 internal，只能通过反射设置属性。
// TODO: 待 CMP 支持 AGP 9 的 KMP Android 插件后移除。
tasks.matching { it.name == "copyAndroidDeviceTestComposeResourcesToAndroidAssets" }.configureEach {
    val getter = javaClass.methods.firstOrNull { it.name == "getOutputDirectory" && it.parameterCount == 0 }
        ?: error("CMP 资产拷贝任务的 outputDirectory 访问器消失了，请检查 CMP 版本变化")
    val property = getter.invoke(this)
    if (property is org.gradle.api.file.DirectoryProperty && !property.isPresent) {
        property.set(layout.buildDirectory.dir("kheti/compose-resources-assets"))
    }
}
