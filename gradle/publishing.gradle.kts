// 三个库模块共用的发布约定（`gradle/publishing.gradle.kts`）。
//
// 用法：模块 build.gradle.kts 中 `apply(from = rootProject.file("gradle/publishing.gradle.kts"))`。
// 只用到 Gradle 核心插件 `maven-publish` + `signing`，不引入任何第三方发布插件，
// 因此在受限网络下也能工作。
//
// 坐标取自 gradle.properties 的 kheti.group / kheti.version。
// 远端仓库按“凭据是否存在”启用，所以：
//   - ./gradlew publishToMavenLocal         永远可用（本地开发/验证）
//   - 缺凭据时不会误传到任何远端
//
// Maven Central 的硬性要求（https://central.sonatype.org/publish/requirements/）：
//   sources jar + javadoc jar + GPG .asc 签名 + 完整 POM 元数据。
//   Kotlin 没有 javadoc，按官方允许的方式发占位 javadoc jar（内含文档入口 README）。

import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.SigningExtension

apply(plugin = "maven-publish")
apply(plugin = "signing")

val moduleName = project.name
val repoUrl = "https://github.com/IAmKings/kheti"
val licenseUrl = "$repoUrl/blob/master/LICENSE"
val docsUrl = "$repoUrl/tree/master/docs"

group = providers.gradleProperty("kheti.group").get()
version = providers.gradleProperty("kheti.version").get()

val moduleDescription = when (moduleName) {
    "kheti-core" ->
        "赫蹏（heti）中文排版规则层：字符分类、中西文间距与全角标点挤压增量、度量常量。纯 Kotlin，无 Compose 依赖。"
    "kheti-layout" ->
        "赫蹏中文排版引擎：按字度量、定位与分段自绘，支持横排、竖排、行间注、着重号、多栏与悬挂标点。"
    "kheti-compose" ->
        "赫蹏中文排版 Compose Multiplatform 组件库：诗词/古文版式、竖排、行间注与文章模式组件。"
    else -> "赫蹏（heti）中文排版 Compose Multiplatform 实现：$moduleName"
}

// ---- 占位 javadoc jar（Central 校验要求）----
// 生成文档入口 README，再打成 <artifactId>-<version>-javadoc.jar。
val javadocReadme = tasks.register("khetiJavadocReadme") {
    val out = layout.buildDirectory.file("kheti-javadoc/README.md")
    outputs.file(out)
    val text = """
        # $moduleName ${project.version}

        kheti 是 Kotlin / Compose Multiplatform 项目，API 文档以 KDoc 形式写在源码中，
        因此这里按 Maven Central 允许的方式提供占位 javadoc jar。

        - 源码与 KDoc：$repoUrl
        - 中文排版规则与用法：$repoUrl/blob/master/README.md
        - 设计与验收文档：$docsUrl

        引用方式（Gradle Kotlin DSL）：

        ```kotlin
        commonMain.dependencies {
            implementation("${project.group}:$moduleName:${project.version}")
        }
        ```
    """.trimIndent()
    doLast {
        val f = out.get().asFile
        f.parentFile.mkdirs()
        f.writeText(text)
    }
}

val javadocJar = tasks.register<Jar>("khetiJavadocJar") {
    description = "占位 javadoc jar（Maven Central 校验要求，内含文档入口 README）"
    archiveClassifier.set("javadoc")
    from(javadocReadme)
}

// ---- POM 元数据 + 发布仓库 ----
extensions.configure<PublishingExtension> {
    publications.withType<MavenPublication>().configureEach {
        // 每个构件都要有 javadoc 伴随件（sources 由各 Kotlin target 的 withSourcesJar() 提供）
        artifact(javadocJar)

        pom {
            name.set("${project.group}:$moduleName")
            description.set(moduleDescription)
            url.set(repoUrl)
            licenses {
                license {
                    name.set("MIT License")
                    url.set(licenseUrl)
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("IAmKings")
                    name.set("kheti contributors")
                    url.set(repoUrl)
                }
            }
            scm {
                url.set(repoUrl)
                connection.set("scm:git:$repoUrl.git")
                developerConnection.set("scm:git:ssh://git@github.com/IAmKings/kheti.git")
            }
        }
    }

    // Sonatype Central Portal：Gradle 内置 maven-publish 属 “Maven-API-like” 插件，
    // 必须用官方给出的 OSSRH Staging API 兼容端点（旧 s01.oss.sonatype.org 已停用）。
    val sonatypeUser = providers.environmentVariable("SONATYPE_USERNAME").orNull
        ?: providers.gradleProperty("sonatype.username").orNull
    val sonatypePassword = providers.environmentVariable("SONATYPE_PASSWORD").orNull
        ?: providers.gradleProperty("sonatype.password").orNull
    if (!sonatypeUser.isNullOrBlank() && !sonatypePassword.isNullOrBlank()) {
        repositories.maven {
            name = "CentralPortal"
            url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            credentials {
                username = sonatypeUser
                password = sonatypePassword
            }
        }
    }

    // GitHub Packages：注意它即使对公开包也要求认证，第三方引用需自带 token，
    // 因此只作为内部/私有分发通道，不作为面向公众的通道。
    val gprUser = providers.environmentVariable("GITHUB_ACTOR").orNull
        ?: providers.gradleProperty("gpr.user").orNull
    val gprToken = providers.environmentVariable("GITHUB_TOKEN").orNull
        ?: providers.gradleProperty("gpr.token").orNull
    if (!gprUser.isNullOrBlank() && !gprToken.isNullOrBlank()) {
        repositories.maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/IAmKings/kheti")
            credentials {
                username = gprUser
                password = gprToken
            }
        }
    }
}

// ---- GPG 签名（仅在有私钥时启用，本地 publishToMavenLocal 不受影响）----
val signingKey = providers.environmentVariable("SIGNING_KEY").orNull
    ?: providers.gradleProperty("signing.key").orNull
val signingPassword = providers.environmentVariable("SIGNING_PASSWORD").orNull
    ?: providers.gradleProperty("signing.password").orNull

if (!signingKey.isNullOrBlank()) {
    extensions.configure<SigningExtension> {
        useInMemoryPgpKeys(signingKey, signingPassword)
    }
    project.afterEvaluate {
        val publications = extensions.getByType(PublishingExtension::class.java).publications
        val signing = extensions.getByType(SigningExtension::class.java)
        publications.forEach { signing.sign(it) }
    }
}
