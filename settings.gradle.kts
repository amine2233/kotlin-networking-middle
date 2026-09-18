rootProject.name = "kotlin-networking-middle"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":networking-middle", ":networking-middle-ktor", ":sample")
