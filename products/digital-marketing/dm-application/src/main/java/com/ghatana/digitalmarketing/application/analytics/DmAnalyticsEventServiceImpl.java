package com.ghatana.digitalmarketing.application.analytics;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.analytics.DmAnalyticsEvent;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmAnalyticsEventService}.
 *
 * @doc.type class
 * @doc.purpose Ingests and exposes analytics events for MVP reporting (DMOS-F2-016)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DmAnalyticsEventServiceImpl implements DmAnalyticsEventService {

    private final DmAnalyticsEventRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmAnalyticsEventServiceImpl(
            DmAnalyticsEventRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmAnalyticsEvent> ingest(DmOperationContext ctx, IngestAnalyticsEventCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "analytics-events", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to ingest analytics events"));
                }
                DmAnalyticsEvent event = DmAnalyticsEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .sessionId(command.sessionId())
                    .eventType(command.eventType())
                    .sourceUrl(command.sourceUrl())
                    .utmSource(command.utmSource())
                    .utmMedium(command.utmMedium())
                    .utmCampaign(command.utmCampaign())
                    .utmContent(command.utmContent())
                    .utmTerm(command.utmTerm())
                    .visitorId(command.visitorId())
                    .properties(command.properties() != null ? command.properties() : Map.of())
                    .occurredAt(Instant.now())
                    .build();
                return repository.save(event)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx, saved.getId(), "analytics-event-ingested",
                        Map.of("eventType", (Object) command.eventType(), "sessionId", command.sessionId())
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<Optional<DmAnalyticsEvent>> findById(DmOperationContext ctx, String eventId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");

        return repository.findById(eventId)
            .map(opt -> opt.filter(e -> e.getTenantId().equals(ctx.getTenantId().getValue())));
    }

    @Override
    public Promise<List<DmAnalyticsEvent>> listBySession(DmOperationContext ctx, String sessionId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");

        return repository.listBySession(ctx.getTenantId().getValue(), sessionId);
    }

    @Override
    public Promise<List<DmAnalyticsEvent>> listByTenant(DmOperationContext ctx, int limit) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        return kernelAdapter.isAuthorized(ctx, "analytics-events", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list analytics events"));
                }
                return repository.listByTenant(ctx.getTenantId().getValue(), limit);
            });
    }
}
