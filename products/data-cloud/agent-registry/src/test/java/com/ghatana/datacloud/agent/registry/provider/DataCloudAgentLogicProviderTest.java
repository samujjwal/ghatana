/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("DataCloudAgentLogicProvider [GH-90000]")
class DataCloudAgentLogicProviderTest {

    private DataCloudAgentLogicProvider provider;

    @BeforeEach
    void setUp() { // GH-90000
        provider = new DataCloudAgentLogicProvider(); // GH-90000
    }

    @Test
    @DisplayName("providerId is 'data-cloud' [GH-90000]")
    void providerIdIsDataCloud() { // GH-90000
        assertThat(provider.getProviderId()).isEqualTo("data-cloud [GH-90000]");
    }

    @Test
    @DisplayName("exposes exactly three supported refs by default [GH-90000]")
    void exposesThreeSupportedRefs() { // GH-90000
        assertThat(provider.getSupportedRefs()).containsExactlyInAnyOrder( // GH-90000
                "data-cloud:agent.data-cloud.schema-validator",
                "data-cloud:agent.data-cloud.data-sync",
                "data-cloud:agent.data-cloud.anomaly-detector");
    }

    @Test
    @DisplayName("creates SchemaValidatorAgent for schema-validator ref [GH-90000]")
    void createsSchemaValidatorAgent() { // GH-90000
        AgentConfig config = mock(AgentConfig.class); // GH-90000
        TypedAgent<?, ?> agent = provider.createAgent( // GH-90000
                "data-cloud:agent.data-cloud.schema-validator", config);
        assertThat(agent).isInstanceOf(SchemaValidatorAgent.class); // GH-90000
    }

    @Test
    @DisplayName("creates DataSyncAgent for data-sync ref [GH-90000]")
    void createsDataSyncAgent() { // GH-90000
        AgentConfig config = mock(AgentConfig.class); // GH-90000
        TypedAgent<?, ?> agent = provider.createAgent( // GH-90000
                "data-cloud:agent.data-cloud.data-sync", config);
        assertThat(agent).isInstanceOf(DataSyncAgent.class); // GH-90000
    }

    @Test
    @DisplayName("creates DataAnomalyDetectorAgent for anomaly-detector ref [GH-90000]")
    void createsDataAnomalyDetectorAgent() { // GH-90000
        AgentConfig config = mock(AgentConfig.class); // GH-90000
        TypedAgent<?, ?> agent = provider.createAgent( // GH-90000
                "data-cloud:agent.data-cloud.anomaly-detector", config);
        assertThat(agent).isInstanceOf(DataAnomalyDetectorAgent.class); // GH-90000
    }

    @Test
    @DisplayName("throws IllegalArgumentException for unknown ref [GH-90000]")
    void throwsForUnknownRef() { // GH-90000
        AgentConfig config = mock(AgentConfig.class); // GH-90000
        assertThatThrownBy(() -> // GH-90000
                provider.createAgent("data-cloud:agent.unknown", config)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("Unsupported implementationRef [GH-90000]");
    }

    @Test
    @DisplayName("registerFactory adds a new ref [GH-90000]")
    void registerFactoryAddsRef() { // GH-90000
        AgentConfig config = mock(AgentConfig.class); // GH-90000
        provider.registerFactory("data-cloud:agent.custom", c -> new SchemaValidatorAgent()); // GH-90000

        assertThat(provider.getSupportedRefs()).contains("data-cloud:agent.custom [GH-90000]");
        assertThat(provider.createAgent("data-cloud:agent.custom", config)) // GH-90000
                .isInstanceOf(SchemaValidatorAgent.class); // GH-90000
    }
}
