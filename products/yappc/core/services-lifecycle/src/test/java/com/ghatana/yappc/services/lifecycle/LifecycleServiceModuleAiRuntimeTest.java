package com.ghatana.yappc.services.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ghatana.yappc.agent.YappcAgentSystem;
import com.ghatana.yappc.services.security.YappcEnvironmentConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests for lifecycle AI runtime mode resolution.
 *
 * @doc.type class
 * @doc.purpose Verify lifecycle boot resolves explicit AI runtime mode safely across profiles
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("LifecycleServiceModule AI Runtime Tests")
class LifecycleServiceModuleAiRuntimeTest {

    @Test
    @DisplayName("resolveAiRuntimeMode defaults to required mode")
    void resolveAiRuntimeModeDefaultsToRequiredMode() {
        Map<String, String> env = new HashMap<>();

        assertThat(LifecycleServiceModule.resolveAiRuntimeMode(env))
                .isEqualTo(YappcAgentSystem.AiRuntimeMode.REQUIRED);
    }

    @Test
    @DisplayName("resolveAiRuntimeMode allows explicit stub mode outside production")
    void resolveAiRuntimeModeAllowsExplicitStubModeOutsideProduction() {
        Map<String, String> env = new HashMap<>();
        env.put(YappcEnvironmentConfig.AGENT_LLM_MODE_ENV, "stub");
        env.put(YappcEnvironmentConfig.PROFILE_ENV, "test");

        assertThat(LifecycleServiceModule.resolveAiRuntimeMode(env))
                .isEqualTo(YappcAgentSystem.AiRuntimeMode.STUB);
    }

    @Test
    @DisplayName("resolveAiRuntimeMode rejects explicit stub mode in production")
    void resolveAiRuntimeModeRejectsExplicitStubModeInProduction() {
        Map<String, String> env = new HashMap<>();
        env.put(YappcEnvironmentConfig.AGENT_LLM_MODE_ENV, "stub");
        env.put(YappcEnvironmentConfig.PROFILE_ENV, "production");

        assertThatThrownBy(() -> LifecycleServiceModule.resolveAiRuntimeMode(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(YappcEnvironmentConfig.AGENT_LLM_MODE_ENV)
                .hasMessageContaining("production");
    }
}