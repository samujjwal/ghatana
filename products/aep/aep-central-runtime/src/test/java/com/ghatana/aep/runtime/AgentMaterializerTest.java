/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.runtime;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.config.AgentConfigMaterializer;
import com.ghatana.agent.spi.AgentLogicProvider;
import com.ghatana.agent.spi.AgentLogicProviderRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AgentMaterializer [GH-90000]")
class AgentMaterializerTest {

    @Mock
    private AgentConfigMaterializer configMaterializer;

    @Mock
    private AgentLogicProviderRegistry providerRegistry;

    @Mock
        private TypedAgent<Object, Object> typedAgent;

    @Mock
    private AgentLogicProvider provider;

    @Test
    @DisplayName("constructor rejects null dependencies [GH-90000]")
    void constructorRejectsNullDependencies() { // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> new AgentMaterializer(null, providerRegistry)); // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> new AgentMaterializer(configMaterializer, null)); // GH-90000
    }

    @Test
    @DisplayName("materialize config uses implementationRef from config [GH-90000]")
    void materializeConfigUsesImplementationRef() { // GH-90000
        AgentConfig config = AgentConfig.builder() // GH-90000
                .agentId("agent-1 [GH-90000]")
                .type(AgentType.DETERMINISTIC) // GH-90000
                .implementationRef("provider-x:demo [GH-90000]")
                .build(); // GH-90000
        doReturn(typedAgent).when(providerRegistry).createAgent("provider-x:demo", config); // GH-90000

        AgentMaterializer materializer = new AgentMaterializer(configMaterializer, providerRegistry); // GH-90000

        TypedAgent<?, ?> result = materializer.materialize(config); // GH-90000

        assertThat(result).isSameAs(typedAgent); // GH-90000
    }

    @Test
    @DisplayName("materialize config falls back to properties implementationRef [GH-90000]")
    void materializeConfigFallsBackToProperties() { // GH-90000
        AgentConfig config = AgentConfig.builder() // GH-90000
                .agentId("agent-2 [GH-90000]")
                .type(AgentType.DETERMINISTIC) // GH-90000
                .properties(Map.of("implementationRef", "provider-y:fallback")) // GH-90000
                .build(); // GH-90000
        doReturn(typedAgent).when(providerRegistry).createAgent("provider-y:fallback", config); // GH-90000

        AgentMaterializer materializer = new AgentMaterializer(configMaterializer, providerRegistry); // GH-90000

        TypedAgent<?, ?> result = materializer.materialize(config); // GH-90000

        assertThat(result).isSameAs(typedAgent); // GH-90000
    }

    @Test
    @DisplayName("materialize config fails when implementationRef is missing everywhere [GH-90000]")
    void materializeConfigFailsWhenRefMissing() { // GH-90000
        AgentConfig config = AgentConfig.builder() // GH-90000
                .agentId("agent-3 [GH-90000]")
                .type(AgentType.DETERMINISTIC) // GH-90000
                .build(); // GH-90000

        AgentMaterializer materializer = new AgentMaterializer(configMaterializer, providerRegistry); // GH-90000

        assertThatIllegalStateException() // GH-90000
                .isThrownBy(() -> materializer.materialize(config)) // GH-90000
                .withMessageContaining("has no implementationRef [GH-90000]");
    }

    @Test
    @DisplayName("explicit ref materialization bypasses config implementationRef [GH-90000]")
    void explicitRefBypassesConfigRef() { // GH-90000
        AgentConfig config = AgentConfig.builder() // GH-90000
                .agentId("agent-4 [GH-90000]")
                .type(AgentType.DETERMINISTIC) // GH-90000
                .implementationRef("ignored:old [GH-90000]")
                .build(); // GH-90000
        doReturn(typedAgent).when(providerRegistry).createAgent("provider-z:explicit", config); // GH-90000

        AgentMaterializer materializer = new AgentMaterializer(configMaterializer, providerRegistry); // GH-90000

        TypedAgent<?, ?> result = materializer.materialize("provider-z:explicit", config); // GH-90000

        assertThat(result).isSameAs(typedAgent); // GH-90000
        verify(providerRegistry).createAgent("provider-z:explicit", config); // GH-90000
    }

    @Test
    @DisplayName("explicit ref materialization rejects null ref [GH-90000]")
    void explicitRefRejectsNull() { // GH-90000
        AgentConfig config = AgentConfig.builder() // GH-90000
                .agentId("agent-5 [GH-90000]")
                .type(AgentType.DETERMINISTIC) // GH-90000
                .build(); // GH-90000

        AgentMaterializer materializer = new AgentMaterializer(configMaterializer, providerRegistry); // GH-90000

        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> materializer.materialize(null, config)); // GH-90000
    }

    @Test
    @DisplayName("canMaterialize delegates to provider registry [GH-90000]")
    void canMaterializeDelegatesToRegistry() { // GH-90000
        when(providerRegistry.resolve("provider-a:agent [GH-90000]")).thenReturn(Optional.of(provider));
        when(providerRegistry.resolve("missing:agent [GH-90000]")).thenReturn(Optional.empty());

        AgentMaterializer materializer = new AgentMaterializer(configMaterializer, providerRegistry); // GH-90000

        assertThat(materializer.canMaterialize("provider-a:agent [GH-90000]")).isTrue();
        assertThat(materializer.canMaterialize("missing:agent [GH-90000]")).isFalse();
        assertThat(materializer.getProviderRegistry()).isSameAs(providerRegistry); // GH-90000
    }
}
