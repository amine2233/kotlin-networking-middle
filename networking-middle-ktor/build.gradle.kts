plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        freeCompilerArgs.add("-Xjdk-release=11")
    }
}

dependencies {
    api(project(":networking-middle"))
    api(libs.ktor.client.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}

tasks.test {
    useJUnitPlatform()
}
