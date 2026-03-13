package com.ghatana.virtualorg.framework;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.virtualorg.framework.agent.AgentRegistry;
import com.ghatana.virtualorg.framework.cnp.TaskMarket;
import com.ghatana.virtualorg.framework.ontology.Ontology;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for VirtualOrgContext - the main orchestration container.
 */
@DisplayName("VirtualOrgContext Tests")
class VirtualOrgContextTest extends EventloopTestBase {

    @Test
    @DisplayName("Should create context with all components using builder")
    void shouldCreateContextWithBuilder() {
        VirtualOrgContext context = VirtualOrgContext.builder(eventloop())
                .build();

        assertThat(context.getAgentRegistry()).isNotNull();
        assertThat(context.getTaskMarket()).isNotNull();
        assertThat(context.getNormRegistry()).isNotNull();
        assertThat(context.getOntology()).isNotNull();
        assertThat(context.getTemplateRegistry()).isNotNull();
    }

    @Test
    @DisplayName("Should create context with custom components")
    void shouldCreateContextWithCustomComponents() {
        AgentRegistry customRegistry = new AgentRegistry();
        TaskMarket customMarket = new TaskMarket();
        Ontology customOntology = Ontology.withCoreConceptsAsync();

        VirtualOrgContext context = VirtualOrgContext.builder(eventloop())
                .agentRegistry(customRegistry)
                .taskMarket(customMarket)
                .ontology(customOntology)
                .build();

        assertThat(context.getAgentRegistry()).isSameAs(customRegistry);
        assertThat(context.getTaskMarket()).isSameAs(customMarket);
        assertThat(context.getOntology()).isSameAs(customOntology);
    }

    @Test
    @DisplayName("Should initialize and shutdown properly")
    void shouldInitializeAndShutdown() {
        VirtualOrgContext context = VirtualOrgContext.builder(eventloop())
                .build();

        runPromise(() -> context.initialize());
        assertThat(context.isInitialized()).isTrue();

        runPromise(() -> context.shutdown());
        assertThat(context.isInitialized()).isFalse();
    }

    @Test
    @DisplayName("Should have default values when not specified")
    void shouldHaveDefaultValues() {
        VirtualOrgContext context = VirtualOrgContext.builder(eventloop()).build();

        assertThat(context.getAgentRegistry()).isNotNull();
        assertThat(context.getNormRegistry()).isNotNull();
    }
}
