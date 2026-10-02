package uz.sergak.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import uz.sergak.api.model.Status;
import uz.sergak.api.model.Verdict;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Provayder javoblari keshi (PostgreSQL). VirusTotal kvotasini tejash uchun muhim. */
@Repository
public class ReputationStore {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public ReputationStore(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Optional<Verdict> find(String kind, String key, Instant now) {
        return jdbc.sql("""
                        SELECT status, detections, engines, label, sources FROM reputation
                        WHERE kind = :kind AND lookup_key = :key AND expires_at > :now
                        """)
                .param("kind", kind).param("key", key).param("now", Timestamp.from(now))
                .query((rs, i) -> new Verdict(
                        key,
                        Status.valueOf(rs.getString("status")),
                        rs.getInt("detections"),
                        rs.getInt("engines"),
                        rs.getString("label"),
                        readSources(rs.getString("sources")),
                        true,
                        false))
                .optional();
    }

    public void save(String kind, Verdict v, Instant now, Instant expires) {
        String sources = writeSources(v.sources());
        int updated = jdbc.sql("""
                        UPDATE reputation SET status = :status, detections = :det, engines = :eng, label = :label,
                               sources = :sources, fetched_at = :now, expires_at = :exp
                        WHERE kind = :kind AND lookup_key = :key
                        """)
                .param("status", v.status().name()).param("det", v.detections()).param("eng", v.engines())
                .param("label", v.label()).param("sources", sources)
                .param("now", Timestamp.from(now)).param("exp", Timestamp.from(expires))
                .param("kind", kind).param("key", v.key())
                .update();
        if (updated == 0) {
            try {
                jdbc.sql("""
                                INSERT INTO reputation (kind, lookup_key, status, detections, engines, label, sources, fetched_at, expires_at)
                                VALUES (:kind, :key, :status, :det, :eng, :label, :sources, :now, :exp)
                                """)
                        .param("kind", kind).param("key", v.key())
                        .param("status", v.status().name()).param("det", v.detections()).param("eng", v.engines())
                        .param("label", v.label()).param("sources", sources)
                        .param("now", Timestamp.from(now)).param("exp", Timestamp.from(expires))
                        .update();
            } catch (org.springframework.dao.DuplicateKeyException ignored) {
                // parallel so'rov allaqachon yozib qo'ydi
            }
        }
    }

    public int purgeExpired(Instant now) {
        return jdbc.sql("DELETE FROM reputation WHERE expires_at <= :now").param("now", Timestamp.from(now)).update();
    }

    private List<String> readSources(String s) {
        try {
            return s == null ? List.of() : json.readValue(s, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeSources(List<String> s) {
        try {
            return json.writeValueAsString(s);
        } catch (Exception e) {
            return "[]";
        }
    }
}
