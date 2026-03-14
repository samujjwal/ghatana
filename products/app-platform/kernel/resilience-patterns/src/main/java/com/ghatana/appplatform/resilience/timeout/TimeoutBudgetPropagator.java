package com.ghatana.appplatform.resilience.timeout;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.logging.Logger;

/**
 * Propagates cascading timeout budgets across service call chains using the
 * {@value #HEADER_NAME} HTTP header.
 *
 * <h2>Protocol</h2>
 * <p>When service A makes an outbound call to service B, service A attaches a
 * deadline header containing the UTC timestamp by which the entire call chain
 * must complete. Service B reads the header, computes the remaining budget,
 * and uses it as its own local timeout. Service B then subtracts its elapsed
 * time before forwarding the header to service C, and so on.
 *
 * <pre>
 * Client ──(500ms deadline)──► A ──(remaining budget after A's work)──► B ──► C
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Outbound: create a deadline header for a 500ms timeout
 * String deadlineHeader = TimeoutBudgetPropagator.createDeadlineHeader(Duration.ofMillis(500));
 * request.header(TimeoutBudgetPropagator.HEADER_NAME, deadlineHeader);
 *
 * // Inbound: compute remaining budget from the incoming header
 * OptionalLong remaining = TimeoutBudgetPropagator.remainingBudgetMillis(incomingHeader);
 * if (remaining.isPresent()) {
 *     asyncOp.withTimeout(Duration.ofMillis(remaining.getAsLong()));
 * }
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Cascading timeout budget propagation via X-Request-Deadline header (K18-008)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class TimeoutBudgetPropagator {

    private static final Logger LOG = Logger.getLogger(TimeoutBudgetPropagator.class.getName());

    /** HTTP header name carrying the UTC deadline timestamp (ISO-8601). */
    public static final String HEADER_NAME = "X-Request-Deadline";

    private TimeoutBudgetPropagator() {}

    // ── Core API ───────────────────────────────────────────────────────────────

    /**
     * Creates the value for the {@value #HEADER_NAME} header.
     *
     * <p>The deadline is set to {@code now + timeout}. Include this header in any
     * outbound request that should participate in timeout budget propagation.
     *
     * @param timeout maximum time the downstream operation is allowed to take
     * @return ISO-8601 UTC timestamp string suitable for use as a header value
     */
    public static String createDeadlineHeader(Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        Instant deadline = Instant.now().plus(timeout);
        return deadline.toString();  // ISO-8601 UTC (e.g. "2026-03-14T12:00:00.500Z")
    }

    /**
     * Computes the remaining budget in milliseconds from an incoming deadline header.
     *
     * <p>Returns an empty optional when the header value is null, blank,
     * or cannot be parsed. This signals the caller to apply its own default timeout.
     *
     * @param headerValue value of the {@value #HEADER_NAME} header received from upstream
     * @return remaining milliseconds until the deadline, or empty if unknown / header absent
     */
    public static OptionalLong remainingBudgetMillis(String headerValue) {
        Optional<Instant> deadline = parseDeadline(headerValue);
        if (deadline.isEmpty()) return OptionalLong.empty();

        long remainingMs = Instant.now().until(deadline.get(), java.time.temporal.ChronoUnit.MILLIS);
        return OptionalLong.of(remainingMs);
    }

    /**
     * Returns {@code true} if the deadline has already passed.
     *
     * <p>A missing or unparseable header is treated as <em>not expired</em>.
     * (Callers that require a deadline should treat empty headers differently.)
     *
     * @param headerValue value of the {@value #HEADER_NAME} header
     * @return true if the UTC deadline instant is before now
     */
    public static boolean isExpired(String headerValue) {
        Optional<Instant> deadline = parseDeadline(headerValue);
        return deadline.isPresent() && deadline.get().isBefore(Instant.now());
    }

    /**
     * Computes an outbound deadline header respecting an upstream deadline.
     *
     * <p>If the upstream already sent a deadline, the earlier of
     * {@code (upstream deadline)} and {@code (now + ownMaxTimeout)} is used,
     * so the outbound call cannot accidentally exceed the upstream's budget.
     * If there is no upstream header, {@code ownMaxTimeout} is used.
     *
     * @param upstreamHeaderValue incoming {@value #HEADER_NAME} header (may be null)
     * @param ownMaxTimeout       this service's own maximum budget for the outbound call
     * @return header value to attach to the outbound request
     */
    public static String propagateDeadline(String upstreamHeaderValue, Duration ownMaxTimeout) {
        if (ownMaxTimeout == null || ownMaxTimeout.isNegative() || ownMaxTimeout.isZero()) {
            throw new IllegalArgumentException("ownMaxTimeout must be positive");
        }

        Instant ownDeadline = Instant.now().plus(ownMaxTimeout);
        Optional<Instant> upstream = parseDeadline(upstreamHeaderValue);

        if (upstream.isEmpty()) {
            return ownDeadline.toString();
        }

        // Use whichever deadline is sooner — preserves the upstream's budget budget
        Instant effective = upstream.get().isBefore(ownDeadline) ? upstream.get() : ownDeadline;
        return effective.toString();
    }

    /**
     * Returns the remaining budget as a {@link Duration}, or {@code fallback} if
     * the header is absent, malformed, or already expired.
     *
     * @param headerValue incoming header value
     * @param fallback    duration to return when no valid deadline exists
     */
    public static Duration remainingBudget(String headerValue, Duration fallback) {
        OptionalLong millis = remainingBudgetMillis(headerValue);
        if (millis.isEmpty() || millis.getAsLong() <= 0) {
            return fallback;
        }
        return Duration.ofMillis(millis.getAsLong());
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private static Optional<Instant> parseDeadline(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(headerValue.trim()));
        } catch (Exception e) {
            LOG.warning("[TimeoutBudgetPropagator] Could not parse deadline header: " + headerValue);
            return Optional.empty();
        }
    }
}
