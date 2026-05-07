package com.ghatana.digitalmarketing.application.agency;

import com.ghatana.digitalmarketing.application.agency.AgencyDeliverableService.CreateDeliverableCommand;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.agency.AgencyDeliverable;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of AgencyDeliverableService.
 *
 * @doc.type class
 * @doc.purpose Agency deliverable service implementation (P3-002)
 * @doc.layer product
 * @doc.pattern Service
 */
public class AgencyDeliverableServiceImpl implements AgencyDeliverableService {

    private final AgencyDeliverableRepository repository;

    public AgencyDeliverableServiceImpl(AgencyDeliverableRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Promise<AgencyDeliverable> create(DmOperationContext ctx, CreateDeliverableCommand command) {
        String deliverableId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        AgencyDeliverable deliverable = AgencyDeliverable.builder()
            .id(deliverableId)
            .contractId(command.contractId())
            .agencyTenantId(ctx.tenantId().getValue())
            .clientId(ctx.tenantId().getValue())
            .deliverableType(command.deliverableType())
            .title(command.title())
            .description(command.description())
            .dueDate(command.dueDate())
            .assignedTo(command.assignedTo())
            .metadata(command.metadata())
            .createdAt(now)
            .updatedAt(now)
            .build();

        return repository.save(deliverable);
    }

    @Override
    public Promise<AgencyDeliverable> start(DmOperationContext ctx, String deliverableId) {
        return repository.findById(deliverableId)
            .then(deliverableOpt -> {
                if (deliverableOpt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Deliverable not found: " + deliverableId));
                }
                AgencyDeliverable deliverable = deliverableOpt.get();
                AgencyDeliverable started = deliverable.start();
                return repository.save(started);
            });
    }

    @Override
    public Promise<AgencyDeliverable> submitForReview(DmOperationContext ctx, String deliverableId) {
        return repository.findById(deliverableId)
            .then(deliverableOpt -> {
                if (deliverableOpt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Deliverable not found: " + deliverableId));
                }
                AgencyDeliverable deliverable = deliverableOpt.get();
                AgencyDeliverable submitted = deliverable.submitForReview();
                return repository.save(submitted);
            });
    }

    @Override
    public Promise<AgencyDeliverable> complete(DmOperationContext ctx, String deliverableId) {
        return repository.findById(deliverableId)
            .then(deliverableOpt -> {
                if (deliverableOpt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Deliverable not found: " + deliverableId));
                }
                AgencyDeliverable deliverable = deliverableOpt.get();
                AgencyDeliverable completed = deliverable.complete();
                return repository.save(completed);
            });
    }

    @Override
    public Promise<AgencyDeliverable> reject(DmOperationContext ctx, String deliverableId, String reason) {
        return repository.findById(deliverableId)
            .then(deliverableOpt -> {
                if (deliverableOpt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Deliverable not found: " + deliverableId));
                }
                AgencyDeliverable deliverable = deliverableOpt.get();
                AgencyDeliverable rejected = deliverable.reject(reason);
                return repository.save(rejected);
            });
    }

    @Override
    public Promise<Optional<AgencyDeliverable>> findById(DmOperationContext ctx, String deliverableId) {
        return repository.findById(deliverableId);
    }

    @Override
    public Promise<java.util.List<AgencyDeliverable>> findByContractId(DmOperationContext ctx, String contractId) {
        return repository.findByContractId(contractId);
    }

    @Override
    public Promise<java.util.List<AgencyDeliverable>> findByAssignedTo(DmOperationContext ctx, String assignedTo) {
        return repository.findByAssignedTo(assignedTo);
    }

    @Override
    public Promise<java.util.List<AgencyDeliverable>> findOverdueByClientId(DmOperationContext ctx, String clientId) {
        return repository.findOverdueByClientId(clientId);
    }

    @Override
    public Promise<java.util.List<AgencyDeliverable>> list(DmOperationContext ctx) {
        return repository.listByTenant(ctx.tenantId().getValue());
    }
}
