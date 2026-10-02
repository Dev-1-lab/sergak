package uz.sergak.guard

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import uz.sergak.core.AppRiskScorer
import uz.sergak.core.MessageAnalyzer
import uz.sergak.core.RiskLevel
import uz.sergak.data.AppScanner
import uz.sergak.data.Prefs
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * Real vaqt himoyasi.
 *
 * Messenjer va SMS ilovalaridan kelgan bildirishnoma matnini TELEFON ICHIDA tahlil qiladi
 * va xavf topilsa ogohlantiradi. Sergak'da internet ruxsati yo'q — matn hech qayerga
 * yuborilmaydi va diskka yozilmaydi.
 *
 * Bonus: servis tizim tomonidan doimiy ushlab turilgani uchun yangi o'rnatilgan ilovalarni
 * ham shu yerda darhol tekshiramiz (PACKAGE_ADDED).
 */
class GuardListenerService : NotificationListenerService() {

    private val worker = Executors.newSingleThreadExecutor()
    private val seen = object : LinkedHashMap<Int, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Long>?) = size > 300
    }
    private var packageReceiver: BroadcastReceiver? = null
    private lateinit var prefs: Prefs

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        registerPackageReceiver()
    }

    override fun onListenerDisconnected() {
        unregisterPackageReceiver()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        unregisterPackageReceiver()
        worker.shutdown()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        if (!isWatched(pkg)) return
        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        val text = extractText(n.extras) ?: return
        if (text.length < 4) return
        val key = (pkg + "\u0000" + text).hashCode()
        synchronized(seen) {
            if (seen.containsKey(key)) return
            seen[key] = System.currentTimeMillis()
        }

        val appName = appLabel(pkg)
        worker.execute {
            try {
                handleMessage(pkg, appName, text)
            } catch (_: Throwable) {
                // Himoya servisi hech qachon qulab tushmasligi kerak
            }
        }
    }

    private fun handleMessage(pkg: String, appName: String, text: String) {
        val report = MessageAnalyzer.analyze(text, pkg)

        report.otp?.let { kind ->
            if (!prefs.alertOtp) return@let
            val isTelegramSource = pkg in TELEGRAM
            val isSmsSource = pkg in smsPackages()
            // Telegram kirish kodi faqat Telegram yoki SMS'dan kelganda; xizmat kodlari — faqat SMS'dan.
            val relevant = when (kind) {
                MessageAnalyzer.OtpKind.TELEGRAM_LOGIN -> isTelegramSource || isSmsSource
                MessageAnalyzer.OtpKind.SERVICE_CODE -> isSmsSource
            }
            val now = System.currentTimeMillis()
            if (relevant && (kind == MessageAnalyzer.OtpKind.TELEGRAM_LOGIN || now - prefs.lastOtpReminderAt > 10 * 60 * 1000L)) {
                prefs.lastOtpReminderAt = now
                Notifier.otpReminder(this, kind)
            }
        }

        if (!prefs.alertScam) return
        when (report.verdict.level) {
            RiskLevel.DANGEROUS -> Notifier.messageAlert(this, appName, text, report)
            RiskLevel.SUSPICIOUS -> if (report.verdict.score >= 35) Notifier.messageAlert(this, appName, text, report)
            RiskLevel.SAFE -> Unit
        }
    }

    private fun extractText(extras: Bundle?): String? {
        extras ?: return null
        val parts = LinkedHashSet<String>()
        extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.let(parts::add)
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.let(parts::add)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.let(parts::add)
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { parts.add(it.toString()) }
        // MessagingStyle: oxirgi xabarlar
        @Suppress("DEPRECATION")
        val msgs = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        msgs?.takeLast(3)?.forEach { p ->
            (p as? Bundle)?.getCharSequence("text")?.toString()?.let(parts::add)
        }
        val joined = parts.filter { it.isNotBlank() }.joinToString("\n")
        return joined.takeIf { it.isNotBlank() }?.take(4000)
    }

    private fun isWatched(pkg: String): Boolean = pkg in WATCHED || pkg in smsPackages()

    private var smsCache: Set<String>? = null
    private fun smsPackages(): Set<String> {
        smsCache?.let { return it }
        val def = try {
            Telephony.Sms.getDefaultSmsPackage(this)
        } catch (_: Exception) {
            null
        }
        return (SMS_APPS + listOfNotNull(def)).toSet().also { smsCache = it }
    }

    private fun appLabel(pkg: String): String = try {
        val ai = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(ai).toString()
    } catch (_: Exception) {
        pkg
    }

    // ---------- Yangi ilova o'rnatilishini kuzatish ----------

    private fun registerPackageReceiver() {
        if (packageReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                val pkg = intent.data?.schemeSpecificPart ?: return
                val pending = goAsync()
                worker.execute {
                    try {
                        checkNewPackage(context.applicationContext, pkg)
                    } catch (_: Throwable) {
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }
        ContextCompat.registerReceiver(this, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        packageReceiver = r
    }

    private fun unregisterPackageReceiver() {
        packageReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        packageReceiver = null
    }

    companion object {
        val TELEGRAM = setOf(
            "org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram",
            "org.telegram.plus", "ir.ilmili.telegraph", "org.telegram.messenger.beta",
        )
        val SMS_APPS = setOf(
            "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
            "com.android.messaging", "com.miui.smsextra", "com.huawei.message", "com.oneplus.mms",
            "com.transsion.message",
        )
        val WATCHED = TELEGRAM + SMS_APPS + setOf(
            "com.whatsapp", "com.whatsapp.w4b", "com.imo.android.imoim", "com.viber.voip",
            "com.instagram.android", "com.facebook.orca", "com.vkontakte.android", "ru.oneme.app",
        )

        /** Yangi o'rnatilgan ilovani tekshirib, xavfli bo'lsa ogohlantiradi. Worker ham ishlatadi. */
        fun checkNewPackage(context: Context, pkg: String) {
            val prefs = Prefs(context)
            if (!prefs.alertInstall || pkg in prefs.trustedApps) return
            val risk = AppScanner(context).scanOne(pkg) ?: return
            val sideloaded = risk.facts.installer == null || risk.facts.installer !in AppRiskScorer.TRUSTED_INSTALLERS
            if (risk.verdict.level != RiskLevel.SAFE && sideloaded) {
                Notifier.appAlert(context, risk)
            }
        }
    }
}
