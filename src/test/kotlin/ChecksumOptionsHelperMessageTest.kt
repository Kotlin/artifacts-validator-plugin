package kotlinx.validation

import kotlin.test.Test
import kotlin.test.assertEquals

class ChecksumOptionsHelperMessageTest {
    @Test
    fun helpMessageUpdateReminder() {
        assertEquals(
            setOf(ChecksumType.MD5, ChecksumType.SHA1, ChecksumType.SHA256, ChecksumType.SHA512),
            ChecksumType.values().toSet(),
            "Available digest types changed, don't forget to update a help message for ArtifactsValidationTask.validateChecksums"
        )
    }
}
