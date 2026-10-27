package app.kozica.data.backup

/**
 * What a restore does to the media already on the phone (ADR-0005). Nothing is ever deleted:
 * [overlaid] are written from the archive, [kept] are in kinds the scope does not carry, and
 * [orphaned] are in kinds it does carry but are absent from it, so no restored row points at them.
 */
data class MediaMergePlan(
    val overlaid: List<String>,
    val kept: List<String>,
    val orphaned: List<String>,
)

/** [scope] comes from the manifest: an Everything archive with no photos still speaks for `photos/`. */
fun planMediaMerge(
    archivePaths: Collection<String>,
    diskPaths: Collection<String>,
    scope: BackupScope,
): MediaMergePlan {
    val fromArchive = archivePaths.toSet()
    val (covered, uncovered) =
        diskPaths.filterNot { it in fromArchive }.partition { path ->
            scope.mediaKinds.any { it.directory == path.substringBefore('/') }
        }
    return MediaMergePlan(overlaid = fromArchive.sorted(), kept = uncovered.sorted(), orphaned = covered.sorted())
}
