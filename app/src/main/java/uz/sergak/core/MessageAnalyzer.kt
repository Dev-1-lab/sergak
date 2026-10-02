package uz.sergak.core

/**
 * Xabar (Telegram, SMS, WhatsApp ...) matnini firibgarlik belgilariga tekshiradi.
 * Hammasi oflayn ishlaydi: matn hech qayerga yuborilmaydi va saqlanmaydi.
 *
 * Qoidalar O'zbekistondagi real holatlar asosida tuzilgan (IIV Kiberxavfsizlik markazi,
 * Group-IB, kun.uz ma'lumotlari): "Sud qarori.apk", "To'y video.apk", soxta kompensatsiya,
 * "tanlovda ovoz bering", "bank xodimi" qo'ng'iroqlari, buzilgan do'st akkauntidan pul so'rash va h.k.
 *
 * Matn avval [Normalizer] orqali lotinga o'giriladi, shuning uchun o'zbek kirill va rus tilidagi
 * xabarlar ham bir xil qoidalar bilan tekshiriladi.
 */
object MessageAnalyzer {

    enum class OtpKind { TELEGRAM_LOGIN, SERVICE_CODE }

    data class Report(
        val verdict: Verdict,
        val urls: List<String>,
        val otp: OtpKind?,
    )

    private class Rule(
        val id: String,
        val pattern: Regex,
        val weight: Int,
        val title: String,
        val explanation: String,
        val advice: String,
    )

    private fun r(p: String) = Regex(p, setOf(RegexOption.IGNORE_CASE))

    // "-ma"/"-mang" (inkor) shakllarini chiqarib tashlaymiz: "kodni hech kimga aytmang" xavfsiz maslahat.
    private const val GIVE_VERBS =
        """(yubor(?!ma)|ayt(?!ma)|ayting|bering|berib|berasiz|kirit(?!ma)|jo'?nat(?!ma)|tashla(?!ma)|yoz(?!ma)|ko'rsat(?!ma)|skin|otprav|soobsh|prodikt|nazov|vvedi|vvesti|diktu|send\b|enter\b|share\b|tell\b)"""

