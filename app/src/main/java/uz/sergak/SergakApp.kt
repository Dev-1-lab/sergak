package uz.sergak

import android.app.Application
import uz.sergak.guard.FeedSyncWorker
import uz.sergak.guard.Notifier
import uz.sergak.guard.PackageWatchWorker

class SergakApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        PackageWatchWorker.schedule(this)
        FeedSyncWorker.schedule(this)
    }
}
