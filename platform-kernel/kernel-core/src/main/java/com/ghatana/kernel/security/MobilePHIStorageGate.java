/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.security;

import io.activej.promise.Promise;

import java.util.Map;
import java.util.Set;

/**
 * Kernel-level mobile PHI offline storage compliance gate.
 *
 * <p>Validates that mobile applications properly handle PHI (Protected Health Information)
 * in offline storage scenarios. This gate ensures:</p>
 * <ul>
 *   <li>PHI is encrypted at rest using hardware-backed secure storage</li>
 *   <li>Keys are managed with rotation and access policies</li>
 *   <li>Tamper detection and integrity checks are in place</li>
 *   <li>PHI cache is cleared on consent revocation, logout, and session expiry</li>
 *   <li>No direct AsyncStorage writes of PHI outside encrypted adapter</li>
 * </ul>
 *
 * <p>This gate is used in Kernel release checks to ensure mobile apps comply
 * with healthcare data protection requirements before production deployment.</p>
 *
 * @doc.type interface
 * @doc.purpose Kernel-level mobile PHI offline storage compliance validation
 * @doc.layer core
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public interface MobilePHIStorageGate {

    /**
     * Validates mobile PHI storage compliance for a product.
     *
     * @param productId the product identifier
     * @param mobileConfig the mobile storage configuration
     * @return Promise containing the validation result
     */
    Promise<MobilePHIStorageValidationResult> validateStorage(String productId, MobileStorageConfig mobileConfig);

    /**
     * Checks if encrypted storage adapter is properly integrated.
     *
     * @param productId the product identifier
     * @return Promise containing true if encrypted storage is properly integrated
     */
    Promise<Boolean> hasEncryptedStorageAdapter(String productId);

    /**
     * Validates key management policies.
     *
     * @param productId the product identifier
     * @return Promise containing the key management validation result
     */
    Promise<KeyManagementValidationResult> validateKeyManagement(String productId);

    /**
     * Checks PHI cache clearing policies.
     *
     * @param productId the product identifier
     * @return Promise containing the cache clearing validation result
     */
    Promise<CacheClearingValidationResult> validateCacheClearing(String productId);

    /**
     * Mobile storage configuration.
     */
    record MobileStorageConfig(
            String productId,
            String storageAdapter,
            String keyStorageType,
            String encryptionAlgorithm,
            int keyRotationDays,
            boolean biometricAuthRequired,
            boolean tamperDetectionEnabled,
            Set<String> allowedStoragePaths) {
        public MobileStorageConfig {
            if (productId == null || productId.isBlank()) {
                throw new IllegalArgumentException("productId must not be blank");
            }
            if (storageAdapter == null || storageAdapter.isBlank()) {
                throw new IllegalArgumentException("storageAdapter must not be blank");
            }
            if (keyStorageType == null || keyStorageType.isBlank()) {
                throw new IllegalArgumentException("keyStorageType must not be blank");
            }
            if (encryptionAlgorithm == null || encryptionAlgorithm.isBlank()) {
                throw new IllegalArgumentException("encryptionAlgorithm must not be blank");
            }
            if (keyRotationDays <= 0) {
                throw new IllegalArgumentException("keyRotationDays must be positive");
            }
            if (allowedStoragePaths == null) {
                allowedStoragePaths = Set.of();
            }
        }
    }

    /**
     * Mobile PHI storage validation result.
     */
    record MobilePHIStorageValidationResult(
            boolean compliant,
            String productId,
            Set<StorageViolation> violations,
            Map<String, String> metadata) {
        public MobilePHIStorageValidationResult {
            if (productId == null || productId.isBlank()) {
                throw new IllegalArgumentException("productId must not be blank");
            }
            if (violations == null) {
                violations = Set.of();
            }
            if (metadata == null) {
                metadata = Map.of();
            }
        }
    }

    /**
     * Storage violation type.
     */
    record StorageViolation(
            String code,
            String description,
            String filePath,
            Severity severity) {
        public StorageViolation {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("code must not be blank");
            }
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("description must not be blank");
            }
        }
    }

    /**
     * Violation severity.
     */
    enum Severity {
        /** Critical - blocks release */
        CRITICAL,
        /** High - should be fixed before release */
        HIGH,
        /** Medium - should be fixed in next release */
        MEDIUM,
        /** Low - informational */
        LOW
    }

    /**
     * Key management validation result.
     */
    record KeyManagementValidationResult(
            boolean compliant,
            boolean rotationEnabled,
            int rotationDays,
            boolean hardwareBacked,
            boolean biometricPolicyEnabled,
            Set<String> violations) {
        public KeyManagementValidationResult {
            if (violations == null) {
                violations = Set.of();
            }
        }
    }

    /**
     * Cache clearing validation result.
     */
    record CacheClearingValidationResult(
            boolean compliant,
            boolean clearsOnConsentRevoke,
            boolean clearsOnLogout,
            boolean clearsOnSessionExpiry,
            boolean clearsOnRoleChange,
            Set<String> violations) {
        public CacheClearingValidationResult {
            if (violations == null) {
                violations = Set.of();
            }
        }
    }

    /**
     * Standard violation codes.
     */
    interface ViolationCodes {
        String DIRECT_ASYNCSTORAGE_PHI = "DIRECT_ASYNCSTORAGE_PHI";
        String NO_ENCRYPTION = "NO_ENCRYPTION";
        String WEAK_ENCRYPTION = "WEAK_ENCRYPTION";
        String NO_KEY_ROTATION = "NO_KEY_ROTATION";
        String NO_HARDWARE_BACKING = "NO_HARDWARE_BACKING";
        String NO_TAMPER_DETECTION = "NO_TAMPER_DETECTION";
        String NO_CACHE_CLEARING = "NO_CACHE_CLEARING";
        String NO_CONSENT_CLEARING = "NO_CONSENT_CLEARING";
        String NO_BIOMETRIC_POLICY = "NO_BIOMETRIC_POLICY";
        String INSECURE_KEY_STORAGE = "INSECURE_KEY_STORAGE";
    }

    /**
     * Standard key storage types.
     */
    interface KeyStorageTypes {
        String SECURE_STORE = "SECURE_STORE";
        String KEYCHAIN = "KEYCHAIN";
        String KEYSTORE = "KEYSTORE";
        String ENCRYPTED_SHARED_PREFS = "ENCRYPTED_SHARED_PREFS";
    }

    /**
     * Standard encryption algorithms.
     */
    interface EncryptionAlgorithms {
        String AES_256_GCM = "AES-256-GCM";
        String AES_256_CBC = "AES-256-CBC";
        String CHACHA20_POLY1305 = "ChaCha20-Poly1305";
    }
}
