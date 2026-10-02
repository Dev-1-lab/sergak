package uz.sergak.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Barcha kalitlar faqat serverda, muhit o'zgaruvchilari orqali beriladi (application.yml ga qarang).
 * Kalit bo'sh bo'lsa, tegishli provayder avtomatik o'chiq turadi — server baribir ishlaydi.
 */
@ConfigurationProperties(prefix = "sergak")
public class SergakProperties {

    /** Ixtiyoriy: ilova yuboradigan X-Sergak-App sarlavhasi. Bo'sh bo'lsa tekshirilmaydi. */
    private String appToken = "";
    /** /admin/** uchun X-Admin-Token. Bo'sh bo'lsa admin API o'chiq. */
    private String adminToken = "";
    /** Havolani tashqi xizmatga yuborishdan oldin ?query va #fragment ni olib tashlash (shaxsiy tokenlar sizib chiqmasligi uchun). */
    private boolean stripUrlQuery = true;
    /** Bir so'rovda maksimal xeshlar soni. */
    private int maxHashesPerRequest = 50;

    private final RateLimit rateLimit = new RateLimit();
    private final Cache cache = new Cache();
    private final VirusTotal virustotal = new VirusTotal();
    private final AbuseCh abusech = new AbuseCh();
    private final Google google = new Google();

    public static class RateLimit {
        private int requests = 60;
        private int windowSeconds = 600;
        public int getRequests() { return requests; }
        public void setRequests(int requests) { this.requests = requests; }
        public int getWindowSeconds() { return windowSeconds; }
        public void setWindowSeconds(int windowSeconds) { this.windowSeconds = windowSeconds; }
    }

    public static class Cache {
        private int maliciousHours = 168;
        private int suspiciousHours = 24;
        private int cleanHours = 24;
        private int unknownHours = 6;
        public int getMaliciousHours() { return maliciousHours; }
        public void setMaliciousHours(int v) { this.maliciousHours = v; }
        public int getSuspiciousHours() { return suspiciousHours; }
        public void setSuspiciousHours(int v) { this.suspiciousHours = v; }
        public int getCleanHours() { return cleanHours; }
        public void setCleanHours(int v) { this.cleanHours = v; }
        public int getUnknownHours() { return unknownHours; }
        public void setUnknownHours(int v) { this.unknownHours = v; }
    }

    public static class VirusTotal {
        private String apiKey = "";
        private String baseUrl = "https://www.virustotal.com/api/v3";
        /** Public API: 4 so'rov/daqiqa, 500/kun. Premium kalit bo'lsa oshiring. */
        private int requestsPerMinute = 4;
        private int requestsPerDay = 500;
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public int getRequestsPerMinute() { return requestsPerMinute; }
        public void setRequestsPerMinute(int v) { this.requestsPerMinute = v; }
        public int getRequestsPerDay() { return requestsPerDay; }
        public void setRequestsPerDay(int v) { this.requestsPerDay = v; }
    }

    public static class AbuseCh {
        /** https://auth.abuse.ch/ dan olinadigan Auth-Key (MalwareBazaar va URLhaus uchun bitta). */
        private String authKey = "";
        private String malwareBazaarUrl = "https://mb-api.abuse.ch/api/v1/";
        private String urlhausUrl = "https://urlhaus-api.abuse.ch/v1/url/";
        public String getAuthKey() { return authKey; }
        public void setAuthKey(String authKey) { this.authKey = authKey; }
        public String getMalwareBazaarUrl() { return malwareBazaarUrl; }
        public void setMalwareBazaarUrl(String v) { this.malwareBazaarUrl = v; }
        public String getUrlhausUrl() { return urlhausUrl; }
        public void setUrlhausUrl(String v) { this.urlhausUrl = v; }
    }

    public static class Google {
        private String apiKey = "";
        /** "web-risk" (tijoriy foydalanish uchun) yoki "safe-browsing" (faqat notijoriy). */
        private String mode = "web-risk";
        private String webRiskUrl = "https://webrisk.googleapis.com/v1/uris:search";
        private String safeBrowsingUrl = "https://safebrowsing.googleapis.com/v4/threatMatches:find";
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public String getWebRiskUrl() { return webRiskUrl; }
        public void setWebRiskUrl(String v) { this.webRiskUrl = v; }
        public String getSafeBrowsingUrl() { return safeBrowsingUrl; }
        public void setSafeBrowsingUrl(String v) { this.safeBrowsingUrl = v; }
    }

    public String getAppToken() { return appToken; }
    public void setAppToken(String appToken) { this.appToken = appToken; }
    public String getAdminToken() { return adminToken; }
    public void setAdminToken(String adminToken) { this.adminToken = adminToken; }
    public boolean isStripUrlQuery() { return stripUrlQuery; }
    public void setStripUrlQuery(boolean stripUrlQuery) { this.stripUrlQuery = stripUrlQuery; }
    public int getMaxHashesPerRequest() { return maxHashesPerRequest; }
    public void setMaxHashesPerRequest(int v) { this.maxHashesPerRequest = v; }
    public RateLimit getRateLimit() { return rateLimit; }
    public Cache getCache() { return cache; }
    public VirusTotal getVirustotal() { return virustotal; }
    public AbuseCh getAbusech() { return abusech; }
    public Google getGoogle() { return google; }
}
