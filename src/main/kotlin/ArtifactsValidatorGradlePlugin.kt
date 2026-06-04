package kotlinx.validation

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Usage
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.initialization.ProjectDescriptor
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
        val extension = target.registerExtension()

        // Find the root project, create the extension and tasks in it.
        // Then, traverse all the subprojects and configure tasks to validate their Maven artifacts.
        target.gradle.beforeProject { project ->
            if (project.path == ":") {
                project.tasks.register(ValidateLocalMavenRepositoryTask.TASK_NAME, ValidateLocalMavenRepositoryTask::class.java) {
                    it.group = LifecycleBasePlugin.VERIFICATION_GROUP
                    it.description = "Validates the artifacts from a standalone local Maven repository"
                }

                project.configureRootProject(target, extension)
            }
            project.configureAnyProject(extension)
        }
    }
}

private fun Settings.registerExtension(): ArtifactsValidatorPluginSettingsExtension {
    val rootDir = layout.rootDirectory
    val ext = extensions.create(
        "artifactsValidation",
        ArtifactsValidatorPluginSettingsExtension::class.java,
    )
    ext.aggregationEnabled.convention(true)
    ext.dumpFileNamePrefix.convention("artifacts")
    ext.dumpFileRootDirectory.convention(rootDir.dir("gradle"))
    return ext
}

private fun Project.configureRootProject(settings: Settings, extension: ArtifactsValidatorPluginSettingsExtension) {
    if (!extension.aggregationEnabled.get()) {
        return
    }

    val publishedDumpFile = extension.centralizedDumpFile(this)

    val configName = "ARTIFACTS_VALIDATOR_DEPENDENCY"

    val dependencyConfig = configurations.create(configName) { it.asDependency() }
    val rootDependencies = dependencies
    settings.rootProject.applyRecursively {
        rootDependencies.add(configName, project.project(this.path))
    }

    val artifacts = project.configurations.create("artifactsDumpAggregator") {
        it.asConsumer()
        it.attributes {
            it.attribute(
                Usage.USAGE_ATTRIBUTE,
                project.objects.named(UsageAttr::class.java, UsageAttr.VALUE)
            )
            it.attribute(ContentAttr.ATTRIBUTE, ContentAttr.LOCAL_ARTIFACT)
        }
        it.extendsFrom(dependencyConfig)
    }

    tasks.register(GenerateRuleFileTask.TASK_NAME, GenerateRuleFileTask::class.java) {
        it.mergedRulesFile.set(publishedDumpFile)
        it.artifactFiles.from(artifacts)
        it.dependsOn(artifacts)

        it.description = "Collects information about all artifacts associated with project's Maven publications"
        it.group = LifecycleBasePlugin.VERIFICATION_GROUP
    }

    tasks.register(PublicationArtifactsValidationTask.TASK_NAME, PublicationArtifactsValidationTask::class.java) {
        it.artifactRuleFiles.from(publishedDumpFile)
        it.publishedArtifactLists.from(artifacts)
        it.dependsOn(artifacts)

        it.description = "Validate all artifacts associated with project's Maven publications match the expected list of artifacts"
        it.group = LifecycleBasePlugin.VERIFICATION_GROUP
    }
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

    if (!extension.aggregationEnabled.get()) {
        val publishedDumpFile = extension.perProjectDumpFile(this)
        val generatedArtifacts = extractArtifactsTask.flatMap { it.dumpFile }

        tasks.register(GenerateRuleFileTask.TASK_NAME, GenerateRuleFileTask::class.java) {
            it.artifactFiles.from(generatedArtifacts)
            it.mergedRulesFile.set(publishedDumpFile)

            it.description = "Dump list of all artifacts associated with project's Maven publications"
            it.group = LifecycleBasePlugin.VERIFICATION_GROUP
        }

        tasks.register(PublicationArtifactsValidationTask.TASK_NAME, PublicationArtifactsValidationTask::class.java) {
            it.artifactRuleFiles.from(publishedDumpFile)
            it.publishedArtifactLists.from(generatedArtifacts)

            it.description = "Validate all artifacts associated with project's Maven publications match the expected list of artifacts"
            it.group = LifecycleBasePlugin.VERIFICATION_GROUP
        }
    } else {
        configurations.register("artifactsListProducer") {
            it.asProducer()
            it.attributes {
                it.attribute(
                    Usage.USAGE_ATTRIBUTE,
                    project.objects.named(UsageAttr::class.java, UsageAttr.VALUE)
                )
                it.attribute(ContentAttr.ATTRIBUTE, ContentAttr.LOCAL_ARTIFACT)
            }

            it.outgoing.artifact(extractArtifactsTask.flatMap { it.dumpFile }) {
                it.builtBy(extractArtifactsTask)
            }
        }
    }
}

