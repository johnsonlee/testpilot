plugins {
    application
}

dependencies {
    implementation(project(":simulator"))
}

application {
    mainClass.set("io.testpilot.demo.MainKt")
}
