package kotlinx.validation

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.relativeTo
import kotlin.test.*

class RepositoryScanningTest {
    @field:TempDir
    lateinit var repositoryRoot: File

    private fun buildRepository(vararg files: String) {
        files.forEach {
            val artifactFile = File(repositoryRoot, it)
            artifactFile.parentFile.mkdirs()
            artifactFile.createNewFile()
        }
    }

    private fun buildRepositoryZip(path: String, vararg files: String): File {
        val zipFile = File(repositoryRoot, path)
        zipFile.parentFile.mkdirs()
        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            files.forEach { file ->
                zip.putNextEntry(ZipEntry(file))
                zip.closeEntry()
            }
        }
        return zipFile
    }

    private fun AugmentedArtifactInfo.assertSignedAndHasChecksums(vararg checksum: ChecksumType) {
        assertTrue(hasChecksums)
        assertEquals(checksum.toSet(), checksumTypes)

        assertTrue(isSigned)
        assertEquals(setOf(SignatureType.PGP), signatureTypes)
    }

    private fun AugmentedArtifactInfo.assertUnsignedAndHasNoChecksums() {
        assertFalse(hasChecksums)
        assertTrue(checksumTypes.isEmpty())

        assertFalse(isSigned)
        assertTrue(signatureTypes.isEmpty())
    }

    private fun ArtifactInfo.assertBasicArtifact(groupId: String, artifactId: String, version: String, ext: String) {
        assertThat(this) {
            hasCoordinates(groupId, artifactId, version)
            isRegularFile()
            hasNoClassifier()
            hasExtension(ext)
        }
    }

    @Test
    fun scanRegularRepository() {
        buildRepository(
            "org/jetbrains/kotlinx/kotlinx-io-core/0.8.0/kotlinx-io-core-0.8.0.pom",
            "org/jetbrains/kotlinx/kotlinx-io-core/0.8.0/kotlinx-io-core-0.8.0.pom.asc",
            "org/jetbrains/kotlinx/kotlinx-io-core/0.8.0/kotlinx-io-core-0.8.0.pom.md5",
            "org/jetbrains/kotlinx/kotlinx-io-core/0.8.0/kotlinx-io-core-0.8.0.pom.sha1",

            "org/jetbrains/kotlinx/kotlinx-io-core/0.8.1/kotlinx-io-core-0.8.1.pom",

            "org/jetbrains/kotlinx/kotlinx-io-core-jvm/0.8.0/kotlinx-io-core-jvm-0.8.0.pom",
            "org/jetbrains/kotlinx/kotlinx-io-core-jvm/0.8.0/kotlinx-io-core-jvm-0.8.0.pom.asc",
            "org/jetbrains/kotlinx/kotlinx-io-core-jvm/0.8.0/kotlinx-io-core-jvm-0.8.0.pom.md5",
            "org/jetbrains/kotlinx/kotlinx-io-core-jvm/0.8.0/kotlinx-io-core-jvm-0.8.0.pom.sha1",
            "org/jetbrains/kotlinx/kotlinx-io-core-jvm/0.8.0/kotlinx-io-core-jvm-0.8.0.jar",
            "org/jetbrains/kotlinx/kotlinx-io-core-jvm/0.8.0/kotlinx-io-core-jvm-0.8.0.jar.asc",
            "org/jetbrains/kotlinx/kotlinx-io-core-jvm/0.8.0/kotlinx-io-core-jvm-0.8.0.jar.md5",
            "org/jetbrains/kotlinx/kotlinx-io-core-jvm/0.8.0/kotlinx-io-core-jvm-0.8.0.jar.sha1",
        )

        val artifacts = repositoryRoot.toPath().scanRepository { path, exception ->
            fail("No errors were expected, but got $exception for $path")
        }

        assertEquals(3, artifacts.size)

        artifacts.find { it.gav.artifactId == "kotlinx-io-core" && it.gav.baseVersion == "0.8.0" }!!.let { artifact ->
            assertEquals(
                ArtifactInfo.Gav("org.jetbrains.kotlinx", "kotlinx-io-core", "0.8.0"),
                artifact.gav
            )

            assertEquals(1, artifact.artifacts.size)
            val pomFile = artifact.artifacts.single()
            assertSame(pomFile, artifact.pom)
            pomFile.assertSignedAndHasChecksums(ChecksumType.SHA1, ChecksumType.MD5)
            pomFile.artifact.assertBasicArtifact("org.jetbrains.kotlinx", "kotlinx-io-core", "0.8.0", "pom")
        }

        artifacts.find { it.gav.artifactId == "kotlinx-io-core" && it.gav.baseVersion == "0.8.1" }!!.let { artifact ->
            assertEquals(
                ArtifactInfo.Gav("org.jetbrains.kotlinx", "kotlinx-io-core", "0.8.1"),
                artifact.gav
            )

            assertEquals(1, artifact.artifacts.size)
            val pomFile = artifact.artifacts.single()
            assertSame(pomFile, artifact.pom)
            pomFile.assertUnsignedAndHasNoChecksums()
            pomFile.artifact.assertBasicArtifact("org.jetbrains.kotlinx", "kotlinx-io-core", "0.8.1", "pom")
        }

        artifacts.find { it.gav.artifactId == "kotlinx-io-core-jvm" }!!.let { artifact ->
            assertEquals(
                ArtifactInfo.Gav("org.jetbrains.kotlinx", "kotlinx-io-core-jvm", "0.8.0"),
                artifact.gav
            )

            assertEquals(2, artifact.artifacts.size)

            val pomFile = assertNotNull(artifact.artifacts.find { it.artifact.extension == "pom" })
            assertSame(pomFile, artifact.pom)
            pomFile.assertSignedAndHasChecksums(ChecksumType.SHA1, ChecksumType.MD5)
            pomFile.artifact.assertBasicArtifact("org.jetbrains.kotlinx", "kotlinx-io-core-jvm", "0.8.0", "pom")

            val jarFile = assertNotNull(artifact.artifacts.find { it.artifact.extension == "jar" })
            jarFile.assertSignedAndHasChecksums(ChecksumType.SHA1, ChecksumType.MD5)
            jarFile.artifact.assertBasicArtifact("org.jetbrains.kotlinx", "kotlinx-io-core-jvm", "0.8.0", "jar")
        }
    }

    @Test
    fun ignoreSpecialFilesAndDirectories() {
        buildRepository(
            ".index/test",
            "org/jetbrains/kotlinx/.index/test",
            "org/jetbrains/kotlinx/.meta/test",
            "archetype-catalog.xml",
            "maven-metadata.xml",
            "org/jetbrains/kotlinx/maven-metadata.xml",
            "org/jetbrains/kotlinx/maven-metadata.xml.md5",
            "org/jetbrains/kotlinx/maven-metadata.xml.sha1",
            "org/jetbrains/kotlinx/maven-metadata.xml.sha256",
            "org/jetbrains/kotlinx/maven-metadata.xml.sha512",
        )

        assertTrue(repositoryRoot.toPath().scanRepository { path, exception ->
            fail("No errors were expected, but got $exception for $path")
        }.isEmpty())
    }

    @Test
    fun scanZipRepository() {
        val repositoryZip = buildRepositoryZip(
            "repo.zip",
            "org/jetbrains/kotlinx/kotlinx-io-core/0.8.0/kotlinx-io-core-0.8.0.pom",
            "org/jetbrains/kotlinx/kotlinx-io-core/0.8.0/kotlinx-io-core-0.8.0.pom.asc",
            "org/jetbrains/kotlinx/kotlinx-io-core/0.8.0/kotlinx-io-core-0.8.0.pom.md5",
            ".index/test",
            "org/jetbrains/kotlinx/.meta/test",
            "org/jetbrains/kotlinx/maven-metadata.xml",
        )

        val zipUri = URI.create("jar:${repositoryZip.toPath().toUri()}")
        val artifacts = FileSystems.newFileSystem(zipUri, mapOf<String, String>()).use { zipFs ->
            zipFs.getPath("/").scanRepository { path, exception ->
                fail("No errors were expected, but got $exception for $path")
            }
        }

        assertEquals(1, artifacts.size)
        artifacts.single().let { artifact ->
            assertEquals(
                ArtifactInfo.Gav("org.jetbrains.kotlinx", "kotlinx-io-core", "0.8.0"),
                artifact.gav
            )

            val pomFile = artifact.artifacts.single()
            assertSame(pomFile, artifact.pom)
            pomFile.assertSignedAndHasChecksums(ChecksumType.MD5)
            pomFile.artifact.assertBasicArtifact("org.jetbrains.kotlinx", "kotlinx-io-core", "0.8.0", "pom")
        }
    }

    @Test
    fun errorPropagationFromParser() {
        buildRepository("test/file")

        val root = repositoryRoot.toPath()
        val errors = mutableListOf<Pair<Path, String>>()

        root.scanRepository { path, exception ->
            errors.add(path.relativeTo(root) to exception.message!!)
        }

        assertEquals(1, errors.size)
        assertEquals("test/file", errors.single().first.toString())

        val expectedError = "The artifact file has invalid path format: " +
                "it has to contain at least 4 segments, but contained only 2: test/file"
        assertEquals(expectedError, errors.single().second)
    }

    @Test
    fun specialFilesWithoutMainArtifact() {
        buildRepository(
            "org/jetbrains/kotlinx/kotlinx-io-bytestring/0.8.4/kotlinx-io-bytestring-0.8.4.jar.asc",
            "org/jetbrains/kotlinx/kotlinx-io-bytestring/0.8.4/kotlinx-io-bytestring-0.8.4.pom.md5"
        )

        val root = repositoryRoot.toPath()
        val errors = mutableMapOf<String, MutableList<String>>()

        root.scanRepository { path, exception ->
            errors.getOrPut(path.relativeTo(root).toString()) { mutableListOf() }.add(exception.message!!)
        }

        assertEquals(2, errors.size)

        fun checkErrorForFile(path: String) {
            assertTrue(errors.containsKey(path))
            val fileErrors = errors[path]!!
            assertEquals(1, fileErrors.size)
            val errorMessage = fileErrors.first()
            assertContains(
                errorMessage,
                "There are checksum and/or signature files corresponding to an artifact, " +
                        "but the main artifact file does not exist:"
            )
        }

        checkErrorForFile("org/jetbrains/kotlinx/kotlinx-io-bytestring/0.8.4/kotlinx-io-bytestring-0.8.4.jar")
        checkErrorForFile("org/jetbrains/kotlinx/kotlinx-io-bytestring/0.8.4/kotlinx-io-bytestring-0.8.4.pom")
    }

    @Test
    fun multipleSnapshots() {
        buildRepository(
            "org/jetbrains/kotlinx/kotlinx-io-bytestring/0.8.4-SNAPSHOT/kotlinx-io-bytestring-0.8.4-20260206.112233-1.jar",
            "org/jetbrains/kotlinx/kotlinx-io-bytestring/0.8.4-SNAPSHOT/kotlinx-io-bytestring-0.8.4-20260206.112233-1.jar.asc",
            "org/jetbrains/kotlinx/kotlinx-io-bytestring/0.8.4-SNAPSHOT/kotlinx-io-bytestring-0.8.4-20260206.112233-2.jar",
            "org/jetbrains/kotlinx/kotlinx-io-bytestring/0.8.4-SNAPSHOT/kotlinx-io-bytestring-0.8.4-20260206.112233-2.jar.md5",
            "org/jetbrains/kotlinx/kotlinx-io-bytestring/0.8.4-SNAPSHOT/kotlinx-io-bytestring-0.8.4-20260206.112233-10.jar",
            )

        val root = repositoryRoot.toPath()

        var reportedErrors = false
        assertTrue(root.scanRepository { _, _ ->
            reportedErrors = true
        }.isEmpty())
        assertTrue(reportedErrors)

        val results = root.scanRepository(SnapshotResolutionStrategy.LATEST_FILE) { file, exception ->
            fail("Reported error for: $file: $exception")
        }
        assertEquals(1, results.size)
        results.first().artifacts.single().let { artifact ->
            assertEquals(
                "kotlinx-io-bytestring-0.8.4-20260206.112233-10.jar",
                artifact.artifact.fileName
            )
            assertFalse(artifact.isSigned)
            assertFalse(artifact.hasChecksums)
        }
    }
}
