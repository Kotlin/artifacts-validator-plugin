package kotlinx.validation

import kotlinx.validation.ArtifactsValidationSettingsPlugin.Companion.DUMP_ARTIFACTS_TASK_NAME
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.nio.file.Paths
import java.util.*

@DisableCachingByDefault
public abstract class ValidateLocalMavenRepositoryTask : DefaultTask() {
    /**
     * A directory containing artifacts to validate.
     *
     * The directory is expected to have a [Maven repository layout](https://maven.apache.org/repository/layout.html).
     */
    @get:InputDirectory
    @get:Option(
        option = "artifacts-dir",
        description = "Path to a directory containing artifacts to be validated. " +
                "It is implied that a directory has Maven 2 layout."
    )
    public abstract val artifactsRepositoryDir: DirectoryProperty

    /**
     * Lists of rules describing expected artifacts associated with
     * an expected version artifacts corresponding to these rules.
     */
    @get:InputFile
    @get:Option(
        option = "artifacts-list",
        description = "A file containing a list of artifacts to validate."
    )
    public abstract val artifactList: RegularFileProperty

    @get:Input
    @get:Optional
    @get:Option(
        option = "artifacts-version",
        description = "Version of artifacts to validate. If not specified, the version will not be verified."
    )
    public abstract val validateVersion: Property<String>

    /**
     * Verify that each artifact has a corresponding signature file (`.asc`-file).
     */
    @get:Input
    @get:Optional
    @get:Option(
        option = "require-signatures",
        description = "Verify that every artifact has associated signature (.asc) file. Disabled by default. " +
                "Only the presence of a signature file will be checked, not the signature itself."
    )
    public abstract val requireSignatures: Property<Boolean>

    // TODO: verify checksums
    /**
     * Verify that each artifact has a set of checksum files (like `.md5`, `.sha1``) associated with.
     * If the set is empty, checksum files are not required, otherwise an artifact has to have checksum
     * files associated with all listed checksum algorithms.
     */
    @get:Input
    @get:Optional
    @get:Option(
        option = "require-checksums",
        description = "Verify that every artifact has associated checksum files and checksums are correct. " +
                "By default, the set of checksums is empty, meaning no checksum validation. " +
                "Acceptable values are: MD5, SHA1, SHA256, SHA512"
    )
    public abstract val requireChecksums: SetProperty<String>

    @TaskAction
    public fun validate() {
        // get the value earlier to validate task inputs
        val checksums = parseChecksumTypes()
        val artifacts = loadArtifacts()

        val rules = loadRules()
        compareArtifacts(rules, artifacts)

        validateAttributes(artifacts, requireSignatures.getOrElse(false), checksums)
    }

    private fun parseChecksumTypes(): Set<ChecksumType> = buildSet {
        requireChecksums.getOrElse(emptySet()).forEach { rawValue ->
            try {
                add(ChecksumType.valueOf(rawValue.uppercase()))
            } catch (_: IllegalArgumentException) {
                throw GradleException(
                    "Invalid checksum type: $rawValue. Use one of ${ChecksumType.values().joinToString(", ")}"
                )
            }
        }
    }

    private val logMessagePrefix = "[Artifacts Validation] "
    private fun error(message: String) = logger.error("$logMessagePrefix$message")
    private fun warn(message: String) = logger.warn("$logMessagePrefix$message")
    private fun debug(message: String) = logger.debug("$logMessagePrefix$message")
    private fun lifecycle(message: String) = logger.lifecycle("$logMessagePrefix$message")

    private fun loadArtifacts(): List<AggregatedArtifactInfo> {
        val repositoryRoot = artifactsRepositoryDir.get().asFile.toPath()
        var hasErrors = false
        debug("Loading artifacts from $repositoryRoot")
        val artifacts = repositoryRoot.scanRepository(SnapshotResolutionStrategy.LATEST_FILE) { path, exception ->
            error("Error detecting while reading file $path: ${exception.message}")
            hasErrors = true
        }
        if (artifacts.isEmpty()) warn("No artifacts were found in $repositoryRoot")
        if (hasErrors) throw GradleException("Errors were detected while loading artifacts info. See log for details")
        return artifacts
    }

    private fun loadRules(): List<ArtifactRule> {
        val file = artifactList.get().asFile
        if (!file.exists()) {
            throw GradleException("Artifacts list file does not exist: $file")
        }
        debug("Loading artifact rules from $file")
        return file.readLines().filter { it.isNotBlank() }.flatMap {
            try {
                ArtifactRule.parseRule(it)
            } catch (e: IllegalArgumentException) {
                throw GradleException("Error while parsing rules file $file: ${e.message}", e)
            }
        }
    }

