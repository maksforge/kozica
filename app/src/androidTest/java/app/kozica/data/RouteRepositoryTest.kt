package app.kozica.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.kozica.data.routes.RoutePoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumented, because Room generates real SQLite code and a JVM test has no SQLite. */
@RunWith(AndroidJUnit4::class)
class RouteRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var routes: RouteRepository

    private val kuznice = RoutePoint(49.2702, 19.9808, "Kuźnice")
    private val zawrat = RoutePoint(49.2191, 20.0164, "Zawrat")
    private val lake = RoutePoint(49.1972, 20.0700, null)

    @Before
    fun open() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        routes = RouteRepository(database.routeDao())
    }

    @After
    fun close() = database.close()

    @Test
    fun aSavedRouteComesBackWithItsPointsInOrder() =
        runTest {
            val id = routes.save(null, "Kuźnice – Morskie Oko", listOf(kuznice, zawrat, lake))

            assertEquals(SavedRoute(id, "Kuźnice – Morskie Oko", listOf(kuznice, zawrat, lake)), routes.get(id))
            assertEquals(listOf(id), routes.observeAll().first().map { it.id })
        }

    @Test
    fun savingAgainReplacesThePointsRatherThanAddingToThem() =
        runTest {
            val id = routes.save(null, "Kuźnice – Morskie Oko", listOf(kuznice, zawrat, lake))

            assertEquals(id, routes.save(id, "Zawrat – Kuźnice", listOf(zawrat, kuznice)))

            assertEquals(SavedRoute(id, "Zawrat – Kuźnice", listOf(zawrat, kuznice)), routes.get(id))
        }

    @Test
    fun writingTheRouteRowOverItselfLeavesItsChildRowsAlone() =
        runTest {
            val id = routes.save(null, "Kuźnice – Morskie Oko", listOf(kuznice, zawrat))
            val dao = database.routeDao()

            dao.upsert(dao.get(id)!!.route.copy(name = "Przez Zawrat"))

            assertEquals(SavedRoute(id, "Przez Zawrat", listOf(kuznice, zawrat)), routes.get(id))
        }

    @Test
    fun theNameIsObservedThroughARenameAndADelete() =
        runTest {
            val id = routes.save(null, "Kuźnice – Morskie Oko", listOf(kuznice, zawrat))
            routes.rename(id, "Przez Zawrat")
            assertEquals("Przez Zawrat", routes.observeName(id).first())

            routes.delete(id)

            assertNull(routes.observeName(id).first())
        }

    @Test
    fun renameKeepsThePointsAndDeleteTakesThemWithIt() =
        runTest {
            val id = routes.save(null, "Kuźnice – Morskie Oko", listOf(kuznice, zawrat))
            routes.rename(id, "Przez Zawrat")
            assertEquals(SavedRoute(id, "Przez Zawrat", listOf(kuznice, zawrat)), routes.get(id))

            routes.delete(id)

            assertNull(routes.get(id))
            database.query("SELECT COUNT(*) FROM route_points", null).use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0))
            }
        }
}
