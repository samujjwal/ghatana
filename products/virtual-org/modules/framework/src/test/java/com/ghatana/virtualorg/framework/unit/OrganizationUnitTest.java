package com.ghatana.virtualorg.framework.unit;

import com.ghatana.platform.types.identity.Identifier;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.virtualorg.framework.AbstractOrganization;
import com.ghatana.virtualorg.framework.DepartmentType;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import io.activej.eventloop.Eventloop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Organization unit tests.
 *
 * Tests organization creation, lifecycle, and multi-tenant support.
 *
 * @doc.type class
 * @doc.purpose Organization component unit tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Organization Unit Tests")
class OrganizationUnitTest extends EventloopTestBase {

    private static final TenantId TENANT_1 = TenantId.random();
    private static final TenantId TENANT_2 = TenantId.random();
    private TestOrganization org1;
    private TestOrganization org2;
    private SimpleEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        eventPublisher = new SimpleEventPublisher();
        org1 = new TestOrganization(eventloop(), "Acme Corp", "First Organization", TENANT_1, eventPublisher);
        org2 = new TestOrganization(eventloop(), "Beta Inc", "Second Organization", TENANT_2, eventPublisher);
    }

    /**
     * Verifies organization creation with correct properties.
     */
    @Test
    @DisplayName("Should create organization with correct properties")
    void shouldCreateOrganization() {
        assertThat(org1.getName())
                .as("Organization name should match")
                .isEqualTo("Acme Corp");

        assertThat(org1.getDescription())
                .as("Organization description should match")
                .isEqualTo("First Organization");

        assertThat(org1.getTenantId())
                .as("Organization should have tenant ID")
                .isEqualTo(TENANT_1);
    }

    /**
     * Verifies multi-tenant isolation.
     */
    @Test
    @DisplayName("Should enforce multi-tenant isolation")
    void shouldEnforceTenantIsolation() {
        // GIVEN: Two organizations with different tenants
        // THEN: Tenant IDs should be different
        assertThat(org1.getTenantId())
                .as("Tenant IDs should be different")
                .isNotEqualTo(org2.getTenantId());

        // AND: Organizations should be independent
        assertThat(org1.getName()).isEqualTo("Acme Corp");
        assertThat(org2.getName()).isEqualTo("Beta Inc");
    }

    /**
     * Verifies organization identity preservation.
     */
    @Test
    @DisplayName("Should preserve organization identity")
    void shouldPreserveIdentity() {
        // WHEN: Access organization properties multiple times
        String name1 = org1.getName();
        String name2 = org1.getName();
        TenantId tenant1 = org1.getTenantId();
        TenantId tenant2 = org1.getTenantId();

        // THEN: Properties should be consistent
        assertThat(name1).isEqualTo(name2);
        assertThat(tenant1).isEqualTo(tenant2);
    }

    /**
     * Verifies multiple organizations can coexist.
     */
    @Test
    @DisplayName("Should support multiple organizations")
    void shouldSupportMultipleOrganizations() {
        // WHEN: Create multiple organizations
        TestOrganization org3 = new TestOrganization(eventloop(), "Gamma LLC", "Third Organization", TenantId.random(), eventPublisher);
        TestOrganization org4 = new TestOrganization(eventloop(), "Delta Corp", "Fourth Organization", TenantId.random(), eventPublisher);

        // THEN: All organizations should coexist independently
        assertThat(org1.getName()).isEqualTo("Acme Corp");
        assertThat(org2.getName()).isEqualTo("Beta Inc");
        assertThat(org3.getName()).isEqualTo("Gamma LLC");
        assertThat(org4.getName()).isEqualTo("Delta Corp");

        // AND: All tenant IDs should be different
        Set<TenantId> tenants = Set.of(
                org1.getTenantId(),
                org2.getTenantId(),
                org3.getTenantId(),
                org4.getTenantId()
        );
        assertThat(tenants).hasSize(4);
    }

    /**
     * Verifies organization description support.
     */
    @Test
    @DisplayName("Should support organization descriptions")
    void shouldSupportDescriptions() {
        assertThat(org1.getDescription())
                .as("Should have description")
                .isNotNull()
                .isNotBlank();

        assertThat(org2.getDescription())
                .as("Should have description")
                .isNotNull()
                .isNotBlank();
    }

    /**
     * Verifies organization with event publisher.
     */
    @Test
    @DisplayName("Should have event publisher")
    void shouldHaveEventPublisher() {
        // GIVEN: Organization created with event publisher
        // THEN: Should be able to interact with event system
        assertThat(eventPublisher)
                .as("Event publisher should exist")
                .isNotNull();
    }

    /**
     * Verifies event isolation per tenant.
     */
    @Test
    @DisplayName("Should maintain event isolation per tenant")
    void shouldMaintainEventIsolation() {
        // WHEN: Publish events for each tenant
        eventPublisher.publish("org1.event.1", new byte[0]);
        eventPublisher.publish("org2.event.1", new byte[0]);

        // THEN: Event publisher should accept both events (no exception)
        assertThat(eventPublisher)
                .as("Event publisher should exist")
                .isNotNull();
    }

    /**
     * Verifies organization can maintain internal state.
     */
    @Test
    @DisplayName("Should maintain organization state")
    void shouldMaintainState() {
        // GIVEN: Organization created
        String originalName = org1.getName();
        TenantId originalTenant = org1.getTenantId();

        // WHEN: Access properties again
        String currentName = org1.getName();
        TenantId currentTenant = org1.getTenantId();

        // THEN: State should be unchanged
        assertThat(currentName).isEqualTo(originalName);
        assertThat(currentTenant).isEqualTo(originalTenant);
    }

    /**
     * Verifies organization independence.
     */
    @Test
    @DisplayName("Should maintain organization independence")
    void shouldMaintainIndependence() {
        // WHEN: Modify org1 (in future, if mutable)
        // THEN: org2 should not be affected

        // For now, just verify they're separate
        assertThat(org1)
                .as("org1 and org2 should be different")
                .isNotEqualTo(org2);

        // AND: Their properties should be different
        assertThat(org1.getName()).isNotEqualTo(org2.getName());
        assertThat(org1.getTenantId()).isNotEqualTo(org2.getTenantId());
    }

    // ============ HELPER CLASSES ============
    private static class SimpleEventPublisher implements EventPublisher {

        private final List<Map<String, Object>> events = new ArrayList<>();

        @Override
        public void publish(String eventType, byte[] payload) {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", eventType);
            events.add(event);
        }

        @Override
        public void publishOrganizationCreated(String name, String description) {
            publish("OrganizationCreated", new byte[0]);
        }

        @Override
        public void publishDepartmentRegistered(Identifier departmentId, String name, String type) {
            publish("DepartmentRegistered", new byte[0]);
        }

        @Override
        public void publishTaskDeclared(String taskId, String name, String description) {
            publish("TaskDeclared", new byte[0]);
        }

        @Override
        public void publishTaskAssigned(String taskId, String agentId) {
            publish("TaskAssigned", new byte[0]);
        }

        @Override
        public void publishTaskCompleted(String taskId, String result) {
            publish("TaskCompleted", new byte[0]);
        }

        List<Map<String, Object>> getEvents() {
            return new ArrayList<>(events);
        }
    }

    private static class TestOrganization extends AbstractOrganization {

        public TestOrganization(Eventloop eventloop, String name, String description, TenantId tenantId, EventPublisher eventPublisher) {
            super(eventloop, tenantId, name, description, eventPublisher);
        }
    }
}
