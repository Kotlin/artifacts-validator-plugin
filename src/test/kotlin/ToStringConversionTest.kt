package kotlinx.validation

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

class ToStringConversionTest {
    @Test
    fun artifactRules() {
        assertEquals(
            "org.example:artifact-0.0.0.pom",
            ArtifactRule("org.example", "artifact", "", "pom").toArtifactIdentifier("0.0.0")
        )

        assertEquals(
            "org.example:artifact-0.0.0-sources.jar",
            ArtifactRule("org.example", "artifact", "sources", "jar").toArtifactIdentifier("0.0.0")
        )
    }

    @Test
    fun artifactInfo() {
        assertEquals(
            "org.example:artifact-0.0.0.pom",
            ArtifactInfo(
                ArtifactInfo.Gav("org.example", "artifact", "0.0.0"),
                Paths.get("whatever"), "pom", "", null, null, false
            ).toArtifactIdentifier()
        )

        assertEquals(
            "org.example:artifact-0.0.0-sources.jar",
            ArtifactInfo(
                ArtifactInfo.Gav("org.example", "artifact", "0.0.0"),
                Paths.get("whatever"), "jar", "sources", null, null, false
            ).toArtifactIdentifier()
        )
    }
}
