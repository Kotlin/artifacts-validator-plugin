package kotlinx.validation

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import java.io.File
import java.util.SortedSet

public abstract class ArtifactsValidationTaskBase : DefaultTask() {
    private val logMessagePrefix = "[Artifacts Validation] "
    internal fun error(message: String) = logger.error("$logMessagePrefix$message")
    internal fun warn(message: String) = logger.warn("$logMessagePrefix$message")
    internal fun debug(message: String) = logger.debug("$logMessagePrefix$message")
    internal fun lifecycle(message: String) = logger.lifecycle("$logMessagePrefix$message")

    internal fun compareArtifactsImpl(expectedArtifacts: SortedSet<String>, actualArtifacts: SortedSet<String>) {
        if (expectedArtifacts == actualArtifacts) {
            lifecycle("Artifacts fully matched the list of expected artifacts.")
            return
        }

        val missingArtifacts = expectedArtifacts.subtract(actualArtifacts)
        if (missingArtifacts.isNotEmpty()) {
            error(
                "Following artifacts were expected, but were not found: "
                        + missingArtifacts.joinToString(", ")
            )
        }
        val extraArtifacts = actualArtifacts.subtract(expectedArtifacts)
        if (extraArtifacts.isNotEmpty()) {
            error(
                "Following artifacts were not expected, but were found: "
                        + extraArtifacts.joinToString(", ")
            )
        }

        throw GradleException(
            "List of found artifacts does not match list of expected artifacts. See log for more details. " +
                    "To generate or update files describing artifacts, run the '${PublicationArtifactsDumpTask.TASK_NAME}' task."
        )
    }

    internal fun loadRules(file: File): List<ArtifactRule> {
        debug("Loading artifact rules from $file")
        return file.readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("//") }
            .flatMap {
                try {
                    ArtifactRule.parseRule(it)
                } catch (e: IllegalArgumentException) {
                    throw GradleException("Error while parsing rules file $file: ${e.message}")
                }
            }
    }
}
