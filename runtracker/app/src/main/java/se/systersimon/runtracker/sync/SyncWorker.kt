package se.systersimon.runtracker.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import se.systersimon.runtracker.BuildConfig
import se.systersimon.runtracker.data.AppDatabase

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (BuildConfig.SYNC_BASE_URL.isBlank() || BuildConfig.SYNC_TOKEN.isBlank()) return Result.success()
        val dao = AppDatabase.get(applicationContext).runDao()
        val client = CloudflareClient(BuildConfig.SYNC_BASE_URL, BuildConfig.SYNC_TOKEN)

        return try {
            dao.unsyncedRuns().forEach { initialRun ->
                var run = dao.getRun(initialRun.id) ?: initialRun
                while (true) {
                    val points = dao.unsyncedPoints(run.id, limit = 200)
                    if (!client.sync(run, points)) return Result.retry()
                    if (points.isNotEmpty()) dao.markPointsSynced(points.map { it.id })
                    if (points.size < 200) break
                    run = dao.getRun(run.id) ?: run
                }
                if (run.status == "FINISHED" && dao.unsyncedPoints(run.id, limit = 1).isEmpty()) {
                    dao.markRunSynced(run.id)
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
