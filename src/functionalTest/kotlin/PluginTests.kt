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
    fun checkArtifactsFailsWhenRulesFileIsMissing() {
        copySettingsKts()
        copyBuildKts(publicationBlock(artifactId = "basic-test-project"))

        runAndFail("checkArtifacts") {
            checkTaskStatus(":checkArtifacts", TaskOutcome.FAILED)
            outputContains("Files describing expected artifacts does not exist:")
            outputContains("To generate the file, run the 'dumpArtifacts' task.")
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
            "--artifacts-list=${projectRoot.resolve("gradle/artifacts.txt")}",
            "--artifacts-version=0.0.1"
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
                defaultArtifactsDumpFile.set(rootDir.resolve("expected/custom-artifacts.txt"))
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
                dumpFileForProjects(rootDir.resolve("gradle/lib-artifacts.txt"), project(":lib"))
                dumpFileForProjects(rootDir.resolve("gradle/ext-artifacts.txt"), project(":ext"))
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
            projectRoot.resolve("gradle/lib-artifacts.txt").readText()
        )
        assertEquals(
            "org.jetbrains.kotlinx:ext/.jar,.pom\n",
            projectRoot.resolve("gradle/ext-artifacts.txt").readText()
        )
    }

    @Test
    fun checkArtifactsUsingProjectSpecificRuleFiles() {
        copySettingsKts(
            """

            include(":lib")
            include(":ext")

            extensions.configure<kotlinx.validation.ArtifactsValidatorPluginSettingsExtension>("artifactsValidation") {
                dumpFileForProjects(rootDir.resolve("gradle/lib-artifacts.txt"), project(":lib"))
                dumpFileForProjects(rootDir.resolve("gradle/ext-artifacts.txt"), project(":ext"))
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
        createFile("gradle/artifacts.txt")
        createFile("gradle/lib-artifacts.txt", "org.jetbrains.kotlinx:lib/.jar,.pom\n")
        createFile("gradle/ext-artifacts.txt", "org.jetbrains.kotlinx:ext/.jar,.pom\n")

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
            "--artifacts-list=${projectRoot.resolve("gradle/artifacts.txt")}",
            "--artifacts-version=0.0.1"
        ) {
            checkTaskStatus(":publishTestPublicationToTestRepository", TaskOutcome.SUCCESS)
            checkTaskStatus(":validateLocalMavenRepo", TaskOutcome.SUCCESS)
            outputContains("[Artifacts Validation] Artifacts fully matched the list of expected artifacts.")
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
