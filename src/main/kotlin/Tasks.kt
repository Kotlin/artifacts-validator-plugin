package kotlinx.validation

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction


public abstract class ArtifactsValidationTask : DefaultTask() {
    @get:InputDirectory
    public abstract val artifactsDir: DirectoryProperty

    @get:InputFile
    public abstract val artifactsListFile: RegularFileProperty

    @get:Input
    public abstract val validateSignatures: Property<Boolean>

    @TaskAction
    public fun validate() {

    }
}
