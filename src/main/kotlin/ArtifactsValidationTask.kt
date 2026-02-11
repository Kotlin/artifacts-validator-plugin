package kotlinx.validation

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.util.*

@DisableCachingByDefault
public abstract class ArtifactsValidationTask : DefaultTask() {
    init {
        dumpTo.convention(artifactsListFile)
    }

    @get:InputDirectory
    @get:Option(
        option = "artifacts-dir",
        description = "Path to a directory containing artifacts to be validated. " +
                "It is implied that a directory has Maven 2 layout."
    )
    public abstract val artifactsRepositoryDir: DirectoryProperty

    // TODO: update readme
    @get:InputFiles // When dumping artifacts list, this file don't have to exist. InputFiles does the trick here.
    @get:Option(
        option = "artifacts-list",
        description = "A file with a list of expected artifacts. See <README> for the format description. " +
                "By default, it is 'gradle/artifacts.txt'."
    )
    public abstract val artifactsListFile: RegularFileProperty

    @get:Input
    @get:Option(
        option = "artifacts-version",
        description = "Expected version of validated artifacts. By default, the current project version will be used."
    )
    public abstract val artifactsVersion: Property<String>

    @get:Input
    @get:Optional
    @get:Option(
        option = "require-signatures",
        description = "Verify that every artifact has associated signature (.asc) file. Disabled by default. " +
                "Only the presence of a signature file will be checked, not the signature itself."
    )
    public abstract val requireSignatures: Property<Boolean>

    // TODO: verify checksums
    @get:Input
    @get:Optional
    @get:Option(
        option = "require-checksums",
        description = "Verify that every artifact has associated checksum files and checksums are correct. " +
                "By default, the set of checksums is empty, meaning no checksum validation. " +
                "Acceptable values are: MD5, SHA1, SHA256, SHA512"
    )
    public abstract val requireChecksums: SetProperty<String>

    @get:Input
    @get:Optional
    @get:Option(
        option = "dump",
        description = "Dump rules describing all found artifacts to a list file " +
                "instead of performing the actual validation."
    )
    public abstract val dump: Property<Boolean>

    @get:OutputFile
    @get:Optional
    @get:Option(
        option = "dump-to",
        description = "Path to an artifacts file that should be generated when running the task in the dump mode. " +
                "By default, it is the same path as artifactsListFile (or its command line version --artifacts-list)."
    )
    public abstract val dumpTo: RegularFileProperty

    @TaskAction
    public fun validate() {
        val version = artifactsVersion.getOrElse(project.version.toString())
        // get the value earlier to validate task inputs
        val checksums = parseChecksumTypes()
        val artifacts = loadArtifacts(version)

        if (dump.getOrElse(false)) {
            dumpArtifacts(artifacts)
            return
        }

        val rules = loadRules()
        compareArtifacts(version, rules, artifacts)

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

    private fun loadArtifacts(version: String): List<AggregatedArtifactInfo> {
        val repositoryRoot = artifactsRepositoryDir.get().asFile.toPath()
        var hasErrors = false
        debug("Loading artifacts from $repositoryRoot")
        val artifacts = repositoryRoot.scanRepository(SnapshotResolutionStrategy.LATEST_FILE) { path, exception ->
            error("Error detecting while reading file $path: ${exception.message}")
            hasErrors = true
        }.filter { it.gav.version == version }
        if (artifacts.isEmpty()) warn("No artifacts with version $version were found in $repositoryRoot")
        if (hasErrors) throw GradleException("Errors were detected while loading artifacts info. See log for details")
        return artifacts
    }

    private fun loadRules(): List<ArtifactRule> {
        var hasErrors = false
        val file = artifactsListFile.get().asFile
        if (!file.exists()) {
            throw GradleException("Artifacts list file does not exist: ${file.relativeTo(project.rootDir)}")
        }
        debug("Loading artifact rules from ${file.relativeTo(project.rootDir)}")
        return file.readLines().filter { it.isNotBlank() }.flatMap {
            try {
                ArtifactRule.parseRule(it)
            } catch (e: IllegalArgumentException) {
                error("Error while parsing rules file: ${e.message}")
                hasErrors = true
                emptyList()
            }
        }.also {
            if (hasErrors) {
                throw GradleException("Failed to load rules file from ${file.relativeTo(project.rootDir)}. " +
                        "See log for more details.")
            }
        }
    }

    private fun dumpArtifacts(artifacts: List<AggregatedArtifactInfo>) {
        val file = dumpTo.getOrElse { artifactsListFile.get().asFile }.asFile
        debug("Updating artifact rules in file ${file.relativeTo(project.rootDir)}")
        file.bufferedWriter(Charsets.UTF_8).use { writer ->
            artifacts.toRules().sorted().forEach { rule ->
                writer.appendLine(rule)
            }
        }
        lifecycle("Artifact rules were saved to ${file.relativeTo(project.rootDir)}")
    }

    private fun compareArtifacts(version: String, rules: List<ArtifactRule>, artifacts: List<AggregatedArtifactInfo>) {
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

        error(
            "To update the list of expected artifacts, run the ${this.name} task with the --dump flag: " +
                    "\"${this.path} --dump --artifacts-version=${version} " +
                    "--artifacts-dir=${artifactsRepositoryDir.get().asFile.relativeTo(project.rootDir)} " +
                    "--artifacts-list=${artifactsListFile.get().asFile.relativeTo(project.rootDir)}"
        )

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
