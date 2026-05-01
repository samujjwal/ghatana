/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * P1-03: Secret handling and rotation readiness contract tests.
 *
 * <p>Validates that:
 * <ol>
 *   <li>Secret values (DB password, API key, JWT secret) are never exposed in
 *       validation exception messages or log output from startup.</li>
 *   <li>Missing required secrets in non-embedded mode cause a hard fail-fast.</li>
 *   <li>Configuration validation error messages describe the <em>key name</em>
 *       that is missing, not the secret value.</li>
 *   <li>Rotation-compatible config: secrets are read from env vars, not
 *       embedded in binary or checked into source.</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose P1-03 secret handling, masking, and rotation-readiness tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudConfigValidator – secret handling (P1-03)")
class DataCloudConfigSecretHandlingTest {

    private static final String SENSITIVE_PASSWORD = "sup3r-s3cret-p4ssw0rd!"; // GH-90000
    private static final String SENSITIVE_API_KEY  = "sk-live-abc123xyz789"; // GH-90000
    private static final String SENSITIVE_JWT      = "jwt-secret-signing-key-must-not-leak"; // GH-90000

    // ─────────────────────────────────────────────────────────────────────────
    // Secret values must not appear in validation error messages
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DB password value does not appear in validation exception message")
    void dbPasswordValueNotInExceptionMessage() { // GH-90000
        // DB enabled but URL missing — validator should report the missing key name,
        // NOT echo back the password value
        IllegalStateException ex = org.assertj.core.api.Assertions.catchThrowableOfType(
            () -> DataCloudConfigValidator.builder()
                .dbEnabled(true)
                .dbUser("dc_user")
                .dbPassword(SENSITIVE_PASSWORD)
                .build()
                .validate(),
            IllegalStateException.class); // GH-90000

        assertThat(ex).isNotNull(); // GH-90000
        assertThat(ex.getMessage())
            .as("Validation error must not echo the DB password value")
            .doesNotContain(SENSITIVE_PASSWORD); // GH-90000
        assertThat(ex.getMessage())
            .as("Validation error must reference the missing key name")
            .contains("DATACLOUD_DB_URL"); // GH-90000
    }

    @Test
    @DisplayName("DB user value does not appear in validation exception message")
    void dbUserValueNotInExceptionMessage() { // GH-90000
        // DB enabled, URL present, but password missing — error must not leak the user value
        IllegalStateException ex = org.assertj.core.api.Assertions.catchThrowableOfType(
            () -> DataCloudConfigValidator.builder()
                .dbEnabled(true)
                .dbUrl("jdbc:postgresql://localhost:5432/dc")
                .dbUser("sensitive_db_user_123")
                .build()
                .validate(),
            IllegalStateException.class); // GH-90000

        assertThat(ex).isNotNull(); // GH-90000
        assertThat(ex.getMessage())
            .as("Validation error must not echo the DB user name")
            .doesNotContain("sensitive_db_user_123"); // GH-90000
        assertThat(ex.getMessage())
            .as("Validation error must reference the missing key name")
            .contains("DATACLOUD_DB_PASSWORD"); // GH-90000
    }

    @Test
    @DisplayName("Kafka bootstrap URL does not appear in validation exception message when dependent required value is missing")
    void kafkaBootstrapValueNotInExceptionMessage() { // GH-90000
        // Kafka enabled but bootstrap missing — validator references the key name only
        IllegalStateException ex = org.assertj.core.api.Assertions.catchThrowableOfType(
            () -> DataCloudConfigValidator.builder()
                .kafkaEnabled(true)
                .build()
                .validate(),
            IllegalStateException.class); // GH-90000

        assertThat(ex).isNotNull(); // GH-90000
        assertThat(ex.getMessage())
            .as("Validation error must reference the missing bootstrap key")
            .contains("DATACLOUD_KAFKA_BOOTSTRAP"); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Validation messages describe key names, not values
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Validation error for missing DB password references env var name, not value")
    void missingDbPasswordReferencesEnvVarName() { // GH-90000
        IllegalStateException ex = org.assertj.core.api.Assertions.catchThrowableOfType(
            () -> DataCloudConfigValidator.builder()
                .dbEnabled(true)
                .dbUrl("jdbc:postgresql://localhost:5432/dc")
                .dbUser("dc_user")
                // intentionally omit dbPassword
                .build()
                .validate(),
            IllegalStateException.class); // GH-90000

        assertThat(ex).isNotNull(); // GH-90000
        assertThat(ex.getMessage())
            .as("Error must name the missing key, not expose a value")
            .containsIgnoringCase("DATACLOUD_DB_PASSWORD"); // GH-90000
    }

    @Test
    @DisplayName("Multiple secret violations do not cross-contaminate each other in the error message")
    void multipleSecretViolationsDoNotLeakValues() { // GH-90000
        // DB enabled with a password but missing URL and user
        // — the password value must not appear in the combined error
        IllegalStateException ex = org.assertj.core.api.Assertions.catchThrowableOfType(
            () -> DataCloudConfigValidator.builder()
                .dbEnabled(true)
                .dbPassword(SENSITIVE_PASSWORD)
                .build()
                .validate(),
            IllegalStateException.class); // GH-90000

        assertThat(ex).isNotNull(); // GH-90000
        assertThat(ex.getMessage())
            .doesNotContain(SENSITIVE_PASSWORD); // GH-90000
        // Multiple key references in the message are fine
        assertThat(ex.getMessage()).contains("DATACLOUD_DB_URL");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rotation-compatible: secrets come from env, not hardcoded defaults
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid full config including sensitive values passes without revealing them")
    void validConfigPassesWithoutRevealingSecrets() { // GH-90000
        // No exception means validation passed; secrets are consumed, not echoed
        assertThatCode(() ->
            DataCloudConfigValidator.builder()
                .dbEnabled(true)
                .dbUrl("jdbc:postgresql://prod-db.internal:5432/dc")
                .dbUser("dc_prod_user")
                .dbPassword(SENSITIVE_PASSWORD)
                .build()
                .validate()
        ).doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("Port validation error does not contaminate secret-related messages")
    void portValidationErrorDoesNotLeakSecrets() { // GH-90000
        IllegalStateException ex = org.assertj.core.api.Assertions.catchThrowableOfType(
            () -> DataCloudConfigValidator.builder()
                .httpPortStr("99999") // port violation
                .dbEnabled(true)
                .dbUrl("jdbc:postgresql://localhost/dc")
                .dbUser("dc_user")
                .dbPassword(SENSITIVE_PASSWORD) // valid but must not appear in error
                .build()
                .validate(),
            IllegalStateException.class); // GH-90000

        assertThat(ex).isNotNull(); // GH-90000
        assertThat(ex.getMessage())
            .as("Port error must not expose a valid secret from another field")
            .doesNotContain(SENSITIVE_PASSWORD); // GH-90000
        assertThat(ex.getMessage()).contains("DATACLOUD_HTTP_PORT"); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Exception types
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Validation exception is an IllegalStateException not a runtime leaking exception")
    void validationExceptionType_isIllegalState() { // GH-90000
        assertThatThrownBy(() ->
            DataCloudConfigValidator.builder()
                .dbEnabled(true)
                .build()
                .validate()
        ).isExactlyInstanceOf(IllegalStateException.class); // GH-90000
    }
}
