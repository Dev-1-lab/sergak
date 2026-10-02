package uz.sergak.core

data class Lesson(
    val id: String,
    val emoji: String,
    val title: String,
    val summary: String,
    val howItWorks: List<String>,
    val signs: List<String>,
    val whatToDo: List<String>,
)

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correct: Int,
    val explanation: String,
)

data class Contact(
    val name: String,
    val description: String,
    val phone: String? = null,
    val link: String? = null,
)

/**
 * O'quv materiallari. Manbalar: IIV Kiberxavfsizlik markazi ogohlantirishlari,
 * Group-IB "Android SMS stealers in Uzbekistan" (2025), kun.uz, zamin.uz, anhor.uz xabarlari.
 */
object Knowledge {

    val lessons = listOf(
        Lesson(
            "apk", "📦", "APK fayllar: \"Sud qarori\", \"To'y video\"",
            "O'zbekistonda kartadan pul o'g'irlashning 1-raqamli usuli. Do'stingizdan kelgan fayl ham xavfli bo'lishi mumkin.",
            howItWorks = listOf(
                "Firibgar buzilgan Telegram akkauntidan uning barcha kontaktlariga fayl yuboradi: \"Sud qarori.pdf.apk\", \"To'y taklifnoma.apk\", \"Siz bilan video.apk\", \"Kamera yozuvi.apk\".",
                "Fayl ochilganda telefon \"ilovani o'rnatish\"ni so'raydi. O'rnatilgach, ilova SMS o'qish ruxsatini so'raydi va belgisini yashiradi.",
                "Virus bank va Telegram SMS-kodlarini firibgarga yuboradi. U kartangizdan pul yechadi va Telegram akkauntingizga kirib, endi sizning nomingizdan yangi qurbonlarga fayl tarqatadi.",
            ),
            signs = listOf(
                "Fayl nomi .apk bilan tugaydi (hatto \"video.mp4.apk\" bo'lsa ham)",
                "Telefon \"Noma'lum manbadan o'rnatishga ruxsat bering\" deb so'raydi",
                "Fayl kutilmaganda, odatiy bo'lmagan matn bilan keladi",
                "Tanish odam odatda bunday narsa yubormaydi",
            ),
            whatToDo = listOf(
                "Hech qachon APK faylni ochmang — rasm, video va hujjatlar .apk bo'lmaydi",
                "Telegram uchun \"Noma'lum ilovalarni o'rnatish\" ruxsatini o'chirib qo'ying (Sergak'dagi Himoya bo'limida bir tugma)",
                "Yuborgan do'stingizga TELEFON QILIB ayting — uning akkaunti buzilgan",
                "Ochib qo'ygan bo'lsangiz: internetni o'chiring, bankka qo'ng'iroq qilib kartani bloklang, \"SOS\" bo'limidagi qadamlarni bajaring",
            ),
        ),
        Lesson(
            "code", "🔐", "SMS-kod va Telegram kodi",
            "Kodni bergan zahotingiz akkaunt yoki karta sizniki bo'lmay qoladi. Hech kim — hatto bank ham — kodni so'ramaydi.",
            howItWorks = listOf(
                "Firibgar sizning raqamingiz bilan Telegram yoki to'lov ilovasiga kirishga urinadi — sizga kod keladi.",
                "Keyin turli bahonalar bilan shu kodni so'raydi: \"tanlovda ovozingizni tasdiqlang\", \"xato yuborib yubordim\", \"bank xavfsizlik xizmatidanmiz\", \"Premium sovg'a qilyapmiz\".",
                "Kod kiritilishi bilan u akkauntingizga kiradi, yozishmalaringizdan karta raqamlarini qidiradi va tanishlaringizdan pul so'raydi.",
            ),
            signs = listOf(
                "Siz hech narsaga kirmayotgan bo'lsangiz ham kod keldi",
                "Kimdir \"adashib kod yubordim, qaytarib yuboring\" deydi",
                "Sayt \"Telegram orqali kirish\" uchun telefon raqami va kodni so'raydi",
            ),
            whatToDo = listOf(
                "Kodni HECH KIMGA bermang va hech qaysi saytga kiritmang",
                "Telegram'da \"Ikki bosqichli tasdiqlash\" (bulutli parol) yoqing — kod o'g'irlansa ham parolsiz kira olmaydi",
                "Kutilmagan kod kelsa — kimdir akkauntingizga kirmoqchi. Telegram → Qurilmalar bo'limini tekshiring",
            ),
        ),
        Lesson(
            "gov", "🏛️", "Soxta davlat saytlari va \"kompensatsiya\"",
            "\"Davlat sizga 30 250 000 so'm kompensatsiya ajratdi\" — gerb va bayroq bilan bezatilgan soxta my.gov.uz sahifasi.",
            howItWorks = listOf(
                "Telegram guruh va kanallarida \"gov.uz\" ga o'xshatilgan havola tarqatiladi.",
                "Sahifada \"to'lov tayyor\" deb yoziladi va pulni olish uchun karta raqami, amal qilish muddati va SMS-kod so'raladi.",
                "Kiritilgan ma'lumot bilan kartangizdan pul yechiladi. Bitta shunday guruh 456 kishidan 1 mlrd so'mdan ortiq o'g'irlagan.",
            ),
            signs = listOf(
                "Manzil gov.uz bilan TUGAMAYDI: my-gov-uz.online, gov.uz.tolov.site va h.k.",
                "Katta pul va'da qilinadi va shoshiltiriladi",
                "Pul \"olish\" uchun SMS-kod yoki karta muddati so'raladi",
            ),
            whatToDo = listOf(
                "Davlat xizmatlariga faqat my.gov.uz manzilini o'zingiz terib kiring",
                "Pul olish uchun SMS-kod hech qachon kerak emas",
                "Havolani Sergak'dagi \"Tekshirish\" bo'limida tekshiring",
            ),
        ),
        Lesson(
            "call", "📞", "\"Bank xodimi\" va \"Markaziy bank\" qo'ng'iroqlari",
            "Firibgarlar bank yoki huquq-tartibot xodimi bo'lib qo'ng'iroq qiladi va qo'rqitadi.",
            howItWorks = listOf(
                "\"Sizning nomingizga kredit olinmoqda\", \"kartangizdan shubhali to'lov bo'ldi\" deyishadi.",
                "Keyin \"bekor qilish\" uchun kod, karta ma'lumoti so'raladi yoki pulni \"xavfsiz hisobga\" o'tkazish buyuriladi.",
                "Ba'zan AnyDesk kabi ilova o'rnattirib, telefoningizni masofadan boshqaradi.",
            ),
            signs = listOf(
                "Qo'ng'iroq qiluvchi shoshiltiradi va qo'rqitadi",
                "Kod, karta ma'lumoti yoki pul o'tkazishni so'raydi",
                "\"Hech kimga aytmang\" deydi",
            ),
            whatToDo = listOf(
                "Darhol go'shakni qo'ying",
                "Bankka kartangiz orqasidagi raqam orqali O'ZINGIZ qo'ng'iroq qiling",
                "\"Xavfsiz hisob\" degan narsa yo'q — hech qayerga pul o'tkazmang",
            ),
        ),
        Lesson(
            "friend", "👥", "Buzilgan do'st akkaunti pul so'raydi",
            "Do'stingiz yoki qarindoshingiz nomidan \"tezda pul tashlab tur\" degan xabar.",
            howItWorks = listOf(
                "Firibgar avval kimningdir Telegram akkauntini egallaydi.",
                "Yozishmalarni o'qib, uning ohangida tanishlariga yozadi: \"Aka, zarur bo'lib qoldi, 500 ming tashlab turing, kechqurun qaytaraman\".",
                "Pul firibgarning kartasiga (ko'pincha boshqa odam nomidagi \"drop\" kartaga) tushadi. Bir holatda viloyat hokimi akkauntidan bir necha soatda 80 mln so'm yig'ilgan.",
            ),
            signs = listOf(
                "Kutilmagan pul so'rovi, odatdagidan boshqacha ohang",
                "Karta egasining ismi do'stingizniki emas",
                "Telefon qilishni so'rasangiz, bahona topadi",
            ),
            whatToDo = listOf(
                "Pul o'tkazishdan oldin ODDIY TELEFON QO'NG'IROG'I bilan tasdiqlang",
                "Akkaunt buzilganini do'stingizga va umumiy guruhlarga xabar bering",
            ),
        ),
        Lesson(
            "loan", "💸", "Soxta kredit va \"komissiya\"",
            "Telegram botlardagi \"foizsiz kredit\" takliflari.",
            howItWorks = listOf(
                "Bot pasport va karta ma'lumotlarini so'raydi.",
                "\"Kreditingiz tasdiqlandi\" degan xabar keladi.",
                "Pulni olish uchun \"komissiya\" yoki \"sug'urta\" to'lash talab qilinadi. To'lagandan keyin bot yo'qoladi.",
            ),
            signs = listOf("Hujjatsiz, tekshiruvsiz kredit", "Oldindan to'lov talabi", "Kredit Telegram bot orqali beriladi"),
            whatToDo = listOf("Kreditni faqat bank ilovasi yoki filialida oling", "Oldindan hech narsa to'lamang"),
        ),
        Lesson(
            "drop", "🚫", "Kartangizni sotmang va ijaraga bermang",
            "\"Karta ochib bering, pul to'laymiz\" — siz jinoyat sherigiga aylanasiz.",
            howItWorks = listOf(
                "Firibgarlar o'g'irlangan pulni yashirish uchun boshqa odamlar nomidagi kartalardan (\"drop\") foydalanadi.",
                "Talaba va yoshlarga \"kripto almashuv uchun\" yoki \"stavkalar uchun\" deb karta ochtirib, 1 mln so'mga sotib olishadi.",
                "Tergov paytida karta egasi ham javobgarlikka tortiladi.",
            ),
            signs = listOf("Kartangiz uchun pul taklif qilinadi", "\"Faqat to'lov qabul qilish uchun\" deyiladi"),
            whatToDo = listOf("Hech qachon kartangizni, SIM-kartangizni yoki ilova kirish ma'lumotini boshqaga bermang"),
        ),
        Lesson(
            "saved", "💳", "Karta rasmini Telegramda saqlamang",
            "Akkauntga kirgan firibgar birinchi bo'lib \"Saqlangan xabarlar\"dan karta qidiradi.",
            howItWorks = listOf(
                "Ko'pchilik karta raqami va rasmini \"Saqlangan xabarlar\" yoki chatlarda saqlaydi.",
                "Akkaunt buzilganda firibgar shu ma'lumotni topib, pul yechishga urinadi. Rasmiy botlar orqali raqamingizga ulangan kartalarni ham qidiradi.",
            ),
            signs = listOf("Yozishmalarda karta raqami, muddati yoki rasmi bor"),
            whatToDo = listOf(
                "Telegram qidiruvida \"8600\", \"9860\", \"karta\" deb qidirib, topilganlarini o'chiring",
                "Karta ma'lumotlarini faqat bank ilovasida saqlang",
            ),
        ),
        Lesson(
            "olx", "🛒", "Onlayn savdo: soxta xaridor",
            "OLX'da narsa sotayotganingizda \"pulni qabul qilish\" havolasi yuboriladi.",
            howItWorks = listOf(
                "\"Xaridor\" mahsulotingizni ko'rmasdan sotib olishga rozi bo'ladi.",
                "\"Pulni o'tkazdim, qabul qilish uchun havolaga kiring\" deb soxta to'lov sahifasini yuboradi.",
                "Sahifa karta ma'lumoti va SMS-kodni so'raydi — natijada pul sizdan yechiladi.",
            ),
            signs = listOf("Pul qabul qilish uchun havola yoki kod kerak bo'ladi", "Muloqotni Telegram/WhatsApp'ga ko'chirishni so'raydi"),
            whatToDo = listOf("Pul QABUL QILISH uchun faqat karta raqami yetarli — boshqa hech narsa kiritmang"),
        ),
    )

