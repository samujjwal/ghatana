package com.ghatana.yappc.domain.run;

import java.util.Set;

/**
 * @doc.type record
 * @doc.purpose Configuration for observation during run
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public record ObservationConfig(
    boolean enableMetrics,
    boolean enableLogs,
    boolean enableTraces,
    Set<String> metricTags,
    int samplingRate
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static ObservationConfig defaultConfig() {
        return new ObservationConfig(true, true, true, Set.of(), 100);
    }
    
    public static class Builder {
        private boolean enableMetrics = true;
        private boolean enableLogs = true;
        private boolean enableTraces = true;
        private Set<String> metricTags = Set.of();
        private int samplingRate = 100;
        
        public Builder enableMetrics(boolean enableMetrics) {
            this.enableMetrics = enableMetrics;
            return this;
        }
        
        public Builder enableLogs(boolean enableLogs) {
            this.enableLogs = enableLogs;
            return this;
        }
        
        public Builder enableTraces(boolean enableTraces) {
            this.enableTraces = enableTraces;
            return this;
        }
        
        public Builder metricTags(Set<String> metricTags) {
            this.metricTags = metricTags;
            return this;
        }
        
        public Builder samplingRate(int samplingRate) {
            this.samplingRate = samplingRate;
            return this;
        }
        
        public ObservationConfig build() {
            return new ObservationConfig(enableMetrics, enableLogs, enableTraces, metricTags, samplingRate);
        }
    }
}
