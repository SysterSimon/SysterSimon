package se.systersimon.runtracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private val red = Color.rgb(244, 45, 61)
    private val bg = Color.rgb(9, 10, 13)
    private val panel = Color.rgb(20, 22, 27)
    private val muted = Color.rgb(143, 149, 160)
    private val white = Color.rgb(245, 247, 250)

    private lateinit var mapView: MapView
    private lateinit var distanceText: TextView
    private lateinit var timeText: TextView
    private lateinit var paceText: TextView
    private lateinit var startButton: Button
    private lateinit var pauseButton: Button
    private lateinit var weekText: TextView
    private lateinit var monthText: TextView
    private lateinit var goalProgress: ProgressBar
    private lateinit var goalLabel: TextView
    private lateinit var recentBox: LinearLayout
    private lateinit var store: LocalStore
    private val handler = Handler(Looper.getMainLooper())
    private var mapReady = false
    private var lastRouteSize = -1

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) startRun()
    }

    private val tick = object : Runnable {
        override fun run() {
            refreshLive()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        store = LocalStore(this)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        setContentView(buildUi(savedInstanceState))
        refreshStats()
        handler.post(tick)
        SyncClient.syncAsync(applicationContext)
    }

    private fun buildUi(savedInstanceState: Bundle?): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(16), dp(10), dp(16), dp(14))
        }

        root.addView(TextView(this).apply {
            text = "RUNTRACKER"
            setTextColor(red)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.18f
        })
        root.addView(TextView(this).apply {
            text = "Ut och spring. Resten sköter sig själv."
            setTextColor(muted)
            textSize = 13f
            setPadding(0, dp(3), 0, dp(12))
        })

        val live = card().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        distanceText = TextView(this).apply {
            text = "0.00 km"
            setTextColor(white)
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        live.addView(distanceText)

        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        timeText = metric("00:00:00", "TID")
        paceText = metric("--:-- /km", "TEMPO")
        metrics.addView(timeText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        metrics.addView(paceText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        live.addView(metrics)
        root.addView(live, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        mapView = MapView(this).also { it.onCreate(savedInstanceState) }
        val mapFrame = FrameLayout(this).apply {
            background = rounded(panel, 18f)
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            addView(mapView, FrameLayout.LayoutParams(-1, -1))
            addView(TextView(this@MainActivity).apply {
                text = "OPENFREEMAP"
                setTextColor(Color.argb(190, 255, 255, 255))
                textSize = 9f
                setPadding(dp(8), dp(5), dp(8), dp(5))
                background = rounded(Color.argb(185, 9, 10, 13), 10f)
            }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                topMargin = dp(8); marginEnd = dp(8)
            })
        }
        root.addView(mapFrame, LinearLayout.LayoutParams(-1, dp(285)).apply { bottomMargin = dp(10) })
        configureMap()

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        startButton = actionButton("STARTA", red).apply { setOnClickListener { ensurePermissionAndStart() } }
        pauseButton = actionButton("PAUS", Color.rgb(44, 47, 55)).apply {
            visibility = View.GONE
            setOnClickListener { togglePause() }
        }
        val stopButton = actionButton("STOPP", Color.rgb(44, 47, 55)).apply {
            setOnClickListener { sendAction(TrackingService.ACTION_STOP); refreshStatsDelayed() }
        }
        controls.addView(startButton, LinearLayout.LayoutParams(0, dp(54), 1.35f).apply { marginEnd = dp(8) })
        controls.addView(pauseButton, LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(8) })
        controls.addView(stopButton, LinearLayout.LayoutParams(0, dp(54), 1f))
        root.addView(controls, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        val stats = card().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val statRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        weekText = metric("0.0 km", "DENNA VECKA")
        monthText = metric("0.0 km", "DENNA MÅNAD")
        statRow.addView(weekText, LinearLayout.LayoutParams(0, -2, 1f))
        statRow.addView(monthText, LinearLayout.LayoutParams(0, -2, 1f))
        stats.addView(statRow)

        goalLabel = TextView(this).apply {
            setTextColor(white)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(12), 0, dp(7))
            setOnClickListener { editGoal() }
        }
        stats.addView(goalLabel)
        goalProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(red)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(48, 51, 59))
        }
        stats.addView(goalProgress, LinearLayout.LayoutParams(-1, dp(5)))

        recentBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        stats.addView(recentBox)
        root.addView(stats, LinearLayout.LayoutParams(-1, 0, 1f))

        return root
    }

    private fun configureMap() {
        mapView.getMapAsync { map ->
            map.uiSettings.apply {
                isCompassEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = true
            }
            map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/dark")) { style ->
                style.addSource(GeoJsonSource("route-source"))
                style.addLayer(
                    LineLayer("route-layer", "route-source").withProperties(
                        PropertyFactory.lineColor(red),
                        PropertyFactory.lineWidth(5.5f),
                        PropertyFactory.lineOpacity(0.95f)
                    )
                )
                mapReady = true
                lastRouteSize = -1
                updateRouteOnMap(TrackerState.snapshot().route)
            }
        }
    }

    private fun refreshLive() {
        val s = TrackerState.snapshot()
        distanceText.text = "%.2f km".format(Locale.US, s.distanceM / 1000.0)
        timeText.text = formatDuration(s.elapsedMs)
        paceText.text = if (s.distanceM >= 50) formatPace(s.elapsedMs, s.distanceM) else "--:-- /km"
        startButton.visibility = if (s.running) View.GONE else View.VISIBLE
        pauseButton.visibility = if (s.running) View.VISIBLE else View.GONE
        pauseButton.text = if (s.paused) "FORTSÄTT" else "PAUS"
        if (s.route.size != lastRouteSize) updateRouteOnMap(s.route)
    }

    private fun updateRouteOnMap(route: List<TrackPoint>) {
        if (!mapReady || route.isEmpty()) return
        lastRouteSize = route.size
        mapView.getMapAsync { map ->
            map.style?.getSourceAs<GeoJsonSource>("route-source")?.let { source ->
                if (route.size >= 2) {
                    source.setGeoJson(LineString.fromLngLats(route.map { Point.fromLngLat(it.lon, it.lat) }))
                }
            }
            val last = route.last()
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(last.lat, last.lon), 15.5), 650)
        }
    }

    private fun ensurePermissionAndStart() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine) startRun() else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    private fun startRun() = sendAction(TrackingService.ACTION_START)

    private fun togglePause() {
        sendAction(if (TrackerState.paused) TrackingService.ACTION_RESUME else TrackingService.ACTION_PAUSE)
    }

    private fun sendAction(action: String) {
        ContextCompat.startForegroundService(this, Intent(this, TrackingService::class.java).setAction(action))
    }

    private fun refreshStatsDelayed() {
        handler.postDelayed({ refreshStats() }, 750)
    }

    private fun refreshStats() {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val weekM = store.totalSince(cal.timeInMillis)

        val month = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val monthM = store.totalSince(month.timeInMillis)
        weekText.text = "%.1f km".format(Locale.US, weekM / 1000.0)
        monthText.text = "%.1f km".format(Locale.US, monthM / 1000.0)

        val goalKm = getPreferences(MODE_PRIVATE).getFloat("weekly_goal_km", 20f)
        goalLabel.text = "VECKOMÅL  ·  %.1f / %.0f km   ›".format(Locale.US, weekM / 1000.0, goalKm)
        goalProgress.progress = ((weekM / 1000.0 / goalKm) * 1000).toInt().coerceIn(0, 1000)

        recentBox.removeAllViews()
        val recent = store.recentRuns(3)
        if (recent.isNotEmpty()) {
            recentBox.addView(TextView(this).apply {
                text = "SENASTE PASS"
                setTextColor(muted)
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
            })
            val dateFmt = SimpleDateFormat("d MMM", Locale("sv", "SE"))
            recent.forEach { (m, ms, started) ->
                recentBox.addView(TextView(this).apply {
                    text = "${dateFmt.format(Date(started))}     ${"%.2f".format(Locale.US, m / 1000.0)} km     ${formatDuration(ms)}"
                    setTextColor(white)
                    textSize = 13f
                    setPadding(0, dp(5), 0, 0)
                })
            }
        }
    }

    private fun editGoal() {
        val current = getPreferences(MODE_PRIVATE).getFloat("weekly_goal_km", 20f)
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(current.toString())
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle("Veckomål i kilometer")
            .setView(input)
            .setPositiveButton("Spara") { _, _ ->
                input.text.toString().replace(',', '.').toFloatOrNull()?.takeIf { it > 0 }?.let {
                    getPreferences(MODE_PRIVATE).edit().putFloat("weekly_goal_km", it).apply()
                    refreshStats()
                }
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    private fun metric(value: String, label: String): TextView = TextView(this).apply {
        text = value
        setTextColor(white)
        textSize = 20f
        typeface = Typeface.DEFAULT_BOLD
        contentDescription = label
        setPadding(0, dp(7), dp(8), 0)
        setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
    }.also { view ->
        // Label is represented in accessibility; visible UI stays compact.
    }

    private fun actionButton(label: String, color: Int) = Button(this).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        background = rounded(color, 16f)
        stateListAnimator = null
    }

    private fun card() = LinearLayout(this).apply { background = rounded(panel, 18f) }

    private fun rounded(color: Int, radiusDp: Float) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
    }

    private fun formatDuration(ms: Long): String {
        val total = ms / 1000
        return "%02d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
    }

    private fun formatPace(ms: Long, meters: Double): String {
        val secPerKm = (ms / 1000.0) / (meters / 1000.0)
        val min = (secPerKm / 60).toInt()
        val sec = (secPerKm % 60).toInt()
        return "%d:%02d /km".format(min, sec)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume(); refreshStats() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { handler.removeCallbacks(tick); mapView.onDestroy(); store.close(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
}
