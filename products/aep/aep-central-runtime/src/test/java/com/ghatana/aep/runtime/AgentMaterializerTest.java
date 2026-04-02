/*
 * Copyright (c) 2026 Ghatana Inc.
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

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentMaterializer")
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
    @DisplayName("constructor rejects null dependencies")
    void constructorRejectsNullDependencies() {
        assertThatNullPointerException()
                .isThrownBy(() -> new AgentMaterializer(null, providerRegistry));
        assertThatNullPointerException()
                .isThrownBy(() -> new AgentMaterializer(configMaterializer, null));
    }

    @Test
    @DisplayName("materialize config uses implementationRef from config")
    void materializeConfigUsesImplementationRef() {
        AgentConfig config = AgentConfig.builder()
                .agentId("agent-1")
                .type(AgentType.DETERMINISTIC)
                .implementationRef("provider-x:demo")
                .build();
        doReturn(typedAgent).when(providerRegistry).createAgent("provider-x:demo", config);

        AgentMaterializer materializer = new AgentMaterializer(configMaterializer, providerRegistry);

        TypedAgent<?, ?> result = materializer.materialize(config);

        assertThat(result).isSameAs(typedAgent);
    }

    @Test
    @DisplayName("materialize config falls back to properties implementationRef")
    void materializeConfigFallsBackToProperties() {
        AgentConfig config = AgentConfig.builder()
                .agentId("agent-2")
                .type(AgentType.DETERMINISTIC)
                .properties(Map.of("implementationRef", "provider-y:fallback"))
                .build();
        doReturn(typedAgent).when(providerRegistry).createAgent("provider-y:fallback", config);

        AgentMaterializer materializer = new AgentMaterializer(configMaterializer, providerRegistry);

        TypedAgent<?, ?> result = materializer.materialize(config);

        assertThat(result).isSameAs(typedAgent);
    }

    @Test
    @DisplayName("materialize config fails when implementationRef is missing everywhere")
    void materializeConfigFailsWhenRefMissing() {
        AgentConfig config = AgentConfig.builder()
                .agentId("agent-3")
                .type(AgentType.DETERMINISTIC)
                .build();

        AgentMaterializer materializer = new AgentMaterializer(configMaterializer, providerRegistry);

        assertThatIllegalStateException()
                .isThrownBy(() -> materializer.materialize(config))
                .withMessageContaining("has no implementationRef");
    }

    @Test
    @DisplayName("explicit ref materialization bypasses config implementationRef")
    void explicitRefBypassesConfigRef() {
        AgentConfig config = AgentConfig.builder()
                .agentId("agent-4")
                .type(AgentType.DETERMINISTIC)
                .implementationRef("ignored:old")
                .build();
        doReturn(typedAgent).when(providerRegistry).createAgent("provider-z:explicit", config);

        AgentMaterializer materializer = new AgentMaterializer(configMaterializer, providerRegistry);

        TypedAgent<?, ?> result = materializer.materialize("provider-z:explicit", config);

        assertThat(result).isSameAs(typedAgent);
        verify(providerRegistry).createAgent("provider-z:explicit", config);
    }

    @Test
    @DisplayName("explicit ref materialization rejects null ref")
    void explicitRefRejectsNull() {
        AgentConfig config = AgentConfig.builder()
                .agentId("agent-5")
                .type(AgentType.DETERMINISTIC)
                .build();

        AgentMaterializer materializer = new AgentMaterializer(configMaterializer, providerRegistry);

        assertThatNullPointerException()
                .isThrownBy(() -> materializer.materialize(null, config));
    }

    @Test
    @DisplayName("canMaterialize delegates to provider registry")
    void canMaterializeDelegatesToRegistry() {
        when(providerRegistry.resolve("provider-a:agent")).thenReturn(Optional.of(provider));
        when(providerRegistry.resolve("missing:agent")).thenReturn(Optional.empty());

        AgentMaterializer materializer = new AgentMaterializer(configMaterializer, providerRegistry);

        assertThat(materializer.canMaterialize("provider-a:agent")).isTrue();
        assertThat(materializer.canMaterialize("missing:agent")).isFalse();
        assertThat(materializer.getProviderRegistry()).isSameAs(providerRegistry);
    }
}