package uz.sergak.data

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.provider.Telephony
import uz.sergak.core.AppFacts
import uz.sergak.core.AppRisk
import uz.sergak.core.AppRiskScorer
import java.util.concurrent.TimeUnit

/**
 * Telefonga o'rnatilgan ilovalarni (tizim ilovalaridan tashqari) SMS-o'g'ri va bank troyani
 * belgilariga tekshiradi. Faqat Android API ma'lumotlaridan foydalanadi, hech narsa yuborilmaydi.
 */
class AppScanner(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    /** Bitta skanerlash davomida qayta ishlatiladigan tizim holati. */
    private class Snapshot(
        val accessibility: Set<String>,
        val notificationListeners: Set<String>,
        val deviceAdmins: Set<String>,
        val defaultSms: String?,
    )

    private fun snapshot(): Snapshot {
        val cr = context.contentResolver
        fun pkgsOf(setting: String): Set<String> =
            (Settings.Secure.getString(cr, setting) ?: "")
                .split(':')
                .mapNotNull { it.substringBefore('/').trim().takeIf { p -> p.isNotEmpty() } }
                .toSet()
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admins = try {
            dpm.activeAdmins?.map { it.packageName }?.toSet() ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
        val sms = try {
            Telephony.Sms.getDefaultSmsPackage(context)
        } catch (_: Exception) {
            null
        }
        return Snapshot(
            pkgsOf(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            pkgsOf("enabled_notification_listeners"),
            admins,
            sms,
        )
    }

    @Suppress("DEPRECATION")
    private fun installedPackages(): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        }

    @Suppress("DEPRECATION")
    private fun packageInfo(pkg: String): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun isUserApp(ai: ApplicationInfo): Boolean =
        (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

    /** Barcha foydalanuvchi ilovalarini skanerlaydi, xavflilari birinchi. */
    fun scanAll(): List<AppRisk> {
        val snap = snapshot()
        return installedPackages()
            .asSequence()
            .filter { it.packageName != context.packageName }
            .filter { it.applicationInfo?.let(::isUserApp) == true }
            .map { AppRiskScorer.score(facts(it, snap)) }
            .sortedByDescending { it.verdict.score }
            .toList()
    }

    /** Yangi o'rnatilgan bitta ilovani tekshiradi (null — tizim ilovasi yoki topilmadi). */
    fun scanOne(pkg: String): AppRisk? {
        if (pkg == context.packageName) return null
        val info = packageInfo(pkg) ?: return null
        val ai = info.applicationInfo ?: return null
        if (!isUserApp(ai)) return null
        return AppRiskScorer.score(facts(info, snapshot()))
    }

    private fun facts(info: PackageInfo, snap: Snapshot): AppFacts {
        val pkg = info.packageName
        val requested = info.requestedPermissions?.toList() ?: emptyList()
        val flags = info.requestedPermissionsFlags
        val granted = requested.filterIndexed { i, _ ->
            flags != null && i < flags.size && (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
        }.toSet()

        val (installer, via) = installSource(pkg)
        val label = info.applicationInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkg
        val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - info.firstInstallTime).coerceAtLeast(0)

        return AppFacts(
            packageName = pkg,
            label = label,
            grantedPermissions = granted,
            installer = installer,
            hasLauncherIcon = pm.getLaunchIntentForPackage(pkg) != null,
            isAccessibilityEnabled = pkg in snap.accessibility,
            isNotificationListener = pkg in snap.notificationListeners,
            isDeviceAdmin = pkg in snap.deviceAdmins,
            canInstallApps = "android.permission.REQUEST_INSTALL_PACKAGES" in requested &&
                canRequestInstalls(pkg, info.applicationInfo?.uid ?: -1),
            isDefaultSmsApp = pkg == snap.defaultSms,
            installedDaysAgo = days,
            requestedPermissions = requested.toSet(),
            installedVia = via,
        )
    }

    /** (o'rnatuvchi do'kon, qaysi ilova orqali ishga tushirilgan) */
    @Suppress("DEPRECATION")
    private fun installSource(pkg: String): Pair<String?, String?> = try {
        if (Build.VERSION.SDK_INT >= 30) {
            val s = pm.getInstallSourceInfo(pkg)
            val installer = s.installingPackageName
            val initiating = s.initiatingPackageName
            installer to (initiating ?: installer)
        } else {
            val i = pm.getInstallerPackageName(pkg)
            i to i
        }
    } catch (_: Exception) {
        null to null
    }

    /** "Noma'lum ilovalarni o'rnatish" ruxsati shu ilovaga berilganmi. */
    @Suppress("DEPRECATION")
    fun canRequestInstalls(pkg: String, uid: Int): Boolean {
        if (uid < 0) return false
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return try {
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                ops.unsafeCheckOpNoThrow(OP_REQUEST_INSTALL, uid, pkg)
            } else {
                ops.checkOpNoThrow(OP_REQUEST_INSTALL, uid, pkg)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    /** Telegram (yoki boshqa ilova) APK o'rnata oladimi? null — ilova o'rnatilmagan. */
    fun installAllowedFor(pkg: String): Boolean? {
        val info = packageInfo(pkg) ?: return null
        val uid = info.applicationInfo?.uid ?: return null
        return canRequestInstalls(pkg, uid)
    }

    fun ownUid(): Int = Process.myUid()

    companion object {
        private const val OP_REQUEST_INSTALL = "android:request_install_packages"
    }
}
