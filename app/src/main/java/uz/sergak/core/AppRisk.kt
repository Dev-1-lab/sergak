package uz.sergak.core

/** Telefonda o'rnatilgan bitta ilova haqidagi faktlar (Android API'dan yig'iladi). */
data class AppFacts(
    val packageName: String,
    val label: String,
    val grantedPermissions: Set<String>,
    val installer: String?,
    val hasLauncherIcon: Boolean,
    val isAccessibilityEnabled: Boolean,
    val isNotificationListener: Boolean,
    val isDeviceAdmin: Boolean,
    val canInstallApps: Boolean,
    val isDefaultSmsApp: Boolean,
    val installedDaysAgo: Long,
    val requestedPermissions: Set<String> = grantedPermissions,
    /** Qaysi ilova orqali o'rnatilgan (masalan, Telegram'dan ochilgan APK). */
    val installedVia: String? = null,
)

data class AppRisk(
    val facts: AppFacts,
    val verdict: Verdict,
    /** O'rnatilgan APK faylning SHA-256 xeshi (faqat do'kondan tashqari o'rnatilganlar uchun hisoblanadi). */
    val sha256: String? = null,
    /** Kiritilgan qo'shimcha topilmalar (statik tahlil, IOC, bulut) — qayta baholashda kerak. */
    val extra: List<Finding> = emptyList(),
)

/**
 * SMS-o'g'ri (SMS stealer) va bank troyanlariga xos belgilarni baholaydi.
 * O'zbekistonda tarqalgan Ajina, Qwizzserial, Wonderland kabi viruslar:
 *  - Google Play'dan emas, APK orqali o'rnatiladi
 *  - SMS o'qish/qabul qilish ruxsatini so'raydi
 *  - ko'pincha belgisini (ikonkasini) yashiradi
 *  - maxsus imkoniyatlar (Accessibility) va bildirishnomalarga kirishni so'raydi
 */
object AppRiskScorer {

    const val PERM_RECEIVE_SMS = "android.permission.RECEIVE_SMS"
    const val PERM_READ_SMS = "android.permission.READ_SMS"
    const val PERM_SEND_SMS = "android.permission.SEND_SMS"
    const val PERM_CALL_PHONE = "android.permission.CALL_PHONE"
    const val PERM_READ_CONTACTS = "android.permission.READ_CONTACTS"
    const val PERM_SYSTEM_ALERT = "android.permission.SYSTEM_ALERT_WINDOW"
    const val PERM_READ_PHONE_NUMBERS = "android.permission.READ_PHONE_NUMBERS"
    const val PERM_READ_CALL_LOG = "android.permission.READ_CALL_LOG"

    /** Ishonchli do'konlar. */
    val TRUSTED_INSTALLERS = setOf(
        "com.android.vending",            // Google Play
        "com.google.android.packageinstaller.playstore",
        "com.huawei.appmarket",           // Huawei AppGallery
        "com.xiaomi.market", "com.xiaomi.mipicks", "com.xiaomi.discover", // Xiaomi GetApps
        "com.sec.android.app.samsungapps", // Galaxy Store
        "com.heytap.market", "com.oppo.market", // OPPO / realme
        "com.bbk.appstore", "com.vivo.appstore", // vivo
        "com.transsion.phoenix", "com.transsnet.store", // Tecno/Infinix
        "com.amazon.venezia",
        "com.google.android.feedback",
    )

    private val SMS_PERMS = setOf(PERM_RECEIVE_SMS, PERM_READ_SMS, PERM_SEND_SMS)

    val MESSENGER_NAMES = mapOf(
        "org.telegram.messenger" to "Telegram", "org.telegram.messenger.web" to "Telegram",
        "org.thunderdog.challegram" to "Telegram X", "org.telegram.plus" to "Plus Messenger",
        "ir.ilmili.telegraph" to "Graph Messenger", "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business", "com.imo.android.imoim" to "imo",
        "com.viber.voip" to "Viber", "com.instagram.android" to "Instagram",
        "com.android.chrome" to "Chrome brauzeri", "com.opera.browser" to "Opera",
        "com.yandex.browser" to "Yandex brauzeri", "com.mi.android.globalFileexplorer" to "Fayl menejeri",
        "com.google.android.apps.nbu.files" to "Files (Google)",
    )

    fun isSideloaded(a: AppFacts): Boolean = a.installer == null || a.installer !in TRUSTED_INSTALLERS

