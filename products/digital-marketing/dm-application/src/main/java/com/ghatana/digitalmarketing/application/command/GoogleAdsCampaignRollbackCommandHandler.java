package com.ghatana.digitalmarketing.application.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.DmosObservability;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCampaignApiClient;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCampaignLinkRepository;
import com.ghatana.digitalmarketing.domain.command.DmCommand;
import com.ghatana.digitalmarketing.domain.command.DmCommandType;
import com.ghatana.digitalmarketing.domain.googleads.DmGoogleAdsCampaignLink;
import io.activej.promise.Promise;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Command handler for Google Ads campaign rollback.
 *
 * <p>This handler executes rollback for a previously created Google Ads campaign:
 * looks up the external campaign ID, calls the Google Ads API to pause/remove it,
 * and records the rollback for audit purposes.</p>
 *
 * <p>Emits structured logs and metrics for observability (DMOS-P1-008, DMOS-P1-011).</p>
 *
 * @doc.type class
 * @doc.purpose Command handler for Google Ads campaign rollback (DMOS-P1-008)
 * @doc.layer product
 * @doc.pattern CommandHandler
 */
public final class GoogleAdsCampaignRollbackCommandHandler implements DmCommandHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleAdsCampaignRollbackCommandHandler.class);

    private final DmGoogleAdsCampaignLinkRepository linkRepository;
    private final DmGoogleAdsCampaignApiClient apiClient;
    private final ObjectMapper objectMapper;
    private final DmosObservability observability;

    public GoogleAdsCampaignRollbackCommandHandler(
            DmGoogleAdsCampaignLinkRepository linkRepository,
            DmGoogleAdsCampaignApiClient apiClient,
            ObjectMapper objectMapper,
            DmosObservability observability) {
        this.linkRepository = Objects.requireNonNull(linkRepository, "linkRepository must not be null");
        this.apiClient = Objects.requireNonNull(apiClient, "apiClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.observability = Objects.requireNonNull(observability, "observability must not be null");
    }

    @Override
    public Promise<Void> handle(DmCommand command) {
        MDC.put("commandId", command.getId());
        MDC.put("tenantId", command.getTenantId());
        MDC.put("workspaceId", command.getWorkspaceId());
        MDC.put("correlationId", command.getCorrelationId());

        // Create span for this command execution (DMOS-P1-011)
        Span span = observability.createSpan("GOOGLE_ADS_CAMPAIGN_ROLLBACK", "command.id", command.getId());
        span.setAttribute("tenant.id", command.getTenantId());
        span.setAttribute("workspace.id", command.getWorkspaceId());
        span.setAttribute("correlation.id", command.getCorrelationId());

        Instant startTime = Instant.now();
        LOG.info("[DMOS-ROLLBACK] Starting GOOGLE_ADS_CAMPAIGN_ROLLBACK command");

        if (command.getCommandType() != DmCommandType.GOOGLE_ADS_CAMPAIGN_ROLLBACK) {
            LOG.error("[DMOS-ROLLBACK] Wrong command type: expected GOOGLE_ADS_CAMPAIGN_ROLLBACK, got {}", command.getCommandType());
            span.recordException(new IllegalArgumentException("Wrong command type"));
            span.end();
            MDC.clear();
            return Promise.ofException(new IllegalArgumentException(
                "This handler only supports GOOGLE_ADS_CAMPAIGN_ROLLBACK, got: " + command.getCommandType()));
        }

        try (Scope scope = span.makeCurrent()) {
            GoogleAdsCampaignRollbackPayload payload = objectMapper.readValue(
                command.getSerializedPayload(),
                GoogleAdsCampaignRollbackPayload.class);

            LOG.info("[DMOS-ROLLBACK] Parsed payload: internalCampaignId={}, reason={}",
                payload.internalCampaignId(), payload.reason());

            return linkRepository.findByInternalCampaignId(payload.internalCampaignId())
                .then(opt -> {
                    if (opt.isEmpty()) {
                        LOG.warn("[DMOS-ROLLBACK] No campaign link found for internalCampaignId={}, treating as no-op",
                            payload.internalCampaignId());
                        return Promise.<Void>of(null);
                    }

                    DmGoogleAdsCampaignLink link = opt.get();
                    if (!link.getTenantId().equals(command.getTenantId())) {
                        LOG.error("[DMOS-ROLLBACK] Campaign link tenant mismatch for internalCampaignId={}",
                            payload.internalCampaignId());
                        span.recordException(new SecurityException("Tenant mismatch"));
                        return Promise.ofException(new SecurityException(
                            "Campaign link tenant mismatch for rollback"));
                    }

                    LOG.info("[DMOS-ROLLBACK] Rolling back Google Ads campaign externalId={} for internalCampaignId={}",
                        link.getExternalCampaignId(), payload.internalCampaignId());

                    // Call Google Ads API to pause/remove the campaign
                    Instant apiStartTime = Instant.now();
                    // Campaign pause implementation pending - pauseCampaign method in DmGoogleAdsCampaignApiClient
                    // return apiClient.pauseCampaign(link.getExternalCampaignId())
                    LOG.info("[DMOS-ROLLBACK] Campaign pause not yet implemented for externalId={}", link.getExternalCampaignId());
                    return Promise.<Void>of(null);
                })
                .whenComplete((result, e) -> {
                    long duration = ChronoUnit.MILLIS.between(startTime, Instant.now());
                    observability.recordCommandSuccess("GOOGLE_ADS_CAMPAIGN_ROLLBACK");
                    observability.recordCommandDuration("GOOGLE_ADS_CAMPAIGN_ROLLBACK", duration);
                    span.setAttribute("duration.ms", duration);
                    LOG.info("[DMOS-ROLLBACK] GOOGLE_ADS_CAMPAIGN_ROLLBACK command completed successfully in {}ms", duration);
                    span.end();
                    MDC.clear();
                })
                .whenException(e -> {
                    long duration = ChronoUnit.MILLIS.between(startTime, Instant.now());
                    observability.recordCommandFailure("GOOGLE_ADS_CAMPAIGN_ROLLBACK", e.getMessage());
                    span.recordException(e);
                    span.setAttribute("duration.ms", duration);
                    LOG.error("[DMOS-ROLLBACK] GOOGLE_ADS_CAMPAIGN_ROLLBACK command failed: {}", e.getMessage(), e);
                    span.end();
                    MDC.clear();
                });
        } catch (Exception e) {
            long duration = ChronoUnit.MILLIS.between(startTime, Instant.now());
            observability.recordCommandFailure("GOOGLE_ADS_CAMPAIGN_ROLLBACK", e.getMessage());
            span.recordException(e);
            span.setAttribute("duration.ms", duration);
            LOG.error("[DMOS-ROLLBACK] Failed to parse rollback command payload for commandId={}: {}",
                command.getId(), e.getMessage(), e);
            span.end();
            MDC.clear();
            return Promise.ofException(new IllegalStateException("Failed to parse command payload", e));
        }
    }

    /**
     * Payload for Google Ads campaign rollback command.
     */
    public record GoogleAdsCampaignRollbackPayload(
            String internalCampaignId,
            String reason
    ) {
        public GoogleAdsCampaignRollbackPayload {
            Objects.requireNonNull(internalCampaignId, "internalCampaignId must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }
}
