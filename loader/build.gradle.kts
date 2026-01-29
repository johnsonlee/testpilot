plugins {
    id("org.jetbrains.kotlin.jvm")
    application
    `maven-publish`
}

application {
    mainClass.set("io.johnsonlee.testpilot.loader.MainKt")
}

dependencies {
    // Simulator module for shim classes
    implementation(project(":simulator"))

    // Android tools for DEX processing
    implementation("com.android.tools.smali:smali-dexlib2:3.0.3")

    // ASM for bytecode manipulation
    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-commons:9.6")
    implementation("org.ow2.asm:asm-util:9.6")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.1")
    testImplementation("org.assertj:assertj-core:3.24.2")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("TestPilot Loader")
                description.set("APK loader with DEX to JVM bytecode conversion")
                url.set("https://github.com/johnsonlee/testpilot")
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
            }
        }
    }
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
