plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

val networkingMiddleVersion =
    providers.gradleProperty("networkingMiddleVersion").orNull
        ?: throw GradleException("Pass the published version with -PnetworkingMiddleVersion=<version>")

kotlin {
    jvm()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation("io.github.amine2233:networking-middle:$networkingMiddleVersion")
            implementation("io.github.amine2233:networking-middle-ktor:$networkingMiddleVersion")
        }
    }
}
