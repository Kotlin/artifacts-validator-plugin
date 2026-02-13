package kotlinx.validation.test

import org.gradle.testkit.runner.TaskOutcome
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginTests : PluginTestBase("/test-projects/basic") {

    @Test
    fun checkPluginAppliesWithoutErrors() {
        copySettingsKts()
        copyBuildKtsWithoutConfiguringPlugin()

        run("tasks") {
            outputContains("validateArtifacts")
        }
    }

    @Test
    fun configurePlugin() {
        copySettingsKts()
        copyBuildKts {
            artifactsList = "artifacts.txt"
            artifactsRepository = "build/repo"
            requireSignatures = true
            requireChecksums = setOf("MD5", "SHA1")
        }

        run("build") {
            checkTaskStatus(":build", TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun disablePlugin() {
        copySettingsKts()
        copyBuildKts {
            enabled = false
        }

        run("validateArtifacts") {
            checkTaskStatus(":validateArtifacts", TaskOutcome.SKIPPED)
        }
    }

    @Test
    fun artifactsListNotFound() {
        copySettingsKts()
        copyBuildKts {
            artifactsList = "artifacts.txt"
            artifactsRepository = "build/repo"
        }
        createFile("build/repo/org/jetbrains/kotlinx/basic-test-project/0.0.1/basic-test-project-0.0.1.pom")
        runAndFail("validateArtifacts") {
            checkTaskStatus(":validateArtifacts", TaskOutcome.FAILED)
            outputContains("Artifacts list file does not exist:")
        }
    }

    @Test
    fun repositoryDoesNotExist() {
        copySettingsKts()
        copyBuildKts {
            artifactsList = "artifacts.txt"
            artifactsRepository = "build/repo"
        }
        createFile("artifacts.txt")
        runAndFail("validateArtifacts") {
            checkTaskStatus(":validateArtifacts", TaskOutcome.FAILED)
            outputContains("An input file was expected to be present but it doesn't exist.")
            outputContains("build/repo' which doesn't exist")
        }
    }

    @Test
    fun noArtifactsFoundButTheListWasEmpy() {
        copySettingsKts()
        copyBuildKts {
            artifactsList = "artifacts.txt"
            artifactsRepository = "build/repo"
        }
        createFile("artifacts.txt")
        createDir("build/repo")
        run("validateArtifacts") {
            checkTaskStatus(":validateArtifacts", TaskOutcome.SUCCESS)
            outputContains("[Artifacts Validation] Artifacts fully matched the list of expected artifacts.")
        }
    }

    @Test
    fun missingArtifacts() {
        copySettingsKts()
        copyBuildKts {
            artifactsList = "artifacts.txt"
            artifactsRepository = "build/repo"
        }
        createFile("artifacts.txt", """
        org.jetbrains.kotlinx:basic-test-project/.pom
        """.trimIndent())
        createDir("build/repo")

        runAndFail("validateArtifacts") {
            checkTaskStatus(":validateArtifacts", TaskOutcome.FAILED)
            outputContains("[Artifacts Validation] Following artifacts were expected, but were not found: org.jetbrains.kotlinx:basic-test-project-0.0.1.pom")
            outputContains("List of found artifacts does not match list of expected artifacts. See log for more details.")
        }
    }

    @Test
    fun extraArtifacts() {
        copySettingsKts()
        copyBuildKts {
            artifactsList = "artifacts.txt"
            artifactsRepository = "build/repo"
        }
        createFile("artifacts.txt", """
        org.jetbrains.kotlinx:basic-test-project/.pom
        """.trimIndent())

        createFile("build/repo/org/jetbrains/kotlinx/basic-test-project/0.0.1/basic-test-project-0.0.1.pom")
        createFile("build/repo/org/jetbrains/kotlinx/basic-test-project/0.0.1/basic-test-project-0.0.1.jar")

        runAndFail("validateArtifacts") {
            checkTaskStatus(":validateArtifacts", TaskOutcome.FAILED)
            outputContains("[Artifacts Validation] Following artifacts were not expected, but were found: org.jetbrains.kotlinx:basic-test-project-0.0.1.jar")
            outputContains("List of found artifacts does not match list of expected artifacts. See log for more details.")
        }
    }

    @Test
    fun missingSignatures() {
        copySettingsKts()
        copyBuildKts {
            artifactsList = "artifacts.txt"
            artifactsRepository = "build/repo"
            requireSignatures = true
        }
        createFile("artifacts.txt", """
        org.jetbrains.kotlinx:basic-test-project/.pom
        """.trimIndent())

        createFile("build/repo/org/jetbrains/kotlinx/basic-test-project/0.0.1/basic-test-project-0.0.1.pom")

        runAndFail("validateArtifacts") {
            checkTaskStatus(":validateArtifacts", TaskOutcome.FAILED)
            outputContains("[Artifacts Validation] Artifacts fully matched the list of expected artifacts.")
            outputContains("org/jetbrains/kotlinx/basic-test-project/0.0.1/basic-test-project-0.0.1.pom is not signed.")
            outputContains("Some artifacts were not signed or missing checksum files. See log for more details.")
        }
    }

    @Test
    fun missingChecksums() {
        copySettingsKts()
        copyBuildKts {
            artifactsList = "artifacts.txt"
            artifactsRepository = "build/repo"
            requireChecksums = setOf("MD5", "SHA1")
        }
        createFile("artifacts.txt", """
        org.jetbrains.kotlinx:basic-test-project/.pom
        """.trimIndent())

        createFile("build/repo/org/jetbrains/kotlinx/basic-test-project/0.0.1/basic-test-project-0.0.1.pom")

        runAndFail("validateArtifacts") {
            checkTaskStatus(":validateArtifacts", TaskOutcome.FAILED)
            outputContains("[Artifacts Validation] Artifacts fully matched the list of expected artifacts.")
            outputContains("org/jetbrains/kotlinx/basic-test-project/0.0.1/basic-test-project-0.0.1.pom is missing following checksums: [MD5, SHA1]")
            outputContains("Some artifacts were not signed or missing checksum files. See log for more details.")
        }
    }

    @Test
    fun missingSomeChecksums() {
        copySettingsKts()
        copyBuildKts {
            artifactsList = "artifacts.txt"
            artifactsRepository = "build/repo"
            requireChecksums = setOf("MD5", "SHA1")
        }
        createFile("artifacts.txt", """
        org.jetbrains.kotlinx:basic-test-project/.pom
        """.trimIndent())

        createFile("build/repo/org/jetbrains/kotlinx/basic-test-project/0.0.1/basic-test-project-0.0.1.pom")
        createFile("build/repo/org/jetbrains/kotlinx/basic-test-project/0.0.1/basic-test-project-0.0.1.pom.md5")

        runAndFail("validateArtifacts") {
            checkTaskStatus(":validateArtifacts", TaskOutcome.FAILED)
            outputContains("[Artifacts Validation] Artifacts fully matched the list of expected artifacts.")
            outputContains("org/jetbrains/kotlinx/basic-test-project/0.0.1/basic-test-project-0.0.1.pom is missing following checksums: [SHA1]")
            outputContains("Some artifacts were not signed or missing checksum files. See log for more details.")
        }
    }

    @Test
    fun invalidChecksumsParameterValues() {
        copySettingsKts()
        copyBuildKts {
            artifactsList = "artifacts.txt"
            artifactsRepository = "build/repo"
            requireChecksums = setOf("CRC32")
        }
        createFile("artifacts.txt")
        createDir("build/repo")

        runAndFail("validateArtifacts") {
            checkTaskStatus(":validateArtifacts", TaskOutcome.FAILED)
            outputContains("Invalid checksum type: CRC32. Use one of MD5, SHA1, SHA256, SHA512")
        }
    }

    @Test
    fun invalidRulesFiles() {
        copySettingsKts()
        copyBuildKts {
            artifactsList = "artifacts.txt"
            artifactsRepository = "build/repo"
        }
        createFile("artifacts.txt", """
        is it a rules file or what?
        """.trimIndent())

        createDir("build/repo/")

        runAndFail("validateArtifacts") {
            checkTaskStatus(":validateArtifacts", TaskOutcome.FAILED)
            outputContains("[Artifacts Validation] Error while parsing rules file ")
            outputContains("artifacts.txt: Rule should contain exactly one '/', was: \"is it a rules file or what?\"")
            outputContains("Failed to load rules file from files. See log for more details.")
        }
    }

    @Test
    fun generateArtifactsList() {
        copySettingsKts()
        copyBuildKts {
            artifactsList = "artifacts.txt"
            artifactsRepository = "build/repo"
        }

        createFile("build/repo/org/jetbrains/kotlinx/basic-test-project/0.0.1/basic-test-project-0.0.1.pom")
        createFile("build/repo/org/jetbrains/kotlinx/basic-test-project/0.0.1/basic-test-project-0.0.1.jar")
        createFile("build/repo/org/jetbrains/kotlinx/basic-test-project/0.0.1/basic-test-project-0.0.1-sources.jar")

        // @Option does not work well with DirectoryProperty for older Gradle versions:
        // https://github.com/gradle/gradle/issues/12009
        useGradleVersion("8.5")

        run("validateArtifacts", "--dump") {
            checkTaskStatus(":validateArtifacts", TaskOutcome.SUCCESS)
            outputContains("[Artifacts Validation] Artifact rules were saved to ")

            val generatedArtifactsFile = projectRoot.resolve("artifacts.txt").readText()
            assertEquals("""
            org.jetbrains.kotlinx:basic-test-project/.jar,.pom,sources.jar
            
            """.trimIndent(), generatedArtifactsFile)
        }
    }

    @Test
    fun allValid() {
        copySettingsKts()
        copyBuildKts {
            artifactsList = "artifacts.txt"
            artifactsRepository = "build/repo"
            requireChecksums = setOf("MD5")
            requireSignatures = true
        }
        createFile("artifacts.txt", """
        org.jetbrains.kotlinx:basic-test-project/.pom,sources.jar
        """.trimIndent())

        createFile("build/repo/org/jetbrains/kotlinx/basic-test-project/0.0.1/basic-test-project-0.0.1.pom")
        createFile("build/repo/org/jetbrains/kotlinx/basic-test-project/0.0.1/basic-test-project-0.0.1.pom.asc")
        createFile("build/repo/org/jetbrains/kotlinx/basic-test-project/0.0.1/basic-test-project-0.0.1.pom.md5")
        createFile("build/repo/org/jetbrains/kotlinx/basic-test-project/0.0.1/basic-test-project-0.0.1-sources.jar")
        createFile("build/repo/org/jetbrains/kotlinx/basic-test-project/0.0.1/basic-test-project-0.0.1-sources.jar.asc")
        createFile("build/repo/org/jetbrains/kotlinx/basic-test-project/0.0.1/basic-test-project-0.0.1-sources.jar.md5")

        run("validateArtifacts") {
            checkTaskStatus(":validateArtifacts", TaskOutcome.SUCCESS)
            outputContains("[Artifacts Validation] Artifacts fully matched the list of expected artifacts.")
            outputContains("[Artifacts Validation] All artifacts are signed.")
            outputContains("[Artifacts Validation] All artifacts have required checksums.")

        }
    }

    @Test
    fun brokenFilesInRepository() {
        copySettingsKts()
        copyBuildKts {
            artifactsList = "artifacts.txt"
            artifactsRepository = "build/repo"
        }
        createFile("artifacts.txt", """
        org.jetbrains.kotlinx:basic-test-project/.pom,sources.jar
        """.trimIndent())

        createFile("build/repo/org/jetbrains/kotlinx/basic-test-project/0.0.1/basic-test-project-0.0.1.pom.asc")
        createFile("build/repo/org/jetbrains/kotlinx/basic-test-project/0.0.1/basic-test-project-0.0.1-sources")

        runAndFail("validateArtifacts") {
            checkTaskStatus(":validateArtifacts", TaskOutcome.FAILED)
            outputContains("[Artifacts Validation] Error detecting while reading file ")
            outputContains(
                "build/repo/org/jetbrains/kotlinx/basic-test-project/0.0.1/basic-test-project-0.0.1.pom: " +
                    "There are checksum and/or signature files corresponding to an artifact, " +
                    "but the main artifact file does not exist:"
            )
            outputContains("/build/repo/org/jetbrains/kotlinx/basic-test-project/0.0.1/basic-test-project-0.0.1-sources: Artifact file has no extension:")
            outputContains("Errors were detected while loading artifacts info. See log for details")
        }
    }

    @Test
    fun overrideVersion() {
        copySettingsKts()
        copyBuildKts {
            artifactsLists = mapOf("artifacts.txt" to "1.0-custom")
            artifactsRepository = "build/repo"
        }

        createFile("artifacts.txt", """
        org.example:artifact/.pom
        """.trimIndent())

        createFile("build/repo/org/example/artifact/1.0-custom/artifact-1.0-custom.pom")

        run("validateArtifacts") {
            checkTaskStatus(":validateArtifacts", TaskOutcome.SUCCESS)
            outputContains("[Artifacts Validation] Artifacts fully matched the list of expected artifacts.")
        }
    }

    @Test
    fun unexpectedVersionInRepository() {
        copySettingsKts()
        copyBuildKts {
            artifactsList = "artifacts.txt"
            artifactsRepository = "build/repo"
        }

        createFile("artifacts.txt", """
        org.example:artifact/.pom
        """.trimIndent())

        createFile("build/repo/org/example/artifact/0.0.1/artifact-0.0.1.pom")
        createFile("build/repo/org/example/artifact/1.0-custom/artifact-1.0-custom.pom")

        runAndFail("validateArtifacts") {
            checkTaskStatus(":validateArtifacts", TaskOutcome.FAILED)
            outputContains("[Artifacts Validation] Following artifacts were not expected, but were found: org.example:artifact-1.0-custom.pom")
            outputContains("List of found artifacts does not match list of expected artifacts. See log for more details.")
        }
    }

    @Test
    fun artifactsWithMultipleVersions() {
        copySettingsKts()
        copyBuildKts {
            artifactsRepository = "build/repo"
            artifactsLists = mapOf(
                "artifacts.core.txt" to "0.0.1",
                "artifacts.ext.txt" to "2025a-0.0.1",
            )
        }

        createFile("artifacts.core.txt", """
        org.example:artifact-core/.pom
        """.trimIndent())

        createFile("artifacts.ext.txt", """
        org.example:artifact-ext/.pom
        """.trimIndent())

        createFile("build/repo/org/example/artifact-core/0.0.1/artifact-core-0.0.1.pom")
        createFile("build/repo/org/example/artifact-ext/2025a-0.0.1/artifact-ext-2025a-0.0.1.pom")

        run("validateArtifacts") {
            checkTaskStatus(":validateArtifacts", TaskOutcome.SUCCESS)
            outputContains("[Artifacts Validation] Artifacts fully matched the list of expected artifacts.")
        }
    }

    @Test
    fun dumpRulesIntoMultipleFiles() {
        copySettingsKts()
        copyBuildKts {
            artifactsRepository = "build/repo"
            artifactsLists = mapOf(
                "artifacts.core.txt" to "0.0.1",
                "artifacts.ext.txt" to "2025a-0.0.1",
            )
        }

        createFile("build/repo/org/example/artifact-core/0.0.1/artifact-core-0.0.1.pom")
        createFile("build/repo/org/example/artifact-ext/2025a-0.0.1/artifact-ext-2025a-0.0.1.pom")

        // @Option does not work well with DirectoryProperty for older Gradle versions:
        // https://github.com/gradle/gradle/issues/12009
        useGradleVersion("8.5")

        run("validateArtifacts", "--dump") {
            checkTaskStatus(":validateArtifacts", TaskOutcome.SUCCESS)
            outputContains("[Artifacts Validation] Artifact rules were saved to ")

            assertEquals("""
            org.example:artifact-core/.pom
            
            """.trimIndent(), projectRoot.resolve("artifacts.core.txt").readText())

            assertEquals("""
            org.example:artifact-ext/.pom
            
            """.trimIndent(), projectRoot.resolve("artifacts.ext.txt").readText())
        }
    }

    @Test
    fun defaultListLocation() {
        copySettingsKts()
        copyBuildKts {
            artifactsRepository = "build/repo"
        }
        createFile("gradle/artifacts.txt", """
        org.jetbrains.kotlinx:basic-test-project/.pom
        """.trimIndent())

        createFile("build/repo/org/jetbrains/kotlinx/basic-test-project/0.0.1/basic-test-project-0.0.1.pom")

        run("validateArtifacts") {
            checkTaskStatus(":validateArtifacts", TaskOutcome.SUCCESS)
            outputContains("[Artifacts Validation] Artifacts fully matched the list of expected artifacts.")
        }
    }

    @Test
    fun artifactsListsCommandLineOptionParsing() {
        copySettingsKts()
        copyBuildKts {
            artifactsRepository = "build/repo"
        }

        createFile("artifacts.core.txt", """
        org.example:artifact-core/.pom
        """.trimIndent())

        createFile("artifacts.ext.txt", """
        org.example:artifact-ext/.pom
        """.trimIndent())

        createFile("build/repo/org/example/artifact-core/0.0.1/artifact-core-0.0.1.pom")
        createFile("build/repo/org/example/artifact-ext/2025a-0.0.1/artifact-ext-2025a-0.0.1.pom")

        // @Option does not work well with DirectoryProperty for older Gradle versions:
        // https://github.com/gradle/gradle/issues/12009
        useGradleVersion("8.5")

        run(
            "validateArtifacts",
            "--artifacts-list=${projectRoot.resolve("artifacts.core.txt")}",
            "--artifacts-list=${projectRoot.resolve("artifacts.ext.txt")}:2025a-0.0.1",
            "--stacktrace"
        ) {
            checkTaskStatus(":validateArtifacts", TaskOutcome.SUCCESS)
            outputContains("[Artifacts Validation] Artifacts fully matched the list of expected artifacts.")
        }
    }

    @Test
    fun artifactsListsCommandLineOptionInvalidValue() {
        copySettingsKts()
        copyBuildKts {
            artifactsRepository = "build/repo"
        }

        // @Option does not work well with DirectoryProperty for older Gradle versions:
        // https://github.com/gradle/gradle/issues/12009
        useGradleVersion("8.5")

        runAndFail("validateArtifacts", "--artifacts-list=a:b:c") {
            outputContains("artifacts-list value must contain at most one ':' delimiting file and a version, was: \"a:b:c\".")
        }
    }
}
