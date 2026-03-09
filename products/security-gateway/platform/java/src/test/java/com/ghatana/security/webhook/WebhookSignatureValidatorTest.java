package com.ghatana.security.webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for WebhookSignatureValidator.
 *
 * Tests validate: - Valid HMAC-SHA256 signature verification - Invalid
 * signatures are rejected - Malformed signatures (missing prefix) are rejected
 * - Null/empty inputs are rejected - Constructor validation
 */
@DisplayName("Webhook Signature Validator Tests")
class WebhookSignatureValidatorTest {

    private static final String SECRET_KEY = "my-secret-key-with-sufficient-length";
    private static final String TEST_PAYLOAD = "{\"event\": \"order.created\", \"orderId\": \"12345\"}";

    private WebhookSignatureValidator validator;

    @BeforeEach
    void setUp() {
        // GIVEN: A validator with a known secret key
        validator = new WebhookSignatureValidator(SECRET_KEY);
    }

    @Nested
    @DisplayName("Valid Signature Tests")
    class ValidSignatureTests {

        /**
         * Verifies that a valid HMAC-SHA256 signature is accepted.
         *
         * GIVEN: A payload and its valid signature WHEN: validate() is called
         * THEN: Returns true
         */
        @Test
        @DisplayName("Should accept valid HMAC-SHA256 signature")
        void shouldAcceptValidSignature() {
            // GIVEN: A valid signature for the test payload
            String validSignature = computeSignature(TEST_PAYLOAD);

            // WHEN: We validate the payload with the correct signature
            boolean isValid = validator.validate(TEST_PAYLOAD, validSignature);

            // THEN: The signature should be valid
            assertThat(isValid)
                    .as("Valid signature should be accepted")
                    .isTrue();
        }

        /**
         * Verifies that signatures work correctly for different payloads.
         *
         * GIVEN: Different payloads WHEN: Each payload is signed and validated
         * THEN: Each combination should be valid
         */
        @Test
        @DisplayName("Should accept signatures for different payloads")
        void shouldAcceptSignaturesForDifferentPayloads() {
            // GIVEN: Multiple payloads (non-empty, valid JSON)
            String[] payloads = {
                "{}",
                "{\"event\": \"simple\"}",
                "{\"nested\": {\"data\": {\"value\": 123}}}"
            };

            // WHEN: Each payload is signed and validated
            for (String payload : payloads) {
                String signature = computeSignature(payload);

                // THEN: Each signature should be valid
                assertThat(validator.validate(payload, signature))
                        .as("Signature for payload: " + payload)
                        .isTrue();
            }
        }
    }

    @Nested
    @DisplayName("Invalid Signature Tests")
    class InvalidSignatureTests {

        /**
         * Verifies that an invalid signature is rejected.
         *
         * GIVEN: A payload and an invalid signature WHEN: validate() is called
         * THEN: Returns false
         */
        @Test
        @DisplayName("Should reject invalid signature")
        void shouldRejectInvalidSignature() {
            // GIVEN: An invalid signature (random hex value)
            String invalidSignature = "sha256=0000000000000000000000000000000000000000000000000000000000000000";

            // WHEN: We validate with the invalid signature
            boolean isValid = validator.validate(TEST_PAYLOAD, invalidSignature);

            // THEN: The signature should be invalid
            assertThat(isValid)
                    .as("Invalid signature should be rejected")
                    .isFalse();
        }

