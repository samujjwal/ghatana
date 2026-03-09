package com.ghatana.products.yappc.domain.agent;

import com.ghatana.ai.llm.ChatMessage;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Copilot Agent for conversational AI assistance.
 * <p>
 * Handles natural language interactions, command interpretation,
 * and action execution in the DevSecOps platform.
 * <p>
 * <b>Responsibilities:</b>
 * <ul>
 *   <li>Conversational AI for user assistance</li>
 *   <li>Command interpretation and action suggestion</li>
 *   <li>Context-aware responses based on current view</li>
 *   <li>Multi-turn conversation support</li>
 * </ul>
 * <p>
 * <b>Models:</b> GPT-4, Claude-3 (configurable)
 * <p>
 * <b>Latency SLA:</b> 2000ms (streaming supported)
 *
 * @doc.type class
 * @doc.purpose Conversational AI copilot agent
 * @doc.layer product
 * @doc.pattern AIAgent
 */
public class CopilotAgent extends AbstractAIAgent<CopilotInput, CopilotOutput> {

    private static final Logger LOG = LoggerFactory.getLogger(CopilotAgent.class);

    private static final String VERSION = "2.0.0";
    private static final String DESCRIPTION = "Conversational AI assistant for DevSecOps platform";
    private static final List<String> CAPABILITIES = List.of(
            "conversation",
            "command-interpretation",
            "action-execution",
            "context-awareness",
            "multi-turn-dialog"
    );
    private static final List<String> SUPPORTED_MODELS = List.of(
            "gpt-4-turbo",
            "gpt-4",
            "claude-3-opus",
            "claude-3-sonnet"
    );

    private static final String SYSTEM_PROMPT = """
            You are an AI assistant for a DevSecOps platform. You help users navigate, manage items,
            and answer questions about their projects, phases, and workflows.
            
            You can:
            - Answer questions about items, phases, milestones, and metrics
            - Help navigate to different parts of the platform
            - Suggest actions like creating, updating, or moving items
            - Provide insights based on the current context
            
            When suggesting actions, format them as structured JSON with:
            - type: 'navigate' | 'filter' | 'create' | 'update' | 'delete' | 'query'
            - parameters: relevant data for the action
            - confidence: your confidence level (0-1)
            
            Always be concise, helpful, and proactive in suggesting next steps.
            
            Current context will be provided with each message.
            """;

    private final LLMGateway llmGateway;
    private final ActionExecutor actionExecutor;

    /**
     * Creates a new CopilotAgent.
     *
     * @param llmGateway       The LLM gateway for AI completions
     * @param actionExecutor   The action executor for handling commands
     * @param metricsCollector The metrics collector
     */
    public CopilotAgent(
            @NotNull LLMGateway llmGateway,
            @NotNull ActionExecutor actionExecutor,
            @NotNull MetricsCollector metricsCollector
    ) {
        super(
                AgentName.COPILOT_AGENT,
                VERSION,
                DESCRIPTION,
                CAPABILITIES,
                SUPPORTED_MODELS,
                metricsCollector
        );
        this.llmGateway = llmGateway;
        this.actionExecutor = actionExecutor;
    }

    @Override
    public void validateInput(@NotNull CopilotInput input) {
        if (input.query() == null || input.query().isBlank()) {
            throw new IllegalArgumentException("Query cannot be empty");
        }
        if (input.currentView() == null) {
            throw new IllegalArgumentException("Current view context is required");
        }
    }

    @Override
    protected Promise<ProcessResult<CopilotOutput>> processRequest(
            @NotNull CopilotInput input,
            @NotNull AIAgentContext context
    ) {
        LOG.debug("Processing copilot request: {}", input.query());

        // Build the context-enriched prompt
        String enrichedPrompt = buildEnrichedPrompt(input, context);

        // Create chat messages
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(SYSTEM_PROMPT));
        messages.add(ChatMessage.user(enrichedPrompt));

        // Get model from preferences or default
        String model = context.preferences() != null
                ? context.preferences().preferredModel()
                : "gpt-4-turbo";

        double temperature = context.preferences() != null
                ? context.preferences().temperature()
                : 0.7;

        int maxTokens = context.preferences() != null
                ? context.preferences().maxTokens()
                : 2048;

        // Build completion request
        CompletionRequest request = CompletionRequest.builder()
                .messages(messages)
                .model(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

        // Call LLM Gateway
        return llmGateway.complete(request)
                .then(completionResult -> processCompletionResult(completionResult, input, context));
    }

