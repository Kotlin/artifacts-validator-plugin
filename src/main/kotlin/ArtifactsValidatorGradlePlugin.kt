package kotlinx.validation

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.language.base.plugins.LifecycleBasePlugin
import java.io.File

private fun Project.applyRecursively(block: Project.() -> Unit) {
    block()
    childProjects.forEach { (_, project) -> project.applyRecursively(block) }
}

public class ArtifactsValidationSettingsPlugin : Plugin<Settings> {
    override fun apply(target: Settings) {
        // Find the root project, create the extension and tasks in it.
        // Then, traverse all the subprojects and configure tasks to validate their Maven artifacts.
        target.gradle.beforeProject { project ->
            if (project.path == ":") {
                project.registerExtension()
                project.tasks.register(
                    ValidateLocalMavenRepositoryTask.TASK_NAME,
                    ValidateLocalMavenRepositoryTask::class.java
                ) {
                    it.group = LifecycleBasePlugin.VERIFICATION_GROUP
                    it.description = "Validates the artifacts from a standalone local Maven repository"
                }
            }
            project.configureAnyProject(
                project.rootProject.extensions.getByType(
                    ArtifactsValidatorPluginSettingsExtension::class.java
                )
            )
        }
    }
}

private fun Project.registerExtension(): ArtifactsValidatorPluginSettingsExtension {
    val rootDir = project.layout.projectDirectory
    val ext = extensions.create(
        "artifactsValidation",
        ArtifactsValidatorPluginSettingsExtension::class.java,
    )
    ext.dumpFileRootDirectory.convention(rootDir.dir("artifacts"))
    return ext
}

private fun Project.configureAnyProject(extension: ArtifactsValidatorPluginSettingsExtension) {
    val extractArtifactsTask = tasks.register(CollectArtifactsTask.TASK_NAME, CollectArtifactsTask::class.java) {
        it.dumpFile.set(layout.buildDirectory.file("artifacts/dump.txt"))

        it.description = "Collects information about all artifacts associated with project's Maven publications"
        it.group = LifecycleBasePlugin.VERIFICATION_GROUP
    }

    project.pluginManager.withPlugin("maven-publish") {
        val publishing = project.extensions.getByType(PublishingExtension::class.java)
        // Discover all publications and register them in dump and check tasks
        publishing.publications.withType(MavenPublication::class.java).configureEach { publication ->
            val descriptors = project.providers.provider {
                publication.toArtifactDescriptors(project.path)
            }
            extractArtifactsTask.configure { it.artifacts.addAll(descriptors) }
        }
    }

    val publishedDumpFile = extension.perProjectDumpFile(this)
    val generatedArtifacts = extractArtifactsTask.flatMap { it.dumpFile }

    tasks.register(GenerateRuleFileTask.TASK_NAME, GenerateRuleFileTask::class.java) {
        it.artifactsFile.set(generatedArtifacts)
        it.rulesFile.set(publishedDumpFile)

        it.description = "Dump list of all artifacts associated with project's Maven publications"
        it.group = LifecycleBasePlugin.VERIFICATION_GROUP
    }

    val checkTask = tasks.register(PublicationArtifactsValidationTask.TASK_NAME, PublicationArtifactsValidationTask::class.java) {
        it.artifactRuleFiles.from(publishedDumpFile)
        it.publishedArtifactLists.from(generatedArtifacts)

        it.description =
            "Validate all artifacts associated with project's Maven publications match the expected list of artifacts"
        it.group = LifecycleBasePlugin.VERIFICATION_GROUP
    }
    tasks.matching {
        it.name == LifecycleBasePlugin.CHECK_TASK_NAME
    }.configureEach {
        it.dependsOn(checkTask)
    }
}

private fun ArtifactsValidatorPluginSettingsExtension.perProjectDumpFile(project: Project): Provider<RegularFile> {
    return dumpFileRootDirectory.map { dumpFileRootDirectory ->
        val projectDumpFile = dumpFileRootDirectory.file("${project.name}.txt")
        checkFileDoesNotEscapeRoot(project.rootProject.rootDir, projectDumpFile) {
            "Configured artifacts file for project \"${project.name}\" (${project.path}) " +
                    "is located outside of the root project's root directory. " +
                    "Check and update dumpFileRootDirectory (\"${dumpFileRootDirectory}\") to fix this error."
        }
        projectDumpFile
    }
}

private fun checkFileDoesNotEscapeRoot(projectRootDirectory: File, file: RegularFile, messageProvider: () -> String) {
    val canonicalRoot = projectRootDirectory.canonicalFile
    val canonicalFile = file.asFile.canonicalFile
    if (!canonicalFile.startsWith(canonicalRoot)) {
        throw GradleException(messageProvider())
    }
}

/**
 * Configures artifact validator plugin.
 */
public interface ArtifactsValidatorPluginSettingsExtension {
    // TODO: Project names can duplicate, let's figure out what to do when it will become a problem.
    //       We can support mapping a Project to a desired filename, for example.
    // TODO: add property to setup excluded projects

    /**
     * Directory where rule files are stored. By default, `<projectRootDir>/artifacts`.
     */
    public val dumpFileRootDirectory: DirectoryProperty

}
