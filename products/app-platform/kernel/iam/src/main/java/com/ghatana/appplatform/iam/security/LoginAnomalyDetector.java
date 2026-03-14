/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.iam.security;

import com.ghatana.appplatform.iam.audit.IamAuditEmitter;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.ZAddParams;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Detects login anomalies in real-time by inspecting the user's recent login history (K01-023).
 *
 * <p>Three detection rules:
 * <ol>
 *   <li><b>new_device</b> — IP address not seen before for this user (passive alert).</li>
 *   <li><b>impossible_travel</b> — Two logins more than {@value IMPOSSIBLE_TRAVEL_KM} km apart
 *       within {@value IMPOSSIBLE_TRAVEL_WINDOW_SECONDS} seconds.</li>
 *   <li><b>unusual_time</b> — Login hour falls in the quiet window 00:00–04:00 UTC.</li>
 * </ol>
 *
 * <p>Redis key: {@code login:history:{userId}} — sorted set, score = epoch seconds, member = JSON string.
 * History window kept to the last {@value MAX_HISTORY_ENTRIES} entries.
 *
 * @doc.type class
 * @doc.purpose Detects impossible-travel, new-device, and unusual-time login anomalies (K01-023)
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle perceive
 */
public final class LoginAnomalyDetector {

    private static final Logger log = LoggerFactory.getLogger(LoginAnomalyDetector.class);

    private static final String KEY_PREFIX = "login:history:";

    /** Maximum entries retained per user in the sorted set. */
    private static final int MAX_HISTORY_ENTRIES = 20;

    /** Quiet-hour window (UTC) that triggers unusual_time anomaly. */
    private static final int QUIET_HOUR_START = 0;   // inclusive
    private static final int QUIET_HOUR_END   = 4;   // exclusive

    /** Below this distance (km) we don't flag impossible travel. */
    private static final double IMPOSSIBLE_TRAVEL_KM = 500.0;

    /** Window (seconds) within which two logins separated by > {@value IMPOSSIBLE_TRAVEL_KM} km
     *  are considered impossible. */
    private static final long IMPOSSIBLE_TRAVEL_WINDOW_SECONDS = 3 * 60 * 60L; // 3 hours

    private final JedisPool jedis;
    private final Executor executor;
    private final IamAuditEmitter auditEmitter;
    private final GeoIpResolver geoIp;

    public LoginAnomalyDetector(JedisPool jedis, Executor executor,
                                 IamAuditEmitter auditEmitter, GeoIpResolver geoIp) {
        this.jedis        = Objects.requireNonNull(jedis, "jedis");
        this.executor     = Objects.requireNonNull(executor, "executor");
        this.auditEmitter = Objects.requireNonNull(auditEmitter, "auditEmitter");
        this.geoIp        = Objects.requireNonNull(geoIp, "geoIp");
    }

    // ─── Public API ────────────────────────────────────────────────────────────

    /**
     * Records a login attempt, evaluates anomaly rules, and emits audit events for each
     * detected anomaly. Returns the list of detected anomaly types.
     *
     * @param userId    principal ID
     * @param tenantId  tenant scope
     * @param sourceIp  IP address of the login request
     * @param userAgent User-Agent header value (used for device fingerprint)
     * @return list of detected anomaly types (may be empty)
     */
    public Promise<List<AnomalyType>> recordLogin(String userId, String tenantId,
                                                   String sourceIp, String userAgent) {
        return Promise.ofBlocking(executor, () -> {
            Instant now = Instant.now();
            GeoIpResolver.Coordinates coord = geoIp.resolve(sourceIp);
            String member = encodeEntry(sourceIp, userAgent, now, coord);
            String key    = KEY_PREFIX + userId;

            List<LoginEntry> history = readHistory(key);
            List<AnomalyType> anomalies = detectAnomalies(history, sourceIp, now, coord);

            persistEntry(key, member, now.getEpochSecond());

            return anomalies;
        }).then(anomalies ->
            emitAuditEvents(userId, tenantId, sourceIp, anomalies)
                .map(v -> anomalies)
        );
    }

    // ─── Detection rules ──────────────────────────────────────────────────────