    private Promise<ProcessResult<CopilotOutput>> processCompletionResult(
            CompletionResult completionResult,
            CopilotInput input,
            AIAgentContext context
    ) {
        String responseText = completionResult.getText();
        int tokensUsed = completionResult.getTokensUsed();

        // Parse action from response if present
        CopilotOutput.CopilotAction action = parseAction(responseText);

        // Execute action if present and auto-execution is enabled
        if (action != null && shouldAutoExecute(action, context)) {
            return actionExecutor.execute(action, context)
                    .map(executionResult -> {
                        CopilotOutput output = CopilotOutput.builder()
                                .response(responseText)
                                .action(action)
                                .executionResult(executionResult)
                                .suggestions(generateFollowUpSuggestions(input, action))
                                .build();

                        return ProcessResult.of(
                                output,
                                tokensUsed,
                            completionResult.getModelUsed(),
                                action.confidence()
                        );
                    });
        }

        // Return without execution
        CopilotOutput output = CopilotOutput.builder()
                .response(responseText)
                .action(action)
                .suggestions(generateFollowUpSuggestions(input, action))
                .build();

        double confidence = action != null ? action.confidence() : 0.8;

        return Promise.of(ProcessResult.of(
                output,
                tokensUsed,
            completionResult.getModelUsed(),
                confidence
        ));
    }

    private String buildEnrichedPrompt(CopilotInput input, AIAgentContext context) {
        StringBuilder sb = new StringBuilder();

        sb.append("User Query: ").append(input.query()).append("\n\n");

        sb.append("Current Context:\n");
        sb.append("- Route: ").append(input.currentView().route()).append("\n");

        if (input.currentView().phaseId() != null) {
            sb.append("- Phase: ").append(input.currentView().phaseId()).append("\n");
        }

        if (input.currentView().viewMode() != null) {
            sb.append("- View Mode: ").append(input.currentView().viewMode()).append("\n");
        }

        if (input.selectedItems() != null && !input.selectedItems().isEmpty()) {
            sb.append("- Selected Items: ").append(String.join(", ", input.selectedItems())).append("\n");
        }

        if (input.recentActions() != null && !input.recentActions().isEmpty()) {
            sb.append("\nRecent Actions:\n");
            for (CopilotInput.CopilotRecentAction action : input.recentActions()) {
                sb.append("- ").append(action.type()).append(": ")
                        .append(action.description()).append("\n");
            }
        }

        sb.append("\nUser ID: ").append(context.userId()).append("\n");
        sb.append("Workspace: ").append(context.workspaceId()).append("\n");

        return sb.toString();
    }

    private CopilotOutput.CopilotAction parseAction(String responseText) {
        // Look for JSON action blocks in the response
        // Format: ```action { "type": "...", "parameters": {...}, "confidence": 0.9 } ```
        int actionStart = responseText.indexOf("```action");
        if (actionStart == -1) {
            return null;
        }

        int jsonStart = responseText.indexOf("{", actionStart);
        int jsonEnd = responseText.indexOf("```", jsonStart);
        if (jsonStart == -1 || jsonEnd == -1) {
            return null;
        }

        String jsonStr = responseText.substring(jsonStart, jsonEnd).trim();

        // Parse JSON (simplified - in production use Jackson/Gson)
        // For now, return null if parsing fails
        try {
            // This would use proper JSON parsing
            // Return a sample action for now
            return null;
        } catch (Exception e) {
            LOG.warn("Failed to parse action from response: {}", e.getMessage());
            return null;
        }
    }

    private boolean shouldAutoExecute(CopilotOutput.CopilotAction action, AIAgentContext context) {
        // Low impact actions can auto-execute if user preferences allow
        if (action.impact() == CopilotOutput.CopilotAction.ActionImpact.LOW) {
            return context.preferences() != null &&
                    context.preferences().autoAcceptLowConfidence();
        }
        // Medium/High impact actions always require confirmation
        return false;
    }

    private List<String> generateFollowUpSuggestions(CopilotInput input, CopilotOutput.CopilotAction action) {
        List<String> suggestions = new ArrayList<>();

        // Context-based suggestions
        if (input.currentView().route().contains("/phase/")) {
            suggestions.add("Show items in this phase");
            suggestions.add("What's blocking progress?");
            suggestions.add("Predict completion date");
        } else if (input.currentView().route().contains("/item/")) {
            suggestions.add("Show related items");
            suggestions.add("What's the risk level?");
            suggestions.add("Suggest next steps");
        } else {
            suggestions.add("Show high priority items");
            suggestions.add("What needs attention?");
            suggestions.add("Generate status report");
        }

        return suggestions;
    }

    @Override
    protected Promise<Map<String, AgentHealth.DependencyStatus>> doHealthCheck() {
        return llmGateway.complete(
                CompletionRequest.builder()
                        .prompt("ping")
                        .maxTokens(1)
                        .build()
        ).map(result -> Map.of(
                "llmGateway", AgentHealth.DependencyStatus.HEALTHY,
                "actionExecutor", AgentHealth.DependencyStatus.HEALTHY
        )).mapException(e -> {
            LOG.error("Health check failed: {}", e.getMessage());
            return new RuntimeException("LLM Gateway unhealthy");
        });
    }

    /**
     * Interface for executing copilot actions.
     */
    public interface ActionExecutor {
        /**
         * Executes a copilot action.
         *
         * @param action  The action to execute
         * @param context The execution context
         * @return Promise resolving to the execution result
         */
        Promise<CopilotOutput.ActionExecutionResult> execute(
                CopilotOutput.CopilotAction action,
                AIAgentContext context
        );
    }
}
