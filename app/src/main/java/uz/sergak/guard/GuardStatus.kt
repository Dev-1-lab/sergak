package uz.sergak.guard

import android.content.ComponentName
import android.content.Context
import androidx.core.app.NotificationManagerCompat

object GuardStatus {
    /** Foydalanuvchi Sergak'ga bildirishnomalarni o'qish ruxsatini berganmi. */
    fun isListenerEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun component(context: Context) = ComponentName(context, GuardListenerService::class.java)
}
