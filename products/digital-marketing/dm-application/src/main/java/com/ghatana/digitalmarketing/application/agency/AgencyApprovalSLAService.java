package com.ghatana.digitalmarketing.application.agency;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.agency.AgencyApprovalSLA;
import io.activej.promise.Promise;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Application service for agency approval SLA management.
 *
 * @doc.type interface
 * @doc.purpose Provides agency approval SLA CRUD and lifecycle management (P3-002)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface AgencyApprovalSLAService {

    /**
     * Creates a new approval SLA.
     *
     * @param ctx the operation context
     * @param command the creation command
     * @return the created SLA
     */
    Promise<AgencyApprovalSLA> create(DmOperationContext ctx, CreateSLACommand command);

    /**
     * Activates an SLA.
     *
     * @param ctx the operation context
     * @param slaId the SLA ID
     * @return the activated SLA
     */
    Promise<AgencyApprovalSLA> activate(DmOperationContext ctx, String slaId);

    /**
     * Deactivates an SLA.
     *
     * @param ctx the operation context
     * @param slaId the SLA ID
     * @param reason the deactivation reason
     * @return the deactivated SLA
     */
    Promise<AgencyApprovalSLA> deactivate(DmOperationContext ctx, String slaId, String reason);

    /**
     * Updates the escalation level of an SLA.
     *
     * @param ctx the operation context
     * @param slaId the SLA ID
     * @param newLevel the new escalation level
     * @return the updated SLA
     */
    Promise<AgencyApprovalSLA> updateEscalationLevel(DmOperationContext ctx, String slaId, int newLevel);

    /**
     * Finds an SLA by ID.
     *
     * @param ctx the operation context
     * @param slaId the SLA ID
     * @return the SLA if found
     */
    Promise<Optional<AgencyApprovalSLA>> findById(DmOperationContext ctx, String slaId);

    /**
     * Finds SLAs by contract ID.
     *
     * @param ctx the operation context
     * @param contractId the contract ID
     * @return list of SLAs for the contract
     */
    Promise<java.util.List<AgencyApprovalSLA>> findByContractId(DmOperationContext ctx, String contractId);

    /**
     * Finds SLAs by approval type.
     *
     * @param ctx the operation context
     * @param approvalType the approval type
     * @return list of SLAs for the approval type
     */
    Promise<java.util.List<AgencyApprovalSLA>> findByApprovalType(DmOperationContext ctx, String approvalType);

    /**
     * Lists SLAs for an agency tenant.
     *
     * @param ctx the operation context
     * @return list of SLAs
     */
    Promise<java.util.List<AgencyApprovalSLA>> list(DmOperationContext ctx);

    /**
     * Command to create an approval SLA.
     */
    record CreateSLACommand(
        String contractId,
        String approvalType,
        Duration maxApprovalTime,
        Map<String, Duration> escalationTimeouts,
        String escalationProcedure
    ) {
        public CreateSLACommand {
            if (contractId == null || contractId.isBlank()) {
                throw new IllegalArgumentException("contractId must not be blank");
            }
            if (approvalType == null || approvalType.isBlank()) {
                throw new IllegalArgumentException("approvalType must not be blank");
            }
            if (maxApprovalTime == null || maxApprovalTime.isNegative() || maxApprovalTime.isZero()) {
                throw new IllegalArgumentException("maxApprovalTime must be positive");
            }
        }
    }
}
