package com.ghatana.security.interceptor;

import com.ghatana.security.audit.SecurityAuditLogger;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP-based blocking and auto-ban interceptor for the Security Gateway.
 * <p>
 * Maintains a blocklist of IPs (manually configured + auto-banned).
 * IPs that exceed a configurable violation threshold within a window
 * are automatically banned for a configurable duration.
 *
 * @doc.type class
 * @doc.purpose IP-level request blocking and auto-ban for DDoS / abuse mitigation
 * @doc.layer platform
 * @doc.pattern Interceptor
 */
public class IpBlockingInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(IpBlockingInterceptor.class);

    private final SecurityAuditLogger auditLogger;
    private final Set<String> permanentBlocklist;
    private final Map<String, BanEntry> autoBanned;
    private final Map<String, ViolationWindow> violationTracker;

    private final int violationThreshold;
    private final Duration violationWindow;
    private final Duration banDuration;
    private final boolean enabled;

    public IpBlockingInterceptor(
            SecurityAuditLogger auditLogger,
            Set<String> permanentBlocklist,
            int violationThreshold,
            Duration violationWindow,
            Duration banDuration,
            boolean enabled
    ) {
        this.auditLogger = auditLogger;
        this.permanentBlocklist = Set.copyOf(permanentBlocklist);
        this.autoBanned = new ConcurrentHashMap<>();
        this.violationTracker = new ConcurrentHashMap<>();
        this.violationThreshold = violationThreshold;
        this.violationWindow = violationWindow;
        this.banDuration = banDuration;
        this.enabled = enabled;
    }

    /**
     * Check the client IP against the blocklist and auto-ban registry.
     *
     * @param request the incoming HTTP request
     * @param next    the next handler in the chain
     * @return 403 Forbidden if blocked, otherwise delegates to next handler
     */
    public Promise<HttpResponse> intercept(HttpRequest request, @NotNull SecurityInterceptor.NextHandler next) {
        if (!enabled) {
            return next.handle(request);
        }

        String clientIp = getClientIp(request);

        // Check permanent blocklist
        if (permanentBlocklist.contains(clientIp)) {
            logger.warn("IP permanently blocked: ip={}", clientIp);
            return forbidden();
        }

        // Check auto-ban
        BanEntry ban = autoBanned.get(clientIp);
        if (ban != null) {
            if (Instant.now().isBefore(ban.expiresAt())) {
                logger.warn("IP auto-banned: ip={}, expires={}", clientIp, ban.expiresAt());
                return forbidden();
            } else {
                autoBanned.remove(clientIp);
            }
        }

        return next.handle(request);
    }

    /**
     * Record a violation for the given IP (e.g., after a WAF block or auth failure).
     * If the IP exceeds the threshold within the window, it is auto-banned.
     *
     * @param clientIp the IP that violated a security rule
     */
    public void recordViolation(String clientIp) {
        Instant now = Instant.now();
        ViolationWindow window = violationTracker.compute(clientIp, (ip, existing) -> {
            if (existing == null || existing.isExpired(now, violationWindow)) {
                return new ViolationWindow(now, 1);
            }
            return existing.increment();
        });

        if (window.count() >= violationThreshold) {
            autoBanned.put(clientIp, new BanEntry(now.plus(banDuration)));
            violationTracker.remove(clientIp);
            logger.warn("IP auto-banned due to {} violations in {}: ip={}",
                    violationThreshold, violationWindow, clientIp);
        }
    }

    /**
     * Manually add an IP to the auto-ban list.
     *
     * @param ip       the IP to ban
     * @param duration how long to ban it
     */
    public void banIp(String ip, Duration duration) {
        autoBanned.put(ip, new BanEntry(Instant.now().plus(duration)));
        logger.info("IP manually banned: ip={}, duration={}", ip, duration);
    }

    /**
     * Remove an IP from the auto-ban list.
     *
     * @param ip the IP to unban
     */
    public void unbanIp(String ip) {
        autoBanned.remove(ip);
        violationTracker.remove(ip);
        logger.info("IP unbanned: ip={}", ip);
    }

    /**
     * Get the number of currently auto-banned IPs.
     */
    public int getAutoBannedCount() {
        return autoBanned.size();
    }

    private static Promise<HttpResponse> forbidden() {
        return Promise.of(
                HttpResponse.ofCode(403)
                        .withBody("Forbidden: IP blocked".getBytes())
                        .build()
        );
    }

    private static String getClientIp(HttpRequest request) {
        String forwarded = request.getHeader(HttpHeaders.of("X-Forwarded-For"));
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return "unknown";
    }

    private record BanEntry(Instant expiresAt) {}

    private record ViolationWindow(Instant start, int count) {
        boolean isExpired(Instant now, Duration window) {
            return now.isAfter(start.plus(window));
        }

        ViolationWindow increment() {
            return new ViolationWindow(start, count + 1);
        }
    }
}
