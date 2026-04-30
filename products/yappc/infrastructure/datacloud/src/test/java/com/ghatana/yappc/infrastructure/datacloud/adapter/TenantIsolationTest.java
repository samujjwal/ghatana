package com.ghatana.yappc.infrastructure.datacloud.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.Identifiable;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests verifying tenant isolation guarantees in {@link YappcDataCloudRepository}.
 *
 * <p>Each operation resolves the current tenant ID from {@link TenantContext} at call-time and
 * passes it as a scoping parameter to the underlying {@link DataCloudClient}. These tests
 * confirm:
 * <ol>
 *   <li>Cross-tenant reads: a query by tenant-B never sees tenant-A's data.</li>
 *   <li>Same-tenant reads: a query by the owning tenant returns its data.</li>
 *   <li>Tenant-ID propagation: every repository call carries the exact ID from TenantContext.</li>
 *   <li>Default-tenant fallback: when no explicit context is set, "default-tenant" is used.</li>
 * </ol>
 *
 * <p>Note: {@link TenantContext#getCurrentTenantId()} falls back to {@code "default-tenant"} when // GH-90000
 * the ThreadLocal is empty; it never returns {@code null}. Consequently, the SecurityException path
 * in {@code resolveTenantId()} is only reachable if a caller explicitly sets a blank string via // GH-90000
 * {@link TenantContext#setCurrentTenantId(String)}. // GH-90000
 *
 * @doc.type class
 * @doc.purpose Tenant isolation integration tests for YappcDataCloudRepository (plan 4.4.3) // GH-90000
 * @doc.layer infrastructure
 * @doc.pattern Test
 */
@DisplayName("TenantIsolationTest — YappcDataCloudRepository (4.4.3)")
class TenantIsolationTest extends EventloopTestBase {

    private static final String TENANT_ALPHA = "tenant-alpha";
    private static final String TENANT_BETA  = "tenant-beta";

    @Mock
    private DataCloudClient client;

    private YappcDataCloudRepository<TestEntity> repository;

    @BeforeEach
    void setUp() { // GH-90000
        MockitoAnnotations.openMocks(this); // GH-90000
        ObjectMapper objectMapper = new ObjectMapper(); // GH-90000
        objectMapper.registerModule(new JavaTimeModule()); // GH-90000
        YappcEntityMapper mapper = new YappcEntityMapper(objectMapper); // GH-90000
        repository = new YappcDataCloudRepository<>( // GH-90000
                client, mapper, "test_collection", TestEntity.class);
    }

    @AfterEach
    void tearDown() { // GH-90000
        runBlocking(TenantContext::clear); // GH-90000
        TenantContext.clear(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cross-Tenant Isolation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cross-Tenant Isolation")
    class CrossTenantIsolation {

        @Test
        @DisplayName("findAll under tenant-B returns empty when tenant-A owns the data")
        void shouldReturnEmptyForDifferentTenant() { // GH-90000
            // GIVEN — tenant-A saves an entity
            UUID entityId = UUID.randomUUID(); // GH-90000
            DataCloudClient.Entity alphaEntity = makeEntity(TENANT_ALPHA, entityId); // GH-90000

            TenantContext.setCurrentTenantId(TENANT_ALPHA); // GH-90000
            runBlocking(() -> TenantContext.setCurrentTenantId(TENANT_ALPHA)); // GH-90000
            when(client.save(eq(TENANT_ALPHA), anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(alphaEntity)); // GH-90000
            runPromise(() -> repository.save(new TestEntity(entityId, "alpha-item", 1))); // GH-90000

            // WHEN — tenant-B switches context and queries findAll
            TenantContext.setCurrentTenantId(TENANT_BETA); // GH-90000
            runBlocking(() -> TenantContext.setCurrentTenantId(TENANT_BETA)); // GH-90000
            when(client.query(eq(TENANT_BETA), anyString(), any(DataCloudClient.Query.class))) // GH-90000
                    .thenReturn(Promise.of(List.of())); // tenant-B namespace has no data // GH-90000

            List<TestEntity> result = runPromise(() -> repository.findAll()); // GH-90000

            // THEN — result is empty; DataCloudClient was called with TENANT_BETA, not TENANT_ALPHA
            assertThat(result).isEmpty(); // GH-90000

            ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class); // GH-90000
            verify(client).query( // GH-90000
                    tenantCaptor.capture(), anyString(), any(DataCloudClient.Query.class)); // GH-90000
            assertThat(tenantCaptor.getValue()) // GH-90000
                    .as("findAll must scope to the active tenant, not the writer's tenant")
                    .isEqualTo(TENANT_BETA); // GH-90000
        }

        @Test
        @DisplayName("findAll under same tenant returns the saved entity")
        void shouldReturnEntityForSameTenant() { // GH-90000
            // GIVEN — tenant-A saves and then queries under the same context
            UUID entityId = UUID.randomUUID(); // GH-90000
            DataCloudClient.Entity alphaEntity = makeEntity(TENANT_ALPHA, entityId); // GH-90000

            TenantContext.setCurrentTenantId(TENANT_ALPHA); // GH-90000
            runBlocking(() -> TenantContext.setCurrentTenantId(TENANT_ALPHA)); // GH-90000
            when(client.save(eq(TENANT_ALPHA), anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(alphaEntity)); // GH-90000
            when(client.query(eq(TENANT_ALPHA), anyString(), any(DataCloudClient.Query.class))) // GH-90000
                    .thenReturn(Promise.of(List.of(alphaEntity))); // GH-90000

            runPromise(() -> repository.save(new TestEntity(entityId, "alpha-item", 1))); // GH-90000

            // WHEN — same tenant queries findAll
            List<TestEntity> result = runPromise(() -> repository.findAll()); // GH-90000

            // THEN — the entity is returned
            assertThat(result).hasSize(1); // GH-90000
            verify(client).query( // GH-90000
                    eq(TENANT_ALPHA), anyString(), any(DataCloudClient.Query.class)); // GH-90000
        }

        @Test
        @DisplayName("explicit blank tenant triggers SecurityException from resolveTenantId")
        void shouldThrowSecurityExceptionForBlankTenant() { // GH-90000
            // GIVEN — a blank tenant ID is explicitly set (simulates misconfigured filter) // GH-90000
            TenantContext.setCurrentTenantId("   ");
            runBlocking(() -> TenantContext.setCurrentTenantId("   "));

            // WHEN / THEN — any repository operation throws SecurityException before hitting the DB
            assertThat(runPromiseThrowing(() -> repository.findAll())) // GH-90000
                    .isInstanceOfAny(SecurityException.class, RuntimeException.class) // GH-90000
                    .satisfiesAnyOf( // GH-90000
                            ex -> assertThat(ex).isInstanceOf(SecurityException.class), // GH-90000
                            ex -> assertThat(ex.getCause()).isInstanceOf(SecurityException.class)); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tenant-ID Propagation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Tenant ID Propagation")
    class TenantIdPropagation {

        @Test
        @DisplayName("save propagates TenantContext tenant ID to EntityRepository")
        void savePropagatesTenantId() { // GH-90000
            // GIVEN
            UUID entityId = UUID.randomUUID(); // GH-90000
            TenantContext.setCurrentTenantId(TENANT_ALPHA); // GH-90000
            runBlocking(() -> TenantContext.setCurrentTenantId(TENANT_ALPHA)); // GH-90000
            when(client.save(anyString(), anyString(), any())) // GH-90000
                    .thenReturn(Promise.of(makeEntity(TENANT_ALPHA, entityId))); // GH-90000

            // WHEN
            runPromise(() -> repository.save(new TestEntity(entityId, "name", 0))); // GH-90000

            // THEN — DataCloudClient.save received exactly TENANT_ALPHA
            ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class); // GH-90000
            verify(client).save(tenantCaptor.capture(), anyString(), any()); // GH-90000
            assertThat(tenantCaptor.getValue()).isEqualTo(TENANT_ALPHA); // GH-90000
        }

        @Test
        @DisplayName("findById propagates TenantContext tenant ID to EntityRepository")
        void findByIdPropagatesTenantId() { // GH-90000
            // GIVEN
            TenantContext.setCurrentTenantId(TENANT_ALPHA); // GH-90000
            runBlocking(() -> TenantContext.setCurrentTenantId(TENANT_ALPHA)); // GH-90000
            when(client.findById(anyString(), anyString(), anyString())) // GH-90000
                    .thenReturn(Promise.of(Optional.empty())); // GH-90000

            // WHEN
            runPromise(() -> repository.findById(UUID.randomUUID())); // GH-90000

            // THEN
            ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class); // GH-90000
            verify(client).findById(tenantCaptor.capture(), anyString(), anyString()); // GH-90000
            assertThat(tenantCaptor.getValue()).isEqualTo(TENANT_ALPHA); // GH-90000
        }

        @Test
        @DisplayName("deleteById propagates TenantContext tenant ID to EntityRepository")
        void deleteByIdPropagatesTenantId() { // GH-90000
            // GIVEN
            UUID entityId = UUID.randomUUID(); // GH-90000
            TenantContext.setCurrentTenantId(TENANT_ALPHA); // GH-90000
            runBlocking(() -> TenantContext.setCurrentTenantId(TENANT_ALPHA)); // GH-90000
            when(client.delete(anyString(), anyString(), anyString())) // GH-90000
                    .thenReturn(Promise.of(null)); // GH-90000

            // WHEN
            runPromise(() -> repository.deleteById(entityId)); // GH-90000

            // THEN
            ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class); // GH-90000
            verify(client).delete(tenantCaptor.capture(), anyString(), anyString()); // GH-90000
            assertThat(tenantCaptor.getValue()).isEqualTo(TENANT_ALPHA); // GH-90000
        }

        @Test
        @DisplayName("when no tenant is explicitly set, 'default-tenant' fallback is used")
        void noExplicitTenantUsesDefaultFallback() { // GH-90000
            // GIVEN — TenantContext cleared; getCurrentTenantId() returns "default-tenant" // GH-90000
            TenantContext.clear(); // GH-90000
            when(client.query(anyString(), anyString(), any(DataCloudClient.Query.class))) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000

            // WHEN
            runPromise(() -> repository.findAll()); // GH-90000

            // THEN — the fallback sentinel is forwarded to DataCloudClient
            ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class); // GH-90000
            verify(client).query( // GH-90000
                    tenantCaptor.capture(), anyString(), any(DataCloudClient.Query.class)); // GH-90000
            assertThat(tenantCaptor.getValue()) // GH-90000
                    .as("TenantContext.getCurrentTenantId() defaults to 'default-tenant' when not set")
                    .isEqualTo("default-tenant");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Runs a promise-returning callable and returns the exception that was thrown, without
     * failing the test if no exception is expected. Used to assert threw-cases cleanly.
     */
    private Throwable runPromiseThrowing(java.util.concurrent.Callable<io.activej.promise.Promise<?>> callable) { // GH-90000
        try {
            @SuppressWarnings("unchecked")
            java.util.concurrent.Callable<io.activej.promise.Promise<Object>> typed =
                (java.util.concurrent.Callable<io.activej.promise.Promise<Object>>) (Object) callable; // GH-90000
            runPromise(typed); // GH-90000
            return null;
        } catch (Throwable t) { // GH-90000
            return t;
        }
    }

    private DataCloudClient.Entity makeEntity(String tenantId, UUID id) { // GH-90000
        return DataCloudClient.Entity.of( // GH-90000
                id.toString(), "test_collection", // GH-90000
                Map.of("id", id.toString(), "name", "item", "value", 1)); // GH-90000
    }

    record TestEntity(UUID id, String name, int value) implements Identifiable<UUID> { // GH-90000
        @Override
        public UUID getId() { // GH-90000
            return id;
        }
    }
}
