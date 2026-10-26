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

    /** The Track is encoded before its rows go, in one transaction, so no point is ever in neither. */
    @Transaction
    suspend fun finish(
        id: String,
        name: String,
        endedAt: Instant,
        track: ByteArray,
        metres: Double,
        movingSeconds: Long,
    ) {
        end(id, name, endedAt, track, metres, movingSeconds)
        deletePoints(id)
    }
}
