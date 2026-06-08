import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // 版本化平台依赖(从 JetBrains 仓库下载),保证任意机器/CI 可复现构建。
        // 选 2023.3 作为最低兼容版本,对齐 sinceBuild=233。
        create(IntelliJPlatformType.IntellijIdeaCommunity, "2023.3")
        // 复用 IDE 内置 Git 插件提供的 VCS API(Change / ChangesUtil 等)
        bundledPlugin("Git4Idea")
    }
}

intellijPlatform {
    // 纯 Kotlin 插件,无 Java form / @NotNull 字节码增强,关闭 instrumentCode
    instrumentCode = false
    pluginConfiguration {
        ideaVersion {
            // 对着 2023.3 平台编译,声明 233+ 兼容
            sinceBuild = "233"
            // 留空表示不限制上限,兼容未来版本
            untilBuild = provider { null }
        }
    }
    // 一键发版:token 从环境变量 PUBLISH_TOKEN 读取(切勿写进代码)
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        // 本机 IDEA 261 平台用 Kotlin 2.3 编译,放宽元数据版本校验以便用 1.9 编译器引用
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

tasks {
    // 预索引设置项的任务需启动 headless IDE,易受本机环境(已运行的沙箱 IDE / JDK 版本)干扰。
    // 禁用它不影响设置页在运行时被搜索到。
    buildSearchableOptions {
        enabled = false
    }
}
