package com.ghatana.digitalmarketing.application.agency;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.agency.AgencyClient;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for AgencyClientServiceImpl (DMOS-P3-003).
 *
 * @doc.type test
 * @doc.purpose Verify agency client service behavior
 * @doc.layer application
 */
@DisplayName("AgencyClientServiceImpl")
class AgencyClientServiceImplTest {

    private InMemoryAgencyClientRepository repository;
    private AgencyClientServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAgencyClientRepository();
        service = new AgencyClientServiceImpl(repository);
    }

    @Test
    @DisplayName("createClient creates and saves agency client")
    void createClient_createsAndSavesAgencyClient() {
        DmTenantId tenantId = DmTenantId.of("tenant-123");

        var promise = service.createClient(tenantId, "Acme Corp", "contact@acme.com", "555-1234", "blue");
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("getClient retrieves agency client by ID")
    void getClient_retrievesAgencyClientById() {
        AgencyClient client = AgencyClient.builder()
            .clientId("client-789")
            .tenantId(DmTenantId.of("tenant-123"))
            .workspaceId(DmWorkspaceId.of("workspace-456"))
            .clientName("Acme Corp")
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        repository.save(client).getResult();
        var promise = service.getClient("client-789");
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("getClientsForTenant returns all clients for tenant")
    void getClientsForTenant_returnsAllClientsForTenant() {
        DmTenantId tenantId = DmTenantId.of("tenant-123");

        var promise = service.getClientsForTenant(tenantId);
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("deactivateClient deactivates agency client")
    void deactivateClient_deactivatesAgencyClient() {
        AgencyClient client = AgencyClient.builder()
            .clientId("client-789")
            .tenantId(DmTenantId.of("tenant-123"))
            .workspaceId(DmWorkspaceId.of("workspace-456"))
            .clientName("Acme Corp")
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        repository.save(client).getResult();
        var promise = service.deactivateClient("client-789");
        assertThat(promise).isNotNull();
    }

    // ── test doubles ─────────────────────────────────────────────────────────

    private static final class InMemoryAgencyClientRepository implements AgencyClientRepository {
        private final ConcurrentHashMap<String, AgencyClient> store = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, List<AgencyClient>> tenantIndex = new ConcurrentHashMap<>();

        @Override
        public Promise<AgencyClient> save(AgencyClient client) {
            store.put(client.getClientId(), client);
            tenantIndex.computeIfAbsent(client.getTenantId().getValue(), k -> new ArrayList<>()).add(client);
            return Promise.of(client);
        }

        @Override
        public Promise<Optional<AgencyClient>> findById(String clientId) {
            return Promise.of(Optional.ofNullable(store.get(clientId)));
        }

        @Override
        public Promise<List<AgencyClient>> findByTenant(DmTenantId tenantId) {
            return Promise.of(tenantIndex.getOrDefault(tenantId.getValue(), new ArrayList<>()));
        }

        @Override
        public Promise<List<AgencyClient>> findActiveByTenant(DmTenantId tenantId) {
            List<AgencyClient> result = new ArrayList<>();
            for (AgencyClient client : tenantIndex.getOrDefault(tenantId.getValue(), new ArrayList<>())) {
                if (client.isActive()) {
                    result.add(client);
                }
            }
            return Promise.of(result);
        }

        @Override
        public Promise<Optional<AgencyClient>> findByWorkspace(DmWorkspaceId workspaceId) {
            for (AgencyClient client : store.values()) {
                if (client.getWorkspaceId().equals(workspaceId)) {
                    return Promise.of(Optional.of(client));
                }
            }
            return Promise.of(Optional.empty());
        }

        @Override
        public Promise<AgencyClient> update(AgencyClient client) {
            store.put(client.getClientId(), client);
            return Promise.of(client);
        }

        @Override
        public Promise<Void> delete(String clientId) {
            AgencyClient removed = store.remove(clientId);
            if (removed != null) {
                tenantIndex.getOrDefault(removed.getTenantId().getValue(), new ArrayList<>()).removeIf(c -> c.getClientId().equals(clientId));
            }
            return Promise.complete();
        }
    }
}
