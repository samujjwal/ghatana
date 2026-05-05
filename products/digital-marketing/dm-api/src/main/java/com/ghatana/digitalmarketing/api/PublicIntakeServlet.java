package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.intake.IntakeService;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.domain.intake.IntakeSubmission;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpMethod;
import io.activej.http.HttpResponse;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * P1-038: Public intake servlet with abuse controls.
 *
 * <p>Handles public form submissions (lead capture, contact forms, etc.)
 * with security controls to prevent abuse, spam, and flooding:</p>
 * <ul>
 *   <li>Rate limiting per IP address</li>
 *   <li>Request validation and sanitization</li>
 *   <li>Bot detection via honeypot fields</li>
 *   <li>Tenant/workspace binding from URL path</li>
 *   <li>PII protection at entry point</li>
 *   <li>Correlation ID generation for tracking</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Public intake endpoint with abuse controls (P1-038)
 * @doc.layer product
 * @doc.pattern Servlet, Security, Rate Limiting
 */
public final class PublicIntakeServlet {

    private static final Logger LOG = LoggerFactory.getLogger(PublicIntakeServlet.class);
    private static final String CONTENT_JSON = "application/json; charset=utf-8";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Rate limiting configuration
    private static final int MAX_REQUESTS_PER_MINUTE = 10;
    private static final int MAX_REQUESTS_PER_HOUR = 100;
    private static final Duration WINDOW_MINUTE = Duration.ofMinutes(1);
    private static final Duration WINDOW_HOUR = Duration.ofHours(1);

    // Suspicious patterns for basic bot detection
    private static final Set<String> SUSPICIOUS_PATTERNS = Set.of(
        "<script", "javascript:", "onerror=", "onload=", "eval(", "document.cookie"
    );

    private final Eventloop eventloop;
    private final IntakeService intakeService;

    // Rate limiter state - in production, use distributed cache (Redis)
    private final Map<String, RateLimitBucket> rateLimits = new ConcurrentHashMap<>();

