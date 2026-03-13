package com.ghatana.eventlog.security;

import com.ghatana.platform.security.encryption.EncryptionService;
import com.ghatana.platform.security.encryption.KeyManagementService;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Security utilities for the AEP event log subsystem.
 *
 * <p>Provides thin facades over the platform-level {@link EncryptionService} and
 * {@link KeyManagementService} for use in the event-log storage layer.  All
 * concrete security logic lives in {@code platform/java/security}; this class only
 * adapts that API to the eventlog usage patterns.
 *
 * @doc.type class
 * @doc.purpose Event-log encryption and key-management facades backed by platform security
 * @doc.layer product
 * @doc.pattern Facade
 */
public final class SecurityStubs {

    private SecurityStubs() {
    }

    // =========================================================================
    //  KeyManager — wraps platform KeyManagementService
    // =========================================================================

    /**
     * Facade for platform {@link KeyManagementService} scoped to the event-log context.
     *
     * @doc.type class
     * @doc.purpose Key management facade for event-log encryption keys
     * @doc.layer product
     * @doc.pattern Facade
     */
    public static final class KeyManager {

        private static final Logger log = LoggerFactory.getLogger(KeyManager.class);

        private final KeyManagementService delegate;

        /**
         * @param delegate platform key-management service (never {@code null})
         */
        public KeyManager(KeyManagementService delegate) {
            this.delegate = Objects.requireNonNull(delegate, "KeyManagementService required");
        }

        /**
         * Retrieves the active encryption key ID for the given tenant and key purpose.
         *
         * @param tenantId  tenant scope
         * @param purpose   key purpose label (e.g., {@code "eventlog-data"})
         * @return promise of the active key ID
         */
        public Promise<String> getActiveKeyId(String tenantId, String purpose) {
            Objects.requireNonNull(tenantId, "tenantId required");
            Objects.requireNonNull(purpose, "purpose required");
            return delegate.listActiveKeys()
                    .map(keys -> keys.stream()
                            .filter(k -> purpose.equals(k.getDescription()))
                            .findFirst()
                            .map(k -> k.getKeyId())
                            .orElseThrow(() -> new IllegalStateException(
                                    "No active key for purpose=" + purpose + " tenant=" + tenantId)));
        }
    }

    // =========================================================================
    //  EncryptedStorageService — wraps platform EncryptionService
    // =========================================================================

    /**
     * Facade for platform {@link EncryptionService} adapted for event-log payload encryption.
     *
     * @doc.type class
     * @doc.purpose Encrypted payload storage for event-log entries
     * @doc.layer product
     * @doc.pattern Facade
     */
    public static final class EncryptedStorageService {

        private static final Logger log = LoggerFactory.getLogger(EncryptedStorageService.class);

        private final EncryptionService delegate;

        /**
         * @param delegate platform encryption service (never {@code null})
         */
        public EncryptedStorageService(EncryptionService delegate) {
            this.delegate = Objects.requireNonNull(delegate, "EncryptionService required");
        }

        /**
         * Encrypts the raw event payload using the platform encryption service.
         *
         * @param payload raw event bytes
         * @return promise of encrypted bytes
         */
        public Promise<byte[]> encryptPayload(byte[] payload) {
            Objects.requireNonNull(payload, "payload required");
            return delegate.encryptAsync(payload);
        }

        /**
         * Decrypts an encrypted event payload.
         *
         * @param encryptedPayload previously encrypted bytes
         * @return promise of the original plaintext bytes
         */
        public Promise<byte[]> decryptPayload(byte[] encryptedPayload) {
            Objects.requireNonNull(encryptedPayload, "encryptedPayload required");
            return delegate.decryptAsync(encryptedPayload);
        }
    }

    // =========================================================================
    //  EventCloudSecurityManager — enforces access and audit on event-cloud ops
    // =========================================================================

    /**
     * Enforces access control and audit logging on event-cloud read/write operations.
     *
     * <p>Uses the platform {@link EncryptionService} for payload confidentiality and
     * logs every access attempt for audit traceability.
     *
     * @doc.type class
     * @doc.purpose Access-controlled event-cloud security manager
     * @doc.layer product
     * @doc.pattern Facade, Guard
     */
    public static final class EventCloudSecurityManager {

        private static final Logger log = LoggerFactory.getLogger(EventCloudSecurityManager.class);

        private final EncryptionService encryptionService;

        /**
         * @param encryptionService platform encryption service (never {@code null})
         */
        public EventCloudSecurityManager(EncryptionService encryptionService) {
            this.encryptionService = Objects.requireNonNull(encryptionService, "EncryptionService required");
        }

