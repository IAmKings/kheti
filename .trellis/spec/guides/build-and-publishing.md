# 构建与发布约定

> **适用**：改动 `build.gradle.kts`、`gradle/`、依赖可见性，或执行发布时。

---

## 1. Android 目标必须用官方 KMP 库插件

AGP 9 起，KMP 的 Android 目标**不能**再用 `com.android.library` + `androidTarget()`：

- KGP 会打印 `deprecated compatibility with Android Gradle plugin: 'com.android.library'`；
- 更严重的是 **不产出 Android publication** —— `publishing.publications` 里只有
  `desktop` / `iosArm64` / `iosSimulatorArm64` / `kotlinMultiplatform`，
  Android 使用方无法解析构件，且不会报错，只是静默缺失。

正确写法：

```kotlin
plugins { alias(libs.plugins.androidKmpLibrary) }   // com.android.kotlin.multiplatform.library

kotlin {
    android {
        namespace = "com.kheti.xxx"
        compileSdk = ...
        minSdk = ...
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        withHostTest {}                                   // 需要 commonTest 在 Android 上跑时
        withDeviceTest { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    }
}
```

配套变化：

- 真机测试源集目录从 `src/androidInstrumentedTest/` 改为 **`src/androidDeviceTest/`**；
- 真机测试任务从 `connectedDebugAndroidTest` 改为 **`connectedAndroidDeviceTest`**；
- 旧 `android { }` 顶层块不再需要。

**验证方法**（不要只看构建成功）：

```bash
./gradlew :kheti-compose:tasks --all | grep generatePomFileFor   # 必须出现 ...AndroidPublication
```

---

## 2. 公共 API 暴露的类型必须是 `api`

发布后 `implementation` 依赖不会出现在使用方的编译类路径上。判断标准是
**该类型是否出现在公共签名里**：

| 模块 | 必须 `api` 的依赖 | 原因 |
|---|---|---|
| `kheti-layout` | `kheti-core`、`compose.ui` | `KhetiMetrics.Size/Heading` 是 `KhetiTextStyle` 的参数；公共 API 暴露 `DrawScope`/`Color`/`IntSize` |
| `kheti-compose` | `kheti-core`、`kheti-layout`、`compose.ui`、`compose.runtime` | 参数里的 `Modifier`/`Color`/`FontFamily`，以及 `@Composable` 注解 |

`compose.foundation` 可以保持 `implementation`（其类型不出现在公共签名里）。

**验证方法**：用第三方消费工程只声明顶层依赖，确认传递依赖出现在编译类路径上。

---

## 3. metadata 编译是发布的守门人

`compileCommonMainKotlinMetadata` 只在**发布**（`publishToMavenLocal` / `publish`）时才跑。
平台编译能过 **不代表** metadata 能过 —— 这个任务会暴露两类只在发布时爆的问题：

### 3.1 公共元数据里缺失的 stdlib 成员

`MatchGroup.range` 在 Kotlin 2.4.10 的**公共元数据**中没有暴露，写
`m.groups[1]?.range` 会在 metadata 编译报 `Unresolved reference 'range'`
（`MatchGroup.value`、`MatchResult.range`、`groupValues` 都正常）。

规避方式：用等价且可用的 API。`AdjustmentPlanner` 的三个间距正则都用环视
（`(?<=…)`/`(?=…)`，零宽）把捕获组 1 限定为整个匹配，所以组 1 区间恒等于 `m.range`：

```kotlin
if (m.groups[1] == null) return
val g = m.range
```

### 3.2 common 代码里的 JVM 专有 API

`String.format`、`java.*`、`System.*` 等只在 JVM 目标可用，common 代码里必须避开。
需要定点格式化时自己写（见 `kheti-layout/.../Spike.kt` 的 `fixed()`）。

**结论**：任何改动公共源码的提交，发布前必须跑

```bash
./gradlew :kheti-core:compileCommonMainKotlinMetadata \
          :kheti-layout:compileCommonMainKotlinMetadata \
          :kheti-compose:compileCommonMainKotlinMetadata
```

---

## 4. 发布坐标与通道

`group` / `version` 来自 `gradle.properties` 的 `kheti.group` / `kheti.version`，
发布约定集中在 `gradle/publishing.gradle.kts`，三个库模块 `apply(from = ...)` 共用。
远端仓库**按凭据是否存在启用**，所以缺凭据时既不会误传远端，也不会让本地构建失败。

完整流程（Maven Central / GitHub Packages / JitPack、GPG 签名、
上传后必需的一次 Portal 调用）见 `docs/publishing.md`；
一次性凭据配置用 `tools/setup-central-publishing.sh`。

### 签名：最危险的是"静默不签"（真实事故复盘）

首次发布到 Maven Central 时部署校验失败，报 `Missing signature for file: ...`，
而 `./gradlew publish` 是**成功**的。复盘后的真实原因链：

1. 向导用 `gpg --armor --export-secret-keys > file` 导出私钥。
   **GnuPG 2.5 导出私钥需要口令**，脚本里 pinentry 弹不出窗，于是留下一个 **0 字节文件**；
   脚本又把 stderr 丢进了 `/dev/null`，失败原因一并丢失。
