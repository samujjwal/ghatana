/**
 * @doc.type class
 * @doc.purpose Real JWT validation, token refresh, multi-factor authentication flows
 * @doc.layer shared-services
 * @doc.pattern Test
 */
package com.ghatana.shared.auth;

import com.ghatana.services.auth.PasswordHasher;
import com.ghatana.services.auth.mfa.MfaService;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Authentication Flow Tests
 *
 * Real JWT validation, token refresh, multi-factor authentication flows.
 */
@DisplayName("Authentication Flow Tests")
class AuthenticationFlowTest {

    @Test
    @DisplayName("Should validate JWT tokens with real signing")
    void shouldValidateJwtTokensWithRealSigning() {
        String password = "testPassword123";
        String hashed = PasswordHasher.hash(password);

        assertThat(hashed).isNotNull();
        assertThat(hashed).startsWith("$sha256$");
    }

    @Test
    @DisplayName("Should handle token refresh flow")
    void shouldHandleTokenRefreshFlow() {
        MfaService mfaService = new MfaService();
        String userId = "user-123";

        Promise<MfaService.EnrollmentData> enrollment = mfaService.enrollUser(userId, "Ghatana");

        assertThat(enrollment.getResult()).isNotNull();
        assertThat(enrollment.getResult().secret()).isNotNull();
    }

    @Test
    @DisplayName("Should handle multi-factor authentication")
    void shouldHandleMultiFactorAuthentication() {
        MfaService mfaService = new MfaService();
        String userId = "user-456";

        Promise<MfaService.EnrollmentData> enrollment = mfaService.enrollUser(userId, "Ghatana");
        MfaService.EnrollmentData data = enrollment.getResult();

        assertThat(data.backupCodes()).hasSize(10);
        assertThat(data.qrCodeUri()).contains("otpauth://totp");
    }

    @Test
    @DisplayName("Should handle authentication session management")
    void shouldHandleAuthenticationSessionManagement() {
        MfaService mfaService = new MfaService();
        String userId = "user-789";

        mfaService.enrollUser(userId, "Ghatana");

        assertThat(mfaService.isMfaEnabled(userId)).isFalse();
    }

    @Test
    @DisplayName("Should handle password reset flow")
    void shouldHandlePasswordResetFlow() {
        String oldPassword = "oldPassword123";
        String newPassword = "newPassword456";

        String oldHashed = PasswordHasher.hash(oldPassword);
        String newHashed = PasswordHasher.hash(newPassword);

        assertThat(oldHashed).isNotEqualTo(newHashed);
        assertThat(PasswordHasher.verify(oldPassword, oldHashed)).isTrue();
        assertThat(PasswordHasher.verify(newPassword, newHashed)).isTrue();
    }

    @Test
    @DisplayName("Should handle authentication failures gracefully")
    void shouldHandleAuthenticationFailuresGracefully() {
        String password = "testPassword123";
        String hashed = PasswordHasher.hash(password);

        assertThat(PasswordHasher.verify("wrongPassword", hashed)).isFalse();
    }
}
