package com.ghatana.digitalmarketing.application.agency;

import com.ghatana.digitalmarketing.domain.agency.AgencyDeliverable;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for AgencyDeliverable persistence.
 *
 * @doc.type interface
 * @doc.purpose Persistence operations for agency deliverables (P3-002)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface AgencyDeliverableRepository {

    /**
     * Saves an agency deliverable.
     *
     * @param deliverable the deliverable to save
     * @return the saved deliverable
     */
    Promise<AgencyDeliverable> save(AgencyDeliverable deliverable);

    /**
     * Finds a deliverable by ID.
     *
     * @param id the deliverable ID
     * @return the deliverable if found
     */
    Promise<Optional<AgencyDeliverable>> findById(String id);

    /**
     * Finds deliverables by contract ID.
     *
     * @param contractId the contract ID
     * @return list of deliverables for the contract
     */
    Promise<List<AgencyDeliverable>> findByContractId(String contractId);

    /**
     * Finds deliverables by assigned user.
     *
     * @param assignedTo the assigned user ID
     * @return list of deliverables assigned to the user
     */
    Promise<List<AgencyDeliverable>> findByAssignedTo(String assignedTo);

    /**
     * Finds deliverables by client ID.
     *
     * @param clientId the client ID
     * @return list of deliverables for the client
     */
    Promise<List<AgencyDeliverable>> findByClientId(String clientId);

    /**
     * Finds overdue deliverables for a client.
     *
     * @param clientId the client ID
     * @return list of overdue deliverables
     */
    Promise<List<AgencyDeliverable>> findOverdueByClientId(String clientId);

    /**
     * Finds deliverables by agency tenant ID.
     *
     * @param agencyTenantId the agency tenant ID
     * @return list of deliverables for the agency
     */
    Promise<List<AgencyDeliverable>> findByAgencyTenantId(String agencyTenantId);

    /**
     * Lists all deliverables for a tenant.
     *
     * @param tenantId the tenant ID
     * @return list of deliverables
     */
    Promise<List<AgencyDeliverable>> listByTenant(String tenantId);

    /**
     * Deletes a deliverable.
     *
     * @param id the deliverable ID
     * @return void
     */
    Promise<Void> delete(String id);
}
