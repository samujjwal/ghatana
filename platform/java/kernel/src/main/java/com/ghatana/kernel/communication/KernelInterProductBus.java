package com.ghatana.kernel.communication;

import com.ghatana.kernel.adapter.aep.AepKernelAdapter;
import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Cross-product event publishing and data sharing through AEP/Data-Cloud adapters.
 *
 * <p>Enables secure communication between products (e.g., PHR ↔ Finance) through:
 * <ul>
 *   <li>AEP event bus for real-time event propagation</li>
 *   <li>Data-Cloud shared data stores for persistent data exchange</li>
 *   <li>Policy-gated sharing with audit trails</li>
 * </ul></p>
 *
 * <p>All operations use ActiveJ Promise — CompletableFuture is BANNED.</p>
 *
 * @doc.type class
 * @doc.purpose Cross-product event bus — routes events between products via AEP
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 * @deprecated Use {@link KernelInterScopeBus} instead. This class uses product id
 *             strings that violate kernel purity. Scheduled for removal after Day 10 migration.
 */
@Deprecated(forRemoval = true)
public class KernelInterProductBus {

    private final AepKernelAdapter aepAdapter;
    private final DataCloudKernelAdapter dataCloudAdapter;

    /**
     * Creates a new inter-product bus.
     *
     * @param aepAdapter the AEP adapter for event publishing
     * @param dataCloudAdapter the Data-Cloud adapter for data sharing
     */
    public KernelInterProductBus(AepKernelAdapter aepAdapter,
                                  DataCloudKernelAdapter dataCloudAdapter) {
        this.aepAdapter = Objects.requireNonNull(aepAdapter, "aepAdapter cannot be null");
        this.dataCloudAdapter = Objects.requireNonNull(dataCloudAdapter, "dataCloudAdapter cannot be null");
    }

    /**
     * Publishes a cross-product event through AEP.
     *
     * <p>Events are routed to the target product's event stream with proper
     * authentication, authorization, and audit logging.</p>
     *
     * @param event the cross-product event to publish
     * @return Promise completing when event is published
     */
    public Promise<Void> publishCrossProductEvent(CrossProductEvent event) {
        Objects.requireNonNull(event, "event cannot be null");

        // Wrap in AEP event format
        AepEvent aepEvent = AepEvent.builder()
            .type("cross-product." + event.getType())
            .payload(event.getPayload())
            .sourceProduct(event.getSourceProduct())
            .targetProduct(event.getTargetProduct())
            .tenantId(event.getTenantId())
            .timestamp(Instant.now())
            .correlationId(event.getCorrelationId())
            .build();

        // Publish through AEP adapter (wraps CompletableFuture with Promise)
        return aepAdapter.publishEvent(aepEvent);
    }

