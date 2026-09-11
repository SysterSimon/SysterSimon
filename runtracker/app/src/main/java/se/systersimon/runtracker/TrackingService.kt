package se.systersimon.runtracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.UUID

class TrackingService : Service() {
    companion object {
        const val ACTION_START = "runtracker.START"
        const val ACTION_PAUSE = "runtracker.PAUSE"
        const val ACTION_RESUME = "runtracker.RESUME"
        const val ACTION_STOP = "runtracker.STOP"
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 7
    }

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var store: LocalStore

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (!TrackerState.running || TrackerState.paused) return
            result.locations.forEach(::acceptLocation)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        store = LocalStore(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        if (TrackerState.running) return
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        TrackerState.reset(id, now)
        store.startRun(id, now)
        startForeground(NOTIFICATION_ID, notification("0.00 km"))
        requestLocations()
    }

    private fun pauseTracking() {
        if (!TrackerState.running || TrackerState.paused) return
        TrackerState.paused = true
        TrackerState.pauseStartedMs = System.currentTimeMillis()
        updateNotification()
    }

    private fun resumeTracking() {
        if (!TrackerState.running || !TrackerState.paused) return
        val now = System.currentTimeMillis()
        TrackerState.pausedTotalMs += now - TrackerState.pauseStartedMs
        TrackerState.pauseStartedMs = 0L
        TrackerState.paused = false
        updateNotification()
    }

    private fun stopTracking() {
        if (!TrackerState.running) {
            stopSelf()
            return
        }
        val snap = TrackerState.snapshot()
        val id = TrackerState.runId
        fused.removeLocationUpdates(callback)
        if (id != null) store.finishRun(id, System.currentTimeMillis(), snap.distanceM, snap.elapsedMs)
        TrackerState.clearAfterStop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        SyncClient.syncAsync(applicationContext)
    }

    private fun requestLocations() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(3_000L)
            .setMinUpdateDistanceMeters(4f)
            .setMaxUpdateDelayMillis(10_000L)
            .build()
        fused.requestLocationUpdates(request, callback, mainLooper)
    }

    private fun acceptLocation(location: Location) {
        if (location.accuracy > 30f) return
        val point = TrackPoint(location.latitude, location.longitude, location.accuracy, location.time)
        val prev = TrackerState.lastPoint()
        if (prev != null) {
            val result = FloatArray(1)
            Location.distanceBetween(prev.lat, prev.lon, point.lat, point.lon, result)
            val delta = result[0].toDouble()
            val seconds = ((point.timeMs - prev.timeMs).coerceAtLeast(1L)) / 1000.0
            val speed = delta / seconds
            if (delta < 2.0 || delta > 120.0 || speed > 12.0) return
            TrackerState.distanceM += delta
        }
        TrackerState.add(point)
        TrackerState.runId?.let { store.addPoint(it, point) }
        updateNotification()
    }

    private fun updateNotification() {
        val km = TrackerState.distanceM / 1000.0
        val text = if (TrackerState.paused) "Pausad · %.2f km".format(km) else "%.2f km".format(km)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, notification(text))
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("RunTracker")
        .setContentText(text)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun createChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Aktivitetsspårning", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

object SyncClient {
    fun syncAsync(context: android.content.Context) {
        if (BuildConfig.SYNC_URL.isBlank()) return
        Thread {
            val store = LocalStore(context)
            store.unsyncedRuns().forEach { id ->
                val payload = store.runAsJson(id) ?: return@forEach
                runCatching {
                    val url = java.net.URL(BuildConfig.SYNC_URL.trimEnd('/') + "/api/runs")
                    val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 10_000
                        readTimeout = 10_000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        if (BuildConfig.SYNC_TOKEN.isNotBlank()) setRequestProperty("Authorization", "Bearer ${BuildConfig.SYNC_TOKEN}")
                    }
                    conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                    val code = conn.responseCode
                    conn.disconnect()
                    if (code in 200..299) store.markSynced(id)
                }
            }
        }.start()
    }
}
