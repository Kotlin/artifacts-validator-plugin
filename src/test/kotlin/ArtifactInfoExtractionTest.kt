package kotlinx.validation.artifacts

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

internal fun assertThat(ai: ArtifactInfo, block: ArtifactInfoAsserter.() -> Unit) {
    ArtifactInfoAsserter(ai).apply(block)
}

internal class ArtifactInfoAsserter(val ai: ArtifactInfo) {
    fun hasGroupId(groupId: String) = assertEquals(groupId, ai.gav.groupId, "Unexpected group ID")
    fun hasArtifactId(artifactId: String) = assertEquals(artifactId, ai.gav.artifactId, "Unexpected artifactId ID")

    fun hasBaseVersion(version: String) = assertEquals(version, ai.gav.baseVersion, "Unexpected version")
    fun hasEffectiveVersion(version: String) = assertEquals(version, ai.version, "Unexpected version")

    fun hasCoordinates(groupId: String, artifactId: String, version: String) {
        hasGroupId(groupId)
        hasArtifactId(artifactId)
        hasBaseVersion(version)
    }

    fun hasClassifier(classifier: String) = assertEquals(classifier, ai.classifier)
    fun hasNoClassifier() = hasClassifier("")
    fun hasExtension(extension: String) = assertEquals(extension, ai.extension)
    fun isSignatureFile() = assertEquals(SignatureType.PGP, ai.signatureType)
    fun isDigestFile(checksumType: ChecksumType) = assertEquals(checksumType, ai.checksumType)
    fun isRegularFile() {
        assertNull(ai.signatureType, "Expected a regular file, but it was a signature file")
        assertNull(ai.checksumType, "Expected a regular file, but it was a digest file (${ai.checksumType})")
    }

    fun hasBaseFileName(name: String) = assertEquals(name, ai.fileName)
}

class ArtifactInfoExtractionTest {
    private fun artifactInfo(from: String): ArtifactInfo {
        return Path(from).extractArtifactInfo().getOrThrow()
    }

    @Test
    fun testRegularVersions() {
        assertThat(artifactInfo("org/jetbrains/kotlinx/artifact-validator-plugin/0.0.1/artifact-validator-plugin-0.0.1.pom")) {
            hasCoordinates("org.jetbrains.kotlinx", "artifact-validator-plugin", "0.0.1")
            hasNoClassifier()
            isRegularFile()
            hasExtension("pom")
            hasBaseFileName("artifact-validator-plugin-0.0.1.pom")
        }

        assertThat(artifactInfo("org/jetbrains/kotlinx/artifact-validator-plugin/0.0.1/artifact-validator-plugin-0.0.1.pom.asc")) {
            hasCoordinates("org.jetbrains.kotlinx", "artifact-validator-plugin", "0.0.1")
            hasNoClassifier()
            isSignatureFile()
            hasExtension("pom")
            hasBaseFileName("artifact-validator-plugin-0.0.1.pom")
        }

        assertThat(artifactInfo("org/jetbrains/kotlinx/artifact-validator-plugin/0.0.1/artifact-validator-plugin-0.0.1.pom.sha512")) {
            hasCoordinates("org.jetbrains.kotlinx", "artifact-validator-plugin", "0.0.1")
            hasNoClassifier()
            isDigestFile(ChecksumType.SHA512)
            hasExtension("pom")
            hasBaseFileName("artifact-validator-plugin-0.0.1.pom")
        }

        assertThat(artifactInfo("org/jetbrains/kotlinx/artifact-validator-plugin/0.0.1/artifact-validator-plugin-0.0.1.jar")) {
            hasCoordinates("org.jetbrains.kotlinx", "artifact-validator-plugin", "0.0.1")
            hasNoClassifier()
            isRegularFile()
            hasExtension("jar")
            hasBaseFileName("artifact-validator-plugin-0.0.1.jar")
        }

        assertThat(artifactInfo("org/jetbrains/kotlinx/artifact-validator-plugin/0.0.1/artifact-validator-plugin-0.0.1-sources.jar")) {
            hasCoordinates("org.jetbrains.kotlinx", "artifact-validator-plugin", "0.0.1")
            hasClassifier("sources")
            isRegularFile()
            hasExtension("jar")
            hasBaseFileName("artifact-validator-plugin-0.0.1-sources.jar")
        }

        assertThat(artifactInfo("org/jetbrains/kotlinx/artifact-validator-plugin/0.0.1/artifact-validator-plugin-0.0.1-sources.jar.md5")) {
            hasCoordinates("org.jetbrains.kotlinx", "artifact-validator-plugin", "0.0.1")
            hasClassifier("sources")
            isDigestFile(ChecksumType.MD5)
            hasExtension("jar")
            hasBaseFileName("artifact-validator-plugin-0.0.1-sources.jar")
        }

        assertThat(artifactInfo("org/jetbrains/kotlinx/artifact-validator-plugin/0.0.1-dev.1/artifact-validator-plugin-0.0.1-dev.1-kotlin-tooling-metadata.json")) {
            hasCoordinates("org.jetbrains.kotlinx", "artifact-validator-plugin", "0.0.1-dev.1")
            isRegularFile()
            hasClassifier("kotlin-tooling-metadata")
            hasExtension("json")
            hasBaseFileName("artifact-validator-plugin-0.0.1-dev.1-kotlin-tooling-metadata.json")
        }
    }

