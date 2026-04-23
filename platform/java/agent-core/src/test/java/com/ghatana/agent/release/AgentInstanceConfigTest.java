/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.agent.release;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link AgentInstanceConfig}.
 *
 * @doc.type class
 * @doc.purpose Tests for AgentInstanceConfig validation and immutability
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("AgentInstanceConfig")
class AgentInstanceConfigTest {

    private static AgentInstanceConfig minimal() { // GH-90000
        return new AgentInstanceConfig( // GH-90000
                "cfg-001",
                "release-001",
                "tenant-001",
                "production",
                Map.of(), // GH-90000
                Map.of(), // GH-90000
                Map.of(), // GH-90000
                Map.of(), // GH-90000
                false,
                Instant.now(), // GH-90000
                Instant.now() // GH-90000
        );
    }

    @Test
    @DisplayName("construction with valid fields succeeds")
    void validConstruction() { // GH-90000
        AgentInstanceConfig config = minimal(); // GH-90000
        assertThat(config.instanceConfigId()).isEqualTo("cfg-001");
        assertThat(config.tenantId()).isEqualTo("tenant-001");
        assertThat(config.killSwitch()).isFalse(); // GH-90000
    }

    @Nested
    @DisplayName("Blank ID validation")
    class BlankIdValidation {

        @Test
        @DisplayName("blank instanceConfigId throws")
        void blankInstanceConfigId() { // GH-90000
            assertThatThrownBy(() -> new AgentInstanceConfig( // GH-90000
                    " ", "release-001", "tenant-001", "production",
                    Map.of(), Map.of(), Map.of(), Map.of(), false, // GH-90000
                    Instant.now(), Instant.now())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("instanceConfigId");
        }

        @Test
        @DisplayName("blank agentReleaseId throws")
        void blankAgentReleaseId() { // GH-90000
            assertThatThrownBy(() -> new AgentInstanceConfig( // GH-90000
                    "cfg-001", "", "tenant-001", "production",
                    Map.of(), Map.of(), Map.of(), Map.of(), false, // GH-90000
                    Instant.now(), Instant.now())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("agentReleaseId");
        }

        @Test
        @DisplayName("blank tenantId throws")
        void blankTenantId() { // GH-90000
            assertThatThrownBy(() -> new AgentInstanceConfig( // GH-90000
                    "cfg-001", "release-001", "", "production",
                    Map.of(), Map.of(), Map.of(), Map.of(), false, // GH-90000
                    Instant.now(), Instant.now())) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("tenantId");
        }
    }

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("modelOverrides map is unmodifiable")
        void modelOverridesUnmodifiable() { // GH-90000
            AgentInstanceConfig config = new AgentInstanceConfig( // GH-90000
                    "cfg-001", "release-001", "tenant-001", "production",
                    Map.of("model", "gpt-4"), // GH-90000
                    Map.of(), Map.of(), Map.of(), false, // GH-90000
                    Instant.now(), Instant.now()); // GH-90000

            assertThatThrownBy(() -> config.modelOverrides().put("key", "value")) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("featureFlags map is unmodifiable")
        void featureFlagsUnmodifiable() { // GH-90000
            AgentInstanceConfig config = new AgentInstanceConfig( // GH-90000
                    "cfg-001", "release-001", "tenant-001", "production",
                    Map.of(), Map.of(), Map.of("flag", "true"), Map.of(), false, // GH-90000
                    Instant.now(), Instant.now()); // GH-90000

            assertThatThrownBy(() -> config.featureFlags().put("extra", "val")) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }

    @Test
    @DisplayName("killSwitch can be set to true")
    void killSwitchTrue() { // GH-90000
        AgentInstanceConfig config = new AgentInstanceConfig( // GH-90000
                "cfg-001", "release-001", "tenant-001", "staging",
                Map.of(), Map.of(), Map.of(), Map.of(), // GH-90000
                true,
                Instant.now(), Instant.now()); // GH-90000

        assertThat(config.killSwitch()).isTrue(); // GH-90000
    }
}
