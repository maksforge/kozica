package app.kozica.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import app.kozica.data.APP_SCHEMA_VERSION
import app.kozica.data.PRESERVED_SIDECAR_SUFFIXES
import app.kozica.data.PRESERVED_TIMESTAMP_FORMAT
import app.kozica.data.SNAPSHOT_PREFIX
import app.kozica.data.SNAPSHOT_SUFFIX
import app.kozica.data.buildAppDatabase
import app.kozica.data.readUserVersion
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.zip.ZipInputStream

const val STAGED_DATABASE_FILE = "restore-staged.db"

/**
 * The restored preferences wait here, relative to `filesDir`, for the next process start: the live
 * DataStore would write its own copy back over them before the process ends.
 */
const val RESTORED_PREFERENCES_PATH = "restore/app_preferences.preferences_pb"

/** A restored Everything archive's time, relative to `filesDir`, for the next process start. */
const val RESTORED_EVERYTHING_EXPORT_PATH = "restore/everything-export.txt"

/** A floor under a hostile or broken archive filling the phone. */
const val RESTORE_MAX_TOTAL_BYTES: Long = 2L * 1024 * 1024 * 1024

sealed interface RestoreOutcome {
    /**
     * [snapshot] is the Records export of what was replaced, in `preserved/`. [imagesWritten]
     * counts what reached the disk; [mediaComplete] is false when an image could not be written. The
     * database is restored either way.
     */
    data class Restored(
        val manifest: BackupManifest,
        val merge: MediaMergePlan,
        val snapshot: File,
        val imagesWritten: Int = 0,
        val mediaComplete: Boolean = true,
    ) : RestoreOutcome

    /** Nothing on the phone was touched. */
    data class Refused(
        val reason: RestoreRefusal,
    ) : RestoreOutcome
}

sealed interface RestoreRefusal {
    data object NotABackup : RestoreRefusal

    data class MadeByANewerKozica(
        val fileVersion: Int,
        val readableVersion: Int,
    ) : RestoreRefusal

    data object TooLarge : RestoreRefusal

    data object Unreadable : RestoreRefusal

    /** Checked by the caller: a restore would swap the database under the recording. */
    data object RecordingUnderway : RestoreRefusal
}

/**
 * Replaces the database from an archive and merges its media in (ADR-0005, docs/phase-6.md § 7):
 * stage, migrate on the copy, snapshot, swap. Every failure lands on the copy.
 */
