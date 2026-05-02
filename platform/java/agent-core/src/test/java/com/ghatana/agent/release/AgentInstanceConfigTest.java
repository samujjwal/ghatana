/*
 * Copyright (c) 2026 Ghatana Inc. 
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

    private static AgentInstanceConfig minimal() { 
        return new AgentInstanceConfig( 
                "cfg-001",
                "release-001",
                "tenant-001",
                "production",
                Map.of(), 
                Map.of(), 
                Map.of(), 
                Map.of(), 
                false,
                Instant.now(), 
                Instant.now() 
        );
    }

    @Test
    @DisplayName("construction with valid fields succeeds")
    void validConstruction() { 
        AgentInstanceConfig config = minimal(); 
        assertThat(config.instanceConfigId()).isEqualTo("cfg-001");
        assertThat(config.tenantId()).isEqualTo("tenant-001");
        assertThat(config.killSwitch()).isFalse(); 
    }

    @Nested
    @DisplayName("Blank ID validation")
    class BlankIdValidation {

        @Test
        @DisplayName("blank instanceConfigId throws")
        void blankInstanceConfigId() { 
            assertThatThrownBy(() -> new AgentInstanceConfig( 
                    " ", "release-001", "tenant-001", "production",
                    Map.of(), Map.of(), Map.of(), Map.of(), false, 
                    Instant.now(), Instant.now())) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("instanceConfigId");
        }

        @Test
        @DisplayName("blank agentReleaseId throws")
        void blankAgentReleaseId() { 
            assertThatThrownBy(() -> new AgentInstanceConfig( 
                    "cfg-001", "", "tenant-001", "production",
                    Map.of(), Map.of(), Map.of(), Map.of(), false, 
                    Instant.now(), Instant.now())) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("agentReleaseId");
        }

        @Test
        @DisplayName("blank tenantId throws")
        void blankTenantId() { 
            assertThatThrownBy(() -> new AgentInstanceConfig( 
                    "cfg-001", "release-001", "", "production",
                    Map.of(), Map.of(), Map.of(), Map.of(), false, 
                    Instant.now(), Instant.now())) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("tenantId");
        }
    }

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("modelOverrides map is unmodifiable")
        void modelOverridesUnmodifiable() { 
            AgentInstanceConfig config = new AgentInstanceConfig( 
                    "cfg-001", "release-001", "tenant-001", "production",
                    Map.of("model", "gpt-4"), 
                    Map.of(), Map.of(), Map.of(), false, 
                    Instant.now(), Instant.now()); 

            assertThatThrownBy(() -> config.modelOverrides().put("key", "value")) 
                    .isInstanceOf(UnsupportedOperationException.class); 
        }

        @Test
        @DisplayName("featureFlags map is unmodifiable")
        void featureFlagsUnmodifiable() { 
            AgentInstanceConfig config = new AgentInstanceConfig( 
                    "cfg-001", "release-001", "tenant-001", "production",
                    Map.of(), Map.of(), Map.of("flag", "true"), Map.of(), false, 
                    Instant.now(), Instant.now()); 

            assertThatThrownBy(() -> config.featureFlags().put("extra", "val")) 
                    .isInstanceOf(UnsupportedOperationException.class); 
        }
    }

    @Test
    @DisplayName("killSwitch can be set to true")
    void killSwitchTrue() { 
        AgentInstanceConfig config = new AgentInstanceConfig( 
                "cfg-001", "release-001", "tenant-001", "staging",
                Map.of(), Map.of(), Map.of(), Map.of(), 
                true,
                Instant.now(), Instant.now()); 

        assertThat(config.killSwitch()).isTrue(); 
    }
}
