package com.ghatana.digitalmarketing.application.agency;

import com.ghatana.digitalmarketing.application.agency.AgencyApprovalSLAService.CreateSLACommand;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.agency.AgencyApprovalSLA;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of AgencyApprovalSLAService.
 *
 * @doc.type class
 * @doc.purpose Agency approval SLA service implementation (P3-002)
 * @doc.layer product
 * @doc.pattern Service
 */
public class AgencyApprovalSLAServiceImpl implements AgencyApprovalSLAService {

    private final AgencyApprovalSLARepository repository;

    public AgencyApprovalSLAServiceImpl(AgencyApprovalSLARepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Promise<AgencyApprovalSLA> create(DmOperationContext ctx, CreateSLACommand command) {
        String slaId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        AgencyApprovalSLA sla = AgencyApprovalSLA.builder()
            .id(slaId)
            .contractId(command.contractId())
            .agencyTenantId(ctx.tenantId().getValue())
            .clientId(ctx.tenantId().getValue())
            .approvalType(command.approvalType())
            .maxApprovalTime(command.maxApprovalTime())
            .escalationLevel(0)
            .escalationTimeouts(command.escalationTimeouts())
            .escalationProcedure(command.escalationProcedure())
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .build();

        return repository.save(sla);
    }

    @Override
    public Promise<AgencyApprovalSLA> activate(DmOperationContext ctx, String slaId) {
        return repository.findById(slaId)
            .then(slaOpt -> {
                if (slaOpt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("SLA not found: " + slaId));
                }
                AgencyApprovalSLA sla = slaOpt.get();
                AgencyApprovalSLA activated = sla.activate();
                return repository.save(activated);
            });
    }

    @Override
    public Promise<AgencyApprovalSLA> deactivate(DmOperationContext ctx, String slaId, String reason) {
        return repository.findById(slaId)
            .then(slaOpt -> {
                if (slaOpt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("SLA not found: " + slaId));
                }
                AgencyApprovalSLA sla = slaOpt.get();
                AgencyApprovalSLA deactivated = sla.deactivate(reason);
                return repository.save(deactivated);
            });
    }

    @Override
    public Promise<AgencyApprovalSLA> updateEscalationLevel(DmOperationContext ctx, String slaId, int newLevel) {
        return repository.findById(slaId)
            .then(slaOpt -> {
                if (slaOpt.isEmpty()) {
                    return Promise.ofException(new IllegalArgumentException("SLA not found: " + slaId));
                }
                AgencyApprovalSLA sla = slaOpt.get();
                AgencyApprovalSLA updated = sla.updateEscalationLevel(newLevel);
                return repository.save(updated);
            });
    }

    @Override
    public Promise<Optional<AgencyApprovalSLA>> findById(DmOperationContext ctx, String slaId) {
        return repository.findById(slaId);
    }

    @Override
    public Promise<java.util.List<AgencyApprovalSLA>> findByContractId(DmOperationContext ctx, String contractId) {
        return repository.findByContractId(contractId);
    }

    @Override
    public Promise<java.util.List<AgencyApprovalSLA>> findByApprovalType(DmOperationContext ctx, String approvalType) {
        return repository.findByApprovalType(approvalType);
    }

    @Override
    public Promise<java.util.List<AgencyApprovalSLA>> list(DmOperationContext ctx) {
        return repository.listByTenant(ctx.tenantId().getValue());
    }
}
