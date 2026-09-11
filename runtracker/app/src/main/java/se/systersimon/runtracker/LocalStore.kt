package se.systersimon.runtracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(context: Context) : SQLiteOpenHelper(context, "runtracker.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE runs(
                id TEXT PRIMARY KEY,
                started_at INTEGER NOT NULL,
                finished_at INTEGER,
                distance_m REAL NOT NULL DEFAULT 0,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                synced INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE points(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                run_id TEXT NOT NULL,
                time_ms INTEGER NOT NULL,
                lat REAL NOT NULL,
                lon REAL NOT NULL,
                accuracy REAL NOT NULL,
                FOREIGN KEY(run_id) REFERENCES runs(id)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_points_run ON points(run_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun startRun(id: String, startedAt: Long) {
        writableDatabase.insertOrThrow("runs", null, ContentValues().apply {
            put("id", id)
            put("started_at", startedAt)
        })
    }

    fun addPoint(runId: String, p: TrackPoint) {
        writableDatabase.insert("points", null, ContentValues().apply {
            put("run_id", runId)
            put("time_ms", p.timeMs)
            put("lat", p.lat)
            put("lon", p.lon)
            put("accuracy", p.accuracy.toDouble())
        })
    }

    fun finishRun(id: String, finishedAt: Long, distanceM: Double, durationMs: Long) {
        writableDatabase.update("runs", ContentValues().apply {
            put("finished_at", finishedAt)
            put("distance_m", distanceM)
            put("duration_ms", durationMs)
        }, "id=?", arrayOf(id))
    }

    fun totalSince(epochMs: Long): Double {
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(distance_m),0) FROM runs WHERE finished_at IS NOT NULL AND started_at>=?",
            arrayOf(epochMs.toString())
        ).use { c -> return if (c.moveToFirst()) c.getDouble(0) else 0.0 }
    }

    fun recentRuns(limit: Int = 5): List<Triple<Double, Long, Long>> {
        val out = mutableListOf<Triple<Double, Long, Long>>()
        readableDatabase.rawQuery(
            "SELECT distance_m,duration_ms,started_at FROM runs WHERE finished_at IS NOT NULL ORDER BY started_at DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) out += Triple(c.getDouble(0), c.getLong(1), c.getLong(2))
        }
        return out
    }

    fun unsyncedRuns(): List<String> {
        val out = mutableListOf<String>()
        readableDatabase.rawQuery(
            "SELECT id FROM runs WHERE finished_at IS NOT NULL AND synced=0 ORDER BY started_at",
            null
        ).use { c -> while (c.moveToNext()) out += c.getString(0) }
        return out
    }

    fun runAsJson(id: String): JSONObject? {
        val db = readableDatabase
        val run = db.rawQuery(
            "SELECT id,started_at,finished_at,distance_m,duration_ms FROM runs WHERE id=?",
            arrayOf(id)
        ).use { c ->
            if (!c.moveToFirst()) return null
            JSONObject().apply {
                put("id", c.getString(0))
                put("startedAt", c.getLong(1))
                put("finishedAt", c.getLong(2))
                put("distanceM", c.getDouble(3))
                put("durationMs", c.getLong(4))
            }
        }

        val pts = JSONArray()
        db.rawQuery(
            "SELECT time_ms,lat,lon,accuracy FROM points WHERE run_id=? ORDER BY time_ms",
            arrayOf(id)
        ).use { c ->
            while (c.moveToNext()) {
                pts.put(JSONObject().apply {
                    put("timeMs", c.getLong(0))
                    put("lat", c.getDouble(1))
                    put("lon", c.getDouble(2))
                    put("accuracy", c.getDouble(3))
                })
            }
        }
        run.put("points", pts)
        return run
    }

    fun markSynced(id: String) {
        writableDatabase.update("runs", ContentValues().apply { put("synced", 1) }, "id=?", arrayOf(id))
    }
}
