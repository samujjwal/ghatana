/*
 * Copyright (c) 2026 Ghatana Inc.
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
@DisplayName("ApiResponse – canonical response envelope")
class ApiResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // ──────────────────── Factory methods ────────────────────

    @Nested
    @DisplayName("success()")
    class SuccessTests {

        @Test
        @DisplayName("sets data field and populates meta block")
        void success_populatesDataAndMeta() {
            Map<String, Object> data = Map.of("key", "value");
            ApiResponse resp = ApiResponse.success(data, "tenant-1", "req-abc");

            assertThat(resp.getData()).isEqualTo(data);
            assertThat(resp.getError()).isNull();
            assertThat(resp.getMeta()).isNotNull();
            assertThat(resp.getMeta().getTenantId()).isEqualTo("tenant-1");
            assertThat(resp.getMeta().getRequestId()).isEqualTo("req-abc");
            assertThat(resp.getMeta().getApiVersion()).isEqualTo("v1");
            assertThat(resp.getMeta().getTimestamp()).isNotNull();
        }
    }

    @Nested
    @DisplayName("error() – without details")
    class ErrorTests {

        @Test
        @DisplayName("sets error block and leaves data null")
        void error_populatesErrorBlock() {
            ApiResponse resp = ApiResponse.error("NOT_FOUND", "Entity not found", "tenant-2", "req-xyz");

            assertThat(resp.getData()).isNull();
            assertThat(resp.getError()).isNotNull();
            assertThat(resp.getError().getCode()).isEqualTo("NOT_FOUND");
            assertThat(resp.getError().getMessage()).isEqualTo("Entity not found");
            assertThat(resp.getError().getDetails()).isNull();
        }
    }

    @Nested
    @DisplayName("error() – with details map")
    class ErrorWithDetailsTests {

        @Test
        @DisplayName("includes details map in error block")
        void errorWithDetails_includesDetails() {
            Map<String, Object> details = Map.of("field", "email", "constraint", "format");
            ApiResponse resp = ApiResponse.error("VALIDATION", "Bad input", details, "t1", "r1");

            assertThat(resp.getError().getDetails()).isEqualTo(details);
        }
    }

    // ──────────────────── withAiMeta ────────────────────

    @Nested
    @DisplayName("withAiMeta()")
    class AiMetaTests {

        @Test
        @DisplayName("creates new instance with ai block populated")
        void withAiMeta_populatesAiBlock() {
            ApiResponse base = ApiResponse.success(Map.of("x", 1), "t", "r");
            ApiResponse result = base.withAiMeta(0.85, "gpt-4o", java.util.List.of("stop"), false);

            assertThat(result.getAi()).isNotNull();
            assertThat(result.getAi().getConfidence()).isEqualTo(0.85);
            assertThat(result.getAi().getModel()).isEqualTo("gpt-4o");
            assertThat(result.getAi().isFallback()).isFalse();
        }

        @Test
        @DisplayName("does not mutate the original instance")
        void withAiMeta_doesNotMutateOriginal() {
            ApiResponse base = ApiResponse.success(Map.of("x", 1), "t", "r");
            base.withAiMeta(0.5, "model", java.util.List.of(), true);

            // original should have no ai block
            assertThat(base.getAi()).isNull();
        }

        @Test
        @DisplayName("fallback flag correctly passed through")
        void withAiMeta_fallbackFlag() {
            ApiResponse resp = ApiResponse.success(Map.of(), "t", "r")
                .withAiMeta(0.20, "heuristic", java.util.List.of("keyword-fallback"), true);

            assertThat(resp.getAi().isFallback()).isTrue();
            assertThat(resp.getAi().getConfidence()).isEqualTo(0.20);
        }
    }

    // ──────────────────── toJson / serialisation ────────────────────

    @Nested
    @DisplayName("toJson()")
    class SerializationTests {

        @Test
        @DisplayName("serialises to valid JSON with all blocks")
        @SuppressWarnings("unchecked")
        void toJson_serialisesAllBlocks() throws Exception {
            ApiResponse resp = ApiResponse.success(Map.of("id", "42"), "t1", "r1")
                .withAiMeta(0.9, "gpt-4o", java.util.List.of("stop"), false);

            String json = resp.toJson(mapper);
            Map<String, Object> parsed = mapper.readValue(json, Map.class);

            assertThat(parsed).containsKey("data");
            assertThat(parsed).containsKey("meta");
            assertThat(parsed).containsKey("ai");
            assertThat(parsed).doesNotContainKey("error");
        }

        @Test
        @DisplayName("error response omits data and ai blocks")
        @SuppressWarnings("unchecked")
        void toJson_errorOmitsDataAndAi() throws Exception {
            ApiResponse resp = ApiResponse.error("ERR", "something broke", "t", "r");
            String json = resp.toJson(mapper);
            Map<String, Object> parsed = mapper.readValue(json, Map.class);

            assertThat(parsed).containsKey("error");
            assertThat(parsed).doesNotContainKey("data");
            assertThat(parsed).doesNotContainKey("ai");
        }
    }

    // ──────────────────── EndpointSensitivity classification ────────────────────

    @Nested
    @DisplayName("EndpointSensitivity.classify()")
    class SensitivityTests {

        @Test
        @DisplayName("health path → PUBLIC")
        void healthPath_isPublic() {
            assertThat(EndpointSensitivity.classify("GET", "/health"))
                .isEqualTo(EndpointSensitivity.PUBLIC);
        }

        @Test
        @DisplayName("metrics path → PUBLIC")
        void metricsPath_isPublic() {
            assertThat(EndpointSensitivity.classify("GET", "/metrics"))
                .isEqualTo(EndpointSensitivity.PUBLIC);
        }

        @Test
        @DisplayName("DELETE entity → CRITICAL")
        void deleteEntity_isCritical() {
            assertThat(EndpointSensitivity.classify("DELETE", "/api/v1/entities/users/abc"))
                .isEqualTo(EndpointSensitivity.CRITICAL);
        }

        @Test
        @DisplayName("governance path → CRITICAL")
        void governancePath_isCritical() {
            assertThat(EndpointSensitivity.classify("POST", "/api/v1/governance/retention/classify"))
                .isEqualTo(EndpointSensitivity.CRITICAL);
        }

        @Test
        @DisplayName("memory read path → SENSITIVE")
        void memoryPath_isSensitive() {
            assertThat(EndpointSensitivity.classify("GET", "/api/v1/memory/agent1"))
            .isEqualTo(EndpointSensitivity.SENSITIVE);
        }

        @Test
        @DisplayName("analytics path → SENSITIVE")
        void analyticsPath_isSensitive() {
            assertThat(EndpointSensitivity.classify("POST", "/api/v1/analytics/query"))
                .isEqualTo(EndpointSensitivity.SENSITIVE);
        }

        @Test
        @DisplayName("pipeline path → INTERNAL for GET (read-only)")
        void pipelinePath_isInternalForGet() {
            assertThat(EndpointSensitivity.classify("GET", "/api/v1/pipelines"))
                .isEqualTo(EndpointSensitivity.INTERNAL);
        }

        @Test
        @DisplayName("pipeline POST → SENSITIVE (mutation)")
        void pipelinePost_isSensitive() {
            assertThat(EndpointSensitivity.classify("POST", "/api/v1/pipelines"))
                .isEqualTo(EndpointSensitivity.SENSITIVE);
        }

        @Test
        @DisplayName("entity list → INTERNAL")
        void entityListPath_isInternal() {
            assertThat(EndpointSensitivity.classify("GET", "/api/v1/entities/users"))
                .isEqualTo(EndpointSensitivity.INTERNAL);
        }
    }

    // ──────────────────── VoiceIntentCatalog ────────────────────

    @Nested
    @DisplayName("VoiceIntentCatalog")
    class VoiceIntentCatalogTests {

        @Test
        @DisplayName("findByName() returns exact match case-insensitively")
        void findByName_exactMatch() {
            assertThat(VoiceIntentCatalog.findByName("LIST_PIPELINES")).isPresent();
            assertThat(VoiceIntentCatalog.findByName("list_pipelines")).isPresent();
        }

        @Test
        @DisplayName("findByName() returns empty for unknown name")
        void findByName_unknown() {
            assertThat(VoiceIntentCatalog.findByName("nonexistent_intent")).isEmpty();
        }

        @Test
        @DisplayName("findCandidates() returns non-empty list for 'pipeline' keyword")
        void findCandidates_pipelineKeyword() {
            assertThat(VoiceIntentCatalog.findCandidates("show me all pipelines")).isNotEmpty();
        }

        @Test
        @DisplayName("ALL catalog contains at least 20 intents")
        void allCatalog_hasMinimumSize() {
            assertThat(VoiceIntentCatalog.ALL).hasSizeGreaterThanOrEqualTo(20);
        }

        @Test
        @DisplayName("resolvePath() replaces :param placeholders")
        void resolvePath_replacesParams() {
            VoiceIntentCatalog.VoiceIntent intent = VoiceIntentCatalog.findByName("get_entity").orElseThrow();
            String resolved = intent.resolvePath(Map.of("collection", "users", "id", "abc123"));
            assertThat(resolved).isEqualTo("/api/v1/entities/users/abc123");
        }

        @Test
        @DisplayName("missingRequiredParams() returns list of missing keys")
        void missingRequiredParams_returnsMissing() {
            VoiceIntentCatalog.VoiceIntent intent = VoiceIntentCatalog.findByName("get_entity").orElseThrow();
            var missing = intent.missingRequiredParams(Map.of("collection", "users")); // missing "id"
            assertThat(missing).contains("id");
        }
    }
}
