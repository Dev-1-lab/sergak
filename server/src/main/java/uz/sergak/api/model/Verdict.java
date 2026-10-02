package uz.sergak.api.model;

import java.util.List;

/**
 * Ilovaga qaytariladigan yakuniy xulosa. Android'dagi uz.sergak.core.Reputation bilan bir xil maydonlar.
 *
 * @param partial ba'zi provayderlar kvota sababli tekshirilmadi — ilova keyinroq qayta so'rashi mumkin
 */
public record Verdict(
        String key,
        Status status,
        int detections,
        int engines,
        String label,
        List<String> sources,
        boolean cached,
        boolean partial
) {
    public Verdict withCached(boolean c) {
        return new Verdict(key, status, detections, engines, label, sources, c, partial);
    }
}
