package uz.sergak.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ThreatIntelTest {

    @Test fun bundledIocMatchesBySha1() {
        val r = ThreatIntel.localMatch(null, "DB1D14D5246F2C8807C55084B74247DEA6465285")
        assertNotNull(r)
        assertEquals(ReputationStatus.MALICIOUS, r!!.status)
        assertTrue(r.label!!.contains("Wonderland"))
    }

    @Test fun feedHashesAreUsed() {
        val h = "a".repeat(64)
        assertNotNull(ThreatIntel.localMatch(h, null, mapOf(h to "TestFamily")))
        assertNull(ThreatIntel.localMatch("b".repeat(64), null))
    }

    @Test fun maliciousIntelMakesAppDangerous() {
        val f = ThreatIntel.fileFinding(Reputation("x", ReputationStatus.MALICIOUS, 31, 66, "trojan.smsspy", listOf("VirusTotal")))!!
        val risk = AppRiskScorer.score(
            AppFacts("a.b", "Foo", emptySet(), "com.android.vending", true, false, false, false, false, false, 100),
            listOf(f),
        )
        assertEquals(RiskLevel.DANGEROUS, risk.verdict.level)
    }

    @Test fun googleAttributionOnUrlFindings() {
        val f = ThreatIntel.urlFinding(Reputation("u", ReputationStatus.MALICIOUS, label = "SOCIAL_ENGINEERING", sources = listOf("Google Safe Browsing")))!!
        assertTrue(f.explanation.contains("Google"))
        assertTrue(f.title.contains("ehtimol"))
    }
}

class FakeAppDetectorTest {
    @Test fun sideloadedBrandAppIsFlagged() {
        assertEquals("fake_brand_app", FakeAppDetector.check("Click Up", "com.xyz.qwe", sideloaded = true)?.id)
        assertEquals("fake_brand_app", FakeAppDetector.check("Google Play", "abc.def", sideloaded = true)?.id)
        assertEquals("fake_brand_app", FakeAppDetector.check("Телеграм Telegram", "a.b", sideloaded = true)?.id)
    }

    @Test fun fileDisguisedAppIsFlagged() {
        listOf("Sud qarori.pdf", "To'ydan video", "Kamera yozuvi", "Видео").forEach {
            assertEquals(it, "fake_file_app", FakeAppDetector.check(it, "x.y", sideloaded = true)?.id)
        }
    }

    @Test fun storeAppsAndNormalNamesAreNotFlagged() {
        assertNull(FakeAppDetector.check("Click Up", "air.com.ssdsoftwaresolutions.clickuz", sideloaded = false))
        assertNull(FakeAppDetector.check("Notes", "x.y", sideloaded = true))
        assertNull(FakeAppDetector.check("Calculator", "x.y", sideloaded = true))
    }
}

class ApkInspectorTest {

    private fun zipOf(vararg entries: Pair<String, ByteArray>): File {
        val f = File.createTempFile("sergak", ".apk")
        f.deleteOnExit()
        ZipOutputStream(f.outputStream()).use { z ->
            for ((name, data) in entries) {
                z.putNextEntry(ZipEntry(name)); z.write(data); z.closeEntry()
            }
        }
        return f
    }

    private fun innerApk(): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry("AndroidManifest.xml")); z.write(ByteArray(100)); z.closeEntry()
            z.putNextEntry(ZipEntry("classes.dex")); z.write("dex\n035".toByteArray()); z.closeEntry()
        }
        return bos.toByteArray()
    }

    private fun random(n: Int) = ByteArray(n).also { Random(42).nextBytes(it) }

    @Test fun cleanApk() {
        val f = zipOf("AndroidManifest.xml" to ByteArray(10), "classes.dex" to "dex\n035".toByteArray(), "assets/readme.txt" to "hello".toByteArray())
        assertTrue(ApkInspector.inspect(f).isClean)
    }

    @Test fun detectsMidnightDatStyleDropper() {
        val f = zipOf(
            "AndroidManifest.xml" to ByteArray(10),
            "assets/Ez1dVV0BD0YcPjk.dat" to random(200_000),
            "lib/arm64-v8a/libandroidcore_native.so" to ByteArray(1000),
        )
        val r = ApkInspector.inspect(f)
        assertEquals(1, r.encryptedBlobs.size)
        assertEquals(1, r.knownMarkers.size)
        val risk = AppRiskScorer.score(
            AppFacts("x.y", "Update", emptySet(), null, true, false, false, false, true, false, 0),
            ApkInspector.findings(r, sideloaded = true, canInstall = true),
        )
        assertEquals(RiskLevel.DANGEROUS, risk.verdict.level)
    }

    @Test fun detectsEmbeddedApk() {
        val f = zipOf("AndroidManifest.xml" to ByteArray(10), "assets/core.bin" to innerApk())
        assertEquals(listOf("assets/core.bin"), ApkInspector.inspect(f).embeddedPackages)
    }

    @Test fun hashesMatchKnownValues() {
        val h = ApkInspector.hash("abc".byteInputStream())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", h.sha256)
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", h.sha1)
    }

    @Test fun entropyOfRandomIsHigh() {
        assertTrue(ApkInspector.entropy(random(65536)) > 7.9)
        assertTrue(ApkInspector.entropy(ByteArray(65536)) < 0.1)
    }
}
