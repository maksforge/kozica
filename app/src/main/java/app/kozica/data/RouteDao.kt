package app.kozica.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface RouteDao {
    @Transaction
    @Query("SELECT * FROM routes")
    fun observeAll(): Flow<List<RouteWithPoints>>

    @Transaction
    @Query("SELECT * FROM routes WHERE id = :id")
    suspend fun get(id: String): RouteWithPoints?

    /** Replaces the Route and all its points, so a point removed in the sheet does not linger. */
    @Transaction
    suspend fun save(
        route: RouteEntity,
        points: List<RoutePointEntity>,
    ) {
        upsert(route)
        deletePoints(route.id)
        insertPoints(points)
    }

    @Query("UPDATE routes SET name = :name, updatedAt = :at WHERE id = :id")
    suspend fun rename(
        id: String,
        name: String,
        at: Instant,
    )

    @Query("SELECT name FROM routes WHERE id = :id")
    fun observeName(id: String): Flow<String?>

    @Query("DELETE FROM routes WHERE id = :id")
    suspend fun delete(id: String)

    /** An UPDATE over an existing Route, not REPLACE's delete-then-insert, which would cascade to every child row. */
    @Upsert
    suspend fun upsert(route: RouteEntity)

    @Query("DELETE FROM route_points WHERE routeId = :id")
    suspend fun deletePoints(id: String)

    @Insert
    suspend fun insertPoints(points: List<RoutePointEntity>)
}
