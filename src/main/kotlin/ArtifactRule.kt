package kotlinx.validation.artifacts

/**
 * A rule describing an artifact that should be present in a repository.
 */
internal data class ArtifactRule(
    val groupId: String,
    val artifactId: String,
    val classifier: String,
    val extension: String
) {
    companion object {
        /**
         * Parses a rule from a string in the following format `<groupId>:<artifactId>/<extensions>`
         * into one or multiple [ArtifactRule]s.
         *
         * `groupId` is a non-empty artifacts group ID consisting of one or more strings delimited by '.',
         * for example `org.example` or `package`.
         *
         * `artifactId` is an artifact ID.
         *
         * `extensions` is a comma separated list of one or multiple `classifier` and `extension` pairs, where
         * `classifier` could be empty, but the `extension` is always required.
         * For example `.pom`, `.pom,sources.jar,javadoc.jar`.
         *
         * @throws IllegalArgumentException when the [line] does not conform the format.
         */
        fun parseRule(line: String): List<ArtifactRule> {
            require(line.isNotBlank()) { "Rule could not be empty" }

            val gaAndClassifiers = line.split('/')
            require(gaAndClassifiers.size == 2) { "Rule should contain exactly one '/', was: \"$line\"" }
            require(gaAndClassifiers[0].isNotBlank()) { "Group and artifact IDs could not be empty, was: \"$line\"" }
            require(gaAndClassifiers[1].isNotBlank()) { "List of classifiers/extensions could not be empty, was: \"$line\"" }

            val ga = gaAndClassifiers[0].split(':')
            require(ga.size == 2) { "Rule should contain groupId:artifactId pair, was: \"$line\"" }
            val (groupId, artifactId) = ga
            require(groupId.isNotBlank()) { "Group ID could not be empty, was: \"$line\"" }
            require(artifactId.isNotBlank()) { "Artifact ID could not be empty, was: \"$line\"" }

            val classifierAndExts = gaAndClassifiers[1].split(",")

            val rules = mutableListOf<ArtifactRule>()
            classifierAndExts.map { it.trim() }.sorted().forEach { ce ->
                val classifierAndExtension = ce.split('.')
                require(classifierAndExtension.size == 2 && classifierAndExtension[1].isNotEmpty()) {
                    "Classifier/extension pair should have following format: classifier.extension or .extensions. " +
                            "Was \"$ce\" in \"$line\""
                }
                rules.add(
                    ArtifactRule(
                        groupId, artifactId,
                        classifierAndExtension[0],
                        classifierAndExtension[1]
                    )
                )
            }
            return rules
        }
    }

    fun toArtifactIdentifier(withVersion: String?): String {
        val classifierStr = if (classifier.isEmpty()) "" else "-${classifier}"
        val versionStr = if (withVersion == null) "" else "-$withVersion"
        return "$groupId:$artifactId$versionStr$classifierStr.$extension"
    }
}

/**
 * Generates a list of artifact rules matching given artifacts.
 * Generated strings are conformant with the format supported by [ArtifactRule.Companion.parseRule].
 */
internal fun Iterable<AggregatedArtifactInfo>.toRules(): List<String> = buildList {
    this@toRules.forEach { info ->
        val ga = "${info.gav.groupId}:${info.gav.artifactId}"
        val classifierAndExtension = info.artifacts
            .map { it.artifact.let { a -> "${a.classifier}.${a.extension}" } }
            .sorted()
            .joinToString(",")
        add("$ga/$classifierAndExtension")
    }
}
