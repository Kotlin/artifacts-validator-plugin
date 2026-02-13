package kotlinx.validation

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import java.io.File
import javax.inject.Inject

public class ArtifactsValidatorPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            ArtifactsValidatorPluginExtension::class.java,
            "artifactsValidation",
            ArtifactsValidatorPluginExtensionImpl::class.java
        )
        extension.enabled.convention(true)
        extension.requireSignatures.convention(false)
        extension.requireChecksums.convention(emptySet())
        extension.artifactLists.convention(project.provider {
            mapOf(
                project.rootDir.resolve("gradle").resolve("artifacts.txt") to
                project.version.toString()
            )
        })

        project.tasks.register("validateArtifacts", ArtifactsValidationTask::class.java) {
            with(it) {
                group = "verification"
                description = "Validate artifacts in the specified local Maven M2 repository"
                onlyIf { extension.enabled.get() }
                artifactsRepositoryDir.set(extension.artifactsRepository)
                artifactLists.set(extension.artifactLists)
                requireChecksums.set(extension.requireChecksums)
                requireSignatures.set(extension.requireSignatures)
            }
        }
    }
}

public abstract class ArtifactsValidatorPluginExtension {
    /**
     * Enables or disables validation task. It is enabled by default.
     */
    public abstract val enabled: Property<Boolean>

    /**
     * Directory containing artifacts to validate. The directory should have a Maven repository layout.
     */
    public abstract val artifactsRepository: DirectoryProperty

    /**
     * Files with rules describing expected artifacts associated with a version these artifacts should have.
     */
    public abstract val artifactLists: MapProperty<File, String>

    /**
     * Verify signature files existence.
     */
    public abstract val requireSignatures: Property<Boolean>

    /**
     * Verify that checksum files for specified algorithms exist.
     * Empty set imply that an artifact does not have to has a checksum file.
     */
    public abstract val requireChecksums: SetProperty<String>

    /**
     * Validation task provider.
     */
    public abstract val task: TaskProvider<ArtifactsValidationTask>

    /**
     * Add a [list file][listFile] to [artifactLists]. The associated version will be [Project.version].
     */
    public abstract fun artifactsList(listFile: File)

    /**
     * Add a [list file][listFile] associated with a [version] to [artifactLists].
     */
    public fun artifactsList(listFile: File, version: String) {
        artifactLists.put(listFile, version)
    }

    /**
     * Add a [list file][listFile] associated with a version provided by the [versionProvider] to [artifactLists].
     */
    public fun artifactsList(listFile: File, versionProvider: Provider<String>) {
        artifactLists.put(listFile, versionProvider)
    }
}

internal abstract class ArtifactsValidatorPluginExtensionImpl @Inject constructor(
    val tasks: TaskContainer,
    val project: Project
) : ArtifactsValidatorPluginExtension() {
    override val task: TaskProvider<ArtifactsValidationTask>
        get() = tasks.named("validateArtifacts", ArtifactsValidationTask::class.java)

    override fun artifactsList(listFile: File) {
        artifactsList(listFile, project.version.toString())
    }
}
