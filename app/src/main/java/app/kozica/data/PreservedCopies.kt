package app.kozica.data

import java.io.File
import java.time.Instant
import java.time.format.DateTimeParseException

/** The two occupants of `preserved/` (ADR-0005): only a restore snapshot can be loaded back in. */
enum class PreservedKind {
    /** ADR-0001's copy before a wipe, at a stale schema: shared, never restored in place. */
    WipeCopy,

    /** The Records export a restore takes of what it replaces, at the current schema. */
    RestoreSnapshot,
    ;

    val restorable: Boolean get() = this == RestoreSnapshot
}

/**
 * One copy as the Backup screen lists it. [files] is the file and whichever sidecars exist, the unit
 * that is shared and deleted together. [savedAt] is read from the name, which dates the data.
 */
data class PreservedCopy(
    val file: File,
    val kind: PreservedKind,
    val savedAt: Instant?,
    val files: List<File>,
) {
    val name: String get() = file.name

    val totalBytes: Long get() = files.sumOf { it.length() }
}

/** Every copy in [preservedDir], newest first; files this app did not write are left out. */
fun listPreservedCopies(preservedDir: File): List<PreservedCopy> =
    preservedDir
        .listFiles()
        .orEmpty()
        .filter { it.isFile }
        .mapNotNull(::preservedCopyOrNull)
        .sortedWith(compareByDescending<PreservedCopy> { it.savedAt ?: Instant.EPOCH }.thenByDescending { it.name })

fun deletePreservedCopy(copy: PreservedCopy) {
    copy.files.forEach { it.delete() }
}

private fun preservedCopyOrNull(file: File): PreservedCopy? =
    when {
        file.name.startsWith(SNAPSHOT_PREFIX) && file.name.endsWith(SNAPSHOT_SUFFIX) ->
            PreservedCopy(
                file = file,
                kind = PreservedKind.RestoreSnapshot,
                savedAt = savedAtFromName(file.name, SNAPSHOT_PREFIX, SNAPSHOT_SUFFIX),
                files = listOf(file),
            )

        file.name.startsWith(PRESERVED_PREFIX) && file.name.endsWith(PRESERVED_SUFFIX) ->
            PreservedCopy(
                file = file,
                kind = PreservedKind.WipeCopy,
                savedAt = savedAtFromName(file.name, PRESERVED_PREFIX, PRESERVED_SUFFIX),
                files = listOf(file) + PRESERVED_SIDECAR_SUFFIXES.map { File(file.path + it) }.filter { it.isFile },
            )

        else -> null
    }

private fun savedAtFromName(
    name: String,
    prefix: String,
    suffix: String,
): Instant? =
    try {
        Instant.from(PRESERVED_TIMESTAMP_FORMAT.parse(name.removePrefix(prefix).removeSuffix(suffix)))
    } catch (e: DateTimeParseException) {
        null
    }
