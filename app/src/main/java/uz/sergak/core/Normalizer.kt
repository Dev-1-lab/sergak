package uz.sergak.core

/**
 * Matnni tahlil uchun yagona ko'rinishga keltiradi:
 *  - kichik harflar
 *  - kirill (o'zbek va rus) -> lotin
 *  - turli apostroflar (ʻ ’ ‘ ` ´) -> '
 *  - ortiqcha bo'shliqlar -> bitta bo'shliq
 *
 * Shu sababli qoidalarni faqat lotin yozuvida yozish kifoya:
 * "КОДНИ ЮБОРИНГ" va "kodni yuboring" bir xil natija beradi.
 */
object Normalizer {

    private val cyr = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e",
        'ё' to "yo", 'ж' to "j", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k",
        'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r",
        'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "x", 'ц' to "ts",
        'ч' to "ch", 'ш' to "sh", 'щ' to "sh", 'ъ' to "'", 'ы' to "i", 'ь' to "",
        'э' to "e", 'ю' to "yu", 'я' to "ya",
        // o'zbek kirill harflari
        'ў' to "o'", 'қ' to "q", 'ғ' to "g'", 'ҳ' to "h",
        // qozoq/qirg'iz matnlarida uchraydigan harflar
        'ң' to "ng", 'ө' to "o", 'ү' to "u", 'і' to "i",
    )

    private val apostrophes = charArrayOf('ʻ', 'ʼ', '’', '‘', '`', '´', 'ʹ', '′')

    fun normalize(input: String): String {
        val sb = StringBuilder(input.length + 16)
        for (ch in input.lowercase()) {
            when {
                ch in apostrophes -> sb.append('\'')
                cyr.containsKey(ch) -> sb.append(cyr.getValue(ch))
                ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r' -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace(Regex(" {2,}"), " ").trim()
    }

    /** Raqam/harf almashtirishlarini qaytaradi: "c1ick" -> "click", "teiegram" o'zgarmaydi. */
    fun deLeet(s: String): String = buildString {
        for (c in s) append(
            when (c) {
                '0' -> 'o'; '1' -> 'l'; '3' -> 'e'; '4' -> 'a'; '5' -> 's'
                '7' -> 't'; '8' -> 'b'; '@' -> 'a'; '$' -> 's'
                else -> c
            }
        )
    }
}
