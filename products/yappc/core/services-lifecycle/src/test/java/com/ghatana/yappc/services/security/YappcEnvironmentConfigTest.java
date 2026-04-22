package com.ghatana.yappc.services.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("YappcEnvironmentConfig Tests [GH-90000]")
class YappcEnvironmentConfigTest {

    // ─── validate() throw-on-error ───────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("validate passes with all required variables set [GH-90000]")
    void validatePassesWithAllRequiredVars() { // GH-90000
        Map<String, String> env = minimalValidEnv(); // GH-90000
        // Should not throw
        YappcEnvironmentConfig.validate(env); // GH-90000
    }

    @Test
    @DisplayName("validate throws when YAPPC_API_KEYS is absent [GH-90000]")
    void validateThrowsWhenApiKeysMissing() { // GH-90000
        Map<String, String> env = minimalValidEnv(); // GH-90000
        env.remove(YappcEnvironmentConfig.API_KEYS_ENV); // GH-90000

        assertThatThrownBy(() -> YappcEnvironmentConfig.validate(env)) // GH-90000
                .isInstanceOf(YappcEnvironmentConfig.YappcEnvironmentConfigException.class) // GH-90000
                .hasMessageContaining(YappcEnvironmentConfig.API_KEYS_ENV); // GH-90000
    }

    @Test
    @DisplayName("validate throws when YAPPC_API_KEYS is blank [GH-90000]")
    void validateThrowsWhenApiKeysBlank() { // GH-90000
        Map<String, String> env = minimalValidEnv(); // GH-90000
        env.put(YappcEnvironmentConfig.API_KEYS_ENV, "   "); // GH-90000

        assertThatThrownBy(() -> YappcEnvironmentConfig.validate(env)) // GH-90000
                .isInstanceOf(YappcEnvironmentConfig.YappcEnvironmentConfigException.class) // GH-90000
                .hasMessageContaining(YappcEnvironmentConfig.API_KEYS_ENV); // GH-90000
    }

    @Test
    @DisplayName("validate throws when dev-key is used in production mode [GH-90000]")
    void validateThrowsForInsecureDefaultKeyInProduction() { // GH-90000
        Map<String, String> env = minimalValidEnv(); // GH-90000
        env.put(YappcEnvironmentConfig.API_KEYS_ENV, YappcEnvironmentConfig.INSECURE_DEFAULT_KEY); // GH-90000
        env.put(YappcEnvironmentConfig.PROFILE_ENV, "production"); // GH-90000

        assertThatThrownBy(() -> YappcEnvironmentConfig.validate(env)) // GH-90000
                .isInstanceOf(YappcEnvironmentConfig.YappcEnvironmentConfigException.class) // GH-90000
                .hasMessageContaining(YappcEnvironmentConfig.INSECURE_DEFAULT_KEY) // GH-90000
                .hasMessageContaining("production [GH-90000]");
    }

    @Test
    @DisplayName("validate allows dev-key in non-production mode [GH-90000]")
    void validateAllowsDevKeyInDevMode() { // GH-90000
        Map<String, String> env = minimalValidEnv(); // GH-90000
        env.put(YappcEnvironmentConfig.API_KEYS_ENV, "dev-key"); // GH-90000
        env.put(YappcEnvironmentConfig.PROFILE_ENV, "dev"); // GH-90000

        // Must not throw in dev mode
        YappcEnvironmentConfig.validate(env); // GH-90000
    }

    @Test
    @DisplayName("validate throws when agent LLM mode is invalid [GH-90000]")
    void validateThrowsWhenAgentLlmModeIsInvalid() { // GH-90000
        Map<String, String> env = minimalValidEnv(); // GH-90000
        env.put(YappcEnvironmentConfig.AGENT_LLM_MODE_ENV, "auto"); // GH-90000

        assertThatThrownBy(() -> YappcEnvironmentConfig.validate(env)) // GH-90000
                .isInstanceOf(YappcEnvironmentConfig.YappcEnvironmentConfigException.class) // GH-90000
                .hasMessageContaining(YappcEnvironmentConfig.AGENT_LLM_MODE_ENV) // GH-90000
                .hasMessageContaining("required, stub [GH-90000]");
    }

