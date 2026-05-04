package com.ghatana.digitalmarketing.bridge;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.kernel.bridge.AbstractKernelBridge;
import com.ghatana.kernel.bridge.port.BridgeAuditEmitter;
import com.ghatana.kernel.bridge.port.BridgeAuthorizationService;
import com.ghatana.kernel.bridge.port.BridgeContext;
import com.ghatana.kernel.bridge.port.BridgeHealthIndicator;
import com.ghatana.plugin.approval.ApprovalRequest;
import com.ghatana.plugin.approval.ApprovalRecord;
import com.ghatana.plugin.approval.HumanApprovalPlugin;
import com.ghatana.plugin.audit.AuditTrailPlugin;
import com.ghatana.plugin.audit.AuditTrailPlugin.AuditEntry;
import com.ghatana.plugin.consent.ConsentPlugin;
import com.ghatana.plugin.notification.NotificationPlugin;
import com.ghatana.plugin.risk.RiskManagementPlugin;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Production implementation of {@link DigitalMarketingKernelAdapter}.
 *
 * <p>Extends {@link AbstractKernelBridge} to inherit lifecycle management,
 * authorization checking, audit emission, health reporting, and retry semantics
 * provided by the kernel bridge base class.</p>
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
public final class DigitalMarketingKernelAdapterImpl
        extends AbstractKernelBridge
        implements DigitalMarketingKernelAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(DigitalMarketingKernelAdapterImpl.class);
    private static final String BRIDGE_ID = "digital-marketing-bridge";

    private final ConsentPlugin consentPlugin;
    private final HumanApprovalPlugin approvalPlugin;
    private final AuditTrailPlugin auditTrailPlugin;
    private final RiskManagementPlugin riskManagementPlugin;
    private final NotificationPlugin notificationPlugin;

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
     */
    public DigitalMarketingKernelAdapterImpl(
            BridgeAuthorizationService authService,
            BridgeAuditEmitter auditEmitter,
            BridgeHealthIndicator healthIndicator,
            ConsentPlugin consentPlugin,
            HumanApprovalPlugin approvalPlugin,
            AuditTrailPlugin auditTrailPlugin,
            RiskManagementPlugin riskManagementPlugin,
            NotificationPlugin notificationPlugin) {
        super(BRIDGE_ID, authService, auditEmitter, healthIndicator);
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
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void start() {
        markStarted();
        LOG.info("[{}] Digital Marketing Kernel Adapter started", BRIDGE_ID);
    }

    @Override
    public void stop() {
        markStopped();
        LOG.info("[{}] Digital Marketing Kernel Adapter stopped", BRIDGE_ID);
    }

    /**
     * Returns {@code true} if the adapter has been started and not yet stopped.
     *
     * @return {@code true} if operational
     */
    public boolean started() {
        return isStarted();
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

        return consentPlugin.verifyConsent(subjectId, purpose)
            .whenResult(valid -> LOG.debug(
                "[{}] Consent check: subjectId={}, purpose={}, valid={}, tenant={}",
                BRIDGE_ID, subjectId, purpose, valid, context.getTenantId().getValue()
            ));
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
            null  // no expiry by default; callers may set expiry via plugin directly if needed
        );

        return approvalPlugin.requestApproval(request)
            .map(ApprovalRecord::requestId)
            .whenResult(id -> LOG.info(
                "[{}] Approval requested: requestId={}, operationType={}, subjectId={}, tenant={}",
                BRIDGE_ID, id, operationType, subjectId, context.getTenantId().getValue()
            ));
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

        Map<String, Object> enrichedAttributes = buildAuditAttributes(context, attributes);

        return auditTrailPlugin.logEvent(entityId, action, enrichedAttributes)
            .map(AuditEntry::entryId)
            .whenResult(id -> LOG.debug(
                "[{}] Audit recorded: entryId={}, entityId={}, action={}, tenant={}",
                BRIDGE_ID, id, entityId, action, context.getTenantId().getValue()
            ));
    }

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

        return riskManagementPlugin.calculateRisk(
                entityId,
                new RiskManagementPlugin.RiskModelId(riskModelId),
                factors
            )
            .map(RiskManagementPlugin.RiskScore::score)
            .whenResult(score -> LOG.debug(
                "[{}] Risk evaluated: entityId={}, model={}, score={}, tenant={}",
                BRIDGE_ID,
                entityId,
                riskModelId,
                score,
                context.getTenantId().getValue()
            ));
    }

    // -----------------------------------------------------------------------
    // Notification (KE-02)
    // -----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to the platform {@link NotificationPlugin} for durable delivery
     * with retry and dead-letter queue support. The notification is queued asynchronously
     * and the returned promise resolves with the notification ID.</p>
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

        // Enrich attributes with context information
        java.util.Map<String, String> enrichedAttributes = new java.util.HashMap<>(attributes);
        enrichedAttributes.put("tenantId", context.getTenantId().getValue());
        enrichedAttributes.put("workspaceId", context.getWorkspaceId().getValue());
        enrichedAttributes.put("correlationId", context.getCorrelationId().getValue());
        enrichedAttributes.put("actor", context.getActor().getPrincipalId());

        return notificationPlugin.dispatch(recipientId, template, enrichedAttributes)
            .whenResult(notificationId -> LOG.info(
                "[{}] Notification dispatched: notificationId={}, recipientId={}, template={}, tenant={}",
                BRIDGE_ID, notificationId, recipientId, template, context.getTenantId().getValue()
            ))
            .map(notificationId -> null);
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private Map<String, Object> buildAuditAttributes(DmOperationContext context,
                                                       Map<String, Object> callerAttributes) {
        // Start with a mutable copy of caller attributes, then overlay required context fields
        java.util.HashMap<String, Object> merged = new java.util.HashMap<>(callerAttributes);
        merged.put("tenantId",      context.getTenantId().getValue());
        merged.put("workspaceId",   context.getWorkspaceId().getValue());
        merged.put("correlationId", context.getCorrelationId().getValue());
        merged.put("actor",         context.getActor().getPrincipalId());
        return Map.copyOf(merged);
    }
}
