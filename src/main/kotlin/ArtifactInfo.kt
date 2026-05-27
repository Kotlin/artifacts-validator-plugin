package kotlinx.validation

import java.nio.file.FileVisitResult
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.fileVisitor
import kotlin.io.path.relativeTo
import kotlin.io.path.visitFileTree

/**
 * Information about an artifact and all additional files corresponding to it.
 */
internal data class AugmentedArtifactInfo(
    val artifact: ArtifactInfo,
    val signatureTypes: Set<SignatureType>,
    val checksumTypes: Set<ChecksumType>
) {
    val isSigned: Boolean = signatureTypes.isNotEmpty()
    val hasChecksums: Boolean = checksumTypes.isNotEmpty()
}

/**
 * Information about all artifacts located by the given [groupId-artifactId-version triple](gav).
 */
internal data class AggregatedArtifactInfo(
    val gav: ArtifactInfo.Gav,
    val artifacts: List<AugmentedArtifactInfo>
) {
    val pom: AugmentedArtifactInfo? = artifacts.find { it.artifact.extension == "pom" }
}

internal enum class SnapshotResolutionStrategy {
    FAIL,
    LATEST_FILE,
    // FROM_MAVEN_METADATA
}

/**
 * Collects information about all artifacts from a Maven repository rooted at the given path.
 * All errors occurred along the way will be reported to a given [onError] callback.
 *
 * Errors include all errors reported by the [extractArtifactInfo] and an error corresponding to
 * signature and/or checksum files for non-existent artifact files.
 */
internal fun Path.scanRepository(
    snapshotResolutionStrategy: SnapshotResolutionStrategy = SnapshotResolutionStrategy.FAIL,
    onError: (Path, Exception) -> Unit
): List<AggregatedArtifactInfo> {
    val allArtifacts = mutableListOf<ArtifactInfo>()

    val root = this

    @OptIn(ExperimentalPathApi::class)
    visitFileTree(
        fileVisitor {
            onPreVisitDirectory { directory, _ ->
                when (directory.fileName?.toString()) {
                    ".index" -> FileVisitResult.SKIP_SUBTREE
                    ".meta" -> FileVisitResult.SKIP_SUBTREE
                    else -> FileVisitResult.CONTINUE
                }
            }
            onVisitFile { file, _ ->
                if (file.isMavenMetadataFile() || file.isArchetypeCatalog()) {
                    return@onVisitFile FileVisitResult.CONTINUE
                }

                val artifactPath = file.relativeTo(root)

                artifactPath.extractArtifactInfo(fullPath = file)
                    .onFailure { onError(file, it as Exception) }
                    .onSuccess(allArtifacts::add)

                FileVisitResult.CONTINUE
            }
        }
    )

    return allArtifacts.groupArtifacts(snapshotResolutionStrategy, onError)
}

private fun Collection<ArtifactInfo>.groupArtifacts(
    snapshotResolutionStrategy: SnapshotResolutionStrategy,
    onError: (Path, Exception) -> Unit
): List<AggregatedArtifactInfo> {
    fun ArtifactInfo.isActualArtifact() = checksumType == null && signatureType == null

    // Group all ArtifactInfo corresponding to the same artifact together.
    // Note that for snapshot versions there might be multiple files which are resolved later.
    // Refer to https://maven.apache.org/repository/layout.html#snapshot for details on snapshot artifacts
    // and their versions.
    val coordinatesToInfo = groupBy { "${it.gav.toCoordinates()}:${it.classifier}:${it.extension}" }

    val artifacts: MutableMap<ArtifactInfo.Gav, MutableList<AugmentedArtifactInfo>> = mutableMapOf()
    coordinatesToInfo.values.forEach { artifactInfos ->
        val gav = artifactInfos.first().gav
        val path = artifactInfos.first().filePath
        val mainArtifacts = artifactInfos.filter { it.isActualArtifact() /* NB: others are signatures and checksums */ }

        if (mainArtifacts.isEmpty()) {
            onError(path, IllegalArgumentException(
                "There are checksum and/or signature files corresponding to an artifact, " +
                        "but the main artifact file does not exist: $path"
            ))
            return@forEach
        }

        // Try to resolve snapshot artifacts
        val mainArtifact = if (mainArtifacts.first().isSnapshot) {
            val resolvedSnapshot = snapshotResolutionStrategy.resolveSnapshot(mainArtifacts)
            if (resolvedSnapshot == null) {
                onError(path, IllegalArgumentException(
                    "Unable to resolve artifact for a snapshot version using a snapshot resolution strategy " +
                            "$snapshotResolutionStrategy: $path"
                ))
                return@forEach
            }
            resolvedSnapshot
        } else {
            check(mainArtifacts.size == 1) { "Multiple artifact files were found for path: $path" }
            mainArtifacts.first()
        }

        val filesMatchingMainArtifactVersion = if (mainArtifact.isSnapshot) {
            artifactInfos.filter { it.version == mainArtifact.version }
        } else {
            artifactInfos
        }

        val aai = AugmentedArtifactInfo(
            mainArtifact,
            signatureTypes = filesMatchingMainArtifactVersion.mapNotNullTo(mutableSetOf()) { it.signatureType },
            checksumTypes = filesMatchingMainArtifactVersion.mapNotNullTo(mutableSetOf()) { it.checksumType }
        )
        artifacts.getOrPut(gav) { mutableListOf() }.add(aai)
    }

    return artifacts.map { AggregatedArtifactInfo(it.key, it.value) }
}