    private fun compareArtifacts(rules: List<ArtifactRule>, artifacts: List<AggregatedArtifactInfo>) {
        val version = validateVersion.getOrElse(null)
        val expectedArtifacts = rules.mapTo(TreeSet<String>()) {
            it.toArtifactIdentifier(version)
        }
        val actualArtifacts = artifacts.flatMapTo(TreeSet<String>()) {
            it.artifacts.map { it.artifact.toArtifactIdentifier() }
        }

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
            "List of found artifacts does not match list of expected artifacts. See log for more details."
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun validateAttributes(
        artifacts: List<AggregatedArtifactInfo>,
        requireSignature: Boolean,
        requiredChecksums: Set<ChecksumType>
    ) {
        var hasChecksumErrors = false
        var hasSignatureErrors = false
        val requireChecksums = requiredChecksums.isNotEmpty()
        artifacts.forEach { artifactBundle ->
            artifactBundle.artifacts.forEach { artifact ->
                if (requireSignature && !artifact.isSigned) {
                    error("Artifact ${artifact.artifact.filePath} is not signed.")
                    hasSignatureErrors = true
                }
                if (requireChecksums) {
                    val missingChecksums = requiredChecksums.subtract(artifact.checksumTypes)
                    if (missingChecksums.isNotEmpty()) {
                        error("Artifact ${artifact.artifact.filePath} is missing following checksums: $missingChecksums")
                        hasChecksumErrors = true
                    }
                }
            }
        }
        if (!hasSignatureErrors && requireSignature) {
            lifecycle("All artifacts are signed.")
        }
        if (!hasChecksumErrors && requiredChecksums.isNotEmpty()) {
            lifecycle("All artifacts have required checksums.")
        }
        if (hasSignatureErrors || hasChecksumErrors) {
            throw GradleException("Some artifacts were not signed or missing checksum files. See log for more details.")
        }
    }
}

private fun PublicationDescriptor.toArtifactIdentifiers(): List<String> {
    return artifacts.map {
        val info = ArtifactInfo(
            ArtifactInfo.Gav(groupId, artifactId, version),
            Paths.get(""),
            it.extension,
            it.classifier,
            null,
            null,
            version.contains("SNAPSHOT")
        )
        info.toArtifactIdentifier(false)
    }
}

private fun PublicationDescriptor.toRules(): String {
    val ga = "${groupId}:${artifactId}"
    val classifierAndExtension = artifacts
        .map { "${it.classifier}.${it.extension}" }
        .sorted()
        .joinToString(",")
    return "$ga/$classifierAndExtension"
}

@DisableCachingByDefault
public abstract class PublicationArtifactsValidationTask : DefaultTask() {
    @get:InputFiles
    public abstract val artifactsDumpFiles: ConfigurableFileCollection

    @get:Input
    public abstract val publications: ListProperty<PublicationDescriptor>

    public fun addPublicationProvider(descriptor: Provider<PublicationDescriptor>) {
        publications.add(descriptor)
    }

    @TaskAction
    public fun validate() {
        val dumpFiles = artifactsDumpFiles.files

        dumpFiles.forEach {
            if (!it.exists()) {
                throw GradleException(
                    "Files describing expected artifacts does not exist: $it. " +
                            "To generate the file, run the '$DUMP_ARTIFACTS_TASK_NAME' task."
                )
            }
        }

        val rules = dumpFiles.flatMap { file ->
            file.readLines()
                .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("//") }
                .flatMap {
                    try {
                        ArtifactRule.parseRule(it)
                    } catch (e: IllegalArgumentException) {
                        error("Error while parsing rules file $file: ${e.message}")
                    }
                }
        }

        compareArtifacts(rules, publications.get().flatMap { it.toArtifactIdentifiers() })
    }

    private fun compareArtifacts(rules: List<ArtifactRule>, artifacts: List<String>) {
        val expectedArtifacts = rules.mapTo(TreeSet<String>()) { it.toArtifactIdentifier(null) }

        val actualArtifacts = TreeSet<String>().also {
            it.addAll(artifacts)
        }

        if (expectedArtifacts == actualArtifacts) {
            project.logger.info("Artifacts fully matched the list of expected artifacts.")
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

        error(
            "To update the list of expected artifacts, ..."
        )

        throw GradleException(
            "List of found artifacts does not match list of expected artifacts. See log for more details."
        )
    }
}

@DisableCachingByDefault
public abstract class PublicationArtifactsDumpTask : DefaultTask() {
    @get:Input
    public abstract val publications: ListProperty<PublicationDescriptor>

    @get:OutputFile
    public abstract val defaultArtifactsDumpFile: RegularFileProperty

    @get:OutputFiles
    public abstract val artifactsDumpFiles: MapProperty<String, File>

    public fun addPublicationProvider(descriptor: Provider<PublicationDescriptor>) {
        publications.add(descriptor)
    }

    @TaskAction
    public fun dump() {
        val project2publication = publications.get().groupBy { it.projectPath }

        val file2publications =  mutableMapOf<File, MutableList<PublicationDescriptor>>()
        val defaultFile = defaultArtifactsDumpFile.get().asFile

        for ((project, publications) in project2publication) {
            val file = artifactsDumpFiles.get().getOrDefault(project, defaultFile)
            file2publications.getOrPut(file) { mutableListOf() }.addAll(publications)
        }

        for ((dumpFile, publications) in file2publications) {
            dumpFile.bufferedWriter(Charsets.UTF_8).use { writer ->
                publications.map { it.toRules() }.sorted().forEach {
                    writer.appendLine(it)
                }
            }
        }
    }
}
