package com.ghatana.virtualorg.framework.collaboration;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Message for inter-agent communication.
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents a message sent between agents with: - Sender and recipient
 * identification - Message type and priority - Content and metadata -
 * Conversation threading
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * AgentMessage message = AgentMessage.builder()
 *     .from("alice-agent")
 *     .to("bob-agent")
 *     .type(MessageType.REQUEST)
 *     .subject("Code Review Needed")
 *     .content("Please review PR #123")
 *     .priority(Priority.HIGH)
 *     .build();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Inter-agent message
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record AgentMessage(
        String id,
        String fromAgentId,
        String toAgentId,
        MessageType type,
        String subject,
        String content,
        Map<String, Object> payload,
        Priority priority,
        String conversationId,
        String inReplyTo,
        Instant timestamp,
        Instant expiresAt,
        MessageStatus status,
        Map<String, Object> metadata
        ) {

    /**
     * Types of inter-agent messages.
     */
    public enum MessageType {
        /**
         * Request for action or information
         */
        REQUEST,
        /**
         * Response to a request
         */
        RESPONSE,
        /**
         * Notification (no response expected)
         */
        NOTIFICATION,
        /**
         * Task delegation
         */
        DELEGATION,
        /**
         * Status update
         */
        STATUS_UPDATE,
        /**
         * Error or exception report
         */
        ERROR,
        /**
         * Acknowledgment of receipt
         */
        ACK
    }

    /**
     * Message priority levels.
     */
    public enum Priority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }

    /**
     * Message delivery status.
     */
    public enum MessageStatus {
        PENDING,
        DELIVERED,
        READ,
        PROCESSED,
        FAILED,
        EXPIRED
    }

    /**
     * Creates a builder for AgentMessage.
     *
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a reply to this message.
     *
     * @param content Reply content
     * @return Reply message builder
     */
    public Builder reply(String content) {
        return builder()
                .from(toAgentId)
                .to(fromAgentId)
                .type(MessageType.RESPONSE)
                .subject("Re: " + subject)
                .content(content)
                .conversationId(conversationId)
                .inReplyTo(id);
    }

    /**
     * Creates an acknowledgment for this message.
     *
     * @return Acknowledgment message
     */
    public AgentMessage acknowledge() {
        return builder()
                .from(toAgentId)
                .to(fromAgentId)
                .type(MessageType.ACK)
                .subject("ACK: " + subject)
                .content("Message received")
                .conversationId(conversationId)
                .inReplyTo(id)
                .build();
    }

    /**
     * Builder for creating AgentMessage instances.
     */
    public static final class Builder {

        private String id;
        private String fromAgentId;
        private String toAgentId;
        private MessageType type = MessageType.NOTIFICATION;
        private String subject;
        private String content;
        private Map<String, Object> payload;
        private Priority priority = Priority.NORMAL;
        private String conversationId;
        private String inReplyTo;
        private Instant timestamp;
        private Instant expiresAt;
        private MessageStatus status = MessageStatus.PENDING;
        private Map<String, Object> metadata;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder from(String fromAgentId) {
            this.fromAgentId = fromAgentId;
            return this;
        }

        public Builder to(String toAgentId) {
            this.toAgentId = toAgentId;
            return this;
        }

        public Builder type(MessageType type) {
            this.type = type;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder payload(Map<String, Object> payload) {
            this.payload = payload;
            return this;
        }

        public Builder priority(Priority priority) {
            this.priority = priority;
            return this;
        }

        public Builder conversationId(String conversationId) {
            this.conversationId = conversationId;
            return this;
        }

        public Builder inReplyTo(String inReplyTo) {
            this.inReplyTo = inReplyTo;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder ttl(java.time.Duration ttl) {
            this.expiresAt = Instant.now().plus(ttl);
            return this;
        }

        public Builder status(MessageStatus status) {
            this.status = status;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Convenience method for request messages.
         */
        public Builder request(String toAgent, String subject, String content) {
            return to(toAgent)
                    .type(MessageType.REQUEST)
                    .subject(subject)
                    .content(content);
        }

        /**
         * Convenience method for delegation messages.
         */
        public Builder delegate(String toAgent, String task, Map<String, Object> taskPayload) {
            return to(toAgent)
                    .type(MessageType.DELEGATION)
                    .subject("Task Delegation: " + task)
                    .payload(taskPayload)
                    .priority(Priority.HIGH);
        }

        public AgentMessage build() {
            return new AgentMessage(
                    id != null ? id : UUID.randomUUID().toString(),
                    fromAgentId,
                    toAgentId,
                    type,
                    subject,
                    content,
                    payload != null ? Map.copyOf(payload) : Map.of(),
                    priority,
                    conversationId != null ? conversationId : UUID.randomUUID().toString(),
                    inReplyTo,
                    timestamp != null ? timestamp : Instant.now(),
                    expiresAt,
                    status,
                    metadata != null ? Map.copyOf(metadata) : Map.of()
            );
        }
    }
}