    /**
     * Subscribes to cross-product events from a specific source.
     *
     * @param sourceProduct the source product identifier
     * @param eventType the event type to subscribe to
     * @param handler the event handler
     * @return Promise completing when subscription is active
     */
    public Promise<Void> subscribeToEvents(String sourceProduct, String eventType,
                                           EventHandler<CrossProductEvent> handler) {
        Objects.requireNonNull(sourceProduct, "sourceProduct cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(handler, "handler cannot be null");

        return aepAdapter.subscribe("cross-product." + eventType, aepEvent -> {
            CrossProductEvent event = CrossProductEvent.builder()
                .type(eventType)
                .sourceProduct(aepEvent.getSourceProduct())
                .targetProduct(aepEvent.getTargetProduct())
                .payload(aepEvent.getPayload())
                .tenantId(aepEvent.getTenantId())
                .correlationId(aepEvent.getCorrelationId())
                .build();
            handler.handle(event);
        });
    }

    /**
     * Shares data across products through Data-Cloud.
     *
     * <p>Data is stored in a shared data store with policy-gated access control.</p>
     *
     * @param dataId the data identifier
     * @param data the data to share
     * @param policy the sharing policy
     * @return Promise completing when data is shared
     */
    public Promise<Void> shareCrossProductData(String dataId, Object data,
                                                CrossProductSharePolicy policy) {
        Objects.requireNonNull(dataId, "dataId cannot be null");
        Objects.requireNonNull(data, "data cannot be null");
        Objects.requireNonNull(policy, "policy cannot be null");

        // Create shared data record with policy
        SharedDataRecord record = SharedDataRecord.builder()
            .dataId(dataId)
            .data(data)
            .sourceProduct(policy.getSourceProduct())
            .targetProduct(policy.getTargetProduct())
            .accessPolicy(policy.getAccessPolicy())
            .retentionPeriod(policy.getRetentionPeriod())
            .encryptionRequired(policy.isEncryptionRequired())
            .auditRequired(policy.isAuditRequired())
            .createdAt(Instant.now())
            .build();

        // Store in Data-Cloud shared store
        return dataCloudAdapter.storeSharedData(record);
    }

    /**
     * Retrieves shared data from another product.
     *
     * @param dataId the data identifier
     * @param requestingProduct the product requesting access
     * @return Promise containing the shared data if accessible
     */
    public Promise<SharedDataRecord> retrieveSharedData(String dataId, String requestingProduct) {
        Objects.requireNonNull(dataId, "dataId cannot be null");
        Objects.requireNonNull(requestingProduct, "requestingProduct cannot be null");

        return dataCloudAdapter.retrieveSharedData(dataId, requestingProduct);
    }

    // ==================== Inner Types ====================

    /**
     * Cross-product event.
     */
    public static class CrossProductEvent {
        private final String type;
        private final String sourceProduct;
        private final String targetProduct;
        private final Object payload;
        private final String tenantId;
        private final String correlationId;

        private CrossProductEvent(Builder builder) {
            this.type = builder.type;
            this.sourceProduct = builder.sourceProduct;
            this.targetProduct = builder.targetProduct;
            this.payload = builder.payload;
            this.tenantId = builder.tenantId;
            this.correlationId = builder.correlationId;
        }

        public String getType() { return type; }
        public String getSourceProduct() { return sourceProduct; }
        public String getTargetProduct() { return targetProduct; }
        public Object getPayload() { return payload; }
        public String getTenantId() { return tenantId; }
        public String getCorrelationId() { return correlationId; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String type;
            private String sourceProduct;
            private String targetProduct;
            private Object payload;
            private String tenantId;
            private String correlationId;

            public Builder type(String type) { this.type = type; return this; }
            public Builder sourceProduct(String sourceProduct) { this.sourceProduct = sourceProduct; return this; }
            public Builder targetProduct(String targetProduct) { this.targetProduct = targetProduct; return this; }
            public Builder payload(Object payload) { this.payload = payload; return this; }
            public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
            public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }

            public CrossProductEvent build() {
                return new CrossProductEvent(this);
            }
        }
    }

    /**
     * Cross-product data sharing policy.
     */
    public static class CrossProductSharePolicy {
        private final String sourceProduct;
        private final String targetProduct;
        private final String accessPolicy;
        private final java.time.Duration retentionPeriod;
        private final boolean encryptionRequired;
        private final boolean auditRequired;

        public CrossProductSharePolicy(String sourceProduct, String targetProduct,
                                       String accessPolicy, java.time.Duration retentionPeriod,
                                       boolean encryptionRequired, boolean auditRequired) {
            this.sourceProduct = sourceProduct;
            this.targetProduct = targetProduct;
            this.accessPolicy = accessPolicy;
            this.retentionPeriod = retentionPeriod;
            this.encryptionRequired = encryptionRequired;
            this.auditRequired = auditRequired;
        }

        public String getSourceProduct() { return sourceProduct; }
        public String getTargetProduct() { return targetProduct; }
        public String getAccessPolicy() { return accessPolicy; }
        public java.time.Duration getRetentionPeriod() { return retentionPeriod; }
        public boolean isEncryptionRequired() { return encryptionRequired; }
        public boolean isAuditRequired() { return auditRequired; }
    }

    /**
     * Shared data record.
     */
    public static class SharedDataRecord {
        private final String dataId;
        private final Object data;
        private final String sourceProduct;
        private final String targetProduct;
        private final String accessPolicy;
        private final java.time.Duration retentionPeriod;
        private final boolean encryptionRequired;
        private final boolean auditRequired;
        private final Instant createdAt;

