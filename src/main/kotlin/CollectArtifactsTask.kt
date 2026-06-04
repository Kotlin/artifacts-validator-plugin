package kotlinx.validation

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.*
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault

/**
 * Dumps [rules](ArtifactRule) describing artifacts from [artifacts]
 * into either a single [sharedRulesFile] file, or one of the [perProjectRuleFiles],
 * where the project is chosen using [PublicationDescriptor.projectPath].
 *
 * Either [sharedRulesFile] or [perProjectRuleFiles] should be configured,
 * it's an error to configure them both simultaneously.
 */
@DisableCachingByDefault
internal abstract class CollectArtifactsTask : DefaultTask() {
    @get:Input
    public abstract val artifacts: ListProperty<ArtifactDescriptor>

    @get:OutputFile
    public abstract val dumpFile: RegularFileProperty

    @TaskAction
    public fun dump() {
        val artifacts = artifacts.get()
        dumpFile.get().asFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            artifacts.forEach { it.serializeTo(writer) }
        }
    }

    public companion object {
        public const val TASK_NAME: String = "collectArtifacts"
    }
}
