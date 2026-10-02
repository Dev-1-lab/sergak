package uz.sergak.api.service;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/** Havolani bir xil ko'rinishga keltiradi va ixtiyoriy ravishda ?query / #fragment ni olib tashlaydi. */
public final class UrlCanonicalizer {
    private UrlCanonicalizer() {}

    public static Optional<String> canonical(String raw, boolean stripQuery) {
        if (raw == null) return Optional.empty();
        String s = raw.trim();
        if (s.isEmpty() || s.length() > 2048 || s.chars().anyMatch(Character::isWhitespace)) return Optional.empty();
        if (s.regionMatches(true, 0, "hxxp", 0, 4)) s = "http" + s.substring(4);
        if (!s.contains("://")) s = "http://" + s;
        try {
            URI u = new URI(s);
            String scheme = u.getScheme() == null ? "http" : u.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) return Optional.empty();
            String host = u.getHost();
            if (host == null || !host.contains(".")) return Optional.empty();
            StringBuilder b = new StringBuilder(scheme).append("://").append(host.toLowerCase(Locale.ROOT));
            if (u.getPort() > 0) b.append(':').append(u.getPort());
            String path = u.getRawPath();
            b.append(path == null || path.isEmpty() ? "/" : path);
            if (!stripQuery && u.getRawQuery() != null) b.append('?').append(u.getRawQuery());
            return Optional.of(b.toString());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static String host(String canonicalUrl) {
        try {
            return URI.create(canonicalUrl).getHost();
        } catch (Exception e) {
            return "";
        }
    }
}
