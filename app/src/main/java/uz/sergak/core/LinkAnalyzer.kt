package uz.sergak.core

/**
 * Havolani (URL) internetga chiqmasdan, faqat telefon ichida tekshiradi.
 * O'zbekistonda keng tarqalgan fishing usullariga moslangan:
 *  - rasmiy saytlarga o'xshatilgan domenlar (my-gov-uz.online, c1ick.uz, paymee.top ...)
 *  - "kompensatsiya", "ovoz bering", "Telegram Premium sovg'a" sahifalari
 *  - .apk yuklab beruvchi havolalar
 *  - qisqartirilgan havolalar, IP manzillar, punycode domenlar
 */
object LinkAnalyzer {

    data class ParsedUrl(
        val raw: String,
        val scheme: String?,
        val userInfo: String?,
        val host: String,
        val path: String,
    )

    /** Rasmiy (ishonchli) domenlar. Subdomenlar ham qabul qilinadi: my.gov.uz -> gov.uz */
    val OFFICIAL_DOMAINS = setOf(
        // davlat
        "gov.uz", "egov.uz", "soliq.uz", "cbu.uz", "iiv.uz", "csec.uz", "lex.uz", "uzex.uz",
        // to'lov tizimlari
        "click.uz", "payme.uz", "paynet.uz", "uzcard.uz", "humocard.uz", "humo.uz",
        "uzum.uz", "uzumbank.uz", "octobank.uz",
        // banklar
        "kapitalbank.uz", "nbu.uz", "ipakyulibank.uz", "hamkorbank.uz", "agrobank.uz",
        "aloqabank.uz", "xb.uz", "asakabank.uz", "sqb.uz", "tbcbank.uz", "anorbank.uz",
        "ofb.uz", "infinbank.com", "davrbank.uz", "trustbank.uz", "turonbank.uz",
        "mkbank.uz", "garantbank.uz", "brb.uz", "aab.uz", "ziraatbank.uz",
        // aloqa
        "ucell.uz", "beeline.uz", "mobi.uz", "uztelecom.uz", "uzmobile.uz",
        // messenjer va platformalar
        "telegram.org", "t.me", "telegram.me", "telegra.ph", "google.com", "youtube.com",
        "youtu.be", "instagram.com", "facebook.com", "apple.com", "olx.uz", "microsoft.com",
        // yangiliklar
        "kun.uz", "gazeta.uz", "daryo.uz", "uzdaily.uz", "spot.uz", "uza.uz", "podrobno.uz",
    )

    /** Brend nomi -> uning rasmiy domeni. Domen ichida brend nomi bo'lsa-yu, domen rasmiy bo'lmasa — soxta. */
    private val BRANDS = mapOf(
        "mygov" to "my.gov.uz", "egov" to "egov.uz", "soliq" to "soliq.uz",
        "click" to "click.uz", "payme" to "payme.uz", "paynet" to "paynet.uz",
        "uzcard" to "uzcard.uz", "humo" to "humocard.uz", "uzum" to "uzum.uz",
        "telegram" to "telegram.org", "kapitalbank" to "kapitalbank.uz",
        "ipakyuli" to "ipakyulibank.uz", "hamkorbank" to "hamkorbank.uz",
        "agrobank" to "agrobank.uz", "aloqabank" to "aloqabank.uz", "asakabank" to "asakabank.uz",
        "anorbank" to "anorbank.uz", "markaziybank" to "cbu.uz", "centralbank" to "cbu.uz",
        "olx" to "olx.uz", "beeline" to "beeline.uz", "ucell" to "ucell.uz", "uzmobile" to "uzmobile.uz",
    )

    /** O'xshatishni tekshirish uchun brendlarning domen yorliqlari. */
    private val LOOKALIKE_TARGETS = listOf(
        "click", "payme", "paynet", "uzcard", "humocard", "uzumbank", "telegram",
        "soliq", "kapitalbank", "ipakyulibank", "hamkorbank", "agrobank", "aloqabank",
        "asakabank", "anorbank", "octobank",
    )

