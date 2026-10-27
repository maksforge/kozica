package app.kozica.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kozica.data.hikes.Height
import app.kozica.data.hikes.HeightSource
import app.kozica.data.hikes.TrackPoint
import app.kozica.data.hikes.decodeTrack
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** Instrumented, because Room generates real SQLite code and a JVM test has no SQLite. */
@RunWith(AndroidJUnit4::class)
class HikeRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var hikes: HikeRepository
    private val deleted = mutableListOf<String>()

    private val start = Instant.parse("2026-09-12T06:30:00Z")

    @Before
    fun open() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        // A metre of climb for every point, from the grid, so every total is easy to predict.
        hikes =
            HikeRepository(
                database.hikeDao(),
                heights = { track ->
                    track.flatten().mapIndexed { i, _ -> Height(1_000.0 + i * 10, HeightSource.GRID) }
                },
                deleteMedia = { deleted += it },
            )
    }

    @After
    fun close() = database.close()

    /** Ten fixes a minute apart, a pause, ten more; Record a minute before the first fix. */
    private suspend fun recorded(): String {
        val id = "hike"
        hikes.start(id, start)
        for (stretch in 0..1) {
            for (i in 0 until 10) {
                val minute = 1L + stretch * 30 + i
                hikes.addPoint(
                    id,
                    stretch,
                    TrackPoint(
                        start.toEpochMilli() + minute * 60_000,
                        49.2 + (stretch * 10 + i) * 0.001,
                        20.0,
                        null,
                    ),
                )
            }
        }
        hikes.finish(id, start.plusSeconds(3_600)) { _, _ -> "Kasprowy" }
        return id
    }

    private fun minute(m: Long): Instant = start.plusSeconds(m * 60)

    @Test
    fun fillingClimbsReadsEveryEndedHikeWithoutOne() =
        runTest {
            val id = recorded()

            hikes.fillClimbs()

            val hike = hikes.get(id)!!
            assertEquals(190.0, hike.ascentMetres!!, 1e-9)
            assertFalse(hike.ascentFromGps)
            assertEquals(listOf(id), hikes.observeEnded().first().map { it.id })
        }

    @Test
    fun aTrimKeepsItsSpanRecomputesTheTotalsAndUnpinsThePhotosOutsideIt() =
        runTest {
            val id = recorded()
            val dao = database.hikeDao()
            val before = hikes.get(id)!!
            val inside = photo(id, "in", minute(5))
            val outside = photo(id, "out", minute(2))
            database.hikeDao().insertPhoto(inside)
            database.hikeDao().insertPhoto(outside)

            hikes.trim(id, first = 3, last = 19)

            val after = hikes.get(id)!!
            val track = decodeTrack(after.track!!)
            assertEquals(listOf(7, 10), track.map { it.size })
            assertEquals(minute(4), after.startedAt)
            assertEquals(before.endedAt, after.endedAt)
            assertTrue(after.metres < before.metres)
            assertEquals(160.0, after.ascentMetres!!, 1e-9)
            val photos = dao.photos(id).associateBy { it.id }
            assertEquals(inside.latitude, photos.getValue("in").latitude)
            assertNull(photos.getValue("out").latitude)
            assertNull(photos.getValue("out").longitude)
        }

    @Test
    fun deletingAHikeDeletesItsPhotosRowsAndFiles() =
        runTest {
            val id = recorded()
            database.hikeDao().insertPhoto(photo(id, "p", minute(5)))

            hikes.delete(id)

            assertNull(hikes.get(id))
            assertEquals(emptyList<HikePhotoEntity>(), database.hikeDao().photos(id))
            assertEquals(listOf("photos/p.jpg", "thumbnails/p.jpg"), deleted)
        }

    private fun photo(
        hikeId: String,
        id: String,
        takenAt: Instant,
    ) = HikePhotoEntity(
        id = id,
        hikeId = hikeId,
        photoPath = "photos/$id.jpg",
        thumbnailPath = "thumbnails/$id.jpg",
        addedAt = takenAt,
        takenAt = takenAt,
        latitude = 49.2,
        longitude = 20.0,
        caption = null,
    )
}
