package app.kozica.data.routes

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** docs/phase-5.md § 4's search order, instrumented because FTS4 is the phone's SQLite. */
@RunWith(AndroidJUnit4::class)
class PlaceIndexTest {
    private lateinit var file: File
    private lateinit var index: PlaceIndex

    @Before
    fun setUp() {
        file = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "places-test.sqlite")
        file.delete()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            // scripts/build-network.py's write_places.
            db.execSQL(
                "CREATE TABLE places (id INTEGER PRIMARY KEY, kind TEXT NOT NULL, lat REAL NOT NULL, " +
                    "lon REAL NOT NULL, region TEXT NOT NULL, name TEXT NOT NULL)",
            )
            db.execSQL(
                "CREATE VIRTUAL TABLE place_names USING fts4(place, name, folded, notindexed=place, notindexed=name)",
            )
            place(db, 1, "clearing", 49.540, 20.110, "Polana Długa")
            place(db, 2, "clearing", 49.700, 20.110, "Polana Gabryśkowa")
            place(db, 3, "hamlet", 49.545, 20.115, "Wielka Polana")
            place(db, 4, "peak", 49.5431, 20.1114, "Turbacz", "Polana pod Turbaczem")
            place(db, 5, "village", 49.5440, 20.1114, "Koninki")
            place(db, 6, "peak", 49.300, 20.100, "Polanka")
        }
        index = PlaceIndex(file)
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun startingWithBeforeHoldingEachNearestFirstAndOnePerPlace() {
        val found = index.search("polana", 49.5431, 20.1114)

        assertEquals(
            listOf("Turbacz", "Polana Długa", "Polana Gabryśkowa", "Wielka Polana"),
            found.map { it.name },
        )
        assertEquals("Polana pod Turbaczem", found.first().matched)
    }

    @Test
    fun accentsAndCaseDoNotMatter() {
        assertEquals(listOf("Polana Długa"), index.search("POLANA DLU", 49.5, 20.1).map { it.name })
    }

    @Test
    fun aPeakNamesASpotBeforeANearerVillage() {
        assertEquals("Turbacz", index.nearest(49.5439, 20.1114)?.name)
        assertEquals(null, index.nearest(49.0, 19.0))
    }

    private fun place(
        db: SQLiteDatabase,
        id: Int,
        kind: String,
        latitude: Double,
        longitude: Double,
        vararg names: String,
    ) {
        db.execSQL(
            "INSERT INTO places VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(id, kind, latitude, longitude, "gorce_pieniny", names[0]),
        )
        for (name in names) {
            db.execSQL(
                "INSERT INTO place_names (place, name, folded) VALUES (?, ?, ?)",
                arrayOf<Any>(id, name, fold(name)),
            )
        }
    }
}
