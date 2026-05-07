package com.ghatana.digitalmarketing.application.agency;

import com.ghatana.digitalmarketing.domain.agency.AgencyRetainer;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for AgencyRetainer persistence.
 *
 * @doc.type interface
 * @doc.purpose Persistence operations for agency retainers (P3-002)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface AgencyRetainerRepository {

    /**
     * Saves an agency retainer.
     *
     * @param retainer the retainer to save
     * @return the saved retainer
     */
    Promise<AgencyRetainer> save(AgencyRetainer retainer);

    /**
     * Finds a retainer by ID.
     *
     * @param id the retainer ID
     * @return the retainer if found
     */
    Promise<Optional<AgencyRetainer>> findById(String id);

    /**
     * Finds retainers by contract ID.
     *
     * @param contractId the contract ID
     * @return list of retainers for the contract
     */
    Promise<List<AgencyRetainer>> findByContractId(String contractId);

    /**
     * Finds retainers by client ID.
     *
     * @param clientId the client ID
     * @return list of retainers for the client
     */
    Promise<List<AgencyRetainer>> findByClientId(String clientId);

    /**
     * Finds retainers by agency tenant ID.
     *
     * @param agencyTenantId the agency tenant ID
     * @return list of retainers for the agency
     */
    Promise<List<AgencyRetainer>> findByAgencyTenantId(String agencyTenantId);

    /**
     * Lists all retainers for a tenant.
     *
     * @param tenantId the tenant ID
     * @return list of retainers
     */
    Promise<List<AgencyRetainer>> listByTenant(String tenantId);

    /**
     * Deletes a retainer.
     *
     * @param id the retainer ID
     * @return void
     */
    Promise<Void> delete(String id);
}