    @Test
    fun testSnapshotVersion() {
        assertThat(artifactInfo("org/jetbrains/kotlinx/artifacts-validator-plugin/org.jetbrains.kotlinx.artifacts-validator-plugin.gradle.plugin/1.0-SNAPSHOT/org.jetbrains.kotlinx.artifacts-validator-plugin.gradle.plugin-1.0-20260205.193350-1.pom")) {
            hasGroupId("org.jetbrains.kotlinx.artifacts-validator-plugin")
            hasArtifactId("org.jetbrains.kotlinx.artifacts-validator-plugin.gradle.plugin")
            hasBaseVersion("1.0-SNAPSHOT")
            hasEffectiveVersion("1.0-20260205.193350-1")
            hasExtension("pom")
            hasNoClassifier()
            hasBaseFileName("org.jetbrains.kotlinx.artifacts-validator-plugin.gradle.plugin-1.0-20260205.193350-1.pom")
        }

        assertThat(artifactInfo("org/jetbrains/kotlinx/artifacts-validator-plugin/org.jetbrains.kotlinx.artifacts-validator-plugin.gradle.plugin/1.0-SNAPSHOT/org.jetbrains.kotlinx.artifacts-validator-plugin.gradle.plugin-1.0-20260205.193350-1-sources.jar")) {
            hasGroupId("org.jetbrains.kotlinx.artifacts-validator-plugin")
            hasArtifactId("org.jetbrains.kotlinx.artifacts-validator-plugin.gradle.plugin")
            hasBaseVersion("1.0-SNAPSHOT")
            hasEffectiveVersion("1.0-20260205.193350-1")
            hasExtension("jar")
            hasClassifier("sources")
            hasBaseFileName("org.jetbrains.kotlinx.artifacts-validator-plugin.gradle.plugin-1.0-20260205.193350-1-sources.jar")
        }

        assertThat(artifactInfo("org/jetbrains/kotlinx/artifacts-validator-plugin/org.jetbrains.kotlinx.artifacts-validator-plugin.gradle.plugin/1.0-SNAPSHOT/org.jetbrains.kotlinx.artifacts-validator-plugin.gradle.plugin-1.0-SNAPSHOT.pom")) {
            hasCoordinates(
                "org.jetbrains.kotlinx.artifacts-validator-plugin",
                "org.jetbrains.kotlinx.artifacts-validator-plugin.gradle.plugin",
                "1.0-SNAPSHOT"
            )
            hasExtension("pom")
            hasNoClassifier()
            hasBaseFileName("org.jetbrains.kotlinx.artifacts-validator-plugin.gradle.plugin-1.0-SNAPSHOT.pom")
        }

        assertThat(artifactInfo("org/jetbrains/kotlinx/artifacts-validator-plugin/org.jetbrains.kotlinx.artifacts-validator-plugin.gradle.plugin/1.0-SNAPSHOT/org.jetbrains.kotlinx.artifacts-validator-plugin.gradle.plugin-1.0-SNAPSHOT-javadoc.jar")) {
            hasCoordinates(
                "org.jetbrains.kotlinx.artifacts-validator-plugin",
                "org.jetbrains.kotlinx.artifacts-validator-plugin.gradle.plugin",
                "1.0-SNAPSHOT"
            )
            hasExtension("jar")
            hasClassifier("javadoc")
            hasBaseFileName("org.jetbrains.kotlinx.artifacts-validator-plugin.gradle.plugin-1.0-SNAPSHOT-javadoc.jar")
        }
    }

