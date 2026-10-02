package uz.sergak.guard

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import uz.sergak.MainActivity
import uz.sergak.R
import uz.sergak.core.AppRisk
import uz.sergak.core.MessageAnalyzer
import uz.sergak.core.RiskLevel

object Notifier {
    const val CH_ALERTS = "alerts"
    const val CH_REMINDERS = "reminders"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "Xavfli xabar va ilovalar", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "APK fayllar, fishing havolalar, kod so'rovlari va xavfli ilovalar haqida ogohlantirish"
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDERS, "Kod eslatmalari", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "SMS yoki Telegram kodi kelganda \"hech kimga bermang\" eslatmasi"
            }
        )
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (!canNotify(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
        }
    }

    private fun openApp(context: Context, requestCode: Int, configure: Intent.() -> Unit): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            configure()
        }
        return PendingIntent.getActivity(context, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun messageAlert(context: Context, sourceApp: String, text: String, report: MessageAnalyzer.Report) {
        val v = report.verdict
        val danger = v.level == RiskLevel.DANGEROUS
        val top = v.findings.take(3).joinToString("\n") { "• " + it.title }
        val advice = v.findings.firstOrNull()?.advice ?: ""
        val id = 1000 + (text.hashCode() and 0x0FFF)
        val title = if (danger) "⚠️ Xavfli xabar ($sourceApp)" else "Shubhali xabar ($sourceApp)"
        val b = NotificationCompat.Builder(context, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setColor(if (danger) 0xFFC62828.toInt() else 0xFFEF8F00.toInt())
            .setContentTitle(title)
            .setContentText(v.findings.firstOrNull()?.title ?: v.headline)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$top\n\n$advice"))
            .setPriority(if (danger) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, id) {
                putExtra(MainActivity.EXTRA_CHECK_TEXT, text.take(4000))
            })
        post(context, id, b)
    }

    fun otpReminder(context: Context, kind: MessageAnalyzer.OtpKind) {
        val (title, body) = when (kind) {
            MessageAnalyzer.OtpKind.TELEGRAM_LOGIN ->
                "Telegram'ga kirish kodi keldi" to
                    "Agar hozir O'ZINGIZ kirmayotgan bo'lsangiz — kimdir akkauntingizni egallamoqchi. Kodni hech kimga bermang va hech qayerga kiritmang. Ikki bosqichli tasdiqlashni yoqing."
            MessageAnalyzer.OtpKind.SERVICE_CODE ->
                "Bu kodni hech kimga aytmang" to
                    "Bank, Click, Payme, Telegram yoki davlat xodimi HECH QACHON kod so'ramaydi. Kodni so'ragan odam — firibgar."
        }
        val b = NotificationCompat.Builder(context, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setColor(0xFF0E5E6F.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setTimeoutAfter(10 * 60 * 1000L)
            .setContentIntent(openApp(context, 77) { putExtra(MainActivity.EXTRA_ROUTE, "learn/code") })
        post(context, if (kind == MessageAnalyzer.OtpKind.TELEGRAM_LOGIN) 77 else 78, b)
    }

    fun appAlert(context: Context, risk: AppRisk) {
        val pkg = risk.facts.packageName
        val id = 5000 + (pkg.hashCode() and 0x0FFF)
        val reasons = risk.verdict.findings.take(3).joinToString("\n") { "• " + it.title }
        val uninstall = PendingIntent.getActivity(
            context, id,
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val danger = risk.verdict.level == RiskLevel.DANGEROUS
        val b = NotificationCompat.Builder(context, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setColor(if (danger) 0xFFC62828.toInt() else 0xFFEF8F00.toInt())
            .setContentTitle(if (danger) "⚠️ Xavfli ilova o'rnatildi: ${risk.facts.label}" else "Shubhali ilova o'rnatildi: ${risk.facts.label}")
            .setContentText(risk.verdict.findings.firstOrNull()?.title ?: "")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$reasons\n\nAgar bu ilovani o'zingiz Google Play'dan o'rnatmagan bo'lsangiz — darhol o'chiring va hech qanday ruxsat bermang."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, id + 1) { putExtra(MainActivity.EXTRA_ROUTE, "scan") })
            .addAction(0, "O'chirish", uninstall)
        post(context, id, b)
    }
}
