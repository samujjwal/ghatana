/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static com.ghatana.datacloud.launcher.http.EndpointSensitivity.*;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Comprehensive unit tests for {@link EndpointSensitivity#classify(String, String)}. // GH-90000
 *
 * <p>Covers every HTTP route registered in {@code DataCloudHttpServer} and
 * verifies that each is assigned the correct sensitivity level.  Any new route
 * added to the server MUST have a corresponding test here to keep the policy
 * coverage matrix complete.
 *
 * <h2>Coverage Matrix</h2>
 * <pre>
 *  PUBLIC   — /health, /health/detail, /ready, /live, /metrics, /info
 *  INTERNAL — authenticated read-only GETs (entities, events, analytics…) // GH-90000
 *  SENSITIVE — mutations (POST/PUT/PATCH) for most resources; AI inference; voice // GH-90000
 *  CRITICAL  — deletions of entities/pipelines/checkpoints;
 *               governance/** ; memory delete/retain; model promote;
 *               learning approve/reject; voice transcripts
 * </pre>
 *
 * @doc.type class
 * @doc.purpose Exhaustive sensitivity-classification tests for all DC routes
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("EndpointSensitivity – route classification matrix")
class EndpointSensitivityTest {

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC routes (no auth required) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PUBLIC paths — health / metrics probes")
    class PublicPaths {

        @ParameterizedTest(name = "{0} {1} → PUBLIC") // GH-90000
        @CsvSource({ // GH-90000
            "GET, /health",
            "GET, /health/detail",
            "GET, /ready",
            "GET, /live",
            "GET, /info",
            "GET, /metrics"
        })
        void publicPaths_returnPublic(String method, String path) { // GH-90000
            assertThat(classify(method, path)).isEqualTo(PUBLIC); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INTERNAL routes (authenticated read-only GETs) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("INTERNAL — authenticated read-only GETs")
    class InternalPaths {

        @ParameterizedTest(name = "GET {0} → INTERNAL") // GH-90000
        @CsvSource({ // GH-90000
            // Entities
            "/api/v1/entities/users",
            "/api/v1/entities/users/123",
            "/api/v1/entities/users/search",
            "/api/v1/data-products",
            "/api/v1/entities/users/export",
            // Events (read) // GH-90000
            "/api/v1/events",
            // Pipelines (read) // GH-90000
            "/api/v1/pipelines",
            "/api/v1/pipelines/pipe-123",
            // Checkpoints (read) // GH-90000
            "/api/v1/checkpoints",
            "/api/v1/checkpoints/cp-001",
            // Analytics (read) // GH-90000
            "/api/v1/analytics/query/q-001",
            "/api/v1/analytics/query/q-001/plan",
            // Reports (read) // GH-90000
            "/api/v1/reports",
            "/api/v1/reports/r-001",
            // AI Models (read) // GH-90000
            "/api/v1/models",
            "/api/v1/models/my-model",
            // Features (read) // GH-90000
            "/api/v1/features/entity-001",
            // Learning (read) // GH-90000
            "/api/v1/learning/status",
            "/api/v1/learning/review",
            // Brain (read, non-memory) // GH-90000
            "/api/v1/brain/health",
            "/api/v1/brain/config",
            "/api/v1/brain/stats",
            "/api/v1/brain/workspace",
            "/api/v1/brain/patterns",
            "/api/v1/brain/salience/item-001",
            "/api/v1/brain/attention/thresholds"
        })
        void getRequests_returnInternal(String path) { // GH-90000
            assertThat(classify("GET", path)).isEqualTo(INTERNAL); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SENSITIVE routes (authenticated writes and AI inference) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SENSITIVE — authenticated writes, AI, voice")
    class SensitivePaths {

        @ParameterizedTest(name = "{0} {1} → SENSITIVE") // GH-90000
        @CsvSource({ // GH-90000
            // Entity mutations (non-DELETE) // GH-90000
            "POST, /api/v1/entities/users",
            "POST, /api/v1/entities/users/batch",
            "POST, /api/v1/entities/users/anomalies",
            "GET,  /api/v1/entities/users/similar",
            "POST, /api/v1/context/users/rag",
            "POST, /api/v1/data-products",
            "POST, /api/v1/data-products/product-123/subscribe",
            // Events
            "POST, /api/v1/events",
            // Pipeline mutations (non-DELETE) // GH-90000
            "POST, /api/v1/pipelines",
            "PUT,  /api/v1/pipelines/pipe-123",
            // Checkpoint mutations (non-DELETE) // GH-90000
            "POST, /api/v1/checkpoints",
            // Memory — GET is SENSITIVE (personal data) // GH-90000
            "GET,  /api/v1/memory",
            "GET,  /api/v1/memory/agent-001",
            "GET,  /api/v1/memory/agent-001/episodic",
            "POST, /api/v1/memory/agent-001",
            "POST, /api/v1/memory/agent-001/search",
            // Brain mutations
            "POST, /api/v1/brain/attention/elevate",
            "PUT,  /api/v1/brain/attention/thresholds",
            "POST, /api/v1/brain/patterns/match",
            "POST, /api/v1/brain/explain",
            // Analytics mutations
            "POST, /api/v1/analytics/query",
            "POST, /api/v1/analytics/aggregate",
            "POST, /api/v1/reports",
            // AI suggestions (SENSITIVE — triggers AI inference on tenant data) // GH-90000
            "POST, /api/v1/entities/users/suggest",
            "POST, /api/v1/analytics/suggest",
            "POST, /api/v1/pipelines/pipe-123/optimise-hint",
            // AI model mutations (non-promote) // GH-90000
            "POST, /api/v1/models",
            // Feature ingest
            "POST, /api/v1/features",
            // Learning trigger (authenticated mutation — not approve/reject) // GH-90000
            "POST, /api/v1/learning/trigger",
            // Voice (non-transcript management) // GH-90000
            "POST, /api/v1/voice/intent",
            "POST, /api/v1/voice/intent/classify"
        })
        void sensitiveRoutes_returnSensitive(String method, String path) { // GH-90000
            assertThat(classify(method.trim(), path.trim())).isEqualTo(SENSITIVE); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CRITICAL routes (high-impact, policy-engine required) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CRITICAL — deletions, governance, model promote, approve/reject")
    class CriticalPaths {

        @ParameterizedTest(name = "{0} {1} → CRITICAL") // GH-90000
        @CsvSource({ // GH-90000
            // Entity DELETE
            "DELETE, /api/v1/entities/users/123",
            "DELETE, /api/v1/entities/users/batch",
            // Pipeline DELETE
            "DELETE, /api/v1/pipelines/pipe-123",
            // Checkpoint DELETE
            "DELETE, /api/v1/checkpoints/cp-001",
            // Memory DELETE / retain
            "DELETE, /api/v1/memory/agent-001/mem-001",
            "PUT,    /api/v1/memory/agent-001/mem-001/retain",
            // Model promote
            "POST,   /api/v1/models/my-model/promote",
            // Learning approve / reject
            "POST,   /api/v1/learning/review/rev-001/approve",
            "POST,   /api/v1/learning/review/rev-001/reject",
            // Governance — all methods
            "POST,   /api/v1/governance/retention/classify",
            "GET,    /api/v1/governance/retention/policy",
            "POST,   /api/v1/governance/retention/purge",
            "POST,   /api/v1/governance/privacy/redact",
            "GET,    /api/v1/governance/privacy/pii-fields",
            "GET,    /api/v1/governance/compliance/summary",
            // Voice transcript management
            "GET,    /api/v1/voice/transcripts",
            "DELETE, /api/v1/voice/transcripts/tr-001"
        })
        void criticalRoutes_returnCritical(String method, String path) { // GH-90000
            assertThat(classify(method.trim(), path.trim())).isEqualTo(CRITICAL); // GH-90000
        }

        // ── Explicit named tests for high-value invariants ───────────────────

        @Test
        @DisplayName("GET /api/v1/governance/compliance/summary is CRITICAL (governance prefix)")
        void governanceGet_isCritical() { // GH-90000
            assertThat(classify("GET", "/api/v1/governance/compliance/summary")) // GH-90000
                .isEqualTo(CRITICAL); // GH-90000
        }

        @Test
        @DisplayName("DELETE /api/v1/entities/:collection/:id is CRITICAL")
        void entityDelete_isCritical() { // GH-90000
            assertThat(classify("DELETE", "/api/v1/entities/user_profiles/u-123")) // GH-90000
                .isEqualTo(CRITICAL); // GH-90000
        }

        @Test
        @DisplayName("DELETE /api/v1/pipelines/:id is CRITICAL (pipeline teardown requires audit)")
        void pipelineDelete_isCritical() { // GH-90000
            assertThat(classify("DELETE", "/api/v1/pipelines/etl-001")) // GH-90000
                .isEqualTo(CRITICAL); // GH-90000
        }

        @Test
        @DisplayName("DELETE /api/v1/checkpoints/:id is CRITICAL (checkpoint deletion requires audit)")
        void checkpointDelete_isCritical() { // GH-90000
            assertThat(classify("DELETE", "/api/v1/checkpoints/cp-001")) // GH-90000
                .isEqualTo(CRITICAL); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Boundary / edge cases
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge cases and boundary conditions")
    class EdgeCases {

        @Test
        @DisplayName("Unknown /api/v1/ path with GET → INTERNAL (authenticated default)")
        void unknownApiV1Get_returnsInternal() { // GH-90000
            assertThat(classify("GET", "/api/v1/unknown-resource")) // GH-90000
                .isEqualTo(INTERNAL); // GH-90000
        }

        @Test
        @DisplayName("Unknown /api/v1/ path with POST → INTERNAL (not matching any prefix)")
        void unknownApiV1Post_returnsInternal() { // GH-90000
            assertThat(classify("POST", "/api/v1/unknown-resource")) // GH-90000
                .isEqualTo(INTERNAL); // GH-90000
        }

        @Test
        @DisplayName("Non-api path → INTERNAL (fallback)")
        void nonApiPath_returnsInternal() { // GH-90000
            assertThat(classify("GET", "/static/index.html")) // GH-90000
                .isEqualTo(INTERNAL); // GH-90000
        }

        @Test
        @DisplayName("POST /api/v1/learning/trigger is SENSITIVE — mutation of learning pipeline")
        void learningTrigger_isSensitive() { // GH-90000
            assertThat(classify("POST", "/api/v1/learning/trigger")) // GH-90000
                .isEqualTo(SENSITIVE); // GH-90000
        }

        @Test
        @DisplayName("classify is case-insensitive for HTTP method")
        void caseInsensitiveMethod() { // GH-90000
            assertThat(classify("delete", "/api/v1/pipelines/my-pipeline")).isEqualTo(CRITICAL); // GH-90000
            assertThat(classify("DELETE", "/api/v1/pipelines/my-pipeline")).isEqualTo(CRITICAL); // GH-90000
            assertThat(classify("Delete", "/api/v1/pipelines/my-pipeline")).isEqualTo(CRITICAL); // GH-90000
        }

        @Test
        @DisplayName("PUBLIC_PATHS set contains the expected probe paths")
        void publicPathsSet_containsProbes() { // GH-90000
            assertThat(PUBLIC_PATHS).containsExactlyInAnyOrder( // GH-90000
                "/health", "/health/detail", "/ready", "/live", "/metrics", "/info"
            );
        }

        @Test
        @DisplayName("CRITICAL_PATH_PREFIXES covers governance, learning review, and voice transcripts (always-critical)")
        void criticalPrefixes_coversExpectedResources() { // GH-90000
            Set<String> expected = Set.of( // GH-90000
                "/api/v1/governance/",
                "/api/v1/learning/review/",
                "/api/v1/voice/transcripts"
            );
            assertThat(CRITICAL_PATH_PREFIXES).isEqualTo(expected); // GH-90000
        }

        @Test
        @DisplayName("DELETE_CRITICAL_PREFIXES covers all resources where deletion requires policy engine")
        void deleteCriticalPrefixes_coversExpectedResources() { // GH-90000
            Set<String> expected = Set.of( // GH-90000
                "/api/v1/entities/",
                "/api/v1/pipelines/",
                "/api/v1/checkpoints/",
                "/api/v1/memory/",
                "/api/v1/models/"
            );
            assertThat(DELETE_CRITICAL_PREFIXES).isEqualTo(expected); // GH-90000
        }
    }
}
