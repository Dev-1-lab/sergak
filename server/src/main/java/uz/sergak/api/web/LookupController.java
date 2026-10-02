package uz.sergak.api.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.sergak.api.config.SergakProperties;
import uz.sergak.api.model.Verdict;
import uz.sergak.api.service.IocStore;
import uz.sergak.api.service.ReputationService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Ilova uchun API. Ilova faqat quyidagilarni yuboradi:
 *  - o'rnatilgan/tekshirilayotgan APK fayllarning SHA-256 XESHI (faylning o'zi emas)
 *  - foydalanuvchi o'zi tekshirmoqchi bo'lgan havola
 * Xabar matnlari hech qachon serverga kelmaydi.
 */
@RestController
@RequestMapping("/v1")
public class LookupController {

    private static final Pattern SHA256 = Pattern.compile("^[a-f0-9]{64}$");

    private final ReputationService service;
    private final IocStore iocs;
    private final SergakProperties props;

    public LookupController(ReputationService service, IocStore iocs, SergakProperties props) {
        this.service = service;
        this.iocs = iocs;
        this.props = props;
    }

    public record HashRequest(@NotEmpty List<@NotBlank String> sha256) {}

    public record HashResponse(List<Verdict> results) {}

    public record UrlRequest(@NotBlank @Size(max = 2048) String url) {}

    public record Feed(long version, Map<String, String> hashes, List<String> domains) {}

    public record Info(String service, List<String> providers) {}

    @GetMapping("/info")
    public Info info() {
        return new Info("sergak-api", service.enabledProviders());
    }

    @PostMapping("/hashes")
    public ResponseEntity<?> hashes(@Valid @RequestBody HashRequest req) {
        if (req.sha256().size() > props.getMaxHashesPerRequest()) {
            return ResponseEntity.badRequest().body(Map.of("error", "too_many_hashes", "max", props.getMaxHashesPerRequest()));
        }
        List<String> clean = req.sha256().stream().map(s -> s.trim().toLowerCase(Locale.ROOT)).distinct().toList();
        if (clean.stream().anyMatch(h -> !SHA256.matcher(h).matches())) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_sha256"));
        }
        return ResponseEntity.ok(new HashResponse(clean.stream().map(service::checkHash).toList()));
    }

    @PostMapping("/url")
    public ResponseEntity<?> url(@Valid @RequestBody UrlRequest req) {
        return service.checkUrl(req.url())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.badRequest().body(Map.of("error", "invalid_url")));
    }

    /** Ilova kuniga bir marta yuklab oladigan IOC ro'yxati — oflayn holatda ham ishlashi uchun. */
    @GetMapping("/feed")
    public ResponseEntity<Feed> feed(@RequestHeader(value = "If-None-Match", required = false) String etag) {
        long version = iocs.version();
        String tag = "\"" + version + "\"";
        if (tag.equals(etag)) return ResponseEntity.status(304).eTag(tag).build();
        Map<String, String> hashes = new LinkedHashMap<>();
        List<String> domains = new java.util.ArrayList<>();
        for (IocStore.Ioc i : iocs.all()) {
            switch (i.type()) {
                case SHA256, SHA1 -> hashes.put(i.value(), i.label());
                case DOMAIN -> domains.add(i.value());
            }
        }
        return ResponseEntity.ok().eTag(tag).body(new Feed(version, hashes, domains));
    }
}
