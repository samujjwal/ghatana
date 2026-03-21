package com.ghatana.statestore.factory;

import com.ghatana.statestore.hybrid.SyncStrategy;
import redis.clients.jedis.JedisPool;

/**
 * Configuration for state store creation.
 * 
 * Use builder pattern for clean configuration:
 * <pre>
 * StateStoreConfig config = StateStoreConfig.builder()
 *     .type(StateStoreType.HYBRID)
 *     .localType(StateStoreType.ROCKSDB)
 *     .localPath("/data/rocksdb")
 *     .centralType(StateStoreType.REDIS)
 *     .redisHost("localhost")
 *     .redisPort(6379)
 *     .syncStrategy(SyncStrategy.BATCHED)
 *     .batchSize(100)
 *     .syncIntervalMs(1000)
 *     .build();
 * </pre>
 */
public class StateStoreConfig {
    
    // Primary type
    private final StateStoreType type;
    
    // File/RocksDB configuration
    private final String path;
    
    // Redis configuration
    private final String redisHost;
    private final int redisPort;
    private final JedisPool jedisPool;
    
    // Hybrid configuration
    private final StateStoreType localType;
    private final String localPath;
    private final StateStoreType centralType;
    private final String centralPath;
    private final SyncStrategy syncStrategy;
    private final int batchSize;
    private final long syncIntervalMs;
    
    private StateStoreConfig(Builder builder) {
        this.type = builder.type;
        this.path = builder.path;
        this.redisHost = builder.redisHost;
        this.redisPort = builder.redisPort;
        this.jedisPool = builder.jedisPool;
        this.localType = builder.localType;
        this.localPath = builder.localPath;
        this.centralType = builder.centralType;
        this.centralPath = builder.centralPath;
        this.syncStrategy = builder.syncStrategy;
        this.batchSize = builder.batchSize;
        this.syncIntervalMs = builder.syncIntervalMs;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    
    public StateStoreType getType() {
        return type;
    }
    
    public String getPath() {
        return path;
    }
    
    public String getRedisHost() {
        return redisHost;
    }
    
    public int getRedisPort() {
        return redisPort;
    }
    
    public JedisPool getJedisPool() {
        return jedisPool;
    }
    
    public StateStoreType getLocalType() {
        return localType;
    }
    
    public String getLocalPath() {
        return localPath;
    }
    
    public StateStoreType getCentralType() {
        return centralType;
    }
    
    public String getCentralPath() {
        return centralPath;
    }
    
    public SyncStrategy getSyncStrategy() {
        return syncStrategy;
    }
    
    public int getBatchSize() {
        return batchSize;
    }
    
    public long getSyncIntervalMs() {
        return syncIntervalMs;
    }
    
    // Builder
    
    public static class Builder {
        private StateStoreType type;
        private String path;
        private String redisHost = "localhost";
        private int redisPort = 6379;
        private JedisPool jedisPool;
        private StateStoreType localType;
        private String localPath;
        private StateStoreType centralType;
        private String centralPath;
        private SyncStrategy syncStrategy = SyncStrategy.BATCHED;
        private int batchSize = 100;
        private long syncIntervalMs = 1000;
        
        public Builder type(StateStoreType type) {
            this.type = type;
            return this;
        }
        
        public Builder path(String path) {
            this.path = path;
            return this;
        }
        
        public Builder redisHost(String redisHost) {
            this.redisHost = redisHost;
            return this;
        }
        
        public Builder redisPort(int redisPort) {
            this.redisPort = redisPort;
            return this;
        }
        
        public Builder jedisPool(JedisPool jedisPool) {
            this.jedisPool = jedisPool;
            return this;
        }
        
        public Builder localType(StateStoreType localType) {
            this.localType = localType;
            return this;
        }
        
        public Builder localPath(String localPath) {
            this.localPath = localPath;
            return this;
        }
        
        public Builder centralType(StateStoreType centralType) {
            this.centralType = centralType;
            return this;
        }
        
        public Builder centralPath(String centralPath) {
            this.centralPath = centralPath;
            return this;
        }
        
        public Builder syncStrategy(SyncStrategy syncStrategy) {
            this.syncStrategy = syncStrategy;
            return this;
        }
        
        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }
        
        public Builder syncIntervalMs(long syncIntervalMs) {
            this.syncIntervalMs = syncIntervalMs;
            return this;
        }
        
        public StateStoreConfig build() {
            if (type == null) {
                throw new IllegalStateException("State store type is required");
            }
            return new StateStoreConfig(this);
        }
    }
}
