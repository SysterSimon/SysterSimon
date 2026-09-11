package se.systersimon.runtracker.ui

import android.content.Context
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

class OfflineMapDownloader(private val context: Context) {
    companion object {
        const val STYLE_URL = "https://tiles.openfreemap.org/styles/dark"
    }

    fun download(
        bounds: LatLngBounds,
        onProgress: (Int) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val definition = OfflineTilePyramidRegionDefinition(
            STYLE_URL,
            bounds,
            8.0,
            16.0,
            context.resources.displayMetrics.density,
            false,
        )
        val metadata = "{\"name\":\"RunTracker offline\"}".toByteArray()
        OfflineManager.getInstance(context).createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            val required = status.requiredResourceCount
                            val progress = if (required > 0) ((status.completedResourceCount * 100) / required).toInt().coerceIn(0, 100) else 0
                            onProgress(progress)
                            if (status.isComplete) {
                                offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                                onDone()
                            }
                        }

                        override fun onError(error: OfflineRegionError) {
                            onError(error.message ?: "Okänt kartfel")
                        }

                        override fun mapboxTileCountLimitExceeded(limit: Long) {
                            onError("Offlinegräns nådd: $limit tiles")
                        }
                    })
                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) = onError(error)
            }
        )
    }
}
