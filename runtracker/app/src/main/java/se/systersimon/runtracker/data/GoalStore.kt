package se.systersimon.runtracker.data

import android.content.Context

class GoalStore(context: Context) {
    private val prefs = context.getSharedPreferences("goals", Context.MODE_PRIVATE)

    var weeklyKm: Float
        get() = prefs.getFloat("weekly_km", 20f)
        set(value) { prefs.edit().putFloat("weekly_km", value.coerceAtLeast(1f)).apply() }
}