    private val SHORTENERS = setOf(
        "bit.ly", "tinyurl.com", "cutt.ly", "clck.ru", "is.gd", "goo.su", "t.ly", "rebrand.ly",
        "shorturl.at", "u.to", "v.gd", "ow.ly", "rb.gy", "tiny.cc", "s.id", "qr.ae", "vk.cc",
        "bitly.com", "short.link", "lnkd.in", "surl.li", "gg.gg",
    )

    private val RISKY_TLDS = setOf(
        "top", "xyz", "site", "online", "icu", "buzz", "shop", "live", "fun", "cfd", "sbs",
        "rest", "monster", "tk", "ml", "ga", "cf", "gq", "work", "vip", "pw", "cc", "click",
        "link", "store", "space", "website", "pro", "lol", "cyou", "bond", "today", "win",
        "loan", "support", "help", "best", "art", "uno",
    )

    /** Domen yoki manzil ichida uchrasa, shubha oshadi. */
    private val LURE_WORDS = listOf(
        "kompensats", "kompensac", "kompensaciy", "nafaqa", "subsid", "bonus", "yutuq", "priz",
        "prize", "premium", "gift", "sovga", "sovg'a", "vote", "ovoz", "golos", "tanlov", "konkurs",
        "kredit", "credit", "verify", "verif", "secure", "login", "signin", "auth", "support",
        "account", "wallet", "reward", "free", "aksiya", "akciya", "promo", "lottery", "lotereya",
        "tolov", "to-lov", "payment", "refund", "vozvrat", "qaytar", "sud", "jarima", "shtraf",
    )

    /** Havolalarni matn ichidan topish uchun keng tarqalgan domen zonalari. */
    private val KNOWN_TLDS = setOf(
        "uz", "com", "net", "org", "ru", "me", "io", "info", "biz", "co", "app", "dev", "su", "kz",
        "tj", "kg", "ua", "by", "tr", "ph", "ly", "gl", "to", "gd", "at", "id", "ae",
    ) + RISKY_TLDS

    private val TWO_LEVEL_SUFFIXES = setOf("gov.uz", "com.uz", "org.uz", "net.uz", "edu.uz", "co.uk", "com.tr", "com.ru")

    private val URL_REGEX = Regex(
        """(?<![@\w])((?:https?|hxxps?)://[^\s<>"'«»]+|(?:[a-z0-9Ѐ-ӿ](?:[a-z0-9Ѐ-ӿ-]{0,61}[a-z0-9Ѐ-ӿ])?\.)+[a-zЀ-ӿ]{2,24}(?:/[^\s<>"'«»]*)?)""",
        RegexOption.IGNORE_CASE,
    )

    /** Matndagi barcha havolalarni qaytaradi (sxemasiz yozilgan "my-gov.uz/..." ham). */
    fun extractUrls(text: String): List<String> {
        return URL_REGEX.findAll(text).map { it.value.trimEnd('.', ',', ')', '!', '?', ';', ':') }
            .filter { candidate ->
                if (candidate.contains("://")) return@filter true
                val host = candidate.substringBefore('/').lowercase()
                val tld = host.substringAfterLast('.')
                tld in KNOWN_TLDS && !isLikelyFileName(host)
            }
            .distinct()
            .toList()
    }

    private fun isLikelyFileName(host: String): Boolean {
        val tld = host.substringAfterLast('.')
        return tld in setOf("apk", "pdf", "mp4", "jpg", "jpeg", "png", "doc", "docx", "zip", "rar", "exe")
    }

