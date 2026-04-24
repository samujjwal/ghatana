/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.services.auth.mfa;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose MFA edge-case contract tests with real TOTP validation
 * @doc.layer shared-services
 * @doc.pattern Test
 */
@DisplayName("MFA Edge Case Contract Tests")
class MfaEdgeCaseContractTest extends EventloopTestBase {

    private MfaService mfaService;

    @BeforeEach
    void setUp() {
        mfaService = new MfaService();
    }

    @Test
    @DisplayName("enrollment verification succeeds with a real generated TOTP")
    void shouldVerifyEnrollmentWithRealTotp() {
        String userId = "mfa-real-totp";
        MfaService.EnrollmentData enrollment = runPromise(() -> mfaService.enrollUser(userId, "Ghatana"));

        String code = generateTotp(enrollment.secret(), Instant.now().getEpochSecond() / 30);
        boolean verified = runPromise(() -> mfaService.verifyEnrollment(userId, code));

        assertThat(verified).isTrue();
        assertThat(mfaService.isMfaEnabled(userId)).isTrue();
    }

    @Test
    @DisplayName("backup code usage clears failed attempt lock state")
    void shouldClearFailedAttemptsAfterBackupCode() {
        String userId = "mfa-clear-failures";
        MfaService.EnrollmentData enrollment = runPromise(() -> mfaService.enrollUser(userId, "Ghatana"));

        String enableCode = generateTotp(enrollment.secret(), Instant.now().getEpochSecond() / 30);
        assertThat(runPromise(() -> mfaService.verifyEnrollment(userId, enableCode))).isTrue();

        for (int i = 0; i < 3; i++) {
            assertThat(runPromise(() -> mfaService.validateCode(userId, "000000"))).isFalse();
        }

        String backupCode = enrollment.backupCodes()[0];
        assertThat(runPromise(() -> mfaService.validateBackupCode(userId, backupCode))).isTrue();

        String currentCode = generateTotp(enrollment.secret(), Instant.now().getEpochSecond() / 30);
        assertThat(runPromise(() -> mfaService.validateCode(userId, currentCode))).isTrue();
    }

    @Test
    @DisplayName("more than five wrong TOTP attempts are blocked")
    void shouldBlockAfterTooManyFailedAttempts() {
        String userId = "mfa-rate-limit";
        MfaService.EnrollmentData enrollment = runPromise(() -> mfaService.enrollUser(userId, "Ghatana"));

        String enableCode = generateTotp(enrollment.secret(), Instant.now().getEpochSecond() / 30);
        assertThat(runPromise(() -> mfaService.verifyEnrollment(userId, enableCode))).isTrue();

        for (int i = 0; i < 6; i++) {
            assertThat(runPromise(() -> mfaService.validateCode(userId, "111111"))).isFalse();
        }

        String validNow = generateTotp(enrollment.secret(), Instant.now().getEpochSecond() / 30);
        assertThat(runPromise(() -> mfaService.validateCode(userId, validNow))).isFalse();
    }

    private static String generateTotp(String base64Secret, long timestep) {
        try {
            byte[] secretBytes = Base64.getDecoder().decode(base64Secret);
            byte[] counterBytes = ByteBuffer.allocate(8).putLong(timestep).array();

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA1"));
            byte[] hash = mac.doFinal(counterBytes);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);

            int otp = binary % 1_000_000;
            return String.format("%06d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate TOTP code for test", e);
        }
    }
}
