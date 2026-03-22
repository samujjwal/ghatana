package com.ghatana.agent.framework.coordination;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a message between agents.
 * 
 * @doc.type class
 * @doc.purpose Agent message representation
 * @doc.layer framework
 * @doc.pattern Value Object
 */
public final class Message {
    
    private final String id;
    private final String fromAgentId;
    private final String toAgentId;
    private final String content;
    private final MessageType type;
    private final Instant sentAt;
    private final Instant readAt;
    private final String inReplyTo;
    private final Map<String, Object> metadata;
    
    private Message(Builder builder) {
        this.id = builder.id;
        this.fromAgentId = Objects.requireNonNull(builder.fromAgentId, "fromAgentId cannot be null");
        this.toAgentId = Objects.requireNonNull(builder.toAgentId, "toAgentId cannot be null");
        this.content = Objects.requireNonNull(builder.content, "content cannot be null");
        this.type = builder.type != null ? builder.type : MessageType.INFO;
        this.sentAt = builder.sentAt != null ? builder.sentAt : Instant.now();
        this.readAt = builder.readAt;
        this.inReplyTo = builder.inReplyTo;
        this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
    }
    
    @Nullable
    public String getId() {
        return id;
    }
    
    @NotNull
    public String getFromAgentId() {
        return fromAgentId;
    }
    
    @NotNull
    public String getToAgentId() {
        return toAgentId;
    }
    
    @NotNull
    public String getContent() {
        return content;
    }
    
    @NotNull
    public MessageType getType() {
        return type;
    }
    
    @NotNull
    public Instant getSentAt() {
        return sentAt;
    }
    
    @Nullable
    public Instant getReadAt() {
        return readAt;
    }
    
    @Nullable
    public String getInReplyTo() {
        return inReplyTo;
    }
    
    @NotNull
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public boolean isRead() {
        return readAt != null;
    }
    
    @NotNull
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private String id;
        private String fromAgentId;
        private String toAgentId;
        private String content;
        private MessageType type;
        private Instant sentAt;
        private Instant readAt;
        private String inReplyTo;
        private Map<String, Object> metadata;
        
        private Builder() {}
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder from(@NotNull String fromAgentId) {
            this.fromAgentId = fromAgentId;
            return this;
        }
        
        public Builder to(@NotNull String toAgentId) {
            this.toAgentId = toAgentId;
            return this;
        }
        
        public Builder content(@NotNull String content) {
            this.content = content;
            return this;
        }
        
        public Builder type(@NotNull MessageType type) {
            this.type = type;
            return this;
        }
        
        public Builder sentAt(@NotNull Instant sentAt) {
            this.sentAt = sentAt;
            return this;
        }
        
        public Builder readAt(@Nullable Instant readAt) {
            this.readAt = readAt;
            return this;
        }
        
        public Builder inReplyTo(@Nullable String inReplyTo) {
            this.inReplyTo = inReplyTo;
            return this;
        }
        
        public Builder metadata(@NotNull Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }
        
        @NotNull
        public Message build() {
            return new Message(this);
        }
    }
    
    public enum MessageType {
        INFO,
        REQUEST,
        RESPONSE,
        ERROR,
        NOTIFICATION
    }
}
