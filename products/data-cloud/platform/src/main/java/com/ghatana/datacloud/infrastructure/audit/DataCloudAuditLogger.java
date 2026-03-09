package com.ghatana.datacloud.infrastructure.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;

/**
 * Data Cloud Audit Logger for tracking connector operations.
 * 
 * <p>Provides audit logging for data access, modification, and search operations
 * in compliance with security and regulatory requirements.</p>
 * 
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * DataCloudAuditLogger auditLogger = new DataCloudAuditLogger(true);
 * auditLogger.logDataAccess("tenant-1", "READ", "collection-1", "entity-1", true);
 * }</pre>
 * 
 * @doc.type service
 * @doc.purpose Audit logging for data cloud operations
 * @doc.layer infrastructure
 * @doc.pattern Observer
 */
public class DataCloudAuditLogger {
    private static final Logger logger = LoggerFactory.getLogger(DataCloudAuditLogger.class);
    
    private final boolean enabled;
    
    /**
     * Creates a new audit logger.
     * 
     * @param enabled whether audit logging is enabled
     */
    public DataCloudAuditLogger(boolean enabled) {
        this.enabled = enabled;
    }
    
    /**
     * Logs a data access event.
     * 
     * @param tenantId the tenant ID
     * @param operation the operation type (READ, QUERY, SCAN)
     * @param collectionId the collection ID
     * @param entityId the entity ID (may be null for bulk operations)
     * @param success whether the operation succeeded
     */
    public void logDataAccess(String tenantId, String operation, String collectionId, String entityId, boolean success) {
        if (!enabled) return;
        
        logger.info("AUDIT: eventType=DATA_ACCESS, timestamp={}, tenantId={}, operation={}, " +
                "collectionId={}, entityId={}, success={}, principal={}",
                Instant.now(), tenantId, operation, collectionId, entityId, success, getCurrentPrincipal());
    }
    
    /**
     * Logs a data modification event.
     * 
     * @param tenantId the tenant ID
     * @param operation the operation type (CREATE, UPDATE, DELETE)
     * @param collectionId the collection ID
     * @param entityId the entity ID
     * @param success whether the operation succeeded
     */
    public void logDataModification(String tenantId, String operation, String collectionId, String entityId, boolean success) {
        if (!enabled) return;
        
        logger.info("AUDIT: eventType=DATA_MODIFICATION, timestamp={}, tenantId={}, operation={}, " +
                "collectionId={}, entityId={}, success={}, principal={}",
                Instant.now(), tenantId, operation, collectionId, entityId, success, getCurrentPrincipal());
    }
    
    /**
     * Logs a search operation event.
     * 
     * @param tenantId the tenant ID
     * @param query the search query
     * @param resultCount the number of results returned
     * @param success whether the operation succeeded
     */
    public void logSearch(String tenantId, String query, int resultCount, boolean success) {
        if (!enabled) return;
        
        logger.info("AUDIT: eventType=SEARCH_EXECUTED, timestamp={}, tenantId={}, query={}, " +
                "resultCount={}, success={}, principal={}",
                Instant.now(), tenantId, query, resultCount, success, getCurrentPrincipal());
    }
    
    /**
     * Logs a bulk operation event.
     * 
     * @param tenantId the tenant ID
     * @param operation the operation type (BULK_CREATE, BULK_DELETE)
     * @param collectionId the collection ID
     * @param count the number of entities affected
     * @param success whether the operation succeeded
     */
    public void logBulkOperation(String tenantId, String operation, String collectionId, int count, boolean success) {
        if (!enabled) return;
        
        logger.info("AUDIT: eventType=BULK_OPERATION, timestamp={}, tenantId={}, operation={}, " +
                "collectionId={}, count={}, success={}, principal={}",
                Instant.now(), tenantId, operation, collectionId, count, success, getCurrentPrincipal());
    }
    
    /**
     * Gets the current principal (user) from the thread-local security context.
     *
     * <p>Attempts to resolve the actor identity in the following order:
     * <ol>
     *   <li>Thread-local {@code TenantContext.current()} principal name</li>
     *   <li>Thread-local {@code TenantContext.getCurrentTenantId()} as fallback</li>
     *   <li>{@code "SYSTEM"} when no security context is available (explicitly audited)</li>
     * </ol>
     *
     * @return the principal identifier; never null
     */
    private String getCurrentPrincipal() {
        try {
            // Attempt to get principal from thread-local TenantContext
            java.util.Optional<com.ghatana.platform.governance.security.Principal> principal =
                    com.ghatana.platform.governance.security.TenantContext.current();
            if (principal.isPresent()) {
                String name = principal.get().getName();
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            }

            // Fallback to tenant ID if principal name not available
            String tenantId = com.ghatana.platform.governance.security.TenantContext.getCurrentTenantId();
            if (tenantId != null && !tenantId.isEmpty() && !"default-tenant".equals(tenantId)) {
                return "tenant:" + tenantId;
            }
        } catch (Exception e) {
            // Security context not available; fall through to SYSTEM
            logger.debug("Security context unavailable for audit principal resolution: {}", e.getMessage());
        }

        return "SYSTEM";
    }
    
    /**
     * Creates a no-op audit logger (disabled).
     * 
     * @return a disabled audit logger
     */
    public static DataCloudAuditLogger noop() {
        return new DataCloudAuditLogger(false);
    }
    
    /**
     * Creates an enabled audit logger.
     * 
     * @return an enabled audit logger
     */
    public static DataCloudAuditLogger enabled() {
        return new DataCloudAuditLogger(true);
    }
}
