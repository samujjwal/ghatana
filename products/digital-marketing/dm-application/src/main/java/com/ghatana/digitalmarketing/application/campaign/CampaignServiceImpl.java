package com.ghatana.digitalmarketing.application.campaign;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.command.DmCommandService;
import com.ghatana.digitalmarketing.application.governance.DmKillSwitchService;
import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
import com.ghatana.digitalmarketing.bridge.CampaignEventSourcingAdapter;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.campaign.CampaignStatus;
import com.ghatana.digitalmarketing.domain.campaign.CampaignType;
import com.ghatana.digitalmarketing.domain.command.DmCommandType;
import com.ghatana.plugin.compliance.CompliancePlugin;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

import static com.ghatana.digitalmarketing.pack.DmComplianceRuleSetIds.DM_CAMPAIGN_PREFLIGHT;

/**
 * Production implementation of {@link CampaignService}.
 *
 * <p>Orchestrates campaign lifecycle operations by coordinating:
 * <ol>
 *   <li>Authorization via {@link DigitalMarketingKernelAdapter#isAuthorized}</li>
 *   <li>Compliance preflight via {@link CompliancePlugin#evaluate}</li>
 *   <li>Domain transition via {@link Campaign} state machine</li>
 *   <li>Approval routing via {@link DigitalMarketingKernelAdapter#requestApproval}</li>
 *   <li>Persistence via {@link CampaignRepository}</li>
 *   <li>Event publishing via {@link CampaignEventSourcingAdapter}</li>
 *   <li>Audit recording via {@link DigitalMarketingKernelAdapter#recordAudit}</li>
 * </ol>
 * <p>P1-006: Event publishing is wired transactionally with repository saves to ensure
 * consistent event sourcing for campaign lifecycle events.</p>
 *
 * @doc.type class
 * @doc.purpose Production DMOS campaign application service
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class CampaignServiceImpl implements CampaignService {

    private static final Logger LOG = LoggerFactory.getLogger(CampaignServiceImpl.class);
    private static final double MAX_LAUNCH_RISK_SCORE = 0.80d;
    private static final String CAMPAIGN_LAUNCH_RISK_MODEL = "DM_CAMPAIGN_LAUNCH";

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final CampaignRepository repository;
    private final CompliancePlugin compliancePlugin;
    private final CampaignPreflightDataProvider preflightDataProvider;
    private final DmosMetricsCollector metrics;
    private final DmKillSwitchService killSwitchService;
    private final DmCommandService commandService;
    private final ObjectMapper objectMapper;
    private final CampaignEventSourcingAdapter eventSourcingAdapter;

    /**
     * Constructs the campaign service.
     *
     * @param kernelAdapter          DMOS kernel adapter for auth, consent, approval, audit, notifications
     * @param repository             campaign persistence
     * @param compliancePlugin       compliance evaluation for preflight checks
     * @param preflightDataProvider  provider for campaign preflight evidence
     * @param metrics                business KPI metrics collector (KE-03)
     * @param killSwitchService      kill switch service for circuit breaker checks (P1-024)
     * @param commandService         command service for issuing outbox commands (P1-023)
     * @param objectMapper           JSON mapper for command payload serialization
     * @param eventSourcingAdapter   event sourcing adapter for publishing campaign lifecycle events (P1-006)
     */
    public CampaignServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            CampaignRepository repository,
            CompliancePlugin compliancePlugin,
            CampaignPreflightDataProvider preflightDataProvider,
            DmosMetricsCollector metrics,
            DmKillSwitchService killSwitchService,
            DmCommandService commandService,
            ObjectMapper objectMapper,
            CampaignEventSourcingAdapter eventSourcingAdapter) {
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.compliancePlugin = Objects.requireNonNull(compliancePlugin, "compliancePlugin must not be null");
        this.preflightDataProvider = Objects.requireNonNull(
            preflightDataProvider,
            "preflightDataProvider must not be null"
        );
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.killSwitchService = Objects.requireNonNull(killSwitchService, "killSwitchService must not be null");
        this.commandService = Objects.requireNonNull(commandService, "commandService must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.eventSourcingAdapter = Objects.requireNonNull(eventSourcingAdapter, "eventSourcingAdapter must not be null");
    }

    // -----------------------------------------------------------------------
    // Create
    // -----------------------------------------------------------------------

    @Override
    public Promise<Campaign> createCampaign(DmOperationContext ctx, CreateCampaignCommand command) {
        Objects.requireNonNull(ctx,     "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "campaigns/*", "write")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException(
                        "Not authorized to create campaigns in workspace " + ctx.getWorkspaceId().getValue()
                    ));
                }
                Instant now = Instant.now();
                Campaign campaign = Campaign.builder()
                    .id(UUID.randomUUID().toString())
                    .workspaceId(ctx.getWorkspaceId())
                    .name(command.name())
                    .type(command.type())
                    .objective(command.objective())
                    .budgetCents(command.budgetCents())
                    .startDate(command.startDate())
                    .endDate(command.endDate())
                    .audience(command.audience())
                    .landingPageUrl(command.landingPageUrl())
                    .status(CampaignStatus.DRAFT)
                    .createdAt(now)
                    .updatedAt(now)
                    .createdBy(ctx.getActor().getPrincipalId())
                    .build();

                return repository.save(ctx.getTenantId(), campaign)
                    .then(saved -> eventSourcingAdapter.publishCreated(ctx, saved)
                        .then(offset -> kernelAdapter.recordAudit(
                            ctx.withIdempotencyKey(DmIdempotencyKey.forCommand("CreateCampaign", saved.getId())),
                            saved.getId(),
                            "create",
                            Map.of("campaignName", saved.getName(), "type", saved.getType().name())
                        ))
                    .map(auditId -> {
                        metrics.increment(DmosMetricsCollector.CAMPAIGN_CREATED, Map.of(
                            "tenantId",    ctx.getTenantId().getValue(),
                            "workspaceId", ctx.getWorkspaceId().getValue(),
                            "campaignType", saved.getType().name()
                        ));
                        LOG.info("[DMOS] Campaign created: id={}, name={}, tenant={}",
                            saved.getId(), saved.getName(), ctx.getTenantId().getValue());
                        return saved;
                    }));
            });
    }

    // -----------------------------------------------------------------------
    // Launch
    // -----------------------------------------------------------------------

    @Override
    public Promise<Campaign> launchCampaign(DmOperationContext ctx, String campaignId) {
        Objects.requireNonNull(ctx,        "ctx must not be null");
        Objects.requireNonNull(campaignId, "campaignId must not be null");

        return kernelAdapter.isAuthorized(ctx, "campaigns/" + campaignId, "launch")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException(
                        "Not authorized to launch campaign " + campaignId
                    ));
                }
                return repository.findById(ctx.getTenantId(), ctx.getWorkspaceId(), campaignId);
            })
            .then(optCampaign -> Promise.of(optCampaign.orElseThrow(
                () -> new NoSuchElementException("Campaign not found: " + campaignId)
            )))
            .then(campaign -> runCompliancePreflight(ctx, campaign))
            .then(campaign -> runLaunchRiskEvaluation(ctx, campaign))
            .then(campaign -> {
                Campaign pendingLaunch = campaign.requestLaunch();
                return repository.save(ctx.getTenantId(), pendingLaunch)
                    .then(saved -> issueGoogleAdsCommandIfNeeded(ctx, saved)
                        .then(commandState -> persistLaunchState(ctx, saved, commandState))
                        .then(updated -> publishLaunchEventIfExternallyLive(ctx, updated))
                        .then(updated -> kernelAdapter.recordAudit(
                            ctx,
                            updated.getId(),
                            "launch",
                            Map.of(
                                "previousStatus", campaign.getStatus().name(),
                                "newStatus", updated.getStatus().name(),
                                "riskModel", CAMPAIGN_LAUNCH_RISK_MODEL,
                                "campaignType", updated.getType().name()
                            )
                        ).map(auditId -> updated))
                    .then(auditId -> kernelAdapter.notifyUser(
                        ctx,
                        ctx.getActor().getPrincipalId(),
                        "dmos.campaign.launched",
                        Map.of("campaignId", auditId.getId(), "campaignName", auditId.getName())
                    ).map(ignored -> {
                        metrics.increment(DmosMetricsCollector.CAMPAIGN_LAUNCHED, Map.of(
                            "tenantId",    ctx.getTenantId().getValue(),
                            "workspaceId", ctx.getWorkspaceId().getValue(),
                            "campaignType", auditId.getType().name(),
                            "campaignStatus", auditId.getStatus().name()
                        ));
                        LOG.info("[DMOS] Campaign launch accepted: id={}, tenant={}, type={}, status={}",
                            auditId.getId(), ctx.getTenantId().getValue(), auditId.getType().name(), auditId.getStatus());
                        return auditId;
                    })));
            });
    }

    // -----------------------------------------------------------------------
    // Pause
    // -----------------------------------------------------------------------

    @Override
    public Promise<Campaign> pauseCampaign(DmOperationContext ctx, String campaignId) {
        Objects.requireNonNull(ctx,        "ctx must not be null");
        Objects.requireNonNull(campaignId, "campaignId must not be null");

        return kernelAdapter.isAuthorized(ctx, "campaigns/" + campaignId, "pause")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException(
                        "Not authorized to pause campaign " + campaignId
                    ));
                }
                return repository.findById(ctx.getTenantId(), ctx.getWorkspaceId(), campaignId);
            })
            .then(optCampaign -> Promise.of(optCampaign.orElseThrow(
                () -> new NoSuchElementException("Campaign not found: " + campaignId)
            )))
            .then(campaign -> {
                Campaign paused = campaign.pause();
                return repository.save(ctx.getTenantId(), paused)
                    .then(saved -> eventSourcingAdapter.publishPaused(ctx, saved)
                        .then(offset -> kernelAdapter.recordAudit(
                            ctx,
                            saved.getId(),
                            "pause",
                            Map.of("previousStatus", campaign.getStatus().name())
                        ))
                    .map(auditId -> {
                        metrics.increment(DmosMetricsCollector.CAMPAIGN_PAUSED, Map.of(
                            "tenantId",    ctx.getTenantId().getValue(),
                            "workspaceId", ctx.getWorkspaceId().getValue()
                        ));
                        LOG.info("[DMOS] Campaign paused: id={}, tenant={}",
                            saved.getId(), ctx.getTenantId().getValue());
                        return saved;
                    }));
            });
    }

    // -----------------------------------------------------------------------
    // Complete
    // -----------------------------------------------------------------------

    @Override
    public Promise<Campaign> completeCampaign(DmOperationContext ctx, String campaignId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(campaignId, "campaignId must not be null");

        return kernelAdapter.isAuthorized(ctx, "campaigns/" + campaignId, "complete")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException(
                        "Not authorized to complete campaign " + campaignId
                    ));
                }
                return repository.findById(ctx.getTenantId(), ctx.getWorkspaceId(), campaignId);
            })
            .then(optCampaign -> Promise.of(optCampaign.orElseThrow(
                () -> new NoSuchElementException("Campaign not found: " + campaignId)
            )))
            .then(campaign -> {
                Campaign completed = campaign.complete();
                return repository.save(ctx.getTenantId(), completed)
                    .then(saved -> eventSourcingAdapter.publishCompleted(ctx, saved)
                        .then(offset -> kernelAdapter.recordAudit(
                            ctx,
                            saved.getId(),
                            "complete",
                            Map.of("previousStatus", campaign.getStatus().name())
                        ))
                    .map(auditId -> {
                        metrics.increment(DmosMetricsCollector.CAMPAIGN_COMPLETED, Map.of(
                            "tenantId", ctx.getTenantId().getValue(),
                            "workspaceId", ctx.getWorkspaceId().getValue()
                        ));
                        LOG.info("[DMOS] Campaign completed: id={}, tenant={}",
                            saved.getId(), ctx.getTenantId().getValue());
                        return saved;
                    }));
            });
    }

    // -----------------------------------------------------------------------
    // Archive
    // -----------------------------------------------------------------------

    @Override
    public Promise<Campaign> archiveCampaign(DmOperationContext ctx, String campaignId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(campaignId, "campaignId must not be null");

        return kernelAdapter.isAuthorized(ctx, "campaigns/" + campaignId, "archive")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException(
                        "Not authorized to archive campaign " + campaignId
                    ));
                }
                return repository.findById(ctx.getTenantId(), ctx.getWorkspaceId(), campaignId);
            })
            .then(optCampaign -> Promise.of(optCampaign.orElseThrow(
                () -> new NoSuchElementException("Campaign not found: " + campaignId)
            )))
            .then(campaign -> {
                Campaign archived = campaign.archive();
                return repository.save(ctx.getTenantId(), archived)
                    .then(saved -> eventSourcingAdapter.publishArchived(ctx, saved)
                        .then(offset -> kernelAdapter.recordAudit(
                            ctx,
                            saved.getId(),
                            "archive",
                            Map.of("previousStatus", campaign.getStatus().name())
                        ))
                    .map(auditId -> {
                        metrics.increment(DmosMetricsCollector.CAMPAIGN_ARCHIVED, Map.of(
                            "tenantId", ctx.getTenantId().getValue(),
                            "workspaceId", ctx.getWorkspaceId().getValue()
                        ));
                        LOG.info("[DMOS] Campaign archived: id={}, tenant={}",
                            saved.getId(), ctx.getTenantId().getValue());
                        return saved;
                    }));
            });
    }

    // -----------------------------------------------------------------------
    // Rollback
    // -----------------------------------------------------------------------

    @Override
    public Promise<Campaign> rollbackCampaign(DmOperationContext ctx, String campaignId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(campaignId, "campaignId must not be null");

        return kernelAdapter.isAuthorized(ctx, "campaigns/" + campaignId, "rollback")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException(
                        "Not authorized to rollback campaign " + campaignId
                    ));
                }
                return repository.findById(ctx.getTenantId(), ctx.getWorkspaceId(), campaignId);
            })
            .then(optCampaign -> Promise.of(optCampaign.orElseThrow(
                () -> new NoSuchElementException("Campaign not found: " + campaignId)
            )))
            .then(campaign -> {
                CampaignStatus previousStatus = campaign.getStatus();
                Campaign rolledBack = campaign.rollback();
                return repository.save(ctx.getTenantId(), rolledBack)
                    .then(saved -> eventSourcingAdapter.publishRolledBack(ctx, saved, previousStatus)
                        .then(offset -> kernelAdapter.recordAudit(
                            ctx,
                            saved.getId(),
                            "rollback",
                            Map.of("previousStatus", previousStatus.name())
                        ))
                    .map(auditId -> {
                        metrics.increment(DmosMetricsCollector.CAMPAIGN_ROLLED_BACK, Map.of(
                            "tenantId", ctx.getTenantId().getValue(),
                            "workspaceId", ctx.getWorkspaceId().getValue()
                        ));
                        LOG.info("[DMOS] Campaign rolled back: id={}, previousStatus={}, tenant={}",
                            saved.getId(), previousStatus, ctx.getTenantId().getValue());
                        return saved;
                    }));
            });
    }

    // -----------------------------------------------------------------------
    // Duplicate
    // -----------------------------------------------------------------------

    @Override
    public Promise<Campaign> duplicateCampaign(DmOperationContext ctx, String campaignId, String newName) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(campaignId, "campaignId must not be null");
        Objects.requireNonNull(newName, "newName must not be null");
        if (newName.isBlank()) {
            throw new IllegalArgumentException("newName must not be blank");
        }

        return kernelAdapter.isAuthorized(ctx, "campaigns/" + campaignId, "read")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException(
                        "Not authorized to read campaign " + campaignId
                    ));
                }
                return repository.findById(ctx.getTenantId(), ctx.getWorkspaceId(), campaignId);
            })
            .then(optCampaign -> Promise.of(optCampaign.orElseThrow(
                () -> new NoSuchElementException("Campaign not found: " + campaignId)
            )))
            .then(original -> {
                Instant now = Instant.now();
                Campaign duplicate = Campaign.builder()
                    .id(UUID.randomUUID().toString())
                    .workspaceId(ctx.getWorkspaceId())
                    .name(newName)
                    .type(original.getType())
                    .objective(original.getObjective())
                    .budgetCents(original.getBudgetCents())
                    .startDate(original.getStartDate())
                    .endDate(original.getEndDate())
                    .audience(original.getAudience())
                    .landingPageUrl(original.getLandingPageUrl())
                    .status(CampaignStatus.DRAFT)
                    .createdAt(now)
                    .updatedAt(now)
                    .createdBy(ctx.getActor().getPrincipalId())
                    .build();

                return repository.save(ctx.getTenantId(), duplicate)
                    .then(saved -> eventSourcingAdapter.publishCreated(ctx, saved)
                        .then(offset -> kernelAdapter.recordAudit(
                            ctx.withIdempotencyKey(DmIdempotencyKey.forCommand("DuplicateCampaign", saved.getId())),
                            saved.getId(),
                            "duplicate",
                            Map.of(
                                "originalCampaignId", campaignId,
                                "originalCampaignName", original.getName(),
                                "campaignName", saved.getName(),
                                "type", saved.getType().name()
                            )
                        ))
                    .map(auditId -> {
                        metrics.increment(DmosMetricsCollector.CAMPAIGN_CREATED, Map.of(
                            "tenantId", ctx.getTenantId().getValue(),
                            "workspaceId", ctx.getWorkspaceId().getValue(),
                            "campaignType", saved.getType().name(),
                            "source", "duplicate"
                        ));
                        LOG.info("[DMOS] Campaign duplicated: originalId={}, newId={}, newName={}, tenant={}",
                            campaignId, saved.getId(), newName, ctx.getTenantId().getValue());
                        return saved;
                    }));
            });
    }

    // -----------------------------------------------------------------------
    // Read
    // -----------------------------------------------------------------------

    @Override
    public Promise<Campaign> getCampaign(DmOperationContext ctx, String campaignId) {
        Objects.requireNonNull(ctx,        "ctx must not be null");
        Objects.requireNonNull(campaignId, "campaignId must not be null");

        return kernelAdapter.isAuthorized(ctx, "campaigns/" + campaignId, "read")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException(
                        "Not authorized to read campaign " + campaignId
                    ));
                }
                return repository.findById(ctx.getTenantId(), ctx.getWorkspaceId(), campaignId);
            })
            .then(optCampaign -> Promise.of(optCampaign.orElseThrow(
                () -> new NoSuchElementException("Campaign not found: " + campaignId)
            )));
    }

    @Override
    public Promise<CampaignListResult> listCampaigns(DmOperationContext ctx, int limit, int offset) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        return kernelAdapter.isAuthorized(ctx, "campaigns/*", "read")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException(
                        "Not authorized to list campaigns in workspace " + ctx.getWorkspaceId().getValue()
                    ));
                }
                return repository.listByWorkspace(ctx.getTenantId(), ctx.getWorkspaceId(), limit, offset)
                    .then(campaigns -> repository.countByWorkspace(ctx.getTenantId(), ctx.getWorkspaceId())
                        .map(totalCount -> new CampaignListResult(campaigns, totalCount, limit, offset)));
            })
            .map(result -> {
                LOG.info("[DMOS] Listed {} campaigns (total={}) for tenant={}, workspace={}",
                    result.items().size(), result.totalCount(), ctx.getTenantId().getValue(), ctx.getWorkspaceId().getValue());
                return result;
            });
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    /**
     * Evaluates the campaign against the DM_CAMPAIGN_PREFLIGHT compliance rule set.
     * Throws {@link CampaignComplianceViolationException} if the campaign is not compliant.
     */
    private Promise<Campaign> runCompliancePreflight(DmOperationContext ctx, Campaign campaign) {
        return preflightDataProvider.resolve(ctx, campaign)
            .then(preflight -> {
                CompliancePlugin.ComplianceContext complianceCtx = new CompliancePlugin.ComplianceContext(
                    campaign.getId(),
                    "campaign",
                    Map.of(
                        "budgetApproved", preflight.budgetApproved(),
                        "targetAudienceCount", preflight.targetAudienceCount(),
                        "approvedContentCount", preflight.approvedContentCount(),
                        "totalSpend", preflight.totalSpend(),
                        "approvedBudget", preflight.approvedBudget()
                    ),
                    ctx.getActor().getPrincipalId(),
                    Instant.now()
                );
                return compliancePlugin.evaluate(DM_CAMPAIGN_PREFLIGHT, complianceCtx);
            })
            .then(result -> {
                if (!result.compliant()) {
                    metrics.increment(DmosMetricsCollector.COMPLIANCE_VIOLATION, Map.of(
                        "tenantId",    ctx.getTenantId().getValue(),
                        "workspaceId", ctx.getWorkspaceId().getValue(),
                        "ruleSet",     DM_CAMPAIGN_PREFLIGHT
                    ));
                    return Promise.ofException(new CampaignComplianceViolationException(
                        "Campaign " + campaign.getId() + " failed preflight compliance: "
                        + result.violations()
                    ));
                }
                return Promise.of(campaign);
            });
    }

    private Promise<Campaign> runLaunchRiskEvaluation(DmOperationContext ctx, Campaign campaign) {
        return kernelAdapter.evaluateRisk(
                ctx,
                campaign.getId(),
                CAMPAIGN_LAUNCH_RISK_MODEL,
                Map.of(
                    "campaignType", campaign.getType().name(),
                    "workspaceId", campaign.getWorkspaceId().getValue(),
                    "status", campaign.getStatus().name()
                )
            )
            .then(score -> {
                if (score > MAX_LAUNCH_RISK_SCORE) {
                    metrics.increment(DmosMetricsCollector.COMPLIANCE_VIOLATION, Map.of(
                        "tenantId",    ctx.getTenantId().getValue(),
                        "workspaceId", ctx.getWorkspaceId().getValue(),
                        "ruleSet",     CAMPAIGN_LAUNCH_RISK_MODEL
                    ));
                    return Promise.ofException(new CampaignComplianceViolationException(
                        "Campaign " + campaign.getId() + " risk score " + score
                            + " exceeds max launch threshold " + MAX_LAUNCH_RISK_SCORE
                    ));
                }
                return Promise.of(campaign);
            });
    }

    /**
     * Issues a Google Ads campaign creation command if this is a PAID_SEARCH campaign.
     *
     * <p>P1-023: Implements the outbox pattern - writes command to durable store for async execution.
     * P1-024: Checks kill switch before issuing command to prevent external writes when blocked.</p>
     *
     * @param ctx      operation context
     * @param campaign the launched campaign
     * @return Promise that completes when command is issued (or immediately if not applicable)
     */
    private Promise<LaunchCommandState> issueGoogleAdsCommandIfNeeded(DmOperationContext ctx, Campaign campaign) {
        // Only PAID_SEARCH campaigns need Google Ads external creation
        if (campaign.getType() != CampaignType.PAID_SEARCH) {
            LOG.debug("[DMOS] Campaign {} is type {}, skipping Google Ads command",
                campaign.getId(), campaign.getType());
            return Promise.of(LaunchCommandState.notRequired());
        }

        LOG.info("[DMOS] Checking kill switch for Google Ads campaign creation: campaignId={}",
            campaign.getId());

        // P1-024: Check kill switch before issuing external-write command
        return killSwitchService.isKillSwitchActive(
                ctx.getTenantId().getValue(),
                ctx.getWorkspaceId().getValue(),
                DmKillSwitchService.Features.GOOGLE_ADS_PUBLISH)
            .then(isBlocked -> {
                if (isBlocked) {
                    LOG.warn("[DMOS-KILLSWITCH] Google Ads campaign creation blocked for campaignId={}",
                        campaign.getId());
                    return killSwitchService.recordKillSwitchAudit(
                            ctx.getTenantId().getValue(),
                            ctx.getWorkspaceId().getValue(),
                            DmKillSwitchService.Features.GOOGLE_ADS_PUBLISH,
                            true,
                            ctx.getCorrelationId().getValue()
                        ).map(ignored -> LaunchCommandState.blocked());
                }

                // P1-023: Issue Google Ads campaign creation command to outbox
                LOG.info("[DMOS] Issuing GOOGLE_ADS_CAMPAIGN_CREATE command for campaignId={}",
                    campaign.getId());

                try {
                    if (campaign.getBudgetCents() == null || campaign.getBudgetCents() <= 0) {
                        return Promise.of(LaunchCommandState.failed("Paid-search launch requires explicit positive budget"));
                    }
                    if (isBlank(campaign.getAudience())) {
                        return Promise.of(LaunchCommandState.failed("Paid-search launch requires explicit targeting/audience"));
                    }
                    if (isBlank(campaign.getObjective())) {
                        return Promise.of(LaunchCommandState.failed("Paid-search launch requires explicit objective/keyword theme"));
                    }
                    if (isBlank(campaign.getLandingPageUrl())) {
                        return Promise.of(LaunchCommandState.failed("Paid-search launch requires explicit landing page URL"));
                    }

                    String dailyBudget = String.format("%.2f", campaign.getBudgetCents() / 100.0);

                    Map<String, Object> payload = Map.of(
                        "internalCampaignId", campaign.getId(),
                        "campaignName", campaign.getName(),
                        "dailyBudget", dailyBudget,
                        "targetingCriteria", List.of(campaign.getAudience()),
                        "keywordTheme", campaign.getObjective().toLowerCase(),
                        "landingPageUrl", campaign.getLandingPageUrl()
                    );
                    String serializedPayload = objectMapper.writeValueAsString(payload);

                    DmCommandService.IssueCommandRequest request = new DmCommandService.IssueCommandRequest(
                        DmCommandType.GOOGLE_ADS_CAMPAIGN_CREATE,
                        serializedPayload
                    );

                    return commandService.issue(ctx, request)
                        .then(cmd -> {
                            LOG.info("[DMOS] GOOGLE_ADS_CAMPAIGN_CREATE command issued: commandId={} campaignId={}",
                                cmd.getId(), campaign.getId());
                            return Promise.of(LaunchCommandState.pending(cmd.getId()));
                        });
                } catch (JsonProcessingException e) {
                    LOG.error("[DMOS] Failed to serialize Google Ads command payload: {}", e.getMessage(), e);
                    return Promise.of(LaunchCommandState.failed("Failed to serialize Google Ads command payload"));
                }
            });
    }

    private Promise<Campaign> persistLaunchState(
            DmOperationContext ctx,
            Campaign campaign,
            LaunchCommandState commandState) {
        Campaign updated = switch (commandState.outcome()) {
            case NOT_REQUIRED -> campaign.markLaunched();
            case PENDING -> campaign;
            case BLOCKED -> campaign.markExternalExecutionBlocked();
            case FAILED -> campaign.markLaunchFailed();
        };
        if (updated.getStatus() == CampaignStatus.LAUNCH_FAILED) {
            LOG.warn("[DMOS] Campaign launch failed before external execution: campaignId={} reason={}",
                campaign.getId(), commandState.reason());
        }
        return repository.save(ctx.getTenantId(), updated).then(saved -> kernelAdapter.recordAudit(
            ctx,
            saved.getId(),
            "launch-state",
            Map.of(
                "status", saved.getStatus().name(),
                "commandId", commandState.commandId() != null ? commandState.commandId() : "",
                "reason", commandState.reason() != null ? commandState.reason() : ""
            )
        ).map(auditId -> saved));
    }

    private Promise<Campaign> publishLaunchEventIfExternallyLive(DmOperationContext ctx, Campaign campaign) {
        if (campaign.getStatus() != CampaignStatus.LAUNCHED) {
            return Promise.of(campaign);
        }
        return eventSourcingAdapter.publishLaunched(ctx, campaign).map(offset -> campaign);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record LaunchCommandState(LaunchCommandOutcome outcome, String commandId, String reason) {
        static LaunchCommandState notRequired() {
            return new LaunchCommandState(LaunchCommandOutcome.NOT_REQUIRED, null, null);
        }

        static LaunchCommandState pending(String commandId) {
            return new LaunchCommandState(LaunchCommandOutcome.PENDING, commandId, null);
        }

        static LaunchCommandState blocked() {
            return new LaunchCommandState(LaunchCommandOutcome.BLOCKED, null, "Google Ads publish kill switch active");
        }

        static LaunchCommandState failed(String reason) {
            return new LaunchCommandState(LaunchCommandOutcome.FAILED, null, reason);
        }
    }

    private enum LaunchCommandOutcome {
        NOT_REQUIRED,
        PENDING,
        BLOCKED,
        FAILED
    }
}
