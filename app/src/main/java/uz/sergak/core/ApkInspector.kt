package uz.sergak.core

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.math.ln

/**
 * APK faylni ochmasdan (o'rnatmasdan) ichini statik tekshiradi.
 *
 * Group-IB tahliliga ko'ra O'zbekistondagi hozirgi viruslar "dropper" ko'rinishida keladi:
 * tashqi ilova toza ko'rinadi, asosiy SMS-o'g'ri esa ichida shifrlangan holda saqlanadi:
 *  - MidnightDat: assets/ ichida shifrlangan .dat fayl + libandroidcore_native.so
 *  - RoundRift: assets/ ichida AES bilan shifrlangan [nom].key fayl
 *  - ichma-ich joylashtirilgan ikkinchi APK yoki DEX
 */
object ApkInspector {

    data class Hashes(val sha256: String, val sha1: String, val size: Long)

    data class StaticReport(
        val embeddedPackages: List<String> = emptyList(),
        val encryptedBlobs: List<String> = emptyList(),
        val knownMarkers: List<String> = emptyList(),
        val error: String? = null,
    ) {
        val isClean get() = embeddedPackages.isEmpty() && encryptedBlobs.isEmpty() && knownMarkers.isEmpty()
    }

    /** Group-IB hisobotida ko'rsatilgan dropper komponentlari. */
    private val KNOWN_NATIVE_MARKERS = setOf("libandroidcore_native.so")

    private const val MAX_HASH_BYTES = 300L * 1024 * 1024

    fun hash(file: File): Hashes? {
        if (!file.canRead() || file.length() > MAX_HASH_BYTES) return null
        return file.inputStream().use { hash(it, file.length()) }
    }

    fun hash(input: InputStream, size: Long = -1): Hashes {
        val s256 = MessageDigest.getInstance("SHA-256")
        val s1 = MessageDigest.getInstance("SHA-1")
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            s256.update(buf, 0, n)
            s1.update(buf, 0, n)
            total += n
        }
        return Hashes(s256.digest().toHex(), s1.digest().toHex(), if (size >= 0) size else total)
    }

    fun inspect(apk: File): StaticReport = try {
        ZipFile(apk).use { zip ->
            val embedded = mutableListOf<String>()
            val encrypted = mutableListOf<String>()
            val markers = mutableListOf<String>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                if (e.isDirectory) continue
                val name = e.name
                val base = name.substringAfterLast('/')
                if (name.startsWith("lib/") && base in KNOWN_NATIVE_MARKERS) markers += name

                val inPayloadArea = name.startsWith("assets/") || name.startsWith("res/raw/")
                if (!inPayloadArea) continue

                val head = ByteArray(4096)
                val read = zip.getInputStream(e).use { readUpTo(it, head) }
                val isZip = read >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() && head[2].toInt() == 3 && head[3].toInt() == 4
                val isDex = read >= 4 && head[0] == 'd'.code.toByte() && head[1] == 'e'.code.toByte() && head[2] == 'x'.code.toByte() && head[3].toInt() == 0x0A
                val ext = base.substringAfterLast('.', "").lowercase()

                when {
                    isDex || ext == "dex" -> embedded += name
                    ext == "apk" || (isZip && containsManifest(zip, e)) -> embedded += name
                    (ext == "dat" || ext == "key" || ext == "bin" || ext == "") && e.size >= 50_000 -> {
                        val sample = ByteArray(65536)
                        val n = zip.getInputStream(e).use { readUpTo(it, sample) }
                        if (entropy(sample, n) > 7.6) encrypted += name
                    }
                }
            }
            StaticReport(embedded, encrypted, markers)
        }
    } catch (e: Exception) {
        StaticReport(error = e.javaClass.simpleName)
    }

    /** Ichki ZIP ichida AndroidManifest.xml bormi (ya'ni u ham APK'mi). */
    private fun containsManifest(zip: ZipFile, entry: java.util.zip.ZipEntry): Boolean = try {
        java.util.zip.ZipInputStream(zip.getInputStream(entry)).use { inner ->
            var count = 0
            var ze = inner.nextEntry
            while (ze != null && count < 200) {
                if (ze.name == "AndroidManifest.xml" || ze.name == "classes.dex") return true
                ze = inner.nextEntry
                count++
            }
            false
        }
    } catch (_: Exception) {
        false
    }

    private fun readUpTo(input: InputStream, buf: ByteArray): Int {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n < 0) break
            off += n
        }
        return off
    }

    /** Shannon entropiyasi (bit/bayt). Shifrlangan yoki siqilgan ma'lumot ~8 ga yaqin. */
    fun entropy(data: ByteArray, len: Int = data.size): Double {
        if (len <= 0) return 0.0
        val counts = IntArray(256)
        for (i in 0 until len) counts[data[i].toInt() and 0xFF]++
        var h = 0.0
        for (c in counts) {
            if (c == 0) continue
            val p = c.toDouble() / len
            h -= p * ln(p) / ln(2.0)
        }
        return h
    }

    fun findings(r: StaticReport, sideloaded: Boolean, canInstall: Boolean): List<Finding> {
        val out = mutableListOf<Finding>()
        if (r.knownMarkers.isNotEmpty()) {
            out += Finding(
                "dropper_marker", "Ma'lum virus komponenti topildi",
                "Ilova ichida O'zbekistonda tarqalgan MidnightDat dropperiga xos fayl bor: ${r.knownMarkers.first().substringAfterLast('/')}.",
                "Darhol o'chiring.", 80,
            )
        }
        if (r.embeddedPackages.isNotEmpty()) {
            out += Finding(
                "embedded_payload", "Ilova ichida yashirin ikkinchi ilova bor",
                "Topildi: ${r.embeddedPackages.take(2).joinToString { it.substringAfterLast('/') }}. Dropper-viruslar asosiy virusni shunday olib yuradi va keyin o'rnatadi.",
                if (sideloaded) "Bu ilovani o'chiring." else "Agar bu ilova sizdan boshqa ilova o'rnatishga ruxsat so'rasa — rad eting.",
                if (sideloaded) (if (canInstall) 55 else 40) else 10,
            )
        }
        if (r.encryptedBlobs.isNotEmpty()) {
            out += Finding(
                "encrypted_payload", "Shifrlangan yashirin fayl",
                "Ilova ichida shifrlangan katta fayl bor (${r.encryptedBlobs.first().substringAfterLast('/')}). MidnightDat va RoundRift viruslari asosiy zararli kodni aynan shunday yashiradi.",
                "Ilovani tanimasangiz — o'chiring.",
                if (sideloaded) 30 else 5,
            )
        }
        return out
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
