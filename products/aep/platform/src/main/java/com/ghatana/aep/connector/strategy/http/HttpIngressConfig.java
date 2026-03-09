package com.ghatana.aep.connector.strategy.http;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for HTTP ingress strategy.
 * 
 * @doc.type class
 * @doc.purpose HTTP ingress configuration
 * @doc.layer infrastructure
 * @doc.pattern Builder
 */
public class HttpIngressConfig {
    
    private final String endpoint;
    private final HttpIngressStrategy.AuthType authType;
    private final Map<String, String> authHeaders;
    private final Duration timeout;
    private final int maxRetries;
    private final Duration retryBackoff;
    private final boolean validateSsl;
    private final String contentType;
    
    private HttpIngressConfig(Builder builder) {
        this.endpoint = Objects.requireNonNull(builder.endpoint, "endpoint required");
        this.authType = builder.authType != null ? builder.authType : HttpIngressStrategy.AuthType.NONE;
        this.authHeaders = new HashMap<>(builder.authHeaders);
        this.timeout = builder.timeout != null ? builder.timeout : Duration.ofSeconds(30);
        this.maxRetries = builder.maxRetries >= 0 ? builder.maxRetries : 3;
        this.retryBackoff = builder.retryBackoff != null ? builder.retryBackoff : Duration.ofSeconds(1);
        this.validateSsl = builder.validateSsl;
        this.contentType = builder.contentType != null ? builder.contentType : "application/json";
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public String getEndpoint() { return endpoint; }
    public HttpIngressStrategy.AuthType getAuthType() { return authType; }
    public Map<String, String> getAuthHeaders() { return new HashMap<>(authHeaders); }
    public Duration getTimeout() { return timeout; }
    public int getMaxRetries() { return maxRetries; }
    public Duration getRetryBackoff() { return retryBackoff; }
    public boolean isValidateSsl() { return validateSsl; }
    public String getContentType() { return contentType; }
    
    public static class Builder {
        private String endpoint;
        private HttpIngressStrategy.AuthType authType;
        private Map<String, String> authHeaders = new HashMap<>();
        private Duration timeout;
        private int maxRetries = 3;
        private Duration retryBackoff;
        private boolean validateSsl = true;
        private String contentType;
        
        public Builder endpoint(String endpoint) { 
            this.endpoint = endpoint; 
            return this; 
        }
        
        public Builder authType(HttpIngressStrategy.AuthType authType) { 
            this.authType = authType; 
            return this; 
        }
        
        public Builder basicAuth(String username, String password) {
            this.authType = HttpIngressStrategy.AuthType.BASIC;
            String credentials = java.util.Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes());
            this.authHeaders.put("Authorization", "Basic " + credentials);
            return this;
        }
        
        public Builder bearerAuth(String token) {
            this.authType = HttpIngressStrategy.AuthType.BEARER;
            this.authHeaders.put("Authorization", "Bearer " + token);
            return this;
        }
        
        public Builder apiKeyAuth(String headerName, String apiKey) {
            this.authType = HttpIngressStrategy.AuthType.API_KEY;
            this.authHeaders.put(headerName, apiKey);
            return this;
        }
        
        public Builder addHeader(String name, String value) {
            this.authHeaders.put(name, value);
            return this;
        }
        
        public Builder timeout(Duration timeout) { 
            this.timeout = timeout; 
            return this; 
        }
        
        public Builder maxRetries(int maxRetries) { 
            this.maxRetries = maxRetries; 
            return this; 
        }
        
        public Builder retryBackoff(Duration retryBackoff) { 
            this.retryBackoff = retryBackoff; 
            return this; 
        }
        
        public Builder validateSsl(boolean validateSsl) { 
            this.validateSsl = validateSsl; 
            return this; 
        }
        
        public Builder contentType(String contentType) { 
            this.contentType = contentType; 
            return this; 
        }
        
        public HttpIngressConfig build() {
            return new HttpIngressConfig(this);
        }
    }
}
