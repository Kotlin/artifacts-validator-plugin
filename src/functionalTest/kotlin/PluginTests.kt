package kotlinx.validation.test

import org.gradle.testkit.runner.TaskOutcome
import kotlin.test.Test
import kotlin.test.assertContentEquals
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
            projectRoot.resolve("artifacts/basic-test-project.txt").readText()
        )
    }

    @Test
    fun dumpArtifactsForMultiModuleProject() {
        copyProject("/test-projects/per-project-dumps")

        run("dumpArtifacts") {
            checkTaskStatus(":dumpArtifacts", TaskOutcome.SUCCESS)
        }

        assertEquals(
            "org.jetbrains.kotlinx:lib/.jar,.pom\n",
            projectRoot.resolve("artifacts/lib.txt").readText()
        )
        assertEquals(
            "org.jetbrains.kotlinx:ext/.jar,.pom\n",
            projectRoot.resolve("artifacts/ext.txt").readText()
        )
    }

    @Test
    fun checkArtifactsForMultiModuleProject() {
        copyProject("/test-projects/multiplatform-publication")

        run("dumpArtifacts") {
            checkTaskStatus(":dumpArtifacts", TaskOutcome.SUCCESS)
        }

        val expectedArtifacts = requireNotNull(PluginTests::class.java.getResourceAsStream(
            "/test-projects/artifacts/multiplatform/default/artifacts/multiplatform-test-project.txt"
        )).bufferedReader(Charsets.UTF_8)
            .readLines()
            .filter { it.isNotBlank() }

        val actualArtifacts = projectRoot.resolve("artifacts/multiplatform-test-project.txt")
            .readLines()
            .filter { it.isNotBlank() }

        assertContentEquals(expectedArtifacts, actualArtifacts)
    }

    @Test
    fun checkArtifactsForMultiplatformPublication() {
        copyProjects(
            "/test-projects/multiplatform-publication",
            "/test-projects/artifacts/multiplatform/default",
        )

        run("checkArtifacts") {
            checkTaskStatus(":checkArtifacts", TaskOutcome.SUCCESS)
        }
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
    fun dumpAndCheckArtifactsWithConfigurationCacheAndProjectIsolation() {
        copyProject("/test-projects/isolated-multi-module")

        runWithConfigurationCacheAndProjectIsolation("dumpArtifacts") {
            checkTaskStatus(":dumpArtifacts", TaskOutcome.SUCCESS)
            checkTaskStatus(":lib:dumpArtifacts", TaskOutcome.SUCCESS)
            checkTaskStatus(":ext:dumpArtifacts", TaskOutcome.SUCCESS)
            outputContains("Configuration cache entry stored.")
        }
        assertEquals("", projectRoot.resolve("artifacts/isolated-multi-module.txt").readText())
        assertEquals("org.jetbrains.kotlinx:lib/.jar,.pom\n", projectRoot.resolve("artifacts/lib.txt").readText())
        assertEquals("org.jetbrains.kotlinx:ext/.jar,.pom\n", projectRoot.resolve("artifacts/ext.txt").readText())

        runWithConfigurationCacheAndProjectIsolation("checkArtifacts") {
            checkTaskStatus(":checkArtifacts", TaskOutcome.SUCCESS)
            checkTaskStatus(":lib:checkArtifacts", TaskOutcome.SUCCESS)
            checkTaskStatus(":ext:checkArtifacts", TaskOutcome.SUCCESS)
            outputContains("Configuration cache entry stored.")
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
