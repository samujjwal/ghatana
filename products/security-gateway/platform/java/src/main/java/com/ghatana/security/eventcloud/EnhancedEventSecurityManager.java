package com.ghatana.security.eventcloud;

import com.ghatana.platform.domain.domain.event.Event;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.List;

/**
 * Enhanced security manager that consolidates event-cloud security capabilities
 * with the existing EventCloud security infrastructure. This service provides
 * comprehensive security operations for event processing, storage, and access control.
 
 *
 * @doc.type interface
 * @doc.purpose Enhanced event security manager
 * @doc.layer core
 * @doc.pattern Manager
*/
public interface EnhancedEventSecurityManager {
    
    // ==================== EVENT ENCRYPTION ====================
    
    /**
     * Encrypts event data before storage with tenant-specific keys.
     * 
     * @param event The event to encrypt
     * @param tenantId The tenant ID for key selection
     * @return Promise completing with encrypted event data
     */
    Promise<byte[]> encryptEvent(Event event, String tenantId);
    
    /**
     * Decrypts event data after retrieval.
     * 
     * @param encryptedData The encrypted event data
     * @param tenantId The tenant ID for key selection
     * @return Promise completing with decrypted event
     */
    Promise<Event> decryptEvent(byte[] encryptedData, String tenantId);
    
    /**
     * Encrypts event data for transmission between services.
     * 
     * @param eventData The event data to encrypt
     * @param recipientServiceId The service ID that will decrypt the data
     * @return Promise completing with encrypted data for transmission
     */
    Promise<byte[]> encryptForTransmission(byte[] eventData, String recipientServiceId);
    
    /**
     * Decrypts event data received from another service.
     * 
     * @param encryptedData The encrypted data received
     * @param senderServiceId The service ID that encrypted the data
     * @return Promise completing with decrypted data
     */
    Promise<byte[]> decryptFromTransmission(byte[] encryptedData, String senderServiceId);
    
    // ==================== ACCESS CONTROL ====================
    
    /**
     * Validates if a user has permission to access an event.
     * 
     * @param userId The user ID requesting access
     * @param event The event being accessed
     * @param operation The operation being performed (READ, WRITE, DELETE)
     * @return Promise completing with true if access is allowed
     */
    Promise<Boolean> validateEventAccess(String userId, Event event, AccessOperation operation);
    
    /**
     * Validates if a user has permission to perform an operation on an event type.
     * 
     * @param userId The user ID requesting access
     * @param eventType The event type being accessed
     * @param operation The operation being performed
     * @return Promise completing with true if access is allowed
     */
    Promise<Boolean> validateEventTypeAccess(String userId, String eventType, AccessOperation operation);
    
    /**
     * Validates tenant-level access for event operations.
     * 
     * @param userId The user ID requesting access
     * @param tenantId The tenant ID being accessed
     * @param operation The operation being performed
     * @return Promise completing with true if access is allowed
     */
    Promise<Boolean> validateTenantAccess(String userId, String tenantId, AccessOperation operation);
    
    // ==================== SECURE QUERIES ====================
    
    /**
     * Applies security filters to event queries based on user permissions.
     * 
     * @param userId The user ID performing the query
     * @param queryParameters The original query parameters
     * @return Promise completing with security-filtered query parameters
     */
    Promise<Map<String, Object>> applySecurityFilters(String userId, Map<String, Object> queryParameters);
    
    /**
     * Redacts sensitive information from events based on user permissions.
     * 
     * @param userId The user ID requesting the events
     * @param events The events to potentially redact
     * @return Promise completing with redacted events
     */
    Promise<List<Event>> redactSensitiveData(String userId, List<Event> events);
    
    // ==================== AUDIT & MONITORING ====================
    
    /**
     * Records security audit log for event access.
     * 
     * @param userId The user ID performing the operation
     * @param operation The operation performed
     * @param eventId The event ID (if applicable)
     * @param success Whether the operation was successful
     * @param additionalInfo Additional audit information
     * @return Promise completing when audit log is recorded
     */
    Promise<Void> auditEventAccess(String userId, AccessOperation operation, String eventId, 
                                   boolean success, Map<String, Object> additionalInfo);
    
    /**
     * Gets security metrics for monitoring and alerting.
     * 
     * @param tenantId The tenant ID (optional, null for global metrics)
     * @return Promise completing with security metrics
     */
    Promise<SecurityMetrics> getSecurityMetrics(String tenantId);
    
    // ==================== KEY MANAGEMENT ====================
    
    /**
     * Rotates encryption keys for a tenant.
     * 
     * @param tenantId The tenant ID
     * @return Promise completing when key rotation is complete
     */
    Promise<Void> rotateKeys(String tenantId);
    
    /**
     * Validates key health and reports any issues.
     * 
     * @param tenantId The tenant ID (optional, null for all tenants)
     * @return Promise completing with key health status
     */
    Promise<KeyHealthStatus> validateKeyHealth(String tenantId);
    
    // ==================== DATA CLASSES ====================
    
    /**
     * Security access operations enumeration
     */
    enum AccessOperation {
        READ,
        WRITE,
        DELETE,
        QUERY,
        STREAM,
        ADMIN
    }
    
    /**
     * Security metrics for monitoring
     */
    record SecurityMetrics(
        long encryptionOperations,
        long decryptionOperations,
        long accessDenials,
        long auditLogEntries,
        double averageEncryptionTimeMs,
        double averageDecryptionTimeMs,
        Map<String, Long> operationCounts
    ) { }
    
    /**
     * Key health status information
     */
    record KeyHealthStatus(
        String tenantId,
        boolean healthy,
        long keyAge,
        long lastRotation,
        java.util.List<String> issues,
        Map<String, Object> details
    ) { }

}