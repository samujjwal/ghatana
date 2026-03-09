package com.ghatana.agent.framework.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Metadata about an OutputGenerator.
 * Used for observability, debugging, configuration validation, and cost tracking.
 * 
 * <p>Instances are immutable and thread-safe.
 * 
 * @doc.type class
 * @doc.purpose Generator metadata for observability
 * @doc.layer framework
 * @doc.pattern Value Object
 */
public final class GeneratorMetadata {
    
    private final String name;
    private final String type;
    private final String description;
    private final String version;
    private final Map<String, Object> properties;
    
    private GeneratorMetadata(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "name cannot be null");
        this.type = Objects.requireNonNull(builder.type, "type cannot be null");
        this.description = builder.description != null ? builder.description : "";
        this.version = builder.version != null ? builder.version : "1.0.0";
        this.properties = builder.properties != null 
            ? Collections.unmodifiableMap(builder.properties) 
            : Collections.emptyMap();
    }
    
    /**
     * Gets the generator name (e.g., "RuleBasedGenerator", "GPT4Generator").
     * @return Generator name (never null)
     */
    @NotNull
    public String getName() {
        return name;
    }
    
    /**
     * Gets the generator type category.
     * Standard types: rule, llm, template, service, script, pipeline, conditional.
     * @return Generator type (never null)
     */
    @NotNull
    public String getType() {
        return type;
    }
    
    /**
     * Gets human-readable description of what this generator does.
     * @return Description (never null, may be empty)
     */
    @NotNull
    public String getDescription() {
        return description;
    }
    
    /**
     * Gets the generator version.
     * @return Version string (never null)
     */
    @NotNull
    public String getVersion() {
        return version;
    }
    
    /**
     * Gets additional properties specific to this generator type.
     * Examples: model name for LLM, rule set name for rules, etc.
     * @return Immutable properties map (never null)
     */
    @NotNull
    public Map<String, Object> getProperties() {
        return properties;
    }
    
    /**
     * Gets a specific property value.
     * @param key Property key
     * @return Property value, or null if not present
     */
    @Nullable
    public Object getProperty(@NotNull String key) {
        return properties.get(key);
    }
    
    /**
     * Creates a new builder for GeneratorMetadata.
     * @return New builder instance
     */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GeneratorMetadata that = (GeneratorMetadata) o;
        return Objects.equals(name, that.name) &&
               Objects.equals(type, that.type) &&
               Objects.equals(version, that.version);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(name, type, version);
    }
    
    @Override
    public String toString() {
        return String.format("GeneratorMetadata{name='%s', type='%s', version='%s'}", 
            name, type, version);
    }
    
    /**
     * Builder for GeneratorMetadata.
     */
    public static final class Builder {
        private String name;
        private String type;
        private String description;
        private String version;
        private Map<String, Object> properties;
        
        private Builder() {}
        
        /**
         * Sets the generator name.
         * @param name Generator name (required)
         * @return This builder
         */
        @NotNull
        public Builder name(@NotNull String name) {
            this.name = name;
            return this;
        }
        
        /**
         * Sets the generator type.
         * @param type Generator type (required)
         * @return This builder
         */
        @NotNull
        public Builder type(@NotNull String type) {
            this.type = type;
            return this;
        }
        
        /**
         * Sets the description.
         * @param description Human-readable description
         * @return This builder
         */
        @NotNull
        public Builder description(@NotNull String description) {
            this.description = description;
            return this;
        }
        
        /**
         * Sets the version.
         * @param version Version string
         * @return This builder
         */
        @NotNull
        public Builder version(@NotNull String version) {
            this.version = version;
            return this;
        }
        
        /**
         * Sets additional properties.
         * @param properties Properties map
         * @return This builder
         */
        @NotNull
        public Builder properties(@NotNull Map<String, Object> properties) {
            this.properties = properties;
            return this;
        }
        
        /**
         * Adds a single property.
         * @param key Property key
         * @param value Property value
         * @return This builder
         */
        @NotNull
        public Builder property(@NotNull String key, @NotNull Object value) {
            if (this.properties == null) {
                this.properties = new java.util.HashMap<>();
            }
            this.properties.put(key, value);
            return this;
        }
        
        /**
         * Builds the GeneratorMetadata instance.
         * @return New GeneratorMetadata
         * @throws NullPointerException if required fields are null
         */
        @NotNull
        public GeneratorMetadata build() {
            return new GeneratorMetadata(this);
        }
    }
}
