package uz.sergak.api.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import uz.sergak.api.config.SergakProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * /v1/** uchun:
 *  1) ixtiyoriy ilova tokeni (X-Sergak-App) — oddiy botlarni to'sish uchun;
 *  2) har bir o'rnatma (X-Install-Id) va IP bo'yicha so'rovlar chegarasi — VirusTotal kvotasini himoya qiladi.
 * IP manzillar va o'rnatma ID'lari faqat xotirada, qisqa muddat saqlanadi.
 */
@Component
public class ClientGuardFilter extends OncePerRequestFilter {

    private static final Pattern INSTALL_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    private final SergakProperties props;
    private final Clock clock;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public ClientGuardFilter(SergakProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String expected = props.getAppToken();
        if (expected != null && !expected.isBlank()) {
            String got = req.getHeader("X-Sergak-App");
            if (got == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), got.getBytes(StandardCharsets.UTF_8))) {
                res.sendError(401);
                return;
            }
        }
        if (!req.getRequestURI().equals("/v1/feed") && !req.getRequestURI().equals("/v1/info")) {
            String id = req.getHeader("X-Install-Id");
            String key = (id != null && INSTALL_ID.matcher(id).matches()) ? "i:" + id : "ip:" + clientIp(req);
            if (!allow(key) || !allow("ip:" + clientIp(req) + ":all")) {
                res.setHeader("Retry-After", String.valueOf(props.getRateLimit().getWindowSeconds()));
                res.sendError(429);
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private boolean allow(String key) {
        long now = clock.millis();
        long window = props.getRateLimit().getWindowSeconds() * 1000L;
        int limit = key.endsWith(":all") ? props.getRateLimit().getRequests() * 5 : props.getRateLimit().getRequests();
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < now - window) q.pollFirst();
            if (q.size() >= limit) return false;
            q.addLast(now);
        }
        if (hits.size() > 100_000) hits.entrySet().removeIf(e -> e.getValue().isEmpty());
        return true;
    }

    private static String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
