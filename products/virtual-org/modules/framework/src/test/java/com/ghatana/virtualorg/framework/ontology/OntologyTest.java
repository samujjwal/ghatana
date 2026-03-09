package com.ghatana.virtualorg.framework.ontology;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the Organizational Ontology system.
 */
@DisplayName("Ontology Tests")
class OntologyTest {

    private Ontology ontology;

    @BeforeEach
    void setUp() {
        ontology = new Ontology();
    }

    @Test
    @DisplayName("Should register and retrieve concepts")
    void shouldRegisterAndRetrieveConcepts() {
        Concept agentConcept = Concept.builder("agent", "Agent")
                .description("A software agent that performs actions")
                .build();

        ontology.defineSync(agentConcept);

        Optional<Concept> retrieved = ontology.get("agent").getResult();
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().name()).isEqualTo("Agent");
    }

    @Test
    @DisplayName("Should build concept hierarchy")
    void shouldBuildConceptHierarchy() {
        Concept entity = Concept.builder("entity", "Entity")
                .description("Base entity")
                .build();

        Concept agent = Concept.builder("agent", "Agent")
                .parent("entity")
                .description("An agent")
                .build();

        Concept developer = Concept.builder("developer", "Developer")
                .parent("agent")
                .description("A software developer agent")
                .build();

        ontology.defineSync(entity);
        ontology.defineSync(agent);
        ontology.defineSync(developer);

        List<Concept> ancestors = ontology.getAncestors("developer").getResult();
        assertThat(ancestors).hasSize(2);
        assertThat(ancestors.stream().map(Concept::id))
                .containsExactly("agent", "entity");
    }

    @Test
    @DisplayName("Should get subconcepts of a parent")
    void shouldGetSubconcepts() {
        Concept entity = Concept.builder("entity", "Entity").build();
        Concept agent = Concept.builder("agent", "Agent").parent("entity").build();
        Concept developer = Concept.builder("developer", "Developer").parent("agent").build();
        Concept architect = Concept.builder("architect", "Architect").parent("agent").build();

        ontology.defineSync(entity);
        ontology.defineSync(agent);
        ontology.defineSync(developer);
        ontology.defineSync(architect);

        List<Concept> agentChildren = ontology.getSubConcepts("agent").getResult();
        assertThat(agentChildren).hasSize(2);
        assertThat(agentChildren.stream().map(Concept::id))
                .containsExactlyInAnyOrder("developer", "architect");
    }

    @Test
    @DisplayName("Should check descendant relationships")
    void shouldCheckDescendantRelationship() {
        Concept entity = Concept.builder("entity", "Entity").build();
        Concept agent = Concept.builder("agent", "Agent").parent("entity").build();
        Concept developer = Concept.builder("developer", "Developer").parent("agent").build();

        ontology.defineSync(entity);
        ontology.defineSync(agent);
        ontology.defineSync(developer);

        assertThat(ontology.isDescendantOf("developer", "agent").getResult()).isTrue();
        assertThat(ontology.isDescendantOf("developer", "entity").getResult()).isTrue();
        assertThat(ontology.isDescendantOf("agent", "developer").getResult()).isFalse();
    }

    @Test
    @DisplayName("Should resolve synonyms")
    void shouldResolveSynonyms() {
        Concept codeReview = Concept.builder("code-review", "CodeReview")
                .synonyms("PR Review", "Pull Request Review")
                .build();

        ontology.defineSync(codeReview);

        Optional<Concept> resolved = ontology.resolve("pr review").getResult();
        assertThat(resolved).isPresent();
        assertThat(resolved.get().id()).isEqualTo("code-review");
    }

    @Test
    @DisplayName("Should create ontology with core concepts")
    void shouldCreateOntologyWithCoreConcepts() {
        Ontology coreOntology = Ontology.withCoreConceptsAsync();

        // Core ontology should have basic organizational concepts
        Optional<Concept> agent = coreOntology.resolve("agent").getResult();
        assertThat(agent).isPresent();
    }
}
