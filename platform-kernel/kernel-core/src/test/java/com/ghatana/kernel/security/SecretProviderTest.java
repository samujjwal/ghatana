/*
 * Copyright (c) 2026 Ghatana Inc. 
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
    void instanceIsNotNull() { 
        assertThat(EnvironmentSecretProvider.INSTANCE).isNotNull(); 
    }

    @Test
    @DisplayName("returns empty when the env var is not set")
    void returnsEmptyForMissingVar() { 
        // Use a name that is extremely unlikely to exist in any real environment
        Optional<String> value = EnvironmentSecretProvider.INSTANCE.get("__GHATANA_TEST_ABSENT_SECRET_XYZ__");
        assertThat(value).isEmpty(); 
    }

    // ─── SecretProvider default methods ───────────────────────────────────────

    @Test
    @DisplayName("require() throws MissingSecretException for absent secret")
    void requireThrowsForAbsentSecret() { 
        SecretProvider provider = name -> Optional.empty(); 

        assertThatThrownBy(() -> provider.require("DB_PASSWORD"))
                .isInstanceOf(SecretProvider.MissingSecretException.class) 
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    @DisplayName("require() returns the value when the secret is present")
    void requireReturnsValueWhenPresent() { 
        SecretProvider provider = name -> Optional.of("s3cr3t");

        assertThat(provider.require("MY_KEY")).isEqualTo("s3cr3t");
    }

    @Test
    @DisplayName("get(name, version) delegates to get(name) by default")
    void versionedGetDelegatesToUnversioned() { 
        SecretProvider provider = name -> Optional.of("value-of-" + name); 

        assertThat(provider.get("API_KEY", "v2")).contains("value-of-API_KEY");
    }

    @Test
    @DisplayName("MissingSecretException captures the secret name")
    void missingSecretExceptionCapturesName() { 
        SecretProvider.MissingSecretException ex = new SecretProvider.MissingSecretException("PAYMENT_KEY");

        assertThat(ex.getSecretName()).isEqualTo("PAYMENT_KEY");
        assertThat(ex.getMessage()).contains("PAYMENT_KEY");
    }
}
