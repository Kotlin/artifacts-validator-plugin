package kotlinx.validation

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.*

@DisableCachingByDefault
public abstract class ArtifactsValidationTask : DefaultTask() {
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
     *
     * If any `--artifacts-list` value was specified on the command line,
     * its value will override this property.
     */
    @get:Input
    public abstract val artifactLists: MapProperty<File, String>

    /**
     * Command line option parser for [artifactLists]. Overrides all values specified in [artifactLists].
     */
    @Option(
        option = "artifacts-list",
        description = "A path to a file listing artifacts to validate and their expected version, separated by column. " +
                "If there's no comma and version following it, a version attribute of a project will be used instead. " +
                "For example, \"gradle/artifacts.extended.txt:0.1.1-dev.1\". This option is aimed for projects " +
                "publishing multiple artifacts versions simultaneously. " +
                "It overrides all values specified in artifactLists property."
    )
    public fun artifactsListOption(values: List<String>) {
        values.forEach { fileAndVersion ->
            val commasCount = fileAndVersion.count { it == ':' }
            if (commasCount > 1) {
                throw GradleException(
                    "artifacts-list value must contain at most one ':' delimiting file and a version, " +
                            "was: \"$fileAndVersion\"."
                )
            }
            if (commasCount == 0) {
                cliArtifactLists[File(fileAndVersion)] = project.version.toString()
            } else {
                val (file, version) = fileAndVersion.split(':', limit = 2)
                cliArtifactLists[File(file)] = version
            }
        }
    }

    private val cliArtifactLists: MutableMap<File, String> = mutableMapOf()

    /**
     * Selects either [cliArtifactLists] or [artifactLists].
     */
    private fun finalArtifactsLists(): Map<File, String> = cliArtifactLists.ifEmpty {
        artifactLists.get()
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
    @get:Option(
        option = "require-checksums",
        description = "Verify that every artifact has associated checksum files and checksums are correct. " +
                "By default, the set of checksums is empty, meaning no checksum validation. " +
                "Acceptable values are: MD5, SHA1, SHA256, SHA512"
    )
    public abstract val requireChecksums: SetProperty<String>

    /**
     * Dump rules describing artifacts from [artifactsRepositoryDir] into files from [artifactLists]
     * instead of performing the validation.
     *
     * Artifacts from [artifactsRepositoryDir] a filtered by a version from [artifactLists] when
     * written to an associated file. There are no other criteria to distribute rules among multiple
     * files from [artifactLists].
     */
    @get:Input
    @get:Optional
    @get:Option(
        option = "dump",
        description = "Dump rules describing all found artifacts to a list file " +
                "instead of performing the actual validation."
    )
    public abstract val dump: Property<Boolean>

    @TaskAction
    public fun validate() {
        // get the value earlier to validate task inputs
        val checksums = parseChecksumTypes()
        val artifacts = loadArtifacts()

        if (dump.getOrElse(false)) {
            dumpArtifacts(artifacts)
            return
        }

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

    private fun loadRules(): Map<File, List<ArtifactRule>> {
        val file2rule = mutableMapOf<File, List<ArtifactRule>>()
        var hasErrors = false

        finalArtifactsLists().forEach { (file, _) ->
            if (!file.exists()) {
                error("Artifacts list file does not exist: $file")
                hasErrors = true
            }
            debug("Loading artifact rules from $file")
            val rules = file.readLines().filter { it.isNotBlank() }.flatMap {
                try {
                    ArtifactRule.parseRule(it)
                } catch (e: IllegalArgumentException) {
                    error("Error while parsing rules file $file: ${e.message}")
                    hasErrors = true
                    emptyList()
                }
            }
            if (!hasErrors) file2rule[file] = rules
        }
        if (hasErrors) throw GradleException("Failed to load rules file from files. See log for more details.")
        return file2rule
    }

    private fun dumpArtifacts(artifacts: List<AggregatedArtifactInfo>) {
        finalArtifactsLists().forEach { (file, version) ->
            debug("Updating artifact rules in file $file")
            file.bufferedWriter(Charsets.UTF_8).use { writer ->
                artifacts.filter { it.gav.version == version }.toRules().sorted().forEach { rule ->
                    writer.appendLine(rule)
                }
            }

            lifecycle("Artifact rules were saved to $file")
        }
    }

    private fun compareArtifacts(rules: Map<File, List<ArtifactRule>>, artifacts: List<AggregatedArtifactInfo>) {
        val expectedArtifacts = rules.flatMapTo(TreeSet<String>()) {
            val version = finalArtifactsLists()[it.key]!!
            it.value.map { it.toArtifactIdentifier(version) }
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
                    "\"${this.path} --dump " + finalArtifactsLists().entries.joinToString(" ") { (f, v) ->
                "--artifacts-list=\"$f\":$v\""
            }
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
