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
上传后必需的一次 Portal 调用）见 `docs/publishing.md`。

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
