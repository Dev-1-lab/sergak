package uz.sergak.data

import android.content.Context
import uz.sergak.guard.GuardStatus

enum class ChecklistGroup(val title: String, val subtitle: String) {
    PHONE("Telefon", "Qurilmaning asosiy himoyasi"),
    TELEGRAM("Telegram", "Akkauntni o'g'irlashdan himoya"),
    PAYMENTS("Click, Payme, bank ilovalari", "Pulingizni himoya qilish"),
}

data class ChecklistItem(
    val id: String,
    val group: ChecklistGroup,
    val title: String,
    val why: String,
    val steps: List<String>,
    val actionLabel: String? = null,
    val action: ((Context) -> Unit)? = null,
    /** null — foydalanuvchi qo'lda belgilaydi; aks holda telefon holatidan avtomatik aniqlanadi. */
    val autoCheck: ((Context) -> Boolean)? = null,
    val weight: Int = 1,
)

object Checklist {

    val items: List<ChecklistItem> = listOf(
        // ---------------- TELEFON ----------------
        ChecklistItem(
            "guard_on", ChecklistGroup.PHONE, "Sergak real vaqt himoyasini yoqing",
            "Telegram va SMS orqali kelgan APK fayllar, fishing havolalar va kod so'rovlari haqida darhol ogohlantiradi. Hammasi telefon ichida tahlil qilinadi.",
            listOf("\"Yoqish\" tugmasini bosing", "Ro'yxatdan \"Sergak\"ni toping va ruxsat bering"),
            "Yoqish", { c -> Intents.notificationListenerSettings(c) },
            autoCheck = { c -> GuardStatus.isListenerEnabled(c) }, weight = 3,
        ),
        ChecklistItem(
            "screen_lock", ChecklistGroup.PHONE, "Ekran qulfi (PIN yoki barmoq izi)",
            "Telefon qo'lga tushsa, qulfsiz holda bank ilovalari va Telegram ochiq qoladi.",
            listOf("Sozlamalar → Xavfsizlik → Ekran qulfi", "Kamida 6 xonali PIN tanlang"),
            "Ochish", { c -> Intents.securitySettings(c) },
            autoCheck = { c -> DeviceAuditor(c).isScreenLockSet() }, weight = 2,
        ),
        ChecklistItem(
            "tg_unknown_sources", ChecklistGroup.PHONE, "Telegram'ga APK o'rnatishni taqiqlang",
            "Shunda chatda kelgan virusli APK bitta bosishda o'rnatilib qolmaydi. Bu eng muhim sozlamalardan biri.",
            listOf("\"Ochish\" ni bosing", "\"Ushbu manbadan ruxsat berish\" ni O'CHIRING"),
            "Ochish", { c -> Intents.unknownSources(c, Intents.TELEGRAM_PACKAGES.firstOrNull { p -> c.packageManager.getLaunchIntentForPackage(p) != null }) },
            autoCheck = { c -> DeviceAuditor(c).telegramInstallAllowed().isEmpty() }, weight = 3,
        ),
        ChecklistItem(
            "play_protect", ChecklistGroup.PHONE, "Google Play Protect yoqilgan",
            "Play Protect ma'lum viruslarni avtomatik aniqlaydi va o'chiradi.",
            listOf("Play Market → Profil rasmi → Play Protect", "\"Ilovalarni Play Protect bilan tekshirish\" yoqilgan bo'lsin"),
            "Ochish", { c -> Intents.playProtect(c) },
        ),
        ChecklistItem(
            "updates", ChecklistGroup.PHONE, "Tizim yangilangan",
            "Yangilanishlar ma'lum zaifliklarni yopadi.",
            listOf("Sozlamalar → Tizim → Yangilanish"),
            "Tekshirish", { c -> Intents.systemUpdate(c) },
            autoCheck = { c -> (DeviceAuditor(c).patchAgeDays() ?: 999) <= 400 },
        ),
        ChecklistItem(
            "dev_off", ChecklistGroup.PHONE, "Dasturchi rejimi o'chiq",
            "Dasturchi bo'lmasangiz, bu rejim kerak emas va xavf tug'diradi.",
            listOf("Sozlamalar → Dasturchilar uchun → O'chirish"),
            "Ochish", { c -> Intents.developerSettings(c) },
            autoCheck = { c -> !DeviceAuditor(c).isDeveloperModeOn() },
        ),
        ChecklistItem(
            "google_2fa", ChecklistGroup.PHONE, "Google akkauntda 2 bosqichli tasdiqlash",
            "Google akkaunt — telefoningizning \"kaliti\": kontaktlar, rasmlar, parollar shu yerda.",
            listOf("myaccount.google.com → Xavfsizlik", "\"2 bosqichli tasdiqlash\" ni yoqing"),
            "Ochish", { c -> Intents.openUrl(c, "https://myaccount.google.com/signinoptions/two-step-verification") },
        ),
        ChecklistItem(
            "sim_pin", ChecklistGroup.PHONE, "SIM-kartaga PIN kod",
            "SIM-karta o'g'irlansa, uning raqamiga keladigan bank va Telegram kodlari firibgarga tushadi.",
            listOf("Sozlamalar → Xavfsizlik → SIM-karta qulfi", "Standart PIN odatda SIM-karta qutisida yozilgan"),
            "Ochish", { c -> Intents.securitySettings(c) },
        ),

        // ---------------- TELEGRAM ----------------
        ChecklistItem(
            "tg_2fa", ChecklistGroup.TELEGRAM, "Ikki bosqichli tasdiqlash (bulutli parol)",
            "ENG MUHIM QADAM. Firibgar SMS-kodni qo'lga kiritsa ham, parolsiz akkauntga kira olmaydi.",
            listOf(
                "Telegram → Sozlamalar → Maxfiylik va xavfsizlik",
                "\"Ikki bosqichli tasdiqlash\" → parol o'rnating",
                "Tiklash uchun email qo'shing",
                "Parolni hech kimga aytmang va boshqa joyda ishlatmang",
            ),
            "Telegram'ni ochish", { c -> Intents.openTelegram(c) }, weight = 3,
        ),
        ChecklistItem(
            "tg_sessions", ChecklistGroup.TELEGRAM, "Begona qurilmalarni chiqarib yuboring",
            "Akkauntingizga boshqa telefon yoki kompyuterdan kirilgan bo'lishi mumkin.",
            listOf("Telegram → Sozlamalar → Qurilmalar", "Tanimagan qurilmani tanlab \"Seansni yakunlash\"", "\"Boshqa barcha seanslarni yakunlash\" ham mumkin"),
            "Telegram'ni ochish", { c -> Intents.openTelegram(c) }, weight = 2,
        ),
        ChecklistItem(
            "tg_passkey", ChecklistGroup.TELEGRAM, "Kirish uchun email yoki passkey",
            "SMS kod o'rniga email yoki passkey orqali kirish SIM almashtirish hujumidan himoya qiladi.",
            listOf("Telegram → Sozlamalar → Maxfiylik va xavfsizlik", "\"Kirish uchun email\" yoki \"Passkey\" bo'limini sozlang"),
            "Telegram'ni ochish", { c -> Intents.openTelegram(c) },
        ),
        ChecklistItem(
            "tg_privacy_phone", ChecklistGroup.TELEGRAM, "Telefon raqamingizni yashiring",
            "Raqamingizni bilgan firibgar sizga \"bank xodimi\" bo'lib qo'ng'iroq qiladi yoki kod so'raydi.",
            listOf("Maxfiylik va xavfsizlik → Telefon raqami", "\"Hech kim\" yoki \"Kontaktlarim\" ni tanlang"),
            "Telegram'ni ochish", { c -> Intents.openTelegram(c) },
        ),
        ChecklistItem(
            "tg_groups", ChecklistGroup.TELEGRAM, "Sizni guruhlarga kim qo'sha oladi",
            "Firibgarlar odamlarni \"kompensatsiya\", \"kredit\" guruhlariga ommaviy qo'shib, havola tarqatadi.",
            listOf("Maxfiylik va xavfsizlik → Guruhlar", "\"Kontaktlarim\" ni tanlang"),
            "Telegram'ni ochish", { c -> Intents.openTelegram(c) },
        ),
        ChecklistItem(
            "tg_autodownload", ChecklistGroup.TELEGRAM, "Fayllarni avtomatik yuklashni o'chiring",
            "Notanish chatlardan kelgan fayllar o'zi yuklanib qolmaydi.",
            listOf("Sozlamalar → Ma'lumotlar va xotira", "\"Avtomatik yuklash\" da \"Fayllar\" ni o'chiring"),
            "Telegram'ni ochish", { c -> Intents.openTelegram(c) },
        ),
        ChecklistItem(
            "tg_cards", ChecklistGroup.TELEGRAM, "Karta ma'lumotlarini chatlardan o'chiring",
            "Akkauntga kirgan firibgar birinchi navbatda \"Saqlangan xabarlar\"dan karta raqami va rasmini qidiradi.",
            listOf("Telegram qidiruvida \"8600\", \"9860\", \"karta\" deb qidiring", "Topilgan karta raqami, muddati va rasmlarini o'chiring"),
            "Telegram'ni ochish", { c -> Intents.openTelegram(c) },
        ),
        ChecklistItem(
            "tg_shield_bot", ChecklistGroup.TELEGRAM, "Guruhlaringizga @cybershielduz_bot qo'shing",
            "IIV Kiberxavfsizlik markazining rasmiy boti: guruh va kanallardan zararli APK va havolalarni avtomatik o'chiradi. Oila va mahalla guruhlari uchun foydali.",
            listOf("Botni oching va \"Start\" bosing", "O'zingiz admin bo'lgan guruhga qo'shing va admin huquqi bering"),
            "Botni ochish", { c -> Intents.openUrl(c, "https://t.me/cybershielduz_bot") },
        ),

        // ---------------- TO'LOV ILOVALARI ----------------
        ChecklistItem(
            "pay_pin", ChecklistGroup.PAYMENTS, "Ilovaga kirishda PIN yoki barmoq izi",
            "Telefon ochiq qolsa ham pul o'tkazib bo'lmaydi.",
            listOf("Click / Payme / bank ilovasi → Sozlamalar → Xavfsizlik", "PIN kod va biometrik kirishni yoqing"),
        ),
        ChecklistItem(
            "pay_limits", ChecklistGroup.PAYMENTS, "Kunlik o'tkazma limiti",
            "Firibgar kirib olsa ham, bir kunda hammasini yecha olmaydi.",
            listOf("Bank ilovasida karta sozlamalariga kiring", "Internet to'lovlar va P2P o'tkazmalarga limit qo'ying"),
        ),
        ChecklistItem(
            "pay_devices", ChecklistGroup.PAYMENTS, "Ulangan qurilmalarni tekshiring",
            "Kartangiz boshqa telefondagi ilovaga ham ulangan bo'lishi mumkin.",
            listOf("Ilova sozlamalarida \"Qurilmalar\" yoki \"Faol seanslar\" bo'limini oching", "Tanimagan qurilmalarni o'chiring"),
            weight = 2,
        ),
        ChecklistItem(
            "pay_sms", ChecklistGroup.PAYMENTS, "Karta SMS-xabarnomasi o'z raqamingizda",
            "Har bir yechim haqida darhol bilasiz. Xabarnoma begona raqamga ulangan bo'lsa — bu xavf belgisi.",
            listOf("Bank ilovasida yoki filialda xabarnoma raqamini tekshiring"),
        ),
        ChecklistItem(
            "pay_separate", ChecklistGroup.PAYMENTS, "Onlayn xaridlar uchun alohida karta",
            "Asosiy pulingiz boshqa kartada tursa, onlayn firibgarlikda yo'qotish kam bo'ladi.",
            listOf("Bank ilovasida virtual karta oching", "Unga faqat xarid oldidan kerakli summani o'tkazing"),
        ),
        ChecklistItem(
            "pay_official", ChecklistGroup.PAYMENTS, "Ilovalarni faqat Google Play'dan o'rnating",
            "\"Yangi versiya\" deb yuborilgan bank ilovasi APK fayli — har doim soxta.",
            listOf("Bank ilovasini Play Market'da nomi orqali qidiring", "Ishlab chiquvchi nomini tekshiring"),
        ),
    )

    fun isDone(context: Context, item: ChecklistItem, prefs: Prefs): Boolean =
        item.autoCheck?.let { check -> runCatching { check(context) }.getOrDefault(false) }
            ?: (item.id in prefs.doneItems)

    /** 0..100: bajarilgan qadamlar ulushi (vazn bilan). */
    fun progress(context: Context, prefs: Prefs): Int {
        val total = items.sumOf { it.weight }
        val done = items.filter { isDone(context, it, prefs) }.sumOf { it.weight }
        return if (total == 0) 0 else done * 100 / total
    }
}
