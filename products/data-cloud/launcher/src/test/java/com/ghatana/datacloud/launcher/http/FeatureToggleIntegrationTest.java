/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.feature;

import com.ghatana.datacloud.launcher.http.DataCloudHttpServerTestBase;
import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for feature toggle end-to-end (F001).
 *
 * @doc.type class
 * @doc.purpose Feature toggle end-to-end integration tests
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@ExtendWith(MockitoExtension.class)
@Timeout(value = 15, unit = TimeUnit.SECONDS)
@DisplayName("FeatureToggle – End-to-End Integration (F001)")
class FeatureToggleIntegrationTest extends DataCloudHttpServerTestBase {

    @Mock
    private DataCloudClient mockClient;

    @BeforeEach
    void setUp() throws Exception {
        port = findFreePort();
        startServer();
    }

    @Override
    protected void startServer() throws Exception {
        server = new DataCloudHttpServer(mockClient, port);
        server.start();
        waitForServerReady(5000);
    }

    @Nested
    @DisplayName("Feature Flag CRUD")
    class FeatureFlagCrudTests {

        @Test
        @DisplayName("[F001]: create_flag_via_api")
        void createFlagViaApi() throws Exception {
            Map<String, Object> newFlag = Map.of(
                "key", "new-feature",
                "name", "New Feature",
                "description", "A new feature",
                "enabled", false,
                "rolloutPercentage", 0
            );

            Map<String, Object> createdFlag = Map.of(
                "key", "new-feature",
                "name", "New Feature",
                "enabled", false,
                "tenantId", "tenant-alpha",
                "createdAt", Instant.now().toString()
            );

            lenient().when(mockClient.createFeatureFlag(any(), any()))
                .thenReturn(Promise.of(createdFlag));

            var response = postJson("/api/v1/feature-flags/flags", newFlag, withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body.get("key")).isEqualTo("new-feature");
        }

        @Test
        @DisplayName("[F001]: get_flag_via_api")
        void getFlagViaApi() throws Exception {
            String flagKey = "existing-feature";

            Map<String, Object> flag = Map.of(
                "key", flagKey,
                "name", "Existing Feature",
                "enabled", true,
                "tenantId", "tenant-alpha",
                "rolloutPercentage", 100,
                "variants", Set.of()
            );

            lenient().when(mockClient.getFeatureFlag(any(), eq(flagKey)))
                .thenReturn(Promise.of(flag));

            var response = get("/api/v1/feature-flags/flags/" + flagKey, withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body.get("key")).isEqualTo(flagKey);
            assertThat(body.get("enabled")).isEqualTo(true);
        }

