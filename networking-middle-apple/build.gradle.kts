import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    explicitApi()

    val xcFramework = XCFramework("NetworkingMiddleKotlin")
    listOf(iosArm64(), iosSimulatorArm64(), macosArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "NetworkingMiddleKotlin"
            isStatic = true
            export(project(":networking-middle"))
            export(project(":networking-middle-ktor"))
            xcFramework.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":networking-middle"))
            api(project(":networking-middle-ktor"))
        }
        appleMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}
