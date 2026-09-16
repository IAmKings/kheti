# 发布与引用

kheti 以标准 Maven 坐标发布，第三方 KMP 项目用一行 `implementation(...)` 即可引用，
不需要源码依赖或 git submodule。

## 1. 坐标与产物

| 项 | 值 |
|---|---|
| groupId | `io.github.iamkings`（Maven Central 命名空间规则 `io.github.<GitHub 用户名小写>`） |
| version | `0.1.0`，定义在 `gradle.properties` 的 `kheti.version` |
| 模块 | `kheti-core`（纯规则层）· `kheti-layout`（排版引擎）· `kheti-compose`（Compose 组件） |

每个模块发布 5 个构件，由 Gradle Module Metadata 自动选择，使用方无需关心：

| 变体 | 后缀 | 形态 |
|---|---|---|
| Android | `-android` | AAR |
| Desktop / JVM | `-desktop` | JAR |
| iOS 真机 / 模拟器 | `-iosarm64` / `-iossimulatorarm64` | klib |
| 根构件 | 无后缀 | `.module` + `.pom` + 公共元数据（KMP 使用方按坐标解析时读它） |

每个构件都带 `-sources.jar` 与 `-javadoc.jar`（Kotlin 无 javadoc，按 Maven Central
允许的方式发布含文档入口 README 的占位 jar）。

## 2. 本机验证

```bash
./gradlew publishToMavenLocal     # 产物进 ~/.m2/repository/io/github/iamkings/
```

这条命令不需要任何凭据，随时可跑。

## 3. 三条分发通道

发布配置写在 `gradle/publishing.gradle.kts`，由三个库模块 `apply(from = ...)` 共用。
**远端仓库按凭据是否存在启用**：没有凭据时不会误传到任何远端，也不会因为缺少
签名配置而让本地构建失败。

### 3.1 Maven Central（推荐：公开、免认证、最方便第三方引用）

前置条件（一次性，需要人工完成）：

1. 注册 <https://central.sonatype.com/>，验证命名空间 `io.github.iamkings`
   （GitHub 用户名方式无需自有域名）。
2. 生成 **Portal User Token**（用户名 + 密码）。
3. 准备 GPG 密钥并发布公钥：`gpg --gen-key` → `gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>`。

> **这三步有脚本可跑**：`tools/setup-central-publishing.sh` 会逐段带你完成
> （本机缺 gpg 时先装、生成密钥、发公钥、生成 token、导出私钥、写入 Gradle 配置并自检），
> 机密写到仓库外的 `~/.gradle/gradle.properties`，可反复重跑且幂等。
>
> ```bash
> ./tools/setup-central-publishing.sh
> ```
>
> 两个容易踩的点，脚本会替你拦住：
> - **密钥类型选 RSA**：默认的 ECC/ed25519 会生成「签名子密钥」，而 Nexus 只能用**主密钥**验签；
> - **口令写错**：脚本会在临时钥匙串里用导出的私钥真签一次，口令不对当场报错，
>   而不是等到 `publish` 时才失败。

然后：

```bash
export SONATYPE_USERNAME=<token 用户名>
export SONATYPE_PASSWORD=<token 密码>
export SIGNING_KEY="$(gpg --armor --export-secret-keys <KEY_ID>)"   # 或 --export-secret-key 的 ASCII 私钥
export SIGNING_PASSWORD=<私钥口令>

./gradlew publish        # 上传到 Central Portal 的 OSSRH Staging API
```

> **上传后还有一步**：Gradle 内置 `maven-publish` 属于官方所说的 “Maven-API-like” 插件，
> 它只发 PUT 请求、不告诉服务端「一次部署何时结束」。按官方要求，上传完还需调用一次接口，
> 部署才会出现在 <https://central.sonatype.com/publishing>，再从那里点 Publish
> 才会进入 Maven Central。
>
> 认证统一是 **Bearer**：`base64(token用户名:token密码)`（不是 Basic 认证）。
>
> **两个端点，按 IP 是否变化二选一：**
>
> ```bash
> T=$(printf '%s:%s' "$SONATYPE_USERNAME" "$SONATYPE_PASSWORD" | base64)
>
> # A. 出口 IP 未变（首选，最简单）
> curl -H "Authorization: Bearer $T" -X POST \
>   https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/io.github.iamkings
>
> # B. 出口 IP 变了 —— staging 仓库是按 IP 隔离的，A 会报
> #    "No repository found for <key>/<新IP>/io.github.iamkings--default-repository"
> #    此时先按 ip=any 查出仓库 key，再用 key 端点（不受 IP 限制）：
> curl -s -H "Authorization: Bearer $T" \
>   "https://ossrh-staging-api.central.sonatype.com/manual/search/repositories?ip=any&profile_id=io.github.iamkings"
> # key 形如 qNyfCK/209.9.201.4/io.github.iamkings--default-repository，需 URL 编码后使用：
> curl -H "Authorization: Bearer $T" -X POST \
>   "https://ossrh-staging-api.central.sonatype.com/manual/upload/repository/<URL 编码后的 key>"
> ```
>
> ⚠️ **家用宽带/代理会导致出口 IP 漂移**（本项目实测在 209.9.201.4 / 120.229.45.17 之间跳变），
> 于是「上传」与「补调用」可能来自不同 IP。官方文档说 B 端点是为「用 UI 完成发布的发布者」
> 准备的，实测跨 IP 可用。若 A、B 都失败，从当前 IP 重新 `./gradlew publish` 一次即可生成
> 属于新 IP 的 staging 仓库。
>
> 若不想手工补这一步，可改用 `com.gradleup.nmcp` 或 `com.vanniktech.maven.publish`
> 这类直接对接 Portal API 的插件——本项目为保持零第三方发布依赖而未采用。

