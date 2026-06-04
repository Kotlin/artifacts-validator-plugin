package kotlinx.validation

import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal
import java.io.Serializable

/**
 * Describes artifacts from [org.gradle.api.publish.maven.MavenArtifact]s
 * associated with a particular [MavenPublication].
 */
internal class ArtifactDescriptor(
    public val projectPath: String,
    public val groupId: String,
    public val artifactId: String,
    public val version: String,
    public val classifier: String,
    public val extension: String
) : Serializable {
    internal companion object {
        private const val DELIMITER = "\u00b6" // ¶

        fun parse(line: String): ArtifactDescriptor {
            val parts = line.split(DELIMITER)
            require(parts.size == 6) {
                "Wrong artifact descriptor format, 6 parts delimited by $DELIMITER are expected:\n$line"
            }
            return ArtifactDescriptor(
                parts[0],
                parts[1],
                parts[2],
                parts[3],
                parts[4],
                parts[5]
            )
        }
    }

    internal fun serializeTo(to: Appendable) {
        to.appendLine(
            "$projectPath$DELIMITER" +
                    "$groupId$DELIMITER" +
                    "$artifactId$DELIMITER" +
                    "$version$DELIMITER" +
                    "$classifier$DELIMITER" +
                    "$extension"
        )
    }
}

internal fun MavenPublication.toArtifactDescriptors(projectPath: String): List<ArtifactDescriptor> {
    val artifacts = if (this is MavenPublicationInternal) {
        // Internal publication contains all artifacts that are actually published,
        // include pom and module files. MavenPublication.artifacts does not contain them.
        this.asNormalisedPublication().allArtifacts
    } else {
        this.artifacts
    }
    return artifacts.map {
        ArtifactDescriptor(projectPath, groupId, artifactId, version, it.classifier ?: "", it.extension)
    }
}
