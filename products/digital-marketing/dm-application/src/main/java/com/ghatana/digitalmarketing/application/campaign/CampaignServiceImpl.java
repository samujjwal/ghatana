package com.ghatana.digitalmarketing.application.campaign;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.digitalmarketing.application.command.DmCommandService;
import com.ghatana.digitalmarketing.application.governance.DmKillSwitchService;
import com.ghatana.digitalmarketing.application.metrics.DmosMetricsCollector;
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
 *   <li>Audit recording via {@link DigitalMarketingKernelAdapter#recordAudit}</li>
 * </ol>
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
     */
    public CampaignServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            CampaignRepository repository,
            CompliancePlugin compliancePlugin,
            CampaignPreflightDataProvider preflightDataProvider,
            DmosMetricsCollector metrics,
            DmKillSwitchService killSwitchService,
            DmCommandService commandService,
            ObjectMapper objectMapper) {
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
                    .status(CampaignStatus.DRAFT)
                    .createdAt(now)
                    .updatedAt(now)
                    .createdBy(ctx.getActor().getPrincipalId())
                    .build();

                return repository.save(campaign)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx.withIdempotencyKey(DmIdempotencyKey.forCommand("CreateCampaign", saved.getId())),
                        saved.getId(),
                        "create",
                        Map.of("campaignName", saved.getName(), "type", saved.getType().name())
                    ).map(auditId -> {
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
                return repository.findById(ctx.getWorkspaceId(), campaignId);
            })
            .then(optCampaign -> Promise.of(optCampaign.orElseThrow(
                () -> new NoSuchElementException("Campaign not found: " + campaignId)
            )))
            .then(campaign -> runCompliancePreflight(ctx, campaign))
            .then(campaign -> runLaunchRiskEvaluation(ctx, campaign))
            .then(campaign -> {
                Campaign launched = campaign.launch();
                return repository.save(launched)
                    .then(saved -> issueGoogleAdsCommandIfNeeded(ctx, saved)
                        .then(ignored -> kernelAdapter.recordAudit(
                            ctx,
                            saved.getId(),
                            "launch",
                            Map.of(
                                "previousStatus", campaign.getStatus().name(),
                                "riskModel", CAMPAIGN_LAUNCH_RISK_MODEL,
                                "campaignType", saved.getType().name()
                            )
                        ))
                    .then(auditId -> kernelAdapter.notifyUser(
                        ctx,
                        ctx.getActor().getPrincipalId(),
                        "dmos.campaign.launched",
                        Map.of("campaignId", saved.getId(), "campaignName", saved.getName())
                    ).map(ignored -> {
                        metrics.increment(DmosMetricsCollector.CAMPAIGN_LAUNCHED, Map.of(
                            "tenantId",    ctx.getTenantId().getValue(),
                            "workspaceId", ctx.getWorkspaceId().getValue(),
                            "campaignType", saved.getType().name()
                        ));
                        LOG.info("[DMOS] Campaign launched: id={}, tenant={}, type={}",
                            saved.getId(), ctx.getTenantId().getValue(), saved.getType().name());
                        return saved;
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
                return repository.findById(ctx.getWorkspaceId(), campaignId);
            })
            .then(optCampaign -> Promise.of(optCampaign.orElseThrow(
                () -> new NoSuchElementException("Campaign not found: " + campaignId)
            )))
            .then(campaign -> {
                Campaign paused = campaign.pause();
                return repository.save(paused)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx,
                        saved.getId(),
                        "pause",
                        Map.of("previousStatus", campaign.getStatus().name())
                    ).map(auditId -> {
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
                return repository.findById(ctx.getWorkspaceId(), campaignId);
            })
            .then(optCampaign -> Promise.of(optCampaign.orElseThrow(
                () -> new NoSuchElementException("Campaign not found: " + campaignId)
            )));
    }

    @Override
    public Promise<List<Campaign>> listCampaigns(DmOperationContext ctx, int limit, int offset) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        return kernelAdapter.isAuthorized(ctx, "campaigns/*", "read")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException(
                        "Not authorized to list campaigns in workspace " + ctx.getWorkspaceId().getValue()
                    ));
                }
                return repository.listByWorkspace(ctx.getWorkspaceId(), limit, offset);
            })
            .then(campaigns -> {
                LOG.info("[DMOS] Listed {} campaigns for tenant={}, workspace={}",
                    campaigns.size(), ctx.getTenantId().getValue(), ctx.getWorkspaceId().getValue());
                return campaigns;
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
    private Promise<Void> issueGoogleAdsCommandIfNeeded(DmOperationContext ctx, Campaign campaign) {
        // Only PAID_SEARCH campaigns need Google Ads external creation
        if (campaign.getType() != CampaignType.PAID_SEARCH) {
            LOG.debug("[DMOS] Campaign {} is type {}, skipping Google Ads command",
                campaign.getId(), campaign.getType());
            return Promise.of(null);
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
                    // Record audit but don't fail the launch - campaign is launched internally
                    return killSwitchService.recordKillSwitchAudit(
                            ctx.getTenantId().getValue(),
                            ctx.getWorkspaceId().getValue(),
                            DmKillSwitchService.Features.GOOGLE_ADS_PUBLISH,
                            true,
                            ctx.getCorrelationId().getValue()
                        ).map(ignored -> null);
                }

                // P1-023: Issue Google Ads campaign creation command to outbox
                LOG.info("[DMOS] Issuing GOOGLE_ADS_CAMPAIGN_CREATE command for campaignId={}",
                    campaign.getId());

                try {
                    // Build command payload
                    Map<String, Object> payload = Map.of(
                        "internalCampaignId", campaign.getId(),
                        "campaignName", campaign.getName(),
                        "dailyBudget", "50.00", // Default budget, should come from campaign config
                        "serviceArea", "US", // Default, should come from targeting config
                        "keywordTheme", "general" // Default, should come from strategy
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
                            return Promise.<Void>of(null);
                        });
                } catch (JsonProcessingException e) {
                    LOG.error("[DMOS] Failed to serialize Google Ads command payload: {}", e.getMessage(), e);
                    // Don't fail the campaign launch, but log the error
                    return Promise.<Void>of(null);
                }
            });
    }
}