class BackupRestorer(
    context: Context,
    private val filesDir: File,
    private val preservedDir: File,
    private val exporter: BackupExporter,
    private val databaseName: String,
    private val lock: Mutex = Mutex(),
    private val schemaVersion: Int = APP_SCHEMA_VERSION,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext

    private val liveDatabase: File get() = appContext.getDatabasePath(databaseName)

    private val stagedDatabase: File get() = appContext.getDatabasePath(STAGED_DATABASE_FILE)

    private val stagedPreferences: File get() = File(filesDir, "$RESTORED_PREFERENCES_PATH.part")

    /** For the confirmation. [open] is a factory because a `content://` stream cannot be rewound. */
    suspend fun readManifest(open: () -> InputStream): BackupManifest? =
        withContext(io) {
            var manifest: BackupManifest? = null
            val walked =
                forEachEntry(open) { entry, stream ->
                    if (entry is ArchiveEntry.Manifest) manifest = decodeManifest(stream.readBytes().decodeToString())
                    manifest == null
                }
            if (walked) manifest else null
        }

    suspend fun restore(
        open: () -> InputStream,
        now: Instant = Instant.now(),
    ): RestoreOutcome = lock.withLock { restoreUnlocked(open, now) }

    private suspend fun restoreUnlocked(
        open: () -> InputStream,
        now: Instant,
    ): RestoreOutcome =
        withContext(io) {
            clearStaging()
            var manifest: BackupManifest? = null
            var sawDatabase = false
            val mediaEntries = mutableListOf<String>()
            var totalBytes = 0L
            var tooLarge = false

            val walked =
                forEachEntry(open) { entry, stream ->
                    when (entry) {
                        is ArchiveEntry.Manifest ->
                            stream.readBytes().let { bytes ->
                                totalBytes += bytes.size
                                manifest = decodeManifest(bytes.decodeToString())
                            }

                        is ArchiveEntry.Database -> {
                            sawDatabase = true
                            totalBytes += stream.drainTo(stagedDatabase)
                        }

                        is ArchiveEntry.Preferences -> totalBytes += stream.drainTo(stagedPreferences)

                        is ArchiveEntry.Media -> {
                            mediaEntries += entry.relativePath
                            totalBytes += stream.discard()
                        }
                    }
                    if (totalBytes > RESTORE_MAX_TOTAL_BYTES) tooLarge = true
                    !tooLarge
                }

            if (!walked) return@withContext refuse(RestoreRefusal.NotABackup)
            if (tooLarge) return@withContext refuse(RestoreRefusal.TooLarge)
            val declared = manifest
            if (declared == null || !sawDatabase) return@withContext refuse(RestoreRefusal.NotABackup)
            if (declared.format > BACKUP_FORMAT_VERSION) {
                return@withContext refuse(RestoreRefusal.MadeByANewerKozica(declared.format, BACKUP_FORMAT_VERSION))
            }
            // Before Room, whose default handler deletes a corrupt file and would swap an empty one in.
            if (!isIntactDatabase(stagedDatabase)) return@withContext refuse(RestoreRefusal.Unreadable)
            // The file's header, not the manifest's claim, is the authority on its schema.
            val fileSchemaVersion = readUserVersion(stagedDatabase)
            if (fileSchemaVersion > schemaVersion) {
                return@withContext refuse(RestoreRefusal.MadeByANewerKozica(fileSchemaVersion, schemaVersion))
            }
            if (fileSchemaVersion < 1) return@withContext refuse(RestoreRefusal.Unreadable)
            if (!migrateStagedDatabase()) return@withContext refuse(RestoreRefusal.Unreadable)

            val snapshot =
                exporter.exportTo(
                    target =
                        File(
                            preservedDir,
                            "$SNAPSHOT_PREFIX${PRESERVED_TIMESTAMP_FORMAT.format(now)}$SNAPSHOT_SUFFIX",
                        ),
                    scope = BackupScope.Records,
                    now = now,
                )
            val merge =
                planMediaMerge(
                    archivePaths = mediaEntries,
                    diskPaths = mediaPathsOnDisk(filesDir),
                    scope = declared.scope,
                )

            swapInStagedDatabase()
            val media = extractMedia(open)
            if (stagedPreferences.isFile) stagedPreferences.renameTo(File(filesDir, RESTORED_PREFERENCES_PATH))
            if (declared.scope == BackupScope.Everything) stageEverythingExport(declared.createdAtEpochMilli)

            RestoreOutcome.Restored(declared, merge, snapshot, media.written, media.complete)
        }

    /** Opens the copy with the real migrations; false when this build cannot open it at all. */
    private fun migrateStagedDatabase(): Boolean =
        try {
            val database = buildAppDatabase(appContext, STAGED_DATABASE_FILE, allowDestructiveMigration = false)
            try {
                database.openHelper.writableDatabase
            } finally {
                database.close()
            }
            true
        } catch (e: RuntimeException) {
            false
        }

    private fun swapInStagedDatabase() {
        val live = liveDatabase
        val staged = stagedDatabase
        PRESERVED_SIDECAR_SUFFIXES.forEach { File(live.path + it).delete() }
        live.delete()
        if (!staged.renameTo(live)) {
            staged.copyTo(live, overwrite = true)
            staged.delete()
        }
        PRESERVED_SIDECAR_SUFFIXES.forEach { File(staged.path + it).delete() }
    }

    /** Through a `.part`, so an image already on the phone is replaced whole or not at all. */
    private fun extractMedia(open: () -> InputStream): MediaPass {
        var written = 0
        val complete =
            forEachEntry(open) { entry, stream ->
                if (entry is ArchiveEntry.Media) {
                    stream.drainThroughPartTo(File(filesDir, entry.relativePath))
                    written++
                }
                true
            }
        return MediaPass(written, complete)
    }

    private data class MediaPass(
        val written: Int,
        val complete: Boolean,
    )

    /** Best effort: losing it only brings the Hikes banner back. */
    private fun stageEverythingExport(createdAtEpochMilli: Long) {
        try {
            File(filesDir, RESTORED_EVERYTHING_EXPORT_PATH).apply {
                parentFile?.mkdirs()
                writeText(createdAtEpochMilli.toString())
            }
        } catch (e: IOException) {
            return
        }
    }

    private fun refuse(reason: RestoreRefusal): RestoreOutcome {
        clearStaging()
        return RestoreOutcome.Refused(reason)
    }

    private fun clearStaging() {
        val staged = stagedDatabase
        staged.delete()
        PRESERVED_SIDECAR_SUFFIXES.forEach { File(staged.path + it).delete() }
        stagedPreferences.delete()
    }
}

