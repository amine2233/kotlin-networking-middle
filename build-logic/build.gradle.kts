plugins {
    `kotlin-dsl`
}

val kotlinPlugin =
    libs.plugins.kotlin.multiplatform
        .get()
val androidPlugin =
    libs.plugins.android.kotlin.multiplatform.library
        .get()

dependencies {
    implementation("${kotlinPlugin.pluginId}:${kotlinPlugin.pluginId}.gradle.plugin:${kotlinPlugin.version}")
    implementation("${androidPlugin.pluginId}:${androidPlugin.pluginId}.gradle.plugin:${androidPlugin.version}")
}
