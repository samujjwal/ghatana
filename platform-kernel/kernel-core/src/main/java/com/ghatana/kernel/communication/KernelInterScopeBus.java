package com.ghatana.kernel.communication;

import com.ghatana.kernel.policy.ClassificationDescriptor;
import com.ghatana.kernel.scope.ScopeDescriptor;
import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Scope-aware inter-scope event bus and data sharing service.
 *
 * <p>Canonical replacement for {@link KernelInterProductBus}. Operates on
 * {@link ScopeDescriptor} source/target pairs and {@link ClassificationDescriptor}
 * metadata instead of product id strings. All operations use ActiveJ Promise.</p>
 *
 * <p>Provides two communication channels:</p>
 * <ul>
 *   <li><b>Event bus</b>: Real-time event propagation between scopes via an EventBusAdapter</li>
 *   <li><b>Shared data</b>: Policy-gated persistent data exchange via a DataShareAdapter</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Scope-aware inter-scope event bus replacing product-id-based communication
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public class KernelInterScopeBus {

    private final EventBusAdapter eventBusAdapter;
    private final DataShareAdapter dataShareAdapter;

    /**
     * Creates a new inter-scope bus.
     *
     * @param eventBusAdapter adapter for scope-to-scope event publishing
     * @param dataShareAdapter adapter for scope-to-scope data sharing
     */
    public KernelInterScopeBus(EventBusAdapter eventBusAdapter,
                               DataShareAdapter dataShareAdapter) {
        this.eventBusAdapter = Objects.requireNonNull(eventBusAdapter, "eventBusAdapter cannot be null");
        this.dataShareAdapter = Objects.requireNonNull(dataShareAdapter, "dataShareAdapter cannot be null");
    }

    /**
     * Publishes a cross-scope event.
     *
     * @param event the cross-scope event to publish
     * @return Promise completing when event is published
     */
    public Promise<Void> publishEvent(CrossScopeEvent event) {
        Objects.requireNonNull(event, "event cannot be null");
        return eventBusAdapter.publish(event);
    }

    /**
     * Subscribes to events from a specific source scope.
     *
     * @param sourceScope the source scope to listen to
     * @param eventType   the event type filter
     * @param handler     the event handler
     * @return Promise completing when subscription is active
     */
    public Promise<Void> subscribe(ScopeDescriptor sourceScope, String eventType,
                                   EventHandler<CrossScopeEvent> handler) {
        Objects.requireNonNull(sourceScope, "sourceScope cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(handler, "handler cannot be null");

        return eventBusAdapter.subscribe(sourceScope, eventType, handler);
    }

    /**
     * Shares data across scopes with policy governance.
     *
     * @param dataId         the data identifier
     * @param data           the data to share
     * @param sharePolicy    the sharing policy
     * @return Promise completing when data is persisted
     */
    public Promise<Void> shareData(String dataId, Object data, ScopeSharePolicy sharePolicy) {
        Objects.requireNonNull(dataId, "dataId cannot be null");
        Objects.requireNonNull(data, "data cannot be null");
        Objects.requireNonNull(sharePolicy, "sharePolicy cannot be null");

        SharedScopeRecord record = SharedScopeRecord.builder()
                .dataId(dataId)
                .data(data)
                .sourceScope(sharePolicy.sourceScope())
                .targetScope(sharePolicy.targetScope())
                .classification(sharePolicy.classification())
                .retentionPeriod(sharePolicy.retentionPeriod())
                .encryptionRequired(sharePolicy.encryptionRequired())
                .auditRequired(sharePolicy.auditRequired())
                .createdAt(Instant.now())
                .build();

        return dataShareAdapter.store(record);
    }

    /**
     * Retrieves shared data from another scope.
     *
     * @param dataId           the data identifier
     * @param requestingScope  the scope requesting access
     * @return Promise containing the shared data if accessible
     */
    public Promise<SharedScopeRecord> retrieveSharedData(String dataId,
                                                          ScopeDescriptor requestingScope) {
        Objects.requireNonNull(dataId, "dataId cannot be null");
        Objects.requireNonNull(requestingScope, "requestingScope cannot be null");

        return dataShareAdapter.retrieve(dataId, requestingScope);
    }

    // ==================== Adapter Interfaces ====================

    /**
     * Adapter for scope-to-scope event publishing.
     */
    public interface EventBusAdapter {
        Promise<Void> publish(CrossScopeEvent event);
        Promise<Void> subscribe(ScopeDescriptor sourceScope, String eventType,
                                EventHandler<CrossScopeEvent> handler);
    }

    /**
     * Adapter for scope-to-scope data sharing.
     */
    public interface DataShareAdapter {
        Promise<Void> store(SharedScopeRecord record);
        Promise<SharedScopeRecord> retrieve(String dataId, ScopeDescriptor requestingScope);
    }

    /**
     * Event handler interface.
     */
    @FunctionalInterface
    public interface EventHandler<T> {
        void handle(T event);
    }

    // ==================== Event Type ====================

    /**
     * Cross-scope event carrying scope descriptors and classification.
     */
    public static class CrossScopeEvent {
        private final String type;
        private final ScopeDescriptor sourceScope;
        private final ScopeDescriptor targetScope;
        private final Object payload;
        private final String tenantId;
        private final String correlationId;
        private final ClassificationDescriptor classification;

        private CrossScopeEvent(Builder builder) {
            this.type = Objects.requireNonNull(builder.type, "type required");
            this.sourceScope = Objects.requireNonNull(builder.sourceScope, "sourceScope required");
            this.targetScope = Objects.requireNonNull(builder.targetScope, "targetScope required");
            this.payload = builder.payload;
            this.tenantId = builder.tenantId;
            this.correlationId = builder.correlationId;
            this.classification = builder.classification;
        }

        public String getType() { return type; }
        public ScopeDescriptor getSourceScope() { return sourceScope; }
        public ScopeDescriptor getTargetScope() { return targetScope; }
        public Object getPayload() { return payload; }
        public String getTenantId() { return tenantId; }
        public String getCorrelationId() { return correlationId; }
        public ClassificationDescriptor getClassification() { return classification; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private String type;
            private ScopeDescriptor sourceScope;
            private ScopeDescriptor targetScope;
            private Object payload;
            private String tenantId;
            private String correlationId;
            private ClassificationDescriptor classification;

            public Builder type(String t) { this.type = t; return this; }
            public Builder sourceScope(ScopeDescriptor s) { this.sourceScope = s; return this; }
            public Builder targetScope(ScopeDescriptor s) { this.targetScope = s; return this; }
            public Builder payload(Object p) { this.payload = p; return this; }
            public Builder tenantId(String t) { this.tenantId = t; return this; }
            public Builder correlationId(String c) { this.correlationId = c; return this; }
            public Builder classification(ClassificationDescriptor c) { this.classification = c; return this; }
            public CrossScopeEvent build() { return new CrossScopeEvent(this); }
        }
    }

    // ==================== Sharing Policy ====================

    /**
     * Policy governing cross-scope data sharing.
     *
     * @param sourceScope        the sharing source
     * @param targetScope        the sharing target
     * @param classification     classification metadata
     * @param retentionPeriod    how long to retain shared data
     * @param encryptionRequired whether data must be encrypted at rest
     * @param auditRequired      whether an audit trail is required
     */
    public record ScopeSharePolicy(
            ScopeDescriptor sourceScope,
            ScopeDescriptor targetScope,
            ClassificationDescriptor classification,
            Duration retentionPeriod,
            boolean encryptionRequired,
            boolean auditRequired
    ) {}

    // ==================== Shared Record ====================

    /**
     * A shared data record with scope and classification metadata.
     */
    public static class SharedScopeRecord {
        private final String dataId;
        private final Object data;
        private final ScopeDescriptor sourceScope;
        private final ScopeDescriptor targetScope;
        private final ClassificationDescriptor classification;
        private final Duration retentionPeriod;
        private final boolean encryptionRequired;
        private final boolean auditRequired;
        private final Instant createdAt;

        private SharedScopeRecord(Builder builder) {
            this.dataId = builder.dataId;
            this.data = builder.data;
            this.sourceScope = builder.sourceScope;
            this.targetScope = builder.targetScope;
            this.classification = builder.classification;
            this.retentionPeriod = builder.retentionPeriod;
            this.encryptionRequired = builder.encryptionRequired;
            this.auditRequired = builder.auditRequired;
            this.createdAt = builder.createdAt;
        }

        public String getDataId() { return dataId; }
        public Object getData() { return data; }
        public ScopeDescriptor getSourceScope() { return sourceScope; }
        public ScopeDescriptor getTargetScope() { return targetScope; }
        public ClassificationDescriptor getClassification() { return classification; }
        public Duration getRetentionPeriod() { return retentionPeriod; }
        public boolean isEncryptionRequired() { return encryptionRequired; }
        public boolean isAuditRequired() { return auditRequired; }
        public Instant getCreatedAt() { return createdAt; }

        public static Builder builder() { return new Builder(); }

        public static class Builder {
            private String dataId;
            private Object data;
            private ScopeDescriptor sourceScope;
            private ScopeDescriptor targetScope;
            private ClassificationDescriptor classification;
            private Duration retentionPeriod;
            private boolean encryptionRequired;
            private boolean auditRequired;
            private Instant createdAt;

            public Builder dataId(String v) { this.dataId = v; return this; }
            public Builder data(Object v) { this.data = v; return this; }
            public Builder sourceScope(ScopeDescriptor v) { this.sourceScope = v; return this; }
            public Builder targetScope(ScopeDescriptor v) { this.targetScope = v; return this; }
            public Builder classification(ClassificationDescriptor v) { this.classification = v; return this; }
            public Builder retentionPeriod(Duration v) { this.retentionPeriod = v; return this; }
            public Builder encryptionRequired(boolean v) { this.encryptionRequired = v; return this; }
            public Builder auditRequired(boolean v) { this.auditRequired = v; return this; }
            public Builder createdAt(Instant v) { this.createdAt = v; return this; }
            public SharedScopeRecord build() { return new SharedScopeRecord(this); }
        }
    }
}
