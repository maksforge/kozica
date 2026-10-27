package app.kozica.data.gpx

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

const val GPX_DIRECTORY = "gpx"

/**
 * Writes a GPX into [dir] in the cache, on its way to the share sheet (docs/phase-6.md § 8). Each write
 * clears the last, whose share is long over by the time the user exports again.
 */
class GpxFiles(
    private val dir: File,
) {
    suspend fun write(
        gpx: Gpx,
        date: LocalDate,
    ): File =
        withContext(Dispatchers.IO) {
            dir.deleteRecursively()
            dir.mkdirs()
            File(dir, gpxFileName(gpx.name, date)).also { file -> file.bufferedWriter().use { gpx.write(it) } }
        }
}
