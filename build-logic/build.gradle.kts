plugins {
    `kotlin-dsl`
}

val kotlinPlugin =
    libs.plugins.kotlin.multiplatform
        .get()

dependencies {
    implementation("${kotlinPlugin.pluginId}:${kotlinPlugin.pluginId}.gradle.plugin:${kotlinPlugin.version}")
}
