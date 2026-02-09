package kotlinx.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArtifactRulesTest {
    @Test
    fun parseCorrectRules() {
        val string2rules = mapOf(
            "org.example:artifact/.pom" to setOf(
                ArtifactRule("org.example", "artifact", "", "pom")
            ),
            "org.example:artifact/.pom,.jar" to setOf(
                ArtifactRule("org.example", "artifact", "", "pom"),
                ArtifactRule("org.example", "artifact", "", "jar"),
            ),
            "org.example:artifact/.pom,.jar,javadoc.jar" to setOf(
                ArtifactRule("org.example", "artifact", "", "pom"),
                ArtifactRule("org.example", "artifact", "", "jar"),
                ArtifactRule("org.example", "artifact", "javadoc", "jar"),
            ),
            "org.example:artifact/.pom,sources.jar,javadoc.jar,.klib" to setOf(
                ArtifactRule("org.example", "artifact", "", "pom"),
                ArtifactRule("org.example", "artifact", "", "klib"),
                ArtifactRule("org.example", "artifact", "sources", "jar"),
                ArtifactRule("org.example", "artifact", "javadoc", "jar"),
            ),
        )

        string2rules.forEach { (line, parsedRules) ->
            assertEquals(
                parsedRules, ArtifactRule.parseRule(line).toSet(),
                "Unexpected set of rules was parsed from $line"
            )
        }
    }

    @Test
    fun parseInvalidStrings() {
        val line2error = mapOf(
            "" to "Rule could not be empty",
            "org/example/artifact:.pom" to "Rule should contain exactly one '/', was: \"org/example/artifact:.pom\"",
            "org.example/artifact/.pom" to "Rule should contain exactly one '/', was: \"org.example/artifact/.pom\"",
            "org.example:artifact/.pom/" to "Rule should contain exactly one '/', was: \"org.example:artifact/.pom/\"",
            "org.example:artifact:.pom" to "Rule should contain exactly one '/', was: \"org.example:artifact:.pom\"",
            "org.example.artifact/.pom" to "Rule should contain groupId:artifactId pair, was: \"org.example.artifact/.pom\"",
            "/.pom" to "Group and artifact IDs could not be empty, was: \"/.pom\"",
            ":artifact/.pom" to "Group ID could not be empty, was: \":artifact/.pom\"",
            "org.example:/.pom" to "Artifact ID could not be empty, was: \"org.example:/.pom\"",
            "org.example:artifact/" to "List of classifiers/extensions could not be empty, was: \"org.example:artifact/\"",
            "org.example:artifact/pom" to "Classifier/extension pair should have following format: classifier.extension or .extensions. Was \"pom\" in \"org.example:artifact/pom\"",
            "org.example:artifact/pom.pom.pom" to "Classifier/extension pair should have following format: classifier.extension or .extensions. Was \"pom.pom.pom\" in \"org.example:artifact/pom.pom.pom\"",
            "org.example:artifact/." to "Classifier/extension pair should have following format: classifier.extension or .extensions. Was \".\" in \"org.example:artifact/.\"",
        )

        line2error.forEach { (line, error) ->
            val t = assertFailsWith<IllegalArgumentException>("Parsed line: $line") {
                ArtifactRule.parseRule(line)
            }
            assertEquals(error, t.message, "Parsed line: $line")
        }
    }

    @Test
    fun dumpRules() {
        fun AugmentedArtifactInfo(
            group: String,
            artifact: String,
            classifier: String,
            extension: String
        ): AugmentedArtifactInfo {
            return AugmentedArtifactInfo(
                ArtifactInfo(
                    ArtifactInfo.Gav(group, artifact, "does.not.matter"),
                    fileName = "---",
                    extension = extension,
                    classifier = classifier,
                    signatureType = null,
                    digestType = null,
                    isSnapshot = false
                ), emptySet(), emptySet()
            )
        }

        val artifacts = listOf(
            AggregatedArtifactInfo(
                ArtifactInfo.Gav("org.example", "artifact", "0.0.0"),
                listOf(
                    AugmentedArtifactInfo("org.example", "artifact", "", "pom"),
                    AugmentedArtifactInfo("org.example", "artifact", "", "jar"),
                    AugmentedArtifactInfo("org.example", "artifact", "sources", "jar")
                )
            ),
            AggregatedArtifactInfo(
                ArtifactInfo.Gav("org.example", "redirect", "0.0.0"),
                listOf(
                    AugmentedArtifactInfo("org.example", "redirect", "", "pom"),
                )
            ),
        )

        assertEquals(
            listOf("org.example:artifact/.jar,.pom,sources.jar", "org.example:redirect/.pom").sorted(),
            artifacts.toRules().sorted()
        )

        // should not throw an exception:
        assertEquals(4, artifacts.toRules().flatMap { ArtifactRule.parseRule(it) }.size)
    }
}
