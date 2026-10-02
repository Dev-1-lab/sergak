package uz.sergak.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import uz.sergak.core.Reputation
import uz.sergak.core.ReputationStatus
import uz.sergak.data.cloud.CloudClient
import java.io.File

/**
 * Tahdid ma'lumotlari ombori:
 *  - serverdan yuklangan IOC ro'yxati (feed.json) — oflayn ham ishlaydi
 *  - bulut javoblarining qisqa muddatli keshi (rep_cache.json) — server va VirusTotal kvotasini tejaydi
 */
class IntelRepository(private val context: Context) {

    private val prefs = Prefs(context)
    private val feedFile = File(context.filesDir, "feed.json")
    private val cacheFile = File(context.filesDir, "rep_cache.json")

    val cloudReady: Boolean get() = CloudClient.isAvailable && prefs.cloudEnabled

    fun feedHashes(): Map<String, String> = synchronized(LOCK) {
        loadFeed()?.first ?: emptyMap()
    }

    fun feedDomains(): Set<String> = synchronized(LOCK) {
        loadFeed()?.second ?: emptySet()
    }

    private fun loadFeed(): Pair<Map<String, String>, Set<String>>? {
        memFeed?.let { return it }
        if (!feedFile.exists()) return null
        return try {
            val o = JSONObject(feedFile.readText())
            val h = HashMap<String, String>()
            o.optJSONObject("hashes")?.let { j -> j.keys().forEach { k -> h[k] = j.optString(k) } }
            val d = HashSet<String>()
            o.optJSONArray("domains")?.let { a -> for (i in 0 until a.length()) d += a.getString(i) }
            (h to d).also { memFeed = it }
        } catch (_: Exception) {
            null
        }
    }

    /** Serverdan yangi IOC ro'yxatini yuklaydi. true — yangilandi. */
    fun syncFeed(): Boolean {
        if (!CloudClient.isAvailable) return false
        return try {
            val feed = CloudClient(prefs.installId).feed(prefs.feedEtag)
            val o = JSONObject()
                .put("hashes", JSONObject(feed.hashes as Map<*, *>))
                .put("domains", JSONArray(feed.domains))
            synchronized(LOCK) {
                feedFile.writeText(o.toString())
                memFeed = feed.hashes to feed.domains.toSet()
            }
            prefs.feedEtag = feed.etag
            prefs.feedSyncedAt = System.currentTimeMillis()
            true
        } catch (_: CloudClient.NotModified) {
            prefs.feedSyncedAt = System.currentTimeMillis()
            false
        } catch (_: Exception) {
            false
        }
    }

    /** Domen (yoki uning ota-domeni) serverdan kelgan fishing ro'yxatidami. */
    fun isBlockedDomain(host: String): Boolean {
        val set = feedDomains()
        if (set.isEmpty()) return false
        var h = host.lowercase()
        while (h.contains('.')) {
            if (h in set) return true
            h = h.substringAfter('.')
        }
        return false
    }

    // ---------- Bulut so'rovlari keshi bilan ----------

    fun lookupHashes(hashes: List<String>): Map<String, Reputation> {
        if (!cloudReady || hashes.isEmpty()) return emptyMap()
        val now = System.currentTimeMillis()
        val cache = readCache()
        val result = HashMap<String, Reputation>()
        val missing = ArrayList<String>()
        for (h in hashes.distinct()) {
            val c = cache["h:$h"]
            if (c != null && c.second > now) result[h] = c.first else missing += h
        }
        if (missing.isNotEmpty()) {
            try {
                val fresh = CloudClient(prefs.installId).lookupHashes(missing)
                for ((k, v) in fresh) {
                    result[k] = v
                    // Noma'lum natijalarni qisqa muddat saqlaymiz — keyinroq VirusTotal kvotasi bo'shaganda qayta so'raladi.
                    if (v.status != ReputationStatus.UNKNOWN || v.sources.isNotEmpty()) cache["h:$k"] = v to (now + ttl(v))
                }
                writeCache(cache)
            } catch (_: Exception) {
                // Internet yo'q yoki server javob bermadi — mahalliy tahlil baribir ishlaydi
            }
        }
        return result
    }

    fun lookupUrl(url: String): Reputation? {
        if (!cloudReady) return null
        val now = System.currentTimeMillis()
        val cache = readCache()
        cache["u:$url"]?.let { if (it.second > now) return it.first }
        return try {
            CloudClient(prefs.installId).lookupUrl(url).also {
                cache["u:$url"] = it to (now + ttl(it))
                writeCache(cache)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun ttl(r: Reputation): Long = when (r.status) {
        ReputationStatus.MALICIOUS -> 7 * DAY
        ReputationStatus.SUSPICIOUS, ReputationStatus.CLEAN -> DAY
        ReputationStatus.UNKNOWN -> 2 * HOUR
    }

    private fun readCache(): MutableMap<String, Pair<Reputation, Long>> = synchronized(LOCK) {
        val out = LinkedHashMap<String, Pair<Reputation, Long>>()
        if (!cacheFile.exists()) return out
        try {
            val arr = JSONArray(cacheFile.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val src = o.optJSONArray("s")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
                val rep = Reputation(
                    o.getString("key"),
                    ReputationStatus.valueOf(o.getString("st")),
                    o.optInt("d"), o.optInt("e"),
                    o.optString("l").takeIf { it.isNotBlank() },
                    src,
                )
                out[o.getString("k")] = rep to o.getLong("x")
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun writeCache(map: Map<String, Pair<Reputation, Long>>) = synchronized(LOCK) {
        val now = System.currentTimeMillis()
        val arr = JSONArray()
        map.entries.filter { it.value.second > now }.takeLast(2000).forEach { (k, v) ->
            val r = v.first
            arr.put(
                JSONObject().put("k", k).put("key", r.key).put("st", r.status.name).put("d", r.detections)
                    .put("e", r.engines).put("l", r.label ?: "").put("s", JSONArray(r.sources)).put("x", v.second)
            )
        }
        try {
            cacheFile.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    companion object {
        private val LOCK = Any()
        @Volatile private var memFeed: Pair<Map<String, String>, Set<String>>? = null
        private const val HOUR = 3600_000L
        private const val DAY = 24 * HOUR
    }
}
