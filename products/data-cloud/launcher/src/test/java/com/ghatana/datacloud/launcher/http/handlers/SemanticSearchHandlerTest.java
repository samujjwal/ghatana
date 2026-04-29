package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataRecord;
import com.ghatana.datacloud.plugins.vector.VectorMemoryPlugin;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Regression tests for SemanticSearchHandler tenant enforcement and entity ID translation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("SemanticSearchHandler")
@ExtendWith(MockitoExtension.class) // GH-90000
class SemanticSearchHandlerTest extends EventloopTestBase {

    @Mock
    private VectorMemoryPlugin vectorPlugin;

    @Mock
    private DataCloudClient client;

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    private SemanticSearchHandler handler;

    @BeforeEach
    void setUp() { // GH-90000
        handler = new SemanticSearchHandler(vectorPlugin, client, http, new ObjectMapper()); // GH-90000
        lenient().when(http.errorResponse(400, "X-Tenant-Id header is required")).thenReturn(errorResponse); // GH-90000
    }

    @Test
    @DisplayName("similar-entities rejects missing tenant before vector lookup")
    void similarEntitiesRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleSimilarEntities(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(vectorPlugin, never()).findSimilar(any(), any(Integer.class), any(Boolean.class), any()); // GH-90000
    }

    @Test
    @DisplayName("collection rag rejects missing tenant before reading body")
    void collectionRagRejectsMissingTenant() { // GH-90000
        when(http.requireTenantIdOrFail(request)).thenReturn(null); // GH-90000

        HttpResponse response = runPromise(() -> handler.handleCollectionRag(request)); // GH-90000

        assertThat(response).isSameAs(errorResponse); // GH-90000
        verify(request, never()).loadBody(); // GH-90000
        verify(vectorPlugin, never()).search(any()); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Entity ID translation: indexEntity / deleteEntity
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tests that verify SemanticSearchHandler.parseUuid() translates entity IDs consistently:
     * - plain UUID strings are used as-is,
     * - non-UUID strings are converted via UUID.nameUUIDFromBytes (deterministic),
     * - the original entity ID is preserved in the {@code sourceEntityId} metadata field.
     *
     * <p>Uses a real VectorMemoryPlugin (no mocks) so that actual storage behaviour is observed.
     */
    @Nested
    @DisplayName("indexEntity – entity ID translation and sourceEntityId metadata")
    class IndexEntityIdTranslation {

        private VectorMemoryPlugin realPlugin;
        private SemanticSearchHandler realHandler;

        @BeforeEach
        void setUpRealHandler() { // GH-90000
            realPlugin = new VectorMemoryPlugin();
            realHandler = new SemanticSearchHandler(realPlugin, client, http, new ObjectMapper()); // GH-90000
        }

        @Test
        @DisplayName("non-UUID entity ID is translated to deterministic UUID via nameUUIDFromBytes")
        void nonUuidEntityIdIsTranslatedToDeterministicUuid() { // GH-90000
            String externalId = "my-entity-non-uuid-id";
            DataCloudClient.Entity entity = new DataCloudClient.Entity(
                    externalId, "col", Map.of("key", "val"),
                    Instant.now(), Instant.now(), 1L);

            runPromise(() -> realHandler.indexEntity("tenant-1", "col", entity));

            UUID expectedKey = UUID.nameUUIDFromBytes(externalId.getBytes(StandardCharsets.UTF_8));
            Optional<DataRecord> retrieved = runPromise(
                    () -> realPlugin.retrieve(expectedKey.toString(), "tenant-1"));

            assertThat(retrieved).isPresent(); // GH-90000
            assertThat(retrieved.get().getId()).isEqualTo(expectedKey); // GH-90000
        }

        @Test
        @DisplayName("valid UUID entity ID is stored under that exact UUID")
        void uuidEntityIdStoredUnderSameUuid() { // GH-90000
            UUID entityUuid = UUID.randomUUID();
            DataCloudClient.Entity entity = new DataCloudClient.Entity(
                    entityUuid.toString(), "col", Map.of(),
                    Instant.now(), Instant.now(), 1L);

            runPromise(() -> realHandler.indexEntity("tenant-1", "col", entity));

            Optional<DataRecord> retrieved = runPromise(
                    () -> realPlugin.retrieve(entityUuid.toString(), "tenant-1"));

            assertThat(retrieved).isPresent(); // GH-90000
            assertThat(retrieved.get().getId()).isEqualTo(entityUuid); // GH-90000
        }

        @Test
        @DisplayName("indexEntity preserves original entity ID in sourceEntityId metadata")
        void indexEntityPreservesSourceEntityIdInMetadata() { // GH-90000
            String externalId = "external-entity-preserve";
            DataCloudClient.Entity entity = new DataCloudClient.Entity(
                    externalId, "col", Map.of(),
                    Instant.now(), Instant.now(), 1L);

            runPromise(() -> realHandler.indexEntity("tenant-1", "col", entity));

            UUID expectedKey = UUID.nameUUIDFromBytes(externalId.getBytes(StandardCharsets.UTF_8));
            Optional<DataRecord> retrieved = runPromise(
                    () -> realPlugin.retrieve(expectedKey.toString(), "tenant-1"));

            assertThat(retrieved).isPresent(); // GH-90000
            assertThat(retrieved.get().getMetadata()).containsEntry("sourceEntityId", externalId); // GH-90000
        }

        @Test
        @DisplayName("deleteEntity applies same UUID translation as indexEntity – record is removed")
        void deleteEntityAppliesSameUuidTranslationAsIndexEntity() { // GH-90000
            String externalId = "entity-to-delete";
            DataCloudClient.Entity entity = new DataCloudClient.Entity(
                    externalId, "col", Map.of(),
                    Instant.now(), Instant.now(), 1L);

            runPromise(() -> realHandler.indexEntity("tenant-1", "col", entity));

            UUID expectedKey = UUID.nameUUIDFromBytes(externalId.getBytes(StandardCharsets.UTF_8));
            assertThat(runPromise(() -> realPlugin.retrieve(expectedKey.toString(), "tenant-1")))
                    .isPresent(); // record exists before delete // GH-90000

            runPromise(() -> realHandler.deleteEntity("tenant-1", externalId));

            Optional<DataRecord> afterDelete = runPromise(
                    () -> realPlugin.retrieve(expectedKey.toString(), "tenant-1"));
            assertThat(afterDelete).isEmpty(); // GH-90000
        }
    }
}