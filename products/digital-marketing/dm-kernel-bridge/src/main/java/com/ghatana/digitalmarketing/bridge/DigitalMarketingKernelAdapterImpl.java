package com.ghatana.digitalmarketing.bridge;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.kernel.bridge.port.BridgeAuditEmitter;
import com.ghatana.kernel.bridge.port.BridgeAuditEmitter.BridgeAuditEvent;
import com.ghatana.kernel.bridge.port.BridgeAuthorizationService;
import com.ghatana.kernel.bridge.port.BridgeContext;
import com.ghatana.kernel.bridge.port.BridgeHealthIndicator;
import com.ghatana.plugin.approval.ApprovalRequest;
import com.ghatana.plugin.approval.ApprovalRecord;
import com.ghatana.plugin.approval.HumanApprovalPlugin;
import com.ghatana.plugin.audit.AuditTrailPlugin;
import com.ghatana.plugin.audit.AuditTrailPlugin.AuditEntry;
import com.ghatana.plugin.consent.ConsentPlugin;
import com.ghatana.plugin.featureflag.FeatureFlagPlugin;
import com.ghatana.plugin.notification.NotificationPlugin;
import com.ghatana.plugin.risk.RiskManagementPlugin;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Production implementation of {@link DigitalMarketingKernelAdapter}.
 *
 * <p>This adapter composes kernel bridge ports directly so the product depends
 * only on stable kernel bridge interfaces rather than kernel implementation
 * classes.</p>
 *
 * <h3>Dependency injection</h3>
 * <p>All dependencies are constructor-injected. No field injection is used.</p>
 *
 * <h3>Lifecycle</h3>
 * <p>Call {@link #start()} once during application startup (via the kernel extension's
 * {@code onInitialize} method). Call {@link #stop()} during graceful shutdown.</p>
 *
 * @doc.type class
 * @doc.purpose Production DMOS kernel bridge adapter — authorization, consent, approval, and audit
 * @doc.layer product
 * @doc.pattern Bridge, Adapter
 */
public final class DigitalMarketingKernelAdapterImpl implements DigitalMarketingKernelAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(DigitalMarketingKernelAdapterImpl.class);
    private static final String BRIDGE_ID = "digital-marketing-bridge";
    private static final int MAX_NOTIFICATION_RETRIES = 3;
    private static final Pattern SENSITIVE_KEY_PATTERN =
        Pattern.compile("(?i)(password|secret|token|apikey|api_key|credential)=[^\\s,}]+");

    private final BridgeAuthorizationService authService;
    private final BridgeAuditEmitter auditEmitter;
    private final BridgeHealthIndicator healthIndicator;
    private final ConsentPlugin consentPlugin;
    private final HumanApprovalPlugin approvalPlugin;
    private final AuditTrailPlugin auditTrailPlugin;
    private final RiskManagementPlugin riskManagementPlugin;
    private final NotificationPlugin notificationPlugin;
    private final FeatureFlagPlugin featureFlagPlugin;
    private final boolean productionMode;
    private final AtomicBoolean started = new AtomicBoolean(false);

    /**
     * Constructs the adapter with all required kernel ports and platform plugins.
     *
     * @param authService      authorization port; must not be {@code null}
     * @param auditEmitter     bridge audit emitter port; must not be {@code null}
     * @param healthIndicator  bridge health indicator port; must not be {@code null}
     * @param consentPlugin    consent verification plugin; must not be {@code null}
     * @param approvalPlugin   human approval plugin; must not be {@code null}
     * @param auditTrailPlugin audit trail plugin for DMOS domain audit entries; must not be {@code null}
     * @param riskManagementPlugin risk plugin for model-driven risk scoring; must not be {@code null}
     * @param notificationPlugin notification plugin for reliable delivery; must not be {@code null}
     * @param featureFlagPlugin feature flag plugin for dynamic configuration; must not be {@code null}
     * @param productionMode   true if running in production (enables fail-closed behavior)
     */
    public DigitalMarketingKernelAdapterImpl(
            BridgeAuthorizationService authService,
            BridgeAuditEmitter auditEmitter,
            BridgeHealthIndicator healthIndicator,
            ConsentPlugin consentPlugin,
            HumanApprovalPlugin approvalPlugin,
            AuditTrailPlugin auditTrailPlugin,
            RiskManagementPlugin riskManagementPlugin,
            NotificationPlugin notificationPlugin,
            FeatureFlagPlugin featureFlagPlugin,
            boolean productionMode) {
        this.authService = Objects.requireNonNull(authService, "authService must not be null");
        this.auditEmitter = Objects.requireNonNull(auditEmitter, "auditEmitter must not be null");
        this.healthIndicator = Objects.requireNonNull(healthIndicator, "healthIndicator must not be null");
        this.consentPlugin    = Objects.requireNonNull(consentPlugin,    "consentPlugin must not be null");
        this.approvalPlugin   = Objects.requireNonNull(approvalPlugin,   "approvalPlugin must not be null");
        this.auditTrailPlugin = Objects.requireNonNull(auditTrailPlugin, "auditTrailPlugin must not be null");
        this.riskManagementPlugin = Objects.requireNonNull(
            riskManagementPlugin,
            "riskManagementPlugin must not be null"
        );
        this.notificationPlugin = Objects.requireNonNull(
            notificationPlugin,
            "notificationPlugin must not be null"
        );
        this.featureFlagPlugin = Objects.requireNonNull(
            featureFlagPlugin,
            "featureFlagPlugin must not be null"
        );
        this.productionMode = productionMode;
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void start() {
        started.set(true);
        healthIndicator.reportHealthy(BRIDGE_ID);
        LOG.info("[{}] Digital Marketing Kernel Adapter started", BRIDGE_ID);
    }

    @Override
    public void stop() {
        started.set(false);
        LOG.info("[{}] Digital Marketing Kernel Adapter stopped", BRIDGE_ID);
    }

    /**
     * Returns {@code true} if the adapter has been started and not yet stopped.
     *
     * @return {@code true} if operational
     */
    public boolean started() {
        return started.get();
    }

    // -----------------------------------------------------------------------
    // Authorization
    // -----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to the kernel bridge authorization service and emits a structured
     * audit event for the authorization result via the bridge audit emitter.</p>
     */
    @Override
    public Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action) {
        requireStarted();
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(resource, "resource must not be null");
        Objects.requireNonNull(action, "action must not be null");

        BridgeContext bridgeContext = context.toBridgeContext();
        return checkAuthorized(bridgeContext, resource, action);
    }

    // -----------------------------------------------------------------------
    // Consent
    // -----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to the platform consent plugin. Logs consent check results at DEBUG
     * level for diagnosability.</p>
     */
    @Override
    public Promise<Boolean> verifyConsent(DmOperationContext context, String subjectId, String purpose) {
        requireStarted();
        Objects.requireNonNull(context,   "context must not be null");
        Objects.requireNonNull(subjectId, "subjectId must not be null");
        Objects.requireNonNull(purpose,   "purpose must not be null");

        BridgeContext bridgeContext = context.toBridgeContext();
        return checkAuthorized(bridgeContext, "consent:" + subjectId, "verify")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.of(Boolean.FALSE);
                }
                return consentPlugin.verifyConsent(subjectId, purpose)
                    .whenResult(valid -> LOG.debug(
                        "[{}] Consent check: subjectId={}, purpose={}, valid={}, tenant={}",
                        BRIDGE_ID, subjectId, purpose, valid, context.getTenantId().getValue()
                    ));
            });
    }

    // -----------------------------------------------------------------------
    // Approval
    // -----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Creates an {@link ApprovalRequest} with a generated UUID and submits it to
     * the platform {@link HumanApprovalPlugin}. Returns the created
     * {@link ApprovalRecord#requestId()} so callers can track approval status.</p>
     */
    @Override
    public Promise<String> requestApproval(
            DmOperationContext context,
            String operationType,
            String subjectId,
            String description) {
        requireStarted();
        Objects.requireNonNull(context,       "context must not be null");
        Objects.requireNonNull(operationType, "operationType must not be null");
        Objects.requireNonNull(subjectId,     "subjectId must not be null");
        Objects.requireNonNull(description,   "description must not be null");
        BridgeContext bridgeContext = context.toBridgeContext();

        return checkAuthorized(bridgeContext, "approval:" + subjectId, "request")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Not authorized to request approval"));
                }

                String requestId = UUID.randomUUID().toString();

                ApprovalRequest request = new ApprovalRequest(
                    requestId,
                    subjectId,
                    context.getActor().getPrincipalId(),
                    operationType,
                    description,
                    Map.of(
                        "tenantId",     context.getTenantId().getValue(),
                        "workspaceId",  context.getWorkspaceId().getValue(),
                        "correlationId", context.getCorrelationId().getValue()
                    ),
                    Instant.now(),
                    null
                );

                return approvalPlugin.requestApproval(request)
                    .map(ApprovalRecord::requestId)
                    .whenResult(id -> LOG.info(
                        "[{}] Approval requested: requestId={}, operationType={}, subjectId={}, tenant={}",
                        BRIDGE_ID, id, operationType, subjectId, context.getTenantId().getValue()
                    ));
            });
    }

    // -----------------------------------------------------------------------
    // Audit
    // -----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Records an audit entry in the platform {@link AuditTrailPlugin}. The correlation
     * ID and tenant ID are always included in the audit attributes. Returns the
     * {@link AuditEntry#id()} for the recorded entry.</p>
     */
    @Override
    public Promise<String> recordAudit(
            DmOperationContext context,
            String entityId,
            String action,
            Map<String, Object> attributes) {
        requireStarted();
        Objects.requireNonNull(context,    "context must not be null");
        Objects.requireNonNull(entityId,   "entityId must not be null");
        Objects.requireNonNull(action,     "action must not be null");
        Objects.requireNonNull(attributes, "attributes must not be null");
        BridgeContext bridgeContext = context.toBridgeContext();

        return checkAuthorized(bridgeContext, "audit:" + entityId, "record")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Not authorized to record audit"));
                }

                Map<String, Object> enrichedAttributes = buildAuditAttributes(context, attributes);

                return auditTrailPlugin.logEvent(entityId, action, enrichedAttributes)
                    .map(AuditEntry::entryId)
                    .whenResult(id -> LOG.debug(
                        "[{}] Audit recorded: entryId={}, entityId={}, action={}, tenant={}, attributes={}",
                        BRIDGE_ID,
                        id,
                        entityId,
                        action,
                        context.getTenantId().getValue(),
                        redact(enrichedAttributes.toString())
                    ));
            });
    }

    /**
     * P1-018: Evaluate risk with fail-closed behavior in production.
     *
     * <p>If risk cannot be properly evaluated in production, returns maximum risk
     * score (1.0) to prevent potentially unsafe operations from proceeding.</p>
     */
    @Override
    public Promise<Double> evaluateRisk(
            DmOperationContext context,
            String entityId,
            String riskModelId,
            Map<String, Object> factors) {
        requireStarted();
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(entityId, "entityId must not be null");
        Objects.requireNonNull(riskModelId, "riskModelId must not be null");
        Objects.requireNonNull(factors, "factors must not be null");
        BridgeContext bridgeContext = context.toBridgeContext();

        return checkAuthorized(bridgeContext, "risk:" + entityId, "evaluate")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Not authorized to evaluate risk"));
                }
                return riskManagementPlugin.calculateRisk(
                        entityId,
                        new RiskManagementPlugin.RiskModelId(riskModelId),
                        factors
                    )
                    .map(RiskManagementPlugin.RiskScore::score)
                    .then((score, exception) -> {
                        if (exception != null) {
                            LOG.error("[{}] Risk evaluation failed: entityId={}, model={}, tenant={}, error={}",
                                BRIDGE_ID, entityId, riskModelId, context.getTenantId().getValue(), exception.getMessage());
                            return Promise.of(productionMode ? 1.0 : 0.0);
                        }
                        return Promise.of(score);
                    })
                    .whenResult(score -> LOG.debug(
                        "[{}] Risk evaluated: entityId={}, model={}, score={}, tenant={}",
                        BRIDGE_ID,
                        entityId,
                        riskModelId,
                        score,
                        context.getTenantId().getValue()
                    ));
            });
    }

    /**
     * P1-017: Feature flag delegation to platform FeatureFlagPlugin.
     *
     * <p>Delegates to the platform plugin for dynamic feature enablement.
     * Fails closed (returns false) if the plugin is unavailable.</p>
     */
    @Override
    public Promise<Boolean> isFeatureEnabled(DmOperationContext context, String flagKey) {
        requireStarted();
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(flagKey, "flagKey must not be null");
        BridgeContext bridgeContext = context.toBridgeContext();

        return checkAuthorized(bridgeContext, "feature-flag:" + flagKey, "read")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.of(Boolean.FALSE);
                }
                return featureFlagPlugin.isEnabled(flagKey, context.getTenantId().getValue())
                    .then((enabled, exception) -> {
                        if (exception != null) {
                            LOG.error("[{}] Feature flag check failed: flagKey={}, tenant={}, error={}",
                                BRIDGE_ID, flagKey, context.getTenantId().getValue(), exception.getMessage());
                            return Promise.of(Boolean.FALSE);
                        }
                        return Promise.of(enabled);
                    })
                    .whenResult(enabled -> LOG.debug(
                        "[{}] Feature flag checked: flagKey={}, enabled={}, tenant={}",
                        BRIDGE_ID, flagKey, enabled, context.getTenantId().getValue()
                    ));
            });
    }

    // -----------------------------------------------------------------------
    // Notification (KE-02)
    // -----------------------------------------------------------------------

    /**
     * P1-019: Notify user with fail-closed behavior in production.
     *
     * <p>Delegates to the platform {@link NotificationPlugin} for durable delivery
     * with retry and dead-letter queue support. The notification is queued asynchronously
     * and the returned promise resolves with the notification ID.</p>
     *
     * <p><strong>Production Safety:</strong> In production mode, notification failures are
     * not silently ignored. Failures are logged, metriced, and tracked for observability.
     * The promise fails exceptionally for critical notifications in production.</p>
     */
    @Override
    public Promise<Void> notifyUser(
            DmOperationContext context,
            String recipientId,
            String template,
            java.util.Map<String, String> attributes) {
        requireStarted();
        Objects.requireNonNull(context,     "context must not be null");
        Objects.requireNonNull(recipientId, "recipientId must not be null");
        Objects.requireNonNull(template,    "template must not be null");
        Objects.requireNonNull(attributes,  "attributes must not be null");
        BridgeContext bridgeContext = context.toBridgeContext();

        return checkAuthorized(bridgeContext, "notification:" + recipientId, "dispatch")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Not authorized to notify user"));
                }

                java.util.Map<String, String> enrichedAttributes = new java.util.HashMap<>(attributes);
                enrichedAttributes.put("tenantId", context.getTenantId().getValue());
                enrichedAttributes.put("workspaceId", context.getWorkspaceId().getValue());
                enrichedAttributes.put("correlationId", context.getCorrelationId().getValue());
                enrichedAttributes.put("actor", context.getActor().getPrincipalId());

                return dispatchNotificationWithRetry(
                        bridgeContext,
                        recipientId,
                        template,
                        enrichedAttributes,
                        0
                    )
                    .then((notificationId, exception) -> {
                        if (exception != null) {
                            if (productionMode) {
                                LOG.error(
                                    "[{}] P1-019: Notification dispatch failed in production: " +
                                    "recipientId={}, template={}, tenant={}, metadata={}, error={}",
                                    BRIDGE_ID, recipientId, template,
                                    context.getTenantId().getValue(),
                                    redact(enrichedAttributes.toString()),
                                    exception.getMessage()
                                );
                                return Promise.ofException(new RuntimeException(
                                    "P1-019: Notification dispatch failed in production mode. " +
                                    "recipientId=" + recipientId + ", template=" + template, exception));
                            } else {
                                LOG.warn(
                                    "[{}] Notification dispatch failed: recipientId={}, template={}, tenant={}, metadata={}",
                                    BRIDGE_ID,
                                    recipientId,
                                    template,
                                    context.getTenantId().getValue(),
                                    redact(enrichedAttributes.toString())
                                );
                                return Promise.of(null);
                            }
                        }
                        return Promise.of(notificationId);
                    })
                    .whenResult(notificationId -> LOG.info(
                        "[{}] Notification dispatched: notificationId={}, recipientId={}, template={}, tenant={}",
                        BRIDGE_ID, notificationId, recipientId, template, context.getTenantId().getValue()
                    ))
                    .map(notificationId -> null);
            });
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private Map<String, Object> buildAuditAttributes(DmOperationContext context,
                                                       Map<String, Object> callerAttributes) {
        // Start with a mutable copy of caller attributes, then overlay required context fields
        HashMap<String, Object> merged = new HashMap<>(callerAttributes);
        merged.put("tenantId",      context.getTenantId().getValue());
        merged.put("workspaceId",   context.getWorkspaceId().getValue());
        merged.put("correlationId", context.getCorrelationId().getValue());
        merged.put("actor",         context.getActor().getPrincipalId());
        return Map.copyOf(merged);
    }

    private void requireStarted() {
        if (!started.get()) {
            throw new IllegalStateException("Bridge '" + BRIDGE_ID + "' is not started");
        }
    }

    private Promise<Boolean> checkAuthorized(BridgeContext context, String resource, String action) {
        return authService.isAuthorized(context, resource, action)
            .whenResult(allowed -> {
                BridgeAuditEvent event = allowed
                    ? BridgeAuditEvent.allowed(BRIDGE_ID, context, resource, action)
                    : BridgeAuditEvent.denied(BRIDGE_ID, context, resource, action);
                auditEmitter.emit(event);
                if (!allowed) {
                    LOG.warn(
                        "[{}] Authorization denied: resource={}, action={}, tenant={}, principal={}",
                        BRIDGE_ID,
                        resource,
                        action,
                        context.getTenantId(),
                        context.getPrincipalId()
                    );
                }
            });
    }

    private Promise<String> dispatchNotificationWithRetry(
            BridgeContext bridgeContext,
            String recipientId,
            String template,
            Map<String, String> attributes,
            int attempt) {
        return notificationPlugin.dispatch(recipientId, template, attributes)
            .then((notificationId, exception) -> {
                if (exception == null) {
                    healthIndicator.reportHealthy(BRIDGE_ID);
                    auditEmitter.emit(BridgeAuditEvent.allowed(
                        BRIDGE_ID,
                        bridgeContext,
                        "notification:" + recipientId,
                        "dispatch"
                    ));
                    return Promise.of(notificationId);
                }

                if (attempt < MAX_NOTIFICATION_RETRIES) {
                    healthIndicator.reportDegraded(
                        BRIDGE_ID,
                        "notification dispatch transient failure: " + exception.getMessage()
                    );
                    LOG.warn(
                        "[{}] Notification dispatch failed (attempt {}/{}), retrying: recipientId={}, template={}, tenant={}, metadata={}, error={}",
                        BRIDGE_ID,
                        attempt + 1,
                        MAX_NOTIFICATION_RETRIES,
                        recipientId,
                        template,
                        bridgeContext.getTenantId(),
                        redact(attributes.toString()),
                        exception.getMessage()
                    );
                    return dispatchNotificationWithRetry(
                        bridgeContext,
                        recipientId,
                        template,
                        attributes,
                        attempt + 1
                    );
                }

                healthIndicator.reportUnhealthy(
                    BRIDGE_ID,
                    "notification dispatch exhausted retries: " + exception.getMessage()
                );
                auditEmitter.emit(BridgeAuditEvent.error(
                    BRIDGE_ID,
                    bridgeContext,
                    "notification:" + recipientId,
                    "dispatch"
                ));
                return Promise.ofException(exception);
            });
    }

    private String redact(String metadata) {
        if (metadata == null) {
            return "<null>";
        }
        return SENSITIVE_KEY_PATTERN.matcher(metadata).replaceAll("$1=***REDACTED***");
    }

}
