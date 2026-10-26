package app.kozica.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migrations against the exported schemas, reading **rows** back: `runMigrationsAndValidate` compares
 * shapes only. This walks past the launch gate; `SchemaGateTest` is the other half.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
        )

    @Test
    fun theExportedSchemaIsReadableOnTheDevice() {
        // If this fails, it is almost always the assets wiring in app/build.gradle.kts:
        // `variant.androidTest?.sources?.assets?.addStaticSourceDirectory("$projectDir/schemas")`.
        // Room's exported JSON has to be *inside the test APK*, not merely committed to the repo.
        helper.createDatabase(TEST_DB, 1).close()
    }

    @Test
    fun migrate1To2KeepsASavedRouteAndItsPointsInOrder() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL("INSERT INTO items (id, title, createdAt) VALUES ('a', 'dormant', 1)")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)
        db.execSQL("INSERT INTO routes (id, name, createdAt, updatedAt) VALUES ('r', 'Kuźnice – Morskie Oko', 1, 1)")
        db.execSQL("INSERT INTO route_points VALUES ('r', 0, 49.2702, 19.9808, 'Kuźnice')")
        db.execSQL("INSERT INTO route_points VALUES ('r', 1, 49.2191, 20.0164, 'Zawrat')")
        db.execSQL("INSERT INTO route_points VALUES ('r', 2, 49.1972, 20.0700, NULL)")

        db.query("SELECT label FROM route_points WHERE routeId = 'r' ORDER BY position").use {
            val read = mutableListOf<String?>()
            while (it.moveToNext()) read += if (it.isNull(0)) null else it.getString(0)
            assertEquals(listOf("Kuźnice", "Zawrat", null), read)
        }
        db.query("SELECT name FROM routes WHERE id = 'r'").use {
            assertTrue(it.moveToFirst())
            assertEquals("Kuźnice – Morskie Oko", it.getString(0))
        }
        db.query("SELECT name FROM sqlite_master WHERE name = 'items'").use { assertFalse(it.moveToFirst()) }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
