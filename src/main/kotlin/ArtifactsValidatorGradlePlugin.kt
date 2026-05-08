package kotlinx.validation

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenArtifact
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal
import org.gradle.language.base.plugins.LifecycleBasePlugin
import java.io.File
import java.io.Serializable

private fun Project.applyRecursively(block: Project.() -> Unit) {
    block()
    childProjects.forEach { (_, project) -> project.applyRecursively(block) }
}

public class ArtifactsValidationSettingsPlugin : Plugin<Settings> {
    override fun apply(target: Settings) {
        // Find the root project, create the extension and tasks in it.
        // Then, traverse all the subprojects and configure tasks to validate their Maven artifacts.
        target.gradle.beforeProject { project ->
            if (project.path != ":") return@beforeProject

            val rootDir = project.layout.projectDirectory
            val ext = project.extensions.create(
                "artifactsValidation",
                ArtifactsValidatorPluginSettingsExtension::class.java,
            )
            ext.usePerProjectDumps.convention(false)
            ext.dumpFileNamePrefix.convention("artifacts")
            ext.dumpFileRootDirectory.convention(rootDir.dir("gradle"))

            // A task validating artifacts registered with MavenPublications
            val checkTask = project.tasks.register(
                PublicationArtifactsValidationTask.TASK_NAME,
                PublicationArtifactsValidationTask::class.java
            ) {
                it.group = LifecycleBasePlugin.VERIFICATION_GROUP
                it.description = "Validates the artifacts from configured Maven publications"
            }
            // A task dumping a list of all artifacts that are currently registered with MavenPublications
            // Refer to README or ArtifactRule.kt for details about the expected rule file format.
            val dumpTask = project.tasks.register(
                PublicationArtifactsDumpTask.TASK_NAME,
                PublicationArtifactsDumpTask::class.java
            ) {
                it.group = LifecycleBasePlugin.VERIFICATION_GROUP
                it.description = "Dumps the list of artifacts from configured Maven publications"
            }
            // A CLI task for validating local M2 repo (or a central portal's deployment ZIP)
            project.tasks.register(
                ValidateLocalMavenRepositoryTask.TASK_NAME,
                ValidateLocalMavenRepositoryTask::class.java
            ) {
                it.group = LifecycleBasePlugin.VERIFICATION_GROUP
                it.description = "Validates the artifacts from a standalone local Maven repository"
            }

            project.tasks.configureEach {
                if (it.name == LifecycleBasePlugin.CHECK_TASK_NAME) {
                    it.dependsOn(checkTask)
                }
            }

            // The plugin can either dump/read artifacts to a single file, or a separate per-project files.
            // If artifacts list is stored in a single file, let's configure it now.
            checkTask.configure {
                ext.onSingleDumpFileConfigured(rootDir) { dumpFile ->
                    it.artifactRuleFiles.from(dumpFile)
                }
            }
            dumpTask.configure {
                ext.onSingleDumpFileConfigured(rootDir) { dumpFile ->
                    it.sharedRulesFile.set(dumpFile)
                }
            }

            // Scan all subprojects
            project.applyRecursively {
                pluginManager.withPlugin("maven-publish") {
                    val publishing = extensions.getByType(PublishingExtension::class.java)
                    // Discover all publications and register them in dump and check tasks
                    publishing.publications.withType(MavenPublication::class.java).configureEach { publication ->
                        val descriptor = providers.provider {
                            PublicationDescriptor.from(this@applyRecursively.path, publication)
                        }
                        checkTask.configure { it.publications.add(descriptor) }
                        dumpTask.configure { it.publications.add(descriptor) }
                    }
                }
                // If the (root) project uses per-project artifact lists,
                // we can now resolve and register corresponding files.
                checkTask.configure {
                    ext.onPerProjectDumpFileConfigured(rootDir, this) { dumpFile ->
                        it.artifactRuleFiles.from(dumpFile)
                    }
                }
                dumpTask.configure {
                    ext.onPerProjectDumpFileConfigured(rootDir, this) { dumpFile ->
                        it.perProjectRuleFiles.put(path, dumpFile)
                    }
                }
            }
        }
    }
}

/**
 * Resolves single global artifacts list file if [ArtifactsValidatorPluginSettingsExtension.usePerProjectDumps]
 * is `false` and passes it to the [block]. Otherwise, the [block] will not be invoked.
 */