    fun parse(input: String): ParsedUrl? {
        var s = input.trim().trim('<', '>', '"', '\'', '«', '»')
        if (s.isEmpty() || s.contains(' ')) return null
        s = s.replaceFirst(Regex("^hxxp", RegexOption.IGNORE_CASE), "http")
        val schemeIdx = s.indexOf("://")
        val scheme = if (schemeIdx > 0) s.substring(0, schemeIdx).lowercase() else null
        val rest = if (schemeIdx > 0) s.substring(schemeIdx + 3) else s
        val authEnd = rest.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, authEnd)
        val path = rest.substring(authEnd)
        val at = authority.lastIndexOf('@')
        val userInfo = if (at >= 0) authority.substring(0, at) else null
        var host = (if (at >= 0) authority.substring(at + 1) else authority).lowercase()
        if (host.startsWith("[")) return ParsedUrl(input, scheme, userInfo, host, path) // IPv6
        host = host.substringBefore(':').trimEnd('.')
        if (host.isEmpty() || !host.contains('.')) return null
        return ParsedUrl(input, scheme, userInfo, host, path)
    }

    fun isOfficial(host: String): Boolean =
        OFFICIAL_DOMAINS.any { host == it || host.endsWith(".$it") }

    fun registrableDomain(host: String): String {
        val labels = host.split('.')
        if (labels.size <= 2) return host
        val lastTwo = labels.takeLast(2).joinToString(".")
        return if (lastTwo in TWO_LEVEL_SUFFIXES) labels.takeLast(3).joinToString(".") else lastTwo
    }

    fun analyze(input: String): Verdict {
        val url = parse(input) ?: return Verdict.of(
            listOf(Finding("not_url", "Bu havola emas", "Kiritilgan matn havola (URL) ko'rinishida emas.", "Havolani to'liq nusxalab qo'ying, masalan: https://my.gov.uz", 0))
        )
        val f = mutableListOf<Finding>()
        val host = url.host
        val path = url.path.lowercase()
        val pathDecoded = percentDecode(path)

        // .apk yuklab berish — eng xavfli holat
        if (Regex("""\.apk(\b|$|[?#])""").containsMatchIn(pathDecoded) || host.endsWith(".apk")) {
            f += Finding(
                "apk_link", "Havola APK fayl yuklab beradi",
                "APK — Android ilova fayli. O'zbekistonda pul o'g'irlashning eng ko'p usuli aynan shunday fayllar orqali SMS-kodlarni o'g'irlovchi viruslarni o'rnatish.",
                "Yuklab olmang va o'rnatmang. Ilovalarni faqat Google Play orqali o'rnating.", 70,
            )
        }

        if (isOfficial(host)) {
            f += Finding(
                "official", "Rasmiy domen: ${registrableDomain(host)}",
                "Domen ma'lum rasmiy saytlar ro'yxatida bor.",
                "Shunda ham SMS-kod, karta muddati yoki CVV kodni hech kimga bermang.", 0,
            )
            if (url.scheme == "http") {
                f += Finding("no_https", "Shifrlanmagan ulanish (http)", "Sahifa https emas, ma'lumot ochiq uzatiladi.", "Manzilni https:// bilan oching.", 5)
            }
            if (host == "t.me" || host == "telegram.me" || host.endsWith(".t.me")) {
                f += analyzeTelegramLink(pathDecoded)
            }
            return Verdict.of(f)
        }

        // IP manzil
        if (Regex("""^\d{1,3}(\.\d{1,3}){3}$""").matches(host) || host.startsWith("[")) {
            f += Finding("ip_host", "Domen o'rniga IP manzil", "Rasmiy tashkilotlar saytlari raqamli IP manzilda joylashmaydi.", "Bunday havolaga kirmang.", 40)
        }

        // Punycode / kirill harflar bilan yasalgan o'xshash domen
        if (host.split('.').any { it.startsWith("xn--") } || host.any { it.code > 127 }) {
            f += Finding(
                "idn", "Domenda boshqa alifbo harflari bor",
                "Firibgarlar lotin harfiga o'xshash kirill harflaridan (masalan, 'а' va 'a') foydalanib, rasmiy saytga juda o'xshash manzil yasaydi.",
                "Manzilni qo'lda tering yoki rasmiy ilovadan foydalaning.", 50,
            )
        }

        // URL ichida login@host hiylasi
        if (url.userInfo != null) {
            f += Finding(
                "userinfo", "Havolada '@' hiylasi",
                "\"https://my.gov.uz@soxta-sayt.com\" ko'rinishidagi havola aslida '@' belgisidan keyingi saytni ochadi.",
                "Bunday havolani ochmang.", 50,
            )
        }

        // Brend nomi soxta domenda
        val hostNorm = Normalizer.deLeet(host.replace("-", "").replace("_", ""))
        val brandHit = findBrand(host)
        val govHit = Regex("""(^|[.\-])(my)?gov([.\-]?uz)?([.\-]|$)""").containsMatchIn(host) ||
            hostNorm.contains("govuz") || hostNorm.contains("mygov")
        if (brandHit != null || govHit) {
            val official = brandHit?.value ?: "gov.uz"
            f += Finding(
                "brand_impersonation", "Rasmiy nomga o'xshatilgan soxta domen",
                "Manzilda \"${brandHit?.key ?: "gov"}\" so'zi bor, lekin bu rasmiy $official sayti EMAS. Bunday saytlar karta ma'lumoti va SMS-kodni o'g'irlash uchun yasaladi.",
                "Sahifaga hech narsa kiritmang. Rasmiy saytga faqat $official manzili orqali kiring.", 65,
            )
        } else {
            // O'xshash yozilgan domen: paymee, c1ick, teiegram, uzcrad ...
            val sld = registrableDomain(host).substringBefore('.')
            val sldNorm = Normalizer.deLeet(sld)
            val target = LOOKALIKE_TARGETS.firstOrNull { t ->
                val d = levenshtein(sldNorm, t)
                (d in 1..(if (t.length >= 6) 2 else 1)) || (sld != t && sldNorm == t)
            }
            if (target != null) {
                f += Finding(
                    "lookalike", "Rasmiy saytga juda o'xshash domen",
                    "\"$sld\" — \"$target\" so'zini eslatadi, lekin bir-ikki harfi o'zgartirilgan. Bu fishing saytlarining klassik usuli.",
                    "Sahifaga hech narsa kiritmang.", 60,
                )
            }
        }

        if (registrableDomain(host) in SHORTENERS) {
            f += Finding(
                "shortener", "Qisqartirilgan havola",
                "Haqiqiy manzil yashirilgan — qayerga olib borishini oldindan bilib bo'lmaydi.",
                "Notanish odamdan kelgan qisqa havolani ochmang.", 25,
            )
        }

        val tld = host.substringAfterLast('.')
        if (tld in RISKY_TLDS) {
            f += Finding(
                "risky_tld", "Shubhali domen zonasi: .$tld",
                "Bu zonadagi domenlar juda arzon va firibgarlar tomonidan ko'p ishlatiladi.",
                "Bank, to'lov yoki davlat xizmati bunday domenda bo'lmaydi.", 20,
            )
        }

        val lureInHost = LURE_WORDS.firstOrNull { host.contains(it) }
        val lureInPath = LURE_WORDS.firstOrNull { pathDecoded.contains(it) }
        if (lureInHost != null) {
            f += Finding(
                "lure_host", "Domenda jalb qiluvchi so'z: \"$lureInHost\"",
                "Kompensatsiya, bonus, sovg'a, ovoz berish kabi so'zlar fishing saytlarida ko'p uchraydi.",
                "Pul yoki sovg'a va'da qilgan sahifaga karta ma'lumotini kiritmang.", 25,
            )
        } else if (lureInPath != null) {
            f += Finding(
                "lure_path", "Manzilda jalb qiluvchi so'z: \"$lureInPath\"",
                "Firibgarlar sahifa nomini shunday qo'yib, ishonch uyg'otishga harakat qiladi.",
                "Sahifada SMS-kod yoki karta ma'lumoti so'ralsa — darhol yoping.", 10,
            )
        }

        if (url.scheme == "http") {
            f += Finding("no_https", "Shifrlanmagan ulanish (http)", "Kiritilgan ma'lumot ochiq holda uzatiladi.", "Bunday sahifaga parol yoki karta kiritmang.", 10)
        }
        if (host.count { it == '-' } >= 3) {
            f += Finding("many_hyphens", "Domenda juda ko'p chiziqcha", "\"my-gov-uz-tolov-kompensatsiya.com\" kabi uzun manzillar odatda soxta bo'ladi.", "Ehtiyot bo'ling.", 10)
        }
        if (host.split('.').size >= 5) {
            f += Finding("deep_subdomain", "Juda ko'p qismli manzil", "\"gov.uz.tolov.site\" kabi manzilda haqiqiy domen oxirgi qismdir (tolov.site).", "Manzilning oxiriga qarang.", 15)
        }

        return Verdict.of(f)
    }

    /**
     * Domen qismlarida brend nomini qidiradi. "clickup.com", "humor.net" kabi begona
     * so'zlarni ushlamaslik uchun brend alohida qism bo'lishi yoki "uz", raqam,
     * yoki jalb qiluvchi so'z bilan qo'shilgan bo'lishi kerak.
     */
    private fun findBrand(host: String): Map.Entry<String, String>? {
        val parts = host.split('.', '-', '_').filter { it.isNotEmpty() }
        for (entry in BRANDS.entries) {
            val token = entry.key
            for (raw in parts) {
                for (p in setOf(raw, Normalizer.deLeet(raw))) {
                    if (p == token) return entry
                    if (token.length >= 6 && p.contains(token)) return entry
                    if (p.startsWith(token)) {
                        val rest = raw.drop(token.length).ifEmpty { p.drop(token.length) }
                        if (rest.all { it.isDigit() } || rest.startsWith("uz") ||
                            LURE_WORDS.any { rest.contains(it) }
                        ) return entry
                    }
                    if (p.endsWith(token)) {
                        val prefix = p.dropLast(token.length)
                        if (prefix in setOf("my", "uz", "www", "new", "id", "pay", "get", "official", "rasmiy")) return entry
                    }
                }
            }
        }
        return null
    }

    private fun analyzeTelegramLink(path: String): List<Finding> {
        val name = path.trimStart('/').substringBefore('/').substringBefore('?')
        if (name.isEmpty() || name.startsWith("+") || name == "joinchat") return emptyList()
        val n = Normalizer.deLeet(name.lowercase())
        val lure = LURE_WORDS.firstOrNull { n.contains(it) }
        val brand = BRANDS.keys.firstOrNull { n.contains(it) }
        val isBot = n.endsWith("bot")
        val out = mutableListOf<Finding>()
        if (isBot && (lure != null || brand != null)) {
            out += Finding(
                "tg_fake_bot", "Shubhali Telegram bot: @$name",
                "Bot nomida ${listOfNotNull(brand, lure).joinToString(", ") { "\"$it\"" }} bor. Firibgarlar rasmiy xizmat yoki \"sovg'a/kompensatsiya\" boti qiyofasida telefon raqami, SMS-kod va karta ma'lumotini so'raydi.",
                "Botga telefon raqamingizni ulashmang, kod va karta ma'lumotini kiritmang.", 40,
            )
        } else if (lure != null) {
            out += Finding(
                "tg_lure", "Telegram manzilida jalb qiluvchi so'z: \"$lure\"",
                "Sovg'a, premium, ovoz berish va'da qilingan kanal yoki botlar ko'pincha akkauntni o'g'irlash uchun ishlatiladi.",
                "Telegram kodini hech qayerga kiritmang.", 25,
            )
        }
        return out
    }

    private fun percentDecode(s: String): String = try {
        java.net.URLDecoder.decode(s.replace("+", "%2B"), "UTF-8").lowercase()
    } catch (e: Exception) {
        s
    }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
