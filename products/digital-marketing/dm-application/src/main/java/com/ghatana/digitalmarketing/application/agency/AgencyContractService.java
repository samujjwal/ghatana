package com.ghatana.digitalmarketing.application.agency;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.agency.AgencyContract;
import io.activej.promise.Promise;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Application service for agency contract management.
 *
 * @doc.type interface
 * @doc.purpose Provides agency contract CRUD and lifecycle management (P3-002)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface AgencyContractService {

    /**
     * Creates a new agency contract.
     *
     * @param ctx the operation context
     * @param command the creation command
     * @return the created contract
     */
    Promise<AgencyContract> create(DmOperationContext ctx, CreateContractCommand command);

    /**
     * Activates a contract.
     *
     * @param ctx the operation context
     * @param contractId the contract ID
     * @return the activated contract
     */
    Promise<AgencyContract> activate(DmOperationContext ctx, String contractId);

    /**
     * Terminates a contract.
     *
     * @param ctx the operation context
     * @param contractId the contract ID
     * @param reason the termination reason
     * @return the terminated contract
     */
    Promise<AgencyContract> terminate(DmOperationContext ctx, String contractId, String reason);

    /**
     * Renews a contract with a new end date.
     *
     * @param ctx the operation context
     * @param contractId the contract ID
     * @param newEndDate the new end date
     * @return the renewed contract
     */
    Promise<AgencyContract> renew(DmOperationContext ctx, String contractId, LocalDate newEndDate);

    /**
     * Finds a contract by ID.
     *
     * @param ctx the operation context
     * @param contractId the contract ID
     * @return the contract if found
     */
    Promise<Optional<AgencyContract>> findById(DmOperationContext ctx, String contractId);

    /**
     * Finds contracts by client ID.
     *
     * @param ctx the operation context
     * @param clientId the client ID
     * @return list of contracts for the client
     */
    Promise<java.util.List<AgencyContract>> findByClientId(DmOperationContext ctx, String clientId);

    /**
     * Lists contracts for an agency tenant.
     *
     * @param ctx the operation context
     * @return list of contracts
     */
    Promise<java.util.List<AgencyContract>> list(DmOperationContext ctx);

    /**
     * Command to create an agency contract.
     */
    record CreateContractCommand(
        String clientId,
        String contractNumber,
        String contractType,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal monthlyRetainer,
        String currency,
        String terms
    ) {
        public CreateContractCommand {
            if (clientId == null || clientId.isBlank()) {
                throw new IllegalArgumentException("clientId must not be blank");
            }
            if (contractNumber == null || contractNumber.isBlank()) {
                throw new IllegalArgumentException("contractNumber must not be blank");
            }
            if (contractType == null || contractType.isBlank()) {
                throw new IllegalArgumentException("contractType must not be blank");
            }
            if (startDate == null) {
                throw new IllegalArgumentException("startDate must not be null");
            }
            if (monthlyRetainer == null || monthlyRetainer.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("monthlyRetainer must be non-negative");
            }
        }
    }
}
