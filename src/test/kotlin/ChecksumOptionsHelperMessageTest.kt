package kotlinx.validation

import kotlin.test.Test
import kotlin.test.assertEquals

class ChecksumOptionsHelperMessageTest {
    @Test
    fun helpMessageUpdateReminder() {
        assertEquals(
            setOf(DigestType.MD5, DigestType.SHA1, DigestType.SHA256, DigestType.SHA512),
            DigestType.values().toSet(),
            "Available digest types changed, don't forget to update a help message for ArtifactsValidationTask.validateChecksums"
        )
    }
}
