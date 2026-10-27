package app.kozica.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.kozica.data.APP_SCHEMA_VERSION
import app.kozica.data.AppDatabase
import app.kozica.data.buildAppDatabase
import app.kozica.data.readUserVersion
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Stage, migrate, swap (docs/phase-6.md § 7) on real files. Throwaway database names throughout,
 * never `kozica.db`.
 */
@RunWith(AndroidJUnit4::class)
class RestoreTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var root: File
    private lateinit var filesDir: File
    private lateinit var preservedDir: File
    private val databaseNames = mutableListOf<String>()

    @Before
    fun setUp() {
        root = File(context.cacheDir, "restore-test-${UUID.randomUUID()}")
        filesDir = File(root, "files").apply { mkdirs() }
        preservedDir = File(root, "preserved").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        (databaseNames + STAGED_DATABASE_FILE).forEach { name ->
            listOf(
                "",
                "-wal",
                "-shm",
                "-journal",
                ".lck",
            ).forEach { File(context.getDatabasePath(name).path + it).delete() }
        }
        root.deleteRecursively()
    }

    @Test
    fun aBackupAtTheCurrentSchemaReplacesTheLiveDatabaseAndLeavesASnapshot() =
        runTest {
            val archive = exporterFor(databaseHolding("Restored")).exportTo(File(root, "a.zip"), BackupScope.Records)
            val live = databaseHolding("Already here")

            val outcome = restorerFor(live).restore(open = { archive.inputStream() })

            val snapshot = (outcome as RestoreOutcome.Restored).snapshot
            assertEquals(listOf("Restored"), routeNamesIn(live))
            assertEquals(preservedDir, snapshot.parentFile)
            assertTrue(snapshot.length() > 0)
        }

    @Test
    fun aSchemaTwoBackupIsMigratedOnTheCopyAndSwappedIn() =
        runTest {
            val old = throwawayName("schema-2")
            helper.createDatabase(old, 2).apply {
                execSQL("INSERT INTO routes (id, name, createdAt, updatedAt) VALUES ('r', 'Kuźnice – Kasprowy', 1, 1)")
                execSQL("INSERT INTO route_points VALUES ('r', 0, 49.2702, 19.9808, 'Kuźnice')")
                close()
            }
            val archive =
                zipOf(
                    BACKUP_MANIFEST_ENTRY to manifestBytes(schemaVersion = 2),
                    BACKUP_DATABASE_ENTRY to context.getDatabasePath(old).readBytes(),
                )
            val live = databaseHolding("Already here")

            val outcome = restorerFor(live).restore(open = { archive.inputStream() })

            assertTrue(outcome.toString(), outcome is RestoreOutcome.Restored)
            assertEquals(APP_SCHEMA_VERSION, readUserVersion(context.getDatabasePath(live)))
            assertEquals(listOf("Kuźnice – Kasprowy"), routeNamesIn(live))
        }

    /** The file's header is the authority, not the manifest, which here claims the current schema. */
    @Test
    fun aNewerSchemaIsRefusedAndTheLiveDatabaseIsUntouched() =
        runTest {
            val newer = rawDatabase(userVersion = APP_SCHEMA_VERSION + 1)
            val archive = zipOf(BACKUP_MANIFEST_ENTRY to manifestBytes(), BACKUP_DATABASE_ENTRY to newer.readBytes())
            val live = databaseHolding("Already here")
            val before = context.getDatabasePath(live).readBytes()

            val outcome = restorerFor(live).restore(open = { archive.inputStream() })

            assertEquals(
                RestoreOutcome.Refused(RestoreRefusal.MadeByANewerKozica(APP_SCHEMA_VERSION + 1, APP_SCHEMA_VERSION)),
                outcome,
            )
            assertArrayEquals(before, context.getDatabasePath(live).readBytes())
        }

    /** A debug build's destructive fallback would have emptied the copy and swapped it in. */
    @Test
    fun aFileThisBuildCannotMigrateIsRefusedRatherThanEmptied() =
        runTest {
            val broken = rawDatabase(userVersion = 2)
            val archive =
                zipOf(
                    BACKUP_MANIFEST_ENTRY to manifestBytes(schemaVersion = 2),
                    BACKUP_DATABASE_ENTRY to broken.readBytes(),
                )
            val live = databaseHolding("Already here")

            val outcome = restorerFor(live).restore(open = { archive.inputStream() })

            assertEquals(RestoreOutcome.Refused(RestoreRefusal.Unreadable), outcome)
            assertEquals(listOf("Already here"), routeNamesIn(live))
        }

    /** Room's default corruption handler would delete it, and an empty database would be swapped in. */
    @Test
    fun aFileThatIsNotADatabaseIsRefused() =
        runTest {
            val archive =
                zipOf(
                    BACKUP_MANIFEST_ENTRY to manifestBytes(),
                    BACKUP_DATABASE_ENTRY to "not a database at all ".repeat(200).toByteArray(),
                )
            val live = databaseHolding("Already here")

            val outcome = restorerFor(live).restore(open = { archive.inputStream() })

            assertEquals(RestoreOutcome.Refused(RestoreRefusal.Unreadable), outcome)
            assertEquals(listOf("Already here"), routeNamesIn(live))
        }

    /** -1 has no migration path, where a debug build's default would empty the file. */
    @Test
    fun thePinnedConfigurationThrowsAndLeavesTheFileAlone() {
        val name = throwawayName("pinned")
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        rawDatabase(userVersion = -1).copyTo(file, overwrite = true)

        val database = buildAppDatabase(context, name, allowDestructiveMigration = false)
        val threw =
            try {
                database.openHelper.writableDatabase
                false
            } catch (e: RuntimeException) {
                true
            } finally {
                database.close()
            }

        assertTrue(threw)
        assertEquals(-1, readUserVersion(file))
    }

    @Test
    fun aTraversalEntryRefusesTheWholeArchiveAndNothingEscapes() =
        runTest {
            val archive =
                zipOf(
                    BACKUP_MANIFEST_ENTRY to manifestBytes(),
                    BACKUP_DATABASE_ENTRY to context.getDatabasePath(databaseHolding("Restored")).readBytes(),
                    "../escaped.txt" to "never written".toByteArray(),
                )
            val live = databaseHolding("Already here")

            val outcome = restorerFor(live).restore(open = { archive.inputStream() })

            assertEquals(RestoreOutcome.Refused(RestoreRefusal.NotABackup), outcome)
            assertEquals(listOf("Already here"), routeNamesIn(live))
            assertTrue(root.walkTopDown().none { it.name == "escaped.txt" })
        }

    @Test
    fun anArchiveWithoutAManifestOrADatabaseIsNotABackup() =
        runTest {
            val live = databaseHolding("Already here")
            val noManifest = zipOf(BACKUP_DATABASE_ENTRY to context.getDatabasePath(live).readBytes())
            val noDatabase = zipOf(BACKUP_MANIFEST_ENTRY to manifestBytes())

            assertEquals(
                RestoreOutcome.Refused(RestoreRefusal.NotABackup),
                restorerFor(live).restore(open = { noManifest.inputStream() }),
            )
            assertEquals(
                RestoreOutcome.Refused(RestoreRefusal.NotABackup),
                restorerFor(live).restore(open = { noDatabase.inputStream() }),
            )
        }

    @Test
    fun aRecordsRestoreKeepsThePhotosOnThePhoneAndStagesThePreferences() =
        runTest {
            val photo = File(filesDir, "photos/3f2504e0-4f89-41d3-9a0c-0305e82c3303.jpg")
            photo.parentFile?.mkdirs()
            photo.writeText("Kasprowy from the ridge")
            File(filesDir, PREFERENCES_FILE_PATH).apply { parentFile?.mkdirs() }.writeText("preferences")
            val archive = exporterFor(databaseHolding("Restored")).exportTo(File(root, "a.zip"), BackupScope.Records)
            File(filesDir, PREFERENCES_FILE_PATH).writeText("changed since")

            val outcome = restorerFor(databaseHolding("Already here")).restore(open = { archive.inputStream() })

            assertEquals(listOf("photos/${photo.name}"), (outcome as RestoreOutcome.Restored).merge.kept)
            assertEquals("Kasprowy from the ridge", photo.readText())
            assertEquals("changed since", File(filesDir, PREFERENCES_FILE_PATH).readText())
            assertEquals("preferences", File(filesDir, RESTORED_PREFERENCES_PATH).readText())
        }

    @Test
    fun anEverythingRestoreNeverWritesOverAPhotoAlreadyThereAndStagesItsTime() =
        runTest {
            val kept = File(filesDir, "photos/3f2504e0-4f89-41d3-9a0c-0305e82c3303.jpg")
            val added = File(filesDir, "photos/3f2504e0-4f89-41d3-9a0c-0305e82c3304.jpg")
            listOf(kept, added).forEach { it.apply { parentFile?.mkdirs() }.writeText("from the archive") }
            val archive =
                exporterFor(databaseHolding("Restored"))
                    .exportTo(File(root, "a.zip"), BackupScope.Everything, Instant.ofEpochMilli(1_793_088_000_000))
            kept.writeText("already on the phone")
            added.delete()

            val outcome = restorerFor(databaseHolding("Already here")).restore(open = { archive.inputStream() })

            assertTrue((outcome as RestoreOutcome.Restored).mediaComplete)
            assertEquals("already on the phone", kept.readText())
            assertEquals("from the archive", added.readText())
            assertTrue(File(filesDir, "photos").listFiles()!!.none { it.name.endsWith(".part") })
            assertEquals(Instant.ofEpochMilli(1_793_088_000_000), adoptRestoredEverythingExport(filesDir))
        }

    @Test
    fun aMediaPassThatCannotReadTheArchiveIsReportedIncomplete() =
        runTest {
            File(filesDir, "photos/3f2504e0-4f89-41d3-9a0c-0305e82c3304.jpg")
                .apply { parentFile?.mkdirs() }
                .writeText("from the archive")
            val archive = exporterFor(databaseHolding("Restored")).exportTo(File(root, "a.zip"), BackupScope.Everything)
            var opens = 0

            val outcome =
                restorerFor(databaseHolding("Already here")).restore(open = {
                    if (++opens > 1) throw IOException("gone") else archive.inputStream()
                })

            assertFalse((outcome as RestoreOutcome.Restored).mediaComplete)
        }

    private fun throwawayName(prefix: String) = "$prefix-${UUID.randomUUID()}.db".also { databaseNames += it }

    private fun databaseHolding(routeName: String): String {
        val name = throwawayName("restore")
        val database = buildAppDatabase(context, name)
        try {
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO routes (id, name, createdAt, updatedAt) VALUES ('${UUID.randomUUID()}', '$routeName', 1, 1)",
            )
        } finally {
            database.close()
        }
        return name
    }

    private fun routeNamesIn(name: String): List<String> {
        val database = buildAppDatabase(context, name, allowDestructiveMigration = false)
        return try {
            database.openHelper.readableDatabase.query("SELECT name FROM routes ORDER BY name").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
        } finally {
            database.close()
        }
    }

    private fun exporterFor(name: String) =
        BackupExporter(context.getDatabasePath(name), filesDir, File(root, "scratch"))

    private fun restorerFor(live: String) = BackupRestorer(context, filesDir, preservedDir, exporterFor(live), live)

    private fun manifestBytes(schemaVersion: Int = APP_SCHEMA_VERSION): ByteArray =
        encodeManifest(
            BackupManifest(scope = BackupScope.Records, schemaVersion = schemaVersion, createdAtEpochMilli = 1),
        ).toByteArray()

    private fun zipOf(vararg entries: Pair<String, ByteArray>): File {
        val target = File(root, "hand-built-${UUID.randomUUID()}.zip")
        ZipOutputStream(target.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return target
    }

    /** A real SQLite file at [userVersion] with a table this build does not know. */
    private fun rawDatabase(userVersion: Int): File {
        val file = File(root, "raw-${UUID.randomUUID()}.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL("CREATE TABLE something_else (id TEXT PRIMARY KEY)")
            database.execSQL("INSERT INTO something_else VALUES ('a summer of hikes')")
            database.execSQL("PRAGMA user_version = $userVersion")
        }
        return file
    }
}