    @Test
    fun testInvalidFiles() {
        fun fsPath(path: String) = Path(path).toString()

        // no extension
        assertFailsWith<IllegalArgumentException> {
            artifactInfo("org/jetbrains/kotlinx/artifact-validator-plugin/0.0.1/artifact-validator-plugin-0.0.1")
        }.also {
            assertEquals(
                "Artifact file has no extension: " +
                        fsPath("org/jetbrains/kotlinx/artifact-validator-plugin/0.0.1/artifact-validator-plugin-0.0.1"),
                it.message
            )
        }

        assertFailsWith<IllegalArgumentException> {
            artifactInfo("org/jetbrains/kotlinx/artifact-validator-plugin/0.0.1/artifact-validator-plugin-0.0.1.asc")
        }.also {
            assertEquals(
                "Signature or checksum files are not allowed for artifacts without an extension: " +
                        fsPath("org/jetbrains/kotlinx/artifact-validator-plugin/0.0.1/artifact-validator-plugin-0.0.1.asc"),
                it.message
            )
        }

        // wrong version
        assertFailsWith<IllegalArgumentException> {
            artifactInfo("org/jetbrains/kotlinx/artifact-validator-plugin/0.0.1/artifact-validator-plugin-1.1.1.pom")
        }.also {
            assertEquals(
                "Artifact ID in a filename does not contain a version," +
                        " or the version does not match a version extracted from a parent directory name (0.0.1): " +
                        fsPath("org/jetbrains/kotlinx/artifact-validator-plugin/0.0.1/artifact-validator-plugin-1.1.1.pom"),
                it.message
            )
        }

        assertFailsWith<IllegalArgumentException> {
            artifactInfo("org/jetbrains/kotlinx/artifact-validator-plugin/1.0-SNAPSHOT/artifact-validator-plugin-1.1.1.pom")
        }.also {
            assertEquals(
                "Invalid snapshot version format in filename: it should be either 1.0-SNAPSHOT or " +
                        "match the pattern 1.0-YYYYMMDD.HHMMSS-N: " +
                        fsPath("org/jetbrains/kotlinx/artifact-validator-plugin/1.0-SNAPSHOT/artifact-validator-plugin-1.1.1.pom"),
                it.message
            )
        }

        // wrong filename
        assertFailsWith<IllegalArgumentException> {
            artifactInfo("org/jetbrains/kotlinx/artifact-validator-plugin/0.0.1/artifact-verifier-plugin-0.0.1.pom")
        }.also {
            assertEquals(
                "Artifact filename prefix should match artifact ID (artifact-validator-plugin): " +
                        fsPath("org/jetbrains/kotlinx/artifact-validator-plugin/0.0.1/artifact-verifier-plugin-0.0.1.pom"),
                it.message
            )
        }

        // wrong directory tree
        assertFailsWith<IllegalArgumentException> {
            artifactInfo("artifact-validator-plugin/0.0.1/artifact-validator-plugin-0.0.1.pom")
        }.also {
            assertEquals(
                "The artifact file has invalid path format: " +
                        "it has to contain at least 4 segments, but contained only 3: " +
                        fsPath("artifact-validator-plugin/0.0.1/artifact-validator-plugin-0.0.1.pom"), it.message
            )
        }

        assertFailsWith<IllegalArgumentException> {
            artifactInfo("0.0.1/artifact-validator-plugin-0.0.1.pom")
        }.also {
            assertEquals(
                "The artifact file has invalid path format: " +
                        "it has to contain at least 4 segments, but contained only 2: " +
                        fsPath("0.0.1/artifact-validator-plugin-0.0.1.pom"), it.message
            )
        }

        assertFailsWith<IllegalArgumentException> {
            artifactInfo("artifact-validator-plugin-0.0.1.pom")
        }.also {
            assertEquals(
                "The artifact file has invalid path format: " +
                        "it has to contain at least 4 segments, but contained only 1: " +
                        "artifact-validator-plugin-0.0.1.pom", it.message
            )
        }
    }

    @Test
    fun rejectAbsolutePath() {
        val absolutePath = Path("artifact-validator-plugin-0.0.1.pom").toAbsolutePath()

        assertFailsWith<IllegalArgumentException> {
            absolutePath.extractArtifactInfo()
        }.also {
            assertEquals(
                "Only relative paths are allowed, but the function was invoked with an absolute path $absolutePath",
                it.message
            )
        }
    }
}
