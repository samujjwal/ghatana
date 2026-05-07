package com.ghatana.digitalmarketing.application.optimization;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.optimization.AnomalyDetectionResult;
import com.ghatana.digitalmarketing.domain.optimization.AnomalyStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Production implementation of {@link AnomalyDetectionService}.
 *
 * @doc.type class
 * @doc.purpose Publishes and manages lifecycle of anomaly detection results (P3-004)
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class AnomalyDetectionServiceImpl implements AnomalyDetectionService {

    private final AnomalyDetectionRepository repository;
    private final DigitalMarketingKernelAdapter kernelAdapter;

    public AnomalyDetectionServiceImpl(
            AnomalyDetectionRepository repository,
            DigitalMarketingKernelAdapter kernelAdapter) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
    }

    @Override
    public Promise<AnomalyDetectionResult> publish(DmOperationContext ctx, PublishAnomalyCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "anomaly-detection", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to publish anomalies"));
                }
                AnomalyDetectionResult anomaly = AnomalyDetectionResult.builder()
                    .id(UUID.randomUUID().toString())
                    .tenantId(ctx.getTenantId().getValue())
                    .workspaceId(ctx.getWorkspaceId().getValue())
                    .campaignId(command.campaignId())
                    .severity(command.severity())
                    .metricName(command.metricName())
                    .anomalyType(command.anomalyType())
                    .expectedValue(command.expectedValue())
                    .actualValue(command.actualValue())
                    .deviationPercentage(command.deviationPercentage())
                    .description(command.description())
                    .context(command.context())
                    .rationale(command.rationale())
                    .status(AnomalyStatus.DETECTED)
                    .detectedAt(Instant.now())
                    .build();
                return repository.save(anomaly)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx, saved.getId(), "anomaly-detected",
                        Map.of("campaignId", command.campaignId(), "metricName", command.metricName(), "severity", command.severity().name())
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<AnomalyDetectionResult> acknowledge(DmOperationContext ctx, String anomalyId, String acknowledgedBy) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(anomalyId, "anomalyId must not be null");
        Objects.requireNonNull(acknowledgedBy, "acknowledgedBy must not be null");

        return kernelAdapter.isAuthorized(ctx, "anomaly-detection", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to acknowledge anomalies"));
                }
                return loadAndValidateTenant(ctx, anomalyId)
                    .then(existing -> {
                        AnomalyDetectionResult updated = existing.acknowledge(acknowledgedBy);
                        return repository.update(updated)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx, saved.getId(), "anomaly-acknowledged", Map.of("acknowledgedBy", acknowledgedBy)
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<AnomalyDetectionResult> resolve(DmOperationContext ctx, String anomalyId, String mitigationAction) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(anomalyId, "anomalyId must not be null");
        Objects.requireNonNull(mitigationAction, "mitigationAction must not be null");

        return kernelAdapter.isAuthorized(ctx, "anomaly-detection", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to resolve anomalies"));
                }
                return loadAndValidateTenant(ctx, anomalyId)
                    .then(existing -> {
                        AnomalyDetectionResult updated = existing.resolve(mitigationAction);
                        return repository.update(updated)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx, saved.getId(), "anomaly-resolved", Map.of("mitigationAction", mitigationAction)
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<AnomalyDetectionResult> dismiss(DmOperationContext ctx, String anomalyId, String reason) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(anomalyId, "anomalyId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");

        return kernelAdapter.isAuthorized(ctx, "anomaly-detection", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to dismiss anomalies"));
                }
                return loadAndValidateTenant(ctx, anomalyId)
                    .then(existing -> {
                        AnomalyDetectionResult updated = existing.dismiss(reason);
                        return repository.update(updated)
                            .then(saved -> kernelAdapter.recordAudit(
                                ctx, saved.getId(), "anomaly-dismissed", Map.of("reason", reason)
                            ).map(__ -> saved));
                    });
            });
    }

    @Override
    public Promise<Optional<AnomalyDetectionResult>> findById(DmOperationContext ctx, String anomalyId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return repository.findById(anomalyId)
            .map(opt -> opt.filter(r -> r.getTenantId().equals(ctx.getTenantId().getValue())));
    }

    @Override
    public Promise<List<AnomalyDetectionResult>> listByWorkspace(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        return kernelAdapter.isAuthorized(ctx, "anomaly-detection", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list anomalies"));
                }
                return repository.listByWorkspace(ctx.getTenantId().getValue(), ctx.getWorkspaceId().getValue());
            });
    }

    @Override
    public Promise<List<AnomalyDetectionResult>> listByCampaign(DmOperationContext ctx, String campaignId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(campaignId, "campaignId must not be null");

        return kernelAdapter.isAuthorized(ctx, "anomaly-detection", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list anomalies"));
                }
                return repository.listByCampaign(ctx.getTenantId().getValue(), campaignId);
            });
    }

    @Override
    public Promise<List<AnomalyDetectionResult>> listBySeverity(DmOperationContext ctx, AnomalySeverity severity) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return kernelAdapter.isAuthorized(ctx, "anomaly-detection", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list anomalies"));
                }
                return repository.listBySeverity(ctx.getTenantId().getValue(), severity);
            });
    }

    @Override
    public Promise<List<AnomalyDetectionResult>> listByStatus(DmOperationContext ctx, AnomalyStatus status) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return kernelAdapter.isAuthorized(ctx, "anomaly-detection", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorised to list anomalies"));
                }
                return repository.listByStatus(ctx.getTenantId().getValue(), status);
            });
    }

    private Promise<AnomalyDetectionResult> loadAndValidateTenant(DmOperationContext ctx, String id) {
        return repository.findById(id)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new NoSuchElementException("Anomaly not found: " + id));
                }
                AnomalyDetectionResult r = opt.get();
                if (!r.getTenantId().equals(ctx.getTenantId().getValue())) {
                    return Promise.ofException(new SecurityException("Anomaly does not belong to tenant"));
                }
                return Promise.of(r);
            });
    }
}
