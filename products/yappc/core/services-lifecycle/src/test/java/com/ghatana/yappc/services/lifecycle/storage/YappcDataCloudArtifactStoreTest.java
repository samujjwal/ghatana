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
@DisplayName("YappcDataCloudArtifactStore [GH-90000]")
class YappcDataCloudArtifactStoreTest extends EventloopTestBase {

    @Mock
    private DataCloudClient client;

    private YappcDataCloudArtifactStore store;

    @BeforeEach
    void setUp() { // GH-90000
        MockitoAnnotations.openMocks(this); // GH-90000
        TenantContext.setCurrentTenantId("tenant-alpha [GH-90000]");
        runBlocking(() -> TenantContext.setCurrentTenantId("tenant-alpha [GH-90000]"));
        store = new YappcDataCloudArtifactStore(client, new ObjectMapper()); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        runBlocking(TenantContext::clear); // GH-90000
        TenantContext.clear(); // GH-90000
    }

    @Test
    @DisplayName("put stores artifact through the canonical repository seam [GH-90000]")
    void putStoresArtifactThroughRepositorySeam() { // GH-90000
        AtomicReference<Map<String, Object>> savedPayloadRef = new AtomicReference<>(); // GH-90000
        when(client.save(eq("tenant-alpha [GH-90000]"), eq("yappc-artifacts [GH-90000]"), any()))
            .thenAnswer(invocation -> { // GH-90000
                Map<String, Object> payload = invocation.getArgument(2); // GH-90000
                savedPayloadRef.set(payload); // GH-90000
                return Promise.of(DataCloudClient.Entity.of( // GH-90000
                    String.valueOf(payload.get("id [GH-90000]")),
                    "yappc-artifacts",
                    payload
                ));
            });

        String version = runPromise(() -> store.put("products/demo/phases/intent", "artifact-body".getBytes(StandardCharsets.UTF_8))); // GH-90000

        Map<String, Object> payload = savedPayloadRef.get(); // GH-90000
        assertThat(version).isNotBlank(); // GH-90000
        assertThat(payload.get("path [GH-90000]")).isEqualTo("products/demo/phases/intent [GH-90000]");
        assertThat(payload.get("version [GH-90000]")).isEqualTo(version);
        assertThat(payload.get("tenantId [GH-90000]")).isEqualTo("tenant-alpha [GH-90000]");
        assertThat(payload.get("content [GH-90000]")).isEqualTo(Base64.getEncoder().encodeToString("artifact-body".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("get loads artifact content by deterministic repository ID [GH-90000]")
    void getLoadsArtifactContentByDeterministicId() { // GH-90000
        String encodedContent = Base64.getEncoder().encodeToString("artifact-body".getBytes(StandardCharsets.UTF_8)); // GH-90000
        String artifactId = "11111111-1111-1111-1111-111111111111";
        when(client.findById(eq("tenant-alpha [GH-90000]"), eq("yappc-artifacts [GH-90000]"), anyString()))
            .thenReturn(Promise.of(Optional.of(DataCloudClient.Entity.of( // GH-90000
                artifactId,
                "yappc-artifacts",
                Map.of( // GH-90000
                    "id", artifactId,
                    "path", "products/demo/phases/intent",
                    "version", "v-1",
                    "content", encodedContent,
                    "size", 13,
                    "tenantId", "tenant-alpha",
                    "createdAt", 1L
                )
            ))));

        byte[] content = runPromise(() -> store.get("products/demo/phases/intent/v-1 [GH-90000]"));

        assertThat(new String(content, StandardCharsets.UTF_8)).isEqualTo("artifact-body [GH-90000]");
    }

    @Test
    @DisplayName("list normalizes the version prefix before querying the repository seam [GH-90000]")
    void listNormalizesVersionPrefixBeforeQuerying() { // GH-90000
        when(client.query(eq("tenant-alpha [GH-90000]"), eq("yappc-artifacts [GH-90000]"), any(DataCloudClient.Query.class)))
            .thenReturn(Promise.of(List.of( // GH-90000
                DataCloudClient.Entity.of("1", "yappc-artifacts", Map.of( // GH-90000
                    "id", "22222222-2222-2222-2222-222222222222",
                    "path", "products/demo/phases/intent",
                    "version", "v-1",
                    "content", "YQ==",
                    "size", 1,
                    "tenantId", "tenant-alpha",
                    "createdAt", 1L
                )),
                DataCloudClient.Entity.of("2", "yappc-artifacts", Map.of( // GH-90000
                    "id", "33333333-3333-3333-3333-333333333333",
                    "path", "products/demo/phases/intent",
                    "version", "v-2",
                    "content", "Yg==",
                    "size", 1,
                    "tenantId", "tenant-alpha",
                    "createdAt", 2L
                ))
            )));

        List<String> versions = runPromise(() -> store.list("products/demo/phases/intent/ [GH-90000]"));

        assertThat(versions).containsExactlyInAnyOrder("v-1", "v-2"); // GH-90000
    }

    @Test
    @DisplayName("delete removes content and metadata through repository-backed deletes [GH-90000]")
    void deleteRemovesContentAndMetadata() { // GH-90000
        when(client.delete(eq("tenant-alpha [GH-90000]"), eq("yappc-artifacts [GH-90000]"), anyString()))
            .thenReturn(Promise.of(null)); // GH-90000
        when(client.delete(eq("tenant-alpha [GH-90000]"), eq("yappc-artifact-metadata [GH-90000]"), anyString()))
            .thenReturn(Promise.of(null)); // GH-90000

        runPromise(() -> store.delete("products/demo/phases/intent/v-1 [GH-90000]"));
    }

    @Test
    @DisplayName("default-tenant is rejected before any Data Cloud call [GH-90000]")
    void defaultTenantIsRejected() { // GH-90000
        runBlocking(() -> TenantContext.setCurrentTenantId("default-tenant [GH-90000]"));
        TenantContext.setCurrentTenantId("default-tenant [GH-90000]");

        assertThatThrownBy(() -> runPromise(() -> store.list("products/demo/phases/intent/ [GH-90000]")))
            .hasMessageContaining("does not allow default-tenant [GH-90000]");
    }
}