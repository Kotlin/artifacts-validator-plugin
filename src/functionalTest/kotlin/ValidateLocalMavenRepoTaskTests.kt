package kotlinx.validation.test

import org.gradle.testkit.runner.TaskOutcome
import kotlin.test.Ignore
import kotlin.test.Test

class ValidateLocalMavenRepoTaskTests : PluginTestBase() {
    @Test
    fun taskReportsUnexpectedArtifacts() {
        copyProjects(
            "/test-projects/publication-with-sources",
            "/test-projects/artifacts/publication/default",
        )

        runAndFail(
            "publishTestPublicationToTestRepository",
            "validateLocalMavenRepo",
            "--artifacts-dir=${projectRoot.resolve("build/test-repo")}",
            "--artifacts-list=${projectRoot.resolve("gradle/artifacts.txt")}:0.0.1"
        ) {
            checkTaskStatus(":publishTestPublicationToTestRepository", TaskOutcome.SUCCESS)
            checkTaskStatus(":validateLocalMavenRepo", TaskOutcome.FAILED)
            outputContains(
                "Following artifacts were not expected, but were found: " +
                    "org.jetbrains.kotlinx:basic-test-project-0.0.1-sources.jar"
            )
        }
    }

    @Test
    fun taskReportsMissingExpectedArtifacts() {
        copyProjects(
            "/test-projects/publication",
            "/test-projects/artifacts/publication/with-sources",
        )

        runAndFail(
            "publishTestPublicationToTestRepository",
            "validateLocalMavenRepo",
            "--artifacts-dir=${projectRoot.resolve("build/test-repo")}",
            "--artifacts-list=${projectRoot.resolve("gradle/artifacts.txt")}:0.0.1"
        ) {
            checkTaskStatus(":publishTestPublicationToTestRepository", TaskOutcome.SUCCESS)
            checkTaskStatus(":validateLocalMavenRepo", TaskOutcome.FAILED)
            outputContains(
                "Following artifacts were expected, but were not found: " +
                    "org.jetbrains.kotlinx:basic-test-project-0.0.1-sources.jar"
            )
        }
    }

    @Test
    fun validateForPublishedRepo() {
        copyProjects(
            "/test-projects/publication-with-sources",
            "/test-projects/artifacts/publication/with-sources",
        )

        run(
            "publishTestPublicationToTestRepository",
            "validateLocalMavenRepo",
            "--artifacts-dir=${projectRoot.resolve("build/test-repo")}",
            "--artifacts-list=${projectRoot.resolve("gradle/artifacts.txt")}:0.0.1"
        ) {
            checkTaskStatus(":publishTestPublicationToTestRepository", TaskOutcome.SUCCESS)
            checkTaskStatus(":validateLocalMavenRepo", TaskOutcome.SUCCESS)
            outputContains("[Artifacts Validation] Artifacts fully matched the list of expected artifacts.")
        }
    }

    @Test
    fun validateWithMultipleArtifactLists() {
        copyProjects(
            "/test-projects/basic",
            "/test-projects/artifacts/basic/multiple",
            "/test-projects/repositories/multiple-lists",
        )

        run(
            "validateLocalMavenRepo",
            "--artifacts-dir=${projectRoot.resolve("build/test-repo")}",
            "--artifacts-list=${projectRoot.resolve("gradle/artifacts-core.txt")}:0.0.1",
            "--artifacts-list=${projectRoot.resolve("gradle/artifacts-ext.txt")}:2025a-0.0.1"
        ) {
            checkTaskStatus(":validateLocalMavenRepo", TaskOutcome.SUCCESS)
            outputContains("[Artifacts Validation] Artifacts fully matched the list of expected artifacts.")
        }
    }

