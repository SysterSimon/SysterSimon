package se.systersimon.runtracker

data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val accuracy: Float,
    val timeMs: Long
)

data class TrackingSnapshot(
    val running: Boolean,
    val paused: Boolean,
    val distanceM: Double,
    val elapsedMs: Long,
    val route: List<TrackPoint>
)

object TrackerState {
    @Volatile var running = false
    @Volatile var paused = false
    @Volatile var distanceM = 0.0
    @Volatile var startedAtMs = 0L
    @Volatile var pausedTotalMs = 0L
    @Volatile var pauseStartedMs = 0L
    @Volatile var runId: String? = null
    private val route = mutableListOf<TrackPoint>()

    @Synchronized fun reset(id: String, now: Long) {
        running = true
        paused = false
        distanceM = 0.0
        startedAtMs = now
        pausedTotalMs = 0L
        pauseStartedMs = 0L
        runId = id
        route.clear()
    }

    @Synchronized fun add(point: TrackPoint) { route.add(point) }
    @Synchronized fun lastPoint(): TrackPoint? = route.lastOrNull()

    @Synchronized fun snapshot(now: Long = System.currentTimeMillis()): TrackingSnapshot {
        val pauseNow = if (paused && pauseStartedMs > 0L) now - pauseStartedMs else 0L
        val elapsed = if (running) (now - startedAtMs - pausedTotalMs - pauseNow).coerceAtLeast(0L) else 0L
        return TrackingSnapshot(running, paused, distanceM, elapsed, route.toList())
    }

    @Synchronized fun clearAfterStop() {
        running = false
        paused = false
        runId = null
    }
}
