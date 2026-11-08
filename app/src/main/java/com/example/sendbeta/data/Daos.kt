package com.example.sendbeta.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GymDao {
    @Query("SELECT * FROM gyms ORDER BY id")
    suspend fun getAll(): List<Gym>
}

@Dao
interface RouteDao {
    @Query("SELECT * FROM routes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Route>>

    @Query("SELECT * FROM routes ORDER BY name")
    suspend fun getAll(): List<Route>

    @Query("SELECT * FROM routes WHERE id = :id")
    fun observe(id: Long): Flow<Route?>

    @Insert
    suspend fun insert(route: Route): Long

    @Update
    suspend fun update(route: Route)

    @Delete
    suspend fun delete(route: Route)

    /** Routes with no "sent" attempt yet = active projects. */
    @Query("SELECT COUNT(*) FROM routes WHERE id NOT IN (SELECT routeId FROM attempts WHERE sent = 1)")
    fun observeActiveProjectCount(): Flow<Int>
}

@Dao
interface AttemptDao {
    @Query("SELECT * FROM attempts WHERE routeId = :routeId ORDER BY createdAt DESC")
    fun observeForRoute(routeId: Long): Flow<List<Attempt>>

    @Query("SELECT * FROM attempts WHERE routeId = :routeId")
    suspend fun getForRoute(routeId: Long): List<Attempt>

    @Query(
        """SELECT a.id, a.routeId, a.sent, a.note, a.durationMs, a.videoPath, a.source, a.createdAt,
                  a.startHold, a.cruxHold, a.topHold,
                  r.name AS routeName, r.gymLabel, r.grade, r.color
           FROM attempts a INNER JOIN routes r ON r.id = a.routeId
           ORDER BY a.createdAt DESC LIMIT :limit"""
    )
    fun observeRecent(limit: Int): Flow<List<AttemptWithRoute>>

    @Query(
        """SELECT a.id, a.routeId, a.sent, a.note, a.durationMs, a.videoPath, a.source, a.createdAt,
                  a.startHold, a.cruxHold, a.topHold,
                  r.name AS routeName, r.gymLabel, r.grade, r.color
           FROM attempts a INNER JOIN routes r ON r.id = a.routeId
           WHERE a.id = :id"""
    )
    suspend fun getWithRoute(id: Long): AttemptWithRoute?

    @Query("SELECT COUNT(*) FROM attempts WHERE sent = 1")
    fun observeSendCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM attempts WHERE sent = 1 AND createdAt >= :since")
    fun observeSendsSince(since: Long): Flow<Int>

    @Query("SELECT DISTINCT r.grade FROM attempts a INNER JOIN routes r ON r.id = a.routeId WHERE a.sent = 1")
    fun observeSentGrades(): Flow<List<String>>

    @Insert
    suspend fun insert(attempt: Attempt): Long

    @Query("UPDATE attempts SET startHold = :start, cruxHold = :crux, topHold = :top WHERE id = :id")
    suspend fun updateHolds(id: Long, start: String?, crux: String?, top: String?)

    @Delete
    suspend fun delete(attempt: Attempt)
}
