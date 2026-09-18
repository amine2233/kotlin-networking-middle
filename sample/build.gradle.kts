plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("sample.MainKt")
}

dependencies {
    implementation(project(":networking-middle-ktor"))
    implementation(libs.ktor.client.cio)
}