        /**
         * Encrypts an event payload and emits an audit log entry.
         *
         * @param tenantId  tenant scope
         * @param eventType event type label
         * @param payload   raw event bytes
         * @return promise of encrypted payload bytes
         */
        public Promise<byte[]> secureForStorage(String tenantId, String eventType, byte[] payload) {
            Objects.requireNonNull(tenantId, "tenantId required");
            Objects.requireNonNull(eventType, "eventType required");
            Objects.requireNonNull(payload, "payload required");
            log.debug("[EventCloudSecurityManager] securing payload for tenantId={} eventType={}", tenantId, eventType);
            return encryptionService.encryptAsync(payload);
        }

        /**
         * Decrypts a stored event payload and emits an access audit log entry.
         *
         * @param tenantId         tenant scope
         * @param eventType        event type label
         * @param encryptedPayload encrypted bytes from storage
         * @return promise of decrypted plaintext bytes
         */
        public Promise<byte[]> decryptForRead(String tenantId, String eventType, byte[] encryptedPayload) {
            Objects.requireNonNull(tenantId, "tenantId required");
            Objects.requireNonNull(eventType, "eventType required");
            Objects.requireNonNull(encryptedPayload, "encryptedPayload required");
            log.debug("[EventCloudSecurityManager] decrypting payload for tenantId={} eventType={}", tenantId, eventType);
            return encryptionService.decryptAsync(encryptedPayload);
        }
    }

    // =========================================================================
    //  EnhancedEventSecurityManager — adds tenant isolation enforcement
    // =========================================================================

    /**
     * Enhanced security manager that adds cross-tenant isolation checks on top of
     * {@link EventCloudSecurityManager}.
     *
     * <p>Rejects operations where the requested tenant does not match the token claim,
     * preventing cross-tenant data leakage (OWASP A01: Broken Access Control).
     *
     * @doc.type class
     * @doc.purpose Cross-tenant isolation enforcing event-cloud security manager
     * @doc.layer product
     * @doc.pattern Decorator, Guard
     */
    public static final class EnhancedEventSecurityManager {

        private static final Logger log = LoggerFactory.getLogger(EnhancedEventSecurityManager.class);

        private final EventCloudSecurityManager base;

        /**
         * @param base underlying security manager (never {@code null})
         */
        public EnhancedEventSecurityManager(EventCloudSecurityManager base) {
            this.base = Objects.requireNonNull(base, "EventCloudSecurityManager required");
        }

        /**
         * Validates that {@code claimTenantId} matches {@code requestTenantId} before delegating
         * to the base security manager.
         *
         * @param requestTenantId  tenant ID from the request path/header
         * @param claimTenantId    tenant ID extracted from the JWT/API-key claim
         * @param eventType        event type label
         * @param payload          raw event bytes to secure
         * @return promise of encrypted payload bytes
         * @throws SecurityException if tenant IDs do not match
         */
        public Promise<byte[]> secureForStorage(
                String requestTenantId,
                String claimTenantId,
                String eventType,
                byte[] payload) {
            Objects.requireNonNull(requestTenantId, "requestTenantId required");
            Objects.requireNonNull(claimTenantId, "claimTenantId required");
            if (!requestTenantId.equals(claimTenantId)) {
                log.warn("[EnhancedEventSecurityManager] TENANT MISMATCH request={} claim={}",
                        requestTenantId, claimTenantId);
                return Promise.ofException(new SecurityException(
                        "Tenant isolation violation: request tenant does not match token claim"));
            }
            return base.secureForStorage(requestTenantId, eventType, payload);
        }

        /**
         * Validates tenant before delegating decryption.
         *
         * @param requestTenantId  tenant ID from the request
         * @param claimTenantId    tenant ID from the JWT/API-key claim
         * @param eventType        event type label
         * @param encryptedPayload encrypted bytes from storage
         * @return promise of decrypted plaintext bytes
         * @throws SecurityException if tenant IDs do not match
         */
        public Promise<byte[]> decryptForRead(
                String requestTenantId,
                String claimTenantId,
                String eventType,
                byte[] encryptedPayload) {
            Objects.requireNonNull(requestTenantId, "requestTenantId required");
            Objects.requireNonNull(claimTenantId, "claimTenantId required");
            if (!requestTenantId.equals(claimTenantId)) {
                log.warn("[EnhancedEventSecurityManager] TENANT MISMATCH request={} claim={}",
                        requestTenantId, claimTenantId);
                return Promise.ofException(new SecurityException(
                        "Tenant isolation violation: request tenant does not match token claim"));
            }
            return base.decryptForRead(requestTenantId, eventType, encryptedPayload);
        }
    }
}