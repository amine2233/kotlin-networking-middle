plugins {
    id("networking.kmp-library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()

    sourceSets {
        commonMain.dependencies {
            api(libs.coroutines.core)
            api(libs.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("NetworkingMiddle")
            description.set(
                "Chain-of-responsibility networking layer with an injected HTTP engine (Kotlin port of the Swift NetworkingMiddle package).",
            )
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
