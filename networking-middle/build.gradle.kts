plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
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
    api(libs.coroutines.core)
    api(libs.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}
