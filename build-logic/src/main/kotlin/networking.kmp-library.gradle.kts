import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    `maven-publish`
}

kotlin {
    explicitApi()
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs.add("-Xjdk-release=11")
        }
        testRuns.named("test") {
            executionTask.configure { useJUnitPlatform() }
        }
    }
}

configurations.matching { it.name == "jvmApiElements" || it.name == "jvmRuntimeElements" }.configureEach {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 11)
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            url.set("https://github.com/amine2233/kotlin-networking-middle")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
            developers {
                developer {
                    id.set("amine2233")
                    name.set("Amine Bensalah")
                }
            }
            scm { url.set("https://github.com/amine2233/kotlin-networking-middle") }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/amine2233/kotlin-networking-middle")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
