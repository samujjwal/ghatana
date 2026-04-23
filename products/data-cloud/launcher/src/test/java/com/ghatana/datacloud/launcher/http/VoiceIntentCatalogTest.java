/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 */
package com.ghatana.datacloud.launcher.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-level tests for {@link VoiceIntentCatalog} covering all top-10 operational
 * intents, path resolution, keyword classification, and sensitivity classification.
 *
 * <p>These tests run in-process without an HTTP server — they validate the intent
 * definitions, keyword heuristic, and parameter validation logic independently
 * of LLM availability.
 *
 * @doc.type class
 * @doc.purpose Unit tests for VoiceIntentCatalog top-10 intents (DC-E4 Sprint 4) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("VoiceIntentCatalog – top-10 intent coverage + keyword classification")
class VoiceIntentCatalogTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Catalog completeness
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Catalog structure")
    class CatalogStructureTests {

        @Test
        @DisplayName("contains at least 20 registered intents")
        void containsAtLeast20Intents() { // GH-90000
            assertThat(VoiceIntentCatalog.ALL).hasSizeGreaterThanOrEqualTo(20); // GH-90000
        }

        @Test
        @DisplayName("all intent names are unique")
        void allIntentNamesAreUnique() { // GH-90000
            List<String> names = VoiceIntentCatalog.ALL.stream() // GH-90000
                .map(VoiceIntentCatalog.VoiceIntent::name) // GH-90000
                .toList(); // GH-90000
            Set<String> distinct = Set.copyOf(names); // GH-90000
            assertThat(distinct).hasSameSizeAs(names); // GH-90000
        }

        @Test
        @DisplayName("top-10 critical operational intents are present")
        void top10IntentsPresent() { // GH-90000
            Set<String> required = Set.of( // GH-90000
                "query_entities",
                "query_events",
                "list_pipelines",
                "get_pipeline_status",
                "list_agents",
                "run_analytics_query",
                "get_workspace_spotlight",
                "search_agent_memory",
                "trigger_learning",
                "list_models"
            );
            Set<String> catalogNames = VoiceIntentCatalog.ALL.stream() // GH-90000
                .map(VoiceIntentCatalog.VoiceIntent::name) // GH-90000
                .collect(Collectors.toSet()); // GH-90000
            assertThat(catalogNames).containsAll(required); // GH-90000
        }

        @Test
        @DisplayName("every intent has non-blank name, description, httpMethod, pathTemplate")
        void everyIntentHasRequiredFields() { // GH-90000
            for (VoiceIntentCatalog.VoiceIntent intent : VoiceIntentCatalog.ALL) { // GH-90000
                assertThat(intent.name()).as("name must not be blank").isNotBlank();
                assertThat(intent.description()).as("description for " + intent.name()).isNotBlank(); // GH-90000
                assertThat(intent.httpMethod()).as("httpMethod for " + intent.name()) // GH-90000
                    .isIn("GET", "POST", "PUT", "DELETE", "PATCH"); // GH-90000
                assertThat(intent.pathTemplate()).as("pathTemplate for " + intent.name()) // GH-90000
                    .startsWith("/api/v1/");
                assertThat(intent.sensitivity()).as("sensitivity for " + intent.name()).isNotNull(); // GH-90000
            }
        }

        @Test
        @DisplayName("CRITICAL intents are destructive mutations (DELETE or promote/approve/reject paths)")
        void criticalIntentsAreMutations() { // GH-90000
            VoiceIntentCatalog.ALL.stream() // GH-90000
                .filter(i -> i.sensitivity() == EndpointSensitivity.CRITICAL) // GH-90000
                .forEach(i -> assertThat( // GH-90000
                    i.httpMethod().equals("DELETE") ||
                    i.pathTemplate().contains("promote") ||
                    i.pathTemplate().contains("approve") ||
                    i.pathTemplate().contains("reject"))
                    .as("intent " + i.name() + " is CRITICAL but not a destructive mutation") // GH-90000
                    .isTrue()); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Top-10 intent: path resolution
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Top-10 intent path resolution")
    class PathResolutionTests {

        @Test
        @DisplayName("query_entities resolves collection parameter")
        void queryEntities_resolvesCollection() { // GH-90000
            Optional<VoiceIntentCatalog.VoiceIntent> intent = VoiceIntentCatalog.findByName("query_entities");
            assertThat(intent).isPresent(); // GH-90000
            String path = intent.get().resolvePath(Map.of("collection", "orders")); // GH-90000
            assertThat(path).isEqualTo("/api/v1/entities/orders");
        }

        @Test
        @DisplayName("get_pipeline_status resolves pipelineId parameter")
        void getPipelineStatus_resolvesPipelineId() { // GH-90000
            Optional<VoiceIntentCatalog.VoiceIntent> intent = VoiceIntentCatalog.findByName("get_pipeline_status");
            assertThat(intent).isPresent(); // GH-90000
            String path = intent.get().resolvePath(Map.of("pipelineId", "etl-daily-123")); // GH-90000
            assertThat(path).isEqualTo("/api/v1/pipelines/etl-daily-123");
        }

        @Test
        @DisplayName("search_agent_memory resolves agentId and retains query in params")
        void searchAgentMemory_resolvesAgentId() { // GH-90000
            Optional<VoiceIntentCatalog.VoiceIntent> intent = VoiceIntentCatalog.findByName("search_agent_memory");
            assertThat(intent).isPresent(); // GH-90000
            String path = intent.get().resolvePath(Map.of("agentId", "agent-42", "query", "orders")); // GH-90000
            assertThat(path).isEqualTo("/api/v1/memory/agent-42/search");
        }

        @Test
        @DisplayName("run_analytics_query has POST method and correct path")
        void runAnalyticsQuery_isPost() { // GH-90000
            Optional<VoiceIntentCatalog.VoiceIntent> intent = VoiceIntentCatalog.findByName("run_analytics_query");
            assertThat(intent).isPresent(); // GH-90000
            assertThat(intent.get().httpMethod()).isEqualTo("POST");
            assertThat(intent.get().pathTemplate()).isEqualTo("/api/v1/analytics/query");
        }

        @Test
        @DisplayName("trigger_learning is SENSITIVE (mutation, not CRITICAL)")
        void triggerLearning_isSensitive() { // GH-90000
            Optional<VoiceIntentCatalog.VoiceIntent> intent = VoiceIntentCatalog.findByName("trigger_learning");
            assertThat(intent).isPresent(); // GH-90000
            assertThat(intent.get().sensitivity()).isEqualTo(EndpointSensitivity.SENSITIVE); // GH-90000
        }

        @Test
        @DisplayName("list_pipelines requires no mandatory params")
        void listPipelines_noRequiredParams() { // GH-90000
            Optional<VoiceIntentCatalog.VoiceIntent> intent = VoiceIntentCatalog.findByName("list_pipelines");
            assertThat(intent).isPresent(); // GH-90000
            assertThat(intent.get().requiredParams()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("get_workspace_spotlight has SENSITIVE classification")
        void getWorkspaceSpotlight_isSensitive() { // GH-90000
            Optional<VoiceIntentCatalog.VoiceIntent> intent = VoiceIntentCatalog.findByName("get_workspace_spotlight");
            assertThat(intent).isPresent(); // GH-90000
            assertThat(intent.get().sensitivity()).isEqualTo(EndpointSensitivity.SENSITIVE); // GH-90000
        }

        @Test
        @DisplayName("list_models requires no mandatory params and is INTERNAL")
        void listModels_noRequiredParamsIsInternal() { // GH-90000
            Optional<VoiceIntentCatalog.VoiceIntent> intent = VoiceIntentCatalog.findByName("list_models");
            assertThat(intent).isPresent(); // GH-90000
            assertThat(intent.get().requiredParams()).isEmpty(); // GH-90000
            assertThat(intent.get().sensitivity()).isEqualTo(EndpointSensitivity.INTERNAL); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Top-10 intent: missingRequiredParams validation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Required parameter validation")
    class RequiredParamValidationTests {

        @Test
        @DisplayName("query_entities with collection present has no missing params")
        void queryEntities_withCollection_noMissing() { // GH-90000
            VoiceIntentCatalog.VoiceIntent intent = findRequired("query_entities");
            List<String> missing = intent.missingRequiredParams(Map.of("collection", "orders")); // GH-90000
            assertThat(missing).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("query_entities without collection reports missing param")
        void queryEntities_missingCollection_reportsMissing() { // GH-90000
            VoiceIntentCatalog.VoiceIntent intent = findRequired("query_entities");
            List<String> missing = intent.missingRequiredParams(Map.of()); // GH-90000
            assertThat(missing).containsExactly("collection");
        }

        @Test
        @DisplayName("search_agent_memory requires both agentId and query")
        void searchAgentMemory_requiresBothParams() { // GH-90000
            VoiceIntentCatalog.VoiceIntent intent = findRequired("search_agent_memory");
            assertThat(intent.missingRequiredParams(Map.of())).containsExactlyInAnyOrder("agentId", "query"); // GH-90000
            assertThat(intent.missingRequiredParams(Map.of("agentId", "a1"))).containsExactly("query");
            assertThat(intent.missingRequiredParams(Map.of("agentId", "a1", "query", "q"))).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("list_pipelines with empty params has no missing params (no required)")
        void listPipelines_noRequiredParams_neverMissing() { // GH-90000
            VoiceIntentCatalog.VoiceIntent intent = findRequired("list_pipelines");
            assertThat(intent.missingRequiredParams(Map.of())).isEmpty(); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findByName lookup
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByName()")
    class FindByNameTests {

        @ParameterizedTest
        @ValueSource(strings = { // GH-90000
            "query_entities", "query_events", "list_pipelines", "get_pipeline_status",
            "list_agents", "run_analytics_query", "get_workspace_spotlight",
            "search_agent_memory", "trigger_learning", "list_models"
        })
        @DisplayName("finds all top-10 intents by exact name")
        void findsTop10IntentsByExactName(String name) { // GH-90000
            assertThat(VoiceIntentCatalog.findByName(name)).isPresent(); // GH-90000
        }

        @Test
        @DisplayName("lookup is case-insensitive")
        void lookupIsCaseInsensitive() { // GH-90000
            assertThat(VoiceIntentCatalog.findByName("LIST_PIPELINES")).isPresent();
            assertThat(VoiceIntentCatalog.findByName("Query_Entities")).isPresent();
        }

        @Test
        @DisplayName("lookup converts dashes and spaces to underscores")
        void lookupNormalisesDelimiters() { // GH-90000
            assertThat(VoiceIntentCatalog.findByName("list-pipelines")).isPresent();
            assertThat(VoiceIntentCatalog.findByName("list pipelines")).isPresent();
        }

        @Test
        @DisplayName("unknown name returns empty")
        void unknownNameReturnsEmpty() { // GH-90000
            assertThat(VoiceIntentCatalog.findByName("does_not_exist")).isEmpty();
            assertThat(VoiceIntentCatalog.findByName(null)).isEmpty(); // GH-90000
            assertThat(VoiceIntentCatalog.findByName("")).isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // findCandidates keyword heuristic
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findCandidates() keyword heuristic")
    class FindCandidatesTests {

        @Test
        @DisplayName("'list pipelines' matches list_pipelines intent")
        void matchesListPipelinesKeywords() { // GH-90000
            List<VoiceIntentCatalog.VoiceIntent> candidates =
                VoiceIntentCatalog.findCandidates("list pipelines");
            assertThat(candidates).isNotEmpty(); // GH-90000
            assertThat(candidates.stream().map(VoiceIntentCatalog.VoiceIntent::name)) // GH-90000
                .contains("list_pipelines");
        }

        @Test
        @DisplayName("'query entities in orders' matches query_entities intent")
        void matchesQueryEntitiesKeywords() { // GH-90000
            List<VoiceIntentCatalog.VoiceIntent> candidates =
                VoiceIntentCatalog.findCandidates("query entities in orders");
            assertThat(candidates).isNotEmpty(); // GH-90000
            assertThat(candidates.stream().map(VoiceIntentCatalog.VoiceIntent::name)) // GH-90000
                .contains("query_entities");
        }

        @Test
        @DisplayName("'list all agents' matches list_agents")
        void matchesListAgentsKeywords() { // GH-90000
            List<VoiceIntentCatalog.VoiceIntent> candidates =
                VoiceIntentCatalog.findCandidates("list all agents");
            assertThat(candidates).isNotEmpty(); // GH-90000
            assertThat(candidates.stream().map(VoiceIntentCatalog.VoiceIntent::name)) // GH-90000
                .contains("list_agents");
        }

        @Test
        @DisplayName("'run analytics query' matches run_analytics_query")
        void matchesRunAnalyticsQuery() { // GH-90000
            List<VoiceIntentCatalog.VoiceIntent> candidates =
                VoiceIntentCatalog.findCandidates("run analytics query");
            assertThat(candidates).isNotEmpty(); // GH-90000
            assertThat(candidates.stream().map(VoiceIntentCatalog.VoiceIntent::name)) // GH-90000
                .contains("run_analytics_query");
        }

        @Test
        @DisplayName("completely unrelated utterance returns empty or small candidate set")
        void unrelatedUtterance_returnsEmpty() { // GH-90000
            List<VoiceIntentCatalog.VoiceIntent> candidates =
                VoiceIntentCatalog.findCandidates("xyzzy frobulate the wumpus");
            // May return empty or a small number (≤1 false-positive from single-token overlap) // GH-90000
            assertThat(candidates).hasSizeLessThanOrEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("null and blank utterances return empty list")
        void nullAndBlank_returnsEmpty() { // GH-90000
            assertThat(VoiceIntentCatalog.findCandidates(null)).isEmpty(); // GH-90000
            assertThat(VoiceIntentCatalog.findCandidates("")).isEmpty();
            assertThat(VoiceIntentCatalog.findCandidates("   ")).isEmpty();
        }

        @Test
        @DisplayName("candidates list is capped at 3 entries")
        void candidatesAreCappedAt3() { // GH-90000
            // Use a broad keyword like "list" that may match many intents
            List<VoiceIntentCatalog.VoiceIntent> candidates =
                VoiceIntentCatalog.findCandidates("list status query");
            assertThat(candidates).hasSizeLessThanOrEqualTo(3); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Security: intent sensitivity coverage
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Intent sensitivity classification")
    class SensitivityTests {

        @Test
        @DisplayName("delete_entity is CRITICAL sensitivity")
        void deleteEntity_isCritical() { // GH-90000
            assertThat(findRequired("delete_entity").sensitivity())
                .isEqualTo(EndpointSensitivity.CRITICAL); // GH-90000
        }

        @Test
        @DisplayName("create_entity is SENSITIVE sensitivity")
        void createEntity_isSensitive() { // GH-90000
            assertThat(findRequired("create_entity").sensitivity())
                .isEqualTo(EndpointSensitivity.SENSITIVE); // GH-90000
        }

        @Test
        @DisplayName("query_entities is INTERNAL sensitivity (read-only)")
        void queryEntities_isInternal() { // GH-90000
            assertThat(findRequired("query_entities").sensitivity())
                .isEqualTo(EndpointSensitivity.INTERNAL); // GH-90000
        }

        @Test
        @DisplayName("all intents have non-null sensitivity")
        void allIntentsHaveNonNullSensitivity() { // GH-90000
            VoiceIntentCatalog.ALL.forEach(i -> // GH-90000
                assertThat(i.sensitivity()).as(i.name() + " must have sensitivity").isNotNull()); // GH-90000
        }

        @Test
        @DisplayName("no public intents in the voice catalog (voice requires at least INTERNAL auth)")
        void noPublicIntents() { // GH-90000
            VoiceIntentCatalog.ALL.forEach(i -> // GH-90000
                assertThat(i.sensitivity()) // GH-90000
                    .as(i.name() + " must not be PUBLIC — voice requires authentication") // GH-90000
                    .isNotEqualTo(EndpointSensitivity.PUBLIC)); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static VoiceIntentCatalog.VoiceIntent findRequired(String name) { // GH-90000
        return VoiceIntentCatalog.findByName(name) // GH-90000
            .orElseThrow(() -> new AssertionError("Intent not found: " + name)); // GH-90000
    }
}
