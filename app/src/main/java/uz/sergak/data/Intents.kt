package uz.sergak.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

/** Tizim sozlamalari va boshqa ilovalarni xavfsiz ochish uchun yordamchi. */
object Intents {

    val TELEGRAM_PACKAGES = listOf(
        "org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram",
        "org.telegram.plus", "ir.ilmili.telegraph",
    )

    fun launch(context: Context, vararg candidates: Intent): Boolean {
        for (i in candidates) {
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        Toast.makeText(context, "Bu bo'limni avtomatik ochib bo'lmadi. Sozlamalardan qo'lda toping.", Toast.LENGTH_LONG).show()
        return false
    }

    fun securitySettings(c: Context) = launch(c, Intent(Settings.ACTION_SECURITY_SETTINGS), Intent(Settings.ACTION_SETTINGS))

    fun developerSettings(c: Context) =
        launch(c, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), Intent(Settings.ACTION_SETTINGS))

    fun systemUpdate(c: Context) =
        launch(c, Intent("android.settings.SYSTEM_UPDATE_SETTINGS"), Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))

    fun appDetails(c: Context, pkg: String) =
        launch(c, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))

    fun uninstall(c: Context, pkg: String) =
        launch(c, Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")), Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))

    fun unknownSources(c: Context, pkg: String?) = launch(
        c,
        *listOfNotNull(
            pkg?.let { Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$it")) },
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
        ).toTypedArray(),
    )

    fun accessibility(c: Context) = launch(c, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun notificationListenerSettings(c: Context) = launch(
        c,
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )

    fun appNotificationSettings(c: Context) = launch(
        c,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}")),
    )

    fun deviceAdmins(c: Context) = launch(
        c,
        Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.DeviceAdminSettings")),
        Intent(Settings.ACTION_SECURITY_SETTINGS),
    )

    fun playProtect(c: Context) = launch(
        c,
        Intent("com.google.android.gms.settings.VERIFY_APPS_SETTINGS"),
        Intent().setClassName("com.google.android.gms", "com.google.android.gms.security.settings.VerifyAppsSettingsActivity"),
        Intent(Settings.ACTION_SECURITY_SETTINGS),
    )

    fun openTelegram(c: Context): Boolean {
        val pm = c.packageManager
        val pkg = TELEGRAM_PACKAGES.firstOrNull { pm.getLaunchIntentForPackage(it) != null }
        return if (pkg != null) launch(c, pm.getLaunchIntentForPackage(pkg)!!)
        else launch(c, Intent(Intent.ACTION_VIEW, Uri.parse("tg://settings")))
    }

    fun openUrl(c: Context, url: String) = launch(c, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    fun dial(c: Context, phone: String) = launch(c, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))

    fun airplaneMode(c: Context) = launch(c, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS))
}
