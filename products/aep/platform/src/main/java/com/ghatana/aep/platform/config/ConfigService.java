package com.ghatana.aep.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration service for managing application configuration.
 * 
 * <p>Provides centralized configuration management with support for:
 * <ul>
 *   <li>Property loading from multiple sources</li>
 *   <li>Environment variable overrides</li>
 *   <li>Type-safe property access</li>
 *   <li>Configuration validation</li>
 *   <li>Hot reload support</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose Configuration management service
 * @doc.layer platform
 */
public class ConfigService {
    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);
    
    private final Map<String, String> properties = new ConcurrentHashMap<>();
    private final List<ConfigSource> sources = new ArrayList<>();
    private final Map<String, ConfigValidator> validators = new ConcurrentHashMap<>();
    
    /**
     * Creates a new ConfigService instance.
     */
    public ConfigService() {
        log.info("ConfigService initialized");
    }
    
    /**
     * Adds a configuration source.
     * 
     * @param source configuration source to add
     */
    public void addSource(ConfigSource source) {
        Objects.requireNonNull(source, "Configuration source cannot be null");
        sources.add(source);
        log.info("Added configuration source: {}", source.getName());
    }
    
    /**
     * Loads configuration from all sources.
     */
    public void load() {
        log.info("Loading configuration from {} sources", sources.size());
        
        for (ConfigSource source : sources) {
            try {
                Map<String, String> sourceProps = source.load();
                properties.putAll(sourceProps);
                log.debug("Loaded {} properties from {}", sourceProps.size(), source.getName());
            } catch (Exception e) {
                log.error("Failed to load configuration from {}: {}", source.getName(), e.getMessage());
                throw new ConfigurationException("Failed to load configuration from " + source.getName(), e);
            }
        }
        
        // Override with environment variables
        loadEnvironmentVariables();
        
        // Validate configuration
        validate();
        
        log.info("Configuration loaded successfully: {} properties", properties.size());
    }
    
    /**
     * Gets a string property value.
     * 
     * @param key property key
     * @return property value or null if not found
     */
    public String getString(String key) {
        return properties.get(key);
    }
    
    /**
     * Gets a string property value with default.
     * 
     * @param key property key
     * @param defaultValue default value if property not found
     * @return property value or default
     */
    public String getString(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }
    
    /**
     * Gets an integer property value.
     * 
     * @param key property key
     * @return property value
     * @throws ConfigurationException if property not found or invalid
     */
    public int getInt(String key) {
        String value = getString(key);
        if (value == null) {
            throw new ConfigurationException("Property not found: " + key);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Invalid integer value for property " + key + ": " + value, e);
        }
    }
    
    /**
     * Gets an integer property value with default.
     * 
     * @param key property key
     * @param defaultValue default value if property not found
     * @return property value or default
     */
    public int getInt(String key, int defaultValue) {
        String value = getString(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value for property {}: {}, using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * Gets a boolean property value.
     * 
     * @param key property key
     * @return property value
     * @throws ConfigurationException if property not found
     */
    public boolean getBoolean(String key) {
        String value = getString(key);
        if (value == null) {
            throw new ConfigurationException("Property not found: " + key);
        }
        return Boolean.parseBoolean(value);
    }
    
    /**
     * Gets a boolean property value with default.
     * 
     * @param key property key
     * @param defaultValue default value if property not found
     * @return property value or default
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = getString(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
    
    /**
     * Gets a long property value.
     * 
     * @param key property key
     * @return property value
     * @throws ConfigurationException if property not found or invalid
     */
    public long getLong(String key) {
        String value = getString(key);
        if (value == null) {
            throw new ConfigurationException("Property not found: " + key);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Invalid long value for property " + key + ": " + value, e);
        }
    }
    
    /**
     * Gets a long property value with default.
     * 
     * @param key property key
     * @param defaultValue default value if property not found
     * @return property value or default
     */
    public long getLong(String key, long defaultValue) {
        String value = getString(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid long value for property {}: {}, using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * Gets a double property value.
     * 
     * @param key property key
     * @return property value
     * @throws ConfigurationException if property not found or invalid
     */
    public double getDouble(String key) {
        String value = getString(key);
        if (value == null) {
            throw new ConfigurationException("Property not found: " + key);
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Invalid double value for property " + key + ": " + value, e);
        }
    }
    
    /**
     * Gets a double property value with default.
     * 
     * @param key property key
     * @param defaultValue default value if property not found
     * @return property value or default
     */
    public double getDouble(String key, double defaultValue) {
        String value = getString(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid double value for property {}: {}, using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * Sets a property value.
     * 
     * @param key property key
     * @param value property value
     */
    public void setProperty(String key, String value) {
        Objects.requireNonNull(key, "Property key cannot be null");
        properties.put(key, value);
        log.debug("Set property: {} = {}", key, value);
    }
    
    /**
     * Checks if a property exists.
     * 
     * @param key property key
     * @return true if property exists
     */
    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }
    
    /**
     * Gets all property keys.
     * 
     * @return set of all property keys
     */
    public Set<String> getKeys() {
        return new HashSet<>(properties.keySet());
    }
    
    /**
     * Gets all properties with a given prefix.
     * 
     * @param prefix property key prefix
     * @return map of matching properties
     */
    public Map<String, String> getPropertiesWithPrefix(String prefix) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
    
    /**
     * Adds a configuration validator.
     * 
     * @param key property key to validate
     * @param validator validator function
     */
    public void addValidator(String key, ConfigValidator validator) {
        validators.put(key, validator);
        log.debug("Added validator for property: {}", key);
    }
    
    /**
     * Reloads configuration from all sources.
     */
    public void reload() {
        log.info("Reloading configuration");
        properties.clear();
        load();
    }
    
    /**
     * Clears all configuration.
     */
    public void clear() {
        properties.clear();
        sources.clear();
        validators.clear();
        log.info("Configuration cleared");
    }
    
    private void loadEnvironmentVariables() {
        Map<String, String> env = System.getenv();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            String key = entry.getKey().toLowerCase().replace('_', '.');
            properties.put(key, entry.getValue());
        }
        log.debug("Loaded {} environment variables", env.size());
    }
    
    private void validate() {
        log.debug("Validating configuration with {} validators", validators.size());
        
        for (Map.Entry<String, ConfigValidator> entry : validators.entrySet()) {
            String key = entry.getKey();
            ConfigValidator validator = entry.getValue();
            String value = properties.get(key);
            
            if (!validator.validate(value)) {
                throw new ConfigurationException(
                    "Configuration validation failed for property: " + key + 
                    " with value: " + value
                );
            }
        }
        
        log.debug("Configuration validation passed");
    }
    
    /**
     * Configuration source interface.
     */
    public interface ConfigSource {
        String getName();
        Map<String, String> load() throws IOException;
    }
    
    /**
     * Configuration validator interface.
     */
    @FunctionalInterface
    public interface ConfigValidator {
        boolean validate(String value);
    }
    
    /**
     * Properties file configuration source.
     */
    public static class PropertiesFileSource implements ConfigSource {
        private final String resourcePath;
        
        public PropertiesFileSource(String resourcePath) {
            this.resourcePath = resourcePath;
        }
        
        @Override
        public String getName() {
            return "PropertiesFile:" + resourcePath;
        }
        
        @Override
        public Map<String, String> load() throws IOException {
            Properties props = new Properties();
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    throw new IOException("Resource not found: " + resourcePath);
                }
                props.load(is);
            }
            
            Map<String, String> result = new HashMap<>();
            for (String key : props.stringPropertyNames()) {
                result.put(key, props.getProperty(key));
            }
            return result;
        }
    }
    
    /**
     * Map-based configuration source.
     */
    public static class MapSource implements ConfigSource {
        private final String name;
        private final Map<String, String> properties;
        
        public MapSource(String name, Map<String, String> properties) {
            this.name = name;
            this.properties = properties;
        }
        
        @Override
        public String getName() {
            return name;
        }
        
        @Override
        public Map<String, String> load() {
            return new HashMap<>(properties);
        }
    }
    
    /**
     * Configuration exception.
     */
    public static class ConfigurationException extends RuntimeException {
        public ConfigurationException(String message) {
            super(message);
        }
        
        public ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
