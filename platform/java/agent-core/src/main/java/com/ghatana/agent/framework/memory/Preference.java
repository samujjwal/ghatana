package com.ghatana.agent.framework.memory;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents preference memory - agent or user preferences.
 * 
 * <p>Preferences control agent behavior and user experience:
 * <ul>
 *   <li>UI preferences (theme, layout)</li>
 *   <li>Notification settings</li>
 *   <li>Language and locale</li>
 *   <li>Agent personality traits</li>
 *   <li>Tool preferences</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose Preference memory representation
 * @doc.layer framework
 * @doc.pattern Value Object
 * @doc.gaa.memory preference
 */
public final class Preference {
    
    private final String key;
    private final String value;
    private final String namespace;
    private final String agentId;
    private final Instant setAt;
    
    private Preference(Builder builder) {
        this.key = Objects.requireNonNull(builder.key, "key cannot be null");
        this.value = Objects.requireNonNull(builder.value, "value cannot be null");
        this.namespace = Objects.requireNonNull(builder.namespace, "namespace cannot be null");
        this.agentId = Objects.requireNonNull(builder.agentId, "agentId cannot be null");
        this.setAt = Objects.requireNonNull(builder.setAt, "setAt cannot be null");
    }
    
    @NotNull
    public String getKey() {
        return key;
    }
    
    @NotNull
    public String getValue() {
        return value;
    }
    
    @NotNull
    public String getNamespace() {
        return namespace;
    }
    
    @NotNull
    public String getAgentId() {
        return agentId;
    }
    
    @NotNull
    public Instant getSetAt() {
        return setAt;
    }
    
    @NotNull
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private String key;
        private String value;
        private String namespace;
        private String agentId;
        private Instant setAt;
        
        private Builder() {
            this.setAt = Instant.now();
        }
        
        public Builder key(@NotNull String key) {
            this.key = key;
            return this;
        }
        
        public Builder value(@NotNull String value) {
            this.value = value;
            return this;
        }
        
        public Builder namespace(@NotNull String namespace) {
            this.namespace = namespace;
            return this;
        }
        
        public Builder agentId(@NotNull String agentId) {
            this.agentId = agentId;
            return this;
        }
        
        public Builder setAt(@NotNull Instant setAt) {
            this.setAt = setAt;
            return this;
        }
        
        @NotNull
        public Preference build() {
            return new Preference(this);
        }
    }
}
