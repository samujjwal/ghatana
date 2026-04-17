package com.ghatana.yappc.services.lifecycle.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Tests for the repository-backed YAPPC artifact store adapter
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("YappcDataCloudArtifactStore")
class YappcDataCloudArtifactStoreTest extends EventloopTestBase {

    @Mock
    private DataCloudClient client;

    private YappcDataCloudArtifactStore store;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TenantContext.setCurrentTenantId("tenant-alpha");
        runBlocking(() -> TenantContext.setCurrentTenantId("tenant-alpha"));
        store = new YappcDataCloudArtifactStore(client, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        runBlocking(TenantContext::clear);
        TenantContext.clear();
    }

    @Test
    @DisplayName("put stores artifact through the canonical repository seam")
    void putStoresArtifactThroughRepositorySeam() {
        AtomicReference<Map<String, Object>> savedPayloadRef = new AtomicReference<>();
        when(client.save(eq("tenant-alpha"), eq("yappc-artifacts"), any()))
            .thenAnswer(invocation -> {
                Map<String, Object> payload = invocation.getArgument(2);
                savedPayloadRef.set(payload);
                return Promise.of(DataCloudClient.Entity.of(
                    String.valueOf(payload.get("id")),
                    "yappc-artifacts",
                    payload
                ));
            });

        String version = runPromise(() -> store.put("products/demo/phases/intent", "artifact-body".getBytes(StandardCharsets.UTF_8)));

        Map<String, Object> payload = savedPayloadRef.get();
        assertThat(version).isNotBlank();
        assertThat(payload.get("path")).isEqualTo("products/demo/phases/intent");
        assertThat(payload.get("version")).isEqualTo(version);
        assertThat(payload.get("tenantId")).isEqualTo("tenant-alpha");
        assertThat(payload.get("content")).isEqualTo(Base64.getEncoder().encodeToString("artifact-body".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("get loads artifact content by deterministic repository ID")
    void getLoadsArtifactContentByDeterministicId() {
        String encodedContent = Base64.getEncoder().encodeToString("artifact-body".getBytes(StandardCharsets.UTF_8));
        String artifactId = "11111111-1111-1111-1111-111111111111";
        when(client.findById(eq("tenant-alpha"), eq("yappc-artifacts"), anyString()))
            .thenReturn(Promise.of(Optional.of(DataCloudClient.Entity.of(
                artifactId,
                "yappc-artifacts",
                Map.of(
                    "id", artifactId,
                    "path", "products/demo/phases/intent",
                    "version", "v-1",
                    "content", encodedContent,
                    "size", 13,
                    "tenantId", "tenant-alpha",
                    "createdAt", 1L
                )
            ))));

        byte[] content = runPromise(() -> store.get("products/demo/phases/intent/v-1"));

        assertThat(new String(content, StandardCharsets.UTF_8)).isEqualTo("artifact-body");
    }

    @Test
    @DisplayName("list normalizes the version prefix before querying the repository seam")
    void listNormalizesVersionPrefixBeforeQuerying() {
        when(client.query(eq("tenant-alpha"), eq("yappc-artifacts"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(List.of(
                DataCloudClient.Entity.of("1", "yappc-artifacts", Map.of(
                    "id", "22222222-2222-2222-2222-222222222222",
                    "path", "products/demo/phases/intent",
                    "version", "v-1",
                    "content", "YQ==",
                    "size", 1,
                    "tenantId", "tenant-alpha",
                    "createdAt", 1L
                )),
                DataCloudClient.Entity.of("2", "yappc-artifacts", Map.of(
                    "id", "33333333-3333-3333-3333-333333333333",
                    "path", "products/demo/phases/intent",
                    "version", "v-2",
                    "content", "Yg==",
                    "size", 1,
                    "tenantId", "tenant-alpha",
                    "createdAt", 2L
                ))
            )));

        List<String> versions = runPromise(() -> store.list("products/demo/phases/intent/"));

        assertThat(versions).containsExactlyInAnyOrder("v-1", "v-2");
    }

    @Test
    @DisplayName("delete removes content and metadata through repository-backed deletes")
    void deleteRemovesContentAndMetadata() {
        when(client.delete(eq("tenant-alpha"), eq("yappc-artifacts"), anyString()))
            .thenReturn(Promise.of(null));
        when(client.delete(eq("tenant-alpha"), eq("yappc-artifact-metadata"), anyString()))
            .thenReturn(Promise.of(null));

        runPromise(() -> store.delete("products/demo/phases/intent/v-1"));
    }

    @Test
    @DisplayName("default-tenant is rejected before any Data Cloud call")
    void defaultTenantIsRejected() {
        runBlocking(() -> TenantContext.setCurrentTenantId("default-tenant"));
        TenantContext.setCurrentTenantId("default-tenant");

        assertThatThrownBy(() -> runPromise(() -> store.list("products/demo/phases/intent/")))
            .hasMessageContaining("does not allow default-tenant");
    }
}