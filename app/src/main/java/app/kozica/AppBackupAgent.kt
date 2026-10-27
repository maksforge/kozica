package app.kozica

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.Build
import android.os.ParcelFileDescriptor
import app.kozica.data.APP_DATABASE_FILE
import app.kozica.data.backup.AUTO_BACKUP_STAGING_PATH
import app.kozica.data.backup.adoptRestoredDatabase
import app.kozica.data.backup.autoBackupFileSet
import app.kozica.data.backup.checkpointDatabaseTo
import app.kozica.data.backup.clearAutoBackupMarker
import app.kozica.data.backup.writeAutoBackupMarker
import java.io.File
import java.time.Instant

/**
 * Auto Backup with its own file set (ADR-0005, docs/phase-6.md § 7). The system binds the base
 * `Application` for a backup, not [MainApplication], so this reaches for paths and nothing else.
 * Needs `fullBackupOnly="true"`, or the no-op key/value [onBackup] is what runs.
 */
class AppBackupAgent : BackupAgent() {
    override fun onFullBackup(data: FullBackupDataOutput) {
        val staged = File(filesDir, AUTO_BACKUP_STAGING_PATH)
        try {
            val databaseFile = getDatabasePath(APP_DATABASE_FILE)
            if (databaseFile.isFile) checkpointDatabaseTo(databaseFile, staged)
            val set = autoBackupFileSet(filesDir, staged, deviceToDeviceTransfer = data.isDeviceToDeviceTransfer())
            set.files.forEach { fullBackupFile(it, data) }
            // The transport can still fail after this; there is no callback to say so.
            writeAutoBackupMarker(filesDir, Instant.now(), set.excludedThumbnails)
        } finally {
            staged.delete()
            staged.parentFile?.delete()
        }
    }

    override fun onRestoreFinished() {
        adoptRestoredDatabase(filesDir, getDatabasePath(APP_DATABASE_FILE))
        clearAutoBackupMarker(filesDir)
    }

    private fun FullBackupDataOutput.isDeviceToDeviceTransfer(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && (transportFlags and FLAG_DEVICE_TO_DEVICE_TRANSFER) != 0

    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?,
    ) = Unit

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?,
    ) = Unit
}
