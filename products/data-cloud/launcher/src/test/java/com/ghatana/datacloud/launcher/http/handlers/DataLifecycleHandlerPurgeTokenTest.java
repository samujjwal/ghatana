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
    void localProfileMayUseEphemeralPurgeTokenSecret() { 
        Map<String, String> env = Map.of("DATACLOUD_PROFILE", "local"); 
        long issuedAtMs = Instant.now().toEpochMilli(); 

        DataLifecycleHandler.TokenSecretRequirement requirement =
            DataLifecycleHandler.validatePurgeTokenSecretConfiguration(env); 
        String token = DataLifecycleHandler.buildPurgeToken("tenant-a", "orders", issuedAtMs, env); 
        DataLifecycleHandler.TokenValidationResult validation =
            DataLifecycleHandler.validatePurgeToken(token, "tenant-a", "orders", env); 

        assertThat(requirement.available()).isTrue(); 
        assertThat(validation.valid()).isTrue(); 
    }

    @Test
    @DisplayName("production profile requires configured durable purge token secret")
    void productionProfileRequiresConfiguredDurablePurgeTokenSecret() { 
        Map<String, String> env = Map.of("DATACLOUD_PROFILE", "production"); 

        DataLifecycleHandler.TokenSecretRequirement requirement =
            DataLifecycleHandler.validatePurgeTokenSecretConfiguration(env); 

        assertThat(requirement.available()).isFalse(); 
        assertThat(requirement.message()).contains("DATACLOUD_PURGE_TOKEN_SECRET");
        assertThat(requirement.profile()).isEqualTo("production");
    }

    @Test
    @DisplayName("production profile validates tokens when durable secret is configured")
    void productionProfileValidatesTokensWhenDurableSecretConfigured() { 
        Map<String, String> env = Map.of( 
            "DATACLOUD_PROFILE", "production",
            "DATACLOUD_PURGE_TOKEN_SECRET", "durable-secret-value"
        );
        long issuedAtMs = Instant.now().toEpochMilli(); 

        DataLifecycleHandler.TokenSecretRequirement requirement =
            DataLifecycleHandler.validatePurgeTokenSecretConfiguration(env); 
        String token = DataLifecycleHandler.buildPurgeToken("tenant-a", "orders", issuedAtMs, env); 
        DataLifecycleHandler.TokenValidationResult valid =
            DataLifecycleHandler.validatePurgeToken(token, "tenant-a", "orders", env); 
        DataLifecycleHandler.TokenValidationResult invalid =
            DataLifecycleHandler.validatePurgeToken(token, "tenant-a", "other-orders", env); 

        assertThat(requirement.available()).isTrue(); 
        assertThat(valid.valid()).isTrue(); 
        assertThat(invalid.valid()).isFalse(); 
        assertThat(invalid.reason()).contains("signature mismatch");
    }
}