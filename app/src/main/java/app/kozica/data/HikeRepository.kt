package app.kozica.data

import app.kozica.data.hikes.Track
import app.kozica.data.hikes.TrackPoint
import app.kozica.data.hikes.encodeTrack
import app.kozica.data.hikes.metres
import app.kozica.data.hikes.movingSeconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class HikeRepository(
    private val dao: HikeDao,
) {
    /** Starts a Hike with a placeholder name; Stop names it (docs/phase-6.md § 2). */
    suspend fun start(
        id: String,
        at: Instant,
    ) = dao.insert(HikeEntity(id = id, name = "", startedAt = at))

    suspend fun get(id: String): HikeEntity? = dao.get(id)

    fun observeUnfinished(): Flow<HikeEntity?> = dao.observeUnfinished()

    /** The stretch the next fix of [hikeId] starts: one past the last, so a resume never joins the two. */
    suspend fun nextStretch(hikeId: String): Int = dao.lastStretch(hikeId) + 1

    suspend fun addPoint(
        hikeId: String,
        stretch: Int,
        point: TrackPoint,
    ) = dao.insertPoint(
        TrackPointEntity(
            hikeId = hikeId,
            stretch = stretch,
            time = Instant.ofEpochMilli(point.timeMillis),
            latitude = point.latitude,
            longitude = point.longitude,
            altitude = point.altitude,
        ),
    )

    /** The Track of a Hike still recording, as its rows arrive. */
    fun observeTrack(hikeId: String): Flow<Track> = dao.observePoints(hikeId).map { it.toTrack() }

    suspend fun track(hikeId: String): Track = dao.points(hikeId).toTrack()

    /**
     * Ends [hikeId] at its last point, or at [now] with none, under the name [name] gives it from
     * its first point; does nothing to a Hike that has already ended.
     */
    suspend fun finish(
        hikeId: String,
        now: Instant,
        name: suspend (hike: HikeEntity, first: TrackPoint?) -> String,
    ) = dao.finish(hikeId) { hike, rows ->
        val track = rows.toTrack()
        val points = track.flatten()
        HikeEnding(
            name = name(hike, points.firstOrNull()),
            endedAt = points.lastOrNull()?.let { Instant.ofEpochMilli(it.timeMillis) } ?: now,
            track = encodeTrack(track),
            metres = track.metres(),
            movingSeconds = track.movingSeconds(),
        )
    }

    private fun List<TrackPointEntity>.toTrack(): Track =
        groupBy { it.stretch }
            .toSortedMap()
            .values
            .map { stretch ->
                stretch.map { TrackPoint(it.time.toEpochMilli(), it.latitude, it.longitude, it.altitude) }
            }
}
