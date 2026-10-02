package uz.sergak.api.service;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Sergak'ning o'z tahdid ro'yxati (IOC): O'zbekistonga xos zararli APK xeshlari va fishing domenlar.
 * Admin API orqali to'ldiriladi (masalan, IIV / CERT / Group-IB e'lonlaridan) va ilovaga /v1/feed orqali tarqatiladi.
 */
@Repository
public class IocStore {

    public enum Type { SHA256, SHA1, DOMAIN }

    public record Ioc(long id, Type type, String value, String label, String source) {}

    private final JdbcClient jdbc;

    public IocStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Ioc> all() {
        return jdbc.sql("SELECT id, ioc_type, ioc_value, label, source FROM ioc ORDER BY id")
                .query((rs, i) -> new Ioc(rs.getLong("id"), Type.valueOf(rs.getString("ioc_type")),
                        rs.getString("ioc_value"), rs.getString("label"), rs.getString("source")))
                .list();
    }

    public long version() {
        Long v = jdbc.sql("SELECT COALESCE(MAX(id), 0) + COUNT(*) FROM ioc").query(Long.class).single();
        return v == null ? 0 : v;
    }

    public Optional<Ioc> findHash(String sha256) {
        return jdbc.sql("SELECT id, ioc_type, ioc_value, label, source FROM ioc WHERE ioc_type = 'SHA256' AND ioc_value = :v")
                .param("v", sha256.toLowerCase(Locale.ROOT))
                .query((rs, i) -> new Ioc(rs.getLong("id"), Type.SHA256, rs.getString("ioc_value"), rs.getString("label"), rs.getString("source")))
                .optional();
    }

    /** Domenning o'zi yoki istalgan ota-domeni ro'yxatda bormi (a.b.evil.top -> evil.top). */
    public Optional<Ioc> findDomain(String host) {
        if (host == null || host.isBlank()) return Optional.empty();
        String h = host.toLowerCase(Locale.ROOT);
        List<String> candidates = new java.util.ArrayList<>();
        while (h.contains(".")) {
            candidates.add(h);
            h = h.substring(h.indexOf('.') + 1);
        }
        if (candidates.isEmpty()) return Optional.empty();
        return jdbc.sql("SELECT id, ioc_type, ioc_value, label, source FROM ioc WHERE ioc_type = 'DOMAIN' AND ioc_value IN (:v)")
                .param("v", candidates)
                .query((rs, i) -> new Ioc(rs.getLong("id"), Type.DOMAIN, rs.getString("ioc_value"), rs.getString("label"), rs.getString("source")))
                .list().stream().findFirst();
    }

    public long add(Type type, String value, String label, String source) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        jdbc.sql("DELETE FROM ioc WHERE ioc_type = :t AND ioc_value = :v").param("t", type.name()).param("v", v).update();
        jdbc.sql("INSERT INTO ioc (ioc_type, ioc_value, label, source) VALUES (:t, :v, :l, :s)")
                .param("t", type.name()).param("v", v).param("l", label).param("s", source)
                .update();
        return jdbc.sql("SELECT id FROM ioc WHERE ioc_type = :t AND ioc_value = :v").param("t", type.name()).param("v", v)
                .query(Long.class).single();
    }

    public boolean delete(long id) {
        return jdbc.sql("DELETE FROM ioc WHERE id = :id").param("id", id).update() > 0;
    }
}
