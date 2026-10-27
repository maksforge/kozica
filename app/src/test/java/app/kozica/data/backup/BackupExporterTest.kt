package app.kozica.data.backup

import app.kozica.media.MediaKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.util.zip.ZipInputStream

/** The archive's layout at each scope; the checkpoint is a plain copy, as SQLite is not on the JVM. */
class BackupExporterTest {
    @get:Rule val temp = TemporaryFolder()

    private val thumbnail = "3f2504e0-4f89-41d3-9a0c-0305e82c3301.jpg"
    private val photo = "3f2504e0-4f89-41d3-9a0c-0305e82c3302.jpg"

    private lateinit var filesDir: File
    private lateinit var databaseFile: File
    private lateinit var scratchDir: File

    @Before
    fun setUp() {
        filesDir = temp.newFolder("files")
        scratchDir = temp.newFolder("cache")
        databaseFile = temp.newFile("kozica.db").apply { writeText("a summer of hikes") }
        write(File(filesDir, "${MediaKind.Thumbnail.directory}/$thumbnail"), "thumbnail")
        write(File(filesDir, "${MediaKind.Photo.directory}/$photo"), "photo")
        write(File(filesDir, PREFERENCES_FILE_PATH), "preferences")
        write(File(filesDir, "${MediaKind.Photo.directory}/notes.txt"), "stray")
        write(File(filesDir, "preserved/db-20261027T073547Z.db"), "a copy taken before a wipe")
    }

    @Test
    fun `Records carries the database, the preferences and the thumbnails`() =
        runTest {
            val entries = exportAt(BackupScope.Records)

            assertEquals(
                setOf(BACKUP_MANIFEST_ENTRY, BACKUP_DATABASE_ENTRY, BACKUP_PREFERENCES_ENTRY, "thumbnails/$thumbnail"),
                entries.keys,
            )
            assertEquals("a summer of hikes", entries[BACKUP_DATABASE_ENTRY])
            assertEquals("preferences", entries[BACKUP_PREFERENCES_ENTRY])
        }

    @Test
    fun `Everything adds the photos, and never a stray file or a preserved copy`() =
        runTest {
            assertEquals(
                setOf(
                    BACKUP_MANIFEST_ENTRY,
                    BACKUP_DATABASE_ENTRY,
                    BACKUP_PREFERENCES_ENTRY,
                    "thumbnails/$thumbnail",
                    "photos/$photo",
                ),
                exportAt(BackupScope.Everything).keys,
            )
        }

    @Test
    fun `the manifest goes first and counts what the scope carries`() =
        runTest {
            val target = File(temp.newFolder("first"), "archive.zip")
            exporter().exportTo(target, BackupScope.Records, Instant.ofEpochMilli(1_800_000_000_000))
            val first = ZipInputStream(target.inputStream()).use { it.nextEntry!!.name }
            val manifest = decodeManifest(entriesOf(target).getValue(BACKUP_MANIFEST_ENTRY))!!

            assertEquals(BACKUP_MANIFEST_ENTRY, first)
            assertEquals(BackupScope.Records, manifest.scope)
            assertEquals(3, manifest.schemaVersion)
            assertEquals(Instant.ofEpochMilli(1_800_000_000_000), manifest.createdAt)
            assertEquals(1, manifest.countFor(MediaKind.Thumbnail))
            assertEquals(0, manifest.countFor(MediaKind.Photo))
        }

    @Test
    fun `the filename names the scope for people`() {
        assertEquals(
            "kozica-everything-20261027T000000Z.zip",
            exportFileName(BackupScope.Everything, Instant.parse("2026-10-27T00:00:00Z")),
        )
    }

    @Test
    fun `no part file survives an export`() =
        runTest {
            val out = temp.newFolder("out-part")
            exporter().exportTo(File(out, "archive.zip"), BackupScope.Records)

            assertEquals(listOf("archive.zip"), out.list()?.toList())
        }

    private fun exporter() =
        BackupExporter(
            databaseFile = databaseFile,
            filesDir = filesDir,
            scratchDir = scratchDir,
            schemaVersion = 3,
            checkpoint = { source, target -> source.copyTo(target, overwrite = true) },
        )

    private suspend fun exportAt(scope: BackupScope): Map<String, String> =
        entriesOf(exporter().exportTo(File(temp.newFolder("out-${scope.name}"), "archive.zip"), scope))

    private fun entriesOf(archive: File): Map<String, String> =
        buildMap {
            ZipInputStream(archive.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    put(entry.name, zip.readBytes().decodeToString())
                }
            }
        }

    private fun write(
        file: File,
        text: String,
    ) {
        file.parentFile?.mkdirs()
        file.writeText(text)
    }
}
