package uz.sergak.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.sergak.api.config.SergakProperties;
import uz.sergak.api.model.ProviderResult;
import uz.sergak.api.model.Status;
import uz.sergak.api.model.Verdict;
import uz.sergak.api.provider.HashProvider;
import uz.sergak.api.provider.UrlProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Asosiy mantiq: kesh -> o'z IOC ro'yxati -> tashqi provayderlar (kvotasi keng manbalar avval).
 * Aniq "zararli" topilsa, qolgan provayderlar chaqirilmaydi (kvota tejaladi).
 */
@Service
public class ReputationService {

    private static final Logger log = LoggerFactory.getLogger(ReputationService.class);

    private final List<HashProvider> hashProviders;
    private final List<UrlProvider> urlProviders;
    private final ReputationStore store;
    private final IocStore iocs;
    private final SergakProperties props;
    private final Clock clock;

    public ReputationService(List<HashProvider> hashProviders, List<UrlProvider> urlProviders,
                             ReputationStore store, IocStore iocs, SergakProperties props, Clock clock) {
        this.hashProviders = hashProviders.stream().sorted(Comparator.comparingInt(HashProvider::priority)).toList();
        this.urlProviders = urlProviders.stream().sorted(Comparator.comparingInt(UrlProvider::priority)).toList();
        this.store = store;
        this.iocs = iocs;
        this.props = props;
        this.clock = clock;
    }

    public List<String> enabledProviders() {
        List<String> out = new ArrayList<>();
        hashProviders.stream().filter(HashProvider::enabled).forEach(p -> out.add(p.name() + " (fayl)"));
        urlProviders.stream().filter(UrlProvider::enabled).forEach(p -> out.add(p.name() + " (havola)"));
        return out;
    }

    public Verdict checkHash(String sha256) {
        String key = sha256.toLowerCase();
        Instant now = clock.instant();

        Optional<IocStore.Ioc> ioc = iocs.findHash(key);
        if (ioc.isPresent()) {
            return new Verdict(key, Status.MALICIOUS, 0, 0, ioc.get().label(), List.of("Sergak IOC"), false, false);
        }
        Optional<Verdict> cached = store.find("hash", key, now);
        if (cached.isPresent()) return cached.get();

        List<ProviderResult> results = new ArrayList<>();
        for (HashProvider p : hashProviders) {
            if (!p.enabled()) continue;
            ProviderResult r = p.lookupHash(key);
            results.add(r);
            if (r.status() == Status.MALICIOUS) break;
        }
        Verdict v = Aggregator.combine(key, results);
        cache("hash", v, now);
        return v;
    }

    public Optional<Verdict> checkUrl(String rawUrl) {
        Optional<String> canon = UrlCanonicalizer.canonical(rawUrl, props.isStripUrlQuery());
        if (canon.isEmpty()) return Optional.empty();
        String url = canon.get();
        Instant now = clock.instant();

        Optional<IocStore.Ioc> ioc = iocs.findDomain(UrlCanonicalizer.host(url));
        if (ioc.isPresent()) {
            return Optional.of(new Verdict(url, Status.MALICIOUS, 0, 0, ioc.get().label(), List.of("Sergak IOC"), false, false));
        }
        Optional<Verdict> cached = store.find("url", url, now);
        if (cached.isPresent()) return cached;

        List<ProviderResult> results = new ArrayList<>();
        for (UrlProvider p : urlProviders) {
            if (!p.enabled()) continue;
            ProviderResult r = p.lookupUrl(url);
            results.add(r);
            if (r.status() == Status.MALICIOUS) break;
        }
        Verdict v = Aggregator.combine(url, results);
        cache("url", v, now);
        return Optional.of(v);
    }

    private void cache(String kind, Verdict v, Instant now) {
        // Qisman (kvota tugagan) natijani keshlamaymiz — keyingi so'rovda to'liq tekshiriladi.
        if (v.partial() && v.status() != Status.MALICIOUS) return;
        if (v.sources().isEmpty() && v.status() == Status.UNKNOWN && enabledProviders().isEmpty()) return;
        SergakProperties.Cache c = props.getCache();
        int hours = switch (v.status()) {
            case MALICIOUS -> c.getMaliciousHours();
            case SUSPICIOUS -> c.getSuspiciousHours();
            case CLEAN -> c.getCleanHours();
            case UNKNOWN -> c.getUnknownHours();
        };
        try {
            store.save(kind, v, now, now.plus(Duration.ofHours(hours)));
        } catch (Exception e) {
            log.warn("Cache write failed: {}", e.getClass().getSimpleName());
        }
    }

    @Scheduled(fixedDelayString = "PT6H", initialDelayString = "PT10M")
    public void purge() {
        int n = store.purgeExpired(clock.instant());
        if (n > 0) log.info("Purged {} expired reputation entries", n);
    }
}
