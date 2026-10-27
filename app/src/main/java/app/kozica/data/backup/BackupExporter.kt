package app.kozica.data.backup

import app.kozica.data.APP_SCHEMA_VERSION
import app.kozica.data.PRESERVED_TIMESTAMP_FORMAT
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Where an export waits for the share sheet, relative to `cacheDir`. */
const val EXPORTS_DIRECTORY = "exports"

fun exportFileName(
    scope: BackupScope,
    at: Instant,
): String = "kozica-${scope.slug}-${PRESERVED_TIMESTAMP_FORMAT.format(at)}.zip"

/**
 * Writes a backup zip at a [BackupScope] (ADR-0005). Paths rather than a `Context`, so the layout is
 * testable on the JVM; [checkpoint] is the seam that stands in for SQLite there.
 */
class BackupExporter(
    private val databaseFile: File,
    private val filesDir: File,
    private val scratchDir: File,
    private val schemaVersion: Int = APP_SCHEMA_VERSION,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val checkpoint: (File, File) -> Unit = ::checkpointDatabaseTo,
) {
    /** Written to a `.part` and renamed, so an interrupted export leaves nothing that looks whole. */
    suspend fun exportTo(
        target: File,
        scope: BackupScope,
        now: Instant = Instant.now(),
    ): File =
        withContext(io) {
            target.parentFile?.mkdirs()
            scratchDir.mkdirs()
            val staged = File(scratchDir, "export-staged.db")
            val part = File(target.path + ".part")
            try {
                checkpoint(databaseFile, staged)
                val media = mediaFilesFor(scope, filesDir)
                val manifest =
                    BackupManifest(
                        scope = scope,
                        schemaVersion = schemaVersion,
                        createdAtEpochMilli = now.toEpochMilli(),
                        mediaCounts =
                            scope.mediaKinds.associate { kind ->
                                kind.name to media.count { it.entryName.startsWith("${kind.directory}/") }
                            },
                    )
                ZipOutputStream(part.outputStream().buffered()).use { zip ->
                    zip.setLevel(Deflater.BEST_SPEED)
                    zip.write(BACKUP_MANIFEST_ENTRY, encodeManifest(manifest).toByteArray())
                    zip.write(BACKUP_DATABASE_ENTRY, staged)
                    val preferences = File(filesDir, PREFERENCES_FILE_PATH)
                    if (preferences.isFile) zip.write(BACKUP_PREFERENCES_ENTRY, preferences)
                    media.forEach { zip.write(it.entryName, it.file) }
                }
                target.delete()
                if (!part.renameTo(target)) {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                }
                target
            } catch (e: Throwable) {
                part.delete()
                throw e
            } finally {
                staged.delete()
            }
        }
}

private fun ZipOutputStream.write(
    entryName: String,
    bytes: ByteArray,
) {
    putNextEntry(ZipEntry(entryName))
    write(bytes)
    closeEntry()
}

private fun ZipOutputStream.write(
    entryName: String,
    file: File,
) {
    putNextEntry(ZipEntry(entryName))
    file.inputStream().use { it.copyTo(this) }
    closeEntry()
}
