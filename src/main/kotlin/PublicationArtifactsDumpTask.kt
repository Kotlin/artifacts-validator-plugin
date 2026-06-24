package kotlinx.validation.artifacts

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.*
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault

/**
 * Dumps [rules](ArtifactRule) describing artifacts from [publications] into either a [dumpFile] file.
 */
@DisableCachingByDefault
internal abstract class PublicationArtifactsDumpTask : DefaultTask() {
    @get:Input
    abstract val publications: ListProperty<PublicationDescriptor>

    @get:OutputFile
    abstract val dumpFile: RegularFileProperty

    @TaskAction
    fun dump() {
        val publications = publications.get()
        dumpFile.get().asFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            publications.map { it.toRules() }.sorted().forEach {
                writer.appendLine(it)
            }
        }
    }

    companion object {
        const val TASK_NAME: String = "dumpArtifacts"
    }
}
