package uz.sergak.api.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.sergak.api.config.SergakProperties;
import uz.sergak.api.service.IocStore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** IOC ro'yxatini boshqarish. X-Admin-Token talab qilinadi; SERGAK_ADMIN_TOKEN bo'sh bo'lsa — o'chiq. */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private static final Pattern SHA256 = Pattern.compile("^[a-f0-9]{64}$");
    private static final Pattern SHA1 = Pattern.compile("^[a-f0-9]{40}$");
    private static final Pattern DOMAIN = Pattern.compile("^(?=.{4,253}$)([a-z0-9-]{1,63}\\.)+[a-z]{2,63}$");

    private final IocStore iocs;
    private final SergakProperties props;

    public AdminController(IocStore iocs, SergakProperties props) {
        this.iocs = iocs;
        this.props = props;
    }

    public record NewIoc(@NotNull IocStore.Type type, @NotBlank @Size(max = 253) String value,
                         @Size(max = 200) String label, @Size(max = 200) String source) {}

    private boolean authorized(String token) {
        String expected = props.getAdminToken();
        if (expected == null || expected.isBlank() || token == null) return false;
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/ioc")
    public ResponseEntity<?> list(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!authorized(token)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        List<IocStore.Ioc> all = iocs.all();
        return ResponseEntity.ok(all);
    }

    @PostMapping("/ioc")
    public ResponseEntity<?> add(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                 @Valid @RequestBody NewIoc req) {
        if (!authorized(token)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String v = req.value().trim().toLowerCase();
        boolean ok = switch (req.type()) {
            case SHA256 -> SHA256.matcher(v).matches();
            case SHA1 -> SHA1.matcher(v).matches();
            case DOMAIN -> DOMAIN.matcher(v).matches();
        };
        if (!ok) return ResponseEntity.badRequest().body(Map.of("error", "invalid_value"));
        long id = iocs.add(req.type(), v, req.label(), req.source());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id));
    }

    @DeleteMapping("/ioc/{id}")
    public ResponseEntity<?> delete(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                    @PathVariable long id) {
        if (!authorized(token)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return iocs.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
