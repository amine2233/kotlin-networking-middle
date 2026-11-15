rootProject.name = "kmp-consumer"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository { maven(file("../build/local-maven-repo")) }
            filter { includeGroup("io.github.amine2233") }
        }
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
