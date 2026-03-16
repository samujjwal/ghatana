package com.ghatana.yappc.infrastructure.datacloud.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.products.yappc.domain.Identifiable;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests verifying tenant isolation guarantees in {@link YappcDataCloudRepository}.
 *
 * <p>Each operation resolves the current tenant ID from {@link TenantContext} at call-time and
 * passes it as a scoping parameter to the underlying {@link EntityRepository}. These tests
 * confirm:
 * <ol>
 *   <li>Cross-tenant reads: a query by tenant-B never sees tenant-A's data.</li>
 *   <li>Same-tenant reads: a query by the owning tenant returns its data.</li>
 *   <li>Tenant-ID propagation: every repository call carries the exact ID from TenantContext.</li>
 *   <li>Default-tenant fallback: when no explicit context is set, "default-tenant" is used.</li>
 * </ol>
 *
 * <p>Note: {@link TenantContext#getCurrentTenantId()} falls back to {@code "default-tenant"} when
 * the ThreadLocal is empty; it never returns {@code null}. Consequently, the SecurityException path
 * in {@code resolveTenantId()} is only reachable if a caller explicitly sets a blank string via
 * {@link TenantContext#setCurrentTenantId(String)}.
 *
 * @doc.type class
 * @doc.purpose Tenant isolation integration tests for YappcDataCloudRepository (plan 4.4.3)
 * @doc.layer infrastructure
 * @doc.pattern Test
 */
@DisplayName("TenantIsolationTest — YappcDataCloudRepository (4.4.3)")
class TenantIsolationTest extends EventloopTestBase {

    private static final String TENANT_ALPHA = "tenant-alpha";
    private static final String TENANT_BETA  = "tenant-beta";

    @Mock
    private EntityRepository entityRepository;

    private YappcDataCloudRepository<TestEntity> repository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        YappcEntityMapper mapper = new YappcEntityMapper(objectMapper);
        repository = new YappcDataCloudRepository<>(
                entityRepository, mapper, "test_collection", TestEntity.class);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cross-Tenant Isolation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cross-Tenant Isolation")
    class CrossTenantIsolation {

        @Test
        @DisplayName("findAll under tenant-B returns empty when tenant-A owns the data")
        void shouldReturnEmptyForDifferentTenant() {
            // GIVEN — tenant-A saves an entity
            UUID entityId = UUID.randomUUID();
            Entity alphaEntity = makeEntity(TENANT_ALPHA, entityId);

            TenantContext.setCurrentTenantId(TENANT_ALPHA);
            when(entityRepository.save(eq(TENANT_ALPHA), any(Entity.class)))
                    .thenReturn(Promise.of(alphaEntity));
            runPromise(() -> repository.save(new TestEntity(entityId, "alpha-item", 1)));

            // WHEN — tenant-B switches context and queries findAll
            TenantContext.setCurrentTenantId(TENANT_BETA);
            when(entityRepository.findAll(eq(TENANT_BETA), anyString(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Promise.of(List.of())); // tenant-B namespace has no data

            List<TestEntity> result = runPromise(() -> repository.findAll());

            // THEN — result is empty; EntityRepository was called with TENANT_BETA, not TENANT_ALPHA
            assertThat(result).isEmpty();

            ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);
            verify(entityRepository).findAll(
                    tenantCaptor.capture(), anyString(), any(), any(), anyInt(), anyInt());
            assertThat(tenantCaptor.getValue())
                    .as("findAll must scope to the active tenant, not the writer's tenant")
                    .isEqualTo(TENANT_BETA);
        }

        @Test
        @DisplayName("findAll under same tenant returns the saved entity")
        void shouldReturnEntityForSameTenant() {
            // GIVEN — tenant-A saves and then queries under the same context
            UUID entityId = UUID.randomUUID();
            Entity alphaEntity = makeEntity(TENANT_ALPHA, entityId);

            TenantContext.setCurrentTenantId(TENANT_ALPHA);
            when(entityRepository.save(eq(TENANT_ALPHA), any(Entity.class)))
                    .thenReturn(Promise.of(alphaEntity));
            when(entityRepository.findAll(eq(TENANT_ALPHA), anyString(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Promise.of(List.of(alphaEntity)));

            runPromise(() -> repository.save(new TestEntity(entityId, "alpha-item", 1)));

            // WHEN — same tenant queries findAll
            List<TestEntity> result = runPromise(() -> repository.findAll());

            // THEN — the entity is returned
            assertThat(result).hasSize(1);
            verify(entityRepository).findAll(
                    eq(TENANT_ALPHA), anyString(), any(), any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("explicit blank tenant triggers SecurityException from resolveTenantId")
        void shouldThrowSecurityExceptionForBlankTenant() {
            // GIVEN — a blank tenant ID is explicitly set (simulates misconfigured filter)
            TenantContext.setCurrentTenantId("   ");

            // WHEN / THEN — any repository operation throws SecurityException before hitting the DB
            assertThat(runPromiseThrowing(() -> repository.findAll()))
                    .isInstanceOfAny(SecurityException.class, RuntimeException.class)
                    .satisfiesAnyOf(
                            ex -> assertThat(ex).isInstanceOf(SecurityException.class),
                            ex -> assertThat(ex.getCause()).isInstanceOf(SecurityException.class));
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
        void savePropagatesTenantId() {
            // GIVEN
            UUID entityId = UUID.randomUUID();
            TenantContext.setCurrentTenantId(TENANT_ALPHA);
            when(entityRepository.save(anyString(), any(Entity.class)))
                    .thenReturn(Promise.of(makeEntity(TENANT_ALPHA, entityId)));

            // WHEN
            runPromise(() -> repository.save(new TestEntity(entityId, "name", 0)));

            // THEN — EntityRepository.save received exactly TENANT_ALPHA
            ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);
            verify(entityRepository).save(tenantCaptor.capture(), any(Entity.class));
            assertThat(tenantCaptor.getValue()).isEqualTo(TENANT_ALPHA);
        }

        @Test
        @DisplayName("findById propagates TenantContext tenant ID to EntityRepository")
        void findByIdPropagatesTenantId() {
            // GIVEN
            TenantContext.setCurrentTenantId(TENANT_ALPHA);
            when(entityRepository.findById(anyString(), anyString(), any(UUID.class)))
                    .thenReturn(Promise.of(Optional.empty()));

            // WHEN
            runPromise(() -> repository.findById(UUID.randomUUID()));

            // THEN
            ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);
            verify(entityRepository).findById(tenantCaptor.capture(), anyString(), any(UUID.class));
            assertThat(tenantCaptor.getValue()).isEqualTo(TENANT_ALPHA);
        }

        @Test
        @DisplayName("deleteById propagates TenantContext tenant ID to EntityRepository")
        void deleteByIdPropagatesTenantId() {
            // GIVEN
            UUID entityId = UUID.randomUUID();
            TenantContext.setCurrentTenantId(TENANT_ALPHA);
            when(entityRepository.delete(anyString(), anyString(), any(UUID.class)))
                    .thenReturn(Promise.of(null));

            // WHEN
            runPromise(() -> repository.deleteById(entityId));

            // THEN
            ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);
            verify(entityRepository).delete(tenantCaptor.capture(), anyString(), any(UUID.class));
            assertThat(tenantCaptor.getValue()).isEqualTo(TENANT_ALPHA);
        }

        @Test
        @DisplayName("when no tenant is explicitly set, 'default-tenant' fallback is used")
        void noExplicitTenantUsesDefaultFallback() {
            // GIVEN — TenantContext cleared; getCurrentTenantId() returns "default-tenant"
            TenantContext.clear();
            when(entityRepository.findAll(anyString(), anyString(), any(), any(), anyInt(), anyInt()))
                    .thenReturn(Promise.of(List.of()));

            // WHEN
            runPromise(() -> repository.findAll());

            // THEN — the fallback sentinel is forwarded to EntityRepository
            ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);
            verify(entityRepository).findAll(
                    tenantCaptor.capture(), anyString(), any(), any(), anyInt(), anyInt());
            assertThat(tenantCaptor.getValue())
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
    private Throwable runPromiseThrowing(java.util.concurrent.Callable<io.activej.promise.Promise<?>> callable) {
        try {
            runPromise(callable);
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    private Entity makeEntity(String tenantId, UUID id) {
        return Entity.builder()
                .tenantId(tenantId)
                .collectionName("test_collection")
                .data(Map.of("id", id.toString(), "name", "item", "value", 1))
                .build();
    }

    record TestEntity(UUID id, String name, int value) implements Identifiable<UUID> {
        @Override
        public UUID getId() {
            return id;
        }
    }
}