private fun SnapshotResolutionStrategy.resolveSnapshot(files: List<ArtifactInfo>): ArtifactInfo? = when (this) {
    SnapshotResolutionStrategy.FAIL -> null
    SnapshotResolutionStrategy.LATEST_FILE -> files.maxByOrNull {
        // Snapshot version has the following format: <version>-<timestamp>-<counter>
        val version = it.version
        val idx = version.lastIndexOf('-')
        // Well, the version has an invalid format, but let's deal with it anyway
        if (idx < 0) return@maxByOrNull version
        val counter = version.substring(idx + 1)
        "${version.substring(0, idx)}-${counter.padStart(5, '0')}"
    }
}

private fun Path.isMavenMetadataFile(): Boolean {
    val fileName = this.fileName.toString()
    if (fileName == "maven-metadata.xml") return true
    if (!fileName.startsWith("maven-metadata.xml.")) return false

    val suffix = fileName.substring("maven-metadata.xml.".length)
    return checksumTypeValues.any { it.extension == suffix }
}

private fun Path.isArchetypeCatalog(): Boolean = fileName.toString() == "archetype-catalog.xml"

internal enum class ChecksumType(val extension: String) {
    MD5("md5"),
    SHA1("sha1"),
    SHA256("sha256"),
    SHA512("sha512");
}

internal enum class SignatureType(val extension: String) {
    PGP("asc")
}

/**
 * Information about an artifact from a path inside a Maven repository.
 *
 * For checksum files and signature files, either [checksumType] or [signatureType] is not null and
 * [filePath], [fileName] and [extension] fields corresponds to a "target" file.
 * For example, an info extracted for path `"org/example/artifact/1.0/artifact-1.0.pom.asc"` will have:
 * - [filePath] `org/example/artifact/1.0/artifact-1.0.pom`;
 * - [fileName] `artifact-1.0.pom`;
 * - [extension] `pom`.
 *
 * For snapshot artifacts, there might be two different yet connected versions: the version and the base version.
 * The former is extracted from a file name and the latter corresponds to a parent directory name.
 */
internal data class ArtifactInfo(
    val gav: Gav,
    val filePath: Path,
    val extension: String,
    val classifier: String,
    val signatureType: SignatureType? = null,
    val checksumType: ChecksumType? = null,
    val isSnapshot: Boolean,
    // This is the "version" in maven GAV terminology, while gav.version is a base version.
    val version: String = gav.baseVersion
) {
    data class Gav(
        val groupId: String,
        val artifactId: String,
        // Unlike "true" maven GAV, this version is a base version.
        // For snapshots, it would be `1.0-SNAPSHOT` and not `1.0-20260206.101225-1`.
        val baseVersion: String
    ) {
        fun toCoordinates(): String = "$groupId:$artifactId:$baseVersion"
    }

    val fileName: String
        get() = filePath.fileName.toString()

    fun toArtifactIdentifier(includeVersion: Boolean = true): String {
        val classifierStr = if (classifier.isEmpty()) "" else "-${classifier}"
        val versionStr = if (includeVersion) "-${gav.baseVersion}" else ""
        return "${gav.groupId}:${gav.artifactId}$versionStr$classifierStr.$extension"
    }
}

/**
 * Extracts [ArtifactInfo] from a given [Path].
 *
 * The path has to be relative (i.e. not [Path.isAbsolute]) and it should follow maven repository layout.
 *
 * Filename format:
 * `<group id>-<artifact id>-<version>[-<classifier>].<extension>[.<signature>|.<digest>]`
 *
 * Read [Maven Repository Layout](https://maven.apache.org/repository/layout.html)
 * for more details on the format and requirements imposed on a valid repository path.
 *
 * The implementation is inspired by the [maven-indexer](https://github.com/apache/maven-indexer/blob/master/indexer-core/src/main/java/org/apache/maven/index/artifact/M2GavCalculator.java).
 *
 * @return extracted [ArtifactInfo] or a validation exception.
 * @throws IllegalArgumentException when this [Path] is absolute.
 */
