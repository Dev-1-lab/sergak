package uz.sergak.guard

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import uz.sergak.data.IntelRepository
import uz.sergak.data.cloud.CloudClient
import java.util.concurrent.TimeUnit

/**
 * Kuniga bir marta serverdan yangilangan tahdid ro'yxatini (zararli APK xeshlari, fishing domenlar) yuklaydi.
 * Ro'yxat telefonda saqlanadi va internet bo'lmaganda ham ishlaydi. Serverga hech qanday shaxsiy ma'lumot ketmaydi.
 */
class FeedSyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        IntelRepository(applicationContext).syncFeed()
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            if (!CloudClient.isAvailable) return
            val req = PeriodicWorkRequestBuilder<FeedSyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("feed_sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