    fun score(a: AppFacts, extra: List<Finding> = emptyList(), sha256: String? = null): AppRisk {
        val f = mutableListOf<Finding>()
        f += extra
        val sideloaded = a.installer == null || a.installer !in TRUSTED_INSTALLERS
        val smsPerms = a.grantedPermissions.intersect(SMS_PERMS)

        if (sideloaded) {
            val via = a.installedVia?.let { MESSENGER_NAMES[it] ?: it }
            f += Finding(
                "sideloaded", "Google Play'dan o'rnatilmagan",
                when {
                    via != null && a.installedVia in MESSENGER_NAMES ->
                        "Ilova $via orqali kelgan APK fayldan o'rnatilgan. Firibgarlar viruslarni aynan shu yo'l bilan tarqatadi."
                    via != null -> "Ilova APK fayl orqali o'rnatilgan (manba: $via)."
                    else -> "Ilova APK fayl orqali qo'lda o'rnatilgan."
                },
                "Bu ilovani qayerdan olganingizni eslang. Bilmasangiz — o'chiring.",
                if (a.installedVia in MESSENGER_NAMES) 35 else 20,
            )
        }
        FakeAppDetector.check(a.label, a.packageName, sideloaded)?.let { f += it }
        if (smsPerms.isNotEmpty() && !a.isDefaultSmsApp) {
            f += Finding(
                "sms_access", "SMS xabarlaringizni o'qiy oladi",
                "Bank va Telegram kodlari SMS orqali keladi. SMS-o'g'ri viruslar aynan shu ruxsat orqali pulni yechadi.",
                "Bu ilovaga SMS kerakmi? Kerak bo'lmasa, ruxsatni o'chiring yoki ilovani o'chiring.",
                if (sideloaded) 45 else 15,
            )
        } else if (sideloaded && !a.isDefaultSmsApp && a.requestedPermissions.any { it in SMS_PERMS }) {
            f += Finding(
                "sms_request", "SMS ruxsatini so'raydi",
                "Ilova hali ruxsat olmagan, lekin SMS o'qish/qabul qilishni so'rashga tayyorlangan. Bu SMS-o'g'ri viruslarning asosiy belgisi.",
                "Ilova SMS ruxsatini so'rasa — RAD ETING va ilovani o'chiring.", 35,
            )
        }
        if (!a.hasLauncherIcon) {
            f += Finding(
                "hidden_icon", "Ilova belgisi (ikonka) yashirilgan",
                "Oddiy ilovalar menyuda ko'rinadi. Viruslar o'zini yashirish uchun belgisini o'chiradi.",
                "Bu ilovani tanimasangiz — o'chiring.",
                if (sideloaded) 30 else 5,
            )
        }
        if (a.isAccessibilityEnabled) {
            f += Finding(
                "accessibility", "Maxsus imkoniyatlar (Accessibility) yoqilgan",
                "Bu ruxsat bilan ilova ekrandagi hamma narsani o'qiy oladi va siz uchun tugmalarni bosa oladi — bank troyanlarining asosiy quroli.",
                "Agar bu ilovaga o'zingiz ataylab ruxsat bermagan bo'lsangiz, darhol o'chiring.",
                if (sideloaded) 40 else 15,
            )
        }
        if (a.isNotificationListener) {
            f += Finding(
                "notif_access", "Bildirishnomalarni o'qiy oladi",
                "Ilova kelgan barcha xabarlarni, jumladan SMS-kodlarni ko'ra oladi.",
                "Ruxsatni o'zingiz bermagan bo'lsangiz, o'chiring.",
                if (sideloaded) 30 else 5,
            )
        }
        if (a.isDeviceAdmin) {
            f += Finding(
                "device_admin", "Qurilma administratori",
                "Bu huquq bilan ilovani o'chirish qiyinlashadi. Viruslar o'zini o'chirtirmaslik uchun shundan foydalanadi.",
                "Sozlamalar → Xavfsizlik → Qurilma administratorlari bo'limida tekshiring.",
                if (sideloaded) 30 else 5,
            )
        }
        if (a.canInstallApps) {
            f += Finding(
                "can_install", "Boshqa ilovalarni o'rnata oladi",
                "Bu ilovaga \"Noma'lum ilovalarni o'rnatish\" ruxsati berilgan. Telegram yoki brauzerga bu ruxsat berilsa, APK fayl bir bosishda o'rnatiladi.",
                "Ruxsatni o'chirib qo'ying — kerak bo'lsa keyin yana yoqasiz.",
                if (sideloaded) 15 else 10,
            )
        }
        if (PERM_SYSTEM_ALERT in a.grantedPermissions && sideloaded) {
            f += Finding(
                "overlay", "Boshqa ilovalar ustidan oyna ochadi",
                "Bank ilovasi ustidan soxta oyna chiqarib parol yoki PIN o'g'irlash mumkin.",
                "Tanimasangiz — o'chiring.", 15,
            )
        }
        if (sideloaded && a.installedDaysAgo <= 3 && (smsPerms.isNotEmpty() || !a.hasLauncherIcon || a.isAccessibilityEnabled)) {
            f += Finding(
                "recent", "Yaqinda o'rnatilgan",
                "Ilova so'nggi ${a.installedDaysAgo + 1} kun ichida o'rnatilgan.",
                "Telegramda kelgan biror faylni ochganmidingiz? Bo'lsa — bu o'sha bo'lishi mumkin.", 10,
            )
        }

        return AppRisk(a, Verdict.of(f), sha256, extra)
    }
}
