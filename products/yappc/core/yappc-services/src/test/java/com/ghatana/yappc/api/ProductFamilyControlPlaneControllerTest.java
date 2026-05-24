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
        assertThat(body).contains("\"status\":\"READY\"");
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
        Map<String, Object> currentAsset = Map.of(
                "id", "entity-1",
                "asset_id", "phr-consent-module",
                "asset_type", "module",
                "display_name", "PHR Consent Module",
                "source_product", "phr",
                "domain", "healthcare",
                "maturity", "candidate",
                "reuse_mode", "reference",
                "owner", "platform",
                "version", 2);
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
