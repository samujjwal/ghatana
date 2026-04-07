package com.ghatana.yappc.services.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("YappcEnvironmentConfig Tests")
class YappcEnvironmentConfigTest {

    // ─── validate() throw-on-error ─────────────────────────────────────────

    @Test
    @DisplayName("validate passes with all required variables set")
    void validatePassesWithAllRequiredVars() {
        Map<String, String> env = minimalValidEnv();
        // Should not throw
        YappcEnvironmentConfig.validate(env);
    }

    @Test
    @DisplayName("validate throws when YAPPC_API_KEYS is absent")
    void validateThrowsWhenApiKeysMissing() {
        Map<String, String> env = minimalValidEnv();
        env.remove(YappcEnvironmentConfig.API_KEYS_ENV);

        assertThatThrownBy(() -> YappcEnvironmentConfig.validate(env))
                .isInstanceOf(YappcEnvironmentConfig.YappcEnvironmentConfigException.class)
                .hasMessageContaining(YappcEnvironmentConfig.API_KEYS_ENV);
    }

    @Test
    @DisplayName("validate throws when YAPPC_API_KEYS is blank")
    void validateThrowsWhenApiKeysBlank() {
        Map<String, String> env = minimalValidEnv();
        env.put(YappcEnvironmentConfig.API_KEYS_ENV, "   ");

        assertThatThrownBy(() -> YappcEnvironmentConfig.validate(env))
                .isInstanceOf(YappcEnvironmentConfig.YappcEnvironmentConfigException.class)
                .hasMessageContaining(YappcEnvironmentConfig.API_KEYS_ENV);
    }

    @Test
    @DisplayName("validate throws when dev-key is used in production mode")
    void validateThrowsForInsecureDefaultKeyInProduction() {
        Map<String, String> env = minimalValidEnv();
        env.put(YappcEnvironmentConfig.API_KEYS_ENV, YappcEnvironmentConfig.INSECURE_DEFAULT_KEY);
        env.put(YappcEnvironmentConfig.PROFILE_ENV, "production");

        assertThatThrownBy(() -> YappcEnvironmentConfig.validate(env))
                .isInstanceOf(YappcEnvironmentConfig.YappcEnvironmentConfigException.class)
                .hasMessageContaining(YappcEnvironmentConfig.INSECURE_DEFAULT_KEY)
                .hasMessageContaining("production");
    }

    @Test
    @DisplayName("validate allows dev-key in non-production mode")
    void validateAllowsDevKeyInDevMode() {
        Map<String, String> env = minimalValidEnv();
        env.put(YappcEnvironmentConfig.API_KEYS_ENV, "dev-key");
        env.put(YappcEnvironmentConfig.PROFILE_ENV, "dev");

        // Must not throw in dev mode
        YappcEnvironmentConfig.validate(env);
    }

    @Test
    @DisplayName("validate throws when agent LLM mode is invalid")
    void validateThrowsWhenAgentLlmModeIsInvalid() {
        Map<String, String> env = minimalValidEnv();
        env.put(YappcEnvironmentConfig.AGENT_LLM_MODE_ENV, "auto");

        assertThatThrownBy(() -> YappcEnvironmentConfig.validate(env))
                .isInstanceOf(YappcEnvironmentConfig.YappcEnvironmentConfigException.class)
                .hasMessageContaining(YappcEnvironmentConfig.AGENT_LLM_MODE_ENV)
                .hasMessageContaining("required, stub");
    }

    @Test
    @DisplayName("validate throws when stub AI mode is requested in production")
    void validateThrowsWhenStubAiModeIsRequestedInProduction() {
        Map<String, String> env = minimalValidEnv();
        env.put(YappcEnvironmentConfig.AGENT_LLM_MODE_ENV, "stub");
        env.put(YappcEnvironmentConfig.PROFILE_ENV, "production");

        assertThatThrownBy(() -> YappcEnvironmentConfig.validate(env))
                .isInstanceOf(YappcEnvironmentConfig.YappcEnvironmentConfigException.class)
                .hasMessageContaining(YappcEnvironmentConfig.AGENT_LLM_MODE_ENV)
                .hasMessageContaining("production");
    }

    @Test
    @DisplayName("validate allows explicit stub AI mode in development")
    void validateAllowsExplicitStubAiModeInDevelopment() {
        Map<String, String> env = minimalValidEnv();
        env.put(YappcEnvironmentConfig.AGENT_LLM_MODE_ENV, "stub");
        env.put(YappcEnvironmentConfig.PROFILE_ENV, "dev");

        YappcEnvironmentConfig.validate(env);
    }

    @Test
    @DisplayName("validate throws when DB_URL is set but credentials are missing")
    void validateThrowsWhenDbUrlSetWithoutCredentials() {
        Map<String, String> env = minimalValidEnv();
        env.put(YappcEnvironmentConfig.DB_URL_ENV, "jdbc:postgresql://localhost:5432/yappc");
        // Deliberately omit DB_USER and DB_PASSWORD

        assertThatThrownBy(() -> YappcEnvironmentConfig.validate(env))
                .isInstanceOf(YappcEnvironmentConfig.YappcEnvironmentConfigException.class)
                .hasMessageContaining(YappcEnvironmentConfig.DB_USER_ENV)
                .hasMessageContaining(YappcEnvironmentConfig.DB_PASSWORD_ENV);
    }

