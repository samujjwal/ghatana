package com.ghatana.datacloud.spi;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;

/**
 * P1-04: Service for managing connector secrets with tenant isolation and audit logging.
 * 
 * <p>This service provides secure storage and management of connector credentials,
 * ensuring that raw credentials never leak into API payloads and that secret references
 * are tenant-isolated and audited.
 *
 * @doc.type interface
 * @doc.purpose Service for managing connector secrets with tenant isolation
 * @doc.layer product
 * @doc.pattern Service
 */
public interface ConnectorSecretService {
    
    /**
     * Creates a secret reference for a connector.
     *
     * <p>This method stores the secret securely and returns a reference ID
     * that can be used to retrieve the secret later. The secret is never
     * returned in the response.
     *
     * @param tenantId the tenant ID
     * @param connectorId the connector ID
     * @param secretType the type of secret (e.g., API_KEY, OAUTH_TOKEN, BASIC_AUTH)
     * @param secretValue the secret value to store
     * @param metadata additional metadata about the secret
     * @param principalId the principal ID making the request
     * @return promise that completes with the secret reference ID
     */
    Promise<String> createSecretReference(String tenantId, String connectorId, String secretType,
                                         String secretValue, Map<String, Object> metadata,
                                         String principalId);
    
    /**
     * Retrieves a secret by reference ID.
     *
     * <p>This method validates that the caller has access to the secret
     * and returns the secret value. Access is logged for audit purposes.
     *
     * @param tenantId the tenant ID
     * @param secretRef the secret reference ID
     * @param principalId the principal ID making the request
     * @return promise that completes with the secret value
     */
    Promise<SecretValue> getSecret(String tenantId, String secretRef, String principalId);
    
    /**
     * Rotates a secret (creates a new version).
     *
     * <p>This method creates a new version of the secret while keeping
     * the old version for rollback. The old version is marked for
     * deletion after a grace period.
     *
     * @param tenantId the tenant ID
     * @param secretRef the secret reference ID
     * @param newSecretValue the new secret value
     * @param principalId the principal ID making the request
     * @return promise that completes when rotation is done
     */
    Promise<Void> rotateSecret(String tenantId, String secretRef, String newSecretValue,
                                String principalId);
    
    /**
     * Revokes a secret.
     *
     * <p>This method permanently deletes the secret and all its versions.
     * This action is irreversible and is logged for audit purposes.
     *
     * @param tenantId the tenant ID
     * @param secretRef the secret reference ID
     * @param principalId the principal ID making the request
     * @return promise that completes when revocation is done
     */
    Promise<Void> revokeSecret(String tenantId, String secretRef, String principalId);
    
    /**
     * Validates that a secret reference is accessible.
     *
     * <p>This method checks if the secret reference exists and is accessible
     * without revealing the secret value.
     *
     * @param tenantId the tenant ID
     * @param secretRef the secret reference ID
     * @return promise that completes with true if accessible, false otherwise
     */
    Promise<Boolean> validateSecretAccessibility(String tenantId, String secretRef);
    
    /**
     * Gets secret metadata without revealing the secret value.
     *
     * @param tenantId the tenant ID
     * @param secretRef the secret reference ID
     * @return promise that completes with the secret metadata
     */
    Promise<SecretMetadata> getSecretMetadata(String tenantId, String secretRef);
    
    /**
     * Records secret access for audit logging.
     *
     * @param tenantId the tenant ID
     * @param secretRef the secret reference ID
     * @param action the action performed (e.g., CREATE, READ, ROTATE, REVOKE)
     * @param principalId the principal ID making the request
     * @return promise that completes when audit logging is done
     */
    Promise<Void> auditSecretAccess(String tenantId, String secretRef, String action,
                                     String principalId);
    
    /**
     * Represents a secret value with metadata.
     */
    record SecretValue(
        String value,
        String secretType,
        Instant createdAt,
        Instant lastRotatedAt
    ) {}
    
    /**
     * Represents secret metadata without the secret value.
     */
    record SecretMetadata(
        String secretRef,
        String secretType,
        String connectorId,
        Instant createdAt,
        Instant lastRotatedAt,
        Instant lastAccessedAt,
        Map<String, Object> redactedMetadata
    ) {}
}
