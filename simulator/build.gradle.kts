dependencies {
    // Skiko for rendering
    implementation(libs.skiko.awt.runtime.linux.x64)

    // Coroutines for async operations
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.assertj.core)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("TestPilot Simulator")
                description.set("Android API shim layer for running APKs on JVM")
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
