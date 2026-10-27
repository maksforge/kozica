package app.kozica.data.gpx

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

const val GPX_DIRECTORY = "gpx"

/**
 * Writes a GPX into [dir] in the cache, on its way to the share sheet (docs/phase-6.md § 8). Each write
 * clears the last, whose share is long over by the time the user exports again, and [revokeShares]
 * first takes back every app's read access to it.
 */
class GpxFiles(
    private val dir: File,
    private val revokeShares: () -> Unit,
) {
    private val lock = Mutex()

    suspend fun write(
        gpx: Gpx,
        date: LocalDate,
    ): File =
        lock.withLock {
            withContext(Dispatchers.IO) {
                revokeShares()
                dir.deleteRecursively()
                dir.mkdirs()
                File(dir, gpxFileName(gpx.name, date)).also { file -> file.bufferedWriter().use { gpx.write(it) } }
            }
        }
}
