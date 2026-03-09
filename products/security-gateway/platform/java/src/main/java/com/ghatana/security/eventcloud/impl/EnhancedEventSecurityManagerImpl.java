package com.ghatana.security.eventcloud.impl;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.security.encryption.EncryptionService;
import com.ghatana.security.eventcloud.EnhancedEventSecurityManager;
import com.ghatana.security.keys.KeyManager;
import com.ghatana.platform.security.rbac.PolicyService;
import com.ghatana.security.storage.EncryptedStorageService;
import com.ghatana.platform.observability.Metrics;
import com.ghatana.platform.domain.domain.event.Event;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
// java.util.concurrent.TimeUnit intentionally not imported; fully-qualified usage kept where needed

/**
 * Implementation of EnhancedEventSecurityManager that consolidates security
 * functionality from event-core EventCloudSecurityManager with the existing
 * EventCloud security infrastructure.
 
 *
 * @doc.type class
 * @doc.purpose Enhanced event security manager implementation
 * @doc.layer core
 * @doc.pattern Component
*/
public class EnhancedEventSecurityManagerImpl implements EnhancedEventSecurityManager {
    
    private static final Logger log = LoggerFactory.getLogger(EnhancedEventSecurityManagerImpl.class);
    private static final ObjectMapper JSON_MAPPER = JsonUtils.getDefaultMapper();
    
    private final KeyManager keyManager;
    private final EncryptionService encryptionService;
    private final EncryptedStorageService storageService;
    private final PolicyService policyService;
    private final Metrics metrics;
    
