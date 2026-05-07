package com.ghatana.digitalmarketing.application.agency;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.agency.AgencyRetainer;
import io.activej.promise.Promise;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * Application service for agency retainer management.
 *
 * @doc.type interface
 * @doc.purpose Provides agency retainer CRUD and lifecycle management (P3-002)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface AgencyRetainerService {

    /**
     * Creates a new agency retainer.
     *
     * @param ctx the operation context
     * @param command the creation command
     * @return the created retainer
     */
    Promise<AgencyRetainer> create(DmOperationContext ctx, CreateRetainerCommand command);

    /**
     * Activates a retainer.
     *
     * @param ctx the operation context
     * @param retainerId the retainer ID
     * @return the activated retainer
     */
    Promise<AgencyRetainer> activate(DmOperationContext ctx, String retainerId);

    /**
     * Suspends a retainer.
     *
     * @param ctx the operation context
     * @param retainerId the retainer ID
     * @param reason the suspension reason
     * @return the suspended retainer
     */
    Promise<AgencyRetainer> suspend(DmOperationContext ctx, String retainerId, String reason);

    /**
     * Cancels a retainer.
     *
     * @param ctx the operation context
     * @param retainerId the retainer ID
     * @param reason the cancellation reason
     * @return the cancelled retainer
     */
    Promise<AgencyRetainer> cancel(DmOperationContext ctx, String retainerId, String reason);

    /**
     * Finds a retainer by ID.
     *
     * @param ctx the operation context
     * @param retainerId the retainer ID
     * @return the retainer if found
     */
    Promise<Optional<AgencyRetainer>> findById(DmOperationContext ctx, String retainerId);

    /**
     * Finds retainers by contract ID.
     *
     * @param ctx the operation context
     * @param contractId the contract ID
     * @return list of retainers for the contract
     */
    Promise<java.util.List<AgencyRetainer>> findByContractId(DmOperationContext ctx, String contractId);

    /**
     * Finds retainers by client ID.
     *
     * @param ctx the operation context
     * @param clientId the client ID
     * @return list of retainers for the client
     */
    Promise<java.util.List<AgencyRetainer>> findByClientId(DmOperationContext ctx, String clientId);

    /**
     * Lists retainers for an agency tenant.
     *
     * @param ctx the operation context
     * @return list of retainers
     */
    Promise<java.util.List<AgencyRetainer>> list(DmOperationContext ctx);

    /**
     * Command to create an agency retainer.
     */
    record CreateRetainerCommand(
        String contractId,
        BigDecimal monthlyAmount,
        String currency,
        LocalDate billingCycleStart,
        int billingDayOfMonth,
        Map<String, Integer> serviceAllowances,
        BigDecimal overageRate
    ) {
        public CreateRetainerCommand {
            if (contractId == null || contractId.isBlank()) {
                throw new IllegalArgumentException("contractId must not be blank");
            }
            if (monthlyAmount == null || monthlyAmount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("monthlyAmount must be non-negative");
            }
            if (billingDayOfMonth < 1 || billingDayOfMonth > 31) {
                throw new IllegalArgumentException("billingDayOfMonth must be between 1 and 31");
            }
        }
    }
}