private fun ArtifactsValidatorPluginSettingsExtension.centralizedDumpFile(project: Project): Provider<RegularFile> {
    return dumpFileRootDirectory.zip(dumpFileNamePrefix) { dumpFileRootDirectory, dumpFileNamePrefix ->
        val projectDumpFile = dumpFileRootDirectory.file("$dumpFileNamePrefix.txt")
        checkFileDoesNotEscapeRoot(project.rootProject.rootDir, projectDumpFile) {
            "Configured artifacts file is located outside of the root project' root directory. " +
                    "Check and update dumpFileRootDirectory (\"${dumpFileRootDirectory}\") and " +
                    "dumpFileNamePrefix (\"${dumpFileNamePrefix}\") properties to fix this error."
        }
        projectDumpFile
    }
}

private fun ArtifactsValidatorPluginSettingsExtension.perProjectDumpFile(project: Project): Provider<RegularFile> {
    return dumpFileRootDirectory.zip(dumpFileNamePrefix) { dumpFileRootDirectory, dumpFileNamePrefix ->
        val projectDumpFile = dumpFileRootDirectory.file("$dumpFileNamePrefix-${project.name}.txt")
        checkFileDoesNotEscapeRoot(project.rootProject.rootDir, projectDumpFile) {
            "Configured artifacts file for project \"${project.name}\" (${project.path}) " +
                    "is located outside of the root project's root directory. " +
                    "Check and update dumpFileRootDirectory (\"${dumpFileRootDirectory}\") and " +
                    "dumpFileNamePrefix (\"${dumpFileNamePrefix}\") properties to fix this error."
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
    /**
     * Artifact rules file's name's prefix. By default, `artifacts`.
     *
     * Artifact rules are stored in a [dumpFileRootDirectory] directory,
     * and has either `<dumpFileNamePrefix>.txt` name (if [aggregationEnabled] is `false`),
     * or `<dumpFileNamePrefix>-<Project.name>.txt` name.
     */
    public val dumpFileNamePrefix: Property<String>

    /**
     * Directory where rule files are stored. By default, `<projectRootDir>/gradle`.
     */
    public val dumpFileRootDirectory: DirectoryProperty

    /**
     * When enabled (by default), the plugin registers tasks only for the root project
     * and their execution will aggregate information about artifacts from all subprojects.
     *
     * Otherwise, artifacts dump and validations tasks will be registered for each individual
     * project.
     */
    public val aggregationEnabled: Property<Boolean>

    // TODO: add property to setup excluded projects
}

private fun ProjectDescriptor.applyRecursively(block: ProjectDescriptor.() -> Unit) {
    block()
    children.forEach { project -> project.applyRecursively(block) }
}

private fun Configuration.asDependency() {
    // leave this for compatibility with older versions
    @Suppress("DEPRECATION")
    isVisible = true
    isCanBeResolved = false
    isCanBeConsumed = false
}

private fun Configuration.asConsumer() {
    // leave this for compatibility with older versions
    @Suppress("DEPRECATION")
    isVisible = false
    isCanBeResolved = true
    // this config consumes modules from OTHER projects, and cannot be consumed by other projects
    isCanBeConsumed = false
}

private fun Configuration.asProducer() {
    // leave this for compatibility with older versions
    @Suppress("DEPRECATION")
    isVisible = false
    isCanBeResolved = false
    // this configuration produces modules that can be consumed by other projects
    isCanBeConsumed = true
}

private interface ContentAttr {
    companion object {
        val ATTRIBUTE = Attribute.of(
            "kotlinx.artifacts.validator.content.type",
            String::class.java
        )

        const val LOCAL_ARTIFACT = "localArtifact"
    }
}

private interface UsageAttr : Usage {
    companion object {
        const val VALUE = "artifacts.validator"
    }
}
