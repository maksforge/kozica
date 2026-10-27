package app.kozica.data.backup

import app.kozica.media.MediaKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Instant

/** The archive layout's version, apart from the database schema. */
const val BACKUP_FORMAT_VERSION = 1

/**
 * What an archive says about itself, and the authority for what it holds (ADR-0005): the
 * confirmation reads this, never the filename. [mediaCounts] is keyed by [MediaKind.name] so an
 * unknown kind decodes as an unknown key.
 */
@Serializable
data class BackupManifest(
    val format: Int = BACKUP_FORMAT_VERSION,
    val scope: BackupScope,
    val schemaVersion: Int,
    val createdAtEpochMilli: Long,
    val mediaCounts: Map<String, Int> = emptyMap(),
) {
    val createdAt: Instant get() = Instant.ofEpochMilli(createdAtEpochMilli)

    fun countFor(kind: MediaKind): Int = mediaCounts[kind.name] ?: 0
}

private val manifestJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

fun encodeManifest(manifest: BackupManifest): String = manifestJson.encodeToString(manifest)

/** Null for anything that is not a manifest of ours, an unknown scope included. */
fun decodeManifest(text: String): BackupManifest? =
    try {
        manifestJson.decodeFromString<BackupManifest>(text)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
