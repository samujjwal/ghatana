package com.ghatana.core.domain.pipeline;

import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

/**
 * Connector specification for pipeline stages.
 * 
 * <p>Defines a connector that connects pipeline stages to external systems
 * or data sources with configuration and type information.
 * 
 * @doc.type class
 * @doc.purpose Connector specification for pipeline stages
 * @doc.layer core
 */
public class ConnectorSpec {
    private final String id;
    private final String type;
    private final String name;
    private final String description;
    private final ConnectorType connectorType;
    private final Map<String, Object> configuration;
    private final boolean enabled;
    
    /**
     * Creates a new connector specification.
     * 
     * @param id Connector identifier
     * @param type Connector type
     * @param name Connector name
     * @param description Connector description
     * @param connectorType Connector type enum
     * @param configuration Connector configuration
     * @param enabled Whether connector is enabled
     */
    public ConnectorSpec(String id, String type, String name, String description,
                        ConnectorType connectorType, Map<String, Object> configuration, boolean enabled) {
        this.id = Objects.requireNonNull(id, "Connector ID cannot be null");
        this.type = Objects.requireNonNull(type, "Connector type cannot be null");
        this.name = name;
        this.description = description;
        this.connectorType = Objects.requireNonNull(connectorType, "Connector type enum cannot be null");
        this.configuration = configuration != null ? new HashMap<>(configuration) : new HashMap<>();
        this.enabled = enabled;
    }
    
    /**
     * Gets the connector identifier.
     * 
     * @return connector ID
     */
    public String getId() {
        return id;
    }
    
    /**
     * Gets the connector type.
     * 
     * @return connector type
     */
    public String getType() {
        return type;
    }
    
    /**
     * Gets the connector name.
     * 
     * @return connector name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Gets the connector description.
     * 
     * @return connector description
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * Gets the connector type enum.
     * 
     * @return connector type enum
     */
    public ConnectorType getConnectorType() {
        return connectorType;
    }
    
    /**
     * Gets the connector configuration.
     * 
     * @return connector configuration
     */
    public Map<String, Object> getConfiguration() {
        return new HashMap<>(configuration);
    }
    
    /**
     * Checks if the connector is enabled.
     * 
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Gets a configuration value.
     * 
     * @param key configuration key
     * @return configuration value or null
     */
    public Object getConfigurationValue(String key) {
        return configuration.get(key);
    }
    
    /**
     * Sets a configuration value.
     * 
     * @param key configuration key
     * @param value configuration value
     */
    public void setConfigurationValue(String key, Object value) {
        Objects.requireNonNull(key, "Configuration key cannot be null");
        if (value != null) {
            configuration.put(key, value);
        } else {
            configuration.remove(key);
        }
    }
    
    /**
     * Checks if a configuration key exists.
     * 
     * @param key configuration key
     * @return true if key exists
     */
    public boolean hasConfigurationValue(String key) {
        return configuration.containsKey(key);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConnectorSpec that = (ConnectorSpec) o;
        return id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return String.format("ConnectorSpec{id='%s', type='%s', name='%s', connectorType=%s, enabled=%s}",
            id, type, name, connectorType, enabled);
    }
}
