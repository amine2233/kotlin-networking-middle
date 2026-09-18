plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("sample.MainKt")
}

dependencies {
    implementation(project(":networking-middle-ktor"))
    implementation(libs.ktor.client.cio)
}
