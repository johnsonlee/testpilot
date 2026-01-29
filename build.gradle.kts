import java.net.URL

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

tasks.register("downloadTestFixtures") {
    group = "verification"
    description = "Downloads test APK files for integration testing"

    val testFixturesDir = file("test-fixtures")
    val apkFile = file("test-fixtures/simple-app.apk")
    val apkUrl = "https://f-droid.org/repo/com.simplemobiletools.calculator_50.apk"

    doLast {
        if (!testFixturesDir.exists()) {
            testFixturesDir.mkdirs()
        }
        if (!apkFile.exists()) {
            println("Downloading test APK from F-Droid...")
            URL(apkUrl).openStream().use { input ->
                apkFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            println("Downloaded: ${apkFile.absolutePath}")
        } else {
            println("Test APK already exists: ${apkFile.absolutePath}")
        }
    }
}

allprojects {
    group = "io.johnsonlee.testpilot"
    version = System.getenv("VERSION")?.removePrefix("v") ?: "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(21)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    if (name != "demo") {
        apply(plugin = "maven-publish")

        configure<PublishingExtension> {
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/johnsonlee/testpilot")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR")
                        password = System.getenv("GITHUB_TOKEN")
                    }
                }
            }
        }
    }
}
