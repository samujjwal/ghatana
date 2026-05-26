package com.ghatana.yappc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verifies YAPPC product-family control-plane writes use governed Data Cloud asset promotion state
 * @doc.layer api
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductFamilyControlPlaneController")
class ProductFamilyControlPlaneControllerTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-1";
    private static final String ASSET_COLLECTION = "product_family_assets";
    private static final String HISTORY_COLLECTION = "product_family_asset_history";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private DataCloudClient dataCloudClient;

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("release readiness ingests product evidence when Data Cloud has no record")
    void getReleaseReadinessIngestsEvidenceWhenMissing() throws Exception {
        Path evidenceDir = tempDir.resolve(".kernel/evidence");
        Files.createDirectories(evidenceDir);
        Files.writeString(
                evidenceDir.resolve("product-release-readiness.phr.json"),
                """
                {
                  "generatedAt": "2026-05-23T22:10:18.389Z",
                  "sourceCommitSha": "ca6a53684a4685fe04fff1dd23cea80b9b65d27d",
                  "targetCommitSha": "ca6a53684a4685fe04fff1dd23cea80b9b65d27d",
                  "targetEnvironment": "staging",
                  "validationStatus": "failed",
                  "expiresAt": "2099-05-23T22:10:18.389Z",
                  "evidenceRun": {
                    "commit": "ca6a53684a4685fe04fff1dd23cea80b9b65d27d",
                    "sourceCommitSha": "ca6a53684a4685fe04fff1dd23cea80b9b65d27d",
                    "targetCommitSha": "ca6a53684a4685fe04fff1dd23cea80b9b65d27d",
                    "targetEnvironment": "staging"
                  },
                  "productId": "phr",
                  "releaseVerdict": "fail",
                  "releaseProfiles": ["standard-web-api-release"],
                  "blockingGaps": [{"severity":"P0","gate":"./scripts/check-production-stubs.mjs","reason":"script-failure"}],
                  "evidencePaths": [".kernel/evidence/product-release-readiness.phr.json"],
                  "dimensions": [{"dimensionName":"Runtime correctness","score":4.8}]
                }
                """,
                StandardCharsets.UTF_8);

        when(dataCloudClient.query(eq(TENANT_ID), eq("product_release_readiness"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Promise.of(List.of()));
        when(dataCloudClient.save(eq(TENANT_ID), eq("product_release_readiness"), org.mockito.ArgumentMatchers.<Map<String, Object>>any()))
                .thenAnswer(invocation -> Promise.of(DataCloudClient.Entity.of(
                        "release-readiness-phr",
                        "product_release_readiness",
                        invocation.getArgument(2))));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/yappc/product-family/releases/phr").build();
        request.attach(Principal.class, new Principal("asset-admin", List.of("admin"), TENANT_ID));
        putPathParameter(request, "productKey", "phr");

        ProductFamilyControlPlaneController controller = new ProductFamilyControlPlaneController(dataCloudClient, objectMapper, tempDir);
        HttpResponse response = runPromise(() -> controller.getReleaseReadiness(request));

        assertThat(response.getCode()).isEqualTo(200);
        String body = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"productKey\":\"phr\"");
        assertThat(body).contains("\"status\":\"FAILED\"");
        assertThat(body).contains("check-production-stubs.mjs");
        verify(dataCloudClient).save(eq(TENANT_ID), eq("product_release_readiness"), org.mockito.ArgumentMatchers.<Map<String, Object>>any());
    }

    @Test
    @DisplayName("asset list ingests reusable asset evidence when Data Cloud is empty")
    void listAssetsIngestsEvidenceWhenMissing() throws Exception {
        Path phrEvidenceDir = tempDir.resolve(".kernel/evidence/phr");
        Files.createDirectories(phrEvidenceDir);
        Files.writeString(
                phrEvidenceDir.resolve("reusable-assets-registration.json"),
                """
                {
                  "productId": "phr",
                  "assets": [
                    {
                      "asset_id": "phr-consent-panel",
                      "asset_name": "PHR Consent Panel",
                      "asset_type": "ui-component",
                      "source_product": "phr",
                      "maturity_level": "hardened",
                      "reuse_mode": "reference",
                      "paths": ["products/phr/frontend/src/components/consent/ConsentPanel.tsx"],
                      "tests": ["products/phr/frontend/src/components/consent/__tests__/ConsentPanel.test.tsx"],
                      "dependencies": ["platform:typescript:design-system"],
                      "product_usage": [],
                      "owner": "phr-team",
                      "promotion_target": "shared-package",
                      "compatibility": {"framework":"react"},
                      "promotion_evidence": {"evidence_refs": [".kernel/evidence/phr/reusable-assets-registration.json"]}
                    }
                  ]
                }
                """,
                StandardCharsets.UTF_8);
        Path dmEvidenceDir = tempDir.resolve(".kernel/evidence/digital-marketing");
        Files.createDirectories(dmEvidenceDir);
        Files.writeString(dmEvidenceDir.resolve("reusable-assets-registration.json"), "{\"productId\":\"digital-marketing\",\"assets\":[]}", StandardCharsets.UTF_8);

        when(dataCloudClient.query(eq(TENANT_ID), eq("product_family_assets"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Promise.of(List.of()))
                .thenReturn(Promise.of(List.of(DataCloudClient.Entity.of(
                        "phr-consent-panel",
                        "product_family_assets",
                        Map.ofEntries(
                                Map.entry("asset_id", "phr-consent-panel"),
                                Map.entry("asset_type", "ui-component"),
                                Map.entry("source_product", "phr"),
                                Map.entry("display_name", "PHR Consent Panel"),
                                Map.entry("domain", "unknown"),
                                Map.entry("paths", List.of("products/phr/frontend/src/components/consent/ConsentPanel.tsx")),
                                Map.entry("maturity", "hardened"),
                                Map.entry("reuse_mode", "reference"),
                                Map.entry("dependencies", List.of("platform:typescript:design-system")),
                                Map.entry("tests", List.of("products/phr/frontend/src/components/consent/__tests__/ConsentPanel.test.tsx")),
                                Map.entry("product_usage", List.of()),
                                Map.entry("owner", "phr-team"),
                                Map.entry("promotion_target", "shared-package"),
                                Map.entry("promotion_state", "proposed"),
                                Map.entry("compatibility", Map.of("framework", "react")))))));

        when(dataCloudClient.save(eq(TENANT_ID), eq("product_family_assets"), org.mockito.ArgumentMatchers.<Map<String, Object>>any()))
                .thenAnswer(invocation -> Promise.of(DataCloudClient.Entity.of(
                        String.valueOf(((Map<?, ?>) invocation.getArgument(2)).get("id")),
                        "product_family_assets",
                        invocation.getArgument(2))));

        HttpRequest request = HttpRequest.get("http://localhost/api/v1/yappc/product-family/assets").build();
        request.attach(Principal.class, new Principal("asset-admin", List.of("admin"), TENANT_ID));

        ProductFamilyControlPlaneController controller = new ProductFamilyControlPlaneController(dataCloudClient, objectMapper, tempDir);
        HttpResponse response = runPromise(() -> controller.listAssets(request));

        assertThat(response.getCode()).isEqualTo(200);
        String body = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(body).contains("phr-consent-panel");
        assertThat(body).contains("\"status\":\"READY\"");
        verify(dataCloudClient).save(eq(TENANT_ID), eq("product_family_assets"), org.mockito.ArgumentMatchers.<Map<String, Object>>any());
    }

    @Test
    @DisplayName("asset promotion persists updated asset and history")
    void promoteAssetPersistsAssetAndHistory() throws Exception {
        Map<String, Object> currentAsset = Map.ofEntries(
                Map.entry("id", "entity-1"),
                Map.entry("asset_id", "phr-consent-module"),
                Map.entry("asset_type", "module"),
                Map.entry("display_name", "PHR Consent Module"),
                Map.entry("source_product", "phr"),
                Map.entry("domain", "healthcare"),
                Map.entry("maturity", "candidate"),
                Map.entry("reuse_mode", "reference"),
                Map.entry("owner", "platform"),
                Map.entry("paths", List.of("products/phr/src/main/java/com/ghatana/phr/consent/ConsentManagementService.java")),
                Map.entry("tests", List.of("products/phr/src/test/java/com/ghatana/phr/consent/ConsentManagementServiceTest.java")),
                Map.entry("dependencies", List.of("platform:java:governance")),
                Map.entry("version", 2));
        when(dataCloudClient.findById(TENANT_ID, ASSET_COLLECTION, "phr-consent-module"))
                .thenReturn(Promise.of(Optional.of(DataCloudClient.Entity.of("entity-1", ASSET_COLLECTION, currentAsset))));
        when(dataCloudClient.save(eq(TENANT_ID), anyString(), org.mockito.ArgumentMatchers.<Map<String, Object>>any()))
                .thenAnswer(invocation -> Promise.of(DataCloudClient.Entity.of(
                        String.valueOf(((Map<?, ?>) invocation.getArgument(2)).get("id")),
                        invocation.getArgument(1),
                        invocation.getArgument(2))));

        HttpResponse response = runPromise(() -> controller().promoteAsset(postPromotion(
                "phr-consent-module",
                Map.of(
                        "targetState", "hardened",
                        "reason", "phase-4 hardening",
                        "evidenceRefs", List.of("evidence://gate/asset-1")))));

        assertThat(response.getCode()).isEqualTo(200);
        ArgumentCaptor<Map<String, Object>> assetCaptor = typedMapCaptor();
        ArgumentCaptor<Map<String, Object>> historyCaptor = typedMapCaptor();
        verify(dataCloudClient).save(eq(TENANT_ID), eq(ASSET_COLLECTION), assetCaptor.capture());
        verify(dataCloudClient).save(eq(TENANT_ID), eq(HISTORY_COLLECTION), historyCaptor.capture());

        Map<String, Object> savedAsset = assetCaptor.getValue();
        assertThat(savedAsset)
                .containsEntry("id", "entity-1")
                .containsEntry("asset_id", "phr-consent-module")
                .containsEntry("promotion_state", "hardened")
                .containsEntry("maturity", "hardened")
                .containsEntry("promotion_reason", "phase-4 hardening")
                .containsEntry("promoted_by", "asset-admin")
                .containsEntry("promotion_correlation_id", "corr-asset-1")
                .containsEntry("version", 3);
        @SuppressWarnings("unchecked")
        List<Object> evidenceRefs = (List<Object>) savedAsset.get("promotion_evidence_refs");
        assertThat(evidenceRefs).containsExactly("evidence://gate/asset-1");

        Map<String, Object> history = historyCaptor.getValue();
        assertThat(history)
                .containsEntry("asset_id", "phr-consent-module")
                .containsEntry("promotion_state", "hardened")
                .containsEntry("actor_id", "asset-admin")
                .containsEntry("correlation_id", "corr-asset-1")
                .containsEntry("version", 3);
    }

    @Test
    @DisplayName("asset promotion rejects skipped maturity transition")
    void promoteAssetRejectsSkippedTransition() throws Exception {
        Map<String, Object> currentAsset = Map.of(
                "id", "entity-1",
                "asset_id", "phr-consent-module",
                "maturity", "candidate",
                "reuse_mode", "reference");
        when(dataCloudClient.findById(TENANT_ID, ASSET_COLLECTION, "phr-consent-module"))
                .thenReturn(Promise.of(Optional.of(DataCloudClient.Entity.of("entity-1", ASSET_COLLECTION, currentAsset))));

        HttpResponse response = runPromise(() -> controller().promoteAsset(postPromotion(
                "phr-consent-module",
                Map.of("targetState", "production"))));

        assertThat(response.getCode()).isEqualTo(409);
        verify(dataCloudClient, never()).save(eq(TENANT_ID), anyString(), org.mockito.ArgumentMatchers.<Map<String, Object>>any());
    }

    @Test
    @DisplayName("YAPPC-004: asset promotion to hardened is blocked when owner is unassigned")
    void promoteToHardenedBlockedWhenOwnerUnassigned() throws Exception {
        Map<String, Object> currentAsset = new java.util.LinkedHashMap<>();
        currentAsset.put("id", "entity-2");
        currentAsset.put("asset_id", "phr-audit-trail");
        currentAsset.put("asset_type", "module");
        currentAsset.put("source_product", "phr");
        currentAsset.put("maturity", "candidate");
        currentAsset.put("reuse_mode", "reference");
        currentAsset.put("owner", "unassigned");
        currentAsset.put("paths", List.of());
        currentAsset.put("tests", List.of());
        when(dataCloudClient.findById(TENANT_ID, ASSET_COLLECTION, "phr-audit-trail"))
                .thenReturn(Promise.of(Optional.of(DataCloudClient.Entity.of("entity-2", ASSET_COLLECTION, Map.copyOf(currentAsset)))));

        HttpResponse response = runPromise(() -> controller().promoteAsset(postPromotion(
                "phr-audit-trail",
                Map.of("targetState", "hardened", "reason", "missing owner"))));

        assertThat(response.getCode()).isEqualTo(422);
        String body = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(body).contains("owner must be set");
        assertThat(body).contains("paths must be non-empty");
        verify(dataCloudClient, never()).save(eq(TENANT_ID), eq(ASSET_COLLECTION), org.mockito.ArgumentMatchers.<Map<String, Object>>any());
    }

    @Test
    @DisplayName("YAPPC-004: asset promotion to production is blocked when evidence_refs are missing")
    void promoteToProductionBlockedWhenEvidenceRefsMissing() throws Exception {
        Map<String, Object> currentAsset = new java.util.LinkedHashMap<>();
        currentAsset.put("id", "entity-3");
        currentAsset.put("asset_id", "phr-fhir-adapter");
        currentAsset.put("asset_type", "connector");
        currentAsset.put("source_product", "phr");
        currentAsset.put("maturity", "hardened");
        currentAsset.put("reuse_mode", "reference");
        currentAsset.put("owner", "phr-platform");
        currentAsset.put("paths", List.of("src/FhirAdapter.java"));
        currentAsset.put("tests", List.of("src/FhirAdapterTest.java"));
        currentAsset.put("dependencies", List.of());
        // evidence_refs absent
        when(dataCloudClient.findById(TENANT_ID, ASSET_COLLECTION, "phr-fhir-adapter"))
                .thenReturn(Promise.of(Optional.of(DataCloudClient.Entity.of("entity-3", ASSET_COLLECTION, Map.copyOf(currentAsset)))));

        HttpResponse response = runPromise(() -> controller().promoteAsset(postPromotion(
                "phr-fhir-adapter",
                Map.of("targetState", "production", "reason", "missing evidence"))));

        assertThat(response.getCode()).isEqualTo(422);
        String body = response.getBody().asString(StandardCharsets.UTF_8);
        assertThat(body).contains("evidence_refs must be non-empty");
        verify(dataCloudClient, never()).save(eq(TENANT_ID), eq(ASSET_COLLECTION), org.mockito.ArgumentMatchers.<Map<String, Object>>any());
    }

    @Nested
    @DisplayName("YAPPC-001: commit mismatch detection")
    class CommitMismatchTests {

        @Test
        @DisplayName("release readiness exposes commitMismatch=true when evidence commit differs from HEAD")
        void releaseReadinessExposesMismatchWhenCommitsDiffer() throws Exception {
            // Write a fake git tree: symbolic HEAD -> refs/heads/main -> a different SHA
            Path gitDir = tempDir.resolve(".git");
            Files.createDirectories(gitDir.resolve("refs/heads"));
            Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/main\n", StandardCharsets.UTF_8);
            Files.writeString(gitDir.resolve("refs/heads/main"), "aabbccddeeff00112233445566778899aabbccdd\n", StandardCharsets.UTF_8);

            Path evidenceDir = tempDir.resolve(".kernel/evidence");
            Files.createDirectories(evidenceDir);
            Files.writeString(
                    evidenceDir.resolve("product-release-readiness.phr.json"),
                    """
                    {
                      "generatedAt": "2026-05-23T00:00:00.000Z",
                      "productId": "phr",
                      "releaseVerdict": "pass",
                      "releaseProfiles": [],
                      "blockingGaps": [],
                      "evidencePaths": [],
                      "dimensions": [],
                      "evidenceRun": { "commit": "ca6a53684a4685fe04fff1dd23cea80b9b65d27d" }
                    }
                    """,
                    StandardCharsets.UTF_8);

            when(dataCloudClient.query(eq(TENANT_ID), eq("product_release_readiness"), org.mockito.ArgumentMatchers.any()))
                    .thenReturn(Promise.of(List.of()));
            when(dataCloudClient.save(eq(TENANT_ID), eq("product_release_readiness"), org.mockito.ArgumentMatchers.<Map<String, Object>>any()))
                    .thenAnswer(invocation -> Promise.of(DataCloudClient.Entity.of(
                            "release-readiness-phr", "product_release_readiness", invocation.getArgument(2))));

            HttpRequest request = HttpRequest.get("http://localhost/api/v1/yappc/product-family/releases/phr").build();
            request.attach(Principal.class, new Principal("tester", List.of("admin"), TENANT_ID));
            putPathParameter(request, "productKey", "phr");

            ProductFamilyControlPlaneController controller = new ProductFamilyControlPlaneController(dataCloudClient, objectMapper, tempDir);
            HttpResponse response = runPromise(() -> controller.getReleaseReadiness(request));

            assertThat(response.getCode()).isEqualTo(200);
            String body = response.getBody().asString(StandardCharsets.UTF_8);
            assertThat(body).contains("\"status\":\"STALE\"");
            assertThat(body).contains("\"commitMismatch\":true");
            assertThat(body).contains("\"evidenceCommit\":\"ca6a53684a46");
        }

        @Test
        @DisplayName("release readiness exposes commitMismatch=false when evidence commit matches HEAD")
        void releaseReadinessExposesMismatchFalseWhenCommitsMatch() throws Exception {
            String sha = "ca6a53684a4685fe04fff1dd23cea80b9b65d27d";
            Path gitDir = tempDir.resolve(".git");
            Files.createDirectories(gitDir.resolve("refs/heads"));
            Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/main\n", StandardCharsets.UTF_8);
            Files.writeString(gitDir.resolve("refs/heads/main"), sha + "\n", StandardCharsets.UTF_8);

            Path evidenceDir = tempDir.resolve(".kernel/evidence");
            Files.createDirectories(evidenceDir);
            Files.writeString(
                    evidenceDir.resolve("product-release-readiness.phr.json"),
                    """
                    {
                      "generatedAt": "2026-05-23T00:00:00.000Z",
                      "productId": "phr",
                      "releaseVerdict": "pass",
                      "releaseProfiles": [],
                      "blockingGaps": [],
                      "evidencePaths": [],
                      "dimensions": [],
                      "evidenceRun": { "commit": "%s" }
                    }
                    """.formatted(sha),
                    StandardCharsets.UTF_8);

            when(dataCloudClient.query(eq(TENANT_ID), eq("product_release_readiness"), org.mockito.ArgumentMatchers.any()))
                    .thenReturn(Promise.of(List.of()));
            when(dataCloudClient.save(eq(TENANT_ID), eq("product_release_readiness"), org.mockito.ArgumentMatchers.<Map<String, Object>>any()))
                    .thenAnswer(invocation -> Promise.of(DataCloudClient.Entity.of(
                            "release-readiness-phr", "product_release_readiness", invocation.getArgument(2))));

            HttpRequest request = HttpRequest.get("http://localhost/api/v1/yappc/product-family/releases/phr").build();
            request.attach(Principal.class, new Principal("tester", List.of("admin"), TENANT_ID));
            putPathParameter(request, "productKey", "phr");

            ProductFamilyControlPlaneController controller = new ProductFamilyControlPlaneController(dataCloudClient, objectMapper, tempDir);
            HttpResponse response = runPromise(() -> controller.getReleaseReadiness(request));

            assertThat(response.getCode()).isEqualTo(200);
            String body = response.getBody().asString(StandardCharsets.UTF_8);
            assertThat(body).contains("\"commitMismatch\":false");
        }

        @Test
        @DisplayName("resolveHeadCommit follows packed-refs when loose ref is absent")
        void resolveHeadCommitFollowsPackedRefs() throws Exception {
            String sha = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
            Path gitDir = tempDir.resolve(".git");
            Files.createDirectories(gitDir);
            Files.writeString(gitDir.resolve("HEAD"), "ref: refs/heads/packed-branch\n", StandardCharsets.UTF_8);
            // No loose ref file; use packed-refs instead
            Files.writeString(gitDir.resolve("packed-refs"),
                    "# pack-refs with: peeled fully-peeled sorted\n" + sha + " refs/heads/packed-branch\n",
                    StandardCharsets.UTF_8);

            ProductFamilyControlPlaneController controller = new ProductFamilyControlPlaneController(dataCloudClient, objectMapper, tempDir);
            assertThat(controller.resolveHeadCommit()).isEqualTo(sha);
        }

        @Test
        @DisplayName("resolveHeadCommit returns null when .git/HEAD is absent")
        void resolveHeadCommitReturnsNullWithoutGit() throws Exception {
            ProductFamilyControlPlaneController controller = new ProductFamilyControlPlaneController(dataCloudClient, objectMapper, tempDir);
            // No .git directory exists in tempDir
            assertThat(controller.resolveHeadCommit()).isNull();
        }
    }

    private ProductFamilyControlPlaneController controller() {
        return new ProductFamilyControlPlaneController(dataCloudClient, objectMapper);
    }

    private HttpRequest postPromotion(String assetId, Object body) throws Exception {
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/yappc/product-family/assets/" + assetId + "/promotions")
                .withHeader(HttpHeaders.of("X-Correlation-ID"), "corr-asset-1")
                .withBody(ByteBuf.wrapForReading(objectMapper.writeValueAsBytes(body)))
                .build();
        request.attach(Principal.class, new Principal("asset-admin", List.of("admin"), TENANT_ID));
        putPathParameter(request, "assetId", assetId);
        return request;
    }

    private static void putPathParameter(HttpRequest request, String key, String value) throws Exception {
        Method method = HttpRequest.class.getDeclaredMethod("putPathParameter", String.class, String.class);
        method.setAccessible(true);
        method.invoke(request, key, value);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, Object>> typedMapCaptor() {
        return ArgumentCaptor.forClass((Class<Map<String, Object>>) (Class<?>) Map.class);
    }
}
