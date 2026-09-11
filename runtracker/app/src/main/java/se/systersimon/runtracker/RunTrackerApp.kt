package se.systersimon.runtracker

import android.app.Application
import org.maplibre.android.MapLibre

class RunTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
    }
}
