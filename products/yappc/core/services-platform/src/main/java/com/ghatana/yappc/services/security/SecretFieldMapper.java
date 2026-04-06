/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Services Platform — Secret Field Mapper
 */
package com.ghatana.yappc.services.security;

import com.ghatana.yappc.infrastructure.security.EncryptionService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Reusable encryption helper for entity mappers that need to protect secret string fields.
 *
 * <p>Centralises the {@code enc::} prefix convention established in {@code YappcEntityMapper}
 * so that lifecycle, approval, and agent-config mappers can apply the same pattern without
 * duplicating the guard logic.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // In any entity mapper:
 * private final SecretFieldMapper secretMapper;
 *
 * // On persist:
 * entity.setApiKey(secretMapper.encrypt(domain.apiKey(), "lifecycle.config.apiKey"));
 *
 * // On load:
 * domain.setApiKey(secretMapper.decrypt(entity.getApiKey(), "lifecycle.config.apiKey"));
 * }</pre>
 *
 * <h2>Format</h2>
 * <p>Encrypted values are stored as {@code enc::<Base64-AES-256-GCM-ciphertext>}.
 * Values that already start with {@code enc::} are returned unchanged (idempotent encrypt).
 * Values without the prefix are treated as plaintext (idempotent decrypt).
 *
 * <h2>Null safety</h2>
 * <p>Both {@link #encrypt} and {@link #decrypt} accept {@code null} and return {@code null},
 * so callers do not need to guard against empty optional fields.
 *
 * @doc.type class
 * @doc.purpose Shared enc:: prefix encryption utility for entity mappers
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class SecretFieldMapper {

    private static final Logger log = LoggerFactory.getLogger(SecretFieldMapper.class);

    static final String ENC_PREFIX = "enc::";

    private final EncryptionService encryptionService;

    /**
     * Creates a {@code SecretFieldMapper} backed by the given encryption service.
     *
     * @param encryptionService AES-256-GCM service loaded with the active encryption key
     */
    public SecretFieldMapper(@NotNull EncryptionService encryptionService) {
        this.encryptionService = Objects.requireNonNull(
                encryptionService, "encryptionService must not be null");
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Encrypts {@code plaintext} and prepends the {@code enc::} prefix.
     *
     * <p>If {@code plaintext} is {@code null}, returns {@code null}.
     * If {@code plaintext} already starts with {@code enc::}, it is returned unchanged
     * (idempotent — prevents double-encryption).
     *
     * @param plaintext  the plaintext value to encrypt
     * @param fieldLabel dotted field path used in log messages (e.g. {@code "lifecycle.apiKey"})
     * @return {@code enc::<ciphertext>}, or {@code null} if input is {@code null}
     */
    public @Nullable String encrypt(@Nullable String plaintext, @NotNull String fieldLabel) {
        if (plaintext == null) {
            return null;
        }
        if (plaintext.startsWith(ENC_PREFIX)) {
            log.debug("Field '{}' is already encrypted — skipping re-encrypt", fieldLabel);
            return plaintext;
        }
        String ciphertext = ENC_PREFIX + encryptionService.encrypt(plaintext);
        log.debug("Encrypted field '{}'", fieldLabel);
        return ciphertext;
    }

    /**
     * Decrypts an {@code enc::}-prefixed value and returns the original plaintext.
     *
     * <p>If {@code stored} is {@code null} or does not start with {@code enc::},
     * the value is returned as-is (idempotent — graceful for un-migrated plaintext).
     *
     * @param stored     the stored value, potentially prefixed with {@code enc::}
     * @param fieldLabel dotted field path used in log messages
     * @return decrypted plaintext, or the original value if it was not encrypted
     */
    public @Nullable String decrypt(@Nullable String stored, @NotNull String fieldLabel) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(ENC_PREFIX)) {
            log.debug("Field '{}' is stored as plaintext — no decryption needed", fieldLabel);
            return stored;
        }
        String ciphertext = stored.substring(ENC_PREFIX.length());
        String plaintext = encryptionService.decrypt(ciphertext);
        log.debug("Decrypted field '{}'", fieldLabel);
        return plaintext;
    }

    /**
     * Returns {@code true} if the stored value has been encrypted (starts with {@code enc::}).
     *
     * @param stored stored field value
     * @return whether the value is encrypted
     */
    public static boolean isEncrypted(@Nullable String stored) {
        return stored != null && stored.startsWith(ENC_PREFIX);
    }
}
