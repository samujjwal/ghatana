package com.ghatana.kernel.communication;

import java.util.Map;

/**
 * Cross-product communication service.
 *
 * <p>Enables secure communication between different products while
 * maintaining isolation and enforcing security policies.</p>
 *
 * @doc.type interface
 * @doc.purpose Secure cross-product communication
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface CrossProductCommunication {

    /**
     * Sends a cross-product event.
     *
     * @param event the cross-product event
     */
    void sendCrossProductEvent(CrossProductEvent event);

    /**
     * Registers a handler for cross-product events.
     *
     * @param productId the product identifier
     * @param handler the event handler
     */
    void registerCrossProductHandler(String productId, KernelEventBus.EventHandler handler);

    /**
     * Unregisters a cross-product handler.
     *
     * @param productId the product identifier
     */
    void unregisterCrossProductHandler(String productId);

    /**
     * Sends a request to another product and waits for response.
     *
     * @param request the cross-product request
     * @return promise with response
     */
    io.activej.promise.Promise<CrossProductResponse> sendRequest(CrossProductRequest request);

    /**
     * Checks if cross-product communication is allowed.
     *
     * @param sourceProduct the source product
     * @param targetProduct the target product
     * @return true if communication is allowed
     */
    boolean isCommunicationAllowed(String sourceProduct, String targetProduct);

    /**
     * Represents a cross-product event.
     */
    class CrossProductEvent {
        private final String eventId;
        private final String sourceProduct;
        private final String targetProduct;
        private final String eventType;
        private final Object payload;
        private final Map<String, String> metadata;
        private final long timestamp;

        private CrossProductEvent(Builder builder) {
            this.eventId = builder.eventId;
            this.sourceProduct = builder.sourceProduct;
            this.targetProduct = builder.targetProduct;
            this.eventType = builder.eventType;
            this.payload = builder.payload;
            this.metadata = builder.metadata;
            this.timestamp = builder.timestamp;
        }

        public String getEventId() { return eventId; }
        public String getSourceProduct() { return sourceProduct; }
        public String getTargetProduct() { return targetProduct; }
        public String getEventType() { return eventType; }
        public Object getPayload() { return payload; }
        public Map<String, String> getMetadata() { return metadata; }
        public long getTimestamp() { return timestamp; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String eventId;
            private String sourceProduct;
            private String targetProduct;
            private String eventType;
            private Object payload;
            private Map<String, String> metadata;
            private long timestamp = System.currentTimeMillis();

            public Builder eventId(String eventId) {
                this.eventId = eventId;
                return this;
            }

            public Builder sourceProduct(String sourceProduct) {
                this.sourceProduct = sourceProduct;
                return this;
            }

            public Builder targetProduct(String targetProduct) {
                this.targetProduct = targetProduct;
                return this;
            }

            public Builder eventType(String eventType) {
                this.eventType = eventType;
                return this;
            }

            public Builder payload(Object payload) {
                this.payload = payload;
                return this;
            }

            public Builder metadata(Map<String, String> metadata) {
                this.metadata = metadata;
                return this;
            }

            public Builder timestamp(long timestamp) {
                this.timestamp = timestamp;
                return this;
            }

            public CrossProductEvent build() {
                return new CrossProductEvent(this);
            }
        }
    }

    /**
     * Represents a cross-product request.
     */
    class CrossProductRequest {
        private final String requestId;
        private final String sourceProduct;
        private final String targetProduct;
        private final String operation;
        private final Object payload;
        private final long timeoutMillis;

        public CrossProductRequest(String requestId, String sourceProduct, String targetProduct, 
                                   String operation, Object payload, long timeoutMillis) {
            this.requestId = requestId;
            this.sourceProduct = sourceProduct;
            this.targetProduct = targetProduct;
            this.operation = operation;
            this.payload = payload;
            this.timeoutMillis = timeoutMillis;
        }

        public String getRequestId() { return requestId; }
        public String getSourceProduct() { return sourceProduct; }
        public String getTargetProduct() { return targetProduct; }
        public String getOperation() { return operation; }
        public Object getPayload() { return payload; }
        public long getTimeoutMillis() { return timeoutMillis; }
    }

    /**
     * Represents a cross-product response.
     */
    class CrossProductResponse {
        private final String requestId;
        private final boolean success;
        private final Object result;
        private final String error;

        public CrossProductResponse(String requestId, boolean success, Object result, String error) {
            this.requestId = requestId;
            this.success = success;
            this.result = result;
            this.error = error;
        }

        public String getRequestId() { return requestId; }
        public boolean isSuccess() { return success; }
        public Object getResult() { return result; }
        public String getError() { return error; }

        public static CrossProductResponse success(String requestId, Object result) {
            return new CrossProductResponse(requestId, true, result, null);
        }

        public static CrossProductResponse failure(String requestId, String error) {
            return new CrossProductResponse(requestId, false, null, error);
        }
    }
}
