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
@DisplayName("LifecycleServiceModule AI Runtime Tests [GH-90000]")
class LifecycleServiceModuleAiRuntimeTest {

    @Test
    @DisplayName("resolveAiRuntimeMode defaults to required mode [GH-90000]")
    void resolveAiRuntimeModeDefaultsToRequiredMode() { // GH-90000
        Map<String, String> env = new HashMap<>(); // GH-90000

        assertThat(LifecycleServiceModule.resolveAiRuntimeMode(env)) // GH-90000
                .isEqualTo(YappcAgentSystem.AiRuntimeMode.REQUIRED); // GH-90000
    }

    @Test
    @DisplayName("resolveAiRuntimeMode allows explicit stub mode outside production [GH-90000]")
    void resolveAiRuntimeModeAllowsExplicitStubModeOutsideProduction() { // GH-90000
        Map<String, String> env = new HashMap<>(); // GH-90000
        env.put(YappcEnvironmentConfig.AGENT_LLM_MODE_ENV, "stub"); // GH-90000
        env.put(YappcEnvironmentConfig.PROFILE_ENV, "test"); // GH-90000

        assertThat(LifecycleServiceModule.resolveAiRuntimeMode(env)) // GH-90000
                .isEqualTo(YappcAgentSystem.AiRuntimeMode.STUB); // GH-90000
    }

    @Test
    @DisplayName("resolveAiRuntimeMode rejects explicit stub mode in production [GH-90000]")
    void resolveAiRuntimeModeRejectsExplicitStubModeInProduction() { // GH-90000
        Map<String, String> env = new HashMap<>(); // GH-90000
        env.put(YappcEnvironmentConfig.AGENT_LLM_MODE_ENV, "stub"); // GH-90000
        env.put(YappcEnvironmentConfig.PROFILE_ENV, "production"); // GH-90000

        assertThatThrownBy(() -> LifecycleServiceModule.resolveAiRuntimeMode(env)) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining(YappcEnvironmentConfig.AGENT_LLM_MODE_ENV) // GH-90000
                .hasMessageContaining("production [GH-90000]");
    }
}
