package uz.sergak.api.provider;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import uz.sergak.api.config.SergakProperties;
import uz.sergak.api.model.ProviderResult;
import uz.sergak.api.model.Status;

/** abuse.ch URLhaus: virus tarqatayotgan havolalar bazasi. */
@Component
public class UrlhausProvider implements UrlProvider {

    private static final Logger log = LoggerFactory.getLogger(UrlhausProvider.class);
    public static final String NAME = "URLhaus";

    private final RestClient client;
    private final String authKey;
    private final String endpoint;

    public UrlhausProvider(RestClient.Builder builder, SergakProperties props) {
        this.authKey = props.getAbusech().getAuthKey() == null ? "" : props.getAbusech().getAuthKey().trim();
        this.endpoint = props.getAbusech().getUrlhausUrl();
        this.client = builder.clone().build();
    }

    @Override public String name() { return NAME; }
    @Override public boolean enabled() { return !authKey.isEmpty(); }
    @Override public int priority() { return 10; }

    @Override
    public ProviderResult lookupUrl(String url) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("url", url);
        try {
            JsonNode body = client.post()
                    .uri(endpoint)
                    .header("Auth-Key", authKey)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            return parse(body);
        } catch (Exception e) {
            log.warn("URLhaus lookup failed: {}", e.getClass().getSimpleName());
            return ProviderResult.skipped(NAME);
        }
    }

    static ProviderResult parse(JsonNode body) {
        if (body == null || !"ok".equals(body.path("query_status").asText(""))) return ProviderResult.unknown(NAME);
        String threat = body.path("threat").asText("malware_download");
        return ProviderResult.of(NAME, Status.MALICIOUS, threat);
    }
}
