package app.kozica.data.backup

import app.kozica.media.MediaKind

const val BACKUP_MANIFEST_ENTRY = "manifest.json"

const val BACKUP_DATABASE_ENTRY = "database/kozica.db"

/** DataStore's own file, carried byte for byte. */
const val BACKUP_PREFERENCES_ENTRY = "preferences/app_preferences.preferences_pb"

/** Where `preferencesDataStore(name = "app_preferences")` keeps that file, relative to `filesDir`. */
const val PREFERENCES_FILE_PATH = "datastore/app_preferences.preferences_pb"

/** One entry a restore will read (ADR-0005). Everything else in an archive is ignored. */
sealed interface ArchiveEntry {
    data object Manifest : ArchiveEntry

    data object Database : ArchiveEntry

    data object Preferences : ArchiveEntry

    /** [relativePath] was matched as `<kind.directory>/<uuid>.jpg`, so it is safe to join onto `filesDir`. */
    data class Media(
        val kind: MediaKind,
        val relativePath: String,
    ) : ArchiveEntry
}

/** Stricter than `UUID.fromString`, which accepts `1-1-1-1-1`. */
private val MEDIA_FILE_NAME =
    Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.jpg")

/**
 * Whether [name] climbs out of the directory it would be joined onto. A hostile archive is refused
 * whole, where an unknown entry is only skipped; below API 34 the platform does not check this itself.
 */
fun isPathTraversal(name: String): Boolean {
    val separated = name.replace('\\', '/')
    return separated.startsWith("/") || separated.split('/').any { it == ".." }
}

/** The entry [name] names, or null. An allowlist: no path is ever built from archive input. */
fun archiveEntryFor(name: String): ArchiveEntry? {
    when (name) {
        BACKUP_MANIFEST_ENTRY -> return ArchiveEntry.Manifest
        BACKUP_DATABASE_ENTRY -> return ArchiveEntry.Database
        BACKUP_PREFERENCES_ENTRY -> return ArchiveEntry.Preferences
    }
    val segments = name.split('/')
    if (segments.size != 2) return null
    val (directory, fileName) = segments
    val kind = MediaKind.entries.firstOrNull { it.directory == directory } ?: return null
    if (!MEDIA_FILE_NAME.matches(fileName)) return null
    return ArchiveEntry.Media(kind = kind, relativePath = name)
}
