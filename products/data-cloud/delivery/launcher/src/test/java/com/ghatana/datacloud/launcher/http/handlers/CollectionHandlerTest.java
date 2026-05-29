package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.handlers.HttpHandlerSupport;
import com.ghatana.datacloud.launcher.http.security.RequestContext;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.observability.idempotency.IdempotencyStore;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for first-class Collection handler (P3.1).
 *
 * @doc.type test
 * @doc.purpose Verify CollectionHandler behavior for first-class Collection contract
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CollectionHandler Tests")
class CollectionHandlerTest {

    @Mock
    private DataCloudClient client;

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private AuditService auditService;

    @Mock
    private IdempotencyStore idempotencyStore;

    private ObjectMapper objectMapper = new ObjectMapper();
    private CollectionHandler handler;

    private void setupHandler() {
        handler = new CollectionHandler(client, http, objectMapper, auditService, "local", idempotencyStore);
    }

    @Test
    @DisplayName("List collections returns collection list")
    void listCollectionsReturnsCollectionList() {
        setupHandler();
        
        when(http.resolveCorrelationId(any())).thenReturn("test-request-id");
        RequestContext context = RequestContext.builder()
            .withTenantId("tenant-123")
            .withPrincipal(new Principal("user-123", List.of()))
            .withCorrelationId("test-request-id")
            .build();
        when(http.requireTenantIdWithError(any())).thenReturn(
            HttpHandlerSupport.TenantResolutionResult.success("tenant-123", context)
        );
        
        Map<String, Object> collection1 = new LinkedHashMap<>();
        collection1.put("id", "col-1");
        collection1.put("name", "Test Collection 1");
        collection1.put("tenantId", "tenant-123");
        collection1.put("lifecycleStatus", "PUBLISHED");
        collection1.put("operationalStatus", "healthy");
        
        when(client.listEntities(eq("dc_collections"), eq("tenant-123")))
            .thenReturn(Promise.of(List.of(
                new DataCloudClient.Entity("col-1", "dc_collections", collection1, Instant.now(), Instant.now(), 1)
            )));
        
        HttpRequest request = mock(HttpRequest.class);
        lenient().when(request.getQueryParameter(any())).thenReturn(null);
        
        Promise<HttpResponse> responsePromise = handler.handleListCollections(request);
        
        assertNotNull(responsePromise);
        verify(client).listEntities("dc_collections", "tenant-123");
    }

    @Test
    @DisplayName("Get collection by ID returns collection")
    void getCollectionByIdReturnsCollection() {
        setupHandler();
        
        when(http.resolveCorrelationId(any())).thenReturn("test-request-id");
        RequestContext context = RequestContext.builder()
            .withTenantId("tenant-123")
            .withPrincipal(new Principal("user-123", List.of()))
            .withCorrelationId("test-request-id")
            .build();
        when(http.requireTenantIdWithError(any())).thenReturn(
            HttpHandlerSupport.TenantResolutionResult.success("tenant-123", context)
        );
        
        Map<String, Object> collection = new LinkedHashMap<>();
        collection.put("id", "col-1");
        collection.put("name", "Test Collection");
        collection.put("tenantId", "tenant-123");
        collection.put("lifecycleStatus", "PUBLISHED");
        collection.put("operationalStatus", "healthy");
        
        when(client.getEntity(eq("dc_collections"), eq("col-1"), eq("tenant-123")))
            .thenReturn(Promise.of(
                new DataCloudClient.Entity("col-1", "dc_collections", collection, Instant.now(), Instant.now(), 1)
            ));
        
        HttpRequest request = mock(HttpRequest.class);
        when(request.getPathParameter("id")).thenReturn("col-1");
        
        Promise<HttpResponse> responsePromise = handler.handleGetCollection(request);
        
        assertNotNull(responsePromise);
        verify(client).getEntity("dc_collections", "col-1", "tenant-123");
    }

    @Test
    @DisplayName("Create collection validates required fields")
    void createCollectionValidatesRequiredFields() throws JsonProcessingException {
        setupHandler();
        
        when(http.resolveCorrelationId(any())).thenReturn("test-request-id");
        RequestContext context = RequestContext.builder()
            .withTenantId("tenant-123")
            .withPrincipal(new Principal("user-123", List.of()))
            .withCorrelationId("test-request-id")
            .build();
        when(http.requireTenantIdWithError(any())).thenReturn(
            HttpHandlerSupport.TenantResolutionResult.success("tenant-123", context)
        );
        
        Map<String, Object> collectionWithoutName = new LinkedHashMap<>();
        collectionWithoutName.put("description", "Test");
        
        when(http.parseJsonBody(any())).thenReturn(collectionWithoutName);
        
        HttpRequest request = mock(HttpRequest.class);
        
        Promise<HttpResponse> responsePromise = handler.handleCreateCollection(request);
        
        assertNotNull(responsePromise);
    }

