/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.kernel.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link SecretProvider} SPI and {@link EnvironmentSecretProvider}.
 */
@DisplayName("SecretProvider SPI")
class SecretProviderTest {

    // ─── EnvironmentSecretProvider ────────────────────────────────────────────

    @Test
    @DisplayName("INSTANCE singleton is non-null")
    void instanceIsNotNull() { // GH-90000
        assertThat(EnvironmentSecretProvider.INSTANCE).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("returns empty when the env var is not set")
    void returnsEmptyForMissingVar() { // GH-90000
        // Use a name that is extremely unlikely to exist in any real environment
        Optional<String> value = EnvironmentSecretProvider.INSTANCE.get("__GHATANA_TEST_ABSENT_SECRET_XYZ__");
        assertThat(value).isEmpty(); // GH-90000
    }

    // ─── SecretProvider default methods ───────────────────────────────────────

    @Test
    @DisplayName("require() throws MissingSecretException for absent secret")
    void requireThrowsForAbsentSecret() { // GH-90000
        SecretProvider provider = name -> Optional.empty(); // GH-90000

        assertThatThrownBy(() -> provider.require("DB_PASSWORD"))
                .isInstanceOf(SecretProvider.MissingSecretException.class) // GH-90000
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    @DisplayName("require() returns the value when the secret is present")
    void requireReturnsValueWhenPresent() { // GH-90000
        SecretProvider provider = name -> Optional.of("s3cr3t");

        assertThat(provider.require("MY_KEY")).isEqualTo("s3cr3t");
    }

    @Test
    @DisplayName("get(name, version) delegates to get(name) by default")
    void versionedGetDelegatesToUnversioned() { // GH-90000
        SecretProvider provider = name -> Optional.of("value-of-" + name); // GH-90000

        assertThat(provider.get("API_KEY", "v2")).contains("value-of-API_KEY");
    }

    @Test
    @DisplayName("MissingSecretException captures the secret name")
    void missingSecretExceptionCapturesName() { // GH-90000
        SecretProvider.MissingSecretException ex = new SecretProvider.MissingSecretException("PAYMENT_KEY");

        assertThat(ex.getSecretName()).isEqualTo("PAYMENT_KEY");
        assertThat(ex.getMessage()).contains("PAYMENT_KEY");
    }
}