        /**
         * Verifies that a signature for different payload is rejected.
         *
         * GIVEN: A signature for one payload WHEN: We try to validate a
         * different payload with that signature THEN: Returns false
         */
        @Test
        @DisplayName("Should reject signature for different payload")
        void shouldRejectSignatureForDifferentPayload() {
            // GIVEN: A valid signature for one payload
            String payload1 = "{\"event\": \"order.created\"}";
            String payload2 = "{\"event\": \"order.updated\"}";
            String signature = computeSignature(payload1);

            // WHEN: We try to validate payload2 with payload1's signature
            boolean isValid = validator.validate(payload2, signature);

            // THEN: The signature should be invalid
            assertThat(isValid)
                    .as("Signature for different payload should be rejected")
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("Malformed Signature Tests")
    class MalformedSignatureTests {

        /**
         * Verifies that a signature without the "sha256=" prefix is rejected.
         *
         * GIVEN: A valid hex value but without the sha256= prefix WHEN:
         * validate() is called THEN: Returns false
         */
        @Test
        @DisplayName("Should reject signature without sha256 prefix")
        void shouldRejectSignatureWithoutPrefix() {
            // GIVEN: A signature without the sha256= prefix
            String signatureWithoutPrefix = "0000000000000000000000000000000000000000000000000000000000000000";

            // WHEN: We try to validate with this signature
            boolean isValid = validator.validate(TEST_PAYLOAD, signatureWithoutPrefix);

            // THEN: The signature should be invalid
            assertThat(isValid)
                    .as("Signature without sha256= prefix should be rejected")
                    .isFalse();
        }

        /**
         * Verifies that a signature with wrong prefix is rejected.
         *
         * GIVEN: A signature with wrong hash algorithm prefix WHEN: validate()
         * is called THEN: Returns false
         */
        @Test
        @DisplayName("Should reject signature with wrong prefix")
        void shouldRejectSignatureWithWrongPrefix() {
            // GIVEN: A signature with sha1= instead of sha256=
            String signatureWithWrongPrefix = "sha1=0000000000000000000000000000000000000000";

            // WHEN: We try to validate with this signature
            boolean isValid = validator.validate(TEST_PAYLOAD, signatureWithWrongPrefix);

            // THEN: The signature should be invalid
            assertThat(isValid)
                    .as("Signature with wrong prefix should be rejected")
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("Null/Empty Input Tests")
    class NullEmptyInputTests {

        /**
         * Verifies that null payload throws exception.
         *
         * GIVEN: null payload WHEN: validate() is called THEN: Throws
         * IllegalArgumentException
         */
        @Test
        @DisplayName("Should throw exception for null payload")
        void shouldThrowExceptionForNullPayload() {
            // GIVEN: A valid signature (doesn't matter, we'll fail before using it)
            String validSignature = "sha256=abc123";

            // WHEN: We try to validate with null payload
            // THEN: IllegalArgumentException should be thrown
            assertThatThrownBy(() -> validator.validate(null, validSignature))
                    .as("null payload should throw IllegalArgumentException")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Payload cannot be null or empty");
        }

        /**
         * Verifies that null signature throws exception.
         *
         * GIVEN: null signature WHEN: validate() is called THEN: Throws
         * IllegalArgumentException
         */
        @Test
        @DisplayName("Should throw exception for null signature")
        void shouldThrowExceptionForNullSignature() {
            // WHEN: We try to validate with null signature
            // THEN: IllegalArgumentException should be thrown
            assertThatThrownBy(() -> validator.validate(TEST_PAYLOAD, null))
                    .as("null signature should throw IllegalArgumentException")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Signature cannot be null or empty");
        }

        /**
         * Verifies that empty payload throws exception.
         *
         * GIVEN: empty payload string WHEN: validate() is called THEN: Throws
         * IllegalArgumentException
         */
        @Test
        @DisplayName("Should throw exception for empty payload")
        void shouldThrowExceptionForEmptyPayload() {
            // GIVEN: An empty payload
            String emptyPayload = "";
            String validSignature = "sha256=abc123";

            // WHEN: We try to validate with empty payload
            // THEN: IllegalArgumentException should be thrown
            assertThatThrownBy(() -> validator.validate(emptyPayload, validSignature))
                    .as("empty payload should throw IllegalArgumentException")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Payload cannot be null or empty");
        }

        /**
         * Verifies that empty signature throws exception.
         *
         * GIVEN: empty signature string WHEN: validate() is called THEN: Throws
         * IllegalArgumentException
         */
        @Test
        @DisplayName("Should throw exception for empty signature")
        void shouldThrowExceptionForEmptySignature() {
            // GIVEN: An empty signature
            String emptySignature = "";

            // WHEN: We try to validate with empty signature
            // THEN: IllegalArgumentException should be thrown
            assertThatThrownBy(() -> validator.validate(TEST_PAYLOAD, emptySignature))
                    .as("empty signature should throw IllegalArgumentException")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Signature cannot be null or empty");
        }
    }

    @Nested
    @DisplayName("Constructor Validation Tests")
    class ConstructorValidationTests {

        /**
         * Verifies that null secret key throws exception.
         *
         * GIVEN: null secret key WHEN: WebhookSignatureValidator is constructed
         * THEN: Throws IllegalArgumentException
         */
        @Test
        @DisplayName("Should throw exception for null secret key")
        void shouldThrowExceptionForNullSecretKey() {
            // WHEN: We try to create validator with null key
            // THEN: IllegalArgumentException should be thrown
            assertThatThrownBy(() -> new WebhookSignatureValidator(null))
                    .as("null secret key should throw IllegalArgumentException")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Secret key cannot be null or empty");
        }

        /**
         * Verifies that empty secret key throws exception.
         *
         * GIVEN: empty secret key WHEN: WebhookSignatureValidator is
         * constructed THEN: Throws IllegalArgumentException
         */
        @Test
        @DisplayName("Should throw exception for empty secret key")
        void shouldThrowExceptionForEmptySecretKey() {
            // WHEN: We try to create validator with empty key
            // THEN: IllegalArgumentException should be thrown
            assertThatThrownBy(() -> new WebhookSignatureValidator(""))
                    .as("empty secret key should throw IllegalArgumentException")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Secret key cannot be null or empty");
        }

        /**
         * Verifies that validators with different secret keys are independent.
         *
         * GIVEN: Two validators with different secret keys WHEN: A signature
         * from one is validated against the other THEN: Validation fails
         */
        @Test
        @DisplayName("Should reject signature from different secret key")
        void shouldRejectSignatureFromDifferentSecretKey() {
            // GIVEN: Two validators with different secret keys
            String secretKey1 = "secret-key-1";
            String secretKey2 = "secret-key-2";
            WebhookSignatureValidator validator1 = new WebhookSignatureValidator(secretKey1);
            WebhookSignatureValidator validator2 = new WebhookSignatureValidator(secretKey2);

            // AND: A signature created with key1
            String signature = computeSignatureWithKey(TEST_PAYLOAD, secretKey1);

            // WHEN: We try to validate with key2
            boolean isValid = validator2.validate(TEST_PAYLOAD, signature);

            // THEN: The signature should be invalid
            assertThat(isValid)
                    .as("Signature from different key should be rejected")
                    .isFalse();
        }
    }

    // Helper methods
    /**
     * Computes an HMAC-SHA256 signature for the given payload using the
     * validator's secret key.
     *
     * @param payload the payload to sign
     * @return the signature in format "sha256=hexvalue"
     */
    private String computeSignature(String payload) {
        return computeSignatureWithKey(payload, SECRET_KEY);
    }

    /**
     * Computes an HMAC-SHA256 signature for the given payload using the given
     * secret key.
     *
     * @param payload the payload to sign
     * @param secretKey the secret key to use
     * @return the signature in format "sha256=hexvalue"
     */
    private String computeSignatureWithKey(String payload, String secretKey) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(
                    secretKey.getBytes(), 0, secretKey.length(), "HmacSHA256"
            );
            mac.init(keySpec);
            byte[] signatureBytes = mac.doFinal(payload.getBytes());
            String hexSignature = HexFormat.of().withLowerCase().formatHex(signatureBytes);
            return "sha256=" + hexSignature;
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute signature", e);
        }
    }
}