    val quiz = listOf(
        QuizQuestion(
            "Do'stingiz Telegramda \"Bu sizmisiz? 😂 video_2025.mp4.apk\" fayl yubordi. Nima qilasiz?",
            listOf("Ochib ko'raman, do'stim yubordi-ku", "Ochmayman va do'stimga telefon qilib akkaunti buzilganini aytaman", "Boshqa do'stlarimga ham yuboraman"),
            1,
            "Video .apk bilan tugamaydi. Bu SMS-o'g'ri virus, do'stingizning akkaunti esa buzilgan.",
        ),
        QuizQuestion(
            "\"Bank xavfsizlik xizmati\"dan qo'ng'iroq: \"Nomingizga kredit olinmoqda, bekor qilish uchun SMS-kodni ayting\".",
            listOf("Kodni aytaman, kreditni bekor qilish kerak", "Go'shakni qo'yib, bankka kartadagi raqam orqali o'zim qo'ng'iroq qilaman"),
            1,
            "Bank hech qachon kod so'ramaydi. O'zingiz rasmiy raqamga qo'ng'iroq qiling.",
        ),
        QuizQuestion(
            "Qaysi havola haqiqiy davlat xizmatlari portali?",
            listOf("https://my-gov-uz.online", "https://my.gov.uz", "https://gov.uz.kompensatsiya.top", "https://mygov-uz.site"),
            1,
            "Haqiqiy manzil gov.uz bilan TUGAYDI. Qolganlari soxta.",
        ),
        QuizQuestion(
            "OLX'da xaridor: \"Pulni qabul qilish uchun karta raqami, muddati va SMS-kodni yozing\". To'g'rimi?",
            listOf("Ha, pul olish uchun kerak", "Yo'q, pul qabul qilish uchun faqat karta raqami yetarli"),
            1,
            "Muddat, CVV va SMS-kod pul YECHISH uchun kerak bo'ladi.",
        ),
        QuizQuestion(
            "Telegram akkauntingizni eng yaxshi nima himoya qiladi?",
            listOf("Murakkab ism qo'yish", "Ikki bosqichli tasdiqlash (bulutli parol)", "Profil rasmini yashirish"),
            1,
            "Bulutli parol bo'lsa, SMS-kod o'g'irlansa ham firibgar kira olmaydi.",
        ),
        QuizQuestion(
            "Notanish odam kartangizni oyiga 1 mln so'mga ijaraga olmoqchi. Bu…",
            listOf("Oson daromad", "Jinoyat: karta o'g'irlangan pullarni yashirishga ishlatiladi"),
            1,
            "\"Drop\" karta egasi ham jinoiy javobgarlikka tortiladi.",
        ),
        QuizQuestion(
            "Siz hech narsaga kirmayotgan edingiz, lekin Telegramdan kirish kodi keldi. Bu nimani bildiradi?",
            listOf("Telegram xatosi", "Kimdir akkauntingizga kirishga urinmoqda"),
            1,
            "Kodni hech kimga bermang va ikki bosqichli tasdiqlashni yoqing.",
        ),
        QuizQuestion(
            "Tanlovda ovoz berish uchun sayt telefon raqamingiz va Telegram kodini so'rayapti.",
            listOf("Kiritaman, jiyanimga yordam beraman", "Kiritmayman — bu akkauntni o'g'irlash usuli"),
            1,
            "Ovoz berish uchun Telegram kodi hech qachon kerak emas.",
        ),
    )

