package com.ghatana.digitalmarketing.bridge;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import io.activej.promise.Promise;

import java.util.Map;

/**
 * Bridge interface for kernel platform integration.
 *
 * <p>Provides abstraction over kernel platform services for:
 * <ul>
 *   <li>Cryptographic operations</li>
 *   <li>Feature flag checking</li>
 *   <li>Audit event recording</li>
 *   <li>Authorization checks</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Kernel platform bridge abstraction
 * @doc.layer product
 * @doc.pattern Bridge, Adapter
 */
public interface KernelBridge {

    /**
     * Computes HMAC-SHA256 using platform-managed keys.
     *
     * @param ctx operation context
     * @param data data to sign
     * @param keyId key identifier
     * @return base64-encoded HMAC
     */
    Promise<String> computeHmac(DmOperationContext ctx, String data, String keyId);

    /**
     * Hashes data using platform-managed hashing.
     *
     * @param ctx operation context
     * @param data data to hash
     * @param salt salt value
     * @return hashed value
     */
    Promise<String> hash(DmOperationContext ctx, String data, String salt);

    /**
     * Hashes PII data using platform-managed hashing for anonymization.
     *
     * @param ctx operation context
     * @param piiData PII data to hash
     * @param salt optional salt value
     * @return hashed value
     */
    Promise<String> hashPii(DmOperationContext ctx, String piiData, String salt);

    /**
     * Checks if a feature flag is enabled.
     *
     * @param ctx operation context
     * @param flagKey feature flag key
     * @return true if enabled
     */
    Promise<Boolean> isFeatureEnabled(DmOperationContext ctx, String flagKey);

    /**
     * Records an audit event.
     *
     * @param ctx operation context
     * @param entityType entity type
     * @param action action performed
     * @param metadata additional metadata
     * @return true if recorded successfully
     */
    Promise<Boolean> recordAudit(DmOperationContext ctx, String entityType, String action, Map<String, Object> metadata);

    /**
     * Checks if actor is authorized for resource and permission.
     *
     * @param ctx operation context
     * @param resource resource name
     * @param permission permission required
     * @return true if authorized
     */
    Promise<Boolean> isAuthorized(DmOperationContext ctx, String resource, String permission);

    /**
     * Rotates a cryptographic key.
     *
     * @param ctx operation context
     * @param keyId key identifier to rotate
     * @return void promise when complete
     */
    Promise<Void> rotateKey(DmOperationContext ctx, String keyId);

    /**
     * Lists available cryptographic keys.
     *
     * @param ctx operation context
     * @return list of key metadata
     */
    Promise<java.util.List<CryptoPlatformAdapter.KeyMetadata>> listCryptoKeys(DmOperationContext ctx);

    /**
     * Generates a secure random string (base64 encoded).
     *
     * @param length length in bytes
     * @return base64-encoded secure random bytes
     */
    Promise<String> generateSecureRandom(int length);
}