    public PublicIntakeServlet(Eventloop eventloop, IntakeService intakeService) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.intakeService = Objects.requireNonNull(intakeService, "intakeService must not be null");
    }

    public AsyncServlet getServlet() {
        return RoutingServlet.builder(eventloop)
            .with(HttpMethod.POST, "/public/intake/:workspaceId/leads", this::handleLeadIntake)
            .with(HttpMethod.POST, "/public/intake/:workspaceId/contacts", this::handleContactIntake)
            .build();
    }

    /**
     * Handles lead intake submissions from public forms.
     */
    private Promise<HttpResponse> handleLeadIntake(io.activej.http.HttpRequest request) {
        return handleIntake(request, "lead");
    }

    /**
     * Handles contact intake submissions from public forms.
     */
    private Promise<HttpResponse> handleContactIntake(io.activej.http.HttpRequest request) {
        return handleIntake(request, "contact");
    }

    /**
     * Generic intake handler with abuse controls.
     */
    private Promise<HttpResponse> handleIntake(io.activej.http.HttpRequest request, String intakeType) {
        String workspaceId = request.getPathParameter("workspaceId");
        String clientIp = getClientIp(request);
        String correlationId = DmCorrelationId.generate().getValue();

        // P1-038: Rate limiting check
        if (isRateLimited(clientIp)) {
            LOG.warn("[DMOS-INTAKE] Rate limit exceeded: ip={}, workspace={}, type={}",
                clientIp, workspaceId, intakeType);
            return jsonError(429, "RATE_LIMITED",
                "Too many requests. Please try again later.", correlationId);
        }

        // Parse and validate request body
        return request.getBody()
            .then(body -> {
                String bodyStr = body.getString(java.nio.charset.StandardCharsets.UTF_8);

                // P1-038: Bot detection via honeypot field
                if (detectBot(bodyStr)) {
                    LOG.warn("[DMOS-INTAKE] Bot detected: ip={}, workspace={}, type={}",
                        clientIp, workspaceId, intakeType);
                    // Return fake success to not tip off the bot
                    return jsonResponse(200, Map.of(
                        "success", true,
                        "correlationId", correlationId
                    ));
                }

                // P1-038: Input validation and sanitization
                if (!isValidIntakeBody(bodyStr)) {
                    LOG.warn("[DMOS-INTAKE] Invalid intake body: ip={}, workspace={}, type={}",
                        clientIp, workspaceId, intakeType);
                    return jsonError(400, "INVALID_INPUT",
                        "Invalid or malformed request", correlationId);
                }

                // Record the request for rate limiting
                recordRequest(clientIp);

                // Process the intake
                IntakeSubmission submission = new IntakeSubmission(
                    workspaceId,
                    intakeType,
                    sanitizeInput(bodyStr),
                    clientIp,
                    correlationId,
                    Instant.now()
                );

                return intakeService.submit(submission)
                    .then(result -> jsonResponse(201, Map.of(
                        "success", true,
                        "submissionId", result.id(),
                        "correlationId", correlationId
                    )))
                    .whenException(e -> {
                        LOG.error("[DMOS-INTAKE] Intake processing failed: workspace={}, type={}, error={}",
                            workspaceId, intakeType, e.getMessage());
                    });
            })
            .whenException(e -> {
                LOG.error("[DMOS-INTAKE] Request handling failed: workspace={}, type={}, error={}",
                    workspaceId, intakeType, e.getMessage());
            });
    }

    /**
     * P1-038: Rate limiting check per IP address.
     */
    private boolean isRateLimited(String clientIp) {
        RateLimitBucket bucket = rateLimits.computeIfAbsent(clientIp, k -> new RateLimitBucket());
        return bucket.isRateLimited();
    }

    /**
     * Records a request for rate limiting purposes.
     */
    private void recordRequest(String clientIp) {
        rateLimits.computeIfAbsent(clientIp, k -> new RateLimitBucket()).recordRequest();
    }

    /**
     * P1-038: Basic bot detection via honeypot and suspicious patterns.
     */
    private boolean detectBot(String body) {
        // Check for honeypot field - if filled, likely a bot
        if (body.contains("\"website\":") && !body.contains("\"website\":\"\"")) {
            return true; // Honeypot field filled
        }

        // Check for suspicious patterns
        String lowerBody = body.toLowerCase();
        for (String pattern : SUSPICIOUS_PATTERNS) {
            if (lowerBody.contains(pattern.toLowerCase())) {
                return true;
            }
        }

        // Check for excessive length (likely spam)
        if (body.length() > 10000) {
            return true;
        }

        return false;
    }

    /**
     * P1-038: Validates intake request body.
     */
    private boolean isValidIntakeBody(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }

        // Must be valid JSON
        try {
            MAPPER.readTree(body);
        } catch (Exception e) {
            return false;
        }

        // Check for required fields presence
        if (!body.contains("email") && !body.contains("phone")) {
            return false; // Must have at least one contact method
        }

        return true;
    }

    /**
     * P1-038: Sanitizes input to prevent injection attacks.
     */
    private String sanitizeInput(String input) {
        if (input == null) {
            return null;
        }

        // Basic HTML tag removal
        String sanitized = input.replaceAll("<[^>]*>", "");

        // Limit length
        if (sanitized.length() > 5000) {
            sanitized = sanitized.substring(0, 5000);
        }

        return sanitized;
    }

    /**
     * Extracts client IP address from request.
     */
    private String getClientIp(io.activej.http.HttpRequest request) {
        // Check X-Forwarded-For header first (for proxies)
        String forwardedFor = request.getHeader(io.activej.http.HttpHeaders.of("X-Forwarded-For"));
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // Take the first IP in the chain
            return forwardedFor.split(",")[0].trim();
        }

        // Fall back to direct connection address
        InetAddress remoteAddress = request.getRemoteAddress();
        return remoteAddress != null ? remoteAddress.getHostAddress() : "unknown";
    }

    private Promise<HttpResponse> jsonResponse(int code, Map<String, Object> body) {
        try {
            return Promise.of(HttpResponse.ofCode(code)
                .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, CONTENT_JSON)
                .withBody(MAPPER.writeValueAsBytes(body))
                .build());
        } catch (Exception e) {
            LOG.error("Failed to serialize response", e);
            return Promise.of(HttpResponse.ofCode(500).build());
        }
    }

    private Promise<HttpResponse> jsonError(int code, String error, String message, String correlationId) {
        return jsonResponse(code, Map.of(
            "error", error,
            "message", message,
            "correlationId", correlationId
        ));
    }

    // Helper class for rate limiting
    private static class RateLimitBucket {
        private final AtomicInteger minuteCount = new AtomicInteger(0);
        private final AtomicInteger hourCount = new AtomicInteger(0);
        private volatile Instant minuteWindowStart = Instant.now();
        private volatile Instant hourWindowStart = Instant.now();

        synchronized boolean isRateLimited() {
            Instant now = Instant.now();

            // Reset minute window if expired
            if (now.isAfter(minuteWindowStart.plus(WINDOW_MINUTE))) {
                minuteCount.set(0);
                minuteWindowStart = now;
            }

            // Reset hour window if expired
            if (now.isAfter(hourWindowStart.plus(WINDOW_HOUR))) {
                hourCount.set(0);
                hourWindowStart = now;
            }

            return minuteCount.get() >= MAX_REQUESTS_PER_MINUTE ||
                   hourCount.get() >= MAX_REQUESTS_PER_HOUR;
        }

        synchronized void recordRequest() {
            minuteCount.incrementAndGet();
            hourCount.incrementAndGet();
        }
    }

    // Helper map factory
    private static Map<String, Object> Map.of(String k1, Object v1, String k2, Object v2) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    private static Map<String, Object> Map.of(String k1, Object v1, String k2, Object v2,
                                                 String k3, Object v3, String k4, Object v4) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        map.put(k3, v3);
        map.put(k4, v4);
        return map;
    }
}
