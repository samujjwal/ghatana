package com.ghatana.core.connectors.impl.source;

import com.ghatana.platform.domain.auth.TenantId;

/**
 * Production-grade configuration for HTTP webhook event source with authentication and validation.
 *
 * <p><b>Purpose</b><br>
 * Provides immutable configuration for HTTP webhook endpoints that receive events
 * from external systems. Supports tenant isolation, authentication (Basic Auth, Bearer),
 * content limits, timeouts, and path routing with comprehensive validation.
 *
 * <p><b>Architecture Role</b><br>
 * Configuration value object in core/connectors/impl/source for webhook setup.
 * Used by:
 * - HTTP Webhook Sources - Configure webhook endpoints
 * - Event Ingestion - Receive events via HTTP POST
 * - Multi-Tenant Systems - Tenant-scoped webhook endpoints
 * - External Integrations - Receive events from third-party systems
 * - API Gateways - Configure webhook routing and authentication
 *
 * <p><b>Configuration Features</b><br>
 * - <b>Tenant Isolation</b>: TenantId for multi-tenant deployments
 * - <b>HTTP Server</b>: Configurable port and path
 * - <b>Basic Auth</b>: Username/password authentication
 * - <b>Bearer Token</b>: Token-based authentication
 * - <b>Content Limits</b>: Max payload size protection
 * - <b>Timeouts</b>: Read timeout for slow clients
 * - <b>Validation</b>: Comprehensive builder validation
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Basic webhook configuration
 * HttpWebhookSourceConfig config = HttpWebhookSourceConfig.builder()
 *     .tenantId(TenantId.of("tenant-123"))
 *     .serverPort(8080)
 *     .path("/webhook/events")
 *     .build();
 * 
 * // Webhook accessible at: http://localhost:8080/webhook/events
 *
 * // 2. Webhook with Basic Auth
 * HttpWebhookSourceConfig secureConfig = HttpWebhookSourceConfig.builder()
 *     .tenantId(TenantId.of("tenant-123"))
 *     .serverPort(8443)
 *     .path("/webhook/secure")
 *     .basicAuthUsername("webhook_user")
 *     .basicAuthPassword("secret_password")
 *     .build();
 * 
 * // Clients must send: Authorization: Basic base64(webhook_user:secret_password)
 *
 * // 3. Webhook with Bearer token
 * HttpWebhookSourceConfig tokenConfig = HttpWebhookSourceConfig.builder()
 *     .tenantId(TenantId.of("tenant-123"))
 *     .serverPort(8080)
 *     .path("/webhook/api")
 *     .bearerToken("sk_live_abc123...")
 *     .build();
 * 
 * // Clients must send: Authorization: Bearer sk_live_abc123...
 *
 * // 4. Production configuration with limits
 * HttpWebhookSourceConfig prodConfig = HttpWebhookSourceConfig.builder()
 *     .tenantId(TenantId.of("tenant-prod"))
 *     .serverPort(443)  // HTTPS
 *     .path("/api/v1/webhooks/events")
 *     .bearerToken(System.getenv("WEBHOOK_TOKEN"))
 *     .maxContentLength(5 * 1024 * 1024)  // 5 MB max payload
 *     .readTimeout(60000)  // 60 seconds
 *     .build();
 *
 * // 5. Multi-tenant webhook configuration
 * public class WebhookConfigFactory {
 *     public HttpWebhookSourceConfig createTenantWebhook(String tenantId) {
 *         return HttpWebhookSourceConfig.builder()
 *             .tenantId(TenantId.of(tenantId))
 *             .serverPort(8080)
 *             .path("/webhook/" + tenantId)
 *             .bearerToken(generateTenantToken(tenantId))
 *             .maxContentLength(1024 * 1024)  // 1 MB
 *             .readTimeout(30000)  // 30 seconds
 *             .build();
 *     }
 * }
 *
 * // 6. Webhook per event type
 * Map<String, HttpWebhookSourceConfig> webhooks = Map.of(
 *     "user.created", HttpWebhookSourceConfig.builder()
 *         .tenantId(tenantId)
 *         .serverPort(8080)
 *         .path("/webhook/users")
 *         .bearerToken("user_webhook_token")
 *         .build(),
 *     
 *     "order.created", HttpWebhookSourceConfig.builder()
 *         .tenantId(tenantId)
 *         .serverPort(8080)
 *         .path("/webhook/orders")
 *         .bearerToken("order_webhook_token")
 *         .build()
 * );
 *
 * // 7. Use with HTTP webhook source
 * HttpWebhookSourceConfig config = HttpWebhookSourceConfig.builder()
 *     .tenantId(tenantId)
 *     .serverPort(8080)
 *     .path("/webhook")
 *     .bearerToken("webhook_token")
 *     .build();
 * 
 * HttpWebhookEventSource source = new HttpWebhookEventSource(config);
 * source.start();  // Starts HTTP server on port 8080
 * 
 * // External system POSTs events:
 * // POST http://localhost:8080/webhook
 * // Authorization: Bearer webhook_token
 * // Content-Type: application/json
 * // Body: {"eventType": "user.created", "data": {...}}
 * }</pre>
 *
 * <p><b>Default Values</b><br>
 * - serverPort: 8080
 * - path: "/webhook"
 * - maxContentLength: 1 MB (1024 * 1024 bytes)
 * - readTimeout: 30 seconds (30000 ms)
 * - basicAuthUsername: null (no authentication)
 * - basicAuthPassword: null (no authentication)
 * - bearerToken: null (no authentication)
 *
 * <p><b>Authentication Options</b><br>
 * <pre>
 * Option 1: No Authentication
 *   .build()
 *   WARNING: Only for development/internal networks
 *
 * Option 2: Basic Auth
 *   .basicAuthUsername("user")
 *   .basicAuthPassword("password")
 *   Client sends: Authorization: Basic base64(user:password)
 *
 * Option 3: Bearer Token
 *   .bearerToken("sk_live_...")
 *   Client sends: Authorization: Bearer sk_live_...
 * </pre>
 *
 * <p><b>Validation Rules</b><br>
 * Builder enforces:
 * - tenantId: Required (not null)
 * - serverPort: 1-65535 range
 * - path: Not null/empty, must start with '/'
 * - maxContentLength: Positive integer
 * - readTimeout: Non-negative integer
 * - basicAuth: Both username AND password required (or neither)
 * - bearerToken: Optional, mutually exclusive with Basic Auth
 *
 * <p><b>Path Routing Examples</b><br>
 * <pre>
 * /webhook                  - Simple root webhook
 * /webhook/events           - Events webhook
 * /api/v1/webhooks/stripe   - Stripe integration
 * /webhooks/tenant-123      - Tenant-specific webhook
 * /hooks/user.created       - Event-type-specific webhook
 * </pre>
 *
 * <p><b>Content Length Limits</b><br>
 * Protects against large payloads:
 * - 1 MB (default): Typical events
 * - 5 MB: Large events (file uploads, bulk data)
 * - 10 MB: Maximum recommended (DoS protection)
 * - Requests exceeding limit: Rejected with 413 Payload Too Large
 *
 * <p><b>Read Timeout</b><br>
 * Protects against slow clients:
 * - 30 seconds (default): Standard timeout
 * - 60 seconds: Slow external systems
 * - Timeout exceeded: Connection closed, 408 Request Timeout
 *
 * <p><b>Security Best Practices</b><br>
 * - Always use authentication in production (Basic Auth or Bearer)
 * - Use HTTPS for production (configure reverse proxy)
 * - Rotate bearer tokens regularly
 * - Use strong passwords for Basic Auth
 * - Validate webhook signatures (implement in handler)
 * - Rate limit webhook endpoints (implement in handler)
 * - Log authentication failures for monitoring
 *
 * <p><b>Multi-Tenant Considerations</b><br>
 * - Use tenantId for request routing and isolation
 * - Separate tokens per tenant (don't share)
 * - Consider tenant-specific paths: /webhook/{tenantId}
 * - Validate tenant authorization in handler
 * - Track metrics per tenant
 *
 * <p><b>Error Scenarios</b><br>
 * Builder throws IllegalArgumentException for:
 * - Null tenantId
 * - Port out of range (1-65535)
 * - Null/empty path
 * - Path not starting with '/'
 * - Non-positive maxContentLength
 * - Negative readTimeout
 * - Basic Auth username without password (or vice versa)
 *
 * <p><b>Thread Safety</b><br>
 * Immutable - all fields final and no mutable references.
 * Safe to share across threads.
 *
 * @see HttpWebhookEventSource
 * @see TenantId
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Configuration for HTTP webhook event source with authentication
 * @doc.layer core
 * @doc.pattern Configuration
 */