    private List<AnomalyType> detectAnomalies(List<LoginEntry> history, String sourceIp,
                                               Instant now, GeoIpResolver.Coordinates coord) {
        List<AnomalyType> anomalies = new ArrayList<>();

        // Rule 1 — new device (IP not seen before)
        boolean knownIp = history.stream().anyMatch(e -> sourceIp.equals(e.ip()));
        if (!knownIp && !history.isEmpty()) {
            log.info("LoginAnomaly[new_device] ip={}", sourceIp);
            anomalies.add(AnomalyType.NEW_DEVICE);
        }

        // Rule 2 — impossible travel
        if (coord != null) {
            for (LoginEntry prior : history) {
                if (prior.coord() == null) continue;
                long deltaSec = now.getEpochSecond() - prior.epochSecond();
                if (deltaSec > 0 && deltaSec < IMPOSSIBLE_TRAVEL_WINDOW_SECONDS) {
                    double km = haversineKm(coord, prior.coord());
                    if (km > IMPOSSIBLE_TRAVEL_KM) {
                        log.warn("LoginAnomaly[impossible_travel] dist={}km window={}s",
                                (int) km, deltaSec);
                        anomalies.add(AnomalyType.IMPOSSIBLE_TRAVEL);
                        break;
                    }
                }
            }
        }

        // Rule 3 — unusual time (UTC quiet hours)
        int hour = now.atZone(java.time.ZoneOffset.UTC).getHour();
        if (hour >= QUIET_HOUR_START && hour < QUIET_HOUR_END) {
            log.info("LoginAnomaly[unusual_time] hour={}UTC", hour);
            anomalies.add(AnomalyType.UNUSUAL_TIME);
        }

        return anomalies;
    }

    // ─── Redis ────────────────────────────────────────────────────────────────

    private List<LoginEntry> readHistory(String key) {
        List<LoginEntry> entries = new ArrayList<>();
        try (var j = jedis.getResource()) {
            Set<String> members = j.zrange(key, 0, -1);
            for (String m : members) {
                LoginEntry e = decodeEntry(m);
                if (e != null) entries.add(e);
            }
        }
        return entries;
    }

    private void persistEntry(String key, String member, long scoreEpoch) {
        try (var j = jedis.getResource()) {
            j.zadd(key, scoreEpoch, member);
            // Trim to most recent MAX_HISTORY_ENTRIES
            long total = j.zcard(key);
            if (total > MAX_HISTORY_ENTRIES) {
                j.zremrangeByRank(key, 0, total - MAX_HISTORY_ENTRIES - 1);
            }
            // Expire the key after 90 days
            j.expire(key, 90 * 24 * 60 * 60L);
        }
    }

    // ─── Audit ────────────────────────────────────────────────────────────────

    private Promise<Void> emitAuditEvents(String userId, String tenantId,
                                           String sourceIp, List<AnomalyType> anomalies) {
        if (anomalies.isEmpty()) return Promise.complete();
        Promise<Void> chain = Promise.complete();
        for (AnomalyType type : anomalies) {
            String typeStr = type.name().toLowerCase();
            chain = chain.then(() ->
                auditEmitter.onLoginAnomaly(userId, tenantId, sourceIp, typeStr, "flag"));
        }
        return chain;
    }

    // ─── Serialisation helpers (pipe-delimited, no external JSON deps) ─────────

    /** Format: {@code ip|userAgent|epochSec|lat|lon} */
    private static String encodeEntry(String ip, String userAgent, Instant t,
                                       GeoIpResolver.Coordinates c) {
        return ip + "|" + sanitise(userAgent) + "|" + t.getEpochSecond()
             + "|" + (c != null ? c.lat() : "") + "|" + (c != null ? c.lon() : "");
    }

    private static LoginEntry decodeEntry(String s) {
        String[] parts = s.split("\\|", 5);
        if (parts.length < 5) return null;
        try {
            String ip    = parts[0];
            long epoch   = Long.parseLong(parts[2]);
            double lat   = parts[3].isEmpty() ? Double.NaN : Double.parseDouble(parts[3]);
            double lon   = parts[4].isEmpty() ? Double.NaN : Double.parseDouble(parts[4]);
            GeoIpResolver.Coordinates coord =
                (Double.isNaN(lat) || Double.isNaN(lon)) ? null
                : new GeoIpResolver.Coordinates(lat, lon);
            return new LoginEntry(ip, epoch, coord);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String sanitise(String s) {
        return s == null ? "" : s.replace("|", "_").replace("\n", " ");
    }

    // ─── Haversine distance ───────────────────────────────────────────────────

    private static double haversineKm(GeoIpResolver.Coordinates a,
                                       GeoIpResolver.Coordinates b) {
        double R = 6371.0;
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLon = Math.toRadians(b.lon() - a.lon());
        double sinLat = Math.sin(dLat / 2);
        double sinLon = Math.sin(dLon / 2);
        double h = sinLat * sinLat
                 + Math.cos(Math.toRadians(a.lat()))
                 * Math.cos(Math.toRadians(b.lat()))
                 * sinLon * sinLon;
        return 2 * R * Math.asin(Math.sqrt(h));
    }

    // ─── Domain types ─────────────────────────────────────────────────────────

    public enum AnomalyType {
        NEW_DEVICE,
        IMPOSSIBLE_TRAVEL,
        UNUSUAL_TIME
    }

    /** Internal projection used only within detection logic. */
    private record LoginEntry(String ip, long epochSecond, GeoIpResolver.Coordinates coord) {}
}
