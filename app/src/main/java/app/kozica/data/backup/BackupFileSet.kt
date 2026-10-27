package app.kozica.data.backup

import app.kozica.media.MediaKind
import java.io.File

/** One file as it appears inside an archive. */
data class ArchiveFile(
    val entryName: String,
    val file: File,
)

fun mediaFilesFor(
    scope: BackupScope,
    mediaRoot: File,
): List<ArchiveFile> = mediaFilesFor(scope.mediaKinds, mediaRoot)

/**
 * The media of [kinds] under [mediaRoot], sorted by entry name. The restore's allowlist decides what
 * leaves too, so a stray file is skipped here rather than shipped and refused at the far end.
 */
fun mediaFilesFor(
    kinds: List<MediaKind>,
    mediaRoot: File,
): List<ArchiveFile> =
    kinds
        .flatMap { kind ->
            File(mediaRoot, kind.directory)
                .listFiles()
                .orEmpty()
                .filter { it.isFile }
                .mapNotNull { file ->
                    val entryName = "${kind.directory}/${file.name}"
                    if (archiveEntryFor(entryName) is ArchiveEntry.Media) ArchiveFile(entryName, file) else null
                }
        }.sortedBy { it.entryName }

fun mediaPathsOnDisk(mediaRoot: File): List<String> = mediaFilesFor(MediaKind.entries, mediaRoot).map { it.entryName }
