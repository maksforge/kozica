package app.kozica.data.backup

import app.kozica.media.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupManifestTest {
    @Test
    fun `a manifest survives a round trip`() {
        val manifest =
            BackupManifest(
                scope = BackupScope.Everything,
                schemaVersion = 3,
                createdAtEpochMilli = 1_800_000_000_000,
                mediaCounts = mapOf(MediaKind.Photo.name to 2),
            )

        assertEquals(manifest, decodeManifest(encodeManifest(manifest)))
    }

    @Test
    fun `the format is written even at its default`() {
        val text =
            encodeManifest(BackupManifest(scope = BackupScope.Records, schemaVersion = 3, createdAtEpochMilli = 1))

        assertEquals(BACKUP_FORMAT_VERSION, Regex("\"format\": (\\d+)").find(text)!!.groupValues[1].toInt())
    }

    @Test
    fun `a newer archive with unknown fields still parses, so it can be refused by its numbers`() {
        val decoded =
            decodeManifest(
                """{"format":1,"scope":"Records","schemaVersion":99,"createdAtEpochMilli":1,"somethingNew":{"a":1}}""",
            )

        assertEquals(99, decoded!!.schemaVersion)
    }

    @Test
    fun `an unknown scope, a missing field or not JSON at all is no manifest`() {
        assertNull(decodeManifest("""{"scope":"Avatars","schemaVersion":3,"createdAtEpochMilli":1}"""))
        assertNull(decodeManifest("""{"scope":"Records","createdAtEpochMilli":1}"""))
        assertNull(decodeManifest("not json"))
    }

    @Test
    fun `a kind the manifest does not count reads as zero`() {
        val manifest = BackupManifest(scope = BackupScope.Records, schemaVersion = 3, createdAtEpochMilli = 1)

        assertEquals(0, manifest.countFor(MediaKind.Photo))
    }
}