    // Dedicated executor for security operations
    private final Executor securityExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "enhanced-security");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY);
        return thread;
    });
    
    // Cache for user principals and permissions (bounded LRU)
    private static final int MAX_PRINCIPAL_CACHE_SIZE = 10_000;
    private final Map<String, Principal> principalCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Principal> eldest) {
                    return size() > MAX_PRINCIPAL_CACHE_SIZE;
                }
            });
    private final Map<String, Set<String>> userPermissionsCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Set<String>> eldest) {
                    return size() > MAX_PRINCIPAL_CACHE_SIZE;
                }
            });
    
    // Security metrics tracking
    private final Map<String, Long> operationMetrics = new ConcurrentHashMap<>();
    
    /**
     * Constructor for EnhancedEventSecurityManagerImpl.
     */
    public EnhancedEventSecurityManagerImpl(
        KeyManager keyManager,
        EncryptionService encryptionService,
        EncryptedStorageService storageService,
        PolicyService policyService,
        Metrics metrics
    ) {
        this.keyManager = keyManager;
        this.encryptionService = encryptionService;
        this.storageService = storageService;
        this.policyService = policyService;
        this.metrics = metrics;
    }
    
    // ==================== EVENT ENCRYPTION ====================

    @Override
    public Promise<byte[]> encryptEvent(Event event, String tenantId) {
        if (event == null) {
            return Promise.ofException(new IllegalArgumentException("Event cannot be null"));
        }
        if (tenantId == null || tenantId.trim().isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("Tenant ID cannot be null or empty"));
        }
        
        return Promise.ofBlocking(securityExecutor, () -> {
            try {
                long startTime = System.currentTimeMillis();
                
                // Serialize event to JSON
                byte[] eventData = JSON_MAPPER.writeValueAsBytes(event);
                
                // Get tenant-specific encryption key
                String keyId = getTenantKeyId(tenantId);
                
                return new Object[]{eventData, keyId, startTime};
                
            } catch (Exception e) {
                log.error("Failed to serialize event for tenant {}: {}", tenantId, e.getMessage(), e);
                metrics.counter("security.event.encryption.errors").increment();
                throw new RuntimeException("Event encryption failed", e);
            }
        }).then(resultArr -> {
            Object[] arr = (Object[]) resultArr;
            byte[] eventData = (byte[]) arr[0];
            String keyId = (String) arr[1];
            long startTime = (long) arr[2];
            
            return encryptionService.encryptAsync(eventData)
                .map(encryptedData -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.timer("security.event.encryption.duration").record(duration, 
                        java.util.concurrent.TimeUnit.MILLISECONDS);
                    incrementOperationMetric("encryptionOperations");
                    
                    log.debug("Encrypted event for tenant {}: {} bytes -> {} bytes", 
                        tenantId, eventData.length, encryptedData.length);
                    
                    return encryptedData;
                });
        });
    }

    @Override
    public Promise<Event> decryptEvent(byte[] encryptedData, String tenantId) {
        if (encryptedData == null || encryptedData.length == 0) {
            return Promise.ofException(new IllegalArgumentException("Encrypted data cannot be null or empty"));
        }
        if (tenantId == null || tenantId.trim().isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("Tenant ID cannot be null or empty"));
        }
        
        return Promise.ofBlocking(securityExecutor, () -> {
            // Get tenant-specific decryption key
            return getTenantKeyId(tenantId);
        }).then(keyId -> {
            return encryptionService.decryptWithKey(encryptedData, keyId);
        }).then(decryptedData -> {
            return Promise.ofBlocking(securityExecutor, () -> {
                try {
                    Event event = JSON_MAPPER.readValue(decryptedData, Event.class);
                    incrementOperationMetric("decryptionOperations");
                    log.debug("Decrypted event for tenant {}: {} bytes -> {} bytes", 
                        tenantId, encryptedData.length, decryptedData.length);
                    return event;
                } catch (Exception e) {
                    log.error("Failed to decrypt event for tenant {}: {}", tenantId, e.getMessage(), e);
                    metrics.counter("security.event.decryption.errors").increment();
                    throw new RuntimeException("Event decryption failed", e);
                }
            });
        });
    }

    @Override
    public Promise<byte[]> encryptForTransmission(byte[] eventData, String recipientServiceId) {
        String keyId = getServiceKeyId(recipientServiceId);
        return encryptionService.encryptWithKey(eventData, keyId)
            .mapException(e -> {
                log.error("Failed to encrypt for transmission to service {}: {}", recipientServiceId, e.getMessage(), e);
                return new RuntimeException("Transmission encryption failed", e);
            });
    }

    @Override
    public Promise<byte[]> decryptFromTransmission(byte[] encryptedData, String senderServiceId) {
        String keyId = getServiceKeyId(senderServiceId);
        return encryptionService.decryptWithKey(encryptedData, keyId)
            .mapException(e -> {
                log.error("Failed to decrypt from transmission from service {}: {}", senderServiceId, e.getMessage(), e);
                return new RuntimeException("Transmission decryption failed", e);
            });
    }
    
    // ==================== ACCESS CONTROL ====================

    @Override
    public Promise<Boolean> validateEventAccess(String userId, Event event, AccessOperation operation) {
        if (userId == null || event == null || operation == null) {
            return Promise.of(false);
        }
        
        return Promise.ofBlocking(securityExecutor, () -> {
            try {
                Principal principal = getPrincipal(userId);
                if (principal == null) {
                    incrementOperationMetric("accessDenials");
                    return false;
                }
                
                // Check basic event access permission
                String permission = "event:" + operation.name().toLowerCase();
                boolean hasAccess = policyService.isAuthorized(principal, permission, event.getType());
                
                // Additional checks for sensitive event types
                if (hasAccess && isSensitiveEventType(event.getType())) {
                    hasAccess = policyService.isAuthorized(principal, "sensitive:event:access", event.getType());
                }
                
                if (!hasAccess) {
                    incrementOperationMetric("accessDenials");
                    log.debug("Access denied for user {} to event {} (operation: {})", userId, event.getId(), operation);
                }
                
                return hasAccess;
                
            } catch (Exception e) {
                log.error("Error validating event access for user {}: {}", userId, e.getMessage(), e);
                incrementOperationMetric("accessDenials");
                return false;
            }
        });
    }

    @Override
    public Promise<Boolean> validateEventTypeAccess(String userId, String eventType, AccessOperation operation) {
        if (userId == null || eventType == null || operation == null) {
            return Promise.of(false);
        }
        
        return Promise.ofBlocking(securityExecutor, () -> {
            try {
                Principal principal = getPrincipal(userId);
                if (principal == null) {
                    incrementOperationMetric("accessDenials");
                    return false;
                }
                
                String permission = "eventtype:" + operation.name().toLowerCase();
                boolean hasAccess = policyService.isAuthorized(principal, permission, eventType);
                
                if (!hasAccess) {
                    incrementOperationMetric("accessDenials");
                    log.debug("Access denied for user {} to event type {} (operation: {})", userId, eventType, operation);
                }
                
                return hasAccess;
                
            } catch (Exception e) {
                log.error("Error validating event type access for user {}: {}", userId, e.getMessage(), e);
                incrementOperationMetric("accessDenials");
                return false;
            }
        });
    }

    @Override
    public Promise<Boolean> validateTenantAccess(String userId, String tenantId, AccessOperation operation) {
        if (userId == null || tenantId == null || operation == null) {
            return Promise.of(false);
        }
        
        return Promise.ofBlocking(securityExecutor, () -> {
            try {
                Principal principal = getPrincipal(userId);
                if (principal == null) {
                    incrementOperationMetric("accessDenials");
                    return false;
                }
                
                String permission = "tenant:" + operation.name().toLowerCase();
                boolean hasAccess = policyService.isAuthorized(principal, permission, tenantId);
                
                if (!hasAccess) {
                    incrementOperationMetric("accessDenials");
                    log.debug("Access denied for user {} to tenant {} (operation: {})", userId, tenantId, operation);
                }
                
                return hasAccess;
                
            } catch (Exception e) {
                log.error("Error validating tenant access for user {}: {}", userId, e.getMessage(), e);
                incrementOperationMetric("accessDenials");
                return false;
            }
        });
    }
    
    // ==================== SECURE QUERIES ====================

    @Override
    public Promise<Map<String, Object>> applySecurityFilters(String userId, Map<String, Object> queryParameters) {
        return Promise.ofBlocking(securityExecutor, () -> {
            try {
                Principal principal = getPrincipal(userId);
                if (principal == null) {
                    return Collections.emptyMap();
                }
                
                Map<String, Object> filteredParams = new HashMap<>(queryParameters);
                
                // Apply tenant filtering if user doesn't have cross-tenant access
                if (!policyService.isAuthorized(principal, "tenant:cross_access", null)) {
                    // Extract user's allowed tenants and filter
                    Set<String> allowedTenants = getUserAllowedTenants(userId);
                    if (!allowedTenants.isEmpty()) {
                        filteredParams.put("tenantFilter", allowedTenants);
                    }
                }
                
                // Apply event type filtering based on permissions
                Set<String> allowedEventTypes = getUserAllowedEventTypes(userId);
                if (!allowedEventTypes.isEmpty()) {
                    filteredParams.put("eventTypeFilter", allowedEventTypes);
                }
                
                return filteredParams;
                
            } catch (Exception e) {
                log.error("Error applying security filters for user {}: {}", userId, e.getMessage(), e);
                return Collections.emptyMap();
            }
        });
    }

    @Override
    public Promise<List<Event>> redactSensitiveData(String userId, List<Event> events) {
        return Promise.ofBlocking(securityExecutor, () -> {
            try {
                Principal principal = getPrincipal(userId);
                if (principal == null) {
                    return Collections.emptyList();
                }
                
                List<Event> redactedEvents = new ArrayList<>();
                
                for (Event event : events) {
                    // Check if user has permission to see sensitive data
                    boolean canSeeSensitive = policyService.isAuthorized(principal, "sensitive:data:view", event.getType());
                    
                    if (canSeeSensitive) {
                        redactedEvents.add(event);
                    } else {
                        // Create redacted version of the event
                        Event redactedEvent = redactEventSensitiveFields(event);
                        redactedEvents.add(redactedEvent);
                    }
                }
                
                return redactedEvents;
                
            } catch (Exception e) {
                log.error("Error redacting sensitive data for user {}: {}", userId, e.getMessage(), e);
                return Collections.emptyList();
            }
        });
    }
    
    // ==================== AUDIT & MONITORING ====================

    @Override
    public Promise<Void> auditEventAccess(String userId, AccessOperation operation, String eventId, 
                                         boolean success, Map<String, Object> additionalInfo) {
        return Promise.ofBlocking(securityExecutor, () -> {
            try {
                Map<String, Object> auditData = new HashMap<>();
                auditData.put("userId", userId);
                auditData.put("operation", operation.name());
                auditData.put("eventId", eventId);
                auditData.put("success", success);
                auditData.put("timestamp", System.currentTimeMillis());
                
                if (additionalInfo != null) {
                    auditData.putAll(additionalInfo);
                }
                
                // Store audit log (implementation depends on audit storage)
                storageService.storeSecurely("audit_log", JSON_MAPPER.writeValueAsBytes(auditData));
                
                incrementOperationMetric("auditLogEntries");
                
                return null;
                
            } catch (Exception e) {
                log.error("Error recording audit log for user {}: {}", userId, e.getMessage(), e);
                throw new RuntimeException("Audit logging failed", e);
            }
        });
    }

    @Override
    public Promise<SecurityMetrics> getSecurityMetrics(String tenantId) {
        return Promise.ofBlocking(securityExecutor, () -> {
            try {
                // Calculate security metrics
                return new SecurityMetrics(
                    operationMetrics.getOrDefault("encryptionOperations", 0L),
                    operationMetrics.getOrDefault("decryptionOperations", 0L),
                    operationMetrics.getOrDefault("accessDenials", 0L),
                    operationMetrics.getOrDefault("auditLogEntries", 0L),
                    0.0, // Would need timer metrics for actual averages
                    0.0,
                    new HashMap<>(operationMetrics)
                );
            } catch (Exception e) {
                log.error("Error getting security metrics: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to get security metrics", e);
            }
        });
    }
    
    // ==================== KEY MANAGEMENT ====================

    @Override
    public Promise<Void> rotateKeys(String tenantId) {
        return Promise.ofBlocking(securityExecutor, () -> {
            try {
                String keyId = getTenantKeyId(tenantId);
                keyManager.rotateKey();
                
                log.info("Rotated encryption keys for tenant: {}", tenantId);
                metrics.counter("security.key.rotations").increment();
                
                return null;
                
            } catch (Exception e) {
                log.error("Error rotating keys for tenant {}: {}", tenantId, e.getMessage(), e);
                throw new RuntimeException("Key rotation failed", e);
            }
        });
    }

    @Override
    public Promise<KeyHealthStatus> validateKeyHealth(String tenantId) {
        return Promise.ofBlocking(securityExecutor, () -> {
            try {
                String keyId = getTenantKeyId(tenantId);
                
                // Validate key health (implementation depends on KeyManager capabilities)
                boolean healthy = keyManager.isKeyHealthy(keyId);
                long keyAge = keyManager.getKeyAge(keyId);
                long lastRotation = keyManager.getLastRotationTime(keyId);
                
                List<String> issues = new ArrayList<>();
                if (keyAge > 90 * 24 * 60 * 60 * 1000) { // 90 days
                    issues.add("Key is older than 90 days");
                }
                
                return new KeyHealthStatus(
                    tenantId,
                    healthy && issues.isEmpty(),
                    keyAge,
                    lastRotation,
                    issues,
                    Map.of("keyId", keyId)
                );
                
            } catch (Exception e) {
                log.error("Error validating key health for tenant {}: {}", tenantId, e.getMessage(), e);
                return new KeyHealthStatus(
                    tenantId,
                    false,
                    0L,
                    0L,
                    List.of("Error validating key health: " + e.getMessage()),
                    Map.of()
                );
            }
        });
    }
    
    // ==================== HELPER METHODS ====================
    
    private String getTenantKeyId(String tenantId) {
        return "tenant:" + tenantId + ":encryption";
    }
    
    private String getServiceKeyId(String serviceId) {
        return "service:" + serviceId + ":transport";
    }
    
    private Principal getPrincipal(String userId) {
        // This would typically load from a user service or cache
        // For now, return a placeholder implementation
        return principalCache.computeIfAbsent(userId, id -> {
            // Load principal from user service
            // Load principal from user service
            return new Principal("tenant-1", List.of("user"));
        });
    }
    
    private boolean isSensitiveEventType(String eventType) {
        // Define sensitive event types
        return eventType.contains("pii") || 
               eventType.contains("financial") || 
               eventType.contains("health") ||
               eventType.startsWith("sensitive.");
    }
    
    private Set<String> getUserAllowedTenants(String userId) {
        // Implementation would query user's tenant permissions
        return Set.of("default"); // Placeholder
    }
    
    private Set<String> getUserAllowedEventTypes(String userId) {
        // Implementation would query user's event type permissions
        return Set.of(); // Empty means no filtering
    }
    
    private Event redactEventSensitiveFields(Event event) {
        // Implementation would redact sensitive fields based on configuration
        // For now, return the original event
        return event;
    }
    
    private void incrementOperationMetric(String metricName) {
        operationMetrics.merge(metricName, 1L, Long::sum);
    }
}