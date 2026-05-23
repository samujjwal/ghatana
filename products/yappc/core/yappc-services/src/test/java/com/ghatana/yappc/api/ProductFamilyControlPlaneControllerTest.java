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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
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
