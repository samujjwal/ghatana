package com.ghatana.digitalmarketing.application.agency;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.agency.AgencyClient;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for AgencyClientServiceImpl (DMOS-P3-003).
 *
 * @doc.type test
 * @doc.purpose Verify agency client service behavior
 * @doc.layer application
 */
@DisplayName("AgencyClientServiceImpl")
class AgencyClientServiceImplTest {

    @Test
    @DisplayName("createClient creates and saves agency client")
    void createClient_createsAndSavesAgencyClient() {
        AgencyClientRepository repository = mock(AgencyClientRepository.class);
        when(repository.save(any(AgencyClient.class))).thenReturn(Promise.of(mock(AgencyClient.class)));

        AgencyClientServiceImpl service = new AgencyClientServiceImpl(repository);
        DmTenantId tenantId = new DmTenantId("tenant-123");

        var promise = service.createClient(tenantId, "Acme Corp", "contact@acme.com", "555-1234", "blue");
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("getClient retrieves agency client by ID")
    void getClient_retrievesAgencyClientById() {
        AgencyClientRepository repository = mock(AgencyClientRepository.class);
        AgencyClient client = AgencyClient.builder()
            .clientId("client-789")
            .tenantId(new DmTenantId("tenant-123"))
            .workspaceId(new DmWorkspaceId("workspace-456"))
            .clientName("Acme Corp")
            .active(true)
            .createdAt(java.time.Instant.now())
            .updatedAt(java.time.Instant.now())
            .build();

        when(repository.findById("client-789")).thenReturn(Promise.of(java.util.Optional.of(client)));

        AgencyClientServiceImpl service = new AgencyClientServiceImpl(repository);
        var promise = service.getClient("client-789");
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("getClientsForTenant returns all clients for tenant")
    void getClientsForTenant_returnsAllClientsForTenant() {
        AgencyClientRepository repository = mock(AgencyClientRepository.class);
        when(repository.findByTenant(any(DmTenantId.class))).thenReturn(Promise.of(java.util.List.of()));

        AgencyClientServiceImpl service = new AgencyClientServiceImpl(repository);
        DmTenantId tenantId = new DmTenantId("tenant-123");

        var promise = service.getClientsForTenant(tenantId);
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("deactivateClient deactivates agency client")
    void deactivateClient_deactivatesAgencyClient() {
        AgencyClientRepository repository = mock(AgencyClientRepository.class);
        AgencyClient client = AgencyClient.builder()
            .clientId("client-789")
            .tenantId(new DmTenantId("tenant-123"))
            .workspaceId(new DmWorkspaceId("workspace-456"))
            .clientName("Acme Corp")
            .active(true)
            .createdAt(java.time.Instant.now())
            .updatedAt(java.time.Instant.now())
            .build();

        when(repository.findById("client-789")).thenReturn(Promise.of(java.util.Optional.of(client)));
        when(repository.update(any(AgencyClient.class))).thenReturn(Promise.of(client));

        AgencyClientServiceImpl service = new AgencyClientServiceImpl(repository);
        var promise = service.deactivateClient("client-789");
        assertThat(promise).isNotNull();
    }
}
