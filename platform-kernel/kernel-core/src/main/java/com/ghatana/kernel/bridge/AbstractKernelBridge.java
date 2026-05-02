/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.bridge;

import com.ghatana.kernel.bridge.port.BridgeAuditEmitter;
import com.ghatana.kernel.bridge.port.BridgeAuditEmitter.BridgeAuditEvent;
import com.ghatana.kernel.bridge.port.BridgeAuthorizationService;
import com.ghatana.kernel.bridge.port.BridgeContext;
import com.ghatana.kernel.bridge.port.BridgeHealthIndicator;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Base class for kernel bridge adapters.
 *
 * <p>Provides common infrastructure for all kernel bridge adapters:</p>
 * <ul>
 *   <li><strong>Lifecycle</strong> — started/stopped state with {@link #requireStarted()}</li>
 *   <li><strong>Authorization</strong> — {@link #checkAuthorized(BridgeContext, String, String)}
 *       delegating to the {@link BridgeAuthorizationService} port</li>
 *   <li><strong>Audit</strong> — structured audit emission via {@link BridgeAuditEmitter}</li>
 *   <li><strong>Health</strong> — health reporting via {@link BridgeHealthIndicator}</li>
 *   <li><strong>Promise wrapping</strong> — {@link #wrapAsync(Supplier)} converts
 *       {@link CompletableFuture} suppliers into ActiveJ {@link Promise}</li>
 *   <li><strong>Resilience</strong> — {@link #executeWithRetry(String, BridgeContext, String, String, Supplier)}
 *       adds bounded retries with exponential back-off and health feedback</li>
 *   <li><strong>Metadata redaction</strong> — {@link #redact(String)} removes sensitive
 *       key–value patterns before logging</li>
 * </ul>
 *
 * <h2>Dependency direction</h2>
 * <pre>
 *   kernel-core (ports)  &lt;──  AbstractKernelBridge  &lt;──  concrete adapter impls
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * public class MyKernelAdapterImpl extends AbstractKernelBridge implements MyKernelAdapter {
 *
 *     public MyKernelAdapterImpl(
 *             BridgeAuthorizationService auth,
 *             BridgeAuditEmitter auditor,
 *             BridgeHealthIndicator health) {
 *         super("my-bridge", auth, auditor, health);
 *     }
 *
 *     public Promise<DataResult> readData(BridgeContext context, String datasetId) {
 *         requireStarted();
 *         return checkAuthorized(context, "dataset:" + datasetId, "read")
 *             .then(allowed -> allowed
 *                 ? executeWithRetry("readData", context, "dataset:" + datasetId, "read",
 *                       () -> backendClient.fetchData(datasetId))
 *                 : Promise.ofException(new SecurityException("Not authorized")));
 *     }
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Base bridge adapter providing authorization, audit, health, resilience, and Promise wrapping
 * @doc.layer core
 * @doc.pattern AbstractTemplate, Bridge
 * @author Ghatana Kernel Team
 * @since 1.3.0
 */
public abstract class AbstractKernelBridge {

    /** Maximum number of retry attempts for transient failures. */
    protected static final int MAX_RETRIES = 3;

    /** Base back-off duration between retries. Each retry doubles this value. */
    protected static final Duration BASE_BACKOFF = Duration.ofMillis(100);

    /**
     * Pattern for redacting sensitive key–value strings in log output.
     * Matches: {@code secret=value}, {@code password=value}, {@code token=value},
     * {@code apiKey=value}, etc.
     */
    private static final Pattern SENSITIVE_KEY_PATTERN =
            Pattern.compile("(?i)(password|secret|token|apikey|api_key|credential)=[^\\s,}]+");

    private final Logger log;
    private final String bridgeId;
    private final BridgeAuthorizationService authService;
    private final BridgeAuditEmitter auditEmitter;
    private final BridgeHealthIndicator healthIndicator;
    private final AtomicBoolean started = new AtomicBoolean(false);

    /**
     * Constructs the bridge with all required kernel ports.
     *
     * @param bridgeId        unique identifier for this bridge (used in audit and health events)
     * @param authService     authorization port — must not be {@code null}
     * @param auditEmitter    audit emission port — must not be {@code null}
     * @param healthIndicator health indicator port — must not be {@code null}
     */
    protected AbstractKernelBridge(
            String bridgeId,
            BridgeAuthorizationService authService,
            BridgeAuditEmitter auditEmitter,
            BridgeHealthIndicator healthIndicator) {
        this.bridgeId        = Objects.requireNonNull(bridgeId, "bridgeId must not be null");
        this.authService     = Objects.requireNonNull(authService, "authService must not be null");
        this.auditEmitter    = Objects.requireNonNull(auditEmitter, "auditEmitter must not be null");
        this.healthIndicator = Objects.requireNonNull(healthIndicator, "healthIndicator must not be null");
        this.log             = LoggerFactory.getLogger(getClass());
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Marks this bridge as started. Subclasses call this in their own start/initialize hook.
     */
    protected final void markStarted() {
        started.set(true);
        log.info("[{}] Bridge started", bridgeId);
        healthIndicator.reportHealthy(bridgeId);
    }

    /**
     * Marks this bridge as stopped. Subclasses call this in their own stop hook.
     */
    protected final void markStopped() {
        started.set(false);
        log.info("[{}] Bridge stopped", bridgeId);
    }

    /**
     * Asserts the bridge is in the started state; throws {@link IllegalStateException} if not.
     */
    protected final void requireStarted() {
        if (!started.get()) {
            throw new IllegalStateException("Bridge '" + bridgeId + "' is not started");
        }
    }

    /** Returns whether this bridge is currently in the started state. */
    protected final boolean isStarted() {
        return started.get();
    }

    /** Returns the bridge identifier supplied at construction time. */
    protected final String getBridgeId() {
        return bridgeId;
    }

    // -------------------------------------------------------------------------
    // Authorization
    // -------------------------------------------------------------------------

    /**
     * Checks authorization via the {@link BridgeAuthorizationService} port and emits an
     * audit event for the result.
     *
     * @param context  the call context carrying tenant and principal identity
     * @param resource the resource being accessed
     * @param action   the action being attempted
     * @return a {@link Promise} resolving to {@code true} when authorized, {@code false} otherwise
     */
    protected final Promise<Boolean> checkAuthorized(
            BridgeContext context, String resource, String action) {
        return authService.isAuthorized(context, resource, action)
                .whenResult(allowed -> {
                    BridgeAuditEvent event = allowed
                            ? BridgeAuditEvent.allowed(bridgeId, context, resource, action)
                            : BridgeAuditEvent.denied(bridgeId, context, resource, action);
                    auditEmitter.emit(event);
                    if (!allowed) {
                        log.warn("[{}] Authorization denied: resource={}, action={}, tenant={}, principal={}",
                                bridgeId, resource, action,
                                context.getTenantId(), context.getPrincipalId());
                    }
                });
    }

    // -------------------------------------------------------------------------
    // Promise wrapping
    // -------------------------------------------------------------------------

    /**
     * Wraps a {@link CompletableFuture} supplier as an ActiveJ {@link Promise}.
     *
     * <p>Any exception thrown by the supplier is converted to a failed Promise.</p>
     *
     * @param futureSupplier the supplier of the async operation
     * @param <T>            the result type
     * @return an ActiveJ Promise tracking the async operation
     */
    protected final <T> Promise<T> wrapAsync(Supplier<CompletableFuture<T>> futureSupplier) {
        try {
            CompletableFuture<T> future = futureSupplier.get();
            return Promise.ofFuture(future);
        } catch (Exception ex) {
            log.error("[{}] Exception during async wrap: {}", bridgeId, ex.getMessage(), ex);
            return Promise.ofException(ex);
        }
    }

    // -------------------------------------------------------------------------
    // Resilience: retry with exponential back-off
    // -------------------------------------------------------------------------

    /**
     * Executes a bridge operation with bounded retries and exponential back-off.
     *
     * <p>On each successful attempt, reports healthy. On transient failure,
     * reports degraded and retries up to {@link #MAX_RETRIES} times. After all
     * retries are exhausted, reports unhealthy and returns the final failure.</p>
     *
     * @param operationName  human-readable name for log context
     * @param context        the bridge call context
     * @param resource       the resource being accessed (for audit)
     * @param action         the action being attempted (for audit)
     * @param futureSupplier the async operation to attempt
     * @param <T>            the result type
     * @return a {@link Promise} that resolves on success or fails after all retries
     */
    protected final <T> Promise<T> executeWithRetry(
            String operationName,
            BridgeContext context,
            String resource,
            String action,
            Supplier<CompletableFuture<T>> futureSupplier) {
        CompletableFuture<T> chainedFuture =
                buildRetryChain(operationName, context, resource, action, futureSupplier, 0);
        return wrapAsync(() -> chainedFuture);
    }

    private <T> CompletableFuture<T> buildRetryChain(
            String operationName,
            BridgeContext context,
            String resource,
            String action,
            Supplier<CompletableFuture<T>> futureSupplier,
            int attempt) {

        return futureSupplier.get()
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        healthIndicator.reportHealthy(bridgeId);
                        auditEmitter.emit(
                                BridgeAuditEvent.allowed(bridgeId, context, resource, action));
                    } else if (attempt < MAX_RETRIES) {
                        long backoffMs = BASE_BACKOFF.toMillis() * (1L << attempt);
                        log.warn("[{}] {} failed (attempt {}/{}), retrying in {}ms: {}",
                                bridgeId, operationName, attempt + 1, MAX_RETRIES,
                                backoffMs, ex.getMessage());
                        healthIndicator.reportDegraded(bridgeId,
                                operationName + " transient failure: " + ex.getMessage());
                    } else {
                        log.error("[{}] {} failed after {} attempts: {}",
                                bridgeId, operationName, MAX_RETRIES, ex.getMessage(), ex);
                        healthIndicator.reportUnhealthy(bridgeId,
                                operationName + " exhausted retries: " + ex.getMessage());
                        auditEmitter.emit(
                                BridgeAuditEvent.error(bridgeId, context, resource, action));
                    }
                })
                .exceptionallyCompose(ex -> {
                    if (attempt < MAX_RETRIES) {
                        return buildRetryChain(
                                operationName, context, resource, action, futureSupplier, attempt + 1);
                    }
                    return CompletableFuture.failedFuture(ex);
                });
    }

    // -------------------------------------------------------------------------
    // Metadata redaction
    // -------------------------------------------------------------------------

    /**
     * Redacts sensitive key–value patterns (e.g. {@code password=…}, {@code token=…})
     * from a metadata string before it is written to logs.
     *
     * @param metadata the raw metadata string — may be {@code null}
     * @return the redacted string, or {@code "<null>"} if {@code metadata} was {@code null}
     */
    protected final String redact(String metadata) {
        if (metadata == null) {
            return "<null>";
        }
        return SENSITIVE_KEY_PATTERN.matcher(metadata).replaceAll("$1=***REDACTED***");
    }

    // -------------------------------------------------------------------------
    // Audit helpers
    // -------------------------------------------------------------------------

    /**
     * Emits a structured bridge audit event directly, for cases where the caller
     * has already resolved the outcome and wishes to record it explicitly.
     *
     * @param event the event to emit — must not be {@code null}
     */
    protected final void emitAudit(BridgeAuditEvent event) {
        auditEmitter.emit(Objects.requireNonNull(event, "audit event must not be null"));
    }
}