private fun ArtifactsValidatorPluginSettingsExtension.onSingleDumpFileConfigured(
    projectRootDirectory: Directory,
    block: (File) -> Unit
) {
    val usePerProjectDumpFile = usePerProjectDumps.get()
    if (usePerProjectDumpFile) return

    val dumpFilePrefix = dumpFileNamePrefix.get()
    val defaultDumpFile = dumpFileRootDirectory.file("$dumpFilePrefix.txt").get().asFile
    // Files have to reside withing the root project directory
    checkFileDoesNotEscapeRoot(projectRootDirectory, defaultDumpFile) {
        "Configured artifacts file is located outside of the root project' root directory. " +
                "Check and update dumpFileRootDirectory (\"${dumpFileRootDirectory.get()}\") and " +
                "dumpFileNamePrefix (\"${dumpFileNamePrefix.get()}\") properties to fix this error."
    }
    block(defaultDumpFile)
}

/**
 * Resolves an artifacts list file for [project] if [ArtifactsValidatorPluginSettingsExtension.usePerProjectDumps]
 * is `true` and passes it to the [block]. Otherwise, the [block] will not be invoked.
 */
private fun ArtifactsValidatorPluginSettingsExtension.onPerProjectDumpFileConfigured(
    projectRootDirectory: Directory,
    project: Project,
    block: (File) -> Unit
) {
    val usePerProjectDumpFile = usePerProjectDumps.get()
    if (!usePerProjectDumpFile) return

    val dumpFilePrefix = dumpFileNamePrefix.get()
    val projectDumpFile = dumpFileRootDirectory.file("$dumpFilePrefix-${project.name}.txt").get().asFile
    // Files have to reside withing the root project directory
    checkFileDoesNotEscapeRoot(projectRootDirectory, projectDumpFile) {
        "Configured artifacts file for project \"${project.name}\" (${project.path}) " +
                "is located outside of the root project's root directory. " +
                "Check and update dumpFileRootDirectory (\"${dumpFileRootDirectory.get()}\") and " +
                "dumpFileNamePrefix (\"${dumpFileNamePrefix.get()}\") properties to fix this error."
    }
    block(projectDumpFile)
}

private fun checkFileDoesNotEscapeRoot(projectRootDirectory: Directory, file: File, messageProvider: () -> String) {
    val canonicalRoot = projectRootDirectory.asFile.canonicalFile
    val canonicalFile = file.canonicalFile
    if (!canonicalFile.startsWith(canonicalRoot)) {
        throw GradleException(messageProvider())
    }
}

/**
 * Describes artifacts from [org.gradle.api.publish.maven.MavenArtifact]s
 * associated with a particular [MavenPublication].
 */
public class PublicationDescriptor(
    public val projectPath: String,
    public val groupId: String,
    public val artifactId: String,
    public val version: String,
    public val artifacts: List<ArtifactDescriptor>
) : Serializable {
    public class ArtifactDescriptor(
        public val classifier: String,
        public val extension: String
    ) : Serializable

    internal companion object {
        internal fun from(projectPath: String, mavenPublication: MavenPublication): PublicationDescriptor {
            val artifacts = if (mavenPublication is MavenPublicationInternal) {
                // Internal publication contains all artifacts that are actually published,
                // include pom and module files. MavenPublication.artifacts does not contain them.
                mavenPublication.asNormalisedPublication().allArtifacts
            } else {
                mavenPublication.artifacts
            }
            val artifactDescriptors = artifacts.map {
                ArtifactDescriptor(it.classifier ?: "", it.extension)
            }
            return PublicationDescriptor(
                projectPath,
                mavenPublication.groupId,
                mavenPublication.artifactId,
                mavenPublication.version,
                artifactDescriptors
            )
        }
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
     * and has either `<dumpFileNamePrefix>.txt` name (if [usePerProjectDumps] is `false`),
     * or `<dumpFileNamePrefix>-<Project.name>.txt` name.
     */
    public val dumpFileNamePrefix: Property<String>

    /**
     * Directory where rule files are stored. By default, `<projectRootDir>/gradle`.
     */
    public val dumpFileRootDirectory: DirectoryProperty

    /**
     * Specifies if rules describing artifacts from all sub-projects should be stored in a single file (when `false`).
     * or each project will have its own file. By default, `false`, meaning that all rules for all projects are merged
     * into a single file.
     *
     * See [dumpFileNamePrefix] for information about rule file names.
     */
    public val usePerProjectDumps: Property<Boolean>
}
