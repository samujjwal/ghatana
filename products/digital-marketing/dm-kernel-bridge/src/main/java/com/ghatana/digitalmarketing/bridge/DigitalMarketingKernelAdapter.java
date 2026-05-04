package com.ghatana.digitalmarketing.bridge;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import io.activej.promise.Promise;

import java.util.Map;

/**
 * Product-level kernel adapter interface for DMOS operations.
 *
 * <p>{@code DigitalMarketingKernelAdapter} defines the contract exposed to DMOS
 * application services. It abstracts kernel authorization, audit, consent verification,
 * and approval workflows behind a product-idiomatic API. Application code never
 * imports kernel bridge ports directly; it depends only on this interface.</p>
 *
 * <p>The production implementation is {@link DigitalMarketingKernelAdapterImpl}.</p>
 *
 * @doc.type interface
 * @doc.purpose Product-level kernel adapter contract for DMOS operations
 * @doc.layer product
 * @doc.pattern Bridge, Port
 */
public interface DigitalMarketingKernelAdapter {

    /**
     * Starts the adapter, making it ready to serve requests.
     *
     * <p>Must be called once during application startup before any other method.</p>
     */
    void start();

    /**
     * Stops the adapter, releasing any held resources.
     */
    void stop();

    /**
     * Checks whether the actor in the given context is authorized to perform {@code action}
     * on {@code resource}.
     *
     * @param context  the operation context carrying tenant and actor identity
     * @param resource the resource being accessed (e.g. {@code "campaigns/c-123"})
     * @param action   the action being attempted (e.g. {@code "launch"})
     * @return promise resolving to {@code true} when authorized
     */
    Promise<Boolean> isAuthorized(DmOperationContext context, String resource, String action);

    /**
     * Verifies that the data subject identified by {@code subjectId} has given consent
     * for the specified marketing {@code purpose}.
     *
     * @param context   the operation context
     * @param subjectId the contact or data subject identifier
     * @param purpose   the consent purpose (e.g. {@code "marketing-email"})
     * @return promise resolving to {@code true} when valid consent exists
     */
    Promise<Boolean> verifyConsent(DmOperationContext context, String subjectId, String purpose);

    /**
     * Requests human approval for a write or lifecycle operation.
     *
     * <p>Returns the approval request identifier. Callers should use the returned ID
     * to poll approval status asynchronously.</p>
     *
     * @param context       the operation context
     * @param operationType a short descriptor for the type of operation requiring approval
     * @param subjectId     the identifier of the entity the approval is for
     * @param description   human-readable description of what is being approved
     * @return promise resolving to the approval request ID
     */
    Promise<String> requestApproval(
        DmOperationContext context,
        String operationType,
        String subjectId,
        String description
    );

    /**
     * Records an audit event for a completed DMOS operation.
     *
     * @param context    the operation context
     * @param entityId   the entity this audit event is about
     * @param action     the action that was performed
     * @param attributes additional key–value attributes to record in the audit trail
     * @return promise resolving to the audit entry identifier
     */
    Promise<String> recordAudit(
        DmOperationContext context,
        String entityId,
        String action,
        java.util.Map<String, Object> attributes
    );

    /**
     * Evaluates risk for a product entity using a named risk model.
     *
     * <p>Default implementation returns a conservative low score ({@code 0.0})
     * to preserve compatibility with older adapter implementations.</p>
     *
     * @param context operation context
     * @param entityId target entity identifier
     * @param riskModelId model identifier (e.g. {@code "DM_CAMPAIGN_LAUNCH"})
     * @param factors model input factors
     * @return promise resolving to a normalized score in range {@code [0.0, 1.0]}
     */
    default Promise<Double> evaluateRisk(
        DmOperationContext context,
        String entityId,
        String riskModelId,
        Map<String, Object> factors
    ) {
        return Promise.of(0.0d);
    }

    /**
     * Dispatches a notification to the specified recipient using the given template.
     *
     * <p>KE-02: When the platform {@code NotificationPlugin} is available, the
     * production implementation will delegate to it via the {@code KernelEventBus}.
     * Until then, the default emits a no-op that is safe to call in all environments;
     * the production override in {@link DigitalMarketingKernelAdapterImpl} logs a
     * structured audit entry so notifications remain diagnosable.</p>
     *
     * @param context     the operation context carrying tenant and correlation identity
     * @param recipientId recipient user or contact identifier
     * @param template    notification template key (e.g. {@code "dmos.campaign.launched"})
     * @param attributes  template merge attributes
     * @return promise completing when the notification has been dispatched
     */
    default Promise<Void> notifyUser(
        DmOperationContext context,
        String recipientId,
        String template,
        Map<String, String> attributes
    ) {
        return Promise.of(null);
    }

    /**
     * Returns {@code true} if the given product feature flag is currently enabled.
     *
     * <p>KE-05: When the platform {@code FeatureFlagPlugin} is available, the
     * production implementation will delegate to it. The default implementation
     * fails closed (returns {@code false}) for incomplete features in production
     * to prevent accidental exposure of non-GA capabilities.</p>
     *
     * <p>Production implementations must override this method to delegate to
     * the platform FeatureFlagPlugin with proper fallback behavior.</p>
     *
     * @param context operation context
     * @param flagKey feature flag key (e.g. {@code "dmos.ai_features_enabled"})
     * @return promise of {@code true} if the flag is enabled
     */
    default Promise<Boolean> isFeatureEnabled(DmOperationContext context, String flagKey) {
        // Fail closed: return false by default for production safety
        // Production implementations must override to delegate to FeatureFlagPlugin
        return Promise.of(false);
    }
}
