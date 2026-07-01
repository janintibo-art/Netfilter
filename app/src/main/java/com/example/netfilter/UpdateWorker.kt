package com.example.netfilter

import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Rafraîchit les listes de blocage une fois par jour (via WorkManager, en respectant
 * la connectivité, et en survivant aux redémarrages). Applique aussitôt si le filtrage
 * tourne.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        BlockListRepository.refreshDownloadedLists(applicationContext)
        if (FilterVpnService.isRunning) {
            runCatching {
                applicationContext.startService(
                    Intent(applicationContext, FilterVpnService::class.java)
                        .setAction(FilterVpnService.ACTION_RELOAD)
                )
            }
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "netfilter-auto-update"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
