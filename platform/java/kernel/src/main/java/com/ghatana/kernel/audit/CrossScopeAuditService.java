package com.ghatana.kernel.audit;

import com.ghatana.kernel.policy.AuditPolicyResolver;
import com.ghatana.kernel.policy.AuditPolicyResolver.AuditPolicy;
import com.ghatana.kernel.policy.ClassificationDescriptor;
import com.ghatana.kernel.scope.ScopeDescriptor;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.*;

/**
 * Scope-aware audit service using policy-driven retention and classification.
 *
 * <p>Canonical replacement for {@link CrossProductAuditService}. Rather than branching
 * on product id strings to determine retention and compliance rules, this service
 * delegates to an {@link AuditPolicyResolver} that resolves policy from classification
 * metadata carried by {@link ScopeDescriptor} and {@link ClassificationDescriptor}.</p>
 *
 * <p>Per KERNEL_CANONICALIZATION_DECISIONS.md §5.1:</p>
 * <ul>
 *   <li>Event metadata includes source scope, target scope, tenant, capability, and classification</li>
 *   <li>Retention is resolved from policy metadata, not from product string checks</li>
 *   <li>All audit events are immutable and cryptographically signed</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Scope-aware, policy-driven audit service replacing product-specific audit logic
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public class CrossScopeAuditService {

    private final AuditPolicyResolver policyResolver;
    private final AuditEventStore auditStore;

    /**
     * Creates a scope-aware audit service.
     *
     * @param policyResolver resolves audit policy from scope and classification metadata
     * @param auditStore     storage backend for audit records
     */
    public CrossScopeAuditService(AuditPolicyResolver policyResolver, AuditEventStore auditStore) {
        this.policyResolver = Objects.requireNonNull(policyResolver, "policyResolver cannot be null");
        this.auditStore = Objects.requireNonNull(auditStore, "auditStore cannot be null");
    }

    /**
     * Records a cross-scope audit event with policy-driven retention.
     *
     * @param event the audit event
     * @return Promise completing when audit is stored
     */
    public Promise<Void> auditCrossScopeAction(CrossScopeAuditEvent event) {
        Objects.requireNonNull(event, "event cannot be null");

        // Resolve audit policy from classification, not product ids
        AuditPolicy policy = policyResolver.resolve(
                event.getSourceScope(), event.getTargetScope(), event.getClassification());

        // Build immutable audit record
        ScopeAuditRecord record = ScopeAuditRecord.builder()
                .auditId(generateAuditId(event))
                .eventType("cross-scope." + event.getAction())
                .sourceScope(event.getSourceScope())
                .targetScope(event.getTargetScope())
                .userId(event.getUserId())
                .tenantId(event.getTenantId())
                .capabilityId(event.getCapabilityId())
                .classification(event.getClassification())
                .timestamp(Instant.now())
                .metadata(event.getMetadata())
                .retentionYears(policy.retentionYears())
                .storageTier(policy.storageTier())
                .signature(policy.signatureRequired() ? generateSignature(event) : null)
                .build();

        return auditStore.store(record);
    }

    /**
     * Queries audit records by scope and time range.
     *
     * @param startDate   query start
     * @param endDate     query end
     * @param sourceScope optional source scope filter (null for all)
     * @param targetScope optional target scope filter (null for all)
     * @return Promise containing matching records
     */
    public Promise<Set<ScopeAuditRecord>> queryAudits(Instant startDate, Instant endDate,
                                                       ScopeDescriptor sourceScope,
                                                       ScopeDescriptor targetScope) {
        return auditStore.query(startDate, endDate, sourceScope, targetScope);
    }

    private String generateAuditId(CrossScopeAuditEvent event) {
        return String.format("AUDIT-%s-%s-%s-%d",
                event.getSourceScope(), event.getTargetScope(),
                event.getAction(), Instant.now().toEpochMilli());
    }

    private String generateSignature(CrossScopeAuditEvent event) {
        // In production: use HMAC-SHA256 with audit signing key
        return Base64.getEncoder()
                .encodeToString((event.toString() + Instant.now()).getBytes());
    }

    // ==================== Event Type ====================

    /**
     * Scope-aware audit event carrying scope descriptors and classification.
     */
    public static class CrossScopeAuditEvent {
        private final ScopeDescriptor sourceScope;
        private final ScopeDescriptor targetScope;
        private final String action;
        private final String userId;
        private final String tenantId;
        private final String capabilityId;
        private final ClassificationDescriptor classification;
        private final Map<String, Object> metadata;

        private CrossScopeAuditEvent(Builder builder) {
            this.sourceScope = Objects.requireNonNull(builder.sourceScope, "sourceScope required");
            this.targetScope = Objects.requireNonNull(builder.targetScope, "targetScope required");
            this.action = Objects.requireNonNull(builder.action, "action required");
            this.userId = builder.userId;
            this.tenantId = builder.tenantId;
            this.capabilityId = builder.capabilityId;
            this.classification = Objects.requireNonNull(builder.classification, "classification required");
            this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
        }

        public ScopeDescriptor getSourceScope() { return sourceScope; }
        public ScopeDescriptor getTargetScope() { return targetScope; }
        public String getAction() { return action; }
        public String getUserId() { return userId; }
        public String getTenantId() { return tenantId; }
        public String getCapabilityId() { return capabilityId; }
        public ClassificationDescriptor getClassification() { return classification; }
        public Map<String, Object> getMetadata() { return metadata; }

        @Override
        public String toString() {
            return String.format("AuditEvent{source=%s, target=%s, action=%s}",
                    sourceScope, targetScope, action);
        }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private ScopeDescriptor sourceScope;
            private ScopeDescriptor targetScope;
            private String action;
            private String userId;
            private String tenantId;
            private String capabilityId;
            private ClassificationDescriptor classification;
            private Map<String, Object> metadata;

            public Builder sourceScope(ScopeDescriptor s) { this.sourceScope = s; return this; }
            public Builder targetScope(ScopeDescriptor s) { this.targetScope = s; return this; }
            public Builder action(String a) { this.action = a; return this; }
            public Builder userId(String u) { this.userId = u; return this; }
            public Builder tenantId(String t) { this.tenantId = t; return this; }
            public Builder capabilityId(String c) { this.capabilityId = c; return this; }
            public Builder classification(ClassificationDescriptor c) { this.classification = c; return this; }
            public Builder metadata(Map<String, Object> m) { this.metadata = m; return this; }
            public CrossScopeAuditEvent build() { return new CrossScopeAuditEvent(this); }
        }
    }

    // ==================== Record Type ====================

    /**
     * Immutable audit record with scope and classification metadata.
     */
    public static class ScopeAuditRecord {
        private final String auditId;
        private final String eventType;
        private final ScopeDescriptor sourceScope;
        private final ScopeDescriptor targetScope;
        private final String userId;
        private final String tenantId;
        private final String capabilityId;
        private final ClassificationDescriptor classification;
        private final Instant timestamp;
        private final Map<String, Object> metadata;
        private final int retentionYears;
        private final String storageTier;
        private final String signature;

        private ScopeAuditRecord(Builder builder) {
            this.auditId = builder.auditId;
            this.eventType = builder.eventType;
            this.sourceScope = builder.sourceScope;
            this.targetScope = builder.targetScope;
            this.userId = builder.userId;
            this.tenantId = builder.tenantId;
            this.capabilityId = builder.capabilityId;
            this.classification = builder.classification;
            this.timestamp = builder.timestamp;
            this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
            this.retentionYears = builder.retentionYears;
            this.storageTier = builder.storageTier;
            this.signature = builder.signature;
        }

        public String getAuditId() { return auditId; }
        public String getEventType() { return eventType; }
        public ScopeDescriptor getSourceScope() { return sourceScope; }
        public ScopeDescriptor getTargetScope() { return targetScope; }
        public String getUserId() { return userId; }
        public String getTenantId() { return tenantId; }
        public String getCapabilityId() { return capabilityId; }
        public ClassificationDescriptor getClassification() { return classification; }
        public Instant getTimestamp() { return timestamp; }
        public Map<String, Object> getMetadata() { return metadata; }
        public int getRetentionYears() { return retentionYears; }
        public String getStorageTier() { return storageTier; }
        public String getSignature() { return signature; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private String auditId;
            private String eventType;
            private ScopeDescriptor sourceScope;
            private ScopeDescriptor targetScope;
            private String userId;
            private String tenantId;
            private String capabilityId;
            private ClassificationDescriptor classification;
            private Instant timestamp;
            private Map<String, Object> metadata;
            private int retentionYears;
            private String storageTier;
            private String signature;

            public Builder auditId(String v) { this.auditId = v; return this; }
            public Builder eventType(String v) { this.eventType = v; return this; }
            public Builder sourceScope(ScopeDescriptor v) { this.sourceScope = v; return this; }
            public Builder targetScope(ScopeDescriptor v) { this.targetScope = v; return this; }
            public Builder userId(String v) { this.userId = v; return this; }
            public Builder tenantId(String v) { this.tenantId = v; return this; }
            public Builder capabilityId(String v) { this.capabilityId = v; return this; }
            public Builder classification(ClassificationDescriptor v) { this.classification = v; return this; }
            public Builder timestamp(Instant v) { this.timestamp = v; return this; }
            public Builder metadata(Map<String, Object> v) { this.metadata = v; return this; }
            public Builder retentionYears(int v) { this.retentionYears = v; return this; }
            public Builder storageTier(String v) { this.storageTier = v; return this; }
            public Builder signature(String v) { this.signature = v; return this; }
            public ScopeAuditRecord build() { return new ScopeAuditRecord(this); }
        }
    }

    // ==================== Store Interface ====================

    /**
     * Storage abstraction for audit records.
     *
     * <p>Implementations may use DataCloud, dedicated audit databases, or other
     * persistence backends. This interface keeps the audit service decoupled
     * from any specific storage technology.</p>
     */
    public interface AuditEventStore {
        /**
         * Stores an audit record.
         */
        Promise<Void> store(ScopeAuditRecord record);

        /**
         * Queries audit records by time range and optional scope filters.
         */
        Promise<Set<ScopeAuditRecord>> query(Instant startDate, Instant endDate,
                                              ScopeDescriptor sourceScope,
                                              ScopeDescriptor targetScope);
    }
}
