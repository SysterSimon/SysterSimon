package se.systersimon.runtracker.sync

import org.json.JSONArray
import org.json.JSONObject
import se.systersimon.runtracker.data.RunEntity
import se.systersimon.runtracker.data.TrackPointEntity
import java.net.HttpURLConnection
import java.net.URL

class CloudflareClient(
    private val baseUrl: String,
    private val token: String,
) {
    fun sync(run: RunEntity, points: List<TrackPointEntity>): Boolean {
        val body = JSONObject().apply {
            put("run", JSONObject().apply {
                put("id", run.id)
                put("startedAt", run.startedAt)
                put("endedAt", run.endedAt ?: JSONObject.NULL)
                put("distanceM", run.distanceM)
                put("durationMs", run.durationMs)
                put("status", run.status)
            })
            put("points", JSONArray().apply {
                points.forEach { p ->
                    put(JSONObject().apply {
                        put("id", p.id)
                        put("runId", p.runId)
                        put("recordedAt", p.recordedAt)
                        put("latitude", p.latitude)
                        put("longitude", p.longitude)
                        put("accuracyM", p.accuracyM)
                        put("altitudeM", p.altitudeM ?: JSONObject.NULL)
                        put("speedMps", p.speedMps ?: JSONObject.NULL)
                    })
                }
            })
        }

        val connection = (URL(baseUrl.trimEnd('/') + "/v1/sync").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            connection.responseCode in 200..299
        } finally {
            connection.disconnect()
        }
    }
}
