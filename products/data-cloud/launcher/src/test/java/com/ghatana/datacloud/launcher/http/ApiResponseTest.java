/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ApiResponse} — the canonical JSON response envelope.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the ApiResponse envelope factory and serialisation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ApiResponse – canonical response envelope [GH-90000]")
class ApiResponseTest {

    private final ObjectMapper mapper = new ObjectMapper(); // GH-90000

    // ──────────────────── Factory methods ────────────────────

    @Nested
    @DisplayName("success() [GH-90000]")
    class SuccessTests {

        @Test
        @DisplayName("sets data field and populates meta block [GH-90000]")
        void success_populatesDataAndMeta() { // GH-90000
            Map<String, Object> data = Map.of("key", "value"); // GH-90000
            ApiResponse resp = ApiResponse.success(data, "tenant-1", "req-abc"); // GH-90000

            assertThat(resp.getData()).isEqualTo(data); // GH-90000
            assertThat(resp.getError()).isNull(); // GH-90000
            assertThat(resp.getMeta()).isNotNull(); // GH-90000
            assertThat(resp.getMeta().getTenantId()).isEqualTo("tenant-1 [GH-90000]");
            assertThat(resp.getMeta().getRequestId()).isEqualTo("req-abc [GH-90000]");
            assertThat(resp.getMeta().getApiVersion()).isEqualTo("v1 [GH-90000]");
            assertThat(resp.getMeta().getTimestamp()).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("error() – without details [GH-90000]")
    class ErrorTests {

        @Test
        @DisplayName("sets error block and leaves data null [GH-90000]")
        void error_populatesErrorBlock() { // GH-90000
            ApiResponse resp = ApiResponse.error("NOT_FOUND", "Entity not found", "tenant-2", "req-xyz"); // GH-90000

            assertThat(resp.getData()).isNull(); // GH-90000
            assertThat(resp.getError()).isNotNull(); // GH-90000
            assertThat(resp.getError().getCode()).isEqualTo("NOT_FOUND [GH-90000]");
            assertThat(resp.getError().getMessage()).isEqualTo("Entity not found [GH-90000]");
            assertThat(resp.getError().getDetails()).isNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("error() – with details map [GH-90000]")
    class ErrorWithDetailsTests {

        @Test
        @DisplayName("includes details map in error block [GH-90000]")
        void errorWithDetails_includesDetails() { // GH-90000
            Map<String, Object> details = Map.of("field", "email", "constraint", "format"); // GH-90000
            ApiResponse resp = ApiResponse.error("VALIDATION", "Bad input", details, "t1", "r1"); // GH-90000

            assertThat(resp.getError().getDetails()).isEqualTo(details); // GH-90000
        }
    }

    // ──────────────────── withAiMeta ────────────────────

    @Nested
    @DisplayName("withAiMeta() [GH-90000]")
    class AiMetaTests {

        @Test
        @DisplayName("creates new instance with ai block populated [GH-90000]")
        void withAiMeta_populatesAiBlock() { // GH-90000
            ApiResponse base = ApiResponse.success(Map.of("x", 1), "t", "r"); // GH-90000
            ApiResponse result = base.withAiMeta(0.85, "gpt-4o", java.util.List.of("stop [GH-90000]"), false);

            assertThat(result.getAi()).isNotNull(); // GH-90000
            assertThat(result.getAi().getConfidence()).isEqualTo(0.85); // GH-90000
            assertThat(result.getAi().getModel()).isEqualTo("gpt-4o [GH-90000]");
            assertThat(result.getAi().isFallback()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("does not mutate the original instance [GH-90000]")
        void withAiMeta_doesNotMutateOriginal() { // GH-90000
            ApiResponse base = ApiResponse.success(Map.of("x", 1), "t", "r"); // GH-90000
            ApiResponse updated = base.withAiMeta(0.5, "model", java.util.List.of(), true); // GH-90000

            // original should have no ai block
            assertThat(base.getAi()).isNull(); // GH-90000
            assertThat(updated.getAi()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("fallback flag correctly passed through [GH-90000]")
        void withAiMeta_fallbackFlag() { // GH-90000
            ApiResponse resp = ApiResponse.success(Map.of(), "t", "r") // GH-90000
                .withAiMeta(0.20, "heuristic", java.util.List.of("keyword-fallback [GH-90000]"), true);

            assertThat(resp.getAi().isFallback()).isTrue(); // GH-90000
            assertThat(resp.getAi().getConfidence()).isEqualTo(0.20); // GH-90000
        }
    }

    // ──────────────────── toJson / serialisation ────────────────────

    @Nested
    @DisplayName("toJson() [GH-90000]")
    class SerializationTests {

        @Test
        @DisplayName("serialises to valid JSON with all blocks [GH-90000]")
        @SuppressWarnings("unchecked [GH-90000]")
        void toJson_serialisesAllBlocks() throws Exception { // GH-90000
            ApiResponse resp = ApiResponse.success(Map.of("id", "42"), "t1", "r1") // GH-90000
                .withAiMeta(0.9, "gpt-4o", java.util.List.of("stop [GH-90000]"), false);

            String json = resp.toJson(mapper); // GH-90000
            Map<String, Object> parsed = mapper.readValue(json, Map.class); // GH-90000

            assertThat(parsed).containsKey("data [GH-90000]");
            assertThat(parsed).containsKey("meta [GH-90000]");
            assertThat(parsed).containsKey("ai [GH-90000]");
            assertThat(parsed).doesNotContainKey("error [GH-90000]");
        }

        @Test
        @DisplayName("error response omits data and ai blocks [GH-90000]")
        @SuppressWarnings("unchecked [GH-90000]")
        void toJson_errorOmitsDataAndAi() throws Exception { // GH-90000
            ApiResponse resp = ApiResponse.error("ERR", "something broke", "t", "r"); // GH-90000
            String json = resp.toJson(mapper); // GH-90000
            Map<String, Object> parsed = mapper.readValue(json, Map.class); // GH-90000

            assertThat(parsed).containsKey("error [GH-90000]");
            assertThat(parsed).doesNotContainKey("data [GH-90000]");
            assertThat(parsed).doesNotContainKey("ai [GH-90000]");
        }
    }

    // ──────────────────── EndpointSensitivity classification ────────────────────

    @Nested
    @DisplayName("EndpointSensitivity.classify() [GH-90000]")
    class SensitivityTests {

        @Test
        @DisplayName("health path → PUBLIC [GH-90000]")
        void healthPath_isPublic() { // GH-90000
            assertThat(EndpointSensitivity.classify("GET", "/health")) // GH-90000
                .isEqualTo(EndpointSensitivity.PUBLIC); // GH-90000
        }

        @Test
        @DisplayName("metrics path → PUBLIC [GH-90000]")
        void metricsPath_isPublic() { // GH-90000
            assertThat(EndpointSensitivity.classify("GET", "/metrics")) // GH-90000
                .isEqualTo(EndpointSensitivity.PUBLIC); // GH-90000
        }

        @Test
        @DisplayName("DELETE entity → CRITICAL [GH-90000]")
        void deleteEntity_isCritical() { // GH-90000
            assertThat(EndpointSensitivity.classify("DELETE", "/api/v1/entities/users/abc")) // GH-90000
                .isEqualTo(EndpointSensitivity.CRITICAL); // GH-90000
        }

        @Test
        @DisplayName("governance path → CRITICAL [GH-90000]")
        void governancePath_isCritical() { // GH-90000
            assertThat(EndpointSensitivity.classify("POST", "/api/v1/governance/retention/classify")) // GH-90000
                .isEqualTo(EndpointSensitivity.CRITICAL); // GH-90000
        }

        @Test
        @DisplayName("memory read path → SENSITIVE [GH-90000]")
        void memoryPath_isSensitive() { // GH-90000
            assertThat(EndpointSensitivity.classify("GET", "/api/v1/memory/agent1")) // GH-90000
            .isEqualTo(EndpointSensitivity.SENSITIVE); // GH-90000
        }

        @Test
        @DisplayName("analytics path → SENSITIVE [GH-90000]")
        void analyticsPath_isSensitive() { // GH-90000
            assertThat(EndpointSensitivity.classify("POST", "/api/v1/analytics/query")) // GH-90000
                .isEqualTo(EndpointSensitivity.SENSITIVE); // GH-90000
        }

        @Test
        @DisplayName("pipeline path → INTERNAL for GET (read-only) [GH-90000]")
        void pipelinePath_isInternalForGet() { // GH-90000
            assertThat(EndpointSensitivity.classify("GET", "/api/v1/pipelines")) // GH-90000
                .isEqualTo(EndpointSensitivity.INTERNAL); // GH-90000
        }

        @Test
        @DisplayName("pipeline POST → SENSITIVE (mutation) [GH-90000]")
        void pipelinePost_isSensitive() { // GH-90000
            assertThat(EndpointSensitivity.classify("POST", "/api/v1/pipelines")) // GH-90000
                .isEqualTo(EndpointSensitivity.SENSITIVE); // GH-90000
        }

        @Test
        @DisplayName("entity list → INTERNAL [GH-90000]")
        void entityListPath_isInternal() { // GH-90000
            assertThat(EndpointSensitivity.classify("GET", "/api/v1/entities/users")) // GH-90000
                .isEqualTo(EndpointSensitivity.INTERNAL); // GH-90000
        }
    }

    // ──────────────────── VoiceIntentCatalog ────────────────────

    @Nested
    @DisplayName("VoiceIntentCatalog [GH-90000]")
    class VoiceIntentCatalogTests {

        @Test
        @DisplayName("findByName() returns exact match case-insensitively [GH-90000]")
        void findByName_exactMatch() { // GH-90000
            assertThat(VoiceIntentCatalog.findByName("LIST_PIPELINES [GH-90000]")).isPresent();
            assertThat(VoiceIntentCatalog.findByName("list_pipelines [GH-90000]")).isPresent();
        }

        @Test
        @DisplayName("findByName() returns empty for unknown name [GH-90000]")
        void findByName_unknown() { // GH-90000
            assertThat(VoiceIntentCatalog.findByName("nonexistent_intent [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("findCandidates() returns non-empty list for 'pipeline' keyword [GH-90000]")
        void findCandidates_pipelineKeyword() { // GH-90000
            assertThat(VoiceIntentCatalog.findCandidates("show me all pipelines [GH-90000]")).isNotEmpty();
        }

        @Test
        @DisplayName("ALL catalog contains at least 20 intents [GH-90000]")
        void allCatalog_hasMinimumSize() { // GH-90000
            assertThat(VoiceIntentCatalog.ALL).hasSizeGreaterThanOrEqualTo(20); // GH-90000
        }

        @Test
        @DisplayName("resolvePath() replaces :param placeholders [GH-90000]")
        void resolvePath_replacesParams() { // GH-90000
            VoiceIntentCatalog.VoiceIntent intent = VoiceIntentCatalog.findByName("get_entity [GH-90000]").orElseThrow();
            String resolved = intent.resolvePath(Map.of("collection", "users", "id", "abc123")); // GH-90000
            assertThat(resolved).isEqualTo("/api/v1/entities/users/abc123 [GH-90000]");
        }

        @Test
        @DisplayName("missingRequiredParams() returns list of missing keys [GH-90000]")
        void missingRequiredParams_returnsMissing() { // GH-90000
            VoiceIntentCatalog.VoiceIntent intent = VoiceIntentCatalog.findByName("get_entity [GH-90000]").orElseThrow();
            var missing = intent.missingRequiredParams(Map.of("collection", "users")); // missing "id" // GH-90000
            assertThat(missing).contains("id [GH-90000]");
        }
    }
}
