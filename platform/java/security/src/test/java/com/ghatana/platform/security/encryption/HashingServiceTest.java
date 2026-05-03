package com.ghatana.platform.security.encryption;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for HashingService.
 *
 * @doc.type test
 * @doc.purpose HashingService unit tests
 * @doc.layer core
 */
@DisplayName("HashingService Tests")
class HashingServiceTest extends EventloopTestBase {

    private static final String TEST_SALT = "test-salt-for-hmac-sha256";

    @Test
    @DisplayName("Should hash contact point consistently")
    void shouldHashContactPointConsistently() {
        HashingService service = new HashingService(TEST_SALT, eventloop());
        String email = "test@example.com";

        String hash1 = runPromise(() -> service.hashContactPoint(email));
        String hash2 = runPromise(() -> service.hashContactPoint(email));

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).isNotEmpty();
        assertThat(hash1).hasSize(64); // SHA256 produces 64 hex characters
    }

    @Test
    @DisplayName("Should produce different hashes for different inputs")
    void shouldProduceDifferentHashesForDifferentInputs() {
        HashingService service = new HashingService(TEST_SALT, eventloop());
        String email1 = "test@example.com";
        String email2 = "different@example.com";

        String hash1 = runPromise(() -> service.hashContactPoint(email1));
        String hash2 = runPromise(() -> service.hashContactPoint(email2));

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("Should verify contact point hash correctly")
    void shouldVerifyContactPointHashCorrectly() {
        HashingService service = new HashingService(TEST_SALT, eventloop());
        String email = "test@example.com";

        String hash = runPromise(() -> service.hashContactPoint(email));
        boolean isValid = runPromise(() -> service.verifyContactPoint(email, hash));

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Should reject invalid contact point hash")
    void shouldRejectInvalidContactPointHash() {
        HashingService service = new HashingService(TEST_SALT, eventloop());
        String email = "test@example.com";
        String wrongHash = "wronghashvalue";

        boolean isValid = runPromise(() -> service.verifyContactPoint(email, wrongHash));

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should throw exception for null salt")
    void shouldThrowExceptionForNullSalt() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new HashingService(null, eventloop())
        );
    }

    @Test
    @DisplayName("Should throw exception for blank salt")
    void shouldThrowExceptionForBlankSalt() {
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> new HashingService("", eventloop())
        );
    }

    @Test
    @DisplayName("Should return correct algorithm name")
    void shouldReturnCorrectAlgorithmName() {
        HashingService service = new HashingService(TEST_SALT, eventloop());
        
        assertThat(service.getAlgorithm()).isEqualTo("HmacSHA256");
    }
}
