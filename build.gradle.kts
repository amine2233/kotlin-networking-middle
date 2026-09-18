plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

subprojects {
    group = "io.github.amine2233"
    version = "0.1.0"
}
