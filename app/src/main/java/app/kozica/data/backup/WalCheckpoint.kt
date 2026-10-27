package app.kozica.data.backup

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import java.io.File

/**
 * Copies [databaseFile] to [target] with its write-ahead log folded in first (ADR-0005), on a
 * connection of its own so the backup agent can call it with no Room. `TRUNCATE`, because `PASSIVE`
 * gives up quietly when a reader is in the way.
 */
fun checkpointDatabaseTo(
    databaseFile: File,
    target: File,
) {
    if (databaseFile.isFile) {
        try {
            SQLiteDatabase
                .openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE)
                .use { database ->
                    database.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
                }
        } catch (e: SQLiteException) {
            // The copy still carries everything up to the last checkpoint, which beats no copy.
        }
    }
    target.parentFile?.mkdirs()
    databaseFile.copyTo(target, overwrite = true)
}