2. `gradle/publishing.gradle.kts` 里 `signingKey` 读到空内容 → `isNullOrBlank()` 为真
   → **整个签名段被跳过**，不报错。
3. `publish` 因此照常成功，上传的却是无 `.asc` 的构件，直到 Central 校验才暴露。

**教训**：
- `signingKey` 为空时**必须告警**（`gradle/publishing.gradle.kts` 已加），
  否则"配了密钥却是空的"和"没配密钥"在构建输出里长得一模一样；
- 导出私钥要 `--batch --pinentry-mode loopback --passphrase`，且**不要吞 stderr**；
- 判断"签名到底挂上没有"，看 **Sign 任务是否存在**与 **`.asc` 是否真的产出**，
  而不是看 `publish` 成不成功。

挂钩写法（`configureEach` 对已存在与后加入的 publication 都会触发）：

```kotlin
val publications = extensions.getByType(PublishingExtension::class.java).publications
extensions.configure<SigningExtension> {
    useInMemoryPgpKeys(signingKey, signingPassword)
    publications.withType(MavenPublication::class.java).configureEach { sign(this) }
}
```

**自检命令**（三条都要看）：

```bash
./gradlew :kheti-core:tasks --all | grep -E "^sign"      # 应列出 5 个 sign*Publication
./gradlew :kheti-core:signDesktopPublication            # 应 BUILD SUCCESSFUL
find kheti-core/build -name "*.asc" | head              # 应真的有 .asc 产出
gpg --verify kheti-core/build/libs/kheti-core-desktop-0.1.0.jar.asc \
             kheti-core/build/libs/kheti-core-desktop-0.1.0.jar   # 应报“完好的签名”
```

### javadoc 伴随件必须每个 publication 一个文件

Central 要求每个 jar 都有 `-javadoc.jar`。若像最初那样**共用一个** `Jar` 任务挂给所有
publication，多个签名任务会争抢同一个 `.asc` 输出路径，Gradle 直接报：

```
Task ':x:publishAndroidPublication...' uses this output of task ':x:signDesktopPublication'
without declaring an explicit or implicit dependency
```

正确做法是按 publication 名分别注册（文件名唯一即可，磁盘文件名不影响发布名 ——
发布名由 publication 坐标决定）：

```kotlin
fun javadocJarFor(publicationName: String) = tasks.register<Jar>("${publicationName}JavadocJar") {
    archiveBaseName.set("$moduleName-$publicationName")
    archiveClassifier.set("javadoc")
    from(javadocReadme)
}
```

### 私钥的三种提供方式

`gradle/publishing.gradle.kts` 按优先级读取：

1. 环境变量 `SIGNING_KEY`（内容）—— CI 用
2. Gradle 属性 `signing.key`（内容）—— **不推荐**：多行 ASCII 私钥在 properties 里要转义换行
3. Gradle 属性 `signing.keyFile`（文件路径）—— 本地发布推荐

本地配置由 `tools/setup-central-publishing.sh` 写入仓库外的
`~/.gradle/gradle.properties`（`sonatype.username` / `sonatype.password` /
`signing.keyFile` / `signing.password`）。

### GPG 密钥的两个硬性要求

- **必须用 RSA**：GnuPG 默认的 ECC/ed25519 会生成「签名子密钥」，
  而 Maven Central（Nexus）只能用**主密钥**验签；带签名子密钥会导致发布验签失败
  （官方文档 “Delete a Sub Key” 一节）。已生成的可用 `gpg --edit-key` 删除或撤销该子密钥。
- **公钥必须发布到官方支持的 keyserver**：`keyserver.ubuntu.com` /
  `keys.openpgp.org` / `pgp.mit.edu`（openpgp.org 需先做邮箱验证）。


---

## 5. 本机环境约束（非项目缺陷）

- `dl.google.com` 不可达，依赖走阿里云镜像；
- **AGP 的 lint 工具链（约 100MB）只在 Google Maven 有**，镜像对大文件会
  超时/截断；而 AAR 的 `annotations.zip` 需要它（`extractAndroidMainAnnotations`
  是 `bundleAndroidAar` 的前置，不能用 `-x` 跳过——下游
  `syncAndroidMainLibJars` 依赖它产出的 `typedefs.txt`）。
- 阿里云镜像对部分大 klib 会与 Gradle 的 HTTP 客户端卡死，而 `curl` 与
  Central 直连正常 —— 遇到这种情况优先怀疑镜像，而不是 Gradle 配置。
- **已验证的绕行方法**（iOS 构件发布时实际用到）：用 init script 清空仓库列表，
  换成「阿里云 google 镜像 + `mavenCentral()` 直连」，配合 `--refresh-dependencies`：

  ```kotlin
  settingsEvaluated {
      dependencyResolutionManagement {
          repositories.clear()
          repositories.maven("https://maven.aliyun.com/repository/google")
          repositories.mavenCentral()
      }
  }
  ```

  注意两点：① 只把 `mavenCentral()` **追加**到列表末尾无效 —— 模块元数据一旦由镜像提供，
  Gradle 就把构件也绑定到该仓库，不会再回退；必须清空重排 + `--refresh-dependencies`。
  ② init script 里 `settingsEvaluated { }` **不能带 lambda 参数**，否则脚本编译失败。
