package com.ghatana.platform.http.client;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;

/**
 * HTTP client configuration value object.
 * 
 * Immutable configuration for HTTP clients with connection pooling,
 * timeouts, and rate limiting settings.
 *
 * @doc.type class
 * @doc.purpose Immutable HTTP client configuration with pooling, timeouts, and rate limiting
 * @doc.layer platform
 * @doc.pattern Config
 */
public final class HttpClientConfig {
    
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration callTimeout;
    private final int maxConnections;
    private final Duration keepAliveDuration;
    private final boolean retryOnConnectionFailure;
    private final double requestsPerSecond;
    private final String userAgent;
    
    private HttpClientConfig(Builder builder) {
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
        this.callTimeout = builder.callTimeout;
        this.maxConnections = builder.maxConnections;
        this.keepAliveDuration = builder.keepAliveDuration;
        this.retryOnConnectionFailure = builder.retryOnConnectionFailure;
        this.requestsPerSecond = builder.requestsPerSecond;
        this.userAgent = builder.userAgent;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public Builder toBuilder() {
        return new Builder()
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .callTimeout(callTimeout)
                .maxConnections(maxConnections)
                .keepAliveDuration(keepAliveDuration)
                .retryOnConnectionFailure(retryOnConnectionFailure)
                .requestsPerSecond(requestsPerSecond)
                .userAgent(userAgent);
    }
    
    @NotNull
    public Duration getConnectTimeout() {
        return connectTimeout;
    }
    
    @NotNull
    public Duration getReadTimeout() {
        return readTimeout;
    }
    
    @NotNull
    public Duration getCallTimeout() {
        return callTimeout;
    }
    
    public int getMaxConnections() {
        return maxConnections;
    }
    
    @NotNull
    public Duration getKeepAliveDuration() {
        return keepAliveDuration;
    }
    
    public boolean isRetryOnConnectionFailure() {
        return retryOnConnectionFailure;
    }
    
    public double getRequestsPerSecond() {
        return requestsPerSecond;
    }
    
    @Nullable
    public String getUserAgent() {
        return userAgent;
    }
    
    public static final class Builder {
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(30);
        private Duration callTimeout = Duration.ofSeconds(30);
        private int maxConnections = 10;
        private Duration keepAliveDuration = Duration.ofMinutes(5);
        private boolean retryOnConnectionFailure = true;
        private double requestsPerSecond = 10.0;
        private String userAgent = null;
        
        private Builder() {}
        
        public Builder connectTimeout(@NotNull Duration connectTimeout) {
            this.connectTimeout = Objects.requireNonNull(connectTimeout);
            return this;
        }
        
        public Builder readTimeout(@NotNull Duration readTimeout) {
            this.readTimeout = Objects.requireNonNull(readTimeout);
            return this;
        }
        
        public Builder callTimeout(@NotNull Duration callTimeout) {
            this.callTimeout = Objects.requireNonNull(callTimeout);
            return this;
        }
        
        public Builder maxConnections(int maxConnections) {
            if (maxConnections <= 0) {
                throw new IllegalArgumentException("maxConnections must be positive");
            }
            this.maxConnections = maxConnections;
            return this;
        }
        
        public Builder keepAliveDuration(@NotNull Duration keepAliveDuration) {
            this.keepAliveDuration = Objects.requireNonNull(keepAliveDuration);
            return this;
        }
        
        public Builder retryOnConnectionFailure(boolean retry) {
            this.retryOnConnectionFailure = retry;
            return this;
        }
        
        public Builder requestsPerSecond(double rps) {
            if (rps <= 0) {
                throw new IllegalArgumentException("requestsPerSecond must be positive");
            }
            this.requestsPerSecond = rps;
            return this;
        }
        
        public Builder userAgent(@Nullable String userAgent) {
            this.userAgent = userAgent;
            return this;
        }
        
        public HttpClientConfig build() {
            return new HttpClientConfig(this);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HttpClientConfig that = (HttpClientConfig) o;
        return maxConnections == that.maxConnections &&
                retryOnConnectionFailure == that.retryOnConnectionFailure &&
                Double.compare(that.requestsPerSecond, requestsPerSecond) == 0 &&
                connectTimeout.equals(that.connectTimeout) &&
                readTimeout.equals(that.readTimeout) &&
                callTimeout.equals(that.callTimeout) &&
                keepAliveDuration.equals(that.keepAliveDuration) &&
                Objects.equals(userAgent, that.userAgent);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(connectTimeout, readTimeout, callTimeout, maxConnections,
                keepAliveDuration, retryOnConnectionFailure, requestsPerSecond, userAgent);
    }
    
    @Override
    public String toString() {
        return "HttpClientConfig{" +
                "connectTimeout=" + connectTimeout +
                ", readTimeout=" + readTimeout +
                ", callTimeout=" + callTimeout +
                ", maxConnections=" + maxConnections +
                ", keepAliveDuration=" + keepAliveDuration +
                ", retryOnConnectionFailure=" + retryOnConnectionFailure +
                ", requestsPerSecond=" + requestsPerSecond +
                ", userAgent='" + userAgent + '\'' +
                '}';
    }
}
