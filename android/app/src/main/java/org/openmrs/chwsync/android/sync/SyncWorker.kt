package org.openmrs.chwsync.android.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import org.openmrs.chwsync.android.ChwSyncApplication
import org.openmrs.chwsync.android.data.remote.ChwSyncApiException

/**
 * Background sync worker. WorkManager is given a connected-network constraint so
 * it only runs when a push/pull has a chance of succeeding. It returns
 * [Result.retry] for transient (network / 5xx) failures and [Result.success]
 * once the round trip completes — even if individual items conflicted, since
 * those are terminal outcomes the UI surfaces.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val engine = (applicationContext as ChwSyncApplication).container.syncEngine
        return try {
            engine.sync()
            Result.success()
        } catch (e: ChwSyncApiException) {
            if (e.httpStatus == 0 || e.httpStatus >= 500) Result.retry() else Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

/** Small helper that owns the unique work names and constraints. */
class SyncScheduler(private val context: Context) {

    fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_SHOT_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    companion object {
        const val PERIODIC_WORK = "chwsync-sync-periodic"
        const val ONE_SHOT_WORK = "chwsync-sync-now"
    }
}
