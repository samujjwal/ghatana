package com.ghatana.pipeline.registry.service;

import com.ghatana.pipeline.registry.session.SessionUtils;
import com.ghatana.platform.security.session.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Example service demonstrating session-aware business operations.
 *
 * <p>Purpose: Provides reference implementation showing how to access and
 * manipulate user session data in business logic. Demonstrates session-scoped
 * preference storage and page tracking patterns.</p>
 *
 * @doc.type class
 * @doc.purpose Example service demonstrating session-aware patterns
 * @doc.layer product
 * @doc.pattern Service
 * @since 2.0.0
 */
public class SessionAwareService {
    
    private static final Logger LOG = LoggerFactory.getLogger(SessionAwareService.class);
    
    /**
     * Get user preferences from the session.
     */
    public Map<String, Object> getUserPreferences() {
        return SessionUtils.withSessionOrDefault(session -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> preferences = session.getAttribute("preferences");
            if (preferences == null) {
                preferences = new HashMap<>();
                session.setAttribute("preferences", preferences);
            }
            return preferences;
        }, new HashMap<>());
    }
    
    /**
     * Save user preferences to the session.
     */
    public void saveUserPreferences(Map<String, Object> preferences) {
        SessionUtils.withSession(session -> {
            session.setAttribute("preferences", preferences);
            return null;
        });
    }
    
    /**
     * Get the last accessed page from the session.
     */
    public Optional<String> getLastAccessedPage() {
        return SessionUtils.getCurrentSession()
            .map(session -> session.getAttribute("lastAccessedPage"));
    }
    
    /**
     * Set the last accessed page in the session.
     */
    public void setLastAccessedPage(String page) {
        SessionUtils.getCurrentSession().ifPresent(session -> 
            session.setAttribute("lastAccessedPage", page));
    }
    
    /**
     * Perform a tenant-specific operation.
     */
    public String performTenantOperation(String operation) {
        return SessionUtils.withTenantId(tenantId -> {
            LOG.info("Performing operation '{}' for tenant '{}'", operation, tenantId);
            // Tenant-specific logic here
            return "Operation '" + operation + "' completed for tenant '" + tenantId + "'";
        });
    }
    
    /**
     * Perform a user-specific operation.
     */
    public String performUserOperation(String operation) {
        return SessionUtils.withUserId(userId -> {
            LOG.info("Performing operation '{}' for user '{}'", operation, userId);
            // User-specific logic here
            return "Operation '" + operation + "' completed for user '" + userId + "'";
        });
    }
    
    /**
     * Perform an operation that requires both user and tenant context.
     */
    public String performContextualOperation(String operation) {
        return SessionUtils.withUserAndTenant((userId, tenantId) -> {
            LOG.info("Performing operation '{}' for user '{}' in tenant '{}'", 
                operation, userId, tenantId);
            // Contextual logic here
            return "Operation '" + operation + "' completed for user '" + userId + 
                "' in tenant '" + tenantId + "'";
        });
    }
    
    /**
     * Store an audit entry in the request context.
     */
    public void addAuditEntry(String action, String result) {
        RequestContext.currentOptional().ifPresent(context -> {
            Map<String, Object> auditEntries = context.getAttribute("auditEntries");
            if (auditEntries == null) {
                auditEntries = new HashMap<>();
                context.setAttribute("auditEntries", auditEntries);
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> entries = (Map<String, Object>) auditEntries;
            
            Map<String, String> entry = new HashMap<>();
            entry.put("action", action);
            entry.put("result", result);
            entry.put("timestamp", String.valueOf(System.currentTimeMillis()));
            
            entries.put(String.valueOf(entries.size() + 1), entry);
        });
    }
    
    /**
     * Get all audit entries from the request context.
     */
    public Map<String, Object> getAuditEntries() {
        return RequestContext.currentOptional()
            .map(context -> {
                Object attributeValue = context.getAttribute("auditEntries");
                @SuppressWarnings("unchecked")
                Map<String, Object> auditEntries = (Map<String, Object>) attributeValue;
                return auditEntries != null ? auditEntries : new HashMap<String, Object>();
            })
            .orElse(new HashMap<String, Object>());
    }
}