        private SharedDataRecord(Builder builder) {
            this.dataId = builder.dataId;
            this.data = builder.data;
            this.sourceProduct = builder.sourceProduct;
            this.targetProduct = builder.targetProduct;
            this.accessPolicy = builder.accessPolicy;
            this.retentionPeriod = builder.retentionPeriod;
            this.encryptionRequired = builder.encryptionRequired;
            this.auditRequired = builder.auditRequired;
            this.createdAt = builder.createdAt;
        }

        public String getDataId() { return dataId; }
        public Object getData() { return data; }
        public String getSourceProduct() { return sourceProduct; }
        public String getTargetProduct() { return targetProduct; }
        public String getAccessPolicy() { return accessPolicy; }
        public java.time.Duration getRetentionPeriod() { return retentionPeriod; }
        public boolean isEncryptionRequired() { return encryptionRequired; }
        public boolean isAuditRequired() { return auditRequired; }
        public Instant getCreatedAt() { return createdAt; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String dataId;
            private Object data;
            private String sourceProduct;
            private String targetProduct;
            private String accessPolicy;
            private java.time.Duration retentionPeriod;
            private boolean encryptionRequired;
            private boolean auditRequired;
            private Instant createdAt;

            public Builder dataId(String dataId) { this.dataId = dataId; return this; }
            public Builder data(Object data) { this.data = data; return this; }
            public Builder sourceProduct(String sourceProduct) { this.sourceProduct = sourceProduct; return this; }
            public Builder targetProduct(String targetProduct) { this.targetProduct = targetProduct; return this; }
            public Builder accessPolicy(String accessPolicy) { this.accessPolicy = accessPolicy; return this; }
            public Builder retentionPeriod(java.time.Duration retentionPeriod) { this.retentionPeriod = retentionPeriod; return this; }
            public Builder encryptionRequired(boolean encryptionRequired) { this.encryptionRequired = encryptionRequired; return this; }
            public Builder auditRequired(boolean auditRequired) { this.auditRequired = auditRequired; return this; }
            public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }

            public SharedDataRecord build() {
                return new SharedDataRecord(this);
            }
        }
    }

    /**
     * AEP event wrapper.
     */
    public static class AepEvent {
        private final String type;
        private final Object payload;
        private final String sourceProduct;
        private final String targetProduct;
        private final String tenantId;
        private final Instant timestamp;
        private final String correlationId;

        private AepEvent(Builder builder) {
            this.type = builder.type;
            this.payload = builder.payload;
            this.sourceProduct = builder.sourceProduct;
            this.targetProduct = builder.targetProduct;
            this.tenantId = builder.tenantId;
            this.timestamp = builder.timestamp;
            this.correlationId = builder.correlationId;
        }

        public String getType() { return type; }
        public Object getPayload() { return payload; }
        public String getSourceProduct() { return sourceProduct; }
        public String getTargetProduct() { return targetProduct; }
        public String getTenantId() { return tenantId; }
        public Instant getTimestamp() { return timestamp; }
        public String getCorrelationId() { return correlationId; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String type;
            private Object payload;
            private String sourceProduct;
            private String targetProduct;
            private String tenantId;
            private Instant timestamp;
            private String correlationId;

            public Builder type(String type) { this.type = type; return this; }
            public Builder payload(Object payload) { this.payload = payload; return this; }
            public Builder sourceProduct(String sourceProduct) { this.sourceProduct = sourceProduct; return this; }
            public Builder targetProduct(String targetProduct) { this.targetProduct = targetProduct; return this; }
            public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
            public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
            public Builder correlationId(String correlationId) { this.correlationId = correlationId; return this; }

            public AepEvent build() {
                return new AepEvent(this);
            }
        }
    }

    /**
     * Event handler interface.
     */
    @FunctionalInterface
    public interface EventHandler<T> {
        void handle(T event);
    }

    // Stub adapter interfaces (would be implemented by actual adapters)
    private interface AepKernelAdapter {
        Promise<Void> publishEvent(AepEvent event);
        Promise<Void> subscribe(String eventType, EventHandler<AepEvent> handler);
    }

    private interface DataCloudKernelAdapter {
        Promise<Void> storeSharedData(SharedDataRecord record);
        Promise<SharedDataRecord> retrieveSharedData(String dataId, String requestingProduct);
    }
}
