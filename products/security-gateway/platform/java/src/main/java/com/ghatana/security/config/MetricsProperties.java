package com.ghatana.security.config;

import io.activej.config.Config;
import io.activej.config.converter.ConfigConverters;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration properties for metrics collection and reporting.
 * 
 * <p>This class holds configuration options for various metrics-related settings
 * including enabled metrics, percentiles, and distribution statistics.</p>
 * 
 * <p>Example configuration (in application.yaml or application.properties):</p>
 * <pre>
 * security:
 *   metrics:
 *     enabled: true
 *     enable-jvm: true
 *     enable-system: true
 *     enable-http: true
 *     percentiles: [0.5, 0.95, 0.99]
 *     sla-http-durations: 1s,5s,10s
 *     distribution:
 *       percentiles-histogram: true
 *       minimum-expected-value: 1ms
 *       maximum-expected-value: 10s
 *       expiry: 5m
 *       buffer-length: 3
 * </pre>
 
 *
 * @doc.type class
 * @doc.purpose Metrics properties
 * @doc.layer core
 * @doc.pattern Component
*/
public class MetricsProperties {
    private final boolean enabled;
    private final boolean enableJvm;
    private final boolean enableSystem;
    private final boolean enableHttp;
    private final double[] percentiles;
    private final Duration[] slaHttpDurations;
    private final DistributionProperties distribution;
    private final Map<String, String> tags;

    /**
     * Creates a new MetricsProperties instance from a Config object.
     * 
     * @param config The configuration source
     * @throws NullPointerException if config is null
     */
    public MetricsProperties(Config config) {
        Objects.requireNonNull(config, "Config cannot be null");
        
        this.enabled = Boolean.parseBoolean(config.get("enabled", "true"));
        this.enableJvm = Boolean.parseBoolean(config.get("enable-jvm", "true"));
        this.enableSystem = Boolean.parseBoolean(config.get("enable-system", "true"));
        this.enableHttp = Boolean.parseBoolean(config.get("enable-http", "true"));
        
        // Parse percentiles (e.g., "0.5,0.95,0.99")
        String[] percentilesStr = config.get("percentiles", "0.5,0.95,0.99").split(",");
        this.percentiles = new double[percentilesStr.length];
        for (int i = 0; i < percentilesStr.length; i++) {
            this.percentiles[i] = Double.parseDouble(percentilesStr[i].trim());
        }
        
        // Parse SLA durations (e.g., "1s,5s,10s")
        String[] slaDurations = config.get("sla-http-durations", "1s,5s,10s").split(",");
        this.slaHttpDurations = new Duration[slaDurations.length];
        for (int i = 0; i < slaDurations.length; i++) {
            this.slaHttpDurations[i] = DistributionProperties.parseDuration(slaDurations[i].trim());
        }
        
        this.distribution = new DistributionProperties(config.getChild("distribution"));
        
        // Parse tags (key1:value1,key2:value2)
        this.tags = new HashMap<>();
        String tagsStr = config.get("tags", "");
        if (!tagsStr.isEmpty()) {
            String[] tagPairs = tagsStr.split(",");
            for (String pair : tagPairs) {
                String[] keyValue = pair.split(":");
                if (keyValue.length == 2) {
                    tags.put(keyValue[0].trim(), keyValue[1].trim());
                }
            }
        }
    }
    
    /**
     * Checks if metrics collection is enabled.
     * 
     * @return true if metrics are enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Checks if JVM metrics collection is enabled.
     * 
     * @return true if JVM metrics are enabled, false otherwise
     */
    public boolean isEnableJvm() {
        return enableJvm;
    }
    
    /**
     * Checks if system metrics collection is enabled.
     * 
     * @return true if system metrics are enabled, false otherwise
     */
    public boolean isEnableSystem() {
        return enableSystem;
    }
    
    /**
     * Checks if HTTP metrics collection is enabled.
     * 
     * @return true if HTTP metrics are enabled, false otherwise
     */
    public boolean isEnableHttp() {
        return enableHttp;
    }
    
    /**
     * Gets the percentiles to calculate for timer and distribution metrics.
     * 
     * @return Array of percentiles (e.g., [0.5, 0.95, 0.99])
     */
    public double[] getPercentiles() {
        return percentiles;
    }
    
    /**
     * Gets the SLA boundaries for HTTP request durations.
     * 
     * @return Array of duration boundaries
     */
    public Duration[] getSlaHttpDurations() {
        return slaHttpDurations;
    }
    
    /**
     * Gets the distribution statistics configuration.
     * 
     * @return The distribution properties
     */
    public DistributionProperties getDistribution() {
        return distribution;
    }
    
    /**
     * Gets the common tags to apply to all metrics.
     * 
     * @return Map of tag names to values
     */
    public Map<String, String> getTags() {
        return tags;
    }
    
