package uz.sergak.api.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import uz.sergak.api.config.SergakProperties;
import uz.sergak.api.model.ProviderResult;
import uz.sergak.api.model.Status;

import java.util.List;
import java.util.Map;

/**
 * Google xavfli saytlar bazasi.
 * - mode=web-risk: Google Cloud Web Risk API (tijoriy foydalanish uchun ruxsat etilgan).
 * - mode=safe-browsing: Safe Browsing Lookup API v4 (faqat notijoriy loyihalar uchun).
 * Google talabi: ogohlantirishda "ehtimol" kabi so'z va Google'ga havola ko'rsatiladi (ilovada bajarilgan).
 */
@Component
public class GoogleUrlProvider implements UrlProvider {

    private static final Logger log = LoggerFactory.getLogger(GoogleUrlProvider.class);
    private static final List<String> THREATS = List.of("MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE");

    private final RestClient client;
    private final SergakProperties.Google cfg;
    private final String apiKey;

    public GoogleUrlProvider(RestClient.Builder builder, SergakProperties props) {
        this.cfg = props.getGoogle();
        this.apiKey = cfg.getApiKey() == null ? "" : cfg.getApiKey().trim();
        this.client = builder.clone().build();
    }

    private boolean webRisk() { return !"safe-browsing".equalsIgnoreCase(cfg.getMode()); }

    @Override public String name() { return webRisk() ? "Google Web Risk" : "Google Safe Browsing"; }
    @Override public boolean enabled() { return !apiKey.isEmpty(); }
    @Override public int priority() { return 20; }

    @Override
    public ProviderResult lookupUrl(String url) {
        try {
            return webRisk() ? webRiskLookup(url) : safeBrowsingLookup(url);
        } catch (Exception e) {
            log.warn("{} lookup failed: {}", name(), e.getClass().getSimpleName());
            return ProviderResult.skipped(name());
        }
    }

    private ProviderResult webRiskLookup(String url) {
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(cfg.getWebRiskUrl());
        THREATS.forEach(t -> b.queryParam("threatTypes", t));
        b.queryParam("uri", "{uri}").queryParam("key", "{key}");
        java.net.URI target = b.encode().buildAndExpand(url, apiKey).toUri();
        JsonNode body = client.get().uri(target).retrieve().body(JsonNode.class);
        JsonNode types = body == null ? null : body.path("threat").path("threatTypes");
        if (types != null && types.isArray() && types.size() > 0) {
            return ProviderResult.of(name(), Status.MALICIOUS, types.get(0).asText());
        }
        return ProviderResult.of(name(), Status.CLEAN, null);
    }

    private ProviderResult safeBrowsingLookup(String url) {
        Map<String, Object> req = Map.of(
                "client", Map.of("clientId", "sergak", "clientVersion", "0.1"),
                "threatInfo", Map.of(
                        "threatTypes", THREATS,
                        "platformTypes", List.of("ANY_PLATFORM"),
                        "threatEntryTypes", List.of("URL"),
                        "threatEntries", List.of(Map.of("url", url))
                )
        );
        java.net.URI uri = UriComponentsBuilder.fromUriString(cfg.getSafeBrowsingUrl()).queryParam("key", "{key}").encode().buildAndExpand(apiKey).toUri();
        JsonNode body = client.post().uri(uri).contentType(MediaType.APPLICATION_JSON).body(req).retrieve().body(JsonNode.class);
        JsonNode matches = body == null ? null : body.path("matches");
        if (matches != null && matches.isArray() && matches.size() > 0) {
            return ProviderResult.of(name(), Status.MALICIOUS, matches.get(0).path("threatType").asText(null));
        }
        return ProviderResult.of(name(), Status.CLEAN, null);
    }
}
