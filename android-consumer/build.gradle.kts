plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.amine2233.networkingmiddle.androidconsumer"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":networking-middle-ktor"))
}

abstract class VerifyAndroidVariants : DefaultTask() {
    @get:Input
    abstract val rootComponent: Property<ResolvedComponentResult>

    @TaskAction
    fun verify() {
        val variants = mutableMapOf<String, String>()
        val pending = ArrayDeque(listOf(rootComponent.get()))
        val visited = mutableSetOf<ComponentIdentifier>()
        while (pending.isNotEmpty()) {
            val component = pending.removeFirst()
            if (!visited.add(component.id)) continue
            component.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { dependency ->
                val id = dependency.selected.id
                if (id is ProjectComponentIdentifier) {
                    variants[id.projectPath] = dependency.resolvedVariant.displayName
                }
                pending.add(dependency.selected)
            }
        }
        listOf(":networking-middle", ":networking-middle-ktor").forEach { path ->
            val variant = variants[path] ?: throw GradleException("$path is not on releaseRuntimeClasspath")
            if (!variant.contains("android", ignoreCase = true) || variant.startsWith("jvm")) {
                throw GradleException("$path resolved to variant '$variant', expected an Android variant")
            }
            println("$path -> $variant")
        }
    }
}

val verifyAndroidVariants =
    tasks.register<VerifyAndroidVariants>("verifyAndroidVariants") {
        rootComponent.set(
            configurations
                .named("releaseRuntimeClasspath")
                .flatMap { it.incoming.resolutionResult.rootComponent },
        )
    }

tasks.named("check") { dependsOn(verifyAndroidVariants) }
