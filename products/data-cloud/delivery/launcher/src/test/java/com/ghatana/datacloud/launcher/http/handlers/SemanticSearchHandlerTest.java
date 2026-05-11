package com.ghatana.datacloud.launcher.http.handlers;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport.TenantResolutionResult;

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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
@ExtendWith(MockitoExtension.class) 
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
    void setUp() { 
        handler = new SemanticSearchHandler(vectorPlugin, client, http, new ObjectMapper()); 
        lenient().when(http.errorResponse(anyInt(), anyString())).thenReturn(errorResponse); 
    }

    @Test
    @DisplayName("similar-entities rejects missing tenant before vector lookup")
    void similarEntitiesRejectsMissingTenant() { 
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleSimilarEntities(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(vectorPlugin, never()).findSimilar(any(), any(Integer.class), any(Boolean.class), any()); 
    }

    @Test
    @DisplayName("collection rag rejects missing tenant before reading body")
    void collectionRagRejectsMissingTenant() { 
        when(http.requireTenantIdWithError(request)).thenReturn(TenantResolutionResult.error(401, "Unauthorized")); 

        HttpResponse response = runPromise(() -> handler.handleCollectionRag(request)); 

        assertThat(response).isSameAs(errorResponse); 
        verify(request, never()).loadBody(); 
        verify(vectorPlugin, never()).search(any()); 
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
        void setUpRealHandler() { 
            realPlugin = new VectorMemoryPlugin();
            realHandler = new SemanticSearchHandler(realPlugin, client, http, new ObjectMapper()); 
        }

        @Test
        @DisplayName("non-UUID entity ID is translated to deterministic UUID via nameUUIDFromBytes")
        void nonUuidEntityIdIsTranslatedToDeterministicUuid() { 
            String externalId = "my-entity-non-uuid-id";
            DataCloudClient.Entity entity = new DataCloudClient.Entity(
                    externalId, "col", Map.of("key", "val"),
                    Instant.now(), Instant.now(), 1L);

            runPromise(() -> realHandler.indexEntity("tenant-1", "col", entity));

            UUID expectedKey = UUID.nameUUIDFromBytes(externalId.getBytes(StandardCharsets.UTF_8));
            Optional<DataRecord> retrieved = runPromise(
                    () -> realPlugin.retrieve(expectedKey.toString(), "tenant-1"));

            assertThat(retrieved).isPresent(); 
            assertThat(retrieved.get().getId()).isEqualTo(expectedKey); 
        }

        @Test
        @DisplayName("valid UUID entity ID is stored under that exact UUID")
        void uuidEntityIdStoredUnderSameUuid() { 
            UUID entityUuid = UUID.randomUUID();
            DataCloudClient.Entity entity = new DataCloudClient.Entity(
                    entityUuid.toString(), "col", Map.of(),
                    Instant.now(), Instant.now(), 1L);

            runPromise(() -> realHandler.indexEntity("tenant-1", "col", entity));

            Optional<DataRecord> retrieved = runPromise(
                    () -> realPlugin.retrieve(entityUuid.toString(), "tenant-1"));

            assertThat(retrieved).isPresent(); 
            assertThat(retrieved.get().getId()).isEqualTo(entityUuid); 
        }

        @Test
        @DisplayName("indexEntity preserves original entity ID in sourceEntityId metadata")
        void indexEntityPreservesSourceEntityIdInMetadata() { 
            String externalId = "external-entity-preserve";
            DataCloudClient.Entity entity = new DataCloudClient.Entity(
                    externalId, "col", Map.of(),
                    Instant.now(), Instant.now(), 1L);

            runPromise(() -> realHandler.indexEntity("tenant-1", "col", entity));

            UUID expectedKey = UUID.nameUUIDFromBytes(externalId.getBytes(StandardCharsets.UTF_8));
            Optional<DataRecord> retrieved = runPromise(
                    () -> realPlugin.retrieve(expectedKey.toString(), "tenant-1"));

            assertThat(retrieved).isPresent(); 
            assertThat(retrieved.get().getMetadata()).containsEntry("sourceEntityId", externalId); 
        }

        @Test
        @DisplayName("deleteEntity applies same UUID translation as indexEntity – record is removed")
        void deleteEntityAppliesSameUuidTranslationAsIndexEntity() { 
            String externalId = "entity-to-delete";
            DataCloudClient.Entity entity = new DataCloudClient.Entity(
                    externalId, "col", Map.of(),
                    Instant.now(), Instant.now(), 1L);

            runPromise(() -> realHandler.indexEntity("tenant-1", "col", entity));

            UUID expectedKey = UUID.nameUUIDFromBytes(externalId.getBytes(StandardCharsets.UTF_8));
            assertThat(runPromise(() -> realPlugin.retrieve(expectedKey.toString(), "tenant-1")))
                    .isPresent(); // record exists before delete 

            runPromise(() -> realHandler.deleteEntity("tenant-1", externalId));

            Optional<DataRecord> afterDelete = runPromise(
                    () -> realPlugin.retrieve(expectedKey.toString(), "tenant-1"));
            assertThat(afterDelete).isEmpty(); 
        }
    }
}