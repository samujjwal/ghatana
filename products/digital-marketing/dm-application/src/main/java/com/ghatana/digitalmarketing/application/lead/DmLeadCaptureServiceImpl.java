package com.ghatana.digitalmarketing.application.lead;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.lead.DmLeadCapture;
import com.ghatana.digitalmarketing.domain.lead.DmLeadStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmLeadCaptureService}.
 *
 * @doc.type class
 * @doc.purpose Executes tenant-safe lead capture and CRM-lite status transitions (DMOS-F2-011)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DmLeadCaptureServiceImpl implements DmLeadCaptureService {

    private final DmLeadCaptureRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmLeadCaptureServiceImpl(
            DmLeadCaptureRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmLeadCapture> capture(DmOperationContext ctx, CaptureLeadFormCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "leads", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to capture leads"));
                }
                return repository.findByEmailAndLandingPage(
                        ctx.getTenantId().getValue(),
                        command.landingPageId(),
                        command.email())
                    .then(existing -> {
                        if (existing.isPresent()) {
                            return Promise.ofException(new IllegalArgumentException("Lead already exists for email + landingPage"));
                        }
                        DmLeadCapture lead = DmLeadCapture.builder()
                            .id(UUID.randomUUID().toString())
                            .tenantId(ctx.getTenantId().getValue())
                            .workspaceId(ctx.getWorkspaceId().getValue())
                            .landingPageId(command.landingPageId())
                            .email(command.email())
                            .name(command.name())
                            .phone(command.phone())
                            .customFields(command.customFields())
                            .utmSource(command.utmSource())
                            .utmMedium(command.utmMedium())
                            .utmCampaign(command.utmCampaign())
                            .status(DmLeadStatus.NEW)
                            .capturedAt(Instant.now())
                            .build();
                        return repository.save(lead)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx,
                                saved.getId(),
                                "lead-form-captured",
                                Map.of(
                                    "landingPageId", saved.getLandingPageId(),
                                    "status", saved.getStatus().name()
                                )
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<DmLeadCapture> qualify(DmOperationContext ctx, String leadCaptureId) {
        return transition(ctx, leadCaptureId, DmLeadStatus.QUALIFIED);
    }

    @Override
    public Promise<DmLeadCapture> markContacted(DmOperationContext ctx, String leadCaptureId) {
        return transition(ctx, leadCaptureId, DmLeadStatus.CONTACTED);
    }

    @Override
    public Promise<DmLeadCapture> convert(DmOperationContext ctx, String leadCaptureId) {
        return transition(ctx, leadCaptureId, DmLeadStatus.CONVERTED);
    }

    @Override
    public Promise<DmLeadCapture> disqualify(DmOperationContext ctx, String leadCaptureId) {
        return transition(ctx, leadCaptureId, DmLeadStatus.DISQUALIFIED);
    }

    @Override
    public Promise<Optional<DmLeadCapture>> findById(DmOperationContext ctx, String leadCaptureId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (leadCaptureId == null || leadCaptureId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("leadCaptureId must not be blank"));
        }

        return repository.findById(leadCaptureId)
            .map(opt -> opt.filter(v -> v.getTenantId().equals(ctx.getTenantId().getValue())));
    }

    @Override
    public Promise<List<DmLeadCapture>> listByStatus(DmOperationContext ctx, DmLeadStatus status, int limit) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (limit <= 0) {
            return Promise.ofException(new IllegalArgumentException("limit must be > 0"));
        }

        return kernelAdapter.isAuthorized(ctx, "leads", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to read leads"));
                }
                return repository.listByStatus(ctx.getTenantId().getValue(), status, limit);
            });
    }

    private Promise<DmLeadCapture> transition(
            DmOperationContext ctx,
            String leadCaptureId,
            DmLeadStatus targetStatus) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (leadCaptureId == null || leadCaptureId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("leadCaptureId must not be blank"));
        }

        return kernelAdapter.isAuthorized(ctx, "leads", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to update leads"));
                }
                return requireOwnedLead(ctx, leadCaptureId)
                    .then(lead -> {
                        DmLeadCapture transitioned = withStatus(lead, targetStatus);
                        return repository.update(transitioned)
                            .then(updated -> kernelAdapter.recordAudit(
                                ctx,
                                updated.getId(),
                                "lead-status-updated",
                                Map.of("status", updated.getStatus().name())
                            ).map(__ -> updated));
                    });
            });
    }

    private Promise<DmLeadCapture> requireOwnedLead(DmOperationContext ctx, String leadCaptureId) {
        return repository.findById(leadCaptureId)
            .then(opt -> {
                if (opt.isEmpty() || !opt.get().getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(new NoSuchElementException("Lead capture not found: " + leadCaptureId));
                }
                return Promise.of(opt.get());
            });
    }

    private DmLeadCapture withStatus(DmLeadCapture lead, DmLeadStatus nextStatus) {
        DmLeadStatus current = lead.getStatus();
        if (nextStatus == DmLeadStatus.QUALIFIED && current != DmLeadStatus.NEW) {
            throw new IllegalStateException("Only NEW leads can be QUALIFIED");
        }
        if (nextStatus == DmLeadStatus.CONTACTED && current != DmLeadStatus.QUALIFIED) {
            throw new IllegalStateException("Only QUALIFIED leads can be CONTACTED");
        }
        if (nextStatus == DmLeadStatus.CONVERTED
                && current != DmLeadStatus.QUALIFIED
                && current != DmLeadStatus.CONTACTED) {
            throw new IllegalStateException("Only QUALIFIED or CONTACTED leads can be CONVERTED");
        }
        if (nextStatus == DmLeadStatus.DISQUALIFIED && current == DmLeadStatus.CONVERTED) {
            throw new IllegalStateException("CONVERTED leads cannot be DISQUALIFIED");
        }

        return DmLeadCapture.builder()
            .id(lead.getId())
            .tenantId(lead.getTenantId())
            .workspaceId(lead.getWorkspaceId())
            .landingPageId(lead.getLandingPageId())
            .email(lead.getEmail())
            .name(lead.getName())
            .phone(lead.getPhone())
            .customFields(lead.getCustomFields())
            .utmSource(lead.getUtmSource())
            .utmMedium(lead.getUtmMedium())
            .utmCampaign(lead.getUtmCampaign())
            .status(nextStatus)
            .capturedAt(lead.getCapturedAt())
            .build();
    }
}