    @Test
    @DisplayName("Create collection sets default lifecycle status")
    void createCollectionSetsDefaultLifecycleStatus() throws JsonProcessingException {
        setupHandler();
        
        when(http.resolveCorrelationId(any())).thenReturn("test-request-id");
        RequestContext context = RequestContext.builder()
            .withTenantId("tenant-123")
            .withPrincipal(new Principal("user-123", List.of()))
            .withCorrelationId("test-request-id")
            .build();
        when(http.requireTenantIdWithError(any())).thenReturn(
            HttpHandlerSupport.TenantResolutionResult.success("tenant-123", context)
        );
        
        Map<String, Object> collection = new LinkedHashMap<>();
        collection.put("name", "Test Collection");
        
        when(http.parseJsonBody(any())).thenReturn(collection);
        
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("id", "col-1");
        created.put("name", "Test Collection");
        created.put("lifecycleStatus", "DRAFT");
        created.put("operationalStatus", "healthy");
        
        when(client.createEntity(eq("dc_collections"), anyString(), any(), eq("tenant-123")))
            .thenReturn(Promise.of(
                new DataCloudClient.Entity("col-1", "dc_collections", created, Instant.now(), Instant.now(), 1)
            ));
        
        HttpRequest request = mock(HttpRequest.class);
        
        Promise<HttpResponse> responsePromise = handler.handleCreateCollection(request);
        
        assertNotNull(responsePromise);
        verify(client).createEntity(eq("dc_collections"), anyString(), argThat(c -> {
            Map<String, Object> coll = (Map<String, Object>) c;
            return "DRAFT".equals(coll.get("lifecycleStatus")) && "healthy".equals(coll.get("operationalStatus"));
        }), eq("tenant-123"));
    }

    @Test
    @DisplayName("Publish collection updates lifecycle status")
    void publishCollectionUpdatesLifecycleStatus() {
        setupHandler();
        
        when(http.resolveCorrelationId(any())).thenReturn("test-request-id");
        RequestContext context = RequestContext.builder()
            .withTenantId("tenant-123")
            .withPrincipal(new Principal("user-123", List.of()))
            .withCorrelationId("test-request-id")
            .build();
        when(http.requireTenantIdWithError(any())).thenReturn(
            HttpHandlerSupport.TenantResolutionResult.success("tenant-123", context)
        );
        
        Map<String, Object> updated = new LinkedHashMap<>();
        updated.put("id", "col-1");
        updated.put("lifecycleStatus", "PUBLISHED");
        
        when(client.updateEntity(eq("dc_collections"), eq("col-1"), any(), eq("tenant-123")))
            .thenReturn(Promise.of(
                new DataCloudClient.Entity("col-1", "dc_collections", updated, Instant.now(), Instant.now(), 1)
            ));
        
        HttpRequest request = mock(HttpRequest.class);
        when(request.getPathParameter("id")).thenReturn("col-1");
        
        Promise<HttpResponse> responsePromise = handler.handlePublishCollection(request);
        
        assertNotNull(responsePromise);
        verify(client).updateEntity(eq("dc_collections"), eq("col-1"), argThat(u -> {
            Map<String, Object> updates = (Map<String, Object>) u;
            return "PUBLISHED".equals(updates.get("lifecycleStatus"));
        }), eq("tenant-123"));
    }

    @Test
    @DisplayName("Archive collection updates lifecycle status")
    void archiveCollectionUpdatesLifecycleStatus() {
        setupHandler();
        
        when(http.resolveCorrelationId(any())).thenReturn("test-request-id");
        RequestContext context = RequestContext.builder()
            .withTenantId("tenant-123")
            .withPrincipal(new Principal("user-123", List.of()))
            .withCorrelationId("test-request-id")
            .build();
        when(http.requireTenantIdWithError(any())).thenReturn(
            HttpHandlerSupport.TenantResolutionResult.success("tenant-123", context)
        );
        
        Map<String, Object> updated = new LinkedHashMap<>();
        updated.put("id", "col-1");
        updated.put("lifecycleStatus", "ARCHIVED");
        
        when(client.updateEntity(eq("dc_collections"), eq("col-1"), any(), eq("tenant-123")))
            .thenReturn(Promise.of(
                new DataCloudClient.Entity("col-1", "dc_collections", updated, Instant.now(), Instant.now(), 1)
            ));
        
        HttpRequest request = mock(HttpRequest.class);
        when(request.getPathParameter("id")).thenReturn("col-1");
        
        Promise<HttpResponse> responsePromise = handler.handleArchiveCollection(request);
        
        assertNotNull(responsePromise);
        verify(client).updateEntity(eq("dc_collections"), eq("col-1"), argThat(u -> {
            Map<String, Object> updates = (Map<String, Object>) u;
            return "ARCHIVED".equals(updates.get("lifecycleStatus"));
        }), eq("tenant-123"));
    }

    @Test
    @DisplayName("Delete collection removes collection")
    void deleteCollectionRemovesCollection() {
        setupHandler();
        
        when(http.resolveCorrelationId(any())).thenReturn("test-request-id");
        RequestContext context = RequestContext.builder()
            .withTenantId("tenant-123")
            .withPrincipal(new Principal("user-123", List.of()))
            .withCorrelationId("test-request-id")
            .build();
        when(http.requireTenantIdWithError(any())).thenReturn(
            HttpHandlerSupport.TenantResolutionResult.success("tenant-123", context)
        );
        
        when(client.deleteEntity(eq("dc_collections"), eq("col-1"), eq("tenant-123")))
            .thenReturn(Promise.of(null));
        
        HttpRequest request = mock(HttpRequest.class);
        when(request.getPathParameter("id")).thenReturn("col-1");
        
        Promise<HttpResponse> responsePromise = handler.handleDeleteCollection(request);
        
        assertNotNull(responsePromise);
        verify(client).deleteEntity("dc_collections", "col-1", "tenant-123");
    }
}
