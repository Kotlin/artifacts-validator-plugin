package kotlinx.validation.test

import org.gradle.testkit.runner.TaskOutcome
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginTests : PluginTestBase("/test-projects/basic") {
    @Test
    fun listTasks() {
        copySettingsKts()
        copyBuildKts()

        run("tasks", "--all") {
            outputContains("checkArtifacts")
            outputContains("dumpArtifacts")
            outputContains("validateLocalMavenRepo")
        }
    }

    @Test
    fun checkArtifacts() {
        copySettingsKts()
        copyBuildKts(publicationBlock(artifactId = "basic-test-project"))
        createFile("gradle/artifacts.txt", "org.jetbrains.kotlinx:basic-test-project/.jar,.pom\n")

        run("check") {
            checkTaskStatus(":checkArtifacts", TaskOutcome.SUCCESS)
            checkTaskStatus(":check", TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun checkArtifactsWithoutRulesFile() {
        copySettingsKts()
        copyBuildKts(publicationBlock(artifactId = "basic-test-project"))

        runAndFail("checkArtifacts") {
            checkTaskStatus(":checkArtifacts", TaskOutcome.FAILED)
            outputContains("[Artifacts Validation] Following artifacts were not expected, but were found: " +
                    "org.jetbrains.kotlinx:basic-test-project.jar, org.jetbrains.kotlinx:basic-test-project.pom")
        }
    }

    @Test
    fun validateLocalMavenRepoReportsUnexpectedArtifacts() {
        copySettingsKts()
        copyBuildKts(publicationBlock(artifactId = "basic-test-project", withSources = true))
        createFile("gradle/artifacts.txt", publishedArtifactsRule("basic-test-project"))
        useGradleVersion("8.5")

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
    fun validateLocalMavenRepoReportsMissingExpectedArtifacts() {
        copySettingsKts()
        copyBuildKts(publicationBlock(artifactId = "basic-test-project"))
        createFile("gradle/artifacts.txt", publishedArtifactsRule("basic-test-project", withSources = true))
        useGradleVersion("8.5")

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
    fun dumpArtifacts() {
        copySettingsKts()
        copyBuildKts(publicationBlock(artifactId = "basic-test-project", withSources = true))

        run("dumpArtifacts") {
            checkTaskStatus(":dumpArtifacts", TaskOutcome.SUCCESS)
        }

        assertEquals(
            "org.jetbrains.kotlinx:basic-test-project/.jar,.pom,sources.jar\n",
            projectRoot.resolve("gradle/artifacts.txt").readText()
        )
    }

    @Test
    fun dumpArtifactsWithOverriddenRulesFile() {
        copySettingsKts(
            """

            extensions.configure<kotlinx.validation.ArtifactsValidatorPluginSettingsExtension>("artifactsValidation") {
                dumpFileNamePrefix.set("custom-artifacts")
                dumpFileRootDirectory.set(rootDir.resolve("expected"))
            }
            """.trimIndent()
        )
        copyBuildKts(publicationBlock(artifactId = "basic-test-project"))
        createFile("expected/custom-artifacts.txt", "org.jetbrains.kotlinx:basic-test-project/.jar,.pom\n")

        run("checkArtifacts") {
            checkTaskStatus(":checkArtifacts", TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun dumpArtifactsToProjectSpecificFiles() {
        copySettingsKts(
            """

            include(":lib")
            include(":ext")

            extensions.configure<kotlinx.validation.ArtifactsValidatorPluginSettingsExtension>("artifactsValidation") {
                usePerProjectDumps.set(true)
            }
            """.trimIndent()
        )
        copyBuildKts()
        copyBuildFile(
            "lib/build.gradle.kts",
            publicationBlock(artifactId = "lib")
        )
        copyBuildFile(
            "ext/build.gradle.kts",
            publicationBlock(artifactId = "ext")
        )

        run("dumpArtifacts") {
            checkTaskStatus(":dumpArtifacts", TaskOutcome.SUCCESS)
        }

        assertEquals(
            "org.jetbrains.kotlinx:lib/.jar,.pom\n",
            projectRoot.resolve("gradle/artifacts-lib.txt").readText()
        )
        assertEquals(
            "org.jetbrains.kotlinx:ext/.jar,.pom\n",
            projectRoot.resolve("gradle/artifacts-ext.txt").readText()
        )
    }

    @Test
    fun checkArtifactsUsingProjectSpecificRuleFiles() {
        copySettingsKts(
            """

            include(":lib")
            include(":ext")

            extensions.configure<kotlinx.validation.ArtifactsValidatorPluginSettingsExtension>("artifactsValidation") {
                usePerProjectDumps.set(true)
            }
            """.trimIndent()
        )
        copyBuildKts()
        copyBuildFile(
            "lib/build.gradle.kts",
            publicationBlock(artifactId = "lib")
        )
        copyBuildFile(
            "ext/build.gradle.kts",
            publicationBlock(artifactId = "ext")
        )
        createFile("gradle/artifacts-basic-test-project.txt")
        createFile("gradle/artifacts-lib.txt", "org.jetbrains.kotlinx:lib/.jar,.pom\n")
        createFile("gradle/artifacts-ext.txt", "org.jetbrains.kotlinx:ext/.jar,.pom\n")

        run("checkArtifacts") {
            checkTaskStatus(":checkArtifacts", TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun validateLocalMavenRepoForPublishedRepo() {
        copySettingsKts()
        copyBuildKts(publicationBlock(artifactId = "basic-test-project", withSources = true))
        createFile("gradle/artifacts.txt", publishedArtifactsRule("basic-test-project", withSources = true))
        useGradleVersion("8.5")

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
    fun validateLocalMavenRepoWithMultipleArtifactLists() {
        copySettingsKts()
        copyBuildKts()
        createFile("gradle/artifacts-core.txt", "org.example:artifact-core/.pom\n")
        createFile("gradle/artifacts-ext.txt", "org.example:artifact-ext/.pom\n")
        createFile("build/test-repo/org/example/artifact-core/0.0.1/artifact-core-0.0.1.pom")
        createFile("build/test-repo/org/example/artifact-ext/2025a-0.0.1/artifact-ext-2025a-0.0.1.pom")
        useGradleVersion("8.5")

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
    fun validateLocalMavenRepoForZipRepository() {
        copySettingsKts()
        copyBuildKts()
        createFile("gradle/artifacts.txt", "org.example:artifact-core/.pom\n")
        createZip(
            "build/test-repo.zip",
            "org/example/artifact-core/0.0.1/artifact-core-0.0.1.pom",
        )
        useGradleVersion("8.5")

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
    fun validateLocalMavenRepoValidatesChecksumsAndSignatures() {
        copySettingsKts()
        copyBuildKts()
        createFile("gradle/artifacts.txt", "org.example:artifact-core/.pom\n")
        createRepositoryArtifact(
            "org/example/artifact-core/0.0.1/artifact-core-0.0.1.pom",
            "org/example/artifact-core/0.0.1/artifact-core-0.0.1.pom.asc",
            "org/example/artifact-core/0.0.1/artifact-core-0.0.1.pom.md5",
            "org/example/artifact-core/0.0.1/artifact-core-0.0.1.pom.sha256"
        )
        useGradleVersion("8.5")

        run(
            "validateLocalMavenRepo",
            "--artifacts-dir=${projectRoot.resolve("build/test-repo")}",
            "--artifacts-list=${projectRoot.resolve("gradle/artifacts.txt")}:0.0.1",
            "--require-signatures",
            "--require-checksums=md5",
            "--require-checksums=Sha256"
        ) {
            checkTaskStatus(":validateLocalMavenRepo", TaskOutcome.SUCCESS)
            outputContains("[Artifacts Validation] All artifacts are signed.")
            outputContains("[Artifacts Validation] All artifacts have required checksums.")
        }
    }

    @Test
    fun validateLocalMavenRepoReportsMissingChecksumsAndSignatures() {
        copySettingsKts()
        copyBuildKts()
        createFile("gradle/artifacts.txt", "org.example:artifact-core/.pom\n")
        createRepositoryArtifact("org/example/artifact-core/0.0.1/artifact-core-0.0.1.pom.sha1")
        createRepositoryArtifact("org/example/artifact-core/0.0.1/artifact-core-0.0.1.pom")
        useGradleVersion("8.5")

        runAndFail(
            "validateLocalMavenRepo",
            "--artifacts-dir=${projectRoot.resolve("build/test-repo")}",
            "--artifacts-list=${projectRoot.resolve("gradle/artifacts.txt")}:0.0.1",
            "--require-signatures",
            "--require-checksums=md5",
            "--require-checksums=sha1"
        ) {
            checkTaskStatus(":validateLocalMavenRepo", TaskOutcome.FAILED)
            outputContains("artifact-core-0.0.1.pom is not signed.")
            outputContains("artifact-core-0.0.1.pom is missing following checksums:")
            outputContains("MD5")
            outputContains("Some artifacts were not signed or missing checksum files. See log for more details.")
        }
    }

    @Test
    fun validateLocalMavenRepoRejectsInvalidChecksumType() {
        copySettingsKts()
        copyBuildKts()
        createFile("gradle/artifacts.txt", "org.example:artifact-core/.pom\n")
        createDir("build/test-repo")
        useGradleVersion("8.5")

        runAndFail(
            "validateLocalMavenRepo",
            "--artifacts-dir=${projectRoot.resolve("build/test-repo")}",
            "--artifacts-list=${projectRoot.resolve("gradle/artifacts.txt")}:0.0.1",
            "--require-checksums=sha999"
        ) {
            checkTaskStatus(":validateLocalMavenRepo", TaskOutcome.FAILED)
            outputContains("Invalid checksum type: sha999. Use one of MD5, SHA1, SHA256, SHA512")
        }
    }

    @Test
    fun validateLocalMavenRepoFailsForOrphanedChecksumFile() {
        copySettingsKts()
        copyBuildKts()
        createFile("gradle/artifacts.txt", "org.example:artifact-core/.pom\n")
        createRepositoryArtifact("org/example/artifact-core/0.0.1/artifact-core-0.0.1.pom.sha1")
        useGradleVersion("8.5")

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
    fun validateLocalMavenRepoRejectsMultipleArtifactSources() {
        copySettingsKts()
        copyBuildKts()
        createFile("gradle/artifacts.txt", "org.example:artifact-core/.pom\n")
        createDir("build/test-repo")
        createZip("build/test-repo.zip", "org/example/artifact-core/0.0.1/artifact-core-0.0.1.pom")
        useGradleVersion("8.5")

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
    fun validateLocalMavenRepoRejectsArtifactListWithoutVersion() {
        copySettingsKts()
        copyBuildKts()
        useGradleVersion("8.5")

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
    fun validateLocalMavenRepoRejectsInvalidRulesFile() {
        copySettingsKts()
        copyBuildKts()
        createFile("gradle/artifacts.txt", "not a valid rule\n")
        createRepositoryArtifact("org/example/artifact-core/0.0.1/artifact-core-0.0.1.pom")
        useGradleVersion("8.5")

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
    fun validateLocalMavenRepoRejectsMissingRulesFile() {
        copySettingsKts()
        copyBuildKts()
        createRepositoryArtifact("org/example/artifact-core/0.0.1/artifact-core-0.0.1.pom")
        useGradleVersion("8.5")

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

    @Test
    fun checkArtifactsSeePublicationArtifactsAddedInAfterEvaluate() {
        copySettingsKts()
        copyBuildKts(publicationBlock(artifactId = "basic-test-project", withSources = true, addSourcesInAfterEvaluate = true))
        createFile("gradle/artifacts.txt", "org.jetbrains.kotlinx:basic-test-project/.jar,.pom,sources.jar\n")

        run("checkArtifacts") {
            checkTaskStatus(":checkArtifacts", TaskOutcome.SUCCESS)
        }
    }

    private fun copyBuildFile(path: String, appendText: String? = null) {
        copyFile("$resourcesPath/build.gradle.kts", path, appendText)
    }

    private fun createRepositoryArtifact(vararg paths: String) {
        paths.forEach { createFile("build/test-repo/$it") }
    }

    private fun publicationBlock(
        artifactId: String,
        withSources: Boolean = false,
        addSourcesInAfterEvaluate: Boolean = false
    ): String = """
        val localTestRepository = rootProject.layout.buildDirectory.dir("test-repo")
        val publishedJar = tasks.register<org.gradle.api.tasks.bundling.Jar>("publishedJar") {
            archiveBaseName.set("$artifactId")
        }
        ${if (withSources) """
        val publishedSourcesJar = tasks.register<org.gradle.api.tasks.bundling.Jar>("publishedSourcesJar") {
            archiveBaseName.set("$artifactId")
            archiveClassifier.set("sources")
        }
        """.trimIndent() else ""}
        publishing {
            repositories {
                maven {
                    name = "test"
                    url = uri(localTestRepository)
                }
            }
            publications {
                create<org.gradle.api.publish.maven.MavenPublication>("test") {
                    this.artifactId = "$artifactId"
                    artifact(publishedJar)
                    ${if (withSources && !addSourcesInAfterEvaluate) """artifact(publishedSourcesJar)""" else ""}
                }
            }
        }
        ${if (addSourcesInAfterEvaluate) """
        afterEvaluate {
            extensions.getByType<org.gradle.api.publish.PublishingExtension>()
                .publications
                .withType(org.gradle.api.publish.maven.MavenPublication::class.java)
                .named("test") {
                    artifact(publishedSourcesJar)
                }
        }
        """.trimIndent() else ""}
    """.trimIndent()

    private fun publishedArtifactsRule(artifactId: String, withSources: Boolean = false): String {
        val suffix = if (withSources) ",sources.jar" else ""
        return "org.jetbrains.kotlinx:$artifactId/.jar,.pom$suffix\n"
    }
}
