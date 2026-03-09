package com.ghatana.aep.connector.strategy.s3;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for AWS S3 storage strategy.
 * 
 * @doc.type class
 * @doc.purpose AWS S3 configuration
 * @doc.layer infrastructure
 * @doc.pattern Builder
 */
public class S3Config {
    
    private final String region;
    private final String bucketName;
    private final String prefix;
    private final boolean deleteAfterRead;
    private final int maxKeysPerRequest;
    private final Duration pollInterval;
    
    private S3Config(Builder builder) {
        this.region = Objects.requireNonNull(builder.region, "region required");
        this.bucketName = Objects.requireNonNull(builder.bucketName, "bucketName required");
        this.prefix = builder.prefix != null ? builder.prefix : "";
        this.deleteAfterRead = builder.deleteAfterRead;
        this.maxKeysPerRequest = builder.maxKeysPerRequest > 0 ? builder.maxKeysPerRequest : 1000;
        this.pollInterval = builder.pollInterval != null ? builder.pollInterval : Duration.ofSeconds(30);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public String getRegion() { return region; }
    public String getBucketName() { return bucketName; }
    public String getPrefix() { return prefix; }
    public boolean isDeleteAfterRead() { return deleteAfterRead; }
    public int getMaxKeysPerRequest() { return maxKeysPerRequest; }
    public Duration getPollInterval() { return pollInterval; }
    
    public static class Builder {
        private String region;
        private String bucketName;
        private String prefix;
        private boolean deleteAfterRead = false;
        private int maxKeysPerRequest = 1000;
        private Duration pollInterval = Duration.ofSeconds(30);
        
        public Builder region(String region) { this.region = region; return this; }
        public Builder bucketName(String bucketName) { this.bucketName = bucketName; return this; }
        public Builder prefix(String prefix) { this.prefix = prefix; return this; }
        public Builder deleteAfterRead(boolean deleteAfterRead) { this.deleteAfterRead = deleteAfterRead; return this; }
        public Builder maxKeysPerRequest(int maxKeysPerRequest) { this.maxKeysPerRequest = maxKeysPerRequest; return this; }
        public Builder pollInterval(Duration pollInterval) { this.pollInterval = pollInterval; return this; }
        
        public S3Config build() {
            return new S3Config(this);
        }
    }
}
