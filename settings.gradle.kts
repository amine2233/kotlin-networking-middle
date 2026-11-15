rootProject.name = "kotlin-networking-middle"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

include(":networking-middle", ":networking-middle-ktor", ":networking-middle-apple", ":sample", ":android-consumer")
