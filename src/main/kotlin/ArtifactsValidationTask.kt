package kotlinx.validation

import kotlinx.validation.ArtifactsValidationSettingsPlugin.Companion.DUMP_ARTIFACTS_TASK_NAME
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.*
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Paths
import java.util.*

public abstract class ArtifactsValidationTaskBase : DefaultTask() {
    private val logMessagePrefix = "[Artifacts Validation] "
    internal fun error(message: String) = logger.error("$logMessagePrefix$message")
    internal fun warn(message: String) = logger.warn("$logMessagePrefix$message")
    internal fun debug(message: String) = logger.debug("$logMessagePrefix$message")
    internal fun lifecycle(message: String) = logger.lifecycle("$logMessagePrefix$message")

    internal fun compareArtifactsImpl(expectedArtifacts: SortedSet<String>, actualArtifacts: SortedSet<String>) {
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
            "List of found artifacts does not match list of expected artifacts. See log for more details. " +
                    "To generate or update files describing artifacts, run the '$DUMP_ARTIFACTS_TASK_NAME' task."
        )
    }

    internal fun loadRules(file: File): List<ArtifactRule> {
        debug("Loading artifact rules from $file")
        return file.readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("//") }
            .flatMap {
                try {
                    ArtifactRule.parseRule(it)
                } catch (e: IllegalArgumentException) {
                    throw GradleException("Error while parsing rules file $file: ${e.message}")
                }
            }
    }
}

/**
 * Checks that all artifacts from a Maven 2 repository pointed by [artifactsRepositoryDir]
 * or [artifactsRepositoryZip]
 * matches expected artifacts described using rules from [artifactRuleFiles], that there are no unexpected
 * artifacts and that there are no missing artifacts, and all artifacts have an appropriate version. When configured
 * using [requireSignatures] and [requireChecksums], the task also check signature and checksum files
 * correspondingly.
 */
@DisableCachingByDefault
public abstract class ValidateLocalMavenRepositoryTask : ArtifactsValidationTaskBase() {
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
    public abstract val artifactsRepositoryDir: DirectoryProperty

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
    public abstract val artifactsRepositoryZip: RegularFileProperty

    /**
     * Lists of rules describing expected artifacts associated with
     * an expected version artifacts corresponding to these rules.
     */
    @get:Input
    public abstract val artifactRuleFiles: MapProperty<File, String>

    /**
     * Command line option parser for [artifactRuleFiles]. Overrides all values specified in [artifactRuleFiles].
     */
    @Option(
        option = "artifacts-list",
        description = "A path to a file listing artifacts to validate paired with their expected version, " +
                "using the format <file>:<version>. Repeat this option to validate multiple artifact lists."
    )
    public fun artifactsListOption(values: List<String>) {
        values.forEach { fileAndVersion ->
            val delimiterIndex = fileAndVersion.lastIndexOf(':')
            if (delimiterIndex <= 0 || delimiterIndex == fileAndVersion.lastIndex) {
                throw GradleException(
                    "artifacts-list value must use the format <file>:<version>, was: \"$fileAndVersion\"."
                )
            }
            val file = File(fileAndVersion.substring(0, delimiterIndex))
            val version = fileAndVersion.substring(delimiterIndex + 1)
            cliArtifactLists[file] = version
        }
    }

    private val cliArtifactLists: MutableMap<File, String> = mutableMapOf()

