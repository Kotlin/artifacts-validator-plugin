package kotlinx.validation.test

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.copyToRecursively
import kotlin.io.path.toPath
import kotlin.test.assertContains
import kotlin.test.assertEquals

abstract class PluginTestBase {
    @field:TempDir
    lateinit var projectRoot: File

    private var gradleVersion: String? = null

    fun copyProjects(vararg projectPaths: String) {
        projectPaths.forEach(::copyProject)
    }

    @OptIn(ExperimentalPathApi::class)
    fun copyProject(projectPath: String) {
        val rootResource = requireNotNull(PluginTestBase::class.java.getResource(projectPath)) {
            "$projectPath was not found among project resources"
        }
        val rootPath = rootResource.toURI().toPath()
        rootPath.copyToRecursively(projectRoot.toPath(), followLinks = false, overwrite = true)
    }

    fun createDir(path: String) {
        projectRoot.resolve(path).mkdirs()
    }

    fun createZipFromDirectory(sourceDirPath: String, zipPath: String) {
        val sourceDir = projectRoot.resolve(sourceDirPath).toPath()
        require(Files.isDirectory(sourceDir)) {
            "$sourceDirPath should point to an existing directory"
        }

        val zipFile = projectRoot.resolve(zipPath)
        zipFile.parentFile.mkdirs()
        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            Files.walk(sourceDir).use { paths ->
                paths
                    .filter(Files::isRegularFile)
                    .filter { it.fileName.toString() != ".gitkeep" }
                    .forEach { source ->
                        val relativePath = sourceDir.relativize(source).toString().replace(File.separatorChar, '/')
                        zip.putNextEntry(ZipEntry(relativePath))
                        Files.copy(source, zip)
                        zip.closeEntry()
                    }
            }
        }
    }

    private fun prepareRunner(vararg commands: String): GradleRunner =
        GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(projectRoot)
            .withArguments(*commands)
            .forwardOutput()
            .withDebug(true) // we need it to collect code coverage
            .let {
                if (gradleVersion != null) {
                    it.withGradleVersion(gradleVersion)
                } else {
                    it
                }
            }

    fun run(vararg commands: String, block: BuildResult.() -> Unit) {
        block(prepareRunner(*commands).build())
    }

    fun runAndFail(vararg commands: String, block: BuildResult.() -> Unit) {
        block(prepareRunner(*commands).buildAndFail())
    }

    fun BuildResult.checkTaskStatus(task: String, status: TaskOutcome) {
        assertEquals(status, this.task(task)?.outcome)
    }

    fun BuildResult.outputContains(text: String) {
        assertContains(output, text)
    }
}
