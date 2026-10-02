package uz.sergak.api.model;

/**
 * Bitta provayder (VirusTotal, MalwareBazaar ...) javobi.
 *
 * @param skipped kvota tugagani yoki xato sababli so'rov bajarilmadi — natija keshlanmaydi
 */
public record ProviderResult(String source, Status status, int detections, int engines, String label, boolean skipped) {

    public static ProviderResult unknown(String source) {
        return new ProviderResult(source, Status.UNKNOWN, 0, 0, null, false);
    }

    public static ProviderResult skipped(String source) {
        return new ProviderResult(source, Status.UNKNOWN, 0, 0, null, true);
    }

    public static ProviderResult of(String source, Status status, String label) {
        return new ProviderResult(source, status, 0, 0, label, false);
    }
}
