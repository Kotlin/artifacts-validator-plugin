package kotlinx.validation

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

public class ArtifactsValidatorPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "artifactsValidation",
            ArtifactsValidatorPluginExtension::class.java
        )
        extension.enabled.convention(true)
        extension.requireSignatures.convention(false)
        extension.requireChecksums.convention(emptySet())
        extension.artifactsVersion.convention(project.provider {
            project.version.toString()
        })
        extension.artifactsList.convention(project.layout.file(project.provider {
            project.rootDir.resolve("gradle").resolve("artifacts.txt")
        }))

        project.tasks.register("validateArtifacts", ArtifactsValidationTask::class.java) {
            with(it) {
                group = "verification"
                description = "Validate artifacts in the specified local Maven M2 repository"
                onlyIf { extension.enabled.get() }
                artifactsRepositoryDir.set(extension.artifactsRepository)
                artifactsListFile.set(extension.artifactsList)
                artifactsVersion.set(extension.artifactsVersion)
                requireChecksums.set(extension.requireChecksums)
                requireSignatures.set(extension.requireSignatures)
            }
        }
    }
}

public abstract class ArtifactsValidatorPluginExtension {
    public abstract val enabled: Property<Boolean>
    public abstract val artifactsVersion: Property<String>
    public abstract val artifactsList: RegularFileProperty
    public abstract val artifactsRepository: DirectoryProperty
    public abstract val requireSignatures: Property<Boolean>
    public abstract val requireChecksums: SetProperty<String>
}
