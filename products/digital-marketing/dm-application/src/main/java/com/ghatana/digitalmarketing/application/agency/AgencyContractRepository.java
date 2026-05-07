package com.ghatana.digitalmarketing.application.agency;

import com.ghatana.digitalmarketing.domain.agency.AgencyContract;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for AgencyContract persistence.
 *
 * @doc.type interface
 * @doc.purpose Persistence operations for agency contracts (P3-002)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface AgencyContractRepository {

    /**
     * Saves an agency contract.
     *
     * @param contract the contract to save
     * @return the saved contract
     */
    Promise<AgencyContract> save(AgencyContract contract);

    /**
     * Finds a contract by ID.
     *
     * @param id the contract ID
     * @return the contract if found
     */
    Promise<Optional<AgencyContract>> findById(String id);

    /**
     * Finds contracts by client ID.
     *
     * @param clientId the client ID
     * @return list of contracts for the client
     */
    Promise<List<AgencyContract>> findByClientId(String clientId);

    /**
     * Finds contracts by agency tenant ID.
     *
     * @param agencyTenantId the agency tenant ID
     * @return list of contracts for the agency
     */
    Promise<List<AgencyContract>> findByAgencyTenantId(String agencyTenantId);

    /**
     * Lists all contracts for a tenant.
     *
     * @param tenantId the tenant ID
     * @return list of contracts
     */
    Promise<List<AgencyContract>> listByTenant(String tenantId);

    /**
     * Deletes a contract.
     *
     * @param id the contract ID
     * @return void
     */
    Promise<Void> delete(String id);
}
