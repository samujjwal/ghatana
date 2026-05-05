package com.ghatana.digitalmarketing.application.workflow;

import com.ghatana.digitalmarketing.application.campaign.CampaignService;
import com.ghatana.digitalmarketing.application.googleads.GoogleAdsConnector;
import com.ghatana.digitalmarketing.application.googleads.GoogleAdsService;
import com.ghatana.digitalmarketing.application.outbox.OutboxService;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.campaign.CampaignStatus;
import com.ghatana.digitalmarketing.domain.googleads.GoogleAdsCampaign;
import com.ghatana.digitalmarketing.domain.googleads.GoogleAdsSyncStatus;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * P1-023: Google Ads workflow executor with outbox pattern.
 *
 * <p>Handles the complete workflow for Google Ads campaign operations:
 * <ol>
 *   <li>Validate campaign state and prerequisites</li>
 *   <li>Create outbox entry for durable execution</li>
 *   <li>Execute Google Ads API calls via connector</li>
 *   <li>Handle success/failure with proper state transitions</li>
 *   <li>Emit audit events for observability</li>
 * </ol>
 *
 * <p>The outbox pattern ensures that operations are durable and can be
 * retried in case of transient failures or service outages.</p>
 *
 * @doc.type class
 * @doc.purpose Google Ads workflow execution with outbox pattern (P1-023)
 * @doc.layer product
 * @doc.pattern Workflow, Outbox, Saga
 */
public final class GoogleAdsWorkflowExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleAdsWorkflowExecutor.class);
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 5000;

    private final Eventloop eventloop;
    private final CampaignService campaignService;
    private final GoogleAdsService googleAdsService;
    private final GoogleAdsConnector googleAdsConnector;
    private final OutboxService outboxService;

    public GoogleAdsWorkflowExecutor(
            Eventloop eventloop,
            CampaignService campaignService,
            GoogleAdsService googleAdsService,
            GoogleAdsConnector googleAdsConnector,
            OutboxService outboxService) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop must not be null");
        this.campaignService = Objects.requireNonNull(campaignService, "campaignService must not be null");
        this.googleAdsService = Objects.requireNonNull(googleAdsService, "googleAdsService must not be null");
        this.googleAdsConnector = Objects.requireNonNull(googleAdsConnector, "googleAdsConnector must not be null");
        this.outboxService = Objects.requireNonNull(outboxService, "outboxService must not be null");
    }

    /**
     * Publishes a campaign to Google Ads via the outbox pattern.
     *
     * <p>This method is the entry point for the publish workflow. It validates
     * prerequisites, creates an outbox entry, and initiates execution.</p>
     *
     * @param ctx operation context
     * @param campaignId the campaign to publish
     * @return promise resolving to workflow result
     */
    public Promise<WorkflowResult> publishCampaign(DmOperationContext ctx, String campaignId) {
        String correlationId = ctx.getCorrelationId().getValue();
        MDC.put("correlationId", correlationId);
        MDC.put("campaignId", campaignId);

        LOG.info("[DMOS-WORKFLOW] Starting Google Ads publish workflow: campaign={}", campaignId);

        return validatePrerequisites(ctx, campaignId)
            .then(this::createOutboxEntry)
            .then(outboxId -> executeWorkflow(ctx, outboxId, WorkflowType.PUBLISH))
            .whenResult(result -> {
                LOG.info("[DMOS-WORKFLOW] Publish workflow completed: campaign={}, status={}",
                    campaignId, result.status());
                MDC.clear();
            })
            .whenException(e -> {
                LOG.error("[DMOS-WORKFLOW] Publish workflow failed: campaign={}, error={}",
                    campaignId, e.getMessage(), e);
                MDC.clear();
            });
    }

    /**
     * Updates an existing Google Ads campaign via the outbox pattern.
     */
    public Promise<WorkflowResult> updateCampaign(DmOperationContext ctx, String campaignId) {
        String correlationId = ctx.getCorrelationId().getValue();
        MDC.put("correlationId", correlationId);
        MDC.put("campaignId", campaignId);

        LOG.info("[DMOS-WORKFLOW] Starting Google Ads update workflow: campaign={}", campaignId);

        return validateUpdatePrerequisites(ctx, campaignId)
            .then(this::createUpdateOutboxEntry)
            .then(outboxId -> executeWorkflow(ctx, outboxId, WorkflowType.UPDATE))
            .whenResult(result -> {
                LOG.info("[DMOS-WORKFLOW] Update workflow completed: campaign={}, status={}",
                    campaignId, result.status());
                MDC.clear();
            })
            .whenException(e -> {
                LOG.error("[DMOS-WORKFLOW] Update workflow failed: campaign={}, error={}",
                    campaignId, e.getMessage(), e);
                MDC.clear();
            });
    }

    /**
     * Pauses a Google Ads campaign via the outbox pattern.
     */
    public Promise<WorkflowResult> pauseCampaign(DmOperationContext ctx, String campaignId) {
        String correlationId = ctx.getCorrelationId().getValue();
        MDC.put("correlationId", correlationId);
        MDC.put("campaignId", campaignId);

        LOG.info("[DMOS-WORKFLOW] Starting Google Ads pause workflow: campaign={}", campaignId);

        return validatePausePrerequisites(ctx, campaignId)
            .then(this::createPauseOutboxEntry)
            .then(outboxId -> executeWorkflow(ctx, outboxId, WorkflowType.PAUSE))
            .whenResult(result -> {
                LOG.info("[DMOS-WORKFLOW] Pause workflow completed: campaign={}, status={}",
                    campaignId, result.status());
                MDC.clear();
            })
            .whenException(e -> {
                LOG.error("[DMOS-WORKFLOW] Pause workflow failed: campaign={}, error={}",
                    campaignId, e.getMessage(), e);
                MDC.clear();
            });
    }

    /**
     * Validates prerequisites for campaign publishing.
     */
    private Promise<ValidationResult> validatePrerequisites(DmOperationContext ctx, String campaignId) {
        return campaignService.getCampaign(ctx, campaignId)
            .then(campaign -> {
                if (campaign.isEmpty()) {
                    return Promise.ofException(new WorkflowException(
                        "Campaign not found: " + campaignId));
                }

                Campaign c = campaign.get();

                // P1-023: Verify campaign is in correct state for publishing
                if (c.getStatus() != CampaignStatus.APPROVED &&
                    c.getStatus() != CampaignStatus.PENDING_PUBLICATION) {
                    return Promise.ofException(new WorkflowException(
                        "Campaign must be in APPROVED or PENDING_PUBLICATION state. Current: " + c.getStatus()));
                }

                // P1-023: Verify Google Ads configuration exists
                if (c.getGoogleAdsConfig() == null || c.getGoogleAdsConfig().getCustomerId() == null) {
                    return Promise.ofException(new WorkflowException(
                        "Campaign missing Google Ads configuration"));
                }

                // P1-023: Verify budget and strategy are approved
                if (!c.hasApprovedBudget() || !c.hasApprovedStrategy()) {
                    return Promise.ofException(new WorkflowException(
                        "Campaign requires approved budget and strategy before publishing"));
                }

                return Promise.of(new ValidationResult(c, ctx));
            });
    }

    /**
     * Validates prerequisites for campaign update.
     */
    private Promise<ValidationResult> validateUpdatePrerequisites(DmOperationContext ctx, String campaignId) {
        return campaignService.getCampaign(ctx, campaignId)
            .then(campaign -> {
                if (campaign.isEmpty()) {
                    return Promise.ofException(new WorkflowException(
                        "Campaign not found: " + campaignId));
                }

                Campaign c = campaign.get();

                // P1-023: Verify campaign is already published
                if (c.getStatus() != CampaignStatus.PUBLISHED &&
                    c.getStatus() != CampaignStatus.PUBLISHING) {
                    return Promise.ofException(new WorkflowException(
                        "Campaign must be published before updating. Current: " + c.getStatus()));
                }

                // P1-023: Verify external campaign ID exists
                if (c.getExternalCampaignId() == null) {
                    return Promise.ofException(new WorkflowException(
                        "Campaign missing external Google Ads campaign ID"));
                }

                return Promise.of(new ValidationResult(c, ctx));
            });
    }

    /**
     * Validates prerequisites for campaign pause.
     */
    private Promise<ValidationResult> validatePausePrerequisites(DmOperationContext ctx, String campaignId) {
        return campaignService.getCampaign(ctx, campaignId)
            .then(campaign -> {
                if (campaign.isEmpty()) {
                    return Promise.ofException(new WorkflowException(
                        "Campaign not found: " + campaignId));
                }

                Campaign c = campaign.get();

                // P1-023: Verify campaign is in active/published state
                if (c.getStatus() != CampaignStatus.PUBLISHED &&
                    c.getStatus() != CampaignStatus.RUNNING) {
                    return Promise.ofException(new WorkflowException(
                        "Campaign must be active to pause. Current: " + c.getStatus()));
                }

                return Promise.of(new ValidationResult(c, ctx));
            });
    }

    /**
     * Creates outbox entry for durable execution.
     */
    private Promise<String> createOutboxEntry(ValidationResult validation) {
        String outboxId = UUID.randomUUID().toString();

        OutboxEntry entry = OutboxEntry.builder()
            .id(outboxId)
            .type(OutboxEntry.Type.GOOGLE_ADS_PUBLISH)
            .campaignId(validation.campaign().getId())
            .tenantId(validation.ctx().getTenantId().getValue())
            .workspaceId(validation.ctx().getWorkspaceId().getValue())
            .correlationId(validation.ctx().getCorrelationId().getValue())
            .payload(createPayload(validation))
            .status(OutboxEntry.Status.PENDING)
            .createdAt(Instant.now())
            .retryCount(0)
            .maxRetries(MAX_RETRIES)
            .build();

        return outboxService.createEntry(entry)
            .then(v -> {
                LOG.info("[DMOS-WORKFLOW] Created outbox entry: id={}, campaign={}",
                    outboxId, validation.campaign().getId());
                return Promise.of(outboxId);
            });
    }

    private Promise<String> createUpdateOutboxEntry(ValidationResult validation) {
        String outboxId = UUID.randomUUID().toString();

        OutboxEntry entry = OutboxEntry.builder()
            .id(outboxId)
            .type(OutboxEntry.Type.GOOGLE_ADS_UPDATE)
            .campaignId(validation.campaign().getId())
            .tenantId(validation.ctx().getTenantId().getValue())
            .workspaceId(validation.ctx().getWorkspaceId().getValue())
            .correlationId(validation.ctx().getCorrelationId().getValue())
            .payload(createPayload(validation))
            .status(OutboxEntry.Status.PENDING)
            .createdAt(Instant.now())
            .retryCount(0)
            .maxRetries(MAX_RETRIES)
            .build();

        return outboxService.createEntry(entry)
            .then(v -> Promise.of(outboxId));
    }

    private Promise<String> createPauseOutboxEntry(ValidationResult validation) {
        String outboxId = UUID.randomUUID().toString();

        OutboxEntry entry = OutboxEntry.builder()
            .id(outboxId)
            .type(OutboxEntry.Type.GOOGLE_ADS_PAUSE)
            .campaignId(validation.campaign().getId())
            .tenantId(validation.ctx().getTenantId().getValue())
            .workspaceId(validation.ctx().getWorkspaceId().getValue())
            .correlationId(validation.ctx().getCorrelationId().getValue())
            .payload(createPayload(validation))
            .status(OutboxEntry.Status.PENDING)
            .createdAt(Instant.now())
            .retryCount(0)
            .maxRetries(MAX_RETRIES)
            .build();

        return outboxService.createEntry(entry)
            .then(v -> Promise.of(outboxId));
    }

    /**
     * Executes the workflow by processing the outbox entry.
     */
    private Promise<WorkflowResult> executeWorkflow(DmOperationContext ctx, String outboxId, WorkflowType type) {
        return outboxService.getEntry(outboxId)
            .then(entry -> {
                if (entry.isEmpty()) {
                    return Promise.ofException(new WorkflowException(
                        "Outbox entry not found: " + outboxId));
                }

                OutboxEntry e = entry.get();
                return processOutboxEntry(ctx, e, type, 0);
            });
    }

    /**
     * Processes the outbox entry with retry logic.
     */
    private Promise<WorkflowResult> processOutboxEntry(
            DmOperationContext ctx,
            OutboxEntry entry,
            WorkflowType type,
            int attempt) {

        LOG.info("[DMOS-WORKFLOW] Processing outbox entry: id={}, type={}, attempt={}",
            entry.getId(), type, attempt + 1);

        Promise<WorkflowResult> executionPromise = switch (type) {
            case PUBLISH -> executePublish(ctx, entry);
            case UPDATE -> executeUpdate(ctx, entry);
            case PAUSE -> executePause(ctx, entry);
        };

        return executionPromise
            .whenException(e -> {
                if (entry.getRetryCount() < entry.getMaxRetries()) {
                    LOG.warn("[DMOS-WORKFLOW] Execution failed, will retry: entry={}, error={}",
                        entry.getId(), e.getMessage());

                    // Schedule retry
                    return outboxService.incrementRetryCount(entry.getId())
                        .then(v -> {
                            eventloop.delay(RETRY_DELAY_MS, () -> {
                                processOutboxEntry(ctx, entry, type, attempt + 1);
                            });
                            return Promise.of(new WorkflowResult(
                                WorkflowStatus.RETRY_SCHEDULED, entry.getId()));
                        });
                } else {
                    LOG.error("[DMOS-WORKFLOW] Max retries exceeded, marking failed: entry={}",
                        entry.getId());

                    return outboxService.markFailed(entry.getId(), e.getMessage())
                        .then(v -> Promise.of(new WorkflowResult(
                            WorkflowStatus.FAILED, entry.getId())));
                }
            });
    }

    /**
     * Executes the Google Ads publish operation.
     */
    private Promise<WorkflowResult> executePublish(DmOperationContext ctx, OutboxEntry entry) {
        return googleAdsConnector.createCampaign(ctx, entry.getPayload())
            .then(result -> {
                if (result.isSuccess()) {
                    // P1-023: Update campaign with external ID and sync status
                    return campaignService.markPublished(ctx, entry.getCampaignId(),
                            result.getExternalCampaignId())
                        .then(v -> outboxService.markCompleted(entry.getId()))
                        .then(v -> Promise.of(new WorkflowResult(
                            WorkflowStatus.COMPLETED, entry.getId(),
                            result.getExternalCampaignId())));
                } else {
                    return Promise.ofException(new WorkflowException(
                        "Google Ads API error: " + result.getErrorMessage()));
                }
            });
    }

    /**
     * Executes the Google Ads update operation.
     */
    private Promise<WorkflowResult> executeUpdate(DmOperationContext ctx, OutboxEntry entry) {
        return googleAdsConnector.updateCampaign(ctx, entry.getPayload())
            .then(result -> {
                if (result.isSuccess()) {
                    return campaignService.markUpdated(ctx, entry.getCampaignId())
                        .then(v -> outboxService.markCompleted(entry.getId()))
                        .then(v -> Promise.of(new WorkflowResult(
                            WorkflowStatus.COMPLETED, entry.getId())));
                } else {
                    return Promise.ofException(new WorkflowException(
                        "Google Ads API error: " + result.getErrorMessage()));
                }
            });
    }

    /**
     * Executes the Google Ads pause operation.
     */
    private Promise<WorkflowResult> executePause(DmOperationContext ctx, OutboxEntry entry) {
        return googleAdsConnector.pauseCampaign(ctx, entry.getPayload())
            .then(result -> {
                if (result.isSuccess()) {
                    return campaignService.markPaused(ctx, entry.getCampaignId())
                        .then(v -> outboxService.markCompleted(entry.getId()))
                        .then(v -> Promise.of(new WorkflowResult(
                            WorkflowStatus.COMPLETED, entry.getId())));
                } else {
                    return Promise.ofException(new WorkflowException(
                        "Google Ads API error: " + result.getErrorMessage()));
                }
            });
    }

    private String createPayload(ValidationResult validation) {
        // Serialize campaign data for outbox storage
        Campaign c = validation.campaign();
        return String.format(
            "{\"campaignId\":\"%s\",\"customerId\":\"%s\",\"name\":\"%s\",\"budget\":%d}",
            c.getId(),
            c.getGoogleAdsConfig().getCustomerId(),
            c.getName(),
            c.getBudgetAmount()
        );
    }

    // Record classes
    private record ValidationResult(Campaign campaign, DmOperationContext ctx) {}

    public enum WorkflowType { PUBLISH, UPDATE, PAUSE }

    public enum WorkflowStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED, RETRY_SCHEDULED }

    public record WorkflowResult(
        WorkflowStatus status,
        String outboxId,
        String externalCampaignId
    ) {
        public WorkflowResult(WorkflowStatus status, String outboxId) {
            this(status, outboxId, null);
        }
    }

    public static class WorkflowException extends RuntimeException {
        public WorkflowException(String message) {
            super(message);
        }
    }
}
