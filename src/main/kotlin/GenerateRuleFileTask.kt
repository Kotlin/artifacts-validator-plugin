package kotlinx.validation

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.util.SortedSet
import java.util.TreeSet

internal abstract class GenerateRuleFileTask : DefaultTask() {
    @get:InputFiles
    abstract val artifactsFile: RegularFileProperty

    @get:OutputFile
    abstract val rulesFile: RegularFileProperty

    @TaskAction
    fun aggregate() {
        val artifacts = artifactsFile.get().asFile.readLines(Charsets.UTF_8).map { line ->
            ArtifactDescriptor.parse(line.trim())
        }.mapToRules()

        rulesFile.get().asFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            artifacts.forEach { writer.appendLine(it) }
        }
    }

    companion object {
        const val TASK_NAME = "dumpArtifacts"
    }
}

private fun Iterable<ArtifactDescriptor>.mapToRules(): SortedSet<String> =
    groupBy({ "${it.groupId}:${it.artifactId}" }) {
        "${it.classifier}.${it.extension}"
    }.mapTo(TreeSet()) { (gav, artifacts) ->
        val sortedArtifacts = artifacts.sorted().joinToString(",")
        "$gav/$sortedArtifacts"
    }
