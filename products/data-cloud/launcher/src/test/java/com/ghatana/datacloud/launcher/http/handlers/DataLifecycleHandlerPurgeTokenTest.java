package com.ghatana.datacloud.launcher.http.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DataLifecycleHandler purge tokens")
class DataLifecycleHandlerPurgeTokenTest {

    @Test
    @DisplayName("local profile may use ephemeral purge token secret")
    void localProfileMayUseEphemeralPurgeTokenSecret() { // GH-90000
        Map<String, String> env = Map.of("DATACLOUD_PROFILE", "local"); // GH-90000
        long issuedAtMs = Instant.now().toEpochMilli(); // GH-90000

        DataLifecycleHandler.TokenSecretRequirement requirement =
            DataLifecycleHandler.validatePurgeTokenSecretConfiguration(env); // GH-90000
        String token = DataLifecycleHandler.buildPurgeToken("tenant-a", "orders", issuedAtMs, env); // GH-90000
        DataLifecycleHandler.TokenValidationResult validation =
            DataLifecycleHandler.validatePurgeToken(token, "tenant-a", "orders", env); // GH-90000

        assertThat(requirement.available()).isTrue(); // GH-90000
        assertThat(validation.valid()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("production profile requires configured durable purge token secret")
    void productionProfileRequiresConfiguredDurablePurgeTokenSecret() { // GH-90000
        Map<String, String> env = Map.of("DATACLOUD_PROFILE", "production"); // GH-90000

        DataLifecycleHandler.TokenSecretRequirement requirement =
            DataLifecycleHandler.validatePurgeTokenSecretConfiguration(env); // GH-90000

        assertThat(requirement.available()).isFalse(); // GH-90000
        assertThat(requirement.message()).contains("DATACLOUD_PURGE_TOKEN_SECRET");
        assertThat(requirement.profile()).isEqualTo("production");
    }

    @Test
    @DisplayName("production profile validates tokens when durable secret is configured")
    void productionProfileValidatesTokensWhenDurableSecretConfigured() { // GH-90000
        Map<String, String> env = Map.of( // GH-90000
            "DATACLOUD_PROFILE", "production",
            "DATACLOUD_PURGE_TOKEN_SECRET", "durable-secret-value"
        );
        long issuedAtMs = Instant.now().toEpochMilli(); // GH-90000

        DataLifecycleHandler.TokenSecretRequirement requirement =
            DataLifecycleHandler.validatePurgeTokenSecretConfiguration(env); // GH-90000
        String token = DataLifecycleHandler.buildPurgeToken("tenant-a", "orders", issuedAtMs, env); // GH-90000
        DataLifecycleHandler.TokenValidationResult valid =
            DataLifecycleHandler.validatePurgeToken(token, "tenant-a", "orders", env); // GH-90000
        DataLifecycleHandler.TokenValidationResult invalid =
            DataLifecycleHandler.validatePurgeToken(token, "tenant-a", "other-orders", env); // GH-90000

        assertThat(requirement.available()).isTrue(); // GH-90000
        assertThat(valid.valid()).isTrue(); // GH-90000
        assertThat(invalid.valid()).isFalse(); // GH-90000
        assertThat(invalid.reason()).contains("signature mismatch");
    }
}