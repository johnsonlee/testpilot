plugins {
    application
}

application {
    mainClass.set("io.johnsonlee.testpilot.simulator.MainKt")
}

dependencies {
    // Renderer module for layoutlib-based screenshots
    implementation(project(":renderer"))

    // Android tools for DEX processing
    implementation(libs.dexlib2)

    // ASM for DEX-to-JVM bytecode conversion (used by DexConverter)
    implementation(libs.asm)
    implementation(libs.asm.commons)

    // Testing
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("TestPilot Simulator")
                description.set("Android APK simulator with DEX conversion and layoutlib integration")
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
}
