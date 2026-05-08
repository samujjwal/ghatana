package com.ghatana.digitalmarketing.application.agency;

import com.ghatana.digitalmarketing.application.agency.AgencyRetainerService.CreateRetainerCommand;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.agency.AgencyRetainer;
import com.ghatana.digitalmarketing.domain.agency.AgencyRetainerStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of AgencyRetainerService.
 *
 * @doc.type class
 * @doc.purpose Agency retainer service implementation (P3-002)
 * @doc.layer product
 * @doc.pattern Service
 */
public class AgencyRetainerServiceImpl implements AgencyRetainerService {

    private final AgencyRetainerRepository repository;

    public AgencyRetainerServiceImpl(AgencyRetainerRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Promise<AgencyRetainer> create(DmOperationContext ctx, CreateRetainerCommand command) {
        String retainerId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        AgencyRetainer retainer = AgencyRetainer.builder()
            .id(retainerId)
            .contractId(command.contractId())
            .agencyTenantId(ctx.getTenantId().getValue())
            .clientId(ctx.getTenantId().getValue())
            .monthlyAmount(command.monthlyAmount())
            .currency(command.currency())
            .billingCycleStart(command.billingCycleStart())
            .billingDayOfMonth(command.billingDayOfMonth())
            .serviceAllowances(command.serviceAllowances())
            .overageRate(command.overageRate())
            .status(AgencyRetainerStatus.PENDING)
            .createdAt(now)
            .updatedAt(now)
            .build();

        return repository.save(retainer);
    }

    @Override
    public Promise<AgencyRetainer> activate(DmOperationContext ctx, String retainerId) {
        return repository.findById(retainerId)
            .then(retainerOpt -> {
                if (retainerOpt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Retainer not found: " + retainerId));
                }
                AgencyRetainer retainer = retainerOpt.get();
                AgencyRetainer activated = retainer.activate();
                return repository.save(activated);
            });
    }

    @Override
    public Promise<AgencyRetainer> suspend(DmOperationContext ctx, String retainerId, String reason) {
        return repository.findById(retainerId)
            .then(retainerOpt -> {
                if (retainerOpt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Retainer not found: " + retainerId));
                }
                AgencyRetainer retainer = retainerOpt.get();
                AgencyRetainer suspended = retainer.suspend(reason);
                return repository.save(suspended);
            });
    }

    @Override
    public Promise<AgencyRetainer> cancel(DmOperationContext ctx, String retainerId, String reason) {
        return repository.findById(retainerId)
            .then(retainerOpt -> {
                if (retainerOpt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Retainer not found: " + retainerId));
                }
                AgencyRetainer retainer = retainerOpt.get();
                AgencyRetainer cancelled = retainer.cancel(reason);
                return repository.save(cancelled);
            });
    }

    @Override
    public Promise<Optional<AgencyRetainer>> findById(DmOperationContext ctx, String retainerId) {
        return repository.findById(retainerId);
    }

    @Override
    public Promise<java.util.List<AgencyRetainer>> findByContractId(DmOperationContext ctx, String contractId) {
        return repository.findByContractId(contractId);
    }

    @Override
    public Promise<java.util.List<AgencyRetainer>> findByClientId(DmOperationContext ctx, String clientId) {
        return repository.findByClientId(clientId);
    }

    @Override
    public Promise<java.util.List<AgencyRetainer>> list(DmOperationContext ctx) {
        return repository.listByTenant(ctx.getTenantId().getValue());
    }
}
