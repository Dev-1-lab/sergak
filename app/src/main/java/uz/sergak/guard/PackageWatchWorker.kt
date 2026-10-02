package uz.sergak.guard

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import uz.sergak.data.Prefs
import java.util.concurrent.TimeUnit

/**
 * Zaxira kuzatuvchi: real vaqt himoyasi yoqilmagan bo'lsa ham, har ~15 daqiqada
 * yangi o'rnatilgan ilovalarni tekshiradi (PackageManager.getChangedPackages).
 */
class PackageWatchWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        val pm = ctx.packageManager
        val seq = prefs.packageSeq
        val changed = pm.getChangedPackages(if (seq < 0) 0 else seq)
        if (changed == null) return Result.success()
        // Birinchi ishga tushishda faqat hisoblagichni eslab qolamiz — eski ilovalar skaner bo'limida ko'rinadi.
        if (seq >= 0 && !GuardStatus.isListenerEnabled(ctx)) {
            for (pkg in changed.packageNames) {
                val isNew = try {
                    val info = pm.getPackageInfo(pkg, 0)
                    info.firstInstallTime == info.lastUpdateTime
                } catch (_: Exception) {
                    false
                }
                if (isNew) GuardListenerService.checkNewPackage(ctx, pkg)
            }
        }
        prefs.packageSeq = changed.sequenceNumber
        return Result.success()
    }

    companion object {
        private const val NAME = "package_watch"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<PackageWatchWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
