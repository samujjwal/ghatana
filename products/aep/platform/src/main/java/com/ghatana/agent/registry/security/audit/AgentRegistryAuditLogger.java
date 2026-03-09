package com.ghatana.agent.registry.security.audit;

import com.ghatana.platform.audit.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lightweight agent-registry specific audit logger.
 * Renamed to AgentRegistryAuditLogger to avoid simple-name collisions with the canonical
 * {@code com.ghatana.security.audit.AuditLogger} implementation.
 * This class intentionally delegates to SLF4J and is provided for backward compatibility
 * within the agent-registry module.
 */
public class AgentRegistryAuditLogger {
    private static final Logger logger = LoggerFactory.getLogger(AgentRegistryAuditLogger.class);

    /**
     * Logs an audit event.
     *
     * @param event The audit event to log
     */
    public void log(AuditEvent event) {
        if (event == null) {
            logger.warn("AUDIT: attempted to log null event");
            return;
        }
        String action = String.valueOf(event.getDetails().getOrDefault("action", "UNKNOWN"));
        logger.info("AUDIT: {} - {} - {}",
                action,
                event.getResourceId(),
                event.getDetails());
    }

    /**
     * Convenience method to log an audit event with individual parameters.
     */
    public void log(String action, String resourceType, String resourceId, String userId, String details) {
        log(AuditEvent.builder()
                .eventType("AGENT_REGISTRY_AUDIT")
                .resourceId(resourceId)
                .principal(userId)
                .detail("action", action)
                .detail("resourceType", resourceType)
                .detail("details", details != null ? details : "")
                .timestamp(java.time.Instant.now())
                .success(true)
                .build());
    }
}
