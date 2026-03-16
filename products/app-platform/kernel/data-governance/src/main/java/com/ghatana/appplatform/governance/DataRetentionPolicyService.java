package com.ghatana.appplatform.governance;

import com.ghatana.appplatform.governance.port.RetentionPolicyStore;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type    DomainService
 * @doc.purpose Defines and stores data retention policies per asset. Default: 7 years for
 *              financial data (NRB requirement). Actions: ARCHIVE / DELETE / ANONYMIZE.
 *              Policy registry with CRUD. K-01 maker-checker for policy changes.
 *              Publishes RetentionPolicyApplied event. Satisfies STORY-K08-009.
 * @doc.layer   Kernel
 * @doc.pattern 7-year NRB default retention; K-01 maker-checker; ARCHIVE/DELETE/ANONYMIZE
 *              actions; glob pattern matching; RetentionPolicyApplied event; Gauge.
 */
public class DataRetentionPolicyService {

    private static final int DEFAULT_RETENTION_YEARS = 7;

    private final RetentionPolicyStore policyStore;
    private final Executor         executor;
    private final WorkflowPort     workflowPort;
    private final EventPort        eventPort;
    private final Counter          policiesAppliedCounter;
    private final AtomicLong       activePoliciesGauge = new AtomicLong(0);

    public DataRetentionPolicyService(RetentionPolicyStore policyStore, Executor executor,
                                       WorkflowPort workflowPort, EventPort eventPort,
                                       MeterRegistry registry) {
        this.policyStore            = policyStore;
        this.executor              = executor;
        this.workflowPort          = workflowPort;
        this.eventPort             = eventPort;
        this.policiesAppliedCounter = Counter.builder("governance.retention.policies_applied_total").register(registry);
        Gauge.builder("governance.retention.active_policies", activePoliciesGauge, AtomicLong::get)
                .register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    public interface WorkflowPort {
        String createApprovalTask(String policyId, String submittedBy, String action);
    }

    public interface EventPort {
        void publish(String topic, Object event);
    }

    // ─── Enums & Records ─────────────────────────────────────────────────────

    public enum RetentionAction { ARCHIVE, DELETE, ANONYMIZE }

    public record RetentionPolicy(String policyId, String assetPattern, int retentionDays,
                                   RetentionAction action, String regulatoryBasis,
                                   boolean active, LocalDateTime createdAt) {}

    public record PolicyMatch(String assetId, String assetName, RetentionPolicy policy) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    public Promise<RetentionPolicy> createPolicy(String assetPattern, int retentionDays,
                                                  RetentionAction action, String regulatoryBasis,
                                                  String submittedBy) {
        return Promise.ofBlocking(executor, () -> {
            String policyId = UUID.randomUUID().toString();
            RetentionPolicy policy = policyStore.insertPolicy(policyId, assetPattern, retentionDays,
                    action, regulatoryBasis);
            // K-01 maker-checker for policy changes
            workflowPort.createApprovalTask(policyId, submittedBy, "CREATE_RETENTION_POLICY");
            activePoliciesGauge.set(policyStore.countActivePolicies());
            return policy;
        });
    }

    /** Create default 7-year NRB financial data retention policy. */
    public Promise<RetentionPolicy> createDefaultFinancialPolicy(String assetPattern,
                                                                   String submittedBy) {
        return createPolicy(assetPattern, DEFAULT_RETENTION_YEARS * 365,
                RetentionAction.ARCHIVE, "NRB Financial Records Retention Regulation", submittedBy);
    }

    public Promise<List<RetentionPolicy>> listPolicies() {
        return Promise.ofBlocking(executor, () -> policyStore.fetchAllPolicies());
    }

    public Promise<RetentionPolicy> deactivatePolicy(String policyId) {
        return Promise.ofBlocking(executor, () -> {
            RetentionPolicy p = policyStore.deactivatePolicy(policyId);
            activePoliciesGauge.set(policyStore.countActivePolicies());
            return p;
        });
    }

    /** Match an asset against all active policies — returns highest-priority match. */
    public Promise<List<PolicyMatch>> matchAssets() {
        return Promise.ofBlocking(executor, () -> {
            List<RetentionPolicyStore.AssetPolicyMatch> raw = policyStore.matchAssets();
            List<PolicyMatch> matches = new java.util.ArrayList<>();
            for (RetentionPolicyStore.AssetPolicyMatch m : raw) {
                RetentionPolicy policy = policyStore.loadPolicy(m.policyId());
                matches.add(new PolicyMatch(m.assetId(), m.assetName(), policy));
            }
            return matches;
        });
    }

    /** Mark policy as applied for a specific asset and publish event. */
    public Promise<Void> applyPolicy(String policyId, String assetId) {
        return Promise.ofBlocking(executor, () -> {
            policyStore.applyPolicy(policyId, assetId);
            policiesAppliedCounter.increment();
            eventPort.publish("governance.retention.policy_applied",
                    new RetentionPolicyAppliedEvent(policyId, assetId, LocalDateTime.now()));
            return null;
        });
    }

    record RetentionPolicyAppliedEvent(String policyId, String assetId, LocalDateTime appliedAt) {}
}
