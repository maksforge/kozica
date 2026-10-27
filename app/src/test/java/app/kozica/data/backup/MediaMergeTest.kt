package app.kozica.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test

/** Restore merges media and deletes none (ADR-0005). */
class MediaMergeTest {
    private val thumbnail = "thumbnails/3f2504e0-4f89-41d3-9a0c-0305e82c3301.jpg"
    private val otherThumbnail = "thumbnails/3f2504e0-4f89-41d3-9a0c-0305e82c3302.jpg"
    private val photo = "photos/3f2504e0-4f89-41d3-9a0c-0305e82c3303.jpg"
    private val otherPhoto = "photos/3f2504e0-4f89-41d3-9a0c-0305e82c3304.jpg"

    @Test
    fun `a Records restore keeps the photos it never looked at`() {
        val plan = planMediaMerge(listOf(thumbnail), listOf(thumbnail, photo, otherPhoto), BackupScope.Records)

        assertEquals(listOf(thumbnail), plan.overlaid)
        assertEquals(listOf(photo, otherPhoto), plan.kept)
        assertEquals(emptyList<String>(), plan.orphaned)
    }

    @Test
    fun `an Everything restore orphans the photos its archive did not carry`() {
        val plan = planMediaMerge(listOf(thumbnail), listOf(thumbnail, photo, otherPhoto), BackupScope.Everything)

        assertEquals(emptyList<String>(), plan.kept)
        assertEquals(listOf(photo, otherPhoto), plan.orphaned)
    }

    @Test
    fun `a file on both sides is only overlaid`() {
        val plan = planMediaMerge(listOf(thumbnail, photo), listOf(thumbnail, photo), BackupScope.Everything)

        assertEquals(listOf(photo, thumbnail), plan.overlaid)
        assertEquals(emptyList<String>(), plan.kept)
        assertEquals(emptyList<String>(), plan.orphaned)
    }

    @Test
    fun `an empty phone takes everything the archive has`() {
        val plan = planMediaMerge(listOf(otherThumbnail, thumbnail), emptyList(), BackupScope.Records)

        assertEquals(listOf(thumbnail, otherThumbnail), plan.overlaid)
        assertEquals(emptyList<String>(), plan.kept + plan.orphaned)
    }
}
