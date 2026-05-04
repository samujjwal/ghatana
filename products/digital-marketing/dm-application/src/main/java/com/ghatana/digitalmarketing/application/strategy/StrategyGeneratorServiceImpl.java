package com.ghatana.digitalmarketing.application.strategy;

import com.ghatana.digitalmarketing.application.DmosFeatureFlags;
import com.ghatana.digitalmarketing.application.ai.DmAgentOrchestrationPort;
import com.ghatana.digitalmarketing.application.ai.GovernedAgentWorkflowService;
import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.domain.DmosFeatureDisabledException;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.strategy.CampaignPlan;
import com.ghatana.digitalmarketing.domain.strategy.MarketingStrategy;
import com.ghatana.digitalmarketing.domain.strategy.StrategyChannel;
import com.ghatana.digitalmarketing.domain.strategy.StrategyGoal;
import com.ghatana.digitalmarketing.domain.strategy.StrategyStatus;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic 30-day marketing strategy generator with governed agent workflow support (DMOS-P1-019).
 *
 * <p>Generates an MVP-scoped strategy limited to Google Search, landing page, and email follow-up
 * channels. Uses intake, audit, research, and budget signals to derive goals, campaign plans,
 * budget cap, assumptions, and a measurement plan.</p>
 *
 * <p>When AI is enabled, uses the governed agent workflow service for strategy generation with
 * AI action logging, confidence tracking, and approval routing.</p>
 *
 * @doc.type class
 * @doc.purpose Service implementation that generates and manages workspace-scoped 30-day marketing strategies (DMOS-P1-019)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class StrategyGeneratorServiceImpl implements StrategyGeneratorService {

    private static final Logger LOG = LoggerFactory.getLogger(StrategyGeneratorServiceImpl.class);

    static final String MODEL_VERSION = "v1.0";

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final MarketingStrategyRepository repository;
    private final GovernedAgentWorkflowService governedWorkflowService;

    public StrategyGeneratorServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            MarketingStrategyRepository repository,
            GovernedAgentWorkflowService governedWorkflowService) {
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.governedWorkflowService = governedWorkflowService; // Optional for governed AI workflow
    }

    public StrategyGeneratorServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            MarketingStrategyRepository repository) {
        this(kernelAdapter, repository, null);
    }

    @Override
    public Promise<MarketingStrategy> generateStrategy(
            DmOperationContext ctx,
            StrategyGeneratorService.GenerateStrategyCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isFeatureEnabled(ctx, DmosFeatureFlags.AI_ENABLED)
            .then(aiEnabled -> {
                if (!aiEnabled) {
                    return Promise.ofException(
                        new DmosFeatureDisabledException(DmosFeatureFlags.AI_ENABLED));
                }
                return kernelAdapter.isAuthorized(ctx, "strategy", "write");
            })
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorized to generate a strategy"));
                }

                // Use governed agent workflow if available (DMOS-P1-019)
                if (governedWorkflowService != null) {
                    return generateStrategyWithGovernedWorkflow(ctx, command);
                }

                // Fallback to deterministic generation
                MarketingStrategy strategy = buildStrategy(ctx, command);
                return repository.save(strategy)
                    .then(saved -> kernelAdapter.recordAudit(ctx, saved.getStrategyId(),
                            "strategy-generated", Map.of("modelVersion", MODEL_VERSION))
                        .map(__ -> saved));
            });
    }

    /**
     * Generates strategy using governed agent workflow (DMOS-P1-019).
     */
    private Promise<MarketingStrategy> generateStrategyWithGovernedWorkflow(
            DmOperationContext ctx,
            GenerateStrategyCommand command) {
        String prompt = buildPromptFromCommand(command);
        return governedWorkflowService.executeGovernedWorkflow(
                DmAgentOrchestrationPort.AgentType.STRATEGY_GENERATOR,
                prompt,
                "gpt-4",
                Map.of(),
                ctx.getTenantId(),
                ctx.getWorkspaceId(),
                ctx.getActor().getPrincipalId()
            )
            .then(result -> {
                if (!result.success()) {
                    LOG.error("Governed workflow failed: {}", result.errorMessage());
                    // Fallback to deterministic generation on failure
                    return generateDeterministicStrategy(ctx, command);
                }

                // Parse AI output into MarketingStrategy
                MarketingStrategy strategy = parseAiOutputToStrategy(ctx, command, result);
                return repository.save(strategy)
                    .then(saved -> {
                        // If approval required, mark for approval (DMOS-P1-019)
                        if (result.approvalRequired()) {
                            return Promise.of(saved.submitForApproval());
                        }
                        return Promise.of(saved);
                    });
            });
    }

    /**
     * Generates strategy deterministically (fallback).
     */
    private Promise<MarketingStrategy> generateDeterministicStrategy(
            DmOperationContext ctx,
            GenerateStrategyCommand command) {
        MarketingStrategy strategy = buildStrategy(ctx, command);
        return repository.save(strategy)
            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getStrategyId(),
                    "strategy-generated", Map.of("modelVersion", MODEL_VERSION))
                .map(__ -> saved));
    }

    /**
     * Builds prompt from command for AI generation (DMOS-P1-019).
     */
    private String buildPromptFromCommand(GenerateStrategyCommand command) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate a 30-day marketing strategy for a local business. ");
        prompt.append("Service area: ").append(command.serviceArea()).append(". ");
        prompt.append("Primary offer: ").append(command.primaryOffer()).append(". ");
        prompt.append("Monthly budget: ").append(command.monthlyBudget()).append(". ");
        if (command.auditFindingCount() > 0) {
            prompt.append("Audit findings: ").append(command.auditFindingCount()).append(". ");
        }
        if (command.topCompetitorCount() > 0) {
            prompt.append("Top competitors: ").append(command.topCompetitorCount()).append(". ");
        }
        return prompt.toString();
    }

    /**
     * Parses AI output into MarketingStrategy (DMOS-P1-019).
     *
     * <p>Parses structured JSON output from the governed AI workflow into a MarketingStrategy.
     * If parsing fails, falls back to deterministic generation to ensure system reliability.</p>
     *
     * <p>Expected AI output format:</p>
     * <pre>
     * {
     *   "goals": [
     *     {"goalType": "lead-generation", "description": "...", "targetMetric": "...", "measurementMethod": "..."}
     *   ],
     *   "channelPlans": [
     *     {"channelType": "GOOGLE_SEARCH", "objective": "...", "estimatedBudget": 1000, "keyMessages": [...], "targetKeywords": [...]}
     *   ],
     *   "rationale": "...",
     *   "assumptions": "...",
     *   "measurementPlan": "...",
     *   "contentPlan": "..."
     * }
     * </pre>
     */
    private MarketingStrategy parseAiOutputToStrategy(
            DmOperationContext ctx,
            GenerateStrategyCommand command,
            GovernedAgentWorkflowService.GovernedWorkflowResult result) {
        try {
            String aiOutput = result.output();
            
            // Parse the AI output as JSON
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(aiOutput);
            
            // Parse goals
            List<StrategyGoal> goals = new ArrayList<>();
            com.fasterxml.jackson.databind.JsonNode goalsNode = root.get("goals");
            if (goalsNode != null && goalsNode.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode goalNode : goalsNode) {
                    goals.add(new StrategyGoal(
                        goalNode.get("goalType").asText(),
                        goalNode.get("description").asText(),
                        goalNode.get("targetMetric").asText(),
                        goalNode.get("measurementMethod").asText()
                    ));
                }
            }
            
            // Parse channel plans
            List<CampaignPlan> channelPlans = new ArrayList<>();
            com.fasterxml.jackson.databind.JsonNode plansNode = root.get("channelPlans");
            if (plansNode != null && plansNode.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode planNode : plansNode) {
                    List<String> keyMessages = new ArrayList<>();
                    com.fasterxml.jackson.databind.JsonNode messagesNode = planNode.get("keyMessages");
                    if (messagesNode != null && messagesNode.isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode msgNode : messagesNode) {
                            keyMessages.add(msgNode.asText());
                        }
                    }
                    
                    List<String> targetKeywords = new ArrayList<>();
                    com.fasterxml.jackson.databind.JsonNode keywordsNode = planNode.get("targetKeywords");
                    if (keywordsNode != null && keywordsNode.isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode kwNode : keywordsNode) {
                            targetKeywords.add(kwNode.asText());
                        }
                    }
                    
                    channelPlans.add(new CampaignPlan(
                        StrategyChannel.valueOf(planNode.get("channelType").asText()),
                        planNode.get("objective").asText(),
                        planNode.get("estimatedBudget").asInt(),
                        keyMessages,
                        targetKeywords
                    ));
                }
            }
            
            // Parse text fields with fallbacks
            String rationale = root.has("rationale") ? root.get("rationale").asText() : buildRationale(command);
            String assumptions = root.has("assumptions") ? root.get("assumptions").asText() : buildAssumptions(command);
            String measurementPlan = root.has("measurementPlan") ? root.get("measurementPlan").asText() : buildMeasurementPlan(command);
            String contentPlan = root.has("contentPlan") ? root.get("contentPlan").asText() : buildContentPlan(command);
            
            // Use AI budget if provided, otherwise use command budget
            int budgetCap = root.has("budgetCap") ? root.get("budgetCap").asInt() : command.monthlyBudget();
            
            // Build the strategy from parsed AI output
            DmWorkspaceId workspaceId = ctx.getWorkspaceId();
            String strategyId = "strat-" + UUID.randomUUID();
            
            MarketingStrategy strategy = MarketingStrategy.builder()
                .strategyId(strategyId)
                .workspaceId(workspaceId)
                .status(StrategyStatus.DRAFT)
                .goals(goals.isEmpty() ? deriveGoals(command) : goals)
                .channelPlans(channelPlans.isEmpty() ? buildChannelPlans(command) : channelPlans)
                .budgetCap(budgetCap)
                .rationale(rationale)
                .assumptions(assumptions)
                .measurementPlan(measurementPlan)
                .contentPlan(contentPlan)
                .modelVersion(MODEL_VERSION + "-ai")
                .generatedAt(Instant.now())
                .generatedBy(ctx.getActor().getPrincipalId())
                .approvedAt(null)
                .approvedBy(null)
                .build();
            
            return strategy;
            
        } catch (Exception e) {
            LOG.debug("Failed to parse AI output, falling back to deterministic generation: {}", e.getMessage());
            return buildStrategy(ctx, command);
        }
    }

    @Override
    public Promise<MarketingStrategy> getLatestStrategy(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        return kernelAdapter.isAuthorized(ctx, "strategy", "read")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorized to read the strategy"));
                }
                return repository.findLatestByWorkspace(ctx.getWorkspaceId())
                    .map(opt -> opt.orElseThrow(
                        () -> new NoSuchElementException("No strategy found for workspace: " + ctx.getWorkspaceId().getValue())));
            });
    }

    @Override
    public Promise<MarketingStrategy> submitForApproval(DmOperationContext ctx, String strategyId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(strategyId, "strategyId must not be null");

        return kernelAdapter.isAuthorized(ctx, "strategy", "write")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorized to submit strategy for approval"));
                }
                return repository.findById(ctx.getWorkspaceId(), strategyId)
                    .then(opt -> {
                        MarketingStrategy strategy = opt.orElseThrow(
                            () -> new NoSuchElementException("Strategy not found: " + strategyId));
                        MarketingStrategy pending = strategy.submitForApproval();
                        return repository.save(pending);
                    });
            });
    }

    @Override
    public Promise<MarketingStrategy> approveStrategy(DmOperationContext ctx, String strategyId) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(strategyId, "strategyId must not be null");

        return kernelAdapter.isAuthorized(ctx, "strategy", "approve")
            .then(authorized -> {
                if (!authorized) {
                    return Promise.ofException(new SecurityException("Actor not authorized to approve strategy"));
                }
                return repository.findById(ctx.getWorkspaceId(), strategyId)
                    .then(opt -> {
                        MarketingStrategy strategy = opt.orElseThrow(
                            () -> new NoSuchElementException("Strategy not found: " + strategyId));
                        String approverIdentity = ctx.getActor().getPrincipalId();
                        MarketingStrategy approved = strategy.approve(approverIdentity, Instant.now());
                        return repository.save(approved)
                            .then(saved -> kernelAdapter.recordAudit(ctx, saved.getStrategyId(),
                                    "strategy-approved",
                                    Map.of("approvedBy", approverIdentity))
                                .map(__ -> saved));
                    });
            });
    }

    private MarketingStrategy buildStrategy(DmOperationContext ctx, GenerateStrategyCommand cmd) {
        DmWorkspaceId workspaceId = ctx.getWorkspaceId();
        String strategyId = "strat-" + UUID.randomUUID();

        List<StrategyGoal> goals = deriveGoals(cmd);
        List<CampaignPlan> channelPlans = buildChannelPlans(cmd);
        int budgetCap = cmd.monthlyBudget();
        String rationale = buildRationale(cmd);
        String assumptions = buildAssumptions(cmd);
        String measurementPlan = buildMeasurementPlan(cmd);
        String contentPlan = buildContentPlan(cmd);

        return MarketingStrategy.builder()
            .strategyId(strategyId)
            .workspaceId(workspaceId)
            .status(StrategyStatus.DRAFT)
            .goals(goals)
            .channelPlans(channelPlans)
            .budgetCap(budgetCap)
            .rationale(rationale)
            .assumptions(assumptions)
            .measurementPlan(measurementPlan)
            .contentPlan(contentPlan)
            .modelVersion(MODEL_VERSION)
            .generatedAt(Instant.now())
            .generatedBy(ctx.getActor().getPrincipalId())
            .approvedAt(null)
            .approvedBy(null)
            .build();
    }

    private List<StrategyGoal> deriveGoals(GenerateStrategyCommand cmd) {
        List<StrategyGoal> goals = new ArrayList<>();
        goals.add(new StrategyGoal(
            "lead-generation",
            "Generate qualified leads for " + cmd.serviceArea() + " from Google Search",
            "leads per 30 days",
            "CRM new contact count"
        ));
        if (cmd.trackingGapsDetected()) {
            goals.add(new StrategyGoal(
                "tracking-setup",
                "Close all tracking gaps to enable accurate attribution",
                "GA4 + conversion events configured",
                "Audit checklist completion"
            ));
        }
        if (cmd.keywordOpportunityCount() > 0) {
            goals.add(new StrategyGoal(
                "keyword-targeting",
                "Capture keyword opportunity traffic in " + cmd.serviceArea(),
                cmd.keywordOpportunityCount() + " opportunities targeted",
                "Impressions and clicks per keyword"
            ));
        }
        return goals;
    }

    private List<CampaignPlan> buildChannelPlans(GenerateStrategyCommand cmd) {
        List<CampaignPlan> plans = new ArrayList<>();
        int googleBudget = (int) (cmd.monthlyBudget() * 0.70);
        int landingBudget = (int) (cmd.monthlyBudget() * 0.20);
        int emailBudget = cmd.monthlyBudget() - googleBudget - landingBudget;

        plans.add(new CampaignPlan(
            StrategyChannel.GOOGLE_SEARCH,
            "Drive high-intent leads for " + cmd.primaryOffer() + " in " + cmd.serviceArea(),
            googleBudget,
            List.of("Best " + cmd.primaryOffer() + " in " + cmd.serviceArea(),
                "Trusted local provider"),
            List.of(cmd.primaryOffer() + " near me",
                cmd.primaryOffer() + " " + cmd.serviceArea())
        ));

        plans.add(new CampaignPlan(
            StrategyChannel.LANDING_PAGE,
            "Convert search traffic to leads with a focused landing page",
            landingBudget,
            List.of("One clear call to action", "Social proof from local customers"),
            List.of()
        ));

        plans.add(new CampaignPlan(
            StrategyChannel.EMAIL_FOLLOW_UP,
            "Nurture leads captured from landing page with a 3-step sequence",
            emailBudget,
            List.of("Welcome + introduction", "Case study / proof", "Call to action offer"),
            List.of()
        ));

        return plans;
    }

    private String buildRationale(GenerateStrategyCommand cmd) {
        StringBuilder sb = new StringBuilder();
        sb.append("Google Search is the highest-intent channel for local services in ")
            .append(cmd.serviceArea())
            .append(". ");
        if (cmd.auditFindingCount() > 0) {
            sb.append(cmd.auditFindingCount())
                .append(" audit findings identified requiring remediation. ");
        }
        if (cmd.topCompetitorCount() > 0) {
            sb.append(cmd.topCompetitorCount())
                .append(" competitors identified — differentiation messaging is critical. ");
        }
        if (cmd.intakeCompletionPct() < 80) {
            sb.append("Intake is incomplete; assumptions may need revision after full completion. ");
        }
        return sb.toString().trim();
    }

    private String buildAssumptions(GenerateStrategyCommand cmd) {
        List<String> items = new ArrayList<>();
        items.add("Client approves strategy before any execution commands are created.");
        items.add("Landing page content will be provided by client within 5 business days.");
        items.add("Monthly budget cap of " + cmd.monthlyBudget() + " will not be exceeded without re-approval.");
        if (cmd.trackingGapsDetected()) {
            items.add("Tracking gaps will be resolved before campaign launch.");
        }
        if (cmd.intakeCompletionPct() < 100) {
            items.add("Intake questionnaire will be completed before execution begins.");
        }
        return String.join(" | ", items);
    }

    private String buildMeasurementPlan(GenerateStrategyCommand cmd) {
        return "Weekly: impressions, clicks, CTR, conversion rate, CPC. "
            + "Monthly: leads generated, CPL, landing page bounce rate. "
            + "Attribution: UTM parameters + GA4 goal completions.";
    }

    private String buildContentPlan(GenerateStrategyCommand cmd) {
        return "Week 1–2: Set up 2 Google Search ad variants for " + cmd.primaryOffer()
            + ". Week 3: Publish landing page. Week 4: Activate 3-step email follow-up sequence.";
    }
}
