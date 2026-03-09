package com.ghatana.security.audit;

import com.ghatana.platform.audit.AuditEvent;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.function.Consumer;

/**
 * Logger for security-related audit events.
 * This class provides methods to log security events such as authentication attempts,
 * authorization decisions, and other security-relevant actions.
 
 *
 * @doc.type class
 * @doc.purpose Security audit logger
 * @doc.layer core
 * @doc.pattern Component
*/
public class SecurityAuditLogger {
    private static final Logger logger = LoggerFactory.getLogger(SecurityAuditLogger.class);
    
    private final Eventloop eventloop;
    private final Consumer<AuditEvent> auditConsumer;

    /**
     * Creates a new SecurityAuditLogger with the specified event loop and audit consumer.
     *
     * @param eventloop the event loop to use for asynchronous operations
     * @param auditConsumer the consumer that will process audit events
     */
    public SecurityAuditLogger(Eventloop eventloop, Consumer<AuditEvent> auditConsumer) {
        this.eventloop = eventloop;
        this.auditConsumer = auditConsumer;
    }

    /**
     * Creates a new SecurityAuditLogger with the specified event loop and audit logger.
     *
     * @param eventloop the event loop to use for asynchronous operations
     * @param auditLogger the audit logger to use for persisting events
     */
    public SecurityAuditLogger(Eventloop eventloop, AuditLogger auditLogger) {
        this(eventloop, event -> {
            try {
                auditLogger.log(event); // fire-and-forget; underlying logger handles persistence
            } catch (Exception e) {
                logger.error("Failed to log audit event: {}", event, e);
            }
        });
    }

    /**
     * Logs an access granted event.
     *
     * @param userId the ID of the user who was granted access
     * @param resource the resource that was accessed
     * @param role the role that granted access
     */
    public void logAccessGranted(String userId, String resource, String role) {
        logEvent(AuditEvent.builder()
                .eventType("ACCESS_GRANTED")
                .timestamp(Instant.now())
                .principal(userId)
                .resourceId(resource)
                .success(true)
                .detail("action", "AUTHORIZE")
                .detail("role", role)
                .build());
    }

    /**
     * Logs an access denied event.
     *
     * @param userId the ID of the user who was denied access
     * @param resource the resource that was attempted to be accessed
     * @param reason the reason for the denial
     */
    public void logAccessDenied(String userId, String resource, String reason) {
        logEvent(AuditEvent.builder()
                .eventType("ACCESS_DENIED")
                .timestamp(Instant.now())
                .principal(userId)
                .resourceId(resource)
                .success(false)
                .detail("action", "AUTHORIZE")
                .detail("reason", reason)
                .build());
    }

    /**
     * Logs an authentication success event.
     *
     * @param userId the ID of the authenticated user
     * @param method the authentication method used
     */
    public void logAuthenticationSuccess(String userId, String method) {
        logEvent(AuditEvent.builder()
                .eventType("AUTHENTICATION_SUCCESS")
                .timestamp(Instant.now())
                .principal(userId)
                .success(true)
                .detail("action", "AUTHENTICATE")
                .detail("method", method)
                .build());
    }

    /**
     * Logs an authentication failure event.
     *
     * @param userId the ID of the user who failed authentication (if known)
     * @param method the authentication method used
     * @param reason the reason for the failure
     */
    public void logAuthenticationFailure(String userId, String method, String reason) {
        String principal = userId != null ? userId : "unknown";
        logEvent(AuditEvent.builder()
                .eventType("AUTHENTICATION_FAILURE")
                .timestamp(Instant.now())
                .principal(principal)
                .success(false)
                .detail("action", "AUTHENTICATE")
                .detail("method", method)
                .detail("reason", reason)
                .build());
    }

    /**
     * Logs a rate limit exceeded event.
     *
     * @param clientIp the client's IP address
     * @param path the requested path
     * @param method the HTTP method
     */
    public void logRateLimitExceeded(String clientIp, String path, String method) {
        logEvent(AuditEvent.builder()
                .eventType("RATE_LIMIT_EXCEEDED")
                .timestamp(Instant.now())
                .principal(clientIp)
                .resourceId(path)
                .success(false)
                .detail("action", method)
                .build());
    }

    /**
     * Logs a security configuration change event.
     *
     * @param adminId the ID of the administrator making the change
     * @param configItem the configuration item that was changed
     * @param oldValue the old value of the configuration item
     * @param newValue the new value of the configuration item
     */
    public void logConfigChange(String adminId, String configItem, String oldValue, String newValue) {
        logEvent(AuditEvent.builder()
                .eventType("CONFIG_CHANGE")
                .timestamp(Instant.now())
                .principal(adminId)
                .resourceId(configItem)
                .success(true)
                .detail("action", "UPDATE")
                .detail("old", oldValue)
                .detail("new", newValue)
                .build());
    }

    /**
     * Logs a custom security event.
     *
     * @param event the audit event to log
     */
    public void logEvent(AuditEvent event) {
        try {
            // Log to SLF4J for immediate visibility (derive success from success field)
            Boolean success = event.getSuccess();
            if (Boolean.TRUE.equals(success)) {
                logger.info("Security event: {}", event.toString());
            } else {
                logger.warn("Security event: {}", event.toString());
            }
            
            // Process the event asynchronously through the audit consumer
            eventloop.execute(() -> {
                try {
                    auditConsumer.accept(event);
                } catch (Exception e) {
                    logger.error("Error processing audit event: {}", event.toString(), e);
                }
            });
        } catch (Exception e) {
            logger.error("Failed to log security event: {}", event.toString(), e);
        }
    }
    
    /**
     * Logs an event and returns a Promise that completes when the event has been processed.
     *
     * @param event the audit event to log
     * @return a Promise that completes when the event has been processed
     */
    public Promise<Void> logEventAsync(AuditEvent event) {
        return Promise.ofCallback(cb -> {
            eventloop.execute(() -> {
                try {
                    auditConsumer.accept(event);
                    cb.accept(null, null);
                } catch (Exception e) {
                    cb.accept(null, e);
                }
            });
        });
    }
    
    /**
     * Logs an event and returns a Promise that completes when the event has been processed.
     *
     * @param event the audit event to log
     * @return a Promise that completes when the event has been processed
     */
    public Promise<Void> logEventPromise(AuditEvent event) {
        return logEventAsync(event);
    }

    public void logAuthFailure(String token, String message) {
        auditConsumer.accept(AuditEvent.builder()
                .eventType("AUTH_FAILURE")
                .timestamp(Instant.now())
                .principal(token)
                .success(false)
                .detail("description", "Authentication failed for token")
                .detail("message", message)
                .build());
    }
    
    public void logRoleRetrievalFailure(String userId, String message) {
        auditConsumer.accept(AuditEvent.builder()
                .eventType("ROLE_RETRIEVAL_FAILURE")
                .timestamp(Instant.now())
                .principal(userId)
                .success(false)
                .detail("description", "Failed to retrieve roles for user " + userId)
                .detail("message", message)
                .build());
    }
}
