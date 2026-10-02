package uz.sergak.api.service;

import uz.sergak.api.model.ProviderResult;
import uz.sergak.api.model.Status;
import uz.sergak.api.model.Verdict;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Bir nechta provayder javobini bitta xulosaga birlashtiradi: eng og'ir status g'olib. */
public final class Aggregator {
    private Aggregator() {}

    public static Verdict combine(String key, List<ProviderResult> results) {
        Status status = Status.UNKNOWN;
        String label = null;
        int detections = 0;
        int engines = 0;
        List<String> sources = new ArrayList<>();
        boolean partial = false;

        List<ProviderResult> sorted = new ArrayList<>(results);
        sorted.sort(Comparator.comparing((ProviderResult r) -> r.status().ordinal()).reversed());

        for (ProviderResult r : sorted) {
            if (r.skipped()) { partial = true; continue; }
            if (r.status() == Status.UNKNOWN) continue;
            sources.add(r.source());
            if (r.status().worseThan(status)) status = r.status();
            if (label == null && r.label() != null && r.status() == status) label = r.label();
            if (r.engines() > 0) {
                detections = Math.max(detections, r.detections());
                engines = Math.max(engines, r.engines());
            }
        }
        return new Verdict(key, status, detections, engines, label, List.copyOf(sources), false, partial);
    }
}