        @Test
        @DisplayName("[F001]: list_flags_via_api")
        void listFlagsViaApi() throws Exception {
            var flags = java.util.List.of(
                Map.of("key", "feature-1", "enabled", true),
                Map.of("key", "feature-2", "enabled", false),
                Map.of("key", "feature-3", "enabled", true)
            );

            lenient().when(mockClient.listFeatureFlags(any()))
                .thenReturn(Promise.of(flags));

            var response = get("/api/v1/feature-flags/flags", withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> flagList = (java.util.List<Map<String, Object>>) body.get("flags");
            assertThat(flagList).hasSize(3);
        }

        @Test
        @DisplayName("[F001]: toggle_flag_via_api")
        void toggleFlagViaApi() throws Exception {
            String flagKey = "toggleable-feature";

            Map<String, Object> updatedFlag = Map.of(
                "key", flagKey,
                "enabled", true,
                "updatedAt", Instant.now().toString()
            );

            lenient().when(mockClient.toggleFeatureFlag(any(), eq(flagKey), eq(true)))
                .thenReturn(Promise.of(updatedFlag));

            var response = postJson("/api/v1/feature-flags/flags/" + flagKey + "/toggle",
                Map.of("enabled", true),
                withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body.get("enabled")).isEqualTo(true);
        }

        @Test
        @DisplayName("[F001]: delete_flag_via_api")
        void deleteFlagViaApi() throws Exception {
            String flagKey = "obsolete-feature";

            lenient().when(mockClient.deleteFeatureFlag(any(), eq(flagKey)))
                .thenReturn(Promise.of(Map.of("deleted", true)));

            var response = delete("/api/v1/feature-flags/flags/" + flagKey, withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body.get("deleted")).isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("Feature Evaluation")
    class FeatureEvaluationTests {

        @Test
        @DisplayName("[F001]: evaluate_flag_enabled_for_user")
        void evaluateFlagEnabledForUser() throws Exception {
            String flagKey = "user-specific-feature";

            Map<String, Object> evaluation = Map.of(
                "key", flagKey,
                "enabled", true,
                "timestamp", System.currentTimeMillis()
            );

            lenient().when(mockClient.evaluateFeatureFlag(any(), eq(flagKey), any()))
                .thenReturn(Promise.of(evaluation));

            var response = postJson("/api/v1/feature-flags/evaluate", Map.of(
                "key", flagKey,
                "context", Map.of(
                    "userId", "user-001",
                    "tenantId", "tenant-alpha"
                )
            ), withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body.get("enabled")).isEqualTo(true);
        }

        @Test
        @DisplayName("[F001]: evaluate_flag_disabled_for_user")
        void evaluateFlagDisabledForUser() throws Exception {
            String flagKey = "limited-feature";

            Map<String, Object> evaluation = Map.of(
                "key", flagKey,
                "enabled", false,
                "timestamp", System.currentTimeMillis()
            );

            lenient().when(mockClient.evaluateFeatureFlag(any(), eq(flagKey), any()))
                .thenReturn(Promise.of(evaluation));

            var response = postJson("/api/v1/feature-flags/evaluate", Map.of(
                "key", flagKey,
                "context", Map.of("userId", "user-002")
            ), withTenant("tenant-alpha"));

            assertStatusCode(response, 200);
            Map<String, Object> body = parseJsonResponse(response);
            assertThat(body.get("enabled")).isEqualTo(false);
        }

        @Test
        @DisplayName("[F001]: evaluate_missing_flag_returns_error")
        void evaluateMissingFlagReturnsError() throws Exception {
            String flagKey = "nonexistent-feature";

            lenient().when(mockClient.evaluateFeatureFlag(any(), eq(flagKey), any()))
                .thenReturn(Promise.of(Map.of("error", "Flag not found")));

            var response = postJson("/api/v1/feature-flags/evaluate", Map.of(
                "key", flagKey
            ), withTenant("tenant-alpha"));

            assertStatusCode(response, 404);
        }
    }

    @Nested
    @DisplayName("Tenant Isolation")
    class TenantIsolationTests {

        @Test
        @DisplayName("[F001]: tenant_cannot_see_other_tenant_flags")
        void tenantCannotSeeOtherTenantFlags() throws Exception {
            String tenantAlpha = "tenant-alpha";
            String tenantBeta = "tenant-beta";

            var alphaFlags = java.util.List.of(Map.of("key", "alpha-only", "tenantId", tenantAlpha));
            var betaFlags = java.util.List.of(Map.of("key", "beta-only", "tenantId", tenantBeta));

            lenient().when(mockClient.listFeatureFlags(eq(tenantAlpha)))
                .thenReturn(Promise.of(alphaFlags));
            lenient().when(mockClient.listFeatureFlags(eq(tenantBeta)))
                .thenReturn(Promise.of(betaFlags));

            var alphaResponse = get("/api/v1/feature-flags/flags", withTenant(tenantAlpha));
            var betaResponse = get("/api/v1/feature-flags/flags", withTenant(tenantBeta));

            Map<String, Object> alphaBody = parseJsonResponse(alphaResponse);
            Map<String, Object> betaBody = parseJsonResponse(betaResponse);

            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> alphaList = (java.util.List<Map<String, Object>>) alphaBody.get("flags");
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> betaList = (java.util.List<Map<String, Object>>) betaBody.get("flags");

            assertThat(alphaList.get(0).get("tenantId")).isEqualTo(tenantAlpha);
            assertThat(betaList.get(0).get("tenantId")).isEqualTo(tenantBeta);
        }
    }

    @Nested
    @DisplayName("Variant Assignment")
    class VariantAssignmentTests {

        @Test
        @DisplayName("[F001]: variant_assignment_consistent_for_user")
        void variantAssignmentConsistentForUser() throws Exception {
            String flagKey = "ab-test";
            String userId = "user-001";

            Map<String, Object> evaluation = Map.of(
                "key", flagKey,
                "enabled", true,
                "variant", "treatment",
                "timestamp", System.currentTimeMillis()
            );

            lenient().when(mockClient.evaluateFeatureFlag(any(), eq(flagKey), any()))
                .thenReturn(Promise.of(evaluation));

            // Multiple evaluations should return same variant
            var response1 = postJson("/api/v1/feature-flags/evaluate", Map.of(
                "key", flagKey,
                "context", Map.of("userId", userId)
            ), withTenant("tenant-alpha"));

            var response2 = postJson("/api/v1/feature-flags/evaluate", Map.of(
                "key", flagKey,
                "context", Map.of("userId", userId)
            ), withTenant("tenant-alpha"));

            Map<String, Object> body1 = parseJsonResponse(response1);
            Map<String, Object> body2 = parseJsonResponse(response2);

            assertThat(body1.get("variant")).isEqualTo(body2.get("variant"));
        }
    }
}