    private val rules = listOf(
        Rule(
            "code_request",
            r("""(\bkod\w*|\bparol\w*|\bsms\w*|\bcode\b|password)\W+(\S+\W+){0,4}?$GIVE_VERBS|$GIVE_VERBS\w*\W+(\S+\W+){0,3}?(\bkod\w*|\bparol\w*|\bcode\b|sms\s*kod)"""),
            50,
            "SMS-kod yoki parol so'ralmoqda",
            "Telegram, bank, Click, Payme yoki davlat xodimi HECH QACHON sizdan SMS-kod yoki parol so'ramaydi. Kodni bergan zahotingiz akkauntingiz yoki kartangiz boshqa odam qo'liga o'tadi.",
            "Kodni hech kimga bermang va hech qayerga kiritmang. Suhbatni to'xtating.",
        ),
        Rule(
            "card_data",
            r("""kart\w*\s*(raqam|nomer|ma'lumot|muddat|orqa)|amal qilish muddat|\bcvv2?\b|\bcvc\b|srok deystviya|nomer kart|danni\w* kart|rekvizit|16 xonali|karta ma'lumot"""),
            40,
            "Karta ma'lumotlari so'ralmoqda",
            "Karta raqami, amal qilish muddati va SMS-kod birga qo'lga tushsa, firibgar kartangizdan pul yecha oladi.",
            "Pul QABUL QILISH uchun faqat karta raqami yetarli. Muddat, CVV va SMS-kodni hech kimga bermang.",
        ),
        Rule(
            "apk_file",
            r("""[^\s/\\]{1,80}\.apk\b"""),
            70,
            "APK fayl (Android ilova) yuborilgan",
            "O'zbekistondagi kiberjinoyatlarning eng katta qismi aynan APK fayllar bilan bog'liq. Bunday fayl o'rnatilsa, SMS-kodlaringizni o'qib, kartangizdan pul yechadi va do'stlaringizga sizning nomingizdan virus tarqatadi.",
            "Faylni OCHMANG. Do'stingiz yuborgan bo'lsa ham — uning akkaunti buzilgan bo'lishi mumkin. Unga telefon orqali qo'ng'iroq qilib xabar bering.",
        ),
        Rule(
            "double_extension",
            r("""\.(pdf|mp4|jpe?g|png|docx?|mov|avi|xlsx?)\s*(\(\d+\))?\s*\.apk\b|(pdf|video|foto|rasm)\s*\(\d+\)\.apk"""),
            15,
            "Fayl o'zini rasm/video/hujjat qilib ko'rsatmoqda",
            "\"video.mp4.apk\" yoki \"Sud qarori.PDF (8).apk\" kabi nom — hujjat emas, ilova. Bu ataylab aldash.",
            "Haqiqiy rasm va videolar .apk bilan tugamaydi.",
        ),
        Rule(
            "lure_court",
            r("""sud\w*\s*(qaror|chaqiruv|chaqiril|ijro|ishi|majlis)|sudga chaqir|chaqiruv qog'oz|povestk|sudebn|ijro varaqa|jarima\w*\s*to'la|shtraf|\bmib\b"""),
            25,
            "\"Sud\", \"jarima\" bilan qo'rqitish",
            "\"Sudga chaqirildingiz\" degan fayl yoki havola — mashhur tuzoq. Sud chaqiruvlari Telegram orqali fayl qilib yuborilmaydi.",
            "Rasmiy ma'lumotni faqat my.gov.uz yoki sud.uz saytidan o'zingiz tekshiring.",
        ),
        Rule(
            "lure_media",
            r("""to'?y\w*\s*(taklifnoma|video|albom|rasm)|to'?ydan\s*(video|rasm)|taklifnoma|video\s*albom|siz bilan video|kamera\s*yozuv|bu sizmisiz|sizmisiz|rasmingiz|videongiz|eto ti\b|eto vi\b|eto tvoe|tvoe foto|svadb|priglashen|razdevator|tashkentgirls|ochiq video"""),
            20,
            "Qiziqtiruvchi tuzoq (to'y, video, \"bu sizmisiz?\")",
            "Firibgarlar to'y taklifnomasi, \"siz bilan video\", \"kamera yozuvi\" kabi qiziqtiruvchi nomlar bilan zararli fayl yoki havolani ochtirishga urinadi.",
            "Taklifnoma yoki videoni yuborgan odamdan telefon orqali so'rang.",
        ),
        Rule(
            "lure_prize",
            r("""kompensats|kompensac|nafaqa|subsid|davlat\s*(to'lov|yordam)|pul mukofot|yutuq|yutib oldingiz|g'olib bo'ldingiz|sovg'?a|\bbonus|viigrish|\bpriz\b|podarok|vi?plat|\b\d{1,3}[ .]?\d{3}[ .]?\d{3}\s*(so'm|sum|som)\b"""),
            20,
            "Pul, sovg'a yoki kompensatsiya va'dasi",
            "\"Davlat kompensatsiyasi 30 250 000 so'm\", \"yutuq\", \"sovg'a\" — karta ma'lumotini olish uchun eng ko'p ishlatiladigan yolg'on. Davlat to'lovlari Telegram havolasi orqali berilmaydi.",
            "Pul olish uchun hech qachon SMS-kod yoki karta muddati kerak emas.",
        ),
        Rule(
            "lure_vote",
            r("""ovoz\s*(ber|tashla|qo'y)|tanlovda|golosu|progolos|\bvote\b"""),
            25,
            "\"Tanlovda ovoz bering\" tuzog'i",
            "Havola ochilib, \"ovozni tasdiqlash\" uchun Telegram orqali kirish so'raladi. Kiritilgan kod bilan firibgar akkauntingizni egallaydi va tanishlaringizdan pul so'raydi.",
            "Ovoz berish uchun Telegram kodi yoki telefon raqami kerak emas.",
        ),
        Rule(
            "lure_loan",
            r("""kredit\w*\s*(tasdiq|ma'qul|odobr|berildi|ajrat)|\bzaym|qarz beramiz|foizsiz kredit|tez kredit|onlayn kredit|mikroqarz|kredit bot"""),
            20,
            "Kredit va'dasi",
            "Telegram botlardagi \"arzon kredit\" takliflari pasport va karta ma'lumotini yig'adi, keyin \"komissiya\" deb pul so'raydi.",
            "Kreditni faqat bankning rasmiy ilovasi yoki filialida rasmiylashtiring.",
        ),
        Rule(
            "prepayment",
            r("""komissiya|sug'urta\s*to'lov|oldindan to'lov|predoplat|strahov\w* vznos|aktivatsiya to'lov"""),
            20,
            "Oldindan to'lov / \"komissiya\" so'ralmoqda",
            "Pul berishdan oldin \"komissiya\", \"sug'urta\" yoki \"aktivatsiya\" uchun to'lov so'rash — firibgarlikning aniq belgisi.",
            "Hech narsa to'lamang.",
        ),
        Rule(
            "impersonation",
            r("""bank\w*\s*xodim|bankdan\s*(qo'ng'iroq|bezovta|yozyap)|xavfsizlik xizmat|markaziy bank|iiv xodim|ichki ishlar|militsiya|politsiya|prokuratura|sotrudnik bank|slujb\w* bezopasnost|tsentraln\w* bank|centraln\w* bank|\bmvd\b|\bdxx\b|telegram\s*(xizmat|administrats|jamoas|support|podderjk)|qo'llab-quvvatlash xizmati"""),
            25,
            "Rasmiy tashkilot nomidan gapirish",
            "Firibgarlar o'zini bank, Markaziy bank, IIV yoki Telegram xodimi deb tanishtirib, \"hisobingizdan kredit olinmoqda\" deb qo'rqitadi.",
            "Qo'ng'iroqni tugating va bankka kartangiz orqasidagi raqam orqali O'ZINGIZ qo'ng'iroq qiling.",
        ),
        Rule(
            "urgency",
            r("""tezda|zudlik bilan|shoshiling|hoziroq|darhol|bugun oxirgi|\b\d+\s*(soat|daqiqa)\s*ichida|srochno|seychas je|nemedlenno|bloklan|blokirov|to'xtatil|muzlatil|o'chirib qo'yiladi|akkaunt\w*\s*(o'chiril|bloklan)"""),
            15,
            "Shoshiltirish va qo'rqitish",
            "\"Hoziroq\", \"24 soat ichida\", \"kartangiz bloklanadi\" — o'ylashga vaqt bermaslik uchun ishlatiladigan bosim.",
            "Shoshilmang. Haqiqiy tashkilotlar sizga o'ylash va tekshirish uchun vaqt beradi.",
        ),
        Rule(
            "money_request",
            r("""pul\w*\s*(tashlab|o'tkazib|jo'natib|yuborib|tashla)|qarz\w*\s*(berib tur|bera olasan|bera olasiz)|kartamga\s*(tashla|o'tkaz)|(shu|bu) kartaga|perekin|skin\w*\s*(deng|pul)|odolj|v dolg|perevedi|pul kerak edi|zudlik bilan pul"""),
            35,
            "Pul o'tkazish so'ralmoqda",
            "Akkaunti buzilgan do'st yoki qarindosh nomidan \"tezda pul tashlab tur\" deb yozish — juda keng tarqalgan usul. Firibgar yozishmalarni o'qib, sizga tanish ohangda yozadi.",
            "Pul o'tkazishdan oldin shu odamga ODDIY TELEFON QO'NG'IROG'I qilib tasdiqlang.",
        ),
        Rule(
            "card_number",
            r("""\b(8600|9860|5614|6262|4\d{3}|5\d{3})[ \-]?\d{4}[ \-]?\d{4}[ \-]?\d{4}\b"""),
            10,
            "Xabarda karta raqami bor",
            "Bu pul o'tkazish so'rovi bo'lishi mumkin.",
            "Kartaga pul tashlashdan oldin egasi bilan ovozli qo'ng'iroq orqali gaplashing.",
        ),
        Rule(
            "remote_access",
            r("""anydesk|any desk|rustdesk|teamviewer|quick ?support|airdroid|ekran\w*\s*(ulash|ko'rsat|namoyish|demonstr)|demonstrats\w* ekran|ekrangizni"""),
            45,
            "Telefoningizni masofadan boshqarish ilovasi so'ralmoqda",
            "AnyDesk, RustDesk kabi ilovalar orqali firibgar ekraningizni ko'radi va bank ilovangizni o'zi boshqaradi.",
            "Hech qachon notanish odam uchun bunday ilova o'rnatmang va ekraningizni ulashmang.",
        ),
        Rule(
            "drop_card",
            r("""kart\w*\s*(ijaraga|sotib olamiz|sotasiz|ochib ber)|kartangizni\s*(ijaraga|sot|bizga)|drop\s*kart|\bdropp|prodat\w* kart|arend\w* kart|kartu v arend|karta uchun pul to'laymiz"""),
            35,
            "Kartangizni sotish/ijaraga berish taklifi",
            "Bunday kartalar (\"drop\") o'g'irlangan pullarni yashirish uchun ishlatiladi. Kartani bergan odam ham jinoiy javobgarlikka tortiladi.",
            "Kartangizni hech kimga bermang, sotmang va ijaraga bermang.",
        ),
        Rule(
            "easy_money",
            r("""kunlik\s*\d+\s*%|daromad kafolat|kafolatlangan daromad|garantirovan|passiv daromad|kriptovalyut|\busdt\b|investitsiya qil|pul ishlang|uydan turib ishla|oson pul|legki\w* deng|zarabotok|zarabotat"""),
            20,
            "\"Oson va kafolatlangan daromad\" taklifi",
            "Kafolatlangan yuqori foiz va'da qilinsa, bu deyarli har doim moliyaviy piramida yoki firibgarlik.",
            "Pul tikishdan oldin kompaniyani rasmiy manbalardan tekshiring.",
        ),
        Rule(
            "install_request",
            r("""(ilova|dastur|prilojeni|programm)\w*\s+(\S+\s+){0,3}?(o'rnat|ustanov|yuklab ol|skacha)|o'rnatib (oling|ko'ring)|faylni och|ustanovit"""),
            30,
            "Ilova o'rnatish so'ralmoqda",
            "Firibgarlar \"yangi bank ilovasi\", \"xavfsizlik ilovasi\" yoki \"kuzatuv dasturi\" deb zararli ilova o'rnattiradi.",
            "Ilovani faqat Google Play'dan, o'zingiz qidirib o'rnating.",
        ),
        Rule(
            "premium_gift",
            r("""(telegram\s*)?premium\s*(sovg'?a|bepul|tekin|podarok|besplatn|gift)|(bepul|tekin|besplatn\w*)\s*(telegram\s*)?premium|stars\s*(sovg'?a|bepul)"""),
            30,
            "\"Bepul Telegram Premium\" tuzog'i",
            "Bepul Premium va'da qilgan havola yoki bot Telegram kodingizni olishga urinadi.",
            "Premium sovg'a qilinganda hech qanday kod kiritish kerak emas.",
        ),
        Rule(
            "passport",
            r("""pasport\w*\s*(raqam|seriya|ma'lumot|nusxa|rasm|surat)|\bjshshir|\bpinfl|pasportingiz"""),
            25,
            "Pasport ma'lumotlari so'ralmoqda",
            "Pasport ma'lumotlari bilan nomingizga kredit yoki karta ochilishi mumkin.",
            "Pasport nusxasini notanish odamlarga va botlarga yubormang.",
        ),
    )

