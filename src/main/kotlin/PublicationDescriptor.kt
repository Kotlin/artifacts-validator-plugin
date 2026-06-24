package kotlinx.validation.artifacts

import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal
import java.io.Serializable
import java.nio.file.Paths

/**
 * Describes artifacts from [org.gradle.api.publish.maven.MavenArtifact]s
 * associated with a particular [MavenPublication].
 */
internal class PublicationDescriptor(
    val projectPath: String,
    val groupId: String,
    val artifactId: String,
    val version: String,
    val artifacts: List<ArtifactDescriptor>
) : Serializable {
    class ArtifactDescriptor(
        val classifier: String,
        val extension: String
    ) : Serializable

    internal companion object {
        internal fun from(projectPath: String, mavenPublication: MavenPublication): PublicationDescriptor {
            val artifacts = if (mavenPublication is MavenPublicationInternal) {
                // Internal publication contains all artifacts that are actually published,
                // include pom and module files. MavenPublication.artifacts does not contain them.
                mavenPublication.asNormalisedPublication().allArtifacts
            } else {
                mavenPublication.artifacts
            }
            val artifactDescriptors = artifacts.map {
                ArtifactDescriptor(it.classifier ?: "", it.extension)
            }
            return PublicationDescriptor(
                projectPath,
                mavenPublication.groupId,
                mavenPublication.artifactId,
                mavenPublication.version,
                artifactDescriptors
            )
        }
    }

    fun toArtifactIdentifiers(): List<String> {
        return artifacts.map {
            val info = ArtifactInfo(
                ArtifactInfo.Gav(groupId, artifactId, version),
                Paths.get(""),
                it.extension,
                it.classifier,
                null,
                null,
                version.contains("SNAPSHOT")
            )
            info.toArtifactIdentifier(false)
        }
    }

    fun toRules(): String {
        val ga = "${groupId}:${artifactId}"
        val classifierAndExtension = artifacts
            .map { "${it.classifier}.${it.extension}" }
            .sorted()
            .joinToString(",")
        return "$ga/$classifierAndExtension"
    }
}
