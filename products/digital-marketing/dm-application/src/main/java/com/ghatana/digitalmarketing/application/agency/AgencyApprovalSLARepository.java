package com.ghatana.digitalmarketing.application.agency;

import com.ghatana.digitalmarketing.domain.agency.AgencyApprovalSLA;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for AgencyApprovalSLA persistence.
 *
 * @doc.type interface
 * @doc.purpose Persistence operations for agency approval SLAs (P3-002)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface AgencyApprovalSLARepository {

    /**
     * Saves an approval SLA.
     *
     * @param sla the SLA to save
     * @return the saved SLA
     */
    Promise<AgencyApprovalSLA> save(AgencyApprovalSLA sla);

    /**
     * Finds an SLA by ID.
     *
     * @param id the SLA ID
     * @return the SLA if found
     */
    Promise<Optional<AgencyApprovalSLA>> findById(String id);

    /**
     * Finds SLAs by contract ID.
     *
     * @param contractId the contract ID
     * @return list of SLAs for the contract
     */
    Promise<List<AgencyApprovalSLA>> findByContractId(String contractId);

    /**
     * Finds SLAs by approval type.
     *
     * @param approvalType the approval type
     * @return list of SLAs for the approval type
     */
    Promise<List<AgencyApprovalSLA>> findByApprovalType(String approvalType);

    /**
     * Finds SLAs by client ID.
     *
     * @param clientId the client ID
     * @return list of SLAs for the client
     */
    Promise<List<AgencyApprovalSLA>> findByClientId(String clientId);

    /**
     * Finds SLAs by agency tenant ID.
     *
     * @param agencyTenantId the agency tenant ID
     * @return list of SLAs for the agency
     */
    Promise<List<AgencyApprovalSLA>> findByAgencyTenantId(String agencyTenantId);

    /**
     * Lists all SLAs for a tenant.
     *
     * @param tenantId the tenant ID
     * @return list of SLAs
     */
    Promise<List<AgencyApprovalSLA>> listByTenant(String tenantId);

    /**
     * Deletes an SLA.
     *
     * @param id the SLA ID
     * @return void
     */
    Promise<Void> delete(String id);
}
