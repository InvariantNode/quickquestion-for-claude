import java.io.File
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Without this Kotlin generates bridges that override experimental platform interface methods.
        jvmDefault.set(org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.NO_COMPATIBILITY)
    }
}

dependencies {
    // The platform does not ship Gson, so bundle it.
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
            untilBuild = provider { null }
        }
    }
    // Secrets come from the environment, never from the repo. See README "Releasing".
    signing {
        certificateChainFile = layout.file(providers.environmentVariable("QQ_CERTIFICATE_CHAIN_FILE").map { File(it) })
        privateKeyFile = layout.file(providers.environmentVariable("QQ_PRIVATE_KEY_FILE").map { File(it) })
        password = providers.environmentVariable("QQ_PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("QQ_PUBLISH_TOKEN")
    }
    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdea, providers.gradleProperty("platformVersion").get())
            create(IntelliJPlatformType.IntellijIdea, providers.gradleProperty("latestPlatformVersion").get())
        }
    }
}
