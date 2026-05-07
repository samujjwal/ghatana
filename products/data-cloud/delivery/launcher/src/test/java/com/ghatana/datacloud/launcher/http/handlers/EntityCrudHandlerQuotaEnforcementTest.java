package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.governance.QuotaCheckResult;
import com.ghatana.datacloud.governance.TenantQuotaService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for P0.5 tenant quota enforcement in entity write operations.
 */
@DisplayName("EntityCrudHandler — Tenant Quota Enforcement")
class EntityCrudHandlerQuotaEnforcementTest extends EventloopTestBase {

    private FakeEntityStore store;
    private EntityCrudHandler handler;

    @BeforeEach
    void setUp() {
        store = new FakeEntityStore();
        HttpHandlerSupport httpSupport = new HttpHandlerSupport(
            new com.fasterxml.jackson.databind.ObjectMapper(),
            "*", "GET,POST,PUT,DELETE,OPTIONS", "Content-Type,Authorization,X-Tenant-ID",
            false, "local"
        );
        handler = new EntityCrudHandler(store, httpSupport, (t, m) -> {});
    }

    @Test
    @DisplayName("P0.5: should allow save when quota is not exceeded")
    void shouldAllowSaveWhenQuotaNotExceeded() {
        TenantQuotaService quotaService = (tenantId, operationType, amount) ->
            QuotaCheckResult.permit();

        handler.withTenantQuotaService(quotaService);

        HttpRequest request = HttpRequest.post("http://localhost/entities/test-collection")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "test-tenant")
            .withBody("{\"id\":\"e1\",\"name\":\"Alpha\"}".getBytes())
            .build();

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(store.saved).hasSize(1);
    }

    @Test
    @DisplayName("P0.5: should reject save with 429 when quota is exceeded")
    void shouldRejectSaveWhenQuotaExceeded() {
        TenantQuotaService quotaService = (tenantId, operationType, amount) ->
            QuotaCheckResult.reject("Tenant storage quota exceeded", 100, 95);

        handler.withTenantQuotaService(quotaService);

        HttpRequest request = HttpRequest.post("http://localhost/entities/test-collection")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "test-tenant")
            .withBody("{\"id\":\"e1\",\"name\":\"Alpha\"}".getBytes())
            .build();

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        assertThat(response.getCode()).isEqualTo(429);
        String body = new String(response.getBody().asArray());
        assertThat(body).contains("Quota exceeded");
        assertThat(body).contains("storage quota exceeded");
        assertThat(store.saved).isEmpty();
    }

    @Test
    @DisplayName("P0.5: should allow save when no quota service is configured (degraded)")
    void shouldAllowSaveWhenNoQuotaService() {
        // No quota service configured
        HttpRequest request = HttpRequest.post("http://localhost/entities/test-collection")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "test-tenant")
            .withBody("{\"id\":\"e1\",\"name\":\"Alpha\"}".getBytes())
            .build();

        HttpResponse response = runPromise(() -> handler.handleSaveEntity(request));

        assertThat(response.getCode()).isEqualTo(200);
    }

    /**
     * Minimal fake entity store for unit testing.
     */
    static class FakeEntityStore implements DataCloudClient {
        java.util.List<DataCloudClient.Entity> saved = new java.util.ArrayList<>();

        @Override public Promise<java.util.Optional<DataCloudClient.Entity>> findById(String tenantId, String collection, String id) { return Promise.of(java.util.Optional.empty()); }
        @Override public Promise<DataCloudClient.Entity> save(String tenantId, String collection, java.util.Map<String, Object> data) {
            DataCloudClient.Entity entity = DataCloudClient.Entity.of(data.getOrDefault("id", "gen").toString(), collection, data);
            saved.add(entity);
            return Promise.of(entity);
        }
        @Override public Promise<java.util.List<DataCloudClient.Entity>> query(String tenantId, String collection, DataCloudClient.Query query) { return Promise.of(java.util.List.of()); }
        @Override public Promise<Void> delete(String tenantId, String collection, String id) { return Promise.complete(); }

        @Override public Promise<DataCloudClient.Offset> appendEvent(String tenantId, DataCloudClient.Event event) { return Promise.of(DataCloudClient.Offset.of(0)); }
        @Override public Promise<java.util.List<DataCloudClient.Event>> queryEvents(String tenantId, DataCloudClient.EventQuery query) { return Promise.of(java.util.List.of()); }
        @Override public DataCloudClient.Subscription tailEvents(String tenantId, DataCloudClient.TailRequest request, java.util.function.Consumer<DataCloudClient.Event> handler) { return new DataCloudClient.Subscription() { @Override public void cancel() { } @Override public boolean isCancelled() { return false; } }; }
        @Override public void close() { }
        @Override public com.ghatana.datacloud.spi.EntityStore entityStore() { return null; }
        @Override public com.ghatana.datacloud.spi.EventLogStore eventLogStore() { return null; }
    }
}
