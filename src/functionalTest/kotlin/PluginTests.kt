package kotlinx.validation.test

import org.gradle.testkit.runner.TaskOutcome
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginTests : PluginTestBase() {
    @Test
    fun listTasks() {
        copyProject("/test-projects/basic")

        run("tasks", "--all") {
            outputContains("checkArtifacts")
            outputContains("dumpArtifacts")
            outputContains("validateLocalMavenRepo")
        }
    }

    @Test
    fun checkArtifacts() {
        copyProjects(
            "/test-projects/publication",
            "/test-projects/artifacts/publication/default",
        )

        run("check") {
            checkTaskStatus(":checkArtifacts", TaskOutcome.SUCCESS)
            checkTaskStatus(":check", TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun checkArtifactsWithoutRulesFile() {
        copyProject("/test-projects/publication")

        runAndFail("checkArtifacts") {
            checkTaskStatus(":checkArtifacts", TaskOutcome.FAILED)
            outputContains(
                "[Artifacts Validation] Following artifacts were not expected, but were found: " +
                    "org.jetbrains.kotlinx:basic-test-project.jar, org.jetbrains.kotlinx:basic-test-project.pom"
            )
        }
    }

    @Test
    fun dumpArtifacts() {
        copyProject("/test-projects/publication-with-sources")

        run("dumpArtifacts") {
            checkTaskStatus(":dumpArtifacts", TaskOutcome.SUCCESS)
        }

        assertEquals(
            "org.jetbrains.kotlinx:basic-test-project/.jar,.pom,sources.jar\n",
            projectRoot.resolve("gradle/artifacts.txt").readText()
        )
    }

    @Test
    fun dumpArtifactsToProjectSpecificRuleFiles() {
        copyProject("/test-projects/per-project-dumps")

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
        copyProjects(
            "/test-projects/per-project-dumps",
            "/test-projects/artifacts/per-project",
        )

        run("checkArtifacts") {
            checkTaskStatus(":checkArtifacts", TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun checkArtifactsUsingCustomDumpLocation() {
        copyProjects("/test-projects/custom-dump-location")

        run("checkArtifacts") {
            checkTaskStatus(":checkArtifacts", TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun checkArtifactsSeePublicationArtifactsAddedInAfterEvaluate() {
        copyProjects(
            "/test-projects/publication-with-sources-after-evaluate",
            "/test-projects/artifacts/publication/with-sources",
        )

        run("checkArtifacts") {
            checkTaskStatus(":checkArtifacts", TaskOutcome.SUCCESS)
        }
    }
}
