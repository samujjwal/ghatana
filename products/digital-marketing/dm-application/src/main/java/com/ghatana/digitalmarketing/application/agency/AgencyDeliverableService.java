package com.ghatana.digitalmarketing.application.agency;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.agency.AgencyDeliverable;
import io.activej.promise.Promise;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * Application service for agency deliverable management.
 *
 * @doc.type interface
 * @doc.purpose Provides agency deliverable CRUD and lifecycle management (P3-002)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface AgencyDeliverableService {

    /**
     * Creates a new agency deliverable.
     *
     * @param ctx the operation context
     * @param command the creation command
     * @return the created deliverable
     */
    Promise<AgencyDeliverable> create(DmOperationContext ctx, CreateDeliverableCommand command);

    /**
     * Starts a deliverable.
     *
     * @param ctx the operation context
     * @param deliverableId the deliverable ID
     * @return the started deliverable
     */
    Promise<AgencyDeliverable> start(DmOperationContext ctx, String deliverableId);

    /**
     * Submits a deliverable for review.
     *
     * @param ctx the operation context
     * @param deliverableId the deliverable ID
     * @return the submitted deliverable
     */
    Promise<AgencyDeliverable> submitForReview(DmOperationContext ctx, String deliverableId);

    /**
     * Completes a deliverable.
     *
     * @param ctx the operation context
     * @param deliverableId the deliverable ID
     * @return the completed deliverable
     */
    Promise<AgencyDeliverable> complete(DmOperationContext ctx, String deliverableId);

    /**
     * Rejects a deliverable.
     *
     * @param ctx the operation context
     * @param deliverableId the deliverable ID
     * @param reason the rejection reason
     * @return the rejected deliverable
     */
    Promise<AgencyDeliverable> reject(DmOperationContext ctx, String deliverableId, String reason);

    /**
     * Finds a deliverable by ID.
     *
     * @param ctx the operation context
     * @param deliverableId the deliverable ID
     * @return the deliverable if found
     */
    Promise<Optional<AgencyDeliverable>> findById(DmOperationContext ctx, String deliverableId);

    /**
     * Finds deliverables by contract ID.
     *
     * @param ctx the operation context
     * @param contractId the contract ID
     * @return list of deliverables for the contract
     */
    Promise<java.util.List<AgencyDeliverable>> findByContractId(DmOperationContext ctx, String contractId);

    /**
     * Finds deliverables by assigned user.
     *
     * @param ctx the operation context
     * @param assignedTo the assigned user ID
     * @return list of deliverables assigned to the user
     */
    Promise<java.util.List<AgencyDeliverable>> findByAssignedTo(DmOperationContext ctx, String assignedTo);

    /**
     * Finds overdue deliverables for a client.
     *
     * @param ctx the operation context
     * @param clientId the client ID
     * @return list of overdue deliverables
     */
    Promise<java.util.List<AgencyDeliverable>> findOverdueByClientId(DmOperationContext ctx, String clientId);

    /**
     * Lists deliverables for an agency tenant.
     *
     * @param ctx the operation context
     * @return list of deliverables
     */
    Promise<java.util.List<AgencyDeliverable>> list(DmOperationContext ctx);

    /**
     * Command to create an agency deliverable.
     */
    record CreateDeliverableCommand(
        String contractId,
        String deliverableType,
        String title,
        String description,
        LocalDate dueDate,
        String assignedTo,
        Map<String, Object> metadata
    ) {
        public CreateDeliverableCommand {
            if (contractId == null || contractId.isBlank()) {
                throw new IllegalArgumentException("contractId must not be blank");
            }
            if (deliverableType == null || deliverableType.isBlank()) {
                throw new IllegalArgumentException("deliverableType must not be blank");
            }
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("title must not be blank");
            }
            if (dueDate == null) {
                throw new IllegalArgumentException("dueDate must not be null");
            }
        }
    }
}
