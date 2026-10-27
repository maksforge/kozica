package app.kozica.data.backup

import app.kozica.media.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The restore's entry allowlist, and the traversal that refuses an archive whole (ADR-0005). */
class ArchiveEntriesTest {
    private val uuid = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"

    @Test
    fun `the three fixed members are recognised`() {
        assertEquals(ArchiveEntry.Manifest, archiveEntryFor(BACKUP_MANIFEST_ENTRY))
        assertEquals(ArchiveEntry.Database, archiveEntryFor(BACKUP_DATABASE_ENTRY))
        assertEquals(ArchiveEntry.Preferences, archiveEntryFor(BACKUP_PREFERENCES_ENTRY))
    }

    @Test
    fun `each media kind resolves to its own directory`() {
        MediaKind.entries.forEach { kind ->
            val name = "${kind.directory}/$uuid.jpg"
            assertEquals(ArchiveEntry.Media(kind, name), archiveEntryFor(name))
        }
    }

    @Test
    fun `a traversal, an absolute path or a nested path is not an entry`() {
        assertNull(archiveEntryFor("../secrets.txt"))
        assertNull(archiveEntryFor("photos/../../x.jpg"))
        assertNull(archiveEntryFor("/photos/$uuid.jpg"))
        assertNull(archiveEntryFor("photos/2026/$uuid.jpg"))
    }

    @Test
    fun `an unknown directory or a name that is not a canonical uuid is not an entry`() {
        assertNull(archiveEntryFor("preserved/$uuid.jpg"))
        assertNull(archiveEntryFor("$uuid.jpg"))
        assertNull(archiveEntryFor("photos/1-1-1-1-1.jpg"))
        assertNull(archiveEntryFor("photos/holiday.jpg"))
        assertNull(archiveEntryFor("photos/$uuid.png"))
        assertNull(archiveEntryFor("thumbnails/$uuid.jpg.exe"))
    }

    @Test
    fun `a traversal is hostile`() {
        assertTrue(isPathTraversal("../escaped.txt"))
        assertTrue(isPathTraversal("photos/../../x.jpg"))
        assertTrue(isPathTraversal("/photos/$uuid.jpg"))
        assertTrue(isPathTraversal("..\\escaped.txt"))
        assertTrue(isPathTraversal("../"))
    }

    @Test
    fun `an unknown entry is only unknown, so a later archive still restores`() {
        assertFalse(isPathTraversal(BACKUP_DATABASE_ENTRY))
        assertFalse(isPathTraversal("photos/$uuid.jpg"))
        assertFalse(isPathTraversal("videos/$uuid.mp4"))
        assertFalse(isPathTraversal("notes/..hidden.txt"))
    }
}
