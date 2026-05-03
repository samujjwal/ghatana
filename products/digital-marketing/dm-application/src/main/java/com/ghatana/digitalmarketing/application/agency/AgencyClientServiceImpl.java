package com.ghatana.digitalmarketing.application.agency;

import com.ghatana.digitalmarketing.domain.agency.AgencyClient;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

/**
 * Service implementation for managing agency clients (DMOS-P3-003).
 *
 * @doc.type class
 * @doc.purpose Service implementation for agency client operations
 * @doc.layer application
 * @doc.pattern Service
 */
public final class AgencyClientServiceImpl implements AgencyClientService {

    private static final Logger logger = LoggerFactory.getLogger(AgencyClientServiceImpl.class);

    private final AgencyClientRepository repository;

    public AgencyClientServiceImpl(AgencyClientRepository repository) {
        this.repository = repository;
    }

    @Override
    public Promise<AgencyClient> createClient(DmTenantId tenantId, String clientName, String contactEmail, String contactPhone, String brandingTheme) {
        String clientId = UUID.randomUUID().toString();
        String workspaceId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        AgencyClient client = AgencyClient.builder()
            .clientId(clientId)
            .tenantId(tenantId)
            .workspaceId(DmWorkspaceId.of(workspaceId))
            .clientName(clientName)
            .contactEmail(contactEmail)
            .contactPhone(contactPhone)
            .brandingTheme(brandingTheme)
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .build();

        logger.info("Creating agency client: {} for tenant: {}", clientName, tenantId.getValue());
        return repository.save(client);
    }

    @Override
    public Promise<AgencyClient> getClient(String clientId) {
        return repository.findById(clientId)
            .then(clientOpt -> Promise.of(clientOpt.orElseThrow(() -> new IllegalArgumentException("Agency client not found: " + clientId))));
    }

    @Override
    public Promise<java.util.List<AgencyClient>> getClientsForTenant(DmTenantId tenantId) {
        logger.info("Fetching all agency clients for tenant: {}", tenantId.getValue());
        return repository.findByTenant(tenantId);
    }

    @Override
    public Promise<java.util.List<AgencyClient>> getActiveClientsForTenant(DmTenantId tenantId) {
        logger.info("Fetching active agency clients for tenant: {}", tenantId.getValue());
        return repository.findActiveByTenant(tenantId);
    }

    @Override
    public Promise<AgencyClient> updateClient(String clientId, String clientName, String contactEmail, String contactPhone, String brandingTheme, boolean active) {
        return getClient(clientId)
            .then(client -> {
                AgencyClient updated = AgencyClient.builder()
                    .clientId(client.getClientId())
                    .tenantId(client.getTenantId())
                    .workspaceId(client.getWorkspaceId())
                    .clientName(clientName)
                    .contactEmail(contactEmail)
                    .contactPhone(contactPhone)
                    .brandingTheme(brandingTheme)
                    .active(active)
                    .createdAt(client.getCreatedAt())
                    .updatedAt(Instant.now())
                    .build();

                logger.info("Updating agency client: {}", clientId);
                return repository.update(updated);
            });
    }

    @Override
    public Promise<Void> deactivateClient(String clientId) {
        return getClient(clientId)
            .then(client -> updateClient(clientId, client.getClientName(), client.getContactEmail(), client.getContactPhone(), client.getBrandingTheme(), false))
            .then(__ -> {
                logger.info("Deactivated agency client: {}", clientId);
                return Promise.of(null);
            });
    }

    @Override
    public Promise<AgencyClient> getClientByWorkspace(DmWorkspaceId workspaceId) {
        return repository.findByWorkspace(workspaceId)
            .then(clientOpt -> Promise.of(clientOpt.orElseThrow(() -> new IllegalArgumentException("Agency client not found for workspace: " + workspaceId.getValue()))));
    }
}