    @Test
    @Ignore // issue with module files
    fun validateForMultiplatformPublication() {
        copyProjects(
            "/test-projects/multiplatform-publication",
            "/test-projects/artifacts/multiplatform/default",
        )

        run(
            "publishAllPublicationsToTestRepository",
            "validateLocalMavenRepo",
            "--artifacts-dir=${projectRoot.resolve("build/test-repo")}",
            "--artifacts-list=${projectRoot.resolve("gradle/artifacts.txt")}:0.0.1"
        ) {
            checkTaskStatus(":publishAllPublicationsToTestRepository", TaskOutcome.SUCCESS)
            checkTaskStatus(":validateLocalMavenRepo", TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun validateZipRepository() {
        copyProjects(
            "/test-projects/basic",
            "/test-projects/artifacts/basic/core",
            "/test-projects/repositories/zipped-core",
        )
        createZipFromDirectory("build/test-repo", "build/test-repo.zip")

        run(
            "validateLocalMavenRepo",
            "--artifacts-zip=${projectRoot.resolve("build/test-repo.zip")}",
            "--artifacts-list=${projectRoot.resolve("gradle/artifacts.txt")}:0.0.1"
        ) {
            checkTaskStatus(":validateLocalMavenRepo", TaskOutcome.SUCCESS)
            outputContains("[Artifacts Validation] Artifacts fully matched the list of expected artifacts.")
        }
    }

    @Test
    fun runWithoutSpecifyingRepository() {
        copyProjects(
            "/test-projects/basic",
            "/test-projects/artifacts/basic/core",
        )

        runAndFail(
            "validateLocalMavenRepo",
            "--artifacts-list=${projectRoot.resolve("gradle/artifacts.txt")}:0.0.1"
        ) {
            checkTaskStatus(":validateLocalMavenRepo", TaskOutcome.FAILED)
            outputContains("Artifact source is not configured. Use either --artifacts-dir or --artifacts-zip.")
        }
    }

    @Test
    fun invalidZipFile() {
        copyProjects(
            "/test-projects/basic",
            "/test-projects/artifacts/basic/empty",
            "/test-projects/repositories/zipped-broken",
        )
        createZipFromDirectory("build/test-repo", "build/test-repo.zip")

        runAndFail(
            "validateLocalMavenRepo",
            "--artifacts-zip=${projectRoot.resolve("build/test-repo.zip")}",
            "--artifacts-list=${projectRoot.resolve("gradle/artifacts.txt")}:0.0.1"
        ) {
            checkTaskStatus(":validateLocalMavenRepo", TaskOutcome.FAILED)
            outputContains("Error detected while reading file")
            outputContains("broken/path")
            outputContains("Errors were detected while loading artifacts info. See log for details")
        }
    }

    @Test
    fun validateChecksumsAndSignatures() {
        copyProjects(
            "/test-projects/basic",
            "/test-projects/artifacts/basic/core",
            "/test-projects/repositories/checksums-and-signatures",
        )

        run(
            "validateLocalMavenRepo",
            "--artifacts-dir=${projectRoot.resolve("build/test-repo")}",
            "--artifacts-list=${projectRoot.resolve("gradle/artifacts.txt")}:0.0.1",
            "--require-signatures",
            "--require-checksums=md5, Sha256"
        ) {
            checkTaskStatus(":validateLocalMavenRepo", TaskOutcome.SUCCESS)
            outputContains("[Artifacts Validation] All artifacts are signed.")
            outputContains("[Artifacts Validation] All artifacts have required checksums.")
        }
    }

    @Test
    fun reportMissingChecksumsAndSignatures() {
        copyProjects(
            "/test-projects/basic",
            "/test-projects/artifacts/basic/core",
            "/test-projects/repositories/pom-and-sha1",
        )

        runAndFail(
            "validateLocalMavenRepo",
            "--artifacts-dir=${projectRoot.resolve("build/test-repo")}",
            "--artifacts-list=${projectRoot.resolve("gradle/artifacts.txt")}:0.0.1",
            "--require-signatures",
            "--require-checksums=md5,sha1"
        ) {
            checkTaskStatus(":validateLocalMavenRepo", TaskOutcome.FAILED)
            outputContains("artifact-core-0.0.1.pom is not signed.")
            outputContains("artifact-core-0.0.1.pom is missing following checksums:")
            outputContains("MD5")
            outputContains("Some artifacts were not signed or missing checksum files. See log for more details.")
        }
    }

    @Test
    fun taskReportsMissingSignatureOnly() {
        copyProjects(
            "/test-projects/basic",
            "/test-projects/artifacts/basic/core",
            "/test-projects/repositories/core",
        )

        runAndFail(
            "validateLocalMavenRepo",
            "--artifacts-dir=${projectRoot.resolve("build/test-repo")}",
            "--artifacts-list=${projectRoot.resolve("gradle/artifacts.txt")}:0.0.1",
            "--require-signatures"
        ) {
            checkTaskStatus(":validateLocalMavenRepo", TaskOutcome.FAILED)
            outputContains("artifact-core-0.0.1.pom is not signed.")
            outputContains("Some artifacts were not signed or missing checksum files. See log for more details.")
        }
    }

    @Test
    fun taskRejectsInvalidChecksumType() {
        copyProjects(
            "/test-projects/basic",
            "/test-projects/artifacts/basic/core",
        )
        createDir("build/test-repo")

        runAndFail(
            "validateLocalMavenRepo",
            "--artifacts-dir=${projectRoot.resolve("build/test-repo")}",
            "--artifacts-list=${projectRoot.resolve("gradle/artifacts.txt")}:0.0.1",
            "--require-checksums=md5,sha999"
        ) {
            checkTaskStatus(":validateLocalMavenRepo", TaskOutcome.FAILED)
            outputContains("Invalid checksum type: sha999. Use one of MD5, SHA1, SHA256, SHA512")
        }
    }

    @Test
    fun checkArtifactsListWithoutFilePath() {
        copyProject("/test-projects/basic")

        runAndFail(
            "validateLocalMavenRepo",
            "--artifacts-dir=${projectRoot.resolve("build/test-repo")}",
            "--artifacts-list=:0.0.1"
        ) {
            outputContains("artifacts-list value must use the format <file>:<version>, was: \":0.0.1\".")
        }
    }

    @Test
    fun checkArtifactsListWithoutVersion() {
        copyProject("/test-projects/basic")

        runAndFail(
            "validateLocalMavenRepo",
            "--artifacts-dir=${projectRoot.resolve("build/test-repo")}",
            "--artifacts-list=${projectRoot.resolve("gradle/artifacts.txt")}"
        ) {
            outputContains(
                "artifacts-list value must use the format <file>:<version>, was: " +
                        "\"${projectRoot.resolve("gradle/artifacts.txt")}\"."
            )
        }
    }

    @Test
    fun taskFailsForOrphanedChecksumFile() {
        copyProjects(
            "/test-projects/basic",
            "/test-projects/artifacts/basic/core",
            "/test-projects/repositories/orphaned-sha1",
        )

        runAndFail(
            "validateLocalMavenRepo",
            "--artifacts-dir=${projectRoot.resolve("build/test-repo")}",
            "--artifacts-list=${projectRoot.resolve("gradle/artifacts.txt")}:0.0.1"
        ) {
            checkTaskStatus(":validateLocalMavenRepo", TaskOutcome.FAILED)
            outputContains("There are checksum and/or signature files corresponding to an artifact, but the main artifact file does not exist:")
            outputContains("Errors were detected while loading artifacts info. See log for details")
        }
    }

    @Test
    fun taskRejectsMultipleArtifactSources() {
        copyProjects(
            "/test-projects/basic",
            "/test-projects/artifacts/basic/core",
            "/test-projects/repositories/zipped-core",
        )
        createZipFromDirectory("build/test-repo", "build/test-repo.zip")

        runAndFail(
            "validateLocalMavenRepo",
            "--artifacts-dir=${projectRoot.resolve("build/test-repo")}",
            "--artifacts-zip=${projectRoot.resolve("build/test-repo.zip")}",
            "--artifacts-list=${projectRoot.resolve("gradle/artifacts.txt")}:0.0.1"
        ) {
            checkTaskStatus(":validateLocalMavenRepo", TaskOutcome.FAILED)
            outputContains("Only one artifact source can be configured. Use either --artifacts-dir or --artifacts-zip.")
        }
    }

    @Test
    fun taskRejectsInvalidRulesFile() {
        copyProjects(
            "/test-projects/basic",
            "/test-projects/artifacts/basic/invalid",
            "/test-projects/repositories/core",
        )

        runAndFail(
            "validateLocalMavenRepo",
            "--artifacts-dir=${projectRoot.resolve("build/test-repo")}",
            "--artifacts-list=${projectRoot.resolve("gradle/artifacts.txt")}:0.0.1"
        ) {
            checkTaskStatus(":validateLocalMavenRepo", TaskOutcome.FAILED)
            outputContains("Error while parsing rules file")
            outputContains("Rule should contain exactly one '/'")
        }
    }

    @Test
    fun taskFailsWhenRulesFileAreMissing() {
        copyProjects(
            "/test-projects/basic",
            "/test-projects/repositories/core",
        )

        runAndFail(
            "validateLocalMavenRepo",
            "--artifacts-dir=${projectRoot.resolve("build/test-repo")}",
            "--artifacts-list=${projectRoot.resolve("gradle/missing-artifacts.txt")}:0.0.1"
        ) {
            checkTaskStatus(":validateLocalMavenRepo", TaskOutcome.FAILED)
            outputContains("Artifacts list file does not exist:")
            outputContains("missing-artifacts.txt")
            outputContains("Failed to load rules file from files. See log for more details.")
        }
    }
}