    private val digitsCode = r("""(?<!\d)\d{4,8}(?!\d)""")
    private val codeWord = r("""\bkod\w*|\bcode\b|\bparol|tasdiq|podtverj""")
    private val protective = r("""hech kimga|hech kimga aytma|nikomu|do not give|don't give|never share|do not share|don't share|ne soobsh|ne pered|ne govorite""")
    private val telegramLogin = r("""login code|kirish kodi|kod dlya vxoda|kod dlya vhoda|kod vxoda|kod dlya vxod|telegram\s*(kodi|code|kod)""")

    fun analyze(text: String, source: String? = null): Report {
        val norm = Normalizer.normalize(text)
        val findings = mutableListOf<Finding>()

        // Xizmatdan kelgan haqiqiy bir martalik kodmi (OTP)?
        val hasCode = digitsCode.containsMatchIn(norm) && codeWord.containsMatchIn(norm)
        val isProtectiveOtp = hasCode && protective.containsMatchIn(norm)
        val otp = when {
            hasCode && telegramLogin.containsMatchIn(norm) -> OtpKind.TELEGRAM_LOGIN
            isProtectiveOtp -> OtpKind.SERVICE_CODE
            else -> null
        }

        for (rule in rules) {
            if (rule.id == "code_request" && isProtectiveOtp) continue
            if (rule.pattern.containsMatchIn(norm)) {
                findings += Finding(rule.id, rule.title, rule.explanation, rule.advice, rule.weight)
            }
        }

        // Havolalar
        val urls = LinkAnalyzer.extractUrls(text)
        for (u in urls) {
            val lv = LinkAnalyzer.analyze(u)
            val top = lv.findings.firstOrNull { it.weight > 0 } ?: continue
            findings += Finding(
                "link:" + (LinkAnalyzer.parse(u)?.host ?: u),
                "Havola: ${top.title}",
                "$u\n${top.explanation}",
                top.advice,
                lv.score,
            )
        }

        val ids = findings.map { it.id }.toSet()
        val asksSecret = "code_request" in ids || "card_data" in ids
        val hasLure = ids.any { it.startsWith("lure_") || it == "impersonation" || it == "premium_gift" }
        if (asksSecret && (hasLure || urls.isNotEmpty())) {
            findings += Finding(
                "combo_secret_lure", "Tuzoq + maxfiy ma'lumot so'rash",
                "Xabarda ham jalb qiluvchi va'da/qo'rqitish, ham kod yoki karta ma'lumoti so'rovi bor. Bu klassik firibgarlik sxemasi.",
                "Suhbatni to'xtating, hech narsa yubormang.", 20,
            )
        }
        if (ids.any { it.startsWith("lure_") } && urls.isNotEmpty()) {
            findings += Finding(
                "combo_lure_link", "Va'da + havola",
                "Sovg'a, kompensatsiya yoki ovoz berish va'dasi bilan kelgan havola — fishing sahifasiga olib borishi ehtimoli yuqori.",
                "Havolani ochmang.", 15,
            )
        }

        return Report(Verdict.of(findings), urls, otp)
    }
}
