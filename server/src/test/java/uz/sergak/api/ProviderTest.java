package uz.sergak.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import uz.sergak.api.config.SergakProperties;
import uz.sergak.api.model.ProviderResult;
import uz.sergak.api.model.Status;
import uz.sergak.api.provider.GoogleUrlProvider;
import uz.sergak.api.provider.MalwareBazaarProvider;
import uz.sergak.api.provider.QuotaLimiter;
import uz.sergak.api.provider.UrlhausProvider;
import uz.sergak.api.provider.VirusTotalProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProviderTest {

    private static final String HASH = "a".repeat(64);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-02T10:00:00Z"), ZoneOffset.UTC);

    private SergakProperties props() {
        SergakProperties p = new SergakProperties();
        p.getVirustotal().setApiKey("vt-key");
        p.getAbusech().setAuthKey("abuse-key");
        p.getGoogle().setApiKey("g-key");
        return p;
    }

    @Test
    void virusTotalMaliciousFile() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("https://www.virustotal.com/api/v3/files/" + HASH))
                .andExpect(header("x-apikey", "vt-key"))
                .andRespond(withSuccess("""
                        {"data":{"attributes":{
                          "last_analysis_stats":{"malicious":31,"suspicious":0,"undetected":35,"harmless":0},
                          "popular_threat_classification":{"suggested_threat_label":"trojan.smsspy/wonderland"}}}}
                        """, MediaType.APPLICATION_JSON));
        ProviderResult r = new VirusTotalProvider(b, props(), clock).lookupHash(HASH);
        assertThat(r.status()).isEqualTo(Status.MALICIOUS);
        assertThat(r.detections()).isEqualTo(31);
        assertThat(r.engines()).isEqualTo(66);
        assertThat(r.label()).contains("smsspy");
        server.verify();
    }

    @Test
    void virusTotalNotFoundIsUnknownAndUrlIdIsBase64Url() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        // base64url("http://evil.top/") without padding
        server.expect(requestTo("https://www.virustotal.com/api/v3/urls/aHR0cDovL2V2aWwudG9wLw"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        ProviderResult r = new VirusTotalProvider(b, props(), clock).lookupUrl("http://evil.top/");
        assertThat(r.status()).isEqualTo(Status.UNKNOWN);
        assertThat(r.skipped()).isFalse();
        server.verify();
    }

    @Test
    void virusTotalQuotaIsRespected() {
        QuotaLimiter q = new QuotaLimiter(4, 500, clock);
        for (int i = 0; i < 4; i++) assertThat(q.tryAcquire()).isTrue();
        assertThat(q.tryAcquire()).isFalse();
    }

    @Test
    void malwareBazaarHit() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("https://mb-api.abuse.ch/api/v1/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Auth-Key", "abuse-key"))
                .andRespond(withSuccess("""
                        {"query_status":"ok","data":[{"sha256_hash":"%s","signature":"Ajina.Banker","tags":["apk"]}]}
                        """.formatted(HASH), MediaType.APPLICATION_JSON));
        ProviderResult r = new MalwareBazaarProvider(b, props()).lookupHash(HASH);
        assertThat(r.status()).isEqualTo(Status.MALICIOUS);
        assertThat(r.label()).isEqualTo("Ajina.Banker");
        server.verify();
    }

    @Test
    void malwareBazaarMissIsUnknown() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("https://mb-api.abuse.ch/api/v1/"))
                .andRespond(withSuccess("{\"query_status\":\"hash_not_found\"}", MediaType.APPLICATION_JSON));
        assertThat(new MalwareBazaarProvider(b, props()).lookupHash(HASH).status()).isEqualTo(Status.UNKNOWN);
    }

    @Test
    void urlhausHit() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("https://urlhaus-api.abuse.ch/v1/url/"))
                .andRespond(withSuccess("{\"query_status\":\"ok\",\"threat\":\"malware_download\",\"url_status\":\"online\"}", MediaType.APPLICATION_JSON));
        ProviderResult r = new UrlhausProvider(b, props()).lookupUrl("http://evil.top/a.apk");
        assertThat(r.status()).isEqualTo(Status.MALICIOUS);
        assertThat(r.label()).isEqualTo("malware_download");
    }

    @Test
    void webRiskHitAndMiss() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo(allOf(startsWith("https://webrisk.googleapis.com/v1/uris:search?threatTypes=MALWARE"),
                        containsString("phish.top"), containsString("key=g-key"))))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"threat\":{\"threatTypes\":[\"SOCIAL_ENGINEERING\"]}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(allOf(startsWith("https://webrisk.googleapis.com/v1/uris:search"), containsString("click.uz"))))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        GoogleUrlProvider g = new GoogleUrlProvider(b, props());
        assertThat(g.lookupUrl("http://phish.top/").status()).isEqualTo(Status.MALICIOUS);
        assertThat(g.lookupUrl("https://click.uz/").status()).isEqualTo(Status.CLEAN);
        assertThat(g.name()).isEqualTo("Google Web Risk");
        server.verify();
    }
}
