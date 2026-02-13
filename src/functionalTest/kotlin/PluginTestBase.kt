package kotlinx.validation.test

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals

abstract class PluginTestBase(val resourcesPath: String) {
    @field:TempDir
    lateinit var projectRoot: File

    private var gradleVersion: String? = null

    class DslBuilder {
        var enabled: Boolean? = null
        var requireSignatures: Boolean? = null
        var requireChecksums: Set<String>? = null
        var artifactsList: String? = null
        var artifactsLists: Map<String, String>? = null
        var artifactsRepository: String? = null

        fun toConfig(): String = buildString {
            appendLine("artifactsValidation {")
            enabled?.let { appendLine("enabled = $it") }
            requireSignatures?.let { appendLine("requireSignatures = $it") }
            requireChecksums?.let {
                val values = it.joinToString(",") { "\"$it\"" }
                appendLine("requireChecksums.set(setOf($values))")
            }
            artifactsList?.let { appendLine("artifactsList(project.rootDir.resolve(\"$it\"))") }
            artifactsLists?.let {
                it.forEach { (file, version) ->
                    appendLine("artifactsList(project.rootDir.resolve(\"$file\"), \"$version\")")
                }
            }
            artifactsRepository?.let { appendLine("artifactsRepository = project.rootDir.resolve(\"$it\")") }
            appendLine("}")
        }
    }

    fun copyFile(from: String, to: String, appendText: String? = null) {
        val dstFile = projectRoot.resolve(to)
        val srcStream = PluginTestBase::class.java.getResourceAsStream(from)
        requireNotNull(srcStream) {
            "$from was not found among project resources"
        }
        srcStream.use {
            dstFile.outputStream().use { outStream ->
                srcStream.copyTo(outStream)
            }
        }
        if (!appendText.isNullOrEmpty()) {
            dstFile.appendText(appendText)
        }
    }

    fun copySettingsKts() {
        copyFile("$resourcesPath//settings.gradle.kts", "settings.gradle.kts")
    }

    fun copyBuildKtsWithoutConfiguringPlugin() {
        copyFile("$resourcesPath/build.gradle.kts", "build.gradle.kts")
    }

    fun copyBuildKts(configureDsl: DslBuilder.() -> Unit) {
        val dslBuilder = DslBuilder()
        configureDsl(dslBuilder)
        copyFile("$resourcesPath/build.gradle.kts", "build.gradle.kts", dslBuilder.toConfig())
    }

    fun createFile(path: String, contents: String? = null) {
        val file = projectRoot.resolve(path)
        file.parentFile.mkdirs()
        file.createNewFile()
        if (contents != null) {
            file.writeText(contents)
        }
    }

    fun createDir(path: String) {
        projectRoot.resolve(path).mkdirs()
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

    fun useGradleVersion(gradleVersion: String) {
        this.gradleVersion = gradleVersion
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
