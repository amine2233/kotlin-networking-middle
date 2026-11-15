import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
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
    android {
        compileSdk = 36
        minSdk = 24
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs.add("-Xjdk-release=11")
        }
        withHostTest { }
    }
}

configurations.matching { it.name == "jvmApiElements" || it.name == "jvmRuntimeElements" }.configureEach {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 11)
}

val localVerifyRepository = rootProject.layout.buildDirectory.dir("local-maven-repo")

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
        maven {
            name = "LocalVerify"
            url = uri(localVerifyRepository.get().asFile)
        }
    }
}

tasks.register<VerifyLocalPublication>("verifyLocalPublication") {
    description = "Checks the artifacts and Gradle module metadata published to build/local-maven-repo."
    repositoryDirectory.set(localVerifyRepository)
    publicationGroup.set(provider { project.group.toString() })
    publicationVersion.set(provider { project.version.toString() })
    rootArtifactId.set(publishing.publications.named<MavenPublication>("kotlinMultiplatform").map { it.artifactId })
    artifactIds.set(provider { publishing.publications.withType<MavenPublication>().map { it.artifactId } })
    mustRunAfter("publishAllPublicationsToLocalVerifyRepository")
}
