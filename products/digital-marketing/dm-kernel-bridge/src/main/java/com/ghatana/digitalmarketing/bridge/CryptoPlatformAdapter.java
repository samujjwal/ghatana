package com.ghatana.digitalmarketing.bridge;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;

/**
 * P1-011: Crypto/HMAC Platform Adapter.
 *
 * <p>Adapter for platform-level cryptographic operations:
 * <ul>
 *   <li>HMAC-SHA256 computation using platform keys</li>
 *   <li>PII data hashing for anonymization</li>
 *   <li>Key rotation support</li>
 *   <li>Consistent hashing across services</li>
 *   <li>Audit trail for crypto operations</li>
 * </ul>
 *
 * <p>Migrates from local crypto implementation to shared Kernel platform.</p>
 *
 * @doc.type class
 * @doc.purpose Platform crypto/HMAC adapter for PII protection (P1-011)
 * @doc.layer product
 * @doc.pattern Platform Adapter, Cryptography, Security
 */
public final class CryptoPlatformAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(CryptoPlatformAdapter.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HASH_ALGORITHM = "SHA-256";

    private final KernelBridge kernelBridge;
    private final String serviceIdentifier;

    public CryptoPlatformAdapter(KernelBridge kernelBridge, String serviceIdentifier) {
        this.kernelBridge = Objects.requireNonNull(kernelBridge, "kernelBridge must not be null");
        this.serviceIdentifier = Objects.requireNonNull(serviceIdentifier, "serviceIdentifier must not be null");
    }

    /**
     * P1-011: Computes HMAC-SHA256 using platform-managed keys.
     *
     * <p>Delegates to Kernel platform for key management and HMAC computation.</p>
     *
     * @param ctx operation context for audit trail
     * @param data the data to sign
     * @param keyId the key identifier to use
     * @return promise resolving to base64-encoded HMAC
     */
    public Promise<String> computeHmac(DmOperationContext ctx, String data, String keyId) {
        LOG.debug("[DMOS-CRYPTO] Computing HMAC for keyId={}", keyId);

        // P1-011: Delegate to platform crypto service
        return kernelBridge.computeHmac(ctx, data, keyId)
            .whenResult(hmac -> {
                LOG.debug("[DMOS-CRYPTO] HMAC computed successfully for keyId={}", keyId);
            })
            .whenException(e -> {
                LOG.error("[DMOS-CRYPTO] HMAC computation failed for keyId={}: {}", keyId, e.getMessage());
            });
    }

    /**
     * P1-011: Verifies HMAC-SHA256 signature.
     *
     * @param ctx operation context
     * @param data the original data
     * @param keyId the key identifier
     * @param expectedHmac the expected HMAC signature
     * @return promise resolving to true if valid
     */
    public Promise<Boolean> verifyHmac(DmOperationContext ctx, String data, String keyId, String expectedHmac) {
        LOG.debug("[DMOS-CRYPTO] Verifying HMAC for keyId={}", keyId);

        return computeHmac(ctx, data, keyId)
            .map(computedHmac -> {
                boolean valid = computedHmac.equals(expectedHmac);
                LOG.debug("[DMOS-CRYPTO] HMAC verification result: {}", valid);
                return valid;
            });
    }

    /**
     * P1-011: Hashes PII data for anonymization.
     *
     * <p>Uses platform-managed hashing keys for consistent anonymization.</p>
     *
     * @param ctx operation context
     * @param piiData the PII to hash
     * @param salt optional salt for additional entropy
     * @return promise resolving to hashed value
     */
    public Promise<String> hashPii(DmOperationContext ctx, String piiData, String salt) {
        LOG.debug("[DMOS-CRYPTO] Hashing PII data");

        // P1-011: Use platform hashing for consistent anonymization
        return kernelBridge.hashPii(ctx, piiData, salt)
            .whenResult(hash -> {
                LOG.debug("[DMOS-CRYPTO] PII hashed successfully");
            });
    }

    /**
     * P1-011: Generates deterministic hash for tenant-scoped data.
     *
     * <p>Combines tenant ID with data for tenant-isolated hashing.</p>
     *
     * @param ctx operation context
     * @param data the data to hash
     * @return promise resolving to tenant-scoped hash
     */
    public Promise<String> computeTenantScopedHash(DmOperationContext ctx, String data) {
        String tenantId = ctx.getTenantId().getValue();
        String tenantScopedData = tenantId + ":" + data;

        LOG.debug("[DMOS-CRYPTO] Computing tenant-scoped hash for tenant={}", tenantId);

        return hashPii(ctx, tenantScopedData, tenantId);
    }

    /**
     * P1-011: Local HMAC computation fallback (for testing or platform unavailable).
     *
     * @param data the data
     * @param keyBytes the raw key bytes
     * @return base64-encoded HMAC
     */
    public String computeHmacLocal(String data, byte[] keyBytes) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
            mac.init(secretKey);

            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            LOG.error("[DMOS-CRYPTO] Local HMAC computation failed", e);
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    /**
     * P1-011: Local SHA-256 hash computation.
     *
     * @param data the data to hash
     * @return hex-encoded hash
     */
    public String computeHashLocal(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            LOG.error("[DMOS-CRYPTO] Hash algorithm not available", e);
            throw new RuntimeException("Hash computation failed", e);
        }
    }

    /**
     * P1-011: Rotates cryptographic keys.
     *
     * <p>Triggers platform key rotation for specified key.</p>
     *
     * @param ctx operation context
     * @param keyId the key to rotate
     * @return promise resolving when complete
     */
    public Promise<Void> rotateKey(DmOperationContext ctx, String keyId) {
        LOG.info("[DMOS-CRYPTO] Initiating key rotation for keyId={}", keyId);

        return kernelBridge.rotateKey(ctx, keyId)
            .whenResult(v -> {
                LOG.info("[DMOS-CRYPTO] Key rotation completed for keyId={}", keyId);
            })
            .whenException(e -> {
                LOG.error("[DMOS-CRYPTO] Key rotation failed for keyId={}: {}", keyId, e.getMessage());
            });
    }

    /**
     * P1-011: Lists available crypto keys.
     *
     * @param ctx operation context
     * @return promise resolving to list of key metadata
     */
    public Promise<java.util.List<KeyMetadata>> listKeys(DmOperationContext ctx) {
        return kernelBridge.listCryptoKeys(ctx);
    }

    /**
     * P1-011: Generates cryptographically secure random bytes.
     *
     * @param length number of bytes to generate
     * @return promise resolving to base64-encoded random bytes
     */
    public Promise<String> generateSecureRandom(int length) {
        return kernelBridge.generateSecureRandom(length);
    }

    /**
     * Key metadata record.
     */
    public record KeyMetadata(
        String keyId,
        String algorithm,
        String purpose,
        java.time.Instant createdAt,
        java.time.Instant rotatedAt,
        boolean active
    ) {}
}