    @Test
    @DisplayName("validate throws in production when required AI mode has no provider configured")
    void validateThrowsInProductionWhenRequiredAiModeHasNoProviderConfigured() {
        Map<String, String> env = minimalValidEnv();
        env.put(YappcEnvironmentConfig.PROFILE_ENV, "production");

        assertThatThrownBy(() -> YappcEnvironmentConfig.validate(env))
                .isInstanceOf(YappcEnvironmentConfig.YappcEnvironmentConfigException.class)
                .hasMessageContaining("Production AI runtime requires at least one configured provider");
    }

    @Test
    @DisplayName("validate throws in production when provider is configured without model")
    void validateThrowsInProductionWhenProviderModelIsMissing() {
        Map<String, String> env = minimalValidEnv();
        env.put(YappcEnvironmentConfig.PROFILE_ENV, "production");
        env.put(YappcEnvironmentConfig.OPENAI_API_KEY_ENV, "openai-key");

        assertThatThrownBy(() -> YappcEnvironmentConfig.validate(env))
                .isInstanceOf(YappcEnvironmentConfig.YappcEnvironmentConfigException.class)
                .hasMessageContaining(YappcEnvironmentConfig.OPENAI_MODEL_ENV);
    }

    @Test
    @DisplayName("validate throws in production when tracing export endpoint is missing")
    void validateThrowsInProductionWhenTracingEndpointIsMissing() {
        Map<String, String> env = productionAiEnv();
        env.remove(YappcEnvironmentConfig.OTEL_EXPORTER_OTLP_ENDPOINT_ENV);

        assertThatThrownBy(() -> YappcEnvironmentConfig.validate(env))
                .isInstanceOf(YappcEnvironmentConfig.YappcEnvironmentConfigException.class)
                .hasMessageContaining(YappcEnvironmentConfig.OTEL_EXPORTER_OTLP_ENDPOINT_ENV);
    }

    @Test
    @DisplayName("validate passes in production when AI provider and tracing are configured")
    void validatePassesInProductionWhenAiProviderAndTracingAreConfigured() {
        YappcEnvironmentConfig.validate(productionAiEnv());
    }

    @Test
    @DisplayName("validate passes when DB_URL is set with both credentials")
    void validatePassesWhenDbUrlAndCredentialsSet() {
        Map<String, String> env = minimalValidEnv();
        env.put(YappcEnvironmentConfig.DB_URL_ENV, "jdbc:postgresql://localhost:5432/yappc");
        env.put(YappcEnvironmentConfig.DB_USER_ENV, "yappc_user");
        env.put(YappcEnvironmentConfig.DB_PASSWORD_ENV, "secret-password");

        // Must not throw
        YappcEnvironmentConfig.validate(env);
    }

    @Test
    @DisplayName("validate collects multiple errors in a single throw")
    void validateCollectsAllErrors() {
        Map<String, String> env = new HashMap<>();
        // Omit API keys AND provide DB_URL without credentials
        env.put(YappcEnvironmentConfig.DB_URL_ENV, "jdbc:postgresql://localhost:5432/yappc");

        assertThatThrownBy(() -> YappcEnvironmentConfig.validate(env))
                .isInstanceOf(YappcEnvironmentConfig.YappcEnvironmentConfigException.class)
                .hasMessageContaining(YappcEnvironmentConfig.API_KEYS_ENV)
                .hasMessageContaining(YappcEnvironmentConfig.DB_USER_ENV)
                .hasMessageContaining(YappcEnvironmentConfig.DB_PASSWORD_ENV);
    }

    // ─── check() non-throwing variant ─────────────────────────────────────

    @Test
    @DisplayName("check returns isValid=true for valid env")
    void checkReturnsValidForCorrectEnv() {
        YappcEnvironmentConfig.ValidationResult result =
                YappcEnvironmentConfig.check(minimalValidEnv());

        assertThat(result.isValid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("check returns isValid=false and exposes all error messages")
    void checkReturnsInvalidAndAllErrors() {
        Map<String, String> env = new HashMap<>();
        env.put(YappcEnvironmentConfig.DB_URL_ENV, "jdbc:postgresql://localhost:5432/yappc");

        YappcEnvironmentConfig.ValidationResult result = YappcEnvironmentConfig.check(env);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result.errors().stream().anyMatch(e -> e.contains(YappcEnvironmentConfig.API_KEYS_ENV))).isTrue();
        assertThat(result.errors().stream().anyMatch(e -> e.contains(YappcEnvironmentConfig.DB_USER_ENV))).isTrue();
    }

    @Test
    @DisplayName("prod profile alias 'prod' is also rejected for dev-key")
    void prodAliasAlsoRejectedForDevKey() {
        Map<String, String> env = minimalValidEnv();
        env.put(YappcEnvironmentConfig.API_KEYS_ENV, "dev-key");
        env.put(YappcEnvironmentConfig.PROFILE_ENV, "prod");

        YappcEnvironmentConfig.ValidationResult result = YappcEnvironmentConfig.check(env);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors().get(0)).contains("dev-key");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static Map<String, String> minimalValidEnv() {
        Map<String, String> env = new HashMap<>();
        env.put(YappcEnvironmentConfig.API_KEYS_ENV, "apikey-abc123,apikey-def456");
        return env;
    }

    private static Map<String, String> productionAiEnv() {
        Map<String, String> env = minimalValidEnv();
        env.put(YappcEnvironmentConfig.PROFILE_ENV, "production");
        env.put(YappcEnvironmentConfig.OPENAI_API_KEY_ENV, "openai-key");
        env.put(YappcEnvironmentConfig.OPENAI_MODEL_ENV, "gpt-4o");
        env.put(YappcEnvironmentConfig.OTEL_EXPORTER_OTLP_ENDPOINT_ENV, "http://localhost:4318");
        return env;
    }
}
