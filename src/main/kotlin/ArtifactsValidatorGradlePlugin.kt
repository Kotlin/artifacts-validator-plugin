package kotlinx.validation

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.language.base.plugins.LifecycleBasePlugin
import java.io.Serializable

private fun Project.applyRecursively(block: Project.() -> Unit) {
    block()
    childProjects.forEach { (_, project) -> project.applyRecursively(block) }
}

public class ArtifactsValidationSettingsPlugin : Plugin<Settings> {
    public companion object {
        public const val CHECK_ARTIFACTS_TASK_NAME: String = "checkArtifacts"
        public const val DUMP_ARTIFACTS_TASK_NAME: String = "dumpArtifacts"
        public const val VALIDATE_LOCAL_MAVEN_REPO_TASK_NAME: String = "validateLocalMavenRepo"
    }

    @Suppress("UnstableApiUsage")
    override fun apply(target: Settings) {
        val ext = target.extensions.create(
            "artifactsValidation",
            ArtifactsValidatorPluginSettingsExtension::class.java,
        )

        val rootDirPath = target.rootDir.toPath()

        ext.usePerProjectDumps.convention(false)
        ext.dumpFileNamePrefix.convention("artifacts")
        ext.dumpFileRootDirectory.convention(target.layout.rootDirectory.dir("gradle"))

        target.gradle.beforeProject { project ->
            if (project.path != ":") return@beforeProject

            val checkTask = project.tasks.register(CHECK_ARTIFACTS_TASK_NAME, PublicationArtifactsValidationTask::class.java)
            val dumpTask = project.tasks.register(DUMP_ARTIFACTS_TASK_NAME, PublicationArtifactsDumpTask::class.java)
            project.tasks.register(VALIDATE_LOCAL_MAVEN_REPO_TASK_NAME, ValidateLocalMavenRepositoryTask::class.java)

            project.tasks.configureEach {
                if (it.name == LifecycleBasePlugin.CHECK_TASK_NAME) {
                    it.dependsOn(checkTask)
                }
            }

            val dumpFilePrefix = ext.dumpFileNamePrefix.get()
            val usePerProjectDumpFile = ext.usePerProjectDumps.get()
            if (!usePerProjectDumpFile) {
                val defaultDumpFile = ext.dumpFileRootDirectory.file("$dumpFilePrefix.txt").get().asFile
                if (!defaultDumpFile.toPath().normalize().startsWith(rootDirPath)) {
                    throw GradleException("Artifacts dump file must be located within the project directory: $defaultDumpFile")
                }
                checkTask.configure { it.artifactRuleFiles.from(defaultDumpFile) }
                dumpTask.configure { it.sharedRulesFile.set(defaultDumpFile) }
            }

            project.applyRecursively {
                pluginManager.withPlugin("maven-publish") {
                    val publishing = extensions.getByType(PublishingExtension::class.java)
                    publishing.publications.withType(MavenPublication::class.java).configureEach { publication ->
                        val descriptor = providers.provider {
                            PublicationDescriptor.from(this@applyRecursively.path, publication)
                        }
                        checkTask.configure { it.addPublicationProvider(descriptor) }
                        dumpTask.configure { it.addPublicationProvider(descriptor) }
                    }
                }
                if (usePerProjectDumpFile) {
                    val projectDumpFile = ext.dumpFileRootDirectory.file("$dumpFilePrefix-$name.txt").get().asFile
                    checkTask.configure { it.artifactRuleFiles.from(projectDumpFile) }
                    dumpTask.configure { it.perProjectRuleFiles.put(path, projectDumpFile) }
                }
            }
        }
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
        // MavenPublication does not list pom file as an artifact,
        // but we need it for several reasons:
        // - it is still there in the repo, anyway
        // - if artifact consists of a pom file only, we need to track it
        private val POM_ARTIFACT = ArtifactDescriptor("", "pom")
        internal fun from(projectPath: String, mavenPublication: MavenPublication): PublicationDescriptor {
            val artifacts = mavenPublication.artifacts.map {
                ArtifactDescriptor(it.classifier ?: "", it.extension)
            } + POM_ARTIFACT
            return PublicationDescriptor(
                projectPath,
                mavenPublication.groupId,
                mavenPublication.artifactId,
                mavenPublication.version,
                artifacts
            )
        }
    }
}

/**
 * Configures artifact validator plugin.
 */
public interface ArtifactsValidatorPluginSettingsExtension {
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
