package kotlinx.validation

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
            outputContains("Artifacts list file does not exist: artifacts.txt")
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
            outputContains("[Artifacts Validation] Error while parsing rules file: Rule should contain exactly one '/', was: \"is it a rules file or what?\"")
            outputContains("Failed to load rules file from artifacts.txt. See log for more details.")
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

        run("validateArtifacts", "--dump") {
            checkTaskStatus(":validateArtifacts", TaskOutcome.SUCCESS)
            outputContains("[Artifacts Validation] Artifact rules were saved to artifacts.txt")

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
}
