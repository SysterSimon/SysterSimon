package se.systersimon.runtracker.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TrackingMode { IDLE, RUNNING, PAUSED }

data class TrackUiPoint(val lat: Double, val lon: Double)

data class TrackingSnapshot(
    val mode: TrackingMode = TrackingMode.IDLE,
    val runId: String? = null,
    val distanceM: Double = 0.0,
    val elapsedMs: Long = 0L,
    val points: List<TrackUiPoint> = emptyList(),
)

object TrackingBus {
    private val mutable = MutableStateFlow(TrackingSnapshot())
    val snapshot = mutable.asStateFlow()

    fun publish(value: TrackingSnapshot) { mutable.value = value }
    fun current(): TrackingSnapshot = mutable.value
}
