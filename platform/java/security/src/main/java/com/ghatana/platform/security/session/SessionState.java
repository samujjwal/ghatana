package com.ghatana.platform.security.session;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Serializable session state container for distributed session management.
 * <p>
 * SessionState stores session metadata (userId, tenantId, timestamps) and custom attributes.
 * It supports expiration logic and is designed for cross-instance session sharing via
 * {@link SessionManager} implementations.
 * </p>
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li><b>Serializable</b>: Safe for Redis/DB storage</li>
 *   <li><b>UUID-Based IDs</b>: Random session IDs by default</li>
 *   <li><b>Attributes Map</b>: Key-value storage (all values must be Serializable)</li>
 *   <li><b>Expiration Logic</b>: isExpired() based on maxInactiveInterval</li>
 *   <li><b>Lifecycle Tracking</b>: createdAt, lastAccessedAt, expiresAt</li>
 *   <li><b>Builder Pattern</b>: Fluent API for construction</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Create new session with builder
 * SessionState session = SessionState.builder()
 *     .userId("user123")
 *     .tenantId("tenant1")
 *     .maxInactiveInterval(Duration.ofMinutes(30))
 *     .build();
 *
 * // Default constructor generates UUID
 * SessionState session2 = new SessionState();
 * System.out.println(session2.getSessionId());  // e.g., "a3b2c1d4-e5f6-..."
 *
 * // Set custom attributes
 * session.setAttribute("cartId", "cart123");
 * session.setAttribute("language", "en-US");
 * session.setAttribute("lastProduct", productId);
 *
 * // Retrieve attributes (type-safe)
 * String cartId = session.getAttribute("cartId");
 * String language = session.getAttribute("language");
 *
 * // Update last access time
 * session.access();  // Updates lastAccessedAt to now
 *
 * // Check expiration
 * if (session.isExpired()) {
 *     sessionManager.deleteSession(session.getSessionId());
 * }
 *
 * // Calculate remaining time
 * Duration remaining = session.getRemainingTime();
 * System.out.println("Session expires in " + remaining.toMinutes() + " minutes");
 * }</pre>
 * 
 * <h2>Session Lifecycle</h2>
 * <ol>
 *   <li><b>Creation</b>: SessionState.builder().build() or new SessionState()</li>
 *   <li><b>Active</b>: access() called on each request (updates lastAccessedAt)</li>
 *   <li><b>Expiration</b>: isExpired() returns true after maxInactiveInterval</li>
 *   <li><b>Cleanup</b>: SessionManager.deleteExpiredSessions() removes old sessions</li>
 * </ol>
 * 
 * <h2>Expiration Logic</h2>
 * <pre>{@code
 * expiresAt = lastAccessedAt + maxInactiveInterval
 * isExpired() = Instant.now().isAfter(expiresAt)
 * }</pre>
 * 
 * <h2>Attribute Storage Rules</h2>
 * <ul>
 *   <li><b>Values MUST be Serializable</b>: Required for Redis/DB storage</li>
 *   <li><b>getAttribute()</b>: Returns null if key not found</li>
 *   <li><b>removeAttribute()</b>: Returns removed value or null</li>
 *   <li><b>clearAttributes()</b>: Removes all custom attributes</li>
 * </ul>
 * 
 * <h2>Common Attributes</h2>
 * <ul>
 *   <li><code>cartId</code>: Shopping cart identifier</li>
 *   <li><code>language</code>: Preferred language (e.g., "en-US", "fr-FR")</li>
 *   <li><code>permissions</code>: User permissions (List<String>)</li>
 *   <li><code>lastProduct</code>: Last viewed product ID</li>
 *   <li><code>searchHistory</code>: Recent searches (List<String>)</li>
 * </ul>
 * 
 * <h2>Thread Safety</h2>
 * <b>WARNING</b>: NOT thread-safe. Use external synchronization or single-threaded access.
 * 
 * <h2>Serialization</h2>
 * Implements Serializable for:
 * <ul>
 *   <li>Redis storage (Jackson JSON serialization)</li>
 *   <li>Database BLOB storage</li>
 *   <li>Cross-JVM session replication</li>
 * </ul>
 *
 * @author Ghatana Team
 * @version 1.0
 * @since 1.0
 * @stability Stable
 * @completeness 95
 * @testing Unit
 * @thread_safety NOT thread-safe
 * @performance O(1) attribute access
 * @see SessionManager
 * @see RedisSessionManager
 
 *
 * @doc.type class
 * @doc.purpose Session state
 * @doc.layer core
 * @doc.pattern Component