internal fun Path.extractArtifactInfo(fullPath: Path = this): Result<ArtifactInfo> {
    require(!isAbsolute) {
        "Only relative paths are allowed, but the function was invoked with an absolute path $fullPath"
    }
    val gav = extractGav().getOrElse { return Result.failure(it) }

    // nameSuffix is consumed (i.e. truncated and updated) during the parsing process.
    var nameSuffix = fileName.toString()
    // Validate artifact ID
    if (!nameSuffix.startsWith(gav.artifactId + "-")) return Result.failure(IllegalArgumentException(
        "Artifact filename prefix should match artifact ID (${gav.artifactId}): $fullPath"
    ))
    nameSuffix = nameSuffix.drop(gav.artifactId.length + 1)

    // Extract and validate version
    val isSnapshot = gav.baseVersion.endsWith("-SNAPSHOT")
    // For regular version, the filename has to contain the exact version.
    // For snapshot versions, the filename can either contain the same version, or ... (see the else branch)
    val effectiveVersion = if (!isSnapshot || nameSuffix.startsWith(gav.baseVersion)) {
        if (!nameSuffix.startsWith(gav.baseVersion)) return Result.failure(IllegalArgumentException(
            "Artifact ID in a filename does not contain a version, " +
                    "or the version does not match a version " +
                    "extracted from a parent directory name (${gav.baseVersion}): $fullPath"
        ))
        nameSuffix = nameSuffix.drop(gav.baseVersion.length)
        gav.baseVersion
    } else {
        // ... or the version in the filename has to follow pattern "XXX-YYYYMMDD.HHmmSS-<counter>",
        // where XXX should match the substring before the "-SNAPSHOT" and the counter is an integer value.
        val versionPrefix = gav.baseVersion.dropLast(8 /* "SNAPSHOT".length */)

        val re = Regex("(${Regex.escape(versionPrefix)}[0-9]{8}\\.[0-9]{6}-[0-9]+).+")
        val match = re.matchEntire(nameSuffix) ?: return Result.failure(IllegalArgumentException(
            "Invalid snapshot version format in filename: it should be either ${gav.baseVersion} or " +
                    "match the pattern ${versionPrefix}YYYYMMDD.HHMMSS-N: $fullPath"
        ))
        match.groupValues[1].also {
            nameSuffix = nameSuffix.drop(it.length)
        }
    }
    if (nameSuffix.startsWith("-")) {
        nameSuffix = nameSuffix.drop(1)
    }

    // Extract and validate a classifier (a substring between the version and the extension) and an extension.
    val dotPos = nameSuffix.indexOf('.')
    if (dotPos < 0) return Result.failure(IllegalArgumentException("Artifact file has no extension: $fullPath"))
    val classifier = nameSuffix.substring(0, dotPos)
    var extension = nameSuffix.substring(dotPos + 1)
    // "grp/artfct/0.0.0/artfct-0.0.0.asc" are not allowed, signature (or checksum) should correspond to some other
    // file that has an extension.
    if (extension in specialExtensions) return Result.failure(IllegalArgumentException(
        "Signature or checksum files are not allowed for artifacts without an extension: $fullPath"
    ))

    // The very last part of an extension, if it has multiple dots. I.e. "asc" for "tar.gz.asc".
    val trailingExtension = extension.lastIndexOf('.').let {
        if (it == -1) ""
        else extension.substring(it + 1)
    }

    // Check if it is a checksum or a signature file
    val signatureType = signatureTypeValues.find { it.extension == trailingExtension }
    val digestType = checksumTypeValues.find { it.extension == trailingExtension }
    val isSpecialFile = signatureType != null || digestType != null

    val fileNameWithoutSuffix = if (isSpecialFile) {
        fileName.toString().dropLast(trailingExtension.length + 1).also {
            // For signatures and checksums, ArtifactInfo is identical to one
            // extracted for the target file (but it includes info about the algorithm).
            // So here were dropping the tailing part from the extension.
            extension = extension.dropLast(trailingExtension.length + 1)
        }
    } else {
        fileName.toString()
    }

    return Result.success(ArtifactInfo(
        gav,
        fullPath.resolveSibling(fileNameWithoutSuffix),
        extension,
        classifier,
        signatureType,
        digestType,
        isSnapshot,
        effectiveVersion
    ))
}

private fun Path.illegalPathFormat(): IllegalArgumentException = IllegalArgumentException(
    "The artifact file has invalid path format: " +
            "it has to contain at least 4 segments, but contained only ${iterator().asSequence().count()}: $this"
)

private fun Path.extractGav(): Result<ArtifactInfo.Gav> {
    val versionDir = parent ?: return Result.failure(illegalPathFormat())
    val artifactIdDir = versionDir.parent ?: return Result.failure(illegalPathFormat())
    val groupIdPath = artifactIdDir.parent ?: return Result.failure(illegalPathFormat())

    return Result.success(ArtifactInfo.Gav(
        groupIdPath.joinToString(".") { it.fileName.toString() },
        artifactIdDir.fileName.toString(),
        versionDir.fileName.toString()
    ))
}

private val checksumTypeValues = ChecksumType.values()
private val signatureTypeValues = SignatureType.values()
private val specialExtensions = signatureTypeValues.map { it.extension }.toSet() + checksumTypeValues.map { it.extension }.toSet()
