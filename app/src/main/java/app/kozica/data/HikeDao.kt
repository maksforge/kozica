package app.kozica.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface HikeDao {
    @Insert
    suspend fun insert(hike: HikeEntity)

    @Query("SELECT * FROM hikes WHERE id = :id")
    suspend fun get(id: String): HikeEntity?

    @Query("SELECT * FROM hikes WHERE id = :id")
    fun observe(id: String): Flow<HikeEntity?>

    @Query(
        "SELECT id, name, startedAt, endedAt, metres, movingSeconds, ascentMetres, ascentFromGps FROM hikes " +
            "WHERE endedAt IS NOT NULL ORDER BY startedAt DESC",
    )
    fun observeEnded(): Flow<List<HikeSummary>>

    /** Ended Hikes whose height grid has not been read, at Stop or by a build before it was. */
    @Query("SELECT id FROM hikes WHERE endedAt IS NOT NULL AND ascentMetres IS NULL")
    suspend fun withoutClimb(): List<String>

    @Query("UPDATE hikes SET name = :name WHERE id = :id")
    suspend fun rename(
        id: String,
        name: String,
    )

    @Query(
        "UPDATE hikes SET ascentMetres = :ascent, descentMetres = :descent, ascentFromGps = :fromGps " +
            "WHERE id = :id AND ascentMetres IS NULL",
    )
    suspend fun setClimb(
        id: String,
        ascent: Double,
        descent: Double,
        fromGps: Boolean,
    )

    @Query(
        "UPDATE hikes SET startedAt = :startedAt, endedAt = :endedAt, track = :track, metres = :metres, " +
            "movingSeconds = :movingSeconds, ascentMetres = :ascent, descentMetres = :descent, " +
            "ascentFromGps = :fromGps WHERE id = :id",
    )
    suspend fun trimmed(
        id: String,
        startedAt: Instant,
        endedAt: Instant,
        track: ByteArray,
        metres: Double,
        movingSeconds: Long,
        ascent: Double?,
        descent: Double?,
        fromGps: Boolean,
    )

    /** A photo taken outside the kept span keeps its place in the Hike but loses its pin (docs/phase-6.md § 4). */
    @Query(
        "UPDATE hike_photos SET latitude = NULL, longitude = NULL " +
            "WHERE hikeId = :hikeId AND (takenAt < :from OR takenAt > :to)",
    )
    suspend fun unpinOutside(
        hikeId: String,
        from: Instant,
        to: Instant,
    )

    @Transaction
    suspend fun trim(
        id: String,
        trim: HikeTrim,
    ) {
        trimmed(
            id,
            trim.startedAt,
            trim.endedAt,
            trim.track,
            trim.metres,
            trim.movingSeconds,
            trim.ascent,
            trim.descent,
            trim.fromGps,
        )
        unpinOutside(id, trim.startedAt, trim.endedAt)
    }

    @Query("UPDATE hikes SET note = :note WHERE id = :id")
    suspend fun setNote(
        id: String,
        note: String?,
    )

    /** In the order they were taken, the undated after them in the order they were added. */
    @Query("SELECT * FROM hike_photos WHERE hikeId = :hikeId ORDER BY takenAt IS NULL, takenAt, addedAt")
    fun observePhotos(hikeId: String): Flow<List<HikePhotoEntity>>

    @Insert
    suspend fun insertPhoto(photo: HikePhotoEntity)

    @Query("SELECT COUNT(*) FROM hike_photos WHERE addedAt > :since")
    fun observePhotosAddedAfter(since: Instant): Flow<Int>

    @Query("SELECT * FROM hike_photos WHERE hikeId = :hikeId")
    suspend fun photos(hikeId: String): List<HikePhotoEntity>

    @Query("SELECT * FROM hike_photos WHERE id = :id")
    suspend fun photo(id: String): HikePhotoEntity?

    @Query("UPDATE hike_photos SET caption = :caption WHERE id = :id")
    suspend fun setCaption(
        id: String,
        caption: String?,
    )

    @Query("UPDATE hike_photos SET latitude = NULL, longitude = NULL WHERE id = :id")
    suspend fun unpin(id: String)

    @Query("DELETE FROM hike_photos WHERE id = :id")
    suspend fun deletePhoto(id: String)

    /** The Hike with no end, if a recording was lost with its process (docs/phase-6.md § 2). */
    @Query("SELECT * FROM hikes WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeUnfinished(): Flow<HikeEntity?>

    @Insert
    suspend fun insertPoint(point: TrackPointEntity)

    @Query("SELECT * FROM track_points WHERE hikeId = :hikeId ORDER BY time")
    suspend fun points(hikeId: String): List<TrackPointEntity>

    @Query("SELECT * FROM track_points WHERE hikeId = :hikeId ORDER BY time")
    fun observePoints(hikeId: String): Flow<List<TrackPointEntity>>

    @Query("SELECT COALESCE(MAX(stretch), -1) FROM track_points WHERE hikeId = :hikeId")
    suspend fun lastStretch(hikeId: String): Int

    @Query(
        "UPDATE hikes SET name = :name, endedAt = :endedAt, track = :track, metres = :metres, " +
            "movingSeconds = :movingSeconds WHERE id = :id",
    )
    suspend fun end(
        id: String,
        name: String,
        endedAt: Instant,
        track: ByteArray,
        metres: Double,
        movingSeconds: Long,
    )

    @Query("DELETE FROM track_points WHERE hikeId = :hikeId")
    suspend fun deletePoints(hikeId: String)

    @Query("DELETE FROM hikes WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * Deletes the Hike, its photos' rows with it, and returns those rows for their files, in one
     * transaction: a photo written meanwhile then fails its foreign key rather than losing its row
     * to the cascade with its files left behind.
     */
    @Transaction
    suspend fun deleteWithPhotos(id: String): List<HikePhotoEntity> = photos(id).also { delete(id) }

    /**
     * Reads, encodes and deletes the rows of a Hike still open in one transaction, so no point is
     * ever in neither and an ended Hike's Track is never encoded again from what is left. A Hike that
     * stored no point is deleted instead (docs/phase-6.md § 2).
     */
    @Transaction
    suspend fun finish(
        id: String,
        ending: suspend (hike: HikeEntity, points: List<TrackPointEntity>) -> HikeEnding,
    ) {
        val hike = get(id)?.takeIf { it.endedAt == null } ?: return
        val points = points(id)
        if (points.isEmpty()) return delete(id)
        val end = ending(hike, points)
        end(id, end.name, end.endedAt, end.track, end.metres, end.movingSeconds)
        deletePoints(id)
    }
}

/** A Hike as the Hikes tab lists it, without its Track. */
data class HikeSummary(
    val id: String,
    val name: String,
    val startedAt: Instant,
    val endedAt: Instant?,
    val metres: Double,
    val movingSeconds: Long,
    val ascentMetres: Double?,
    val ascentFromGps: Boolean,
)

class HikeTrim(
    val startedAt: Instant,
    val endedAt: Instant,
    val track: ByteArray,
    val metres: Double,
    val movingSeconds: Long,
    val ascent: Double?,
    val descent: Double?,
    val fromGps: Boolean,
)

class HikeEnding(
    val name: String,
    val endedAt: Instant,
    val track: ByteArray,
    val metres: Double,
    val movingSeconds: Long,
)
