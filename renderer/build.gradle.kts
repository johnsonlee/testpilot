import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.util.zip.ZipInputStream

plugins {
    id("org.jetbrains.kotlin.jvm")
    `maven-publish`
}

// Layoutlib native runtime classifier based on OS
val nativeClassifier: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    when {
        os.contains("mac") && arch.contains("aarch64") -> "mac-arm"
        os.contains("mac") -> "mac"
        os.contains("win") -> "win"
        else -> "linux"
    }
}

// Register JAR to directory transform
@CacheableTransform
abstract class UnzipTransform : TransformAction<TransformParameters.None> {
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val inputFile = inputArtifact.get().asFile
        val outputDir = outputs.dir(inputFile.nameWithoutExtension)

        ZipInputStream(inputFile.inputStream().buffered()).use { zis ->
            generateSequence { zis.nextEntry }
                .filterNot { it.isDirectory }
                .forEach { entry ->
                    val outFile = outputDir.resolve(entry.name)
                    outFile.parentFile.mkdirs()
                    outFile.outputStream().buffered().use { out ->
                        zis.copyTo(out)
                    }
                }
        }
    }
}

val DIRECTORY_TYPE = Attribute.of("artifactType", String::class.java)

dependencies {
    registerTransform(UnzipTransform::class) {
        from.attribute(ARTIFACT_TYPE_ATTRIBUTE, "jar")
        to.attribute(ARTIFACT_TYPE_ATTRIBUTE, "directory")
    }
}

// Layoutlib runtime configuration (native libs)
val layoutlibRuntime: Configuration by configurations.creating {
    isTransitive = false
    attributes {
        attribute(ARTIFACT_TYPE_ATTRIBUTE, "directory")
    }
}

// Layoutlib resources configuration (fonts, icu, etc.)
val layoutlibResources: Configuration by configurations.creating {
    isTransitive = false
    attributes {
        attribute(ARTIFACT_TYPE_ATTRIBUTE, "directory")
    }
}

dependencies {
    layoutlibRuntime(variantOf(libs.android.tools.layoutlib.runtime) { classifier(nativeClassifier) })
    layoutlibResources(libs.android.tools.layoutlib.resources)
}

// Helper to get directory from configuration
fun Configuration.asDirectory(): File {
    return incoming.artifactView {
        attributes { attribute(ARTIFACT_TYPE_ATTRIBUTE, "directory") }
    }.files.singleFile
}

dependencies {
    // Kotlin
    api(kotlin("stdlib"))
    api(kotlin("reflect"))

    // Android Tools / Layoutlib
    api(libs.android.tools.layoutlib)
    api(libs.android.tools.layoutlib.api)
    api(libs.android.tools.sdk.common)
    api(libs.android.tools.common)
    api(libs.android.tools.build.aapt2.proto)
    api(libs.android.tools.external.intellij.core)

    // AndroidX
    api(libs.androidx.lifecycle.common.java8)

    // XML parsing
    api(libs.kxml2)

    // JSON
    api(libs.jackson.databind)

    // Protobuf
    api(libs.protobuf)

    // Logging
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.simple)

    // IO
    implementation(libs.okio)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.assertj.core)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()

    // Pass layoutlib paths to tests
    doFirst {
        systemProperty("testpilot.layoutlib.runtime", layoutlibRuntime.asDirectory().absolutePath)
        systemProperty("testpilot.layoutlib.resources", layoutlibResources.asDirectory().absolutePath)
    }

    // Pass through testpilot.record system property for golden image recording
    systemProperty("testpilot.record", System.getProperty("testpilot.record") ?: "false")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("TestPilot Renderer")
                description.set("Layoutlib-based rendering for TestPilot")
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
