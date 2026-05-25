/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.governance.policy;

import java.util.Set;

/**
 * Encryption policy for sensitive data.
 *
 * <p>Defines which fields should be encrypted and with what algorithm.
 *
 * @doc.type record
 * @doc.purpose Defines encryption requirements for sensitive fields
 * @doc.layer product
 * @doc.pattern Policy
 */
public record EncryptionPolicy(
    String policyId,
    Set<String> encryptedFields,
    EncryptionAlgorithm algorithm,
    EncryptionScope encryptionScope) {

    public enum EncryptionAlgorithm {
        AES_256_GCM,
        RSA_4096,
        CHACHA20_POLY1305
    }

    public enum EncryptionScope {
        FIELD_LEVEL,
        ROW_LEVEL,
        TENANT_LEVEL,
        FULL_ENCRYPTION
    }

    public EncryptionPolicy {
        if (policyId == null || policyId.isBlank()) {
            throw new IllegalArgumentException("policyId must not be blank");
        }
        if (encryptedFields == null) {
            encryptedFields = Set.of();
        }
        if (algorithm == null) {
            algorithm = EncryptionAlgorithm.AES_256_GCM;
        }
        if (encryptionScope == null) {
            encryptionScope = EncryptionScope.FIELD_LEVEL;
        }
    }

    public boolean shouldEncrypt(String field) {
        return encryptedFields.contains(field);
    }

    public boolean isFieldLevelEncryption() {
        return encryptionScope == EncryptionScope.FIELD_LEVEL;
    }

    public boolean isRowLevelEncryption() {
        return encryptionScope == EncryptionScope.ROW_LEVEL;
    }

    public boolean isTenantLevelEncryption() {
        return encryptionScope == EncryptionScope.TENANT_LEVEL;
    }

    public boolean isFullEncryption() {
        return encryptionScope == EncryptionScope.FULL_ENCRYPTION;
    }
}
