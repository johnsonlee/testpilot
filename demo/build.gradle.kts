plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":simulator"))
}

application {
    mainClass.set("io.testpilot.demo.MainKt")
}

kotlin {
    jvmToolchain(17)
}
