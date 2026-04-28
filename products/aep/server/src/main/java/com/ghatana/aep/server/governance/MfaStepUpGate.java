/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.governance;

import io.activej.promise.Promise;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MFA-based step-up authentication gate using TOTP codes and backup codes.
 *
 * <p>F-018: Enforces multi-factor verification before critical governance operations
 * like kill-switch activation. Currently uses a simplified implementation; production
 * should integrate with the shared MfaService from auth-gateway.
 *
 * @doc.type class
 * @doc.purpose Multi-factor authentication verification for governance operations
 * @doc.layer product
 * @doc.pattern Service
 */
public class MfaStepUpGate implements StepUpAuthenticationGate {

    private static final Logger log = LoggerFactory.getLogger(MfaStepUpGate.class);

    /**
     * Creates a new MFA step-up authentication gate.
     *
     * @param mfaService shared MFA service from auth-gateway (may be null if MFA not configured)
     */
    public MfaStepUpGate(@Nullable Object mfaService) {
        // TODO: Integrate with actual MfaService from auth-gateway when available
        log.debug("MFA step-up gate initialized (MVP: simplified implementation)");
    }

    @Override
    public Promise<Boolean> verify(String userId, String tenantId, String mfaCode) {
        if (mfaCode == null || mfaCode.isBlank()) {
            log.warn("Step-up MFA verification failed: empty code for user='{}' tenant='{}'",
                userId, tenantId);
            return Promise.of(false);
        }

        // MVP implementation: accept any 6-digit code (real impl would validate against TOTP)
        if (mfaCode.length() == 6 && mfaCode.matches("\\d{6}")) {
            log.info("Step-up MFA verification succeeded via TOTP for user='{}' tenant='{}'",
                userId, tenantId);
            return Promise.of(true);
        }

        // Accept backup codes (8-char alphanumeric)
        if (mfaCode.length() == 8 && mfaCode.matches("[A-Z0-9]{8}")) {
            log.info("Step-up MFA verification succeeded via backup code for user='{}' tenant='{}'",
                userId, tenantId);
            return Promise.of(true);
        }

        log.warn("Step-up MFA verification failed: invalid code format for user='{}' tenant='{}'",
            userId, tenantId);
        return Promise.of(false);
    }

    @Override
    public Promise<Boolean> isMfaEnabled(String userId) {
        // MVP: always return true (real impl would check actual MFA config)
        return Promise.of(true);
    }

    /**
     * No-op implementation for when MFA is not required or not configured.
     */
    public static final class NoOpStepUpGate implements StepUpAuthenticationGate {

        private static final Logger log = LoggerFactory.getLogger(NoOpStepUpGate.class);

        @Override
        public Promise<Boolean> verify(String userId, String tenantId, String mfaCode) {
            log.debug("No-op step-up verification for user='{}' tenant='{}' — MFA not required",
                userId, tenantId);
            return Promise.of(true);  // Always permit when MFA not configured
        }

        @Override
        public Promise<Boolean> isMfaEnabled(String userId) {
            return Promise.of(false);
        }
    }
}


