package uz.sergak.core

/**
 * Tashqi tahdid ma'lumotlari (VirusTotal, MalwareBazaar, URLhaus, Google Safe Browsing / Web Risk)
 * va telefon ichidagi ma'lum zararli fayllar ro'yxati (IOC) bilan ishlash.
 *
 * Bu modul faqat ma'lumot modellari va ularni [Finding]ga aylantirish qoidalarini saqlaydi —
 * tarmoq bilan ishlash Android qatlamida (CloudClient) va serverda.
 */
enum class ReputationStatus { MALICIOUS, SUSPICIOUS, CLEAN, UNKNOWN }

data class Reputation(
    /** sha256 yoki URL */
    val key: String,
    val status: ReputationStatus,
    /** Nechta antivirus "zararli" degan (VirusTotal) */
    val detections: Int = 0,
    /** Nechta antivirus tekshirgan */
    val engines: Int = 0,
    /** Zararli dastur oilasi yoki tahdid turi, masalan "Wonderland", "phishing" */
    val label: String? = null,
    /** Qaysi manbalar xulosa bergan: "VirusTotal", "MalwareBazaar", "URLhaus", "Google Safe Browsing", "Sergak IOC" */
    val sources: List<String> = emptyList(),
)

object ThreatIntel {

    /**
     * Group-IB tomonidan e'lon qilingan O'zbekistondagi SMS-o'g'ri va dropper namunalari (SHA-1).
     * Manba: "Choose Your Fighter: A New Stage in the Evolution of Android SMS Stealers in Uzbekistan", 2025.
     * Server ishga tushgach, yangilangan ro'yxat /v1/feed orqali avtomatik yuklanadi.
     */
    val BUNDLED_SHA1: Map<String, String> = mapOf(
        "db1d14d5246f2c8807c55084b74247dea6465285" to "Wonderland (\"Sud Qarori.PDF (8).apk\")",
        "6f5502b0e2e99d5f9be4e5f9dcf3fa21b48a92e4" to "Wonderland",
        "516943e93a2bd8f7d91dc5d8b130073d60f4fe67" to "Wonderland (\"Sud qarori\")",
        "6ab99f2396f309647f1adabd290f711960d41696" to "Wonderland (\"Siz bilan video\")",
        "3343e72eb3f04244c7ebf464883cb120365e3a4e" to "Wonderland (\"Toyga Taklifnoma\")",
        "916c66810f0c1ffb447ac98c8e707c75aa911b4f" to "MidnightDat (\"SUD CHAQIRUV\")",
        "85605b32c2a61c258709a19a54cb8a2aa59f8f04" to "MidnightDat (\"Toy Video Albom\")",
        "23cb06e1d97b951b36b77b0e7c38ccedb1f9ea2d" to "MidnightDat (\"Toy Video Albom\")",
        "e2ca86efebc6338a37cdcc0f07ea5d1e8eeeb53a" to "MidnightDat",
        "2dd2371efced160743d88ef08905e3eb7aa83531" to "MidnightDat (\"SudIlovasi\")",
        "c8b8415ee68ec26af544d2ec9d5b8e86e6670690" to "RoundRift (\"toydan video\")",
        "d5b1e45cdf941e5e5771b50881e167dea8020b18" to "RoundRift (\"To'ydan video\")",
        "86ee96cd11cf08f37a511fa860c78b12cb237b73" to "RoundRift (\"Kamera yozuvi\")",
        "22b7bc9bdccbdac0ec40cf8cedaf7bce3b313e94" to "RoundRift (\"to'ydan video\")",
        "9374204e405814cd2a0f364272cf1b417a9b0163" to "RoundRift (\"to'ydan video\")",
    )

    /**
     * Telefon ichidagi ro'yxatdan qidiradi. [feed] — serverdan yuklangan qo'shimcha xeshlar
     * (sha256 yoki sha1 -> nomi).
     */
    fun localMatch(sha256: String?, sha1: String?, feed: Map<String, String> = emptyMap()): Reputation? {
        val keys = listOfNotNull(sha256?.lowercase(), sha1?.lowercase())
        for (k in keys) {
            val label = BUNDLED_SHA1[k] ?: feed[k] ?: continue
            return Reputation(sha256 ?: k, ReputationStatus.MALICIOUS, label = label, sources = listOf("Sergak IOC"))
        }
        return null
    }

    /** Fayl/ilova uchun xulosa -> foydalanuvchiga ko'rsatiladigan topilma. */
    fun fileFinding(rep: Reputation): Finding? {
        val src = rep.sources.joinToString(", ").ifEmpty { "tahdid bazasi" }
        val vt = if (rep.engines > 0) " ${rep.engines} ta antivirusdan ${rep.detections} tasi zararli deb topgan." else ""
        val family = rep.label?.let { " Turi: $it." } ?: ""
        return when (rep.status) {
            ReputationStatus.MALICIOUS -> Finding(
                "intel_malicious", "Ma'lum zararli dastur!",
                "Bu fayl $src bazasida virus sifatida qayd etilgan.$vt$family",
                "DARHOL o'chiring. Agar ruxsat bergan bo'lsangiz — internetni o'chiring, bankka qo'ng'iroq qiling va SOS bo'limidagi qadamlarni bajaring.",
                100,
            )
            ReputationStatus.SUSPICIOUS -> Finding(
                "intel_suspicious", "Antiviruslar shubha bildirgan",
                "Bu fayl $src bazasida shubhali deb belgilangan.$vt$family",
                "Ilovani tanimasangiz — o'chiring.",
                40,
            )
            ReputationStatus.CLEAN -> Finding(
                "intel_clean", "Antivirus bazalarida zararli deb topilmagan",
                "Tekshirildi: $src.$vt",
                "Bu kafolat emas: yangi viruslar hali bazalarda bo'lmasligi mumkin.",
                0,
            )
            ReputationStatus.UNKNOWN -> null
        }
    }

    /** Havola uchun xulosa -> topilma. Google manbasi bo'lsa, ularning talabiga ko'ra "ehtimol" so'zi va manba ko'rsatiladi. */
    fun urlFinding(rep: Reputation): Finding? {
        val google = rep.sources.any { it.startsWith("Google") }
        val src = rep.sources.joinToString(", ")
        val attribution = if (google) " Ogohlantirish Google tomonidan taqdim etilgan (Google Safe Browsing / Web Risk)." else ""
        val what = rep.label?.let { " Tahdid turi: ${labelUz(it)}." } ?: ""
        return when (rep.status) {
            ReputationStatus.MALICIOUS -> Finding(
                "intel_url_malicious", "Xavfli sayt (ehtimol fishing yoki virus tarqatadi)",
                "Havola $src bazasida xavfli deb qayd etilgan.$what$attribution",
                "Ochmang va hech qanday ma'lumot kiritmang.",
                90,
            )
            ReputationStatus.SUSPICIOUS -> Finding(
                "intel_url_suspicious", "Shubhali sayt",
                "Ba'zi xavfsizlik bazalari bu havolani shubhali deb belgilagan ($src).$what$attribution",
                "Ehtiyot bo'ling, kod va karta ma'lumotini kiritmang.",
                35,
            )
            ReputationStatus.CLEAN -> Finding(
                "intel_url_clean", "Xavfsizlik bazalarida xavfli deb topilmagan",
                "Tekshirildi: $src.",
                "Yangi ochilgan fishing saytlar hali bazalarda bo'lmasligi mumkin.",
                0,
            )
            ReputationStatus.UNKNOWN -> null
        }
    }

    private fun labelUz(l: String): String = when (l.lowercase()) {
        "social_engineering", "phishing" -> "fishing (aldov sahifasi)"
        "malware", "malware_download" -> "virus tarqatish"
        "unwanted_software" -> "keraksiz/zararli dastur"
        "potentially_harmful_application" -> "zararli ilova"
        else -> l
    }
}

/**
 * Mashhur ilovalar qiyofasidagi soxta ilovalarni aniqlaydi.
 * Dropperlar o'zini "Google Play", bank yoki Click/Payme, yoki "video", "sud qarori" qilib ko'rsatadi.
 * Haqiqiy versiyalari do'kondan o'rnatiladi — shuning uchun brend nomi + do'kondan tashqari o'rnatish = soxta.
 */
object FakeAppDetector {

    private val BRANDS = listOf(
        "telegram", "click", "payme", "uzum", "paynet", "humo", "uzcard", "kapitalbank", "kapital bank",
        "ipak yo'li", "ipak yuli", "ipakyuli", "hamkor", "agrobank", "xalq banki", "aloqabank", "asakabank",
        "anorbank", "tbc", "octo", "nbu", "milliy bank", "my.gov", "mygov", "my gov", "egov", "soliq",
        "whatsapp", "instagram", "youtube", "google", "play market", "playmarket", "play store", "chrome",
        "beeline", "ucell", "uzmobile", "mobiuz", "olx",
    )

    private val UPDATE_WORDS = listOf(
        "update", "yangilan", "obnovlen", "system", "tizim", "xavfsizlik", "security", "antivirus", "cleaner",
    )

    private val FILE_LURES = Regex(
        """\.(pdf|mp4|jpe?g|png|docx?|mov|avi)\b|\bvideo\b|\bfoto\b|\brasm\b|\bsud\b|chaqiruv|qaror|taklifnoma|to'?y\w*|albom|kamera|yozuv|hujjat|povestk|svadb""",
        RegexOption.IGNORE_CASE,
    )

    fun check(label: String, packageName: String, sideloaded: Boolean): Finding? {
        if (!sideloaded) return null
        val l = Normalizer.normalize(label)
        if (FILE_LURES.containsMatchIn(l)) {
            return Finding(
                "fake_file_app", "Ilova o'zini fayl qilib ko'rsatmoqda: \"$label\"",
                "Ilova nomi video, rasm yoki hujjatga o'xshatilgan. Bu — Telegram orqali tarqatiladigan SMS-o'g'ri viruslarning aniq belgisi.",
                "Darhol o'chiring.", 60,
            )
        }
        val brand = BRANDS.firstOrNull { b -> Regex("""(^|[^a-z])${Regex.escape(b)}([^a-z]|$)""").containsMatchIn(l) }
        if (brand != null) {
            return Finding(
                "fake_brand_app", "Soxta \"$label\" bo'lishi mumkin",
                "Ilova nomi mashhur xizmatnikiga o'xshaydi, lekin rasmiy do'kondan o'rnatilmagan ($packageName). Haqiqiy ${brand.replaceFirstChar { it.uppercase() }} ilovasi faqat Google Play / AppGallery orqali tarqatiladi.",
                "O'chiring va rasmiy ilovani Google Play'dan qayta o'rnating.", 50,
            )
        }
        if (UPDATE_WORDS.any { l.contains(it) }) {
            return Finding(
                "fake_update_app", "\"Yangilanish\" yoki \"xavfsizlik\" niqobidagi ilova",
                "Dropper-viruslar o'zini tizim yangilanishi qilib ko'rsatadi va ichidagi asosiy virusni o'rnatadi.",
                "Tizim yangilanishlari Sozlamalar orqali keladi, alohida APK orqali emas. O'chiring.", 35,
            )
        }
        return null
    }
}