    @Test
    @DisplayName("validate throws when stub AI mode is requested in production [GH-90000]")
    void validateThrowsWhenStubAiModeIsRequestedInProduction() { // GH-90000
        Map<String, String> env = minimalValidEnv(); // GH-90000
        env.put(YappcEnvironmentConfig.AGENT_LLM_MODE_ENV, "stub"); // GH-90000
        env.put(YappcEnvironmentConfig.PROFILE_ENV, "production"); // GH-90000

        assertThatThrownBy(() -> YappcEnvironmentConfig.validate(env)) // GH-90000
                .isInstanceOf(YappcEnvironmentConfig.YappcEnvironmentConfigException.class) // GH-90000
                .hasMessageContaining(YappcEnvironmentConfig.AGENT_LLM_MODE_ENV) // GH-90000
                .hasMessageContaining("production [GH-90000]");
    }

    @Test
    @DisplayName("validate allows explicit stub AI mode in development [GH-90000]")
    void validateAllowsExplicitStubAiModeInDevelopment() { // GH-90000
        Map<String, String> env = minimalValidEnv(); // GH-90000
        env.put(YappcEnvironmentConfig.AGENT_LLM_MODE_ENV, "stub"); // GH-90000
        env.put(YappcEnvironmentConfig.PROFILE_ENV, "dev"); // GH-90000

        YappcEnvironmentConfig.validate(env); // GH-90000
    }

    @Test
    @DisplayName("validate throws when DB_URL is set but credentials are missing [GH-90000]")
    void validateThrowsWhenDbUrlSetWithoutCredentials() { // GH-90000
        Map<String, String> env = minimalValidEnv(); // GH-90000
        env.put(YappcEnvironmentConfig.DB_URL_ENV, "jdbc:postgresql://localhost:5432/yappc"); // GH-90000
        // Deliberately omit DB_USER and DB_PASSWORD

        assertThatThrownBy(() -> YappcEnvironmentConfig.validate(env)) // GH-90000
                .isInstanceOf(YappcEnvironmentConfig.YappcEnvironmentConfigException.class) // GH-90000
                .hasMessageContaining(YappcEnvironmentConfig.DB_USER_ENV) // GH-90000
                .hasMessageContaining(YappcEnvironmentConfig.DB_PASSWORD_ENV); // GH-90000
    }

    @Test
    @DisplayName("validate throws in production when required AI mode has no provider configured [GH-90000]")
    void validateThrowsInProductionWhenRequiredAiModeHasNoProviderConfigured() { // GH-90000
        Map<String, String> env = minimalValidEnv(); // GH-90000
        env.put(YappcEnvironmentConfig.PROFILE_ENV, "production"); // GH-90000

        assertThatThrownBy(() -> YappcEnvironmentConfig.validate(env)) // GH-90000
                .isInstanceOf(YappcEnvironmentConfig.YappcEnvironmentConfigException.class) // GH-90000
                .hasMessageContaining("Production AI runtime requires at least one configured provider [GH-90000]");
    }

    @Test
    @DisplayName("validate throws in production when provider is configured without model [GH-90000]")
    void validateThrowsInProductionWhenProviderModelIsMissing() { // GH-90000
        Map<String, String> env = minimalValidEnv(); // GH-90000
        env.put(YappcEnvironmentConfig.PROFILE_ENV, "production"); // GH-90000
        env.put(YappcEnvironmentConfig.OPENAI_API_KEY_ENV, "openai-key"); // GH-90000

        assertThatThrownBy(() -> YappcEnvironmentConfig.validate(env)) // GH-90000
                .isInstanceOf(YappcEnvironmentConfig.YappcEnvironmentConfigException.class) // GH-90000
                .hasMessageContaining(YappcEnvironmentConfig.OPENAI_MODEL_ENV); // GH-90000
    }

    @Test
    @DisplayName("validate throws in production when tracing export endpoint is missing [GH-90000]")
    void validateThrowsInProductionWhenTracingEndpointIsMissing() { // GH-90000
        Map<String, String> env = productionAiEnv(); // GH-90000
        env.remove(YappcEnvironmentConfig.OTEL_EXPORTER_OTLP_ENDPOINT_ENV); // GH-90000

        assertThatThrownBy(() -> YappcEnvironmentConfig.validate(env)) // GH-90000
                .isInstanceOf(YappcEnvironmentConfig.YappcEnvironmentConfigException.class) // GH-90000
                .hasMessageContaining(YappcEnvironmentConfig.OTEL_EXPORTER_OTLP_ENDPOINT_ENV); // GH-90000
    }

    @Test
    @DisplayName("validate passes in production when AI provider and tracing are configured [GH-90000]")
    void validatePassesInProductionWhenAiProviderAndTracingAreConfigured() { // GH-90000
        YappcEnvironmentConfig.validate(productionAiEnv()); // GH-90000
    }

