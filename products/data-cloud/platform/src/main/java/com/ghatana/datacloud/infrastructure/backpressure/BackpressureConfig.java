package com.ghatana.datacloud.infrastructure.backpressure;

import java.util.Objects;

/**
 * Configuration for backpressure management.
 * 
 * @doc.type config
 * @doc.purpose Backpressure tuning parameters
 * @doc.layer infrastructure
 */
public class BackpressureConfig {
    private final int maxQueueSize;
    private final double highWatermark;
    private final double mediumWatermark;
    private final double lowWatermark;
    private final int initialRateLimit;
    private final int minRateLimit;
    private final int maxRateLimit;
    private final long adjustmentIntervalMs;
    private final long offerTimeoutMs;
    
    private BackpressureConfig(Builder builder) {
        this.maxQueueSize = builder.maxQueueSize;
        this.highWatermark = builder.highWatermark;
        this.mediumWatermark = builder.mediumWatermark;
        this.lowWatermark = builder.lowWatermark;
        this.initialRateLimit = builder.initialRateLimit;
        this.minRateLimit = builder.minRateLimit;
        this.maxRateLimit = builder.maxRateLimit;
        this.adjustmentIntervalMs = builder.adjustmentIntervalMs;
        this.offerTimeoutMs = builder.offerTimeoutMs;
    }
    
    public int getMaxQueueSize() {
        return maxQueueSize;
    }
    
    public double getHighWatermark() {
        return highWatermark;
    }
    
    public double getMediumWatermark() {
        return mediumWatermark;
    }
    
    public double getLowWatermark() {
        return lowWatermark;
    }
    
    public int getInitialRateLimit() {
        return initialRateLimit;
    }
    
    public int getMinRateLimit() {
        return minRateLimit;
    }
    
    public int getMaxRateLimit() {
        return maxRateLimit;
    }
    
    public long getAdjustmentIntervalMs() {
        return adjustmentIntervalMs;
    }
    
    public long getOfferTimeoutMs() {
        return offerTimeoutMs;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private int maxQueueSize = 10000;
        private double highWatermark = 0.9;
        private double mediumWatermark = 0.7;
        private double lowWatermark = 0.3;
        private int initialRateLimit = 1000;
        private int minRateLimit = 100;
        private int maxRateLimit = 10000;
        private long adjustmentIntervalMs = 5000;
        private long offerTimeoutMs = 5000;
        
        public Builder maxQueueSize(int size) {
            this.maxQueueSize = size;
            return this;
        }
        
        public Builder highWatermark(double watermark) {
            this.highWatermark = watermark;
            return this;
        }
        
        public Builder mediumWatermark(double watermark) {
            this.mediumWatermark = watermark;
            return this;
        }
        
        public Builder lowWatermark(double watermark) {
            this.lowWatermark = watermark;
            return this;
        }
        
        public Builder initialRateLimit(int limit) {
            this.initialRateLimit = limit;
            return this;
        }
        
        public Builder minRateLimit(int limit) {
            this.minRateLimit = limit;
            return this;
        }
        
        public Builder maxRateLimit(int limit) {
            this.maxRateLimit = limit;
            return this;
        }
        
        public Builder adjustmentIntervalMs(long interval) {
            this.adjustmentIntervalMs = interval;
            return this;
        }
        
        public Builder offerTimeoutMs(long timeout) {
            this.offerTimeoutMs = timeout;
            return this;
        }
        
        public BackpressureConfig build() {
            return new BackpressureConfig(this);
        }
    }
}