    val contacts = listOf(
        Contact("Favqulodda: politsiya", "Firibgarlik, pul o'g'irlanishi haqida xabar berish", phone = "102"),
        Contact("IIV ishonch telefoni", "Ichki ishlar organlariga murojaat", phone = "1102"),
        Contact("Kiberxavfsizlik markazi", "Axborot xavfsizligi bo'yicha 24/7 ishonch telefoni (csec.uz)", phone = "+998555021010"),
        Contact("Markaziy bank ishonch telefoni", "Bank va to'lov tizimlari ustidan murojaat", phone = "+998712000044"),
        Contact("@cybershielduz_bot", "IIV Kiberxavfsizlik markazining rasmiy boti: guruh va kanallaringizdan zararli APK va havolalarni avtomatik o'chiradi", link = "https://t.me/cybershielduz_bot"),
    )

    val tips = listOf(
        "Rasm, video va hujjatlar hech qachon .apk bilan tugamaydi.",
        "Pul QABUL QILISH uchun faqat karta raqami yetarli.",
        "Bank, Telegram va davlat xodimlari SMS-kod so'ramaydi.",
        "Do'stingiz pul so'rasa — avval ovozli qo'ng'iroq qiling.",
        "Telegram'da bulutli parol yoqing — bu 2 daqiqa vaqt oladi.",
        "Asosiy kartada kam pul saqlang, onlayn to'lovlar uchun alohida karta oching.",
        "Ilovalarni faqat Google Play'dan o'rnating.",
        "Haqiqiy davlat portali manzili gov.uz bilan tugaydi.",
    )
}