    @Test
    @DisplayName("validate passes when DB_URL is set with both credentials [GH-90000]")
    void validatePassesWhenDbUrlAndCredentialsSet() { // GH-90000
        Map<String, String> env = minimalValidEnv(); // GH-90000
        env.put(YappcEnvironmentConfig.DB_URL_ENV, "jdbc:postgresql://localhost:5432/yappc"); // GH-90000
        env.put(YappcEnvironmentConfig.DB_USER_ENV, "yappc_user"); // GH-90000
        env.put(YappcEnvironmentConfig.DB_PASSWORD_ENV, "secret-password"); // GH-90000

        // Must not throw
        YappcEnvironmentConfig.validate(env); // GH-90000
    }

    @Test
    @DisplayName("validate collects multiple errors in a single throw [GH-90000]")
    void validateCollectsAllErrors() { // GH-90000
        Map<String, String> env = new HashMap<>(); // GH-90000
        // Omit API keys AND provide DB_URL without credentials
        env.put(YappcEnvironmentConfig.DB_URL_ENV, "jdbc:postgresql://localhost:5432/yappc"); // GH-90000

        assertThatThrownBy(() -> YappcEnvironmentConfig.validate(env)) // GH-90000
                .isInstanceOf(YappcEnvironmentConfig.YappcEnvironmentConfigException.class) // GH-90000
                .hasMessageContaining(YappcEnvironmentConfig.API_KEYS_ENV) // GH-90000
                .hasMessageContaining(YappcEnvironmentConfig.DB_USER_ENV) // GH-90000
                .hasMessageContaining(YappcEnvironmentConfig.DB_PASSWORD_ENV); // GH-90000
    }

    // ─── check() non-throwing variant ───────────────────────────────────── // GH-90000

    @Test
    @DisplayName("check returns isValid=true for valid env [GH-90000]")
    void checkReturnsValidForCorrectEnv() { // GH-90000
        YappcEnvironmentConfig.ValidationResult result =
                YappcEnvironmentConfig.check(minimalValidEnv()); // GH-90000

        assertThat(result.isValid()).isTrue(); // GH-90000
        assertThat(result.errors()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("check returns isValid=false and exposes all error messages [GH-90000]")
    void checkReturnsInvalidAndAllErrors() { // GH-90000
        Map<String, String> env = new HashMap<>(); // GH-90000
        env.put(YappcEnvironmentConfig.DB_URL_ENV, "jdbc:postgresql://localhost:5432/yappc"); // GH-90000

        YappcEnvironmentConfig.ValidationResult result = YappcEnvironmentConfig.check(env); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors()).hasSizeGreaterThanOrEqualTo(2); // GH-90000
        assertThat(result.errors().stream().anyMatch(e -> e.contains(YappcEnvironmentConfig.API_KEYS_ENV))).isTrue(); // GH-90000
        assertThat(result.errors().stream().anyMatch(e -> e.contains(YappcEnvironmentConfig.DB_USER_ENV))).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("prod profile alias 'prod' is also rejected for dev-key [GH-90000]")
    void prodAliasAlsoRejectedForDevKey() { // GH-90000
        Map<String, String> env = minimalValidEnv(); // GH-90000
        env.put(YappcEnvironmentConfig.API_KEYS_ENV, "dev-key"); // GH-90000
        env.put(YappcEnvironmentConfig.PROFILE_ENV, "prod"); // GH-90000

        YappcEnvironmentConfig.ValidationResult result = YappcEnvironmentConfig.check(env); // GH-90000

        assertThat(result.isValid()).isFalse(); // GH-90000
        assertThat(result.errors().get(0)).contains("dev-key [GH-90000]");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static Map<String, String> minimalValidEnv() { // GH-90000
        Map<String, String> env = new HashMap<>(); // GH-90000
        env.put(YappcEnvironmentConfig.API_KEYS_ENV, "apikey-abc123,apikey-def456"); // GH-90000
        env.put(YappcEnvironmentConfig.JWT_SECRET_ENV, "test-jwt-secret-minimum-32-bytes-long"); // GH-90000
        return env;
    }

    private static Map<String, String> productionAiEnv() { // GH-90000
        Map<String, String> env = minimalValidEnv(); // GH-90000
        env.put(YappcEnvironmentConfig.PROFILE_ENV, "production"); // GH-90000
        env.put(YappcEnvironmentConfig.OPENAI_API_KEY_ENV, "openai-key"); // GH-90000
        env.put(YappcEnvironmentConfig.OPENAI_MODEL_ENV, "gpt-4o"); // GH-90000
        env.put(YappcEnvironmentConfig.OTEL_EXPORTER_OTLP_ENDPOINT_ENV, "http://localhost:4318"); // GH-90000
        return env;
    }
}
