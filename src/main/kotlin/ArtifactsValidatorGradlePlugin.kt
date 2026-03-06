package kotlinx.validation

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.initialization.Settings
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.language.base.plugins.LifecycleBasePlugin
import java.io.File

private fun Project.applyRecursively(block: Project.() -> Unit) {
    block()
    childProjects.forEach { (_, project) -> project.applyRecursively(block) }
}

public class ArtifactsValidationSettingsPlugin : Plugin<Settings> {
    public companion object {
        public const val CHECK_ARTIFACTS_TASK_NAME: String = "checkArtifacts"
        public const val DUMP_ARTIFACTS_TASK_NAME: String = "dumpArtifacts"
    }

    override fun apply(target: Settings) {
        val ext = target.extensions.create(
            ArtifactsValidatorPluginSettingsExtension::class.java,
            "artifactsValidation", ArtifactsValidatorPluginSettingsExtensionImpl::class.java
        ) as ArtifactsValidatorPluginSettingsExtensionImpl

        val rootDirPath = target.rootDir.toPath()

        ext.defaultArtifactsDumpFile.set(target.rootDir.resolve("gradle/artifacts.txt"))

        target.gradle.beforeProject { project ->
            if (project.path != ":") return@beforeProject

            val p2f = ext.projectPath2dumpFile
            val defaultDumpFile = ext.defaultArtifactsDumpFile.get().asFile

            if (!defaultDumpFile.toPath().normalize().startsWith(rootDirPath)) {
                throw GradleException("Artifacts dump file must be located within the project directory: $defaultDumpFile")
            }

            p2f.forEach { (_, file) ->
                val normalizedPath = file.toPath().normalize()
                if (!normalizedPath.startsWith(rootDirPath)) {
                    throw GradleException("Artifacts dump file must be located within the project directory: $file")
                }
            }

            val checkTask =
                project.tasks.register(CHECK_ARTIFACTS_TASK_NAME, PublicationArtifactsValidationTask::class.java) {
                    // TODO: if all projects have an explicitly configure file in p2f map,
                    //       there will be an error about non-existent defaultDumpFile (if it does not exist)
                    it.artifactsDumpFiles.from(defaultDumpFile)
                    it.artifactsDumpFiles.from(p2f.values)
                }

            project.tasks.configureEach {
                if (it.name == LifecycleBasePlugin.CHECK_TASK_NAME) {
                    it.dependsOn(checkTask)
                }
            }

            val dumpTask = project.tasks.register(DUMP_ARTIFACTS_TASK_NAME, PublicationArtifactsDumpTask::class.java) {
                it.defaultArtifactsDumpFile.set(defaultDumpFile)
                p2f.forEach { (project, file) ->
                    it.artifactsDumpFiles.put(project, file)
                }
            }

            project.tasks.register("validateArtifacts", ArtifactsValidationTask::class.java)

            project.applyRecursively {
                this.afterEvaluate {
                    val ext = it.extensions.findByType(PublishingExtension::class.java)
                    if (ext == null) {
                        logger.info("Publication was not configured for the project: ${this.name}, skipping artifacts validation setup.")
                        return@afterEvaluate
                    }
                    // TODO: can we really do that?
                    ext.publications.configureEach { publication ->
                        if (publication is MavenPublication) {
                            checkTask.configure { it.addPublication(this, publication) }
                            dumpTask.configure { it.addPublication(this, publication) }
                        }
                    }
                }
            }
        }
    }
}

public interface ArtifactsValidatorPluginSettingsExtension {
    public val defaultArtifactsDumpFile: RegularFileProperty

    public fun dumpFileForProjects(file: File, project: ProjectDescriptor, vararg projects: ProjectDescriptor)
}

internal abstract class ArtifactsValidatorPluginSettingsExtensionImpl : ArtifactsValidatorPluginSettingsExtension {
    internal val projectPath2dumpFile = mutableMapOf<String, File>()

    override fun dumpFileForProjects(file: File, project: ProjectDescriptor, vararg projects: ProjectDescriptor) {
        projectPath2dumpFile[project.path] = file
        projects.forEach { project ->
            projectPath2dumpFile[project.path] = file
        }
    }
}
