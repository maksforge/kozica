package app.kozica.data

import app.kozica.data.hikes.Height
import app.kozica.data.hikes.Track
import app.kozica.data.hikes.TrackPoint
import app.kozica.data.hikes.climb
import app.kozica.data.hikes.decodeTrack
import app.kozica.data.hikes.encodeTrack
import app.kozica.data.hikes.metres
import app.kozica.data.hikes.movingSeconds
import app.kozica.data.hikes.trim
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * @param heights each point's height by docs/phase-6.md § 5's rule, one per point of the Track in order.
 * @param deleteMedia deletes a file by its relative path, as `MediaFiles.delete` does.
 */
class HikeRepository(
    private val dao: HikeDao,
    private val heights: suspend (Track) -> List<Height?> = { track -> track.flatten().map { null } },
    private val deleteMedia: (relativePath: String) -> Unit = {},
) {
    /** Starts a Hike with a placeholder name; Stop names it (docs/phase-6.md § 2). */
    suspend fun start(
        id: String,
        at: Instant,
    ) = dao.insert(HikeEntity(id = id, name = "", startedAt = at))

    suspend fun get(id: String): HikeEntity? = dao.get(id)

    fun observeUnfinished(): Flow<HikeEntity?> = dao.observeUnfinished()

    /** Newest first. */
    fun observeEnded(): Flow<List<HikeSummary>> = dao.observeEnded()

    fun observe(id: String): Flow<HikeEntity?> = dao.observe(id)

    fun observePhotoCount(id: String): Flow<Int> = dao.observePhotoCount(id)

    suspend fun rename(
        id: String,
        name: String,
    ) = dao.rename(id, name)

    /** Reads the height grid for every ended Hike that has not had it read, the one just stopped included. */
    suspend fun fillClimbs() {
        for (id in dao.withoutClimb()) {
            val track = dao.get(id)?.track?.let(::decodeTrack) ?: continue
            val climb = climb(heights(track))
            dao.setClimb(id, climb.ascent, climb.descent, climb.fromGps)
        }
    }

    /**
     * Keeps the points [first] to [last] of the Track in order and deletes the rest for good; the
     * totals are computed again, and the start or end moves to the first or last point kept where
     * that end was cut (docs/phase-6.md § 4).
     */
    suspend fun trim(
        id: String,
        first: Int,
        last: Int,
    ) {
        val hike = dao.get(id) ?: return
        val endedAt = hike.endedAt ?: return
        val whole = hike.track?.let(::decodeTrack) ?: return
        val count = whole.sumOf { it.size }
        val track = whole.trim(first, last)
        val points = track.flatten()
        if (points.isEmpty()) return
        val climb = climb(heights(track))
        dao.trim(
            id,
            HikeTrim(
                startedAt = if (first > 0) Instant.ofEpochMilli(points.first().timeMillis) else hike.startedAt,
                endedAt = if (last < count - 1) Instant.ofEpochMilli(points.last().timeMillis) else endedAt,
                track = encodeTrack(track),
                metres = track.metres(),
                movingSeconds = track.movingSeconds(),
                ascent = climb.ascent,
                descent = climb.descent,
                fromGps = climb.fromGps,
            ),
        )
    }

    /** Deletes the Hike and its photos' rows, then their files (docs/phase-6.md § 4). */
    suspend fun delete(id: String) {
        val photos = dao.photos(id)
        dao.delete(id)
        for (photo in photos) {
            deleteMedia(photo.photoPath)
            deleteMedia(photo.thumbnailPath)
        }
    }

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
