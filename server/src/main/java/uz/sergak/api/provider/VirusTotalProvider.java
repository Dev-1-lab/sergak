package uz.sergak.api.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import uz.sergak.api.config.SergakProperties;
import uz.sergak.api.model.ProviderResult;
import uz.sergak.api.model.Status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;

/**
 * VirusTotal API v3.
 * - Fayllar: GET /files/{sha256} — faqat XESH yuboriladi, fayl hech qachon yuklanmaydi.
 * - Havolalar: GET /urls/{base64url(url)} — faqat qidiruv. Noma'lum havolalar VirusTotal'ga
 *   YUBORILMAYDI (yuborilgan havolalar VT hamjamiyatiga ochiq bo'lib qoladi va shaxsiy ma'lumot sizishi mumkin).
 */
@Component
public class VirusTotalProvider implements HashProvider, UrlProvider {

    private static final Logger log = LoggerFactory.getLogger(VirusTotalProvider.class);
    public static final String NAME = "VirusTotal";

    private final RestClient client;
    private final String apiKey;
    private final QuotaLimiter quota;

    public VirusTotalProvider(RestClient.Builder builder, SergakProperties props, Clock clock) {
        SergakProperties.VirusTotal vt = props.getVirustotal();
        this.apiKey = vt.getApiKey() == null ? "" : vt.getApiKey().trim();
        this.client = builder.clone().baseUrl(vt.getBaseUrl()).build();
        this.quota = new QuotaLimiter(vt.getRequestsPerMinute(), vt.getRequestsPerDay(), clock);
    }

    @Override public String name() { return NAME; }
    @Override public boolean enabled() { return !apiKey.isEmpty(); }
    @Override public int priority() { return 50; }

    @Override
    public ProviderResult lookupHash(String sha256) {
        return fetch("/files/{id}", sha256, 3);
    }

    @Override
    public ProviderResult lookupUrl(String url) {
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(url.getBytes(StandardCharsets.UTF_8));
        return fetch("/urls/{id}", id, 2);
    }

    private ProviderResult fetch(String path, String id, int maliciousThreshold) {
        if (!quota.tryAcquire()) return ProviderResult.skipped(NAME);
        try {
            JsonNode body = client.get().uri(path, id)
                    .header("x-apikey", apiKey)
                    .retrieve()
                    .body(JsonNode.class);
            return parse(body, maliciousThreshold);
        } catch (HttpClientErrorException.NotFound e) {
            return ProviderResult.unknown(NAME);
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("VirusTotal quota exceeded");
            return ProviderResult.skipped(NAME);
        } catch (Exception e) {
            log.warn("VirusTotal lookup failed: {}", e.getClass().getSimpleName());
            return ProviderResult.skipped(NAME);
        }
    }

    static ProviderResult parse(JsonNode body, int maliciousThreshold) {
        if (body == null) return ProviderResult.unknown(NAME);
        JsonNode attrs = body.path("data").path("attributes");
        JsonNode stats = attrs.path("last_analysis_stats");
        if (stats.isMissingNode()) return ProviderResult.unknown(NAME);
        int malicious = stats.path("malicious").asInt(0);
        int suspicious = stats.path("suspicious").asInt(0);
        int engines = malicious + suspicious + stats.path("undetected").asInt(0) + stats.path("harmless").asInt(0);
        String label = textOrNull(attrs.path("popular_threat_classification").path("suggested_threat_label"));
        if (label == null) {
            JsonNode cats = attrs.path("categories");
            if (cats.isObject() && cats.size() > 0) label = cats.elements().next().asText();
        }
        Status status;
        if (malicious >= maliciousThreshold) status = Status.MALICIOUS;
        else if (malicious > 0 || suspicious >= 2) status = Status.SUSPICIOUS;
        else if (engines > 0) status = Status.CLEAN;
        else status = Status.UNKNOWN;
        return new ProviderResult(NAME, status, malicious, engines, status == Status.CLEAN ? null : label, false);
    }

    private static String textOrNull(JsonNode n) {
        return n == null || n.isMissingNode() || n.isNull() || n.asText().isBlank() ? null : n.asText();
    }
}
