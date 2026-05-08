package com.ghatana.digitalmarketing.application.agency;

import com.ghatana.digitalmarketing.application.agency.AgencyContractService.CreateContractCommand;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.agency.AgencyContract;
import com.ghatana.digitalmarketing.domain.agency.AgencyContractStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of AgencyContractService.
 *
 * @doc.type class
 * @doc.purpose Agency contract service implementation (P3-002)
 * @doc.layer product
 * @doc.pattern Service
 */
public class AgencyContractServiceImpl implements AgencyContractService {

    private final AgencyContractRepository repository;

    public AgencyContractServiceImpl(AgencyContractRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Promise<AgencyContract> create(DmOperationContext ctx, CreateContractCommand command) {
        String contractId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        AgencyContract contract = AgencyContract.builder()
            .id(contractId)
            .agencyTenantId(ctx.getTenantId().getValue())
            .clientId(command.clientId())
            .contractNumber(command.contractNumber())
            .contractType(command.contractType())
            .startDate(command.startDate())
            .endDate(command.endDate())
            .monthlyRetainer(command.monthlyRetainer())
            .currency(command.currency())
            .status(AgencyContractStatus.DRAFT)
            .terms(command.terms())
            .createdAt(now)
            .updatedAt(now)
            .build();

        return repository.save(contract);
    }

    @Override
    public Promise<AgencyContract> activate(DmOperationContext ctx, String contractId) {
        return repository.findById(contractId)
            .then(contractOpt -> {
                if (contractOpt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Contract not found: " + contractId));
                }
                AgencyContract contract = contractOpt.get();
                AgencyContract activated = contract.activate();
                return repository.save(activated);
            });
    }

    @Override
    public Promise<AgencyContract> terminate(DmOperationContext ctx, String contractId, String reason) {
        return repository.findById(contractId)
            .then(contractOpt -> {
                if (contractOpt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Contract not found: " + contractId));
                }
                AgencyContract contract = contractOpt.get();
                AgencyContract terminated = contract.terminate(reason);
                return repository.save(terminated);
            });
    }

    @Override
    public Promise<AgencyContract> renew(DmOperationContext ctx, String contractId, java.time.LocalDate newEndDate) {
        return repository.findById(contractId)
            .then(contractOpt -> {
                if (contractOpt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("Contract not found: " + contractId));
                }
                AgencyContract contract = contractOpt.get();
                AgencyContract renewed = contract.renew(newEndDate);
                return repository.save(renewed);
            });
    }

    @Override
    public Promise<Optional<AgencyContract>> findById(DmOperationContext ctx, String contractId) {
        return repository.findById(contractId);
    }

    @Override
    public Promise<java.util.List<AgencyContract>> findByClientId(DmOperationContext ctx, String clientId) {
        return repository.findByClientId(clientId);
    }

    @Override
    public Promise<java.util.List<AgencyContract>> list(DmOperationContext ctx) {
        return repository.listByTenant(ctx.getTenantId().getValue());
    }
}
