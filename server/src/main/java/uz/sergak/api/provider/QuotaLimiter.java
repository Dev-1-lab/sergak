package uz.sergak.api.provider;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;

/** Provayder kvotasini (daqiqa va kun bo'yicha) himoya qiladi. VirusTotal Public API: 4/daqiqa, 500/kun. */
public class QuotaLimiter {
    private final int perMinute;
    private final int perDay;
    private final Clock clock;
    private final Deque<Instant> lastMinute = new ArrayDeque<>();
    private LocalDate day;
    private int usedToday;

    public QuotaLimiter(int perMinute, int perDay, Clock clock) {
        this.perMinute = perMinute;
        this.perDay = perDay;
        this.clock = clock;
    }

    public synchronized boolean tryAcquire() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        if (!today.equals(day)) {
            day = today;
            usedToday = 0;
        }
        while (!lastMinute.isEmpty() && lastMinute.peekFirst().isBefore(now.minusSeconds(60))) {
            lastMinute.pollFirst();
        }
        if (perMinute > 0 && lastMinute.size() >= perMinute) return false;
        if (perDay > 0 && usedToday >= perDay) return false;
        lastMinute.addLast(now);
        usedToday++;
        return true;
    }
}
