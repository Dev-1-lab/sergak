package uz.sergak.data.cloud

import org.json.JSONArray
import org.json.JSONObject
import uz.sergak.BuildConfig
import uz.sergak.core.Reputation
import uz.sergak.core.ReputationStatus
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sergak serveri bilan aloqa. API kalitlari (VirusTotal, abuse.ch, Google) ilovada EMAS,
 * faqat serverda saqlanadi — APK'dan kalitni sug'urib olib bo'lmaydi.
 *
 * Yuboriladi: APK fayl SHA-256 xeshlari va foydalanuvchi o'zi tekshirgan havolalar.
 * Yuborilmaydi: xabar matnlari, kontaktlar, ilova ro'yxati nomlari, telefon raqami.
 */
class CloudClient(private val installId: String) {

    data class Feed(val etag: String?, val hashes: Map<String, String>, val domains: List<String>)

    class NotModified : Exception()

    companion object {
        /** Bulutli tekshiruv bu versiyada mavjudmi (online variant + server manzili berilgan). */
        val isAvailable: Boolean get() = BuildConfig.CLOUD && BuildConfig.API_URL.isNotBlank()

        private const val CONNECT_TIMEOUT = 5000
        private const val READ_TIMEOUT = 20000
    }

    @Throws(IOException::class)
    fun lookupHashes(sha256: List<String>): Map<String, Reputation> {
        if (sha256.isEmpty()) return emptyMap()
        val out = HashMap<String, Reputation>()
        for (chunk in sha256.distinct().chunked(50)) {
            val body = JSONObject().put("sha256", JSONArray(chunk))
            val res = request("POST", "/v1/hashes", body.toString())
            val arr = JSONObject(res).optJSONArray("results") ?: continue
            for (i in 0 until arr.length()) {
                val r = parseVerdict(arr.getJSONObject(i))
                out[r.key] = r
            }
        }
        return out
    }

    @Throws(IOException::class)
    fun lookupUrl(url: String): Reputation {
        val res = request("POST", "/v1/url", JSONObject().put("url", url).toString())
        return parseVerdict(JSONObject(res))
    }

    @Throws(IOException::class)
    fun feed(etag: String?): Feed {
        val conn = open("GET", "/v1/feed")
        if (etag != null) conn.setRequestProperty("If-None-Match", etag)
        try {
            if (conn.responseCode == 304) throw NotModified()
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val hashes = HashMap<String, String>()
            json.optJSONObject("hashes")?.let { h -> h.keys().forEach { k -> hashes[k.lowercase()] = h.optString(k, "IOC") } }
            val domains = ArrayList<String>()
            json.optJSONArray("domains")?.let { d -> for (i in 0 until d.length()) domains += d.getString(i).lowercase() }
            return Feed(conn.getHeaderField("ETag"), hashes, domains)
        } finally {
            conn.disconnect()
        }
    }

    private fun open(method: String, path: String): HttpURLConnection {
        val conn = URL(BuildConfig.API_URL.trimEnd('/') + path).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("X-Install-Id", installId)
        if (BuildConfig.APP_TOKEN.isNotBlank()) conn.setRequestProperty("X-Sergak-App", BuildConfig.APP_TOKEN)
        return conn
    }

    private fun request(method: String, path: String, body: String?): String {
        val conn = open(method, path)
        try {
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            if (code == 429) throw IOException("rate_limited")
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseVerdict(o: JSONObject): Reputation {
        val sources = o.optJSONArray("sources")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        val status = runCatching { ReputationStatus.valueOf(o.optString("status", "UNKNOWN")) }.getOrDefault(ReputationStatus.UNKNOWN)
        return Reputation(
            key = o.optString("key"),
            status = status,
            detections = o.optInt("detections", 0),
            engines = o.optInt("engines", 0),
            label = o.optString("label").takeIf { it.isNotBlank() && it != "null" },
            sources = sources,
        )
    }
}
