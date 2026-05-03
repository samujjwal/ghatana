package com.ghatana.digitalmarketing.domain.api;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Domain entity representing a public API key for tenant/workspace access (DMOS-P1-016).
 *
 * <p>API keys are stored as hashed values only. The raw key is displayed only once during creation.
 * Keys include a prefix for lookup and support rotation, revocation, and last-used tracking.</p>
 *
 * @doc.type class
 * @doc.purpose Stores hashed API keys with rotation, revocation, and usage tracking (DMOS-P1-016)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmApiKey {

    private final String id;
    private final DmTenantId tenantId;
    private final DmWorkspaceId workspaceId;
    private final String keyPrefix; // First 8 characters for lookup
    private final String keyHash; // SHA-256 hash of the full key
    private final String rateLimitPlan;
    private final Instant createdAt;
    private final Instant lastUsedAt;
    private final Instant expiresAt;
    private final boolean revoked;
    private final Instant revokedAt;
    private final String revokedBy;
    private final String createdBy;

    private static final int KEY_LENGTH = 32; // 256 bits
    private static final int PREFIX_LENGTH = 8;
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private DmApiKey(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id must not be null");
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId must not be null");
        this.workspaceId = Objects.requireNonNull(builder.workspaceId, "workspaceId must not be null");
        this.keyPrefix = Objects.requireNonNull(builder.keyPrefix, "keyPrefix must not be null");
        this.keyHash = Objects.requireNonNull(builder.keyHash, "keyHash must not be null");
        this.rateLimitPlan = builder.rateLimitPlan != null ? builder.rateLimitPlan : "default";
        this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt must not be null");
        this.lastUsedAt = builder.lastUsedAt;
        this.expiresAt = builder.expiresAt;
        this.revoked = builder.revoked;
        this.revokedAt = builder.revokedAt;
        this.revokedBy = builder.revokedBy;
        this.createdBy = Objects.requireNonNull(builder.createdBy, "createdBy must not be null");

        if (this.id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (this.keyPrefix.isBlank()) {
            throw new IllegalArgumentException("keyPrefix must not be blank");
        }
        if (this.keyHash.isBlank()) {
            throw new IllegalArgumentException("keyHash must not be blank");
        }
    }

    /**
     * Generates a new API key with the specified tenant/workspace scope.
     * Returns a tuple of (apiKey, rawKey) - the raw key is displayed only once.
     */
    public static ApiKeyWithRaw generate(DmTenantId tenantId, DmWorkspaceId workspaceId, String createdBy, String rateLimitPlan) {
        String rawKey = generateRawKey();
        String keyPrefix = rawKey.substring(0, PREFIX_LENGTH);
        String keyHash = computeHash(rawKey);
        String id = generateId();

        DmApiKey apiKey = new Builder()
            .id(id)
            .tenantId(tenantId)
            .workspaceId(workspaceId)
            .keyPrefix(keyPrefix)
            .keyHash(keyHash)
            .rateLimitPlan(rateLimitPlan)
            .createdAt(Instant.now())
            .createdBy(createdBy)
            .build();

        return new ApiKeyWithRaw(apiKey, rawKey);
    }

    /**
     * Returns a rotated API key with a new raw key but same metadata.
     */
    public ApiKeyWithRaw rotate(String rotatedBy) {
        String rawKey = generateRawKey();
        String keyPrefix = rawKey.substring(0, PREFIX_LENGTH);
        String keyHash = computeHash(rawKey);

        DmApiKey newApiKey = toBuilder()
            .keyPrefix(keyPrefix)
            .keyHash(keyHash)
            .createdAt(Instant.now())
            .createdBy(rotatedBy)
            .build();

        return new ApiKeyWithRaw(newApiKey, rawKey);
    }

    /**
     * Returns a revoked API key.
     */
    public DmApiKey revoke(String revokedBy) {
        if (revoked) {
            throw new IllegalStateException("API key is already revoked");
        }
        return toBuilder()
            .revoked(true)
            .revokedAt(Instant.now())
            .revokedBy(revokedBy)
            .build();
    }

    /**
     * Updates the last-used timestamp.
     */
    public DmApiKey recordUsage() {
        return toBuilder().lastUsedAt(Instant.now()).build();
    }

    /**
     * Checks if this API key is valid (not revoked and not expired).
     */
    public boolean isValid() {
        return !revoked && (expiresAt == null || Instant.now().isBefore(expiresAt));
    }

    /**
     * Verifies that the provided raw key matches the stored hash.
     */
    public boolean verify(String rawKey) {
        String computedHash = computeHash(rawKey);
        return computedHash.equals(this.keyHash);
    }

    public String getId() { return id; }
    public DmTenantId getTenantId() { return tenantId; }
    public DmWorkspaceId getWorkspaceId() { return workspaceId; }
    public String getKeyPrefix() { return keyPrefix; }
    public String getKeyHash() { return keyHash; }
    public String getRateLimitPlan() { return rateLimitPlan; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getRevokedBy() { return revokedBy; }
    public String getCreatedBy() { return createdBy; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmApiKey other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        // Redact hash from logs (DMOS-P1-016)
        return "DmApiKey{id='" + id + "', tenantId='" + tenantId + "', workspaceId='" + workspaceId + "', keyPrefix='" + keyPrefix + "', revoked=" + revoked + "}";
    }

    public Builder toBuilder() {
        return new Builder()
            .id(id)
            .tenantId(tenantId)
            .workspaceId(workspaceId)
            .keyPrefix(keyPrefix)
            .keyHash(keyHash)
            .rateLimitPlan(rateLimitPlan)
            .createdAt(createdAt)
            .lastUsedAt(lastUsedAt)
            .expiresAt(expiresAt)
            .revoked(revoked)
            .revokedAt(revokedAt)
            .revokedBy(revokedBy)
            .createdBy(createdBy);
    }

    public static Builder builder() { return new Builder(); }

    private static String generateRawKey() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[KEY_LENGTH];
        random.nextBytes(bytes);
        return HEX_FORMAT.formatHex(bytes);
    }

    private static String computeHash(String rawKey) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HEX_FORMAT.formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String generateId() {
        return java.util.UUID.randomUUID().toString();
    }

    public static final class Builder {
        private String id;
        private DmTenantId tenantId;
        private DmWorkspaceId workspaceId;
        private String keyPrefix;
        private String keyHash;
        private String rateLimitPlan;
        private Instant createdAt;
        private Instant lastUsedAt;
        private Instant expiresAt;
        private boolean revoked = false;
        private Instant revokedAt;
        private String revokedBy;
        private String createdBy;

        public Builder id(String id) { this.id = id; return this; }
        public Builder tenantId(DmTenantId tenantId) { this.tenantId = tenantId; return this; }
        public Builder workspaceId(DmWorkspaceId workspaceId) { this.workspaceId = workspaceId; return this; }
        public Builder keyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; return this; }
        public Builder keyHash(String keyHash) { this.keyHash = keyHash; return this; }
        public Builder rateLimitPlan(String rateLimitPlan) { this.rateLimitPlan = rateLimitPlan; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder lastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; return this; }
        public Builder expiresAt(Instant expiresAt) { this.expiresAt = expiresAt; return this; }
        public Builder revoked(boolean revoked) { this.revoked = revoked; return this; }
        public Builder revokedAt(Instant revokedAt) { this.revokedAt = revokedAt; return this; }
        public Builder revokedBy(String revokedBy) { this.revokedBy = revokedBy; return this; }
        public Builder createdBy(String createdBy) { this.createdBy = createdBy; return this; }

        public DmApiKey build() {
            return new DmApiKey(this);
        }
    }

    /**
     * Tuple containing an API key and its raw secret key.
     * The raw key should be displayed only once during creation/rotation.
     */
    public record ApiKeyWithRaw(DmApiKey apiKey, String rawKey) {
        public ApiKeyWithRaw {
            Objects.requireNonNull(apiKey, "apiKey must not be null");
            Objects.requireNonNull(rawKey, "rawKey must not be null");
        }
    }
}
