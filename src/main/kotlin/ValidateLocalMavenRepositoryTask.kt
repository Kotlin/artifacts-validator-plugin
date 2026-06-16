package kotlinx.validation

import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.io.Serializable
import java.net.URI
import java.nio.file.FileSystems
import java.util.SortedSet
import java.util.TreeSet

/**
 * Checks that all artifacts from a Maven 2 repository pointed by [artifactsRepositoryDir]
 * or [artifactsRepositoryZip]
 * matches expected artifacts described using rules from [artifactsList], that there are no unexpected
 * artifacts and that there are no missing artifacts, and all artifacts have an appropriate version. When configured
 * using [requireSignatures] and [requireChecksums], the task also check signature and checksum files
 * correspondingly.
 */
@DisableCachingByDefault
internal abstract class ValidateLocalMavenRepositoryTask : ArtifactsValidationTaskBase() {
    /**
     * A directory containing artifacts to validate.
     *
     * The directory is expected to have a [Maven repository layout](https://maven.apache.org/repository/layout.html).
     */
    @get:InputDirectory
    @get:Optional
    @get:Option(
        option = "artifacts-dir",
        description = "Path to a directory containing artifacts to be validated. " +
                "It is implied that a directory has Maven 2 layout."
    )
    abstract val artifactsRepositoryDir: DirectoryProperty

    /**
     * A ZIP archive containing artifacts to validate.
     *
     * The archive is expected to have a [Maven repository layout](https://maven.apache.org/repository/layout.html).
     */
    @get:InputFile
    @get:Optional
    @get:Option(
        option = "artifacts-zip",
        description = "Path to a ZIP archive containing artifacts to be validated. " +
                "It is implied that archive entries have Maven 2 layout."
    )
    abstract val artifactsRepositoryZip: RegularFileProperty

    /**
     * Lists of rules describing expected artifacts associated with
     * an expected version artifacts corresponding to these rules.
     *
     * A rules have to point to a file, or to a directory. In the latter case, all files
     * within a directory will be read and combined.
     */
    @get:Nested
    abstract val artifactsList: ListProperty<RuleFileWithVersion>

    /**
     * Command line option parser for [artifactsList]. Overrides all values specified in [artifactsList].
     */
    @Option(
        option = "artifacts-list",
        description = "A path to a file listing artifacts to validate paired with their expected version, " +
                "using the format <file|directory>:<version>. If a path points to a directory, all files from it" +
                "will be read and combined together. Repeat this option to validate multiple artifact lists."
    )
    fun artifactsListOption(values: List<String>) {
        values.forEach { fileAndVersion ->
            val delimiterIndex = fileAndVersion.lastIndexOf(':')
            if (delimiterIndex <= 0 || delimiterIndex == fileAndVersion.lastIndex) {
                throw GradleException(
                    "artifacts-list value must use the format <file>:<version>, was: \"$fileAndVersion\"."
                )
            }
            val file = File(fileAndVersion.substring(0, delimiterIndex))
            val version = fileAndVersion.substring(delimiterIndex + 1)
            val fileProperty = project.objects.fileCollection().from(file)
            artifactsList.add(RuleFileWithVersion(fileProperty, version))
        }
    }

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
    abstract val requireSignatures: Property<Boolean>

    // TODO: verify checksums
    /**
     * Verify that each artifact has a set of checksum files (like `.md5`, `.sha1``) associated with.
     * If the set is empty, checksum files are not required, otherwise an artifact has to have checksum
     * files associated with all listed checksum algorithms.
     */
    @get:Input
    @get:Optional
    abstract val requireChecksums: SetProperty<String>

    @Option(
        option = "require-checksums",
        description = "Verify that each artifact has a set of checksum files (like '.md5', '.sha1') associated with. " +
                "By default, the set of checksums is empty, meaning no checksum validation. " +
                "Accepts a comma-separated list of values: MD5, SHA1, SHA256, SHA512"
    )
    fun requireChecksumsOption(value: String) {
        requireChecksums.set(
            value
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet()
        )
    }

    @TaskAction
    fun validate() {
        // get the value earlier to validate task inputs
        val checksums = parseChecksumTypes()
        val artifacts = loadArtifacts()

        compareArtifacts(loadExpectedArtifactsList(), artifacts)

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

    private fun loadArtifacts(): List<AggregatedArtifactInfo> {
        var hasErrors = false
        val artifacts = when {
            artifactsRepositoryDir.isPresent && artifactsRepositoryZip.isPresent -> {
                throw GradleException(
                    "Only one artifact source can be configured. Use either --artifacts-dir or --artifacts-zip."
                )
            }

            artifactsRepositoryDir.isPresent -> {
                val repositoryRoot = artifactsRepositoryDir.get().asFile.toPath()
                debug("Loading artifacts from directory $repositoryRoot")
                repositoryRoot.scanRepository(SnapshotResolutionStrategy.LATEST_FILE) { path, exception ->
                    error("Error detected while reading file $path: ${exception.message}")
                    hasErrors = true
                }
            }

            artifactsRepositoryZip.isPresent -> {
                val repositoryZip = artifactsRepositoryZip.get().asFile.toPath()
                debug("Loading artifacts from ZIP archive $repositoryZip")
                val zipUri = URI.create("jar:${repositoryZip.toUri()}")
                FileSystems.newFileSystem(zipUri, mapOf<String, String>()).use { zipFs ->
                    zipFs.getPath("/").scanRepository(SnapshotResolutionStrategy.LATEST_FILE) { path, exception ->
                        error("Error detected while reading file $path: ${exception.message}")
                        hasErrors = true
                    }
                }
            }

            else -> {
                throw GradleException("Artifact source is not configured. Use either --artifacts-dir or --artifacts-zip.")
            }
        }
        if (artifacts.isEmpty()) {
            val source = when {
                artifactsRepositoryDir.isPresent -> artifactsRepositoryDir.get().asFile
                artifactsRepositoryZip.isPresent -> artifactsRepositoryZip.get().asFile
                else -> null
            }
            warn("No artifacts were found in $source")
        }
        if (hasErrors) throw GradleException("Errors were detected while loading artifacts info. See log for details")
        return artifacts
    }

    private fun loadExpectedArtifactsList(): SortedSet<String> {
        val expectedArtifacts = sortedSetOf<String>()

        artifactsList.getOrElse(emptyList()).forEach {
            val filesOrDirectories = it.files.files
            val version = it.version
            filesOrDirectories.forEach { fileOrDirectory ->
                val files = if (fileOrDirectory.exists() && fileOrDirectory.isDirectory) {
                    fileOrDirectory.listFiles()
                } else {
                    arrayOf(fileOrDirectory)
                }
                files.forEach { file ->
                    if (!file.exists()) {
                        error("Artifacts list file does not exist: $file")
                    }
                    expectedArtifacts.addAll(loadRules(file).map { it.toArtifactIdentifier(version) })
                }
            }
        }

        return expectedArtifacts
    }

    private fun compareArtifacts(expectedArtifacts: SortedSet<String>, artifacts: List<AggregatedArtifactInfo>) {
        val actualArtifacts = artifacts.flatMapTo(TreeSet<String>()) {
            it.artifacts.map { it.artifact.toArtifactIdentifier() }
        }

        compareArtifactsImpl(expectedArtifacts, actualArtifacts)
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

    companion object {
        const val TASK_NAME: String = "validateLocalMavenRepo"
    }
}

internal class RuleFileWithVersion(
    @get:InputFiles val files: ConfigurableFileCollection,
    @get:Input val version: String
) : Serializable