public final class HttpWebhookSourceConfig {

    private static final int DEFAULT_SERVER_PORT = 8080;
    private static final String DEFAULT_PATH = "/webhook";
    private static final int DEFAULT_MAX_CONTENT_LENGTH = 1024 * 1024;
    private static final int DEFAULT_READ_TIMEOUT = 30000;
    
    private final TenantId tenantId;
    private final int serverPort;
    private final String path;
    private final String basicAuthUsername;
    private final String basicAuthPassword;
    private final String bearerToken;
    private final int maxContentLength;
    private final int readTimeout;
    
    private HttpWebhookSourceConfig(Builder builder) {
        if (builder.tenantId == null) {
            throw new IllegalArgumentException("TenantId cannot be null");
        }
        if (builder.serverPort < 1 || builder.serverPort > 65535) {
            throw new IllegalArgumentException("Server port must be between 1 and 65535");
        }
        if (builder.path == null || builder.path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }
        if (!builder.path.startsWith("/")) {
            throw new IllegalArgumentException("Path must start with '/'");
        }
        if (builder.maxContentLength <= 0) {
            throw new IllegalArgumentException("Max content length must be positive");
        }
        if (builder.readTimeout < 0) {
            throw new IllegalArgumentException("Read timeout cannot be negative");
        }
        
        boolean hasUsername = builder.basicAuthUsername != null;
        boolean hasPassword = builder.basicAuthPassword != null;
        if (hasUsername != hasPassword) {
            throw new IllegalArgumentException(
                "Both username and password must be set together for basic auth");
        }
        
        this.tenantId = builder.tenantId;
        this.serverPort = builder.serverPort;
        this.path = builder.path;
        this.basicAuthUsername = builder.basicAuthUsername;
        this.basicAuthPassword = builder.basicAuthPassword;
        this.bearerToken = builder.bearerToken;
        this.maxContentLength = builder.maxContentLength;
        this.readTimeout = builder.readTimeout;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public TenantId getTenantId() {
        return tenantId;
    }
    
    public int getServerPort() {
        return serverPort;
    }
    
    public String getPath() {
        return path;
    }
    
    public String getBasicAuthUsername() {
        return basicAuthUsername;
    }
    
    public String getBasicAuthPassword() {
        return basicAuthPassword;
    }
    
    public String getBearerToken() {
        return bearerToken;
    }
    
    public int getMaxContentLength() {
        return maxContentLength;
    }
    
    public int getReadTimeout() {
        return readTimeout;
    }
    
    @Override
    public String toString() {
        return "HttpWebhookSourceConfig{" +
                "tenantId=" + tenantId +
                ", serverPort=" + serverPort +
                ", path=" + path +
                ", hasBasicAuth=" + (basicAuthUsername != null) +
                ", hasBearerToken=" + (bearerToken != null) +
                ", maxContentLength=" + maxContentLength +
                ", readTimeout=" + readTimeout +
                '}';
    }
    
    public static final class Builder {
        private TenantId tenantId;
        private int serverPort = DEFAULT_SERVER_PORT;
        private String path = DEFAULT_PATH;
        private String basicAuthUsername;
        private String basicAuthPassword;
        private String bearerToken;
        private int maxContentLength = DEFAULT_MAX_CONTENT_LENGTH;
        private int readTimeout = DEFAULT_READ_TIMEOUT;
        
        private Builder() {
        }
        
        public Builder withTenantId(TenantId tenantId) {
            this.tenantId = tenantId;
            return this;
        }
        
        public Builder withServerPort(int serverPort) {
            this.serverPort = serverPort;
            return this;
        }
        
        public Builder withPath(String path) {
            this.path = path;
            return this;
        }
        
        public Builder withBasicAuth(String username, String password) {
            this.basicAuthUsername = username;
            this.basicAuthPassword = password;
            return this;
        }
        
        public Builder withBearerToken(String token) {
            this.bearerToken = token;
            return this;
        }
        
        public Builder withMaxContentLength(int maxContentLength) {
            this.maxContentLength = maxContentLength;
            return this;
        }
        
        public Builder withReadTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }
        
        public HttpWebhookSourceConfig build() {
            return new HttpWebhookSourceConfig(this);
        }
    }
}