    private fun finalArtifactsLists(): Map<File, String> = cliArtifactLists.ifEmpty {
        artifactRuleFiles.getOrElse(emptyMap())
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
    public abstract val requireSignatures: Property<Boolean>

    // TODO: verify checksums
    /**
     * Verify that each artifact has a set of checksum files (like `.md5`, `.sha1``) associated with.
     * If the set is empty, checksum files are not required, otherwise an artifact has to have checksum
     * files associated with all listed checksum algorithms.
     */
    @get:Input
    @get:Optional
    public abstract val requireChecksums: SetProperty<String>

    @Option(
        option = "require-checksums",
        description = "Verify that each artifact has a set of checksum files (like `.md5`, `.sha1``) associated with. " +
                "By default, the set of checksums is empty, meaning no checksum validation. " +
                "Accepts a comma-separated list of values: MD5, SHA1, SHA256, SHA512"
    )
    public fun requireChecksumsOption(value: String) {
        requireChecksums.set(
            value
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet()
        )
    }

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
                    error("Error detecting while reading file $path: ${exception.message}")
                    hasErrors = true
                }
            }

            artifactsRepositoryZip.isPresent -> {
                val repositoryZip = artifactsRepositoryZip.get().asFile.toPath()
                debug("Loading artifacts from ZIP archive $repositoryZip")
                val zipUri = URI.create("jar:${repositoryZip.toUri()}")
                FileSystems.newFileSystem(zipUri, mapOf<String, String>()).use { zipFs ->
                    zipFs.getPath("/").scanRepository(SnapshotResolutionStrategy.LATEST_FILE) { path, exception ->
                        error("Error detecting while reading file $path: ${exception.message}")
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

    private fun loadRules(): Map<File, List<ArtifactRule>> {
        val fileToRules = mutableMapOf<File, List<ArtifactRule>>()
        var hasErrors = false

        finalArtifactsLists().forEach { (file, _) ->
            if (!file.exists()) {
                error("Artifacts list file does not exist: $file")
                hasErrors = true
                return@forEach
            }
            fileToRules[file] = loadRules(file)
        }

        if (hasErrors) {
            throw GradleException("Failed to load rules file from files. See log for more details.")
        }

        return fileToRules
    }

    private fun compareArtifacts(rules: Map<File, List<ArtifactRule>>, artifacts: List<AggregatedArtifactInfo>) {
        val expectedArtifacts = rules.flatMapTo(TreeSet<String>()) { (file, fileRules) ->
            val version = finalArtifactsLists().getValue(file)
            fileRules.map { it.toArtifactIdentifier(version) }
        }
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
}

/**
 * Checks that all artifacts from [publications] matches expected artifacts
 * described using rules from [artifactRuleFiles], that there are no unexpected
 * artifacts and that there are no missing artifacts.
 *
 * This task checks only the list of artifacts and does not verify any additional attributes.
 */
@DisableCachingByDefault
public abstract class PublicationArtifactsValidationTask : ArtifactsValidationTaskBase() {
    @get:InputFiles
    public abstract val artifactRuleFiles: ConfigurableFileCollection

    @get:Input
    public abstract val publications: ListProperty<PublicationDescriptor>

    public fun addPublicationProvider(descriptor: Provider<PublicationDescriptor>) {
        publications.add(descriptor)
    }

    @TaskAction
    public fun validate() {
        val dumpFiles = artifactRuleFiles.files.filter { it.exists() }

        val rules = dumpFiles.flatMap(::loadRules)

        compareArtifactsImpl(
            rules.mapTo(TreeSet()) { it.toArtifactIdentifier(null) },
            publications.get().flatMapTo(TreeSet()) { it.toArtifactIdentifiers() }
        )
    }
}

/**
 * Dumps [rules](ArtifactRule) describing artifacts from [publications]
 * into either a single [sharedRulesFile] file, or one of the [perProjectRuleFiles],
 * where the project is chosen using [PublicationDescriptor.projectPath].
 *
 * Either [sharedRulesFile] or [perProjectRuleFiles] should be configured,
 * it's an error to configure them both simultaneously.
 */
@DisableCachingByDefault
public abstract class PublicationArtifactsDumpTask : DefaultTask() {
    @get:Input
    public abstract val publications: ListProperty<PublicationDescriptor>

    @get:OutputFile
    @get:Optional
    public abstract val sharedRulesFile: RegularFileProperty

    @get:OutputFiles
    public abstract val perProjectRuleFiles: MapProperty<String, File>

    public fun addPublicationProvider(descriptor: Provider<PublicationDescriptor>) {
        publications.add(descriptor)
    }

    @TaskAction
    public fun dump() {
        val project2publication = publications.get().groupBy { it.projectPath }

        check(!(sharedRulesFile.isPresent && perProjectRuleFiles.get().isNotEmpty())) {
            "Either sharedRulesFile, or perProjectRuleFiles should configured, but not both"
        }

        val file2publications = mutableMapOf<File, MutableList<PublicationDescriptor>>()

        for ((project, publications) in project2publication) {
            val file = if (sharedRulesFile.isPresent) {
                sharedRulesFile.get().asFile
            } else {
                val projectDump = perProjectRuleFiles.get()[project]
                check(projectDump != null) {
                    "Dump was not configured for project $project"
                }
                projectDump
            }
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
