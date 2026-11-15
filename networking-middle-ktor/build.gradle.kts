plugins {
    id("networking.kmp-library")
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()

    android {
        namespace = "io.github.amine2233.networkingmiddle.ktor"
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":networking-middle"))
            api(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("NetworkingMiddleKtor")
            description.set("Ktor HttpClient adapter for networking-middle.")
        }
    }
}
