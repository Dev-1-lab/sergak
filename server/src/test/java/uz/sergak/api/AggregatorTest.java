package uz.sergak.api;

import org.junit.jupiter.api.Test;
import uz.sergak.api.model.ProviderResult;
import uz.sergak.api.model.Status;
import uz.sergak.api.model.Verdict;
import uz.sergak.api.service.Aggregator;
import uz.sergak.api.service.UrlCanonicalizer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AggregatorTest {

    @Test
    void worstStatusWinsAndLabelComesFromIt() {
        Verdict v = Aggregator.combine("k", List.of(
                new ProviderResult("VirusTotal", Status.SUSPICIOUS, 1, 70, "generic", false),
                ProviderResult.of("MalwareBazaar", Status.MALICIOUS, "Wonderland")));
        assertThat(v.status()).isEqualTo(Status.MALICIOUS);
        assertThat(v.label()).isEqualTo("Wonderland");
        assertThat(v.engines()).isEqualTo(70);
        assertThat(v.sources()).containsExactlyInAnyOrder("VirusTotal", "MalwareBazaar");
    }

    @Test
    void skippedProvidersMarkPartial() {
        Verdict v = Aggregator.combine("k", List.of(ProviderResult.skipped("VirusTotal"), ProviderResult.unknown("MalwareBazaar")));
        assertThat(v.status()).isEqualTo(Status.UNKNOWN);
        assertThat(v.partial()).isTrue();
        assertThat(v.sources()).isEmpty();
    }

    @Test
    void cleanOnlyWhenSomeoneSaysClean() {
        Verdict v = Aggregator.combine("k", List.of(new ProviderResult("VirusTotal", Status.CLEAN, 0, 68, null, false)));
        assertThat(v.status()).isEqualTo(Status.CLEAN);
    }

    @Test
    void urlCanonicalization() {
        assertThat(UrlCanonicalizer.canonical("my-gov-uz.online/tolov?id=123&token=secret#x", true))
                .contains("http://my-gov-uz.online/tolov");
        assertThat(UrlCanonicalizer.canonical("HTTPS://Click.UZ", false)).contains("https://click.uz/");
        assertThat(UrlCanonicalizer.canonical("hxxp://evil.top/a.apk", true)).contains("http://evil.top/a.apk");
        assertThat(UrlCanonicalizer.canonical("javascript:alert(1)", true)).isEmpty();
        assertThat(UrlCanonicalizer.canonical("not a url", true)).isEmpty();
    }
}
