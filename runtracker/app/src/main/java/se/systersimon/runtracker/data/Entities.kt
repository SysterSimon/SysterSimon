package se.systersimon.runtracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "runs")
data class RunEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val distanceM: Double = 0.0,
    val durationMs: Long = 0L,
    val status: String = "ACTIVE",
    val synced: Boolean = false,
)

@Entity(
    tableName = "track_points",
    indices = [Index("runId"), Index("synced")],
)
data class TrackPointEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val recordedAt: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float,
    val altitudeM: Double?,
    val speedMps: Float?,
    val synced: Boolean = false,
)
