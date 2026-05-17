package com.ghatana.datacloud.spi;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * P1-04: In-memory implementation of ConnectorSecretService for local development.
 * 
 * <p>This implementation stores connector secrets in memory for local development.
 * For production, a secure secret management system (e.g., AWS Secrets Manager,
 * HashiCorp Vault) should be used.
 *
 * @doc.type class
 * @doc.purpose In-memory connector secret service for local development
 * @doc.layer product
 * @doc.pattern Service, InMemory
 */
public class InMemoryConnectorSecretService implements ConnectorSecretService {
    
    private static final Logger log = LoggerFactory.getLogger(InMemoryConnectorSecretService.class);
    
    private final Map<String, SecretEntry> secretStore = new ConcurrentHashMap<>();
    private final Map<String, SecretMetadata> metadataStore = new ConcurrentHashMap<>();
    
    @Override
    public Promise<String> createSecretReference(String tenantId, String connectorId, String secretType,
                                                  String secretValue, Map<String, Object> metadata,
                                                  String principalId) {
        log.info("[P1-04] Creating secret reference: tenantId={}, connectorId={}, secretType={}",
            tenantId, connectorId, secretType);
        
        String secretRef = UUID.randomUUID().toString();
        String key = tenantId + ":" + secretRef;
        
        SecretEntry entry = new SecretEntry(
            secretValue,
            secretType,
            connectorId,
            Instant.now(),
            Instant.now()
        );
        
        secretStore.put(key, entry);
        
        SecretMetadata meta = new SecretMetadata(
            secretRef,
            secretType,
            connectorId,
            Instant.now(),
            Instant.now(),
            null,
            redactMetadata(metadata)
        );
        
        metadataStore.put(key, meta);
        
        // Audit the secret creation
        auditSecretAccess(tenantId, secretRef, "CREATE", principalId).whenComplete((ignored, error) -> {
            if (error != null) {
                log.warn("[P1-04] Failed to audit secret creation: {}", secretRef, error);
            }
        });
        
        return Promise.of(secretRef);
    }
    
    @Override
    public Promise<SecretValue> getSecret(String tenantId, String secretRef, String principalId) {
        String key = tenantId + ":" + secretRef;
        SecretEntry entry = secretStore.get(key);
        
        if (entry == null) {
            return Promise.of((SecretValue) null);
        }
        
        // Update last accessed time
        SecretMetadata meta = metadataStore.get(key);
        if (meta != null) {
            SecretMetadata updated = new SecretMetadata(
                meta.secretRef(),
                meta.secretType(),
                meta.connectorId(),
                meta.createdAt(),
                meta.lastRotatedAt(),
                Instant.now(),
                meta.redactedMetadata()
            );
            metadataStore.put(key, updated);
        }
        
        // Audit the secret access
        auditSecretAccess(tenantId, secretRef, "READ", principalId).whenComplete((ignored, error) -> {
            if (error != null) {
                log.warn("[P1-04] Failed to audit secret access: {}", secretRef, error);
            }
        });
        
        SecretValue value = new SecretValue(
            entry.value(),
            entry.secretType(),
            entry.createdAt(),
            entry.lastRotatedAt()
        );
        
        return Promise.of(value);
    }
    
    @Override
    public Promise<Void> rotateSecret(String tenantId, String secretRef, String newSecretValue,
                                       String principalId) {
        String key = tenantId + ":" + secretRef;
        SecretEntry entry = secretStore.get(key);
        
        if (entry == null) {
            return Promise.complete();
        }
        
        SecretEntry rotated = new SecretEntry(
            newSecretValue,
            entry.secretType(),
            entry.connectorId(),
            entry.createdAt(),
            Instant.now()
        );
        
        secretStore.put(key, rotated);
        
        // Update metadata
        SecretMetadata meta = metadataStore.get(key);
        if (meta != null) {
            SecretMetadata updated = new SecretMetadata(
                meta.secretRef(),
                meta.secretType(),
                meta.connectorId(),
                meta.createdAt(),
                Instant.now(),
                Instant.now(),
                meta.redactedMetadata()
            );
            metadataStore.put(key, updated);
        }
        
        // Audit the secret rotation
        auditSecretAccess(tenantId, secretRef, "ROTATE", principalId).whenComplete((ignored, error) -> {
            if (error != null) {
                log.warn("[P1-04] Failed to audit secret rotation: {}", secretRef, error);
            }
        });
        
        return Promise.complete();
    }
    
    @Override
    public Promise<Void> revokeSecret(String tenantId, String secretRef, String principalId) {
        String key = tenantId + ":" + secretRef;
        
        secretStore.remove(key);
        metadataStore.remove(key);
        
        // Audit the secret revocation
        auditSecretAccess(tenantId, secretRef, "REVOKE", principalId).whenComplete((ignored, error) -> {
            if (error != null) {
                log.warn("[P1-04] Failed to audit secret revocation: {}", secretRef, error);
            }
        });
        
        return Promise.complete();
    }
    
    @Override
    public Promise<Boolean> validateSecretAccessibility(String tenantId, String secretRef) {
        String key = tenantId + ":" + secretRef;
        return Promise.of(secretStore.containsKey(key));
    }
    
    @Override
    public Promise<SecretMetadata> getSecretMetadata(String tenantId, String secretRef) {
        String key = tenantId + ":" + secretRef;
        return Promise.of(metadataStore.get(key));
    }
    
    @Override
    public Promise<Void> auditSecretAccess(String tenantId, String secretRef, String action,
                                            String principalId) {
        log.info("[P1-04] Audit secret access: tenantId={}, secretRef={}, action={}, principalId={}",
            tenantId, secretRef, action, principalId);
        // In production, this would write to an audit log
        return Promise.complete();
    }
    
    private Map<String, Object> redactMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return Map.of();
        }
        // Redact sensitive fields
        return Map.of("type", metadata.getOrDefault("type", "unknown"));
    }
    
    private record SecretEntry(
        String value,
        String secretType,
        String connectorId,
        Instant createdAt,
        Instant lastRotatedAt
    ) {}
}
