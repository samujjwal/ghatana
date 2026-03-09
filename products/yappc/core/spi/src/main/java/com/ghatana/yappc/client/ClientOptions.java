package com.ghatana.yappc.client;

/**
 * Options for remote YAPPC client configuration.
 * 
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles client options operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class ClientOptions {
    
    private final int timeoutMs;
    private final int maxRetries;
    private final int connectionPoolSize;
    private final boolean enableCompression;
    
    private ClientOptions(Builder builder) {
        this.timeoutMs = builder.timeoutMs;
        this.maxRetries = builder.maxRetries;
        this.connectionPoolSize = builder.connectionPoolSize;
        this.enableCompression = builder.enableCompression;
    }
    
    public int getTimeoutMs() {
        return timeoutMs;
    }
    
    /**
     * Gets the timeout in milliseconds.
     * Alias for {@link #getTimeoutMs()}.
     * 
     * @return the timeout in milliseconds
     */
    public int getTimeout() {
        return timeoutMs;
    }
    
    public int getMaxRetries() {
        return maxRetries;
    }
    
    public int getConnectionPoolSize() {
        return connectionPoolSize;
    }
    
    public boolean isEnableCompression() {
        return enableCompression;
    }
    
    public static ClientOptions defaults() {
        return builder().build();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private int timeoutMs = 30000;
        private int maxRetries = 3;
        private int connectionPoolSize = 10;
        private boolean enableCompression = true;
        
        public Builder timeout(int timeoutMs) {
            if (timeoutMs <= 0) {
                throw new IllegalArgumentException("Timeout must be positive");
            }
            this.timeoutMs = timeoutMs;
            return this;
        }
        
        public Builder retries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("Retries cannot be negative");
            }
            this.maxRetries = maxRetries;
            return this;
        }
        
        public Builder connectionPoolSize(int size) {
            if (size <= 0) {
                throw new IllegalArgumentException("Connection pool size must be positive");
            }
            this.connectionPoolSize = size;
            return this;
        }
        
        public Builder enableCompression(boolean enable) {
            this.enableCompression = enable;
            return this;
        }
        
        public ClientOptions build() {
            return new ClientOptions(this);
        }
    }
}
