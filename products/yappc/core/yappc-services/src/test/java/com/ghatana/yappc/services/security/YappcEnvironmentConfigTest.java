package com.ghatana.yappc.services.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type test
 * @doc.purpose Verifies fail-fast YAPPC runtime environment validation
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("YappcEnvironmentConfig")
class YappcEnvironmentConfigTest {

    @Test
    @DisplayName("production requires Data Cloud or Event Cloud Kernel lifecycle truth")
    void production_requiresCanonicalKernelLifecycleTruthSource() {
        Map<String, String> env = validProductionEnv();
        env.remove(YappcEnvironmentConfig.KERNEL_LIFECYCLE_TRUTH_SOURCE_ENV);

        YappcEnvironmentConfig.ValidationResult result = YappcEnvironmentConfig.check(env);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anySatisfy(error ->
                assertThat(error).contains(YappcEnvironmentConfig.KERNEL_LIFECYCLE_TRUTH_SOURCE_ENV)
                        .contains("data-cloud or event-cloud"));
    }

    @Test
    @DisplayName("production rejects local filesystem Kernel lifecycle truth")
    void production_rejectsLocalKernelLifecycleTruthSource() {
        Map<String, String> env = validProductionEnv();
        env.put(YappcEnvironmentConfig.KERNEL_LIFECYCLE_TRUTH_SOURCE_ENV, "local-filesystem");

        YappcEnvironmentConfig.ValidationResult result = YappcEnvironmentConfig.check(env);

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).anySatisfy(error ->
                assertThat(error).contains("local-filesystem").contains("dev/test-only"));
    }

    @Test
    @DisplayName("production accepts Data Cloud Kernel lifecycle truth")
    void production_acceptsDataCloudKernelLifecycleTruthSource() {
        Map<String, String> env = validProductionEnv();
        env.put(YappcEnvironmentConfig.KERNEL_LIFECYCLE_TRUTH_SOURCE_ENV, "data-cloud");

        YappcEnvironmentConfig.ValidationResult result = YappcEnvironmentConfig.check(env);

        assertThat(result.errors()).noneSatisfy(error ->
                assertThat(error).contains(YappcEnvironmentConfig.KERNEL_LIFECYCLE_TRUTH_SOURCE_ENV));
    }

    private static Map<String, String> validProductionEnv() {
        Map<String, String> env = new HashMap<>();
        env.put(YappcEnvironmentConfig.PROFILE_ENV, "production");
        env.put(YappcEnvironmentConfig.API_KEYS_ENV, "prod-key-1,prod-key-2");
        env.put(YappcEnvironmentConfig.AGENT_LLM_MODE_ENV, "stub");
        env.put(YappcEnvironmentConfig.JWT_SECRET_ENV, "0123456789abcdef0123456789abcdef");
        return env;
    }
}
