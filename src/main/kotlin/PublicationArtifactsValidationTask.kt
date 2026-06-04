package kotlinx.validation

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.nio.file.Paths
import java.util.*

/**
 * Checks that all artifacts from [publications] matches expected artifacts
 * described using rules from [artifactRuleFiles], that there are no unexpected
 * artifacts and that there are no missing artifacts.
 *
 * This task checks only the list of artifacts and does not verify any additional attributes.
 */
@DisableCachingByDefault
internal abstract class PublicationArtifactsValidationTask : ArtifactsValidationTaskBase() {
    @get:InputFiles
    public abstract val artifactRuleFiles: ConfigurableFileCollection

    @get:Input
    public abstract val publications: ListProperty<PublicationDescriptor>

    @TaskAction
    public fun validate() {
        val dumpFiles = artifactRuleFiles.files.filter { it.exists() }

        val rules = dumpFiles.flatMap(::loadRules)

        val artifacts = publications.get().flatMapTo(TreeSet()) { it.toArtifactIdentifiers() }

        compareArtifactsImpl(
            rules.mapTo(TreeSet()) { it.toArtifactIdentifier(null) },
            artifacts
        )
    }

    public companion object {
        public const val TASK_NAME: String = "checkArtifacts"
    }
}
