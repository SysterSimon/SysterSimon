package se.systersimon.runtracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface RunDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRun(run: RunEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPoint(point: TrackPointEntity)

    @Update
    suspend fun updateRun(run: RunEntity)

    @Query("SELECT * FROM runs WHERE id = :runId LIMIT 1")
    suspend fun getRun(runId: String): RunEntity?

    @Query("SELECT * FROM runs WHERE status = 'ACTIVE' ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveRun(): RunEntity?

    @Query("SELECT * FROM track_points WHERE runId = :runId ORDER BY recordedAt ASC")
    suspend fun getPoints(runId: String): List<TrackPointEntity>

    @Query("SELECT * FROM runs WHERE status = 'FINISHED' ORDER BY startedAt DESC LIMIT :limit")
    suspend fun recentFinishedRuns(limit: Int = 100): List<RunEntity>

    @Query("SELECT COALESCE(SUM(distanceM), 0) FROM runs WHERE status = 'FINISHED'")
    suspend fun totalFinishedDistance(): Double

    @Query("SELECT COALESCE(SUM(distanceM), 0) FROM runs WHERE status = 'FINISHED' AND startedAt >= :from AND startedAt < :to")
    suspend fun distanceBetween(from: Long, to: Long): Double

    @Query("SELECT * FROM runs WHERE synced = 0 ORDER BY startedAt ASC LIMIT :limit")
    suspend fun unsyncedRuns(limit: Int = 20): List<RunEntity>

    @Query("SELECT * FROM track_points WHERE runId = :runId AND synced = 0 ORDER BY recordedAt ASC LIMIT :limit")
    suspend fun unsyncedPoints(runId: String, limit: Int = 1000): List<TrackPointEntity>

    @Query("UPDATE runs SET synced = 1 WHERE id = :runId")
    suspend fun markRunSynced(runId: String)

    @Query("UPDATE track_points SET synced = 1 WHERE id IN (:ids)")
    suspend fun markPointsSynced(ids: List<String>)
}
