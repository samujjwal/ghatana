/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.registry.provider;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.TypedAgent;
import com.ghatana.datacloud.agent.registry.agents.DataAnomalyDetectorAgent;
import com.ghatana.datacloud.agent.registry.agents.DataSyncAgent;
import com.ghatana.datacloud.agent.registry.agents.SchemaValidatorAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link DataCloudAgentLogicProvider}.
 *
 * @doc.type class
 * @doc.purpose Verify provider resolves all supported refs to real agent instances
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("DataCloudAgentLogicProvider")
class DataCloudAgentLogicProviderTest {

    private DataCloudAgentLogicProvider provider;

    @BeforeEach
    void setUp() { 
        provider = new DataCloudAgentLogicProvider(); 
    }

    @Test
    @DisplayName("providerId is 'data-cloud'")
    void providerIdIsDataCloud() { 
        assertThat(provider.getProviderId()).isEqualTo("data-cloud");
    }

    @Test
    @DisplayName("exposes exactly three supported refs by default")
    void exposesThreeSupportedRefs() { 
        assertThat(provider.getSupportedRefs()).containsExactlyInAnyOrder( 
                "data-cloud:agent.data-cloud.schema-validator",
                "data-cloud:agent.data-cloud.data-sync",
                "data-cloud:agent.data-cloud.anomaly-detector");
    }

    @Test
    @DisplayName("creates SchemaValidatorAgent for schema-validator ref")
    void createsSchemaValidatorAgent() { 
        AgentConfig config = mock(AgentConfig.class); 
        TypedAgent<?, ?> agent = provider.createAgent( 
                "data-cloud:agent.data-cloud.schema-validator", config);
        assertThat(agent).isInstanceOf(SchemaValidatorAgent.class); 
    }

    @Test
    @DisplayName("creates DataSyncAgent for data-sync ref")
    void createsDataSyncAgent() { 
        AgentConfig config = mock(AgentConfig.class); 
        TypedAgent<?, ?> agent = provider.createAgent( 
                "data-cloud:agent.data-cloud.data-sync", config);
        assertThat(agent).isInstanceOf(DataSyncAgent.class); 
    }

    @Test
    @DisplayName("creates DataAnomalyDetectorAgent for anomaly-detector ref")
    void createsDataAnomalyDetectorAgent() { 
        AgentConfig config = mock(AgentConfig.class); 
        TypedAgent<?, ?> agent = provider.createAgent( 
                "data-cloud:agent.data-cloud.anomaly-detector", config);
        assertThat(agent).isInstanceOf(DataAnomalyDetectorAgent.class); 
    }

    @Test
    @DisplayName("throws IllegalArgumentException for unknown ref")
    void throwsForUnknownRef() { 
        AgentConfig config = mock(AgentConfig.class); 
        assertThatThrownBy(() -> 
                provider.createAgent("data-cloud:agent.unknown", config)) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("Unsupported implementationRef");
    }

    @Test
    @DisplayName("registerFactory adds a new ref")
    void registerFactoryAddsRef() { 
        AgentConfig config = mock(AgentConfig.class); 
        provider.registerFactory("data-cloud:agent.custom", c -> new SchemaValidatorAgent()); 

        assertThat(provider.getSupportedRefs()).contains("data-cloud:agent.custom");
        assertThat(provider.createAgent("data-cloud:agent.custom", config)) 
                .isInstanceOf(SchemaValidatorAgent.class); 
    }
}