    /**
     * Creates a MeterFilter for HTTP request metrics with the configured SLA boundaries.
     * 
     * @return A configured MeterFilter instance
     */
    public MeterFilter httpSlaFilter() {
        return new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                if (id.getName().startsWith("http.server.requests")) {
                    double[] slaSeconds = Arrays.stream(slaHttpDurations)
                        .mapToDouble(d -> d.toMillis() / 1000.0)
                        .toArray();
                    return DistributionStatisticConfig.builder()
                            .percentiles(percentiles)
                            .serviceLevelObjectives(slaSeconds)
                            .minimumExpectedValue(distribution.getMinimumExpectedValue().toMillis() / 1000.0)
                            .maximumExpectedValue(distribution.getMaximumExpectedValue().toMillis() / 1000.0)
                            .build()
                            .merge(config);
                }
                return config;
            }
        };
    }
    
    @Override
    public String toString() {
        return "MetricsProperties{" +
                "enabled=" + enabled +
                ", enableJvm=" + enableJvm +
                ", enableSystem=" + enableSystem +
                ", enableHttp=" + enableHttp +
                ", percentiles=" + java.util.Arrays.toString(percentiles) +
                ", slaHttpDurations=" + java.util.Arrays.toString(slaHttpDurations) +
                ", distribution=" + distribution +
                ", tags=" + tags +
                '}';
    }
    
    /**
     * Configuration for distribution statistics.
     */
    public static class DistributionProperties {
        private final boolean percentilesHistogram;
        private final Duration minimumExpectedValue;
        private final Duration maximumExpectedValue;
        private final Duration expiry;
        private final int bufferLength;
        
        public DistributionProperties(Config config) {
            this.percentilesHistogram = Boolean.parseBoolean(config.get("percentiles-histogram", "true"));
            this.minimumExpectedValue = parseDuration(config.get("minimum-expected-value", "1ms"));
            this.maximumExpectedValue = parseDuration(config.get("maximum-expected-value", "10s"));
            this.expiry = parseDuration(config.get("expiry", "5m"));
            this.bufferLength = Integer.parseInt(config.get("buffer-length", "3"));
        }
        
        public boolean isPercentilesHistogram() {
            return percentilesHistogram;
        }
        
        public Duration getMinimumExpectedValue() {
            return minimumExpectedValue;
        }
        
        public Duration getMaximumExpectedValue() {
            return maximumExpectedValue;
        }
        
        public Duration getExpiry() {
            return expiry;
        }
        
        public int getBufferLength() {
            return bufferLength;
        }
        
        /**
         * Creates a DistributionStatisticConfig with the current settings.
         * 
         * @return A configured DistributionStatisticConfig
         */
        public DistributionStatisticConfig toDistributionStatisticConfig() {
            return DistributionStatisticConfig.builder()
                    .percentilesHistogram(percentilesHistogram)
                    .minimumExpectedValue((double) minimumExpectedValue.toNanos())
                    .maximumExpectedValue((double) maximumExpectedValue.toNanos())
                    .expiry(expiry)
                    .bufferLength(bufferLength)
                    .build();
        }
        
        @Override
        public String toString() {
            return "DistributionProperties{" +
                    "percentilesHistogram=" + percentilesHistogram +
                    ", minimumExpectedValue=" + minimumExpectedValue +
                    ", maximumExpectedValue=" + maximumExpectedValue +
                    ", expiry=" + expiry +
                    ", bufferLength=" + bufferLength +
                    '}';
        }
        
        public static DistributionProperties fromConfig(Config config) {
            return new DistributionProperties(config);
        }
        
        private static Duration parseDuration(String duration) {
            try {
                return Duration.parse(duration);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid duration format: " + duration, e);
            }
        }
    }
    
    public static MetricsProperties fromConfig(Config config) {
        Objects.requireNonNull(config, "Config cannot be null");
        
        return new MetricsProperties(
            config,
            config.get(ConfigConverters.ofBoolean(), "enabled", true),
            config.get(ConfigConverters.ofBoolean(), "enable-jvm", true),
            config.get(ConfigConverters.ofBoolean(), "enable-system", true),
            config.get(ConfigConverters.ofBoolean(), "enable-http", true),
            parsePercentiles(config.get("percentiles", "0.5,0.95,0.99")),
            parseSlaDurations(config.get("sla-http-durations", "1s,5s,10s")),
            DistributionProperties.fromConfig(config.getChild("distribution")),
            parseTags(config.getChild("tags"))
        );
    }
    
    private static double[] parsePercentiles(String percentilesStr) {
        String[] percentilesStrArray = percentilesStr.split(",");
        double[] percentiles = new double[percentilesStrArray.length];
        for (int i = 0; i < percentilesStrArray.length; i++) {
            percentiles[i] = Double.parseDouble(percentilesStrArray[i].trim());
        }
        return percentiles;
    }
    
    private static Duration[] parseSlaDurations(String slaDurationsStr) {
        String[] slaDurationsStrArray = slaDurationsStr.split(",");
        Duration[] slaDurations = new Duration[slaDurationsStrArray.length];
        for (int i = 0; i < slaDurationsStrArray.length; i++) {
            slaDurations[i] = DistributionProperties.parseDuration(slaDurationsStrArray[i].trim());
        }
        return slaDurations;
    }
    
    private static Map<String, String> parseTags(Config tagsConfig) {
        Map<String, String> tags = new HashMap<>();
        String tagsStr = tagsConfig.get("tags", "");
        if (!tagsStr.isEmpty()) {
            String[] tagPairs = tagsStr.split(",");
            for (String pair : tagPairs) {
                String[] keyValue = pair.split(":");
                if (keyValue.length == 2) {
                    tags.put(keyValue[0].trim(), keyValue[1].trim());
                }
            }
        }
        return tags;
    }
    
    public MetricsProperties(Config config, boolean enabled, boolean enableJvm, boolean enableSystem, boolean enableHttp, double[] percentiles, Duration[] slaHttpDurations, DistributionProperties distribution, Map<String, String> tags) {
        this.enabled = enabled;
        this.enableJvm = enableJvm;
        this.enableSystem = enableSystem;
        this.enableHttp = enableHttp;
        this.percentiles = percentiles;
        this.slaHttpDurations = slaHttpDurations;
        this.distribution = distribution;
        this.tags = tags;
    }
}
