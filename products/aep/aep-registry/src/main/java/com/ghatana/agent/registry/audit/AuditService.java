package com.ghatana.agent.registry.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.inject.annotation.Inject;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for logging audit events related to agent operations.
 * Stores audit events in the agent_audit_log table.
 *
 * @doc.type class
 * @doc.purpose Persists audit trail entries for agent lifecycle operations via JPA
 * @doc.layer product
 * @doc.pattern Service
 */
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final String currentUser;

    @Inject
    public AuditService(EntityManager entityManager, ObjectMapper objectMapper, String currentUser) {
        this.entityManager = entityManager;
        this.objectMapper = objectMapper;
        this.currentUser = currentUser != null ? currentUser : "system";
    }

    /**
     * Log an audit event for an agent operation.
     *
     * @param agentId The ID of the agent
     * @param action The action performed (CREATE, UPDATE, DELETE)
     * @param message A description of the action
     */
    public void logAuditEvent(String agentId, String action, String message) {
        logAuditEvent(agentId, action, message, null);
    }

    /**
     * Log an audit event for an agent operation with additional details.
     *
     * @param agentId The ID of the agent
     * @param action The action performed (CREATE, UPDATE, DELETE)
     * @param message A description of the action
     * @param details Additional details about the action
     */
    public void logAuditEvent(String agentId, String action, String message, Map<String, Object> details) {
        try {
            Map<String, Object> auditDetails = details != null ? new HashMap<>(details) : new HashMap<>();
            auditDetails.put("message", message);
            
            String detailsJson;
            try {
                detailsJson = objectMapper.writeValueAsString(auditDetails);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize audit details for agent {}: {}", agentId, e.getMessage());
                detailsJson = "{\"error\":\"Failed to serialize details\"}";
            }
            
            // Insert audit event using native SQL
            entityManager.createNativeQuery(
                    "INSERT INTO agent_audit_log (agent_id, action, actor, timestamp, details) VALUES (?, ?, ?, ?, ?::jsonb)")
                    .setParameter(1, agentId)
                    .setParameter(2, action)
                    .setParameter(3, currentUser)
                    .setParameter(4, Instant.now())
                    .setParameter(5, detailsJson)
                    .executeUpdate();
            
            if (log.isDebugEnabled()) {
                log.debug("Audit event logged: agent={}, action={}, actor={}, details={}", 
                    agentId, action, currentUser, detailsJson);
            }
        } catch (Exception e) {
            log.error("Failed to log audit event for agent {}: {}", agentId, e.getMessage(), e);
        }
    }

    /**
     * Get audit events for an agent.
     *
     * @param agentId The ID of the agent
     * @param limit Maximum number of events to return
     * @param offset Pagination offset
     * @return List of audit events as a list of maps
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getAuditEvents(String agentId, int limit, int offset) {
        return entityManager.createNativeQuery(
                "SELECT id, agent_id, action, actor, timestamp, details " +
                "FROM agent_audit_log " +
                "WHERE agent_id = ? " +
                "ORDER BY timestamp DESC " +
                "LIMIT ? OFFSET ?", 
                "AuditEventMapping")
                .setParameter(1, agentId)
                .setParameter(2, limit)
                .setParameter(3, offset)
                .getResultList();
    }
}
