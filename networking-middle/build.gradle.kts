plugins {
    id("networking.kmp-library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka)
}

kotlin {
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
