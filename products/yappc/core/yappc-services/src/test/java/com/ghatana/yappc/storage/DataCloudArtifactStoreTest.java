package com.ghatana.yappc.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@DisplayName("DataCloudArtifactStore")
class DataCloudArtifactStoreTest extends EventloopTestBase {

    @Mock
    private DataCloudClient client;

    private DataCloudArtifactStore store;

    @BeforeEach
    void setUp() { // GH-90000
        MockitoAnnotations.openMocks(this); // GH-90000
        TenantContext.setCurrentTenantId("tenant-alpha");
        runBlocking(() -> TenantContext.setCurrentTenantId("tenant-alpha"));
        store = new DataCloudArtifactStore(client, new ObjectMapper()); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        runBlocking(TenantContext::clear); // GH-90000
        TenantContext.clear(); // GH-90000
    }

    @Test
    @DisplayName("put stores artifact through the canonical repository seam")
    void putStoresArtifactThroughRepositorySeam() { // GH-90000
        AtomicReference<Map<String, Object>> savedPayloadRef = new AtomicReference<>(); // GH-90000
        when(client.save(eq("tenant-alpha"), eq("yappc-artifacts"), any()))
            .thenAnswer(invocation -> { // GH-90000
                Map<String, Object> payload = invocation.getArgument(2); // GH-90000
                savedPayloadRef.set(payload); // GH-90000
                return Promise.of(DataCloudClient.Entity.of( // GH-90000
                    String.valueOf(payload.get("id")),
                    "yappc-artifacts",
                    payload
                ));
            });

        String version = runPromise(() -> store.put("products/demo/phases/intent", "artifact-body".getBytes(StandardCharsets.UTF_8))); // GH-90000

        Map<String, Object> payload = savedPayloadRef.get(); // GH-90000
        assertThat(version).isNotBlank(); // GH-90000
        assertThat(payload.get("path")).isEqualTo("products/demo/phases/intent");
        assertThat(payload.get("version")).isEqualTo(version);
        assertThat(payload.get("tenantId")).isEqualTo("tenant-alpha");
        assertThat(payload.get("content")).isEqualTo(Base64.getEncoder().encodeToString("artifact-body".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("get loads artifact content by deterministic repository ID")
    void getLoadsArtifactContentByDeterministicId() { // GH-90000
        String encodedContent = Base64.getEncoder().encodeToString("artifact-body".getBytes(StandardCharsets.UTF_8)); // GH-90000
        String artifactId = "11111111-1111-1111-1111-111111111111";
        when(client.findById(eq("tenant-alpha"), eq("yappc-artifacts"), anyString()))
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

        byte[] content = runPromise(() -> store.get("products/demo/phases/intent/v-1"));

        assertThat(new String(content, StandardCharsets.UTF_8)).isEqualTo("artifact-body");
    }

    @Test
    @DisplayName("list normalizes the version prefix before querying the repository seam")
    void listNormalizesVersionPrefixBeforeQuerying() { // GH-90000
        when(client.query(eq("tenant-alpha"), eq("yappc-artifacts"), any(DataCloudClient.Query.class)))
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

        List<String> versions = runPromise(() -> store.list("products/demo/phases/intent/"));

        assertThat(versions).containsExactlyInAnyOrder("v-1", "v-2"); // GH-90000
    }

    @Test
    @DisplayName("delete removes content and metadata through repository-backed deletes")
    void deleteRemovesContentAndMetadata() { // GH-90000
        when(client.delete(eq("tenant-alpha"), eq("yappc-artifacts"), anyString()))
            .thenReturn(Promise.of(null)); // GH-90000
        when(client.delete(eq("tenant-alpha"), eq("yappc-artifact-metadata"), anyString()))
            .thenReturn(Promise.of(null)); // GH-90000

        runPromise(() -> store.delete("products/demo/phases/intent/v-1"));
    }

    @Test
    @DisplayName("default-tenant is rejected before any Data Cloud call")
    void defaultTenantIsRejected() { // GH-90000
        runBlocking(() -> TenantContext.setCurrentTenantId("default-tenant"));
        TenantContext.setCurrentTenantId("default-tenant");

        assertThatThrownBy(() -> runPromise(() -> store.list("products/demo/phases/intent/")))
            .hasMessageContaining("does not allow default-tenant");
    }
}