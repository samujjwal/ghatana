package com.ghatana.digitalmarketing.application.campaign;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.campaign.Campaign;
import com.ghatana.digitalmarketing.domain.campaign.CampaignStatus;
import com.ghatana.plugin.compliance.CompliancePlugin;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
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

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final CampaignRepository repository;
    private final CompliancePlugin compliancePlugin;
    private final CampaignPreflightDataProvider preflightDataProvider;

    /**
     * Constructs the campaign service.
     *
     * @param kernelAdapter          DMOS kernel adapter for auth, consent, approval, audit
     * @param repository             campaign persistence
     * @param compliancePlugin       compliance evaluation for preflight checks
     * @param preflightDataProvider  provider for campaign preflight evidence
     */
    public CampaignServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            CampaignRepository repository,
            CompliancePlugin compliancePlugin,
            CampaignPreflightDataProvider preflightDataProvider) {
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.compliancePlugin = Objects.requireNonNull(compliancePlugin, "compliancePlugin must not be null");
        this.preflightDataProvider = Objects.requireNonNull(
            preflightDataProvider,
            "preflightDataProvider must not be null"
        );
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
            .then(campaign -> {
                Campaign launched = campaign.launch();
                return repository.save(launched)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx,
                        saved.getId(),
                        "launch",
                        Map.of("previousStatus", campaign.getStatus().name())
                    ).map(auditId -> {
                        LOG.info("[DMOS] Campaign launched: id={}, tenant={}",
                            saved.getId(), ctx.getTenantId().getValue());
                        return saved;
                    }));
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
                    return Promise.ofException(new CampaignComplianceViolationException(
                        "Campaign " + campaign.getId() + " failed preflight compliance: "
                        + result.violations()
                    ));
                }
                return Promise.of(campaign);
            });
    }
}
