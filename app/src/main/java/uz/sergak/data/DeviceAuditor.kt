package uz.sergak.data

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class CheckState { OK, WARN, BAD }

data class DeviceCheck(
    val id: String,
    val title: String,
    val state: CheckState,
    val detail: String,
    val fixLabel: String? = null,
    val fix: ((Context) -> Unit)? = null,
)

/** Telefonning asosiy xavfsizlik sozlamalarini tekshiradi. */
class DeviceAuditor(private val context: Context) {

    fun run(): List<DeviceCheck> = listOf(
        screenLock(), telegramInstall(), patchLevel(), developerOptions(), usbDebugging(), root(),
    )

    fun isScreenLockSet(): Boolean =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure

    private fun screenLock(): DeviceCheck = if (isScreenLockSet()) {
        DeviceCheck("screen_lock", "Ekran qulfi", CheckState.OK, "PIN, parol yoki grafik kalit o'rnatilgan.")
    } else {
        DeviceCheck(
            "screen_lock", "Ekran qulfi yo'q", CheckState.BAD,
            "Telefon yo'qolsa yoki o'g'irlansa, bank ilovalari va Telegram'ga istalgan odam kira oladi.",
            "Qulf o'rnatish", { c -> Intents.securitySettings(c) },
        )
    }

    fun isDeveloperModeOn(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1

    private fun developerOptions(): DeviceCheck = if (!isDeveloperModeOn()) {
        DeviceCheck("dev_options", "Dasturchi rejimi o'chiq", CheckState.OK, "Oddiy foydalanuvchi uchun to'g'ri holat.")
    } else {
        DeviceCheck(
            "dev_options", "Dasturchi rejimi yoqilgan", CheckState.WARN,
            "Agar siz dasturchi bo'lmasangiz, bu rejimni kimdir yoqqan bo'lishi mumkin. U orqali telefonni kompyuterdan boshqarish oson.",
            "O'chirish", { c -> Intents.developerSettings(c) },
        )
    }

    private fun usbDebugging(): DeviceCheck {
        val on = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        return if (!on) {
            DeviceCheck("adb", "USB orqali boshqarish o'chiq", CheckState.OK, "USB debugging yoqilmagan.")
        } else {
            DeviceCheck(
                "adb", "USB orqali boshqarish (USB debugging) yoqilgan", CheckState.WARN,
                "Telefon kompyuterga ulanganda undan ma'lumot olish va ilova o'rnatish mumkin.",
                "O'chirish", { c -> Intents.developerSettings(c) },
            )
        }
    }

    /** Telegram'ga APK o'rnatish ruxsati berilganmi — O'zbekistondagi eng xavfli sozlama. */
    fun telegramInstallAllowed(): List<String> {
        val scanner = AppScanner(context)
        return Intents.TELEGRAM_PACKAGES.filter { scanner.installAllowedFor(it) == true }
    }

    fun isTelegramInstalled(): Boolean =
        Intents.TELEGRAM_PACKAGES.any { context.packageManager.getLaunchIntentForPackage(it) != null }

    private fun telegramInstall(): DeviceCheck {
        val allowed = telegramInstallAllowed()
        return when {
            allowed.isNotEmpty() -> DeviceCheck(
                "tg_install", "Telegram APK o'rnata oladi!", CheckState.BAD,
                "Telegram'ga \"Noma'lum ilovalarni o'rnatish\" ruxsati berilgan. Chatda kelgan \"Sud qarori.apk\" kabi virus bitta bosishda o'rnatilib qoladi.",
                "Ruxsatni o'chirish", { c -> Intents.unknownSources(c, allowed.first()) },
            )
            isTelegramInstalled() -> DeviceCheck(
                "tg_install", "Telegram APK o'rnata olmaydi", CheckState.OK,
                "Telegram orqali kelgan APK fayllar avtomatik o'rnatilmaydi.",
            )
            else -> DeviceCheck("tg_install", "Telegram topilmadi", CheckState.OK, "Tekshirish shart emas.")
        }
    }

    fun patchAgeDays(): Long? = try {
        ChronoUnit.DAYS.between(LocalDate.parse(Build.VERSION.SECURITY_PATCH), LocalDate.now())
    } catch (_: Exception) {
        null
    }

    private fun patchLevel(): DeviceCheck {
        val age = patchAgeDays()
        val date = Build.VERSION.SECURITY_PATCH
        return when {
            age == null -> DeviceCheck("patch", "Xavfsizlik yangilanishi", CheckState.WARN, "Sanani aniqlab bo'lmadi.", "Tekshirish", { c -> Intents.systemUpdate(c) })
            age <= 180 -> DeviceCheck("patch", "Tizim yangilangan", CheckState.OK, "Oxirgi xavfsizlik yangilanishi: $date")
            age <= 400 -> DeviceCheck(
                "patch", "Tizim yangilanishi eskirgan", CheckState.WARN,
                "Oxirgi xavfsizlik yangilanishi: $date. Yangilanish bormi, tekshiring.", "Tekshirish", { c -> Intents.systemUpdate(c) },
            )
            else -> DeviceCheck(
                "patch", "Tizim juda eski", CheckState.BAD,
                "Oxirgi xavfsizlik yangilanishi: $date (${age / 30} oy oldin). Eski tizimdagi zaifliklar orqali viruslar osonroq ishlaydi.",
                "Yangilash", { c -> Intents.systemUpdate(c) },
            )
        }
    }

    private fun root(): DeviceCheck {
        val paths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/su", "/data/local/xbin/su", "/data/local/bin/su", "/su/bin/su", "/data/adb/magisk")
        val rooted = paths.any { File(it).exists() } || (Build.TAGS?.contains("test-keys") == true)
        return if (!rooted) {
            DeviceCheck("root", "Root huquqlari yo'q", CheckState.OK, "Tizim himoyasi buzilmagan.")
        } else {
            DeviceCheck(
                "root", "Telefonda root huquqi bor", CheckState.WARN,
                "Root qilingan telefonda virus tizim himoyasini chetlab o'tishi mumkin. Ko'p bank ilovalari ham bunday telefonda ishlamaydi.",
            )
        }
    }
}
