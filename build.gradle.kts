plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka)
}

allprojects {
    group = "io.github.amine2233"
    version = (findProperty("version") as String?)?.takeIf { it.isNotBlank() && it != "unspecified" } ?: "0.1.0-SNAPSHOT"
}

dependencies {
    kover(project(":networking-middle"))
    kover(project(":networking-middle-ktor"))
    dokka(project(":networking-middle"))
    dokka(project(":networking-middle-ktor"))
}

kover {
    reports {
        verify {
            rule {
                bound {
                    minValue = 93
                    coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE
                }
            }
        }
    }
}
