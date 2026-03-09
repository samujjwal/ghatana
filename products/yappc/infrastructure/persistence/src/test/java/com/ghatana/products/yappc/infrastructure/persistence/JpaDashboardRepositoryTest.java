package com.ghatana.products.yappc.domain.repository;

import com.ghatana.products.yappc.domain.model.Dashboard;
import com.ghatana.products.yappc.infrastructure.persistence.JpaDashboardRepository;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for JpaDashboardRepository.
 *
 * Tests validate:
 * - Dashboard CRUD operations
 * - Persona-based filtering
 * - Slug uniqueness enforcement
 * - Default dashboard management
 * - Tenant isolation
 * - Workspace scoping
 *
 * @see JpaDashboardRepository
 */
@DisplayName("JPA Dashboard Repository Tests")
/**
 * @doc.type class
 * @doc.purpose Handles jpa dashboard repository test operations
 * @doc.layer platform
 * @doc.pattern Test
 */
class JpaDashboardRepositoryTest extends EventloopTestBase {

    private JpaDashboardRepository repository;
    private EntityManager entityManager;
    private MetricsCollector metricsCollector;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        metricsCollector = NoopMetricsCollector.getInstance();
        repository = new JpaDashboardRepository(entityManager, metricsCollector);
    }

    /**
     * Verifies saving a new dashboard.
     *
     * GIVEN: New dashboard
     * WHEN: save() is called
     * THEN: Dashboard is persisted
     */
    @Test
    @DisplayName("Should persist new dashboard")
    void shouldPersistNewDashboard() {
        // GIVEN: New dashboard
        Dashboard dashboard = new Dashboard();
        dashboard.setTenantId("tenant-123");
        dashboard.setWorkspaceId(UUID.randomUUID());
        dashboard.setName("Security Dashboard");
        dashboard.setPersona("CISO");
        dashboard.setSlug("security-dashboard");
        dashboard.setDefault(false);

        when(entityManager.merge(any(Dashboard.class)))
            .thenReturn(dashboard);

        // WHEN: Save dashboard
        Dashboard saved = runPromise(() -> repository.save(dashboard));

        // THEN: Dashboard is persisted
        assertThat(saved).isNotNull();
        assertThat(saved.getName()).isEqualTo("Security Dashboard");
        verify(entityManager).merge(any(Dashboard.class));
    }

    /**
     * Verifies finding dashboards by persona.
     *
     * GIVEN: Dashboards for different personas
     * WHEN: findByPersona() is called
     * THEN: Only dashboards for that persona are returned
     */
    @Test
    @DisplayName("Should find dashboards by persona")
    void shouldFindDashboardsByPersona() {
        // GIVEN: CISO dashboards
        UUID workspaceId = UUID.randomUUID();
        
        Dashboard dashboard1 = new Dashboard();
        dashboard1.setPersona("CISO");
        dashboard1.setName("Security Overview");

        Dashboard dashboard2 = new Dashboard();
        dashboard2.setPersona("CISO");
        dashboard2.setName("Compliance Status");

        TypedQuery<Dashboard> query = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(Dashboard.class)))
            .thenReturn(query);
        when(query.setParameter(anyString(), any()))
            .thenReturn(query);
        when(query.getResultList())
            .thenReturn(List.of(dashboard1, dashboard2));

        // WHEN: Find CISO dashboards
        List<Dashboard> results = runPromise(() -> 
            repository.findByPersona("tenant-123", workspaceId, "CISO"));

        // THEN: Both dashboards returned
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(d -> d.getPersona().equals("CISO"));
    }

    /**
     * Verifies slug uniqueness check.
     *
     * GIVEN: Dashboard with slug "security-dashboard"
     * WHEN: findBySlug() is called
     * THEN: Dashboard is found
     */
    @Test
    @DisplayName("Should find dashboard by slug")
    void shouldFindDashboardBySlug() {
        // GIVEN: Dashboard with slug
        UUID workspaceId = UUID.randomUUID();
        
        Dashboard dashboard = new Dashboard();
        dashboard.setTenantId("tenant-123");
        dashboard.setWorkspaceId(workspaceId);
        dashboard.setSlug("security-dashboard");

        TypedQuery<Dashboard> query = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(Dashboard.class)))
            .thenReturn(query);
        when(query.setParameter(anyString(), any()))
            .thenReturn(query);
        when(query.getResultStream())
            .thenReturn(List.of(dashboard).stream());

        // WHEN: Find by slug
        Optional<Dashboard> result = runPromise(() -> 
            repository.findBySlug("tenant-123", workspaceId, "security-dashboard"));

        // THEN: Dashboard is found
        assertThat(result).isPresent();
        assertThat(result.get().getSlug()).isEqualTo("security-dashboard");
    }

    /**
     * Verifies setting dashboard as default.
     *
     * GIVEN: Dashboard to set as default
     * WHEN: setAsDefault() is called
     * THEN: Only this dashboard is default
     */
    @Test
    @DisplayName("Should set dashboard as default")
    void shouldSetDashboardAsDefault() {
        // GIVEN: Dashboard ID
        UUID dashboardId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        Dashboard dashboard = new Dashboard();
        dashboard.setId(dashboardId);
        dashboard.setDefault(true);

        when(entityManager.find(Dashboard.class, dashboardId))
            .thenReturn(dashboard);
        when(entityManager.createQuery(anyString()))
            .thenReturn(mock(javax.persistence.Query.class));

        // WHEN: Set as default
        runPromise(() -> repository.setAsDefault("tenant-123", workspaceId, dashboardId));

        // THEN: Default flag updated
        verify(entityManager).createQuery(anyString()); // Clear other defaults
        verify(entityManager).find(Dashboard.class, dashboardId);
    }

    /**
     * Verifies tenant isolation.
     *
     * GIVEN: Dashboards for different tenants
     * WHEN: findByTenant() is called
     * THEN: Only dashboards for that tenant are returned
     */
    @Test
    @DisplayName("Should enforce tenant isolation")
    void shouldEnforceTenantIsolation() {
        // GIVEN: Tenant-specific dashboards
        Dashboard tenant1Dashboard = new Dashboard();
        tenant1Dashboard.setTenantId("tenant-1");

        TypedQuery<Dashboard> query = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(Dashboard.class)))
            .thenReturn(query);
        when(query.setParameter(eq("tenantId"), eq("tenant-1")))
            .thenReturn(query);
        when(query.getResultList())
            .thenReturn(List.of(tenant1Dashboard));

        // WHEN: Find tenant-1 dashboards
        List<Dashboard> results = runPromise(() -> 
            repository.findByTenant("tenant-1"));

        // THEN: Only tenant-1 dashboards returned
        assertThat(results).allMatch(d -> d.getTenantId().equals("tenant-1"));
    }

    /**
     * Verifies deleting a dashboard.
     *
     * GIVEN: Existing dashboard
     * WHEN: delete() is called
     * THEN: Dashboard is removed
     */
    @Test
    @DisplayName("Should delete dashboard")
    void shouldDeleteDashboard() {
        // GIVEN: Dashboard to delete
        UUID dashboardId = UUID.randomUUID();
        
        Dashboard dashboard = new Dashboard();
        dashboard.setId(dashboardId);

        when(entityManager.find(Dashboard.class, dashboardId))
            .thenReturn(dashboard);

        // WHEN: Delete dashboard
        runPromise(() -> repository.delete("tenant-123", dashboardId));

        // THEN: Dashboard is removed
        verify(entityManager).remove(dashboard);
    }
}