private fun isIntactDatabase(file: File): Boolean =
    try {
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE) { }.use { database ->
            database.rawQuery("PRAGMA quick_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }
        }
    } catch (e: SQLiteException) {
        false
    }

/**
 * Puts preferences a restore left waiting into DataStore's place. Called at process start before
 * the DataStore is first touched.
 */
fun adoptRestoredPreferences(filesDir: File): Boolean {
    val staged = File(filesDir, RESTORED_PREFERENCES_PATH)
    if (!staged.isFile) return false
    val live = File(filesDir, PREFERENCES_FILE_PATH)
    live.parentFile?.mkdirs()
    staged.copyTo(live, overwrite = true)
    staged.delete()
    staged.parentFile?.delete()
    return true
}

/**
 * When the restored Everything archive was made, once, at the next process start; null when the
 * last restore was not one.
 */
fun adoptRestoredEverythingExport(filesDir: File): Instant? {
    val staged = File(filesDir, RESTORED_EVERYTHING_EXPORT_PATH)
    if (!staged.isFile) return null
    val at = runCatching { staged.readText().trim().toLong() }.getOrNull()?.let(Instant::ofEpochMilli)
    staged.delete()
    staged.parentFile?.delete()
    return at
}

/**
 * Hands every allowlisted entry to [onEntry], which returns false to stop. Returns false when the
 * archive could not be walked to its end: truncated, not a zip, or carrying a traversal entry, which
 * refuses the whole file on every API level.
 */
private inline fun forEachEntry(
    open: () -> InputStream,
    onEntry: (ArchiveEntry, InputStream) -> Boolean,
): Boolean {
    try {
        ZipInputStream(open().buffered()).use { zip ->
            while (true) {
                val next = zip.nextEntry ?: break
                if (isPathTraversal(next.name)) return false
                if (!next.isDirectory) {
                    val entry = archiveEntryFor(next.name)
                    if (entry != null && !onEntry(entry, zip)) break
                }
                zip.closeEntry()
            }
        }
    } catch (_: IOException) {
        return false
    }
    return true
}

private fun InputStream.drainTo(target: File): Long {
    target.parentFile?.mkdirs()
    return target.outputStream().use { copyTo(it) }
}

private fun InputStream.drainThroughPartTo(target: File) {
    val part = File(target.path + ".part")
    try {
        drainTo(part)
        if (!part.renameTo(target)) throw IOException("Cannot rename $part")
    } finally {
        part.delete()
    }
}

private fun InputStream.discard(): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) return total
        total += read
    }
}
