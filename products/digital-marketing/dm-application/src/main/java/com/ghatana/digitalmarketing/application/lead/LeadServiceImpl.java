package com.ghatana.digitalmarketing.application.lead;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.application.suppression.SuppressionRepository;
import com.ghatana.digitalmarketing.domain.lead.Lead;
import com.ghatana.digitalmarketing.domain.lead.LeadStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Production implementation of {@link LeadService}.
 *
 * <p>Enforces authorization via the kernel adapter, persists lead state transitions,
 * and emits structured audit events for every lifecycle action.</p>
 *
 * @doc.type class
 * @doc.purpose CRM-lite lead capture and qualification service implementation
 * @doc.layer product
 * @doc.pattern Service
 */
public final class LeadServiceImpl implements LeadService {

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final LeadRepository repository;
    private final SuppressionRepository suppressionRepository;

    /**
     * Constructs the service with required dependencies.
     *
     * @param kernelAdapter kernel adapter for auth and audit; must not be null
     * @param repository lead persistence port; must not be null
     * @param suppressionRepository suppression persistence port; must not be null
     */
    public LeadServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            LeadRepository repository,
            SuppressionRepository suppressionRepository) {
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.suppressionRepository = Objects.requireNonNull(
            suppressionRepository,
            "suppressionRepository must not be null"
        );
    }

    @Override
    public Promise<Lead> captureLead(DmOperationContext ctx, CaptureLeadCommand command) {
        Objects.requireNonNull(ctx,     "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "leads/*", "write")
            .then(allowed -> {
                if (!allowed) {
                    throw new SecurityException("Actor " + ctx.getActor().getPrincipalId()
                        + " is not authorized to capture leads");
                }
                return suppressionRepository.findActiveByEmail(ctx.getWorkspaceId(), command.email())
                    .then(activeSuppression -> {
                        if (activeSuppression.isPresent()) {
                            throw new IllegalArgumentException(
                                "Lead email is suppressed for workspace: " + command.email()
                            );
                        }
                        return repository.existsByEmail(ctx.getWorkspaceId(), command.campaignId(), command.email());
                    });
            })
            .then(exists -> {
                if (exists) {
                    throw new IllegalArgumentException(
                        "Lead already exists for email " + command.email()
                        + " in campaign " + command.campaignId());
                }
                Instant now = Instant.now();
                Lead lead = Lead.builder()
                    .id(UUID.randomUUID().toString())
                    .workspaceId(ctx.getWorkspaceId())
                    .campaignId(command.campaignId())
                    .email(command.email())
                    .firstName(command.firstName())
                    .lastName(command.lastName())
                    .phone(command.phone())
                    .source(command.source() != null ? command.source() : "unknown")
                    .status(LeadStatus.NEW)
                    .capturedAt(now)
                    .updatedAt(now)
                    .build();
                return repository.save(lead);
            })
            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getId(), "lead-captured",
                    Map.of("campaignId", saved.getCampaignId(), "email", saved.getEmail()))
                .map(ignored -> saved));
    }

    @Override
    public Promise<Lead> qualifyLead(DmOperationContext ctx, String leadId) {
        Objects.requireNonNull(ctx,    "ctx must not be null");
        Objects.requireNonNull(leadId, "leadId must not be null");

        return kernelAdapter.isAuthorized(ctx, "leads/" + leadId, "write")
            .then(allowed -> {
                if (!allowed) {
                    throw new SecurityException("Actor " + ctx.getActor().getPrincipalId()
                        + " is not authorized to qualify lead " + leadId);
                }
                return repository.findById(ctx.getWorkspaceId(), leadId);
            })
            .then(opt -> {
                Lead existing = opt.orElseThrow(() ->
                    new NoSuchElementException("Lead not found: " + leadId));
                Lead qualified = existing.qualify();
                return repository.save(qualified);
            })
            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getId(), "lead-qualified",
                    Map.of("previousStatus", LeadStatus.NEW.name()))
                .map(ignored -> saved));
    }

    @Override
    public Promise<Lead> convertLead(DmOperationContext ctx, String leadId) {
        Objects.requireNonNull(ctx,    "ctx must not be null");
        Objects.requireNonNull(leadId, "leadId must not be null");

        return kernelAdapter.isAuthorized(ctx, "leads/" + leadId, "write")
            .then(allowed -> {
                if (!allowed) {
                    throw new SecurityException("Actor " + ctx.getActor().getPrincipalId()
                        + " is not authorized to convert lead " + leadId);
                }
                return repository.findById(ctx.getWorkspaceId(), leadId);
            })
            .then(opt -> {
                Lead existing = opt.orElseThrow(() ->
                    new NoSuchElementException("Lead not found: " + leadId));
                Lead converted = existing.convert();
                return repository.save(converted);
            })
            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getId(), "lead-converted",
                    Map.of("previousStatus", LeadStatus.QUALIFIED.name()))
                .map(ignored -> saved));
    }

    @Override
    public Promise<Lead> disqualifyLead(DmOperationContext ctx, String leadId) {
        Objects.requireNonNull(ctx,    "ctx must not be null");
        Objects.requireNonNull(leadId, "leadId must not be null");

        return kernelAdapter.isAuthorized(ctx, "leads/" + leadId, "write")
            .then(allowed -> {
                if (!allowed) {
                    throw new SecurityException("Actor " + ctx.getActor().getPrincipalId()
                        + " is not authorized to disqualify lead " + leadId);
                }
                return repository.findById(ctx.getWorkspaceId(), leadId);
            })
            .then(opt -> {
                Lead existing = opt.orElseThrow(() ->
                    new NoSuchElementException("Lead not found: " + leadId));
                Lead disqualified = existing.disqualify();
                return repository.save(disqualified);
            })
            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getId(), "lead-disqualified",
                    Map.of("email", saved.getEmail()))
                .map(ignored -> saved));
    }

    @Override
    public Promise<Lead> getLead(DmOperationContext ctx, String leadId) {
        Objects.requireNonNull(ctx,    "ctx must not be null");
        Objects.requireNonNull(leadId, "leadId must not be null");

        return kernelAdapter.isAuthorized(ctx, "leads/" + leadId, "read")
            .then(allowed -> {
                if (!allowed) {
                    throw new SecurityException("Actor " + ctx.getActor().getPrincipalId()
                        + " is not authorized to read lead " + leadId);
                }
                return repository.findById(ctx.getWorkspaceId(), leadId);
            })
            .map(opt -> opt.orElseThrow(() ->
                new NoSuchElementException("Lead not found: " + leadId)));
    }
}
