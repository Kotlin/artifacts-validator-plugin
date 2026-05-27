package kotlinx.validation

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Paths
import java.util.TreeSet

/**
 * Checks that all artifacts from [publications] matches expected artifacts
 * described using rules from [artifactRuleFiles], that there are no unexpected
 * artifacts and that there are no missing artifacts.
 *
 * This task checks only the list of artifacts and does not verify any additional attributes.
 */
@DisableCachingByDefault
public abstract class PublicationArtifactsValidationTask : ArtifactsValidationTaskBase() {
    @get:InputFiles
    public abstract val artifactRuleFiles: ConfigurableFileCollection

    @get:Input
    public abstract val publications: ListProperty<PublicationDescriptor>

    @TaskAction
    public fun validate() {
        val dumpFiles = artifactRuleFiles.files.filter { it.exists() }

        val rules = dumpFiles.flatMap(::loadRules)

        compareArtifactsImpl(
            rules.mapTo(TreeSet()) { it.toArtifactIdentifier(null) },
            publications.get().flatMapTo(TreeSet()) { it.toArtifactIdentifiers() }
        )
    }

    public companion object {
        public const val TASK_NAME: String = "checkArtifacts"
    }
}

private fun PublicationDescriptor.toArtifactIdentifiers(): List<String> {
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
