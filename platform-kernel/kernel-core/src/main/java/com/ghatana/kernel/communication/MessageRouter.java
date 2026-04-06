package com.ghatana.kernel.communication;

import java.util.List;
import java.util.Map;

/**
 * Message router for inter-scope communication.
 *
 * <p>Routes messages between different scopes (products, modules, tenants)
 * with support for message queuing, acknowledgment, and delivery guarantees.</p>
 *
 * @doc.type interface
 * @doc.purpose Message routing and delivery
 * @doc.layer core
 * @doc.pattern Router
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public interface MessageRouter {

    /**
     * Routes a message to a target scope.
     *
     * @param message the message to route
     * @param targetScope the target scope identifier
     */
    void routeMessage(Message message, String targetScope);

    /**
     * Gets pending messages for a scope.
     *
     * @param scopeId the scope identifier
     * @return list of pending messages
     */
    List<Message> getPendingMessages(String scopeId);

    /**
     * Acknowledges message receipt.
     *
     * @param messageId the message identifier
     */
    void acknowledgeMessage(String messageId);

    /**
     * Routes a message with delivery options.
     *
     * @param message the message to route
     * @param targetScope the target scope
     * @param options delivery options
     * @return routing result
     */
    RoutingResult routeMessage(Message message, String targetScope, DeliveryOptions options);

    /**
     * Broadcasts a message to multiple scopes.
     *
     * @param message the message to broadcast
     * @param targetScopes the target scope identifiers
     */
    void broadcastMessage(Message message, List<String> targetScopes);

    /**
     * Represents a routable message.
     */
    interface Message {
        String getMessageId();
        String getSourceScope();
        String getTargetScope();
        MessageType getType();
        Object getPayload();
        Map<String, String> getHeaders();
        long getTimestamp();
        int getPriority();
    }

    /**
     * Message type enumeration.
     */
    enum MessageType {
        REQUEST,
        RESPONSE,
        EVENT,
        COMMAND,
        NOTIFICATION
    }

    /**
     * Delivery options for message routing.
     */
    class DeliveryOptions {
        private final boolean persistent;
        private final int retryCount;
        private final long timeoutMillis;
        private final DeliveryMode deliveryMode;

        private DeliveryOptions(Builder builder) {
            this.persistent = builder.persistent;
            this.retryCount = builder.retryCount;
            this.timeoutMillis = builder.timeoutMillis;
            this.deliveryMode = builder.deliveryMode;
        }

        public boolean isPersistent() { return persistent; }
        public int getRetryCount() { return retryCount; }
        public long getTimeoutMillis() { return timeoutMillis; }
        public DeliveryMode getDeliveryMode() { return deliveryMode; }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private boolean persistent = false;
            private int retryCount = 3;
            private long timeoutMillis = 30000;
            private DeliveryMode deliveryMode = DeliveryMode.AT_LEAST_ONCE;

            public Builder persistent(boolean persistent) {
                this.persistent = persistent;
                return this;
            }

            public Builder retryCount(int retryCount) {
                this.retryCount = retryCount;
                return this;
            }

            public Builder timeoutMillis(long timeoutMillis) {
                this.timeoutMillis = timeoutMillis;
                return this;
            }

            public Builder deliveryMode(DeliveryMode deliveryMode) {
                this.deliveryMode = deliveryMode;
                return this;
            }

            public DeliveryOptions build() {
                return new DeliveryOptions(this);
            }
        }
    }

    /**
     * Delivery mode enumeration.
     */
    enum DeliveryMode {
        AT_MOST_ONCE,
        AT_LEAST_ONCE,
        EXACTLY_ONCE
    }

    /**
     * Routing result.
     */
    class RoutingResult {
        private final boolean success;
        private final String messageId;
        private final String error;

        public RoutingResult(boolean success, String messageId, String error) {
            this.success = success;
            this.messageId = messageId;
            this.error = error;
        }

        public boolean isSuccess() { return success; }
        public String getMessageId() { return messageId; }
        public String getError() { return error; }

        public static RoutingResult success(String messageId) {
            return new RoutingResult(true, messageId, null);
        }

        public static RoutingResult failure(String error) {
            return new RoutingResult(false, null, error);
        }
    }
}
