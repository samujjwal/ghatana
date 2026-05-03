package com.ghatana.digitalmarketing.application.attribution;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.attribution.DmAttributionRecord;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link DmAttributionRecordService}.
 *
 * @doc.type class
 * @doc.purpose Records last-click and UTM attribution for MVP reporting (DMOS-F2-017)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class DmAttributionRecordServiceImpl implements DmAttributionRecordService {

    private final DmAttributionRecordRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public DmAttributionRecordServiceImpl(
            DmAttributionRecordRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<DmAttributionRecord> record(DmOperationContext ctx, RecordAttributionCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "attribution-records", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to record attribution"));
                }
                DmAttributionRecord rec = DmAttributionRecord.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .visitorId(command.visitorId())
                    .sessionId(command.sessionId())
                    .conversionEventId(command.conversionEventId())
                    .attributedSource(command.attributedSource())
                    .attributedMedium(command.attributedMedium())
                    .attributedCampaign(command.attributedCampaign())
                    .attributedContent(command.attributedContent())
                    .attributedTerm(command.attributedTerm())
                    .model(command.model())
                    .attributionWeight(command.attributionWeight())
                    .convertedAt(Instant.now())
                    .createdAt(Instant.now())
                    .build();
                return repository.save(rec)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx, saved.getId(), "attribution-recorded",
                        Map.of("model", (Object) command.model().name(), "conversionEventId", command.conversionEventId())
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<Optional<DmAttributionRecord>> findById(DmOperationContext ctx, String recordId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(recordId, "recordId must not be null");

        return repository.findById(recordId)
            .map(opt -> opt.filter(r -> r.getTenantId().equals(ctx.getTenantId().getValue())));
    }

    @Override
    public Promise<List<DmAttributionRecord>> listByVisitor(DmOperationContext ctx, String visitorId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(visitorId, "visitorId must not be null");

        return repository.listByVisitor(ctx.getTenantId().getValue(), visitorId);
    }

    @Override
    public Promise<Optional<DmAttributionRecord>> findByConversionEvent(DmOperationContext ctx, String conversionEventId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(conversionEventId, "conversionEventId must not be null");

        return repository.findByConversionEvent(ctx.getTenantId().getValue(), conversionEventId)
            .map(opt -> opt.filter(r -> r.getTenantId().equals(ctx.getTenantId().getValue())));
    }
}