*/
public class SessionState implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private final String id;
    private final Map<String, Object> attributes = new HashMap<>();
    private final Instant createdAt;
    private Instant lastAccessedAt;
    private String userId;
    private String tenantId;
    private long maxInactiveInterval; // in seconds
    
    /**
     * Create a new session state with a random ID.
     */
    public SessionState() {
        this(UUID.randomUUID().toString());
    }
    
    /**
     * Create a new session state with the specified ID.
     */
    public SessionState(String id) {
        this.id = id;
        this.createdAt = Instant.now();
        this.lastAccessedAt = Instant.now();
        this.maxInactiveInterval = 1800; // 30 minutes default
    }
    
    /**
     * Get the session ID.
     */
    public String getId() {
        return id;
    }
    
    /**
     * Get the creation timestamp.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    /**
     * Get the last accessed timestamp.
     */
    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }
    
    /**
     * Update the last accessed timestamp.
     */
    public void access() {
        this.lastAccessedAt = Instant.now();
    }
    
    /**
     * Get the user ID associated with this session.
     */
    public String getUserId() {
        return userId;
    }
    
    /**
     * Set the user ID associated with this session.
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    /**
     * Get the tenant ID associated with this session.
     */
    public String getTenantId() {
        return tenantId;
    }
    
    /**
     * Set the tenant ID associated with this session.
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
    
    /**
     * Get the maximum inactive interval in seconds.
     */
    public long getMaxInactiveInterval() {
        return maxInactiveInterval;
    }
    
    /**
     * Set the maximum inactive interval in seconds.
     */
    public void setMaxInactiveInterval(long maxInactiveInterval) {
        this.maxInactiveInterval = maxInactiveInterval;
    }
    
    /**
     * Check if the session has expired.
     */
    public boolean isExpired() {
        return lastAccessedAt.plusSeconds(maxInactiveInterval).isBefore(Instant.now());
    }
    
    /**
     * Get an attribute from the session.
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String name) {
        return (T) attributes.get(name);
    }
    
    /**
     * Set an attribute in the session.
     */
    public void setAttribute(String name, Object value) {
        if (value == null) {
            removeAttribute(name);
        } else if (!(value instanceof Serializable)) {
            throw new IllegalArgumentException("Attribute value must be serializable");
        } else {
            attributes.put(name, value);
        }
    }
    
    /**
     * Remove an attribute from the session.
     */
    public void removeAttribute(String name) {
        attributes.remove(name);
    }
    
    /**
     * Get all attribute names in the session.
     */
    public Iterable<String> getAttributeNames() {
        return attributes.keySet();
    }
    
    /**
     * Check if the session contains an attribute.
     */
    public boolean hasAttribute(String name) {
        return attributes.containsKey(name);
    }
    
    /**
     * Clear all attributes from the session.
     */
    public void clearAttributes() {
        attributes.clear();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SessionState that = (SessionState) o;
        return id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
    
    @Override
    public String toString() {
        return "SessionState{" +
            "id='" + id + '\'' +
            ", createdAt=" + createdAt +
            ", lastAccessedAt=" + lastAccessedAt +
            ", userId='" + userId + '\'' +
            ", tenantId='" + tenantId + '\'' +
            ", attributes=" + attributes.keySet() +
            '}';
    }
}
