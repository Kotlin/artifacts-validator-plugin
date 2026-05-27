package kotlinx.validation

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Dumps [rules](ArtifactRule) describing artifacts from [publications]
 * into either a single [sharedRulesFile] file, or one of the [perProjectRuleFiles],
 * where the project is chosen using [PublicationDescriptor.projectPath].
 *
 * Either [sharedRulesFile] or [perProjectRuleFiles] should be configured,
 * it's an error to configure them both simultaneously.
 */
@DisableCachingByDefault
public abstract class PublicationArtifactsDumpTask : DefaultTask() {
    @get:Input
    public abstract val publications: ListProperty<PublicationDescriptor>

    @get:OutputFile
    @get:Optional
    public abstract val sharedRulesFile: RegularFileProperty

    @get:OutputFiles
    public abstract val perProjectRuleFiles: MapProperty<String, File>

    @TaskAction
    public fun dump() {
        val project2publication = publications.get().groupBy { it.projectPath }

        check(!(sharedRulesFile.isPresent && perProjectRuleFiles.get().isNotEmpty())) {
            "Either sharedRulesFile, or perProjectRuleFiles should be configured, but not both"
        }

        val file2publications = mutableMapOf<File, MutableList<PublicationDescriptor>>()

        for ((project, publications) in project2publication) {
            val file = if (sharedRulesFile.isPresent) {
                sharedRulesFile.get().asFile
            } else {
                val projectDump = perProjectRuleFiles.get()[project]
                check(projectDump != null) {
                    "Dump was not configured for project $project"
                }
                projectDump
            }
            file2publications.getOrPut(file) { mutableListOf() }.addAll(publications)
        }

        for ((dumpFile, publications) in file2publications) {
            dumpFile.bufferedWriter(Charsets.UTF_8).use { writer ->
                publications.map { it.toRules() }.sorted().forEach {
                    writer.appendLine(it)
                }
            }
        }
    }

    public companion object {
        public const val TASK_NAME: String = "dumpArtifacts"
    }
}

private fun PublicationDescriptor.toRules(): String {
    val ga = "${groupId}:${artifactId}"
    val classifierAndExtension = artifacts
        .map { "${it.classifier}.${it.extension}" }
        .sorted()
        .joinToString(",")
    return "$ga/$classifierAndExtension"
}
