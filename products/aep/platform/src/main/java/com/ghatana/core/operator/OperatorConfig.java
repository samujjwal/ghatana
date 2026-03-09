package com.ghatana.core.operator;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Configuration for operators in the Unified Operator Model.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates operator configuration with type-safe accessors, resource limits,
 * and timeout controls. Provides immutable configuration objects passed to
 * {@link UnifiedOperator#initialize(OperatorConfig)} to configure operator behavior,
 * resource allocation, and processing parameters.
 *
 * <p><b>Architecture Role</b><br>
 * OperatorConfig is the canonical configuration mechanism for all operators
 * (Stream, Pattern, Learning). Separates configuration from operator logic to enable:
 * <ul>
 *   <li>Dynamic reconfiguration: Change config without operator restart</li>
 *   <li>Environment-specific settings: Dev/staging/production configs</li>
 *   <li>Multi-tenant isolation: Per-tenant config overrides</li>
 *   <li>Version migration: Preserve config across operator version upgrades</li>
 *   <li>Configuration validation: Fail fast on invalid config during initialize()</li>
 *   <li>Operator catalog: Store operator configs in EventCloud</li>
 * </ul>
 *
 * <p><b>Configuration Structure</b>
 * <ul>
 *   <li><b>Properties</b>: Key-value pairs (String → String) for operator-specific config</li>
 *   <li><b>Processing Timeout</b>: Max time for single event processing (default 30s)</li>
 *   <li><b>Max Batch Size</b>: Max events per batch for processBatch() (default 1000)</li>
 * </ul>
 *
 * <p><b>Supported Property Types</b>
 * <ul>
 *   <li>{@code String}: {@code getString(key)}</li>
 *   <li>{@code int}: {@code getInt(key)}</li>
 *   <li>{@code long}: {@code getLong(key)}</li>
 *   <li>{@code boolean}: {@code getBoolean(key)}</li>
 *   <li>{@code Duration}: {@code getDuration(key)} (formats: "5s", "30m", "1h", "PT5S")</li>
 * </ul>
 *
 * <p><b>Usage Examples</b>
 *
 * <p><b>Example 1: Basic operator configuration</b>
 * <pre>{@code
 * OperatorConfig config = OperatorConfig.builder()
 *     .withProperty("windowSize", "60s")
 *     .withProperty("partitionBy", "userId")
 *     .withTimeout(Duration.ofSeconds(5))
 *     .withMaxBatchSize(500)
 *     .build();
 * 
 * // Pass to operator during initialization
 * operator.initialize(config).getResult();
 * }</pre>
 *
 * <p><b>Example 2: Type-safe property access</b>
 * <pre>{@code
 * // Get duration property with default fallback
 * Duration windowSize = config.getDuration("windowSize")
 *     .orElse(Duration.ofMinutes(1));
 * 
 * // Get string property with default
 * String partitionKey = config.getString("partitionBy", "tenantId");
 * 
 * // Get int property with validation
 * int batchSize = config.getInt("batchSize")
 *     .filter(size -> size > 0 && size <= 1000)
 *     .orElseThrow(() -> new OperatorConfigurationException(
 *         "Invalid batchSize: must be 1-1000"
 *     ));
 * }</pre>
 *
 * <p><b>Example 3: Pattern operator configuration</b>
 * <pre>{@code
 * // SEQ operator with temporal constraints
 * OperatorConfig seqConfig = OperatorConfig.builder()
 *     .withProperty("pattern", "login.failed → transaction")
 *     .withProperty("withinWindow", "5m")
 *     .withProperty("maxPartialMatches", "1000")
 *     .withTimeout(Duration.ofSeconds(10))
 *     .build();
 * 
 * PatternOperator seqOp = new SequenceOperator(operatorId);
 * seqOp.initialize(seqConfig).getResult();
 * }</pre>
 *
 * <p><b>Example 4: Learning operator configuration</b>
 * <pre>{@code
 * // Frequent sequence miner (Apriori)
 * OperatorConfig aprioriConfig = OperatorConfig.builder()
 *     .withProperty("minSupport", "0.1")
 *     .withProperty("minConfidence", "0.5")
 *     .withProperty("maxSequenceLength", "5")
 *     .withTimeout(Duration.ofMinutes(5))  // Batch processing timeout
 *     .withMaxBatchSize(10000)              // Large batches for mining
 *     .build();
 * 
 * FrequentSequenceMiner miner = new FrequentSequenceMiner(operatorId);
 * miner.initialize(aprioriConfig).getResult();
 * }</pre>
 *
 * <p><b>Example 5: Environment-specific configuration</b>
 * <pre>{@code
 * // Development config (small timeouts, verbose logging)
 * OperatorConfig devConfig = OperatorConfig.builder()
 *     .withProperty("logLevel", "DEBUG")
 *     .withTimeout(Duration.ofSeconds(10))
 *     .withMaxBatchSize(100)
 *     .build();
 * 
 * // Production config (longer timeouts, optimized batch size)
 * OperatorConfig prodConfig = OperatorConfig.builder()
 *     .withProperty("logLevel", "WARN")
 *     .withTimeout(Duration.ofSeconds(30))
 *     .withMaxBatchSize(1000)
 *     .build();
 * 
 * // Select config based on environment
 * String env = System.getenv("ENV");
 * OperatorConfig config = "production".equals(env) ? prodConfig : devConfig;
 * }</pre>
 *
 * <p><b>Example 6: Validate configuration in operator</b>
 * <pre>{@code
 * public class WindowOperator extends AbstractOperator {
 *     @Override
 *     protected Promise<Void> doInitialize(OperatorConfig config) {
 *         try {
 *             // Required properties
 *             Duration windowSize = config.getDuration("windowSize")
 *                 .orElseThrow(() -> new OperatorConfigurationException(
 *                     "Missing required config: windowSize"
 *                 ));
 *             
 *             if (windowSize.isNegative() || windowSize.isZero()) {
 *                 throw new OperatorConfigurationException(
 *                     "Invalid windowSize: must be positive, got " + windowSize
 *                 );
 *             }
 *             
 *             // Optional properties with defaults
 *             Duration slideInterval = config.getDuration("slideInterval", windowSize);
 *             int maxEventsPerWindow = config.getInt("maxEventsPerWindow", 10000);
 *             
 *             // Initialize window state
 *             this.windowSize = windowSize;
 *             this.slideInterval = slideInterval;
 *             this.maxEventsPerWindow = maxEventsPerWindow;
 *             
 *             return Promise.complete();
 *         } catch (Exception e) {
 *             return Promise.ofException(new OperatorConfigurationException(
 *                 "Window operator initialization failed",
 *                 e,
 *                 getId()
 *             ));
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example 7: Load configuration from external source</b>
 * <pre>{@code
 * // Load from properties file
 * Properties props = new Properties();
 * props.load(new FileInputStream("operator.properties"));
 * 
 * OperatorConfig.Builder builder = OperatorConfig.builder();
 * props.forEach((key, value) -> 
 *     builder.withProperty(key.toString(), value.toString())
 * );
 * 
 * // Add global timeouts
 * builder.withTimeout(Duration.parse(props.getProperty("timeout", "PT30S")));
 * builder.withMaxBatchSize(Integer.parseInt(props.getProperty("batchSize", "1000")));
 * 
 * OperatorConfig config = builder.build();
 * }</pre>
 *
 * <p><b>Best Practices</b>
 * <ul>
 *   <li>Use type-safe accessors (getDuration, getInt) instead of getString + parse</li>
 *   <li>Provide sensible defaults with {@code get*(key, default)} methods</li>
 *   <li>Validate configuration early in {@code doInitialize()}</li>
 *   <li>Throw {@link OperatorConfigurationException} for invalid config</li>
 *   <li>Document required vs optional properties in operator JavaDoc</li>
 *   <li>Use builder pattern for configuration construction (not constructors)</li>
 *   <li>Keep timeout reasonable (30s default, increase for batch operators)</li>
 *   <li>Set maxBatchSize based on memory constraints (default 1000)</li>
 * </ul>
 *
 * <p><b>Anti-Patterns</b>
 * <ul>
 *   <li>❌ DON'T mutate config after build() (immutable)</li>
 *   <li>❌ DON'T store mutable objects in properties (String only)</li>
 *   <li>❌ DON'T skip validation (fail fast on invalid config)</li>
 *   <li>❌ DON'T hardcode config in operator (use OperatorConfig)</li>
 *   <li>❌ DON'T use infinite timeout (always set reasonable limits)</li>
 *   <li>❌ DON'T store sensitive data in properties (use secure config stores)</li>
 * </ul>
 *
 * <p><b>Duration Format Support</b>
 * <ul>
 *   <li>ISO-8601: {@code "PT5S"} (5 seconds), {@code "PT30M"} (30 minutes)</li>
 *   <li>Simple: {@code "5s"} (5 seconds), {@code "30m"} (30 minutes), {@code "1h"} (1 hour), {@code "2d"} (2 days)</li>
 *   <li>Parsing: {@link #parseDuration(String)} handles both formats</li>
 * </ul>
 *
 * <p><b>Performance Characteristics</b>
 * <ul>
 *   <li>Construction: O(n) where n = number of properties (HashMap copy)</li>
 *   <li>Property access: O(1) average (HashMap lookup)</li>
 *   <li>Type conversion: O(1) for primitives, O(n) for Duration parsing</li>
 *   <li>Memory: ~50 bytes overhead + n * (key + value) size</li>
 *   <li>GC pressure: Minimal (immutable, no defensive copies on read)</li>
 * </ul>
 *
 * <p><b>Integration Points</b>
 * <ul>
 *   <li>{@link UnifiedOperator#initialize(OperatorConfig)} - Operator initialization</li>
 *   <li>{@link UnifiedOperator#getConfig()} - Query current operator config</li>
 *   <li>{@link AbstractOperator} - Stores config in protected field</li>
 *   <li>{@link OperatorConfigurationException} - Thrown for invalid config</li>
 *   <li>OperatorCatalog - Store configs alongside operators in EventCloud</li>
 *   <li>PipelineBuilder - Apply configs during pipeline construction</li>
 * </ul>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable and thread-safe after {@code build()}. All fields are final and
 * defensive copies are made during construction. Safe for concurrent access
 * from multiple threads without synchronization.
 *
 * <p><b>Comparison with Other Config Types</b>
 * <ul>
 *   <li>vs {@code Map<String, String>}: Type-safe accessors, immutability, validation support</li>
 *   <li>vs {@code Properties}: Type conversion, Duration parsing, builder pattern</li>
 *   <li>vs {@code TypesafeConfig}: Simpler API, operator-specific defaults, no external dependency</li>
 * </ul>
 *
 * @see UnifiedOperator
 * @see OperatorConfigurationException
 * @see AbstractOperator
 * 
 * @doc.type class
 * @doc.purpose Configuration for operators with type-safe accessors and resource limits
 * @doc.layer core
 * @doc.pattern Builder (fluent API for construction)
 * 
 * @author Ghatana Platform Team
 * @version 2.0.0
 * @since 2025-10-25
 *
 * <h3>Example Usage:</h3>
 * <pre>{@code
 * OperatorConfig config = OperatorConfig.builder()
 *     .withProperty("windowSize", "60s")
 *     .withProperty("partitionBy", "userId")
 *     .withTimeout(Duration.ofSeconds(5))
 *     .build();
 * 
 * Duration window = config.getDuration("windowSize").orElse(Duration.ofMinutes(1));
 * String partitionKey = config.getString("partitionBy").orElse("tenantId");
 * }</pre>
 */
public final class OperatorConfig {
    
    private final Map<String, String> properties;
    private final Duration processingTimeout;
    private final int maxBatchSize;

    private OperatorConfig(Builder builder) {
        this.properties = Map.copyOf(builder.properties);
        this.processingTimeout = builder.processingTimeout;
        this.maxBatchSize = builder.maxBatchSize;
    }

    /**
     * Get all configuration properties.
     * 
     * @return unmodifiable properties map
     */
    public Map<String, String> getProperties() {
        return properties;
    }

    /**
     * Get string property by key.
     * 
     * @param key property key
     * @return optional value
     */
    public Optional<String> getString(String key) {
        return Optional.ofNullable(properties.get(key));
    }

    /**
     * Get string property or default.
     * 
     * @param key property key
     * @param defaultValue default if missing
     * @return property value or default
     */
    public String getString(String key, String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }

    /**
     * Get integer property.
     * 
     * @param key property key
     * @return optional integer value
     */
    public Optional<Integer> getInt(String key) {
        return getString(key).map(Integer::parseInt);
    }

    /**
     * Get integer property or default.
     * 
     * @param key property key
     * @param defaultValue default if missing
     * @return property value or default
     */
    public int getInt(String key, int defaultValue) {
        return getInt(key).orElse(defaultValue);
    }

    /**
     * Get long property.
     * 
     * @param key property key
     * @return optional long value
     */
    public Optional<Long> getLong(String key) {
        return getString(key).map(Long::parseLong);
    }

    /**
     * Get long property or default.
     * 
     * @param key property key
     * @param defaultValue default if missing
     * @return property value or default
     */
    public long getLong(String key, long defaultValue) {
        return getLong(key).orElse(defaultValue);
    }

    /**
     * Get boolean property.
     * 
     * @param key property key
     * @return optional boolean value
     */
    public Optional<Boolean> getBoolean(String key) {
        return getString(key).map(Boolean::parseBoolean);
    }

    /**
     * Get boolean property or default.
     * 
     * @param key property key
     * @param defaultValue default if missing
     * @return property value or default
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        return getBoolean(key).orElse(defaultValue);
    }

    /**
     * Get duration property.
     * 
     * <p>Supports formats: "5s", "30m", "1h", "PT5S" (ISO-8601)
     * 
     * @param key property key
     * @return optional duration value
     */
    public Optional<Duration> getDuration(String key) {
        return getString(key).map(OperatorConfig::parseDuration);
    }

    /**
     * Get duration property or default.
     * 
     * @param key property key
     * @param defaultValue default if missing
     * @return property value or default
     */
    public Duration getDuration(String key, Duration defaultValue) {
        return getDuration(key).orElse(defaultValue);
    }

    /**
     * Get processing timeout.
     * 
     * @return timeout duration
     */
    public Duration getProcessingTimeout() {
        return processingTimeout;
    }

    /**
     * Get max batch size.
     * 
     * @return max batch size
     */
    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    /**
     * Parse duration from string.
     * 
     * <p>Formats: "5s", "30m", "1h", "PT5S"
     * 
     * @param value duration string
     * @return parsed duration
     */
    private static Duration parseDuration(String value) {
        if (value.startsWith("PT") || value.startsWith("P")) {
            return Duration.parse(value);
        }
        
        long amount = Long.parseLong(value.replaceAll("[^0-9]", ""));
        if (value.endsWith("s")) {
            return Duration.ofSeconds(amount);
        } else if (value.endsWith("m")) {
            return Duration.ofMinutes(amount);
        } else if (value.endsWith("h")) {
            return Duration.ofHours(amount);
        } else if (value.endsWith("d")) {
            return Duration.ofDays(amount);
        }
        
        return Duration.parse(value);
    }

    /**
     * Create empty config.
     * 
     * @return empty config
     */
    public static OperatorConfig empty() {
        return builder().build();
    }

    /**
     * Create config builder.
     * 
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * OperatorConfig Builder.
     */
    public static final class Builder {
        
        private final Map<String, String> properties = new HashMap<>();
        private Duration processingTimeout = Duration.ofSeconds(30);
        private int maxBatchSize = 1000;

        private Builder() {}

        /**
         * Set property.
         * 
         * @param key property key
         * @param value property value
         * @return this builder
         */
        public Builder withProperty(String key, String value) {
            this.properties.put(
                Objects.requireNonNull(key, "key cannot be null"),
                Objects.requireNonNull(value, "value cannot be null")
            );
            return this;
        }

        /**
         * Set multiple properties.
         * 
         * @param properties properties map
         * @return this builder
         */
        public Builder withProperties(Map<String, String> properties) {
            Objects.requireNonNull(properties, "properties cannot be null");
            this.properties.putAll(properties);
            return this;
        }

        /**
         * Set processing timeout.
         * 
         * @param timeout timeout duration
         * @return this builder
         */
        public Builder withTimeout(Duration timeout) {
            this.processingTimeout = Objects.requireNonNull(timeout, "timeout cannot be null");
            return this;
        }

        /**
         * Set max batch size.
         * 
         * @param maxBatchSize max batch size
         * @return this builder
         */
        public Builder withMaxBatchSize(int maxBatchSize) {
            if (maxBatchSize <= 0) {
                throw new IllegalArgumentException("maxBatchSize must be positive");
            }
            this.maxBatchSize = maxBatchSize;
            return this;
        }

        /**
         * Build immutable OperatorConfig.
         * 
         * @return operator config
         */
        public OperatorConfig build() {
            return new OperatorConfig(this);
        }
    }

    @Override
    public String toString() {
        return String.format(
            "OperatorConfig{properties=%d, timeout=%s, maxBatch=%d}",
            properties.size(), processingTimeout, maxBatchSize
        );
    }
}
