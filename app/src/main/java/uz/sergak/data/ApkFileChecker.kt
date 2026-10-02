package uz.sergak.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import uz.sergak.core.ApkInspector
import uz.sergak.core.AppFacts
import uz.sergak.core.AppRisk
import uz.sergak.core.AppRiskScorer
import uz.sergak.core.Finding
import uz.sergak.core.ThreatIntel
import java.io.File

/**
 * O'rnatilmagan APK faylni (masalan, Telegram'da kelgan) o'rnatishdan OLDIN tekshiradi.
 * Fayl faqat ilovaning vaqtinchalik papkasiga nusxalanadi va tekshiruvdan keyin o'chiriladi.
 */
class ApkFileChecker(private val context: Context) {

    data class Result(
        val fileName: String,
        val risk: AppRisk,
        val isApk: Boolean,
        val cloudChecked: Boolean,
    )

    fun check(uri: Uri): Result {
        val name = displayName(uri) ?: "fayl.apk"
        val tmp = File(context.cacheDir, "check_${System.nanoTime()}.apk")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Faylni ochib bo'lmadi" }
                tmp.outputStream().use { input.copyTo(it) }
            }
            return checkFile(tmp, name)
        } finally {
            tmp.delete()
        }
    }

    fun checkFile(file: File, name: String): Result {
        val hashes = ApkInspector.hash(file)
        val info = archiveInfo(file)
        val extra = ArrayList<Finding>()

        // Fayl nomining o'zi tuzoq bo'lishi mumkin: "Sud qarori.pdf.apk"
        val nameReport = uz.sergak.core.MessageAnalyzer.analyze(name)
        nameReport.verdict.findings.filter { it.id in setOf("double_extension", "lure_court", "lure_media", "lure_prize") }
            .forEach { extra += it.copy(id = "name_" + it.id, title = "Fayl nomi: " + it.title) }

        val feed = IntelRepository(context).feedHashes()
        if (hashes != null) {
            ThreatIntel.localMatch(hashes.sha256, hashes.sha1, feed)?.let { rep -> ThreatIntel.fileFinding(rep)?.let(extra::add) }
        }
        extra += ApkInspector.findings(ApkInspector.inspect(file), sideloaded = true, canInstall = info?.requestedPermissions?.contains("android.permission.REQUEST_INSTALL_PACKAGES") == true)

        val intel = IntelRepository(context)
        var cloudChecked = false
        if (hashes != null && intel.cloudReady) {
            intel.lookupHashes(listOf(hashes.sha256))[hashes.sha256]?.let { rep ->
                cloudChecked = true
                ThreatIntel.fileFinding(rep)?.let(extra::add)
            }
        }

        val facts = AppFacts(
            packageName = info?.packageName ?: "—",
            label = info?.let { label(it, file) } ?: name,
            grantedPermissions = emptySet(),
            installer = null,
            hasLauncherIcon = true,
            isAccessibilityEnabled = false,
            isNotificationListener = false,
            isDeviceAdmin = false,
            canInstallApps = false,
            isDefaultSmsApp = false,
            installedDaysAgo = 0,
            requestedPermissions = info?.requestedPermissions?.toSet() ?: emptySet(),
            installedVia = null,
        )
        val base = AppRiskScorer.score(facts, extra, hashes?.sha256)
        // O'rnatilmagan fayl uchun "Google Play'dan o'rnatilmagan" topilmasini "APK fayl" deb qayta nomlaymiz.
        val findings = base.verdict.findings.map {
            if (it.id == "sideloaded") it.copy(
                title = "Google Play'dan tashqaridagi APK fayl",
                explanation = "Haqiqiy bank, to'lov va davlat ilovalari faqat Google Play orqali tarqatiladi. Telegram yoki saytdan kelgan APK — eng katta xavf manbai.",
                advice = "Agar faylni kim va nima uchun yuborganini aniq bilmasangiz — o'rnatmang.",
            ) else it
        }
        val risk = base.copy(verdict = uz.sergak.core.Verdict.of(findings))
        return Result(name, risk, info != null, cloudChecked)
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(file: File): PackageInfo? = try {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            context.packageManager.getPackageArchiveInfo(file.path, PackageManager.GET_PERMISSIONS)
        }
    } catch (_: Exception) {
        null
    }

    private fun label(info: PackageInfo, file: File): String? = try {
        val ai = info.applicationInfo ?: return null
        ai.sourceDir = file.path
        ai.publicSourceDir = file.path
        context.packageManager.getApplicationLabel(ai).toString()
    } catch (_: Exception) {
        null
    }

    private fun displayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment
    } catch (_: Exception) {
        uri.lastPathSegment
    }
}
