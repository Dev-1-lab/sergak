package uz.sergak.data

import android.content.Context
import android.content.SharedPreferences

/** Kichik sozlamalar ombori. Xabar matnlari HECH QACHON saqlanmaydi. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("sergak", Context.MODE_PRIVATE)

    var doneItems: Set<String>
        get() = sp.getStringSet(KEY_DONE, emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet(KEY_DONE, v).apply()

    fun setDone(id: String, done: Boolean) {
        doneItems = if (done) doneItems + id else doneItems - id
    }

    /** Foydalanuvchi "Men bu ilovaga ishonaman" deb belgilagan paketlar. */
    var trustedApps: Set<String>
        get() = sp.getStringSet(KEY_TRUSTED, emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet(KEY_TRUSTED, v).apply()

    var alertScam: Boolean
        get() = sp.getBoolean("alert_scam", true)
        set(v) = sp.edit().putBoolean("alert_scam", v).apply()

    var alertOtp: Boolean
        get() = sp.getBoolean("alert_otp", true)
        set(v) = sp.edit().putBoolean("alert_otp", v).apply()

    var alertInstall: Boolean
        get() = sp.getBoolean("alert_install", true)
        set(v) = sp.edit().putBoolean("alert_install", v).apply()

    var packageSeq: Int
        get() = sp.getInt("pkg_seq", -1)
        set(v) = sp.edit().putInt("pkg_seq", v).apply()

    var lastOtpReminderAt: Long
        get() = sp.getLong("last_otp", 0L)
        set(v) = sp.edit().putLong("last_otp", v).apply()

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    var quizBest: Int
        get() = sp.getInt("quiz_best", -1)
        set(v) = sp.edit().putInt("quiz_best", v).apply()

    companion object {
        private const val KEY_DONE = "done_items"
        private const val KEY_TRUSTED = "trusted_apps"
    }
}