发布端点：`https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/`
（旧的 `s01.oss.sonatype.org` 已停用）。

### 3.2 GitHub Packages（内部/私有分发）

```bash
export GITHUB_ACTOR=<用户名>
export GITHUB_TOKEN=<具备 packages:write 的 PAT>
./gradlew publishAllPublicationsToGitHubPackagesRepository
```

⚠️ GitHub Packages **对公开包也要求认证**，第三方必须自带 token 才能拉取，
因此它适合内部共享，不适合作为面向公众的通道。

### 3.3 JitPack（零配置，但有风险）

JitPack 直接从 GitHub tag 构建，无需任何发布配置：使用方加
`maven("https://jitpack.io")` 后引用 `com.github.IAmKings:kheti:<tag>`。

⚠️ **本机未验证**。JitPack 的构建机是 Linux，而本项目含 iOS 目标，
iOS klib 需要 macOS 工具链——这类组合在 JitPack 上经常失败。
若要走这条路，建议先只发布 Android/Desktop 相关目标。

## 4. 使用方如何引用

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()          // 走 Maven Central 时
        // maven("https://jitpack.io")
    }
}

// build.gradle.kts（KMP 使用方）
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.iamkings:kheti-compose:0.1.0")
        }
    }
}
```

Gradle 会按目标平台自动选变体：Android 取 `-android`（AAR），
Desktop/JVM 取 `-desktop`（JAR），iOS 取对应 klib。

只需规则层或引擎层时可以只依赖 `kheti-core` / `kheti-layout`——
`kheti-layout` 已把 `kheti-core` 与 `compose.ui` 声明为 `api`，
`kheti-compose` 把 `kheti-core`、`kheti-layout`、`compose.ui`、`compose.runtime` 声明为 `api`，
因此使用方即使不显式声明这些依赖也能编译。

字体：库不打包字体（`KhetiTheme(fonts = KhetiFontFamilies(...))` 由使用方注入），
不引入字体许可问题。

## 5. 发布前检查清单

- [ ] `gradle.properties` 的 `kheti.version` 已递增（Central 上的版本不可覆盖）
- [ ] `./gradlew :kheti-core:desktopTest :kheti-layout:desktopTest :kheti-compose:desktopTest` 全绿
- [ ] `./gradlew :kheti-layout:connectedAndroidDeviceTest` 在真机上全绿
- [ ] 三个模块的 `compileCommonMainKotlinMetadata` 通过（发布公共构件的前提）
- [ ] `publishToMavenLocal` 后，产物里的 POM 含 name/description/url/license/developers/scm

## 6. 已知限制

| 项 | 说明 |
|---|---|
| 版本号 | 目前手写在 `gradle.properties`；若改用 git tag 驱动需额外配置 |
| 无 CI 发布流水线 | 尚未提供 GitHub Actions 工作流 |
| javadoc | 占位 jar（内含文档入口），不是 Dokka 生成的 API 文档 |
| 测试覆盖 | 桌面 84 项 + 真机 4 项；iOS 目标能构建与发布，但未在 iOS 设备/模拟器上跑过验收 |
| 本机网络 | 开发机直连 `dl.google.com` 不稳定，AGP 的 lint 工具链（约 100MB，仅 Google Maven 有）需靠镜像；且阿里云 central 镜像对部分大 klib 会与 Gradle 的 HTTP 客户端卡死（`curl` 正常），iOS 构件发布时需临时改用 Central 直连（见下） |

### 本机网络绕行方法

若遇到镜像导致的下载失败（典型症状：`Read timed out`，但同一 URL 用 `curl` 能正常下载），
用 init script 临时把仓库换成「阿里云 google 镜像 + Central 直连」再发布：

```kotlin
// /tmp/direct.init.gradle.kts
settingsEvaluated {
    dependencyResolutionManagement {
        repositories.clear()
        repositories.maven("https://maven.aliyun.com/repository/google")  // dl.google.com 不可达
        repositories.mavenCentral()
    }
}
```

```bash
./gradlew -I /tmp/direct.init.gradle.kts --refresh-dependencies \
  -Dorg.gradle.internal.http.socketTimeout=300000 \
  :kheti-compose:publishIosArm64PublicationToMavenLocal
```

