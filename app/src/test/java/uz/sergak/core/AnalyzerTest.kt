package uz.sergak.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkAnalyzerTest {
    private fun level(url: String) = LinkAnalyzer.analyze(url).level

    @Test fun officialDomainsAreSafe() {
        listOf("https://my.gov.uz/uz/service/1", "https://click.uz", "https://payme.uz", "https://olx.uz/d/x", "https://t.me/kunuzofficial")
            .forEach { assertEquals(it, RiskLevel.SAFE, level(it)) }
    }

    @Test fun impersonationIsDangerous() {
        listOf(
            "my-gov-uz.online/kompensatsiya", "https://gov.uz.kompensatsiya.top/form", "http://c1ick.uz/login",
            "https://paymee.uz", "https://telegram-premium.site/gift", "https://uzcard-kompensatsiya.online",
            "https://google.com@evil.site/x", "https://files.example.com/Sud_qarori.pdf.apk",
        ).forEach { assertEquals(it, RiskLevel.DANGEROUS, level(it)) }
    }

    @Test fun unrelatedBrandsLikeWordsAreNotFlagged() {
        listOf("https://clickup.com", "https://humor.net", "https://wikipedia.org", "https://ok.ru")
            .forEach { assertEquals(it, RiskLevel.SAFE, level(it)) }
    }

    @Test fun shortenerIsSuspicious() = assertEquals(RiskLevel.SUSPICIOUS, level("https://bit.ly/3abcd"))

    @Test fun extractsBareDomainsButNotFileNames() {
        val urls = LinkAnalyzer.extractUrls("Kiring: my-gov-uz.online/tolov va fayl: video.mp4 hamda https://t.me/x")
        assertTrue(urls.toString(), urls.any { it.startsWith("my-gov-uz.online") })
        assertTrue(urls.none { it.startsWith("video.mp4") })
    }
}

class MessageAnalyzerTest {
    private fun analyze(t: String) = MessageAnalyzer.analyze(t)

    @Test fun apkLuresAreDangerous() {
        listOf("Sud qarori.PDF (8).apk", "To'y taklifnoma.apk", "Это ты на видео? video_2025.mp4.apk")
            .forEach { assertEquals(it, RiskLevel.DANGEROUS, analyze(it).verdict.level) }
    }

    @Test fun codeRequestsInUzbekLatinCyrillicAndRussian() {
        listOf(
            "Men bank xodimiman. Sizga kelgan SMS kodni ayting, kartangiz bloklanadi",
            "ЗДРАВСТВУЙТЕ! Я сотрудник банка, продиктуйте код из смс",
        ).forEach { assertEquals(it, RiskLevel.DANGEROUS, analyze(it).verdict.level) }
        assertTrue(analyze("Кодни юборинг илтимос").verdict.level != RiskLevel.SAFE)
    }

    @Test fun genuineOtpIsNotFlaggedButRecognised() {
        val tg = analyze("Telegram code: 51234. Do not give this code to anyone, even if they say they are from Telegram!")
        assertEquals(RiskLevel.SAFE, tg.verdict.level)
        assertEquals(MessageAnalyzer.OtpKind.TELEGRAM_LOGIN, tg.otp)
        val bank = analyze("Sizning tasdiqlash kodingiz 482911. Kodni hech kimga aytmang!")
        assertEquals(RiskLevel.SAFE, bank.verdict.level)
        assertEquals(MessageAnalyzer.OtpKind.SERVICE_CODE, bank.otp)
    }

    @Test fun phishingWithPrize() {
        val r = analyze("Davlat tomonidan 30 250 000 so'm kompensatsiya ajratildi. Olish uchun: https://my-gov-uz.online")
        assertEquals(RiskLevel.DANGEROUS, r.verdict.level)
    }

    @Test fun hackedFriendMoneyRequest() {
        assertEquals(RiskLevel.DANGEROUS, analyze("Aka tezda 500 ming pul tashlab tur, kechqurun qaytaraman 8600 1234 5678 9012").verdict.level)
    }

    @Test fun normalMessagesAreSafe() {
        listOf("Ertaga soat 10 da uchrashamiz, kechikmang", "Salom, bugun dars bormi?", "Onam sizga salom aytdi")
            .forEach { assertEquals(it, RiskLevel.SAFE, analyze(it).verdict.level) }
    }
}

class AppRiskTest {
    private fun facts(
        installer: String? = null, granted: Set<String> = emptySet(), launcher: Boolean = true,
        accessibility: Boolean = false, via: String? = null,
    ) = AppFacts("x.y", "X", granted, installer, launcher, accessibility, false, false, false, false, 1, granted, via)

    @Test fun smsStealerProfileIsDangerous() {
        val r = AppRiskScorer.score(facts(granted = setOf(AppRiskScorer.PERM_RECEIVE_SMS, AppRiskScorer.PERM_READ_SMS), launcher = false, via = "org.telegram.messenger"))
        assertEquals(RiskLevel.DANGEROUS, r.verdict.level)
    }

    @Test fun playStoreAppIsSafe() {
        assertEquals(RiskLevel.SAFE, AppRiskScorer.score(facts(installer = "com.android.vending")).verdict.level)
    }
}
