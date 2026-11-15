import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class VerifyLocalPublication : DefaultTask() {
    @get:Internal
    abstract val repositoryDirectory: DirectoryProperty

    @get:Input
    abstract val publicationGroup: Property<String>

    @get:Input
    abstract val publicationVersion: Property<String>

    @get:Input
    abstract val rootArtifactId: Property<String>

    @get:Input
    abstract val artifactIds: ListProperty<String>

    @TaskAction
    fun verify() {
        val version = publicationVersion.get()
        val rootArtifactId = rootArtifactId.get()
        val groupDirectory = repositoryDirectory.get().asFile.resolve(publicationGroup.get().replace('.', '/'))
        val artifactIds = artifactIds.get().toSet()
        if (rootArtifactId !in artifactIds) {
            throw GradleException("No publication has the root artifact id $rootArtifactId")
        }

        artifactIds.sorted().forEach { artifactId ->
            val versionDirectory = groupDirectory.resolve("$artifactId/$version")
            listOf("module", "pom").forEach { extension ->
                val file = versionDirectory.resolve("$artifactId-$version.$extension")
                if (!file.isFile) {
                    throw GradleException("$artifactId: missing ${file.path}")
                }
            }
        }

        val rootModule = groupDirectory.resolve("$rootArtifactId/$version/$rootArtifactId-$version.module")
        val availableAt =
            parseVariants(rootModule).mapNotNull { variant ->
                @Suppress("UNCHECKED_CAST")
                variant["available-at"] as Map<String, Any?>?
            }
        val referencedIds = availableAt.map { it["module"].toString() }.toSet()
        val targetIds = artifactIds - rootArtifactId
        val missing = targetIds - referencedIds
        val unexpected = referencedIds - targetIds
        if (missing.isNotEmpty() || unexpected.isNotEmpty()) {
            throw GradleException(
                "$rootArtifactId: ${rootModule.name} available-at modules do not match the publications " +
                    "(missing: ${missing.sorted()}, unexpected: ${unexpected.sorted()})",
            )
        }
        availableAt.forEach { entry ->
            val module = entry["module"]
            if (entry["version"] != version) {
                throw GradleException("$rootArtifactId: available-at $module has version ${entry["version"]}, expected $version")
            }
            val url = entry["url"] as String? ?: throw GradleException("$rootArtifactId: available-at $module has no url")
            val target = rootModule.parentFile.resolve(url).normalize()
            if (!target.isFile) {
                throw GradleException("$rootArtifactId: available-at $module url $url points at missing ${target.path}")
            }
        }
        logger.lifecycle("$rootArtifactId $version: ${artifactIds.size} publications and ${rootModule.name} are consistent")
    }

    private fun parseVariants(rootModule: File): List<Map<String, Any?>> {
        @Suppress("UNCHECKED_CAST")
        val json = JsonSlurper().parse(rootModule) as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        return json["variants"] as List<Map<String, Any?>>? ?: emptyList()
    }
}
