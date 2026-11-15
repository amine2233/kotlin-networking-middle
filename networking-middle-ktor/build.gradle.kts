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

// Apple publishing is turned on by item 14 (issue #14).
tasks.withType<AbstractPublishToMaven>().configureEach {
    val appleTargets = setOf("iosArm64", "iosSimulatorArm64", "macosArm64")
    if (publication?.name in appleTargets) {
        enabled = false
    }
}
