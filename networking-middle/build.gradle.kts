plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka)
    `java-library`
    `maven-publish`
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    explicitApi()
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        freeCompilerArgs.add("-Xjdk-release=11")
    }
}

dependencies {
    api(libs.coroutines.core)
    api(libs.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("gpr") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = "networking-middle"
            version = project.version.toString()
            pom {
                name.set("NetworkingMiddle")
                description.set(
                    "Chain-of-responsibility networking layer with an injected HTTP engine (Kotlin port of the Swift NetworkingMiddle package).",
                )
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
