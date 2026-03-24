package com.ghatana.yappc.plugin;

/**
 * Metadata about a plugin.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles plugin metadata operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class PluginMetadata {
    
    private final String id;
    private final String name;
    private final String version;
    private final String description;
    private final String author;
    private final String category;
    private final String minYappcVersion;
    
    private PluginMetadata(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.version = builder.version;
        this.description = builder.description;
        this.author = builder.author;
        this.category = builder.category;
        this.minYappcVersion = builder.minYappcVersion;
    }
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public String getVersion() {
        return version;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getAuthor() {
        return author;
    }
    
    public String getCategory() {
        return category;
    }
    
    public String getMinYappcVersion() {
        return minYappcVersion;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private String id;
        private String name;
        private String version = "1.0.0";
        private String description = "";
        private String author = "";
        private String category = "general";
        private String minYappcVersion = "1.0.0";
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder version(String version) {
            this.version = version;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder author(String author) {
            this.author = author;
            return this;
        }
        
        public Builder category(String category) {
            this.category = category;
            return this;
        }
        
        public Builder minYappcVersion(String minYappcVersion) {
            this.minYappcVersion = minYappcVersion;
            return this;
        }
        
        public PluginMetadata build() {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Plugin ID is required");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Plugin name is required");
            }
            return new PluginMetadata(this);
        }
    }
}
