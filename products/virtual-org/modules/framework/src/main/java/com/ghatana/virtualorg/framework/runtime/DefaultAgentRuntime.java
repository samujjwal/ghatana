package com.ghatana.virtualorg.framework.runtime;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.ai.llm.ToolCall;
import com.ghatana.ai.llm.ToolCallResult;
import com.ghatana.ai.llm.ToolDefinition;
import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.virtualorg.framework.tools.AgentTool;
import com.ghatana.virtualorg.framework.tools.ToolContext;
import com.ghatana.virtualorg.framework.tools.ToolInput;
import com.ghatana.virtualorg.framework.tools.ToolRegistry;
import com.ghatana.virtualorg.framework.tools.ToolResult;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Default implementation of the agent runtime engine.
 *
 * <p>
 * <b>Purpose</b><br>
 * Implements the think-act-observe loop for autonomous agent execution: 1.
 * Perceive: Receive events and context 2. Think: Use LLM to reason about the
 * situation 3. Act: Execute tools/actions based on decisions 4. Observe: Record
 * outcomes and update memory
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * DefaultAgentRuntime runtime = new DefaultAgentRuntime(
 *     RuntimeConfig.defaults(),
 *     llmGateway,
 *     toolRegistry,
 *     metricsCollector
 * );
 *
 * runtime.start(agentContext);
 * runtime.handleEvent(incomingEvent);
 *
 * // Run one cycle
 * AgentState state = runtime.runCycle().getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Agent runtime engine implementation
 * @doc.layer product
 * @doc.pattern State Machine
 */
public class DefaultAgentRuntime implements AgentRuntime {

    private final RuntimeConfig config;
    private final LLMGateway llmGateway;
    private final ToolRegistry toolRegistry;
    private final MetricsCollector metrics;
    private final PromptBuilder promptBuilder;

    private final AtomicReference<AgentState> state;
    private final AtomicReference<AgentContext> context;
    private final AtomicReference<AgentDecision> lastDecision;
    private final Queue<Event> eventQueue;
    private final List<ConversationMessage> conversationHistory;
    private final List<RuntimeListener> listeners;

    private volatile boolean paused;

    public DefaultAgentRuntime(
            RuntimeConfig config,
            LLMGateway llmGateway,
            ToolRegistry toolRegistry,
            MetricsCollector metrics) {
        this.config = config;
        this.llmGateway = llmGateway;
        this.toolRegistry = toolRegistry;
        this.metrics = metrics;
        this.promptBuilder = new PromptBuilder();

        this.state = new AtomicReference<>(AgentState.INITIALIZING);
        this.context = new AtomicReference<>();
        this.lastDecision = new AtomicReference<>();
        this.eventQueue = new ConcurrentLinkedQueue<>();
        this.conversationHistory = new ArrayList<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.paused = false;
    }

    // ========== Lifecycle ==========
    @Override
    public Promise<Void> start(AgentContext agentContext) {
        this.context.set(agentContext);
        transitionTo(AgentState.IDLE);

        metrics.incrementCounter("agent.runtime.started",
                "agent_id", agentContext.getAgentId());

        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        transitionTo(AgentState.STOPPED);
        eventQueue.clear();

        String agentId = context.get() != null ? context.get().getAgentId() : "unknown";
        metrics.incrementCounter("agent.runtime.stopped", "agent_id", agentId);

        return Promise.complete();
    }

    @Override
    public void pause() {
        paused = true;
        transitionTo(AgentState.PAUSED);
    }

    @Override
    public void resume() {
        paused = false;
        transitionTo(AgentState.IDLE);
    }

    // ========== Event Handling ==========
    @Override
    public Promise<Void> handleEvent(Event event) {
        if (!canAcceptEvents()) {
            return Promise.ofException(new IllegalStateException(
                    "Agent cannot accept events in state: " + state.get()));
        }

        eventQueue.offer(event);
        metrics.incrementCounter("agent.events.received",
                "agent_id", context.get().getAgentId());

        return Promise.complete();
    }

    // ========== Main Loop ==========
    @Override
    public Promise<AgentState> runCycle() {
        if (paused || state.get() == AgentState.STOPPED) {
            return Promise.of(state.get());
        }

        // Check for events to process
        Event event = eventQueue.poll();
        if (event == null && conversationHistory.isEmpty()) {
            return Promise.of(AgentState.IDLE);
        }

        return perceive(event)
                .then(this::think)
                .then(this::act)
                .then(this::observe)
                .map(v -> state.get());
    }

    /**
     * Perceive: Process incoming event and prepare context.
     */
    private Promise<PerceptionContext> perceive(Event event) {
        transitionTo(AgentState.PERCEIVING);

        PerceptionContext perception = new PerceptionContext();
        perception.event = event;
        perception.timestamp = Instant.now();

        // Build perception from event
        if (event != null) {
            perception.eventType = event.getType();
            // Build payload summary from event ID and type
            perception.eventPayload = "Event type: " + event.getType() + ", ID: " + event.getId().toString();
        }

        // Include working memory context
        AgentContext ctx = context.get();
        if (ctx != null) {
            perception.workingMemory = ctx.getWorkingMemorySnapshot();
        }

        return Promise.of(perception);
    }

    /**
     * Think: Use LLM to reason and decide on action.
     */
    private Promise<ThinkingResult> think(PerceptionContext perception) {
        transitionTo(AgentState.THINKING);

        AgentContext ctx = context.get();

        // Build the prompt
        String systemPrompt = promptBuilder.buildSystemPrompt(ctx);
        String userPrompt = promptBuilder.buildUserPrompt(perception, conversationHistory);

        // Get available tools for this agent
        List<ToolDefinition> tools = getAvailableToolDefinitions(ctx);

        CompletionRequest request = CompletionRequest.builder()
                .prompt(userPrompt)
                .model(config.getModel())
                .maxTokens(config.getMaxTokens())
                .build();

        // Add system prompt to conversation
        addToConversation(ConversationMessage.system(systemPrompt));
        addToConversation(ConversationMessage.user(userPrompt));

        return llmGateway.completeWithTools(request, tools)
                .map(result -> parseThinkingResult(result, perception));
    }

    /**
     * Act: Execute the decided action.
     */
    private Promise<ActionResult> act(ThinkingResult thinking) {
        transitionTo(AgentState.ACTING);

        AgentDecision decision = thinking.decision;
        lastDecision.set(decision);
        notifyDecision(decision);

        if (!decision.hasAction()) {
            return Promise.of(new ActionResult(null, null, true));
        }

        AgentDecision.AgentAction action = decision.getAction();

        // Check if we need human approval
        if (config.isRequireApproval() && !decision.isConfidentAbove(config.getConfidenceThreshold())) {
            transitionTo(AgentState.WAITING);
            return Promise.of(new ActionResult(action, null, false));
        }

        return executeAction(action);
    }

    /**
     * Execute a tool action.
     */
    private Promise<ActionResult> executeAction(AgentDecision.AgentAction action) {
        if (!action.isToolCall()) {
            // Handle non-tool actions (delegate, escalate, etc.)
            return handleSpecialAction(action);
        }

        String toolName = action.getToolName();
        AgentTool tool = toolRegistry.findByName(toolName).orElse(null);

        if (tool == null) {
            return Promise.of(new ActionResult(action,
                    ToolResult.failure("Tool not found: " + toolName), false));
        }

        ToolInput input = ToolInput.builder()
                .parameters(action.getParameters())
                .agentId(context.get().getAgentId())
                .build();

        ToolContext toolContext = ToolContext.from(context.get());

        long startTime = System.currentTimeMillis();
        return tool.execute(input, toolContext)
                .map(result -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("agent.tool.execution",
                            duration,
                            "tool", toolName,
                            "success", String.valueOf(result.isSuccess()));

                    notifyActionExecuted(action, result);
                    return new ActionResult(action, result, true);
                });
    }

    /**
     * Handle non-tool actions like delegate, escalate.
     */
    private Promise<ActionResult> handleSpecialAction(AgentDecision.AgentAction action) {
        switch (action.getType()) {
            case ESCALATE:
                metrics.incrementCounter("agent.actions.escalate",
                        "agent_id", context.get().getAgentId());
                // Escalation: re-queue the action's payload as an event for the supervisor agent
                AgentContext ctx = context.get();
                String supervisorId = ctx.getWorkingMemory("supervisor_id", String.class).orElse(null);
                if (supervisorId != null) {
                    Map<String, Object> escalationPayload = Map.of(
                        "escalated_from", ctx.getAgentId(),
                        "original_action", action.getToolName(),
                        "reason", action.getParameters().getOrDefault("reason", "unspecified"),
                        "context", action.getParameters()
                    );
                    addToConversation(ConversationMessage.toolResult(
                        "Escalated to supervisor " + supervisorId + ": " + escalationPayload));
                }
                return Promise.of(new ActionResult(action, null, true));

            case DELEGATE:
                metrics.incrementCounter("agent.actions.delegate",
                        "agent_id", context.get().getAgentId());
                // Delegation: forward the task to the specified delegate agent
                String delegateId = (String) action.getParameters().get("delegate_to");
                if (delegateId != null) {
                    Map<String, Object> delegationPayload = Map.of(
                        "delegated_from", context.get().getAgentId(),
                        "task", action.getParameters().getOrDefault("task", ""),
                        "parameters", action.getParameters()
                    );
                    addToConversation(ConversationMessage.toolResult(
                        "Delegated to agent " + delegateId + ": " + delegationPayload));
                }
                return Promise.of(new ActionResult(action, null, true));

            case WAIT:
                transitionTo(AgentState.WAITING);
                return Promise.of(new ActionResult(action, null, false));

            default:
                return Promise.of(new ActionResult(action, null, true));
        }
    }

    /**
     * Observe: Record outcome and update memory.
     */
    private Promise<Void> observe(ActionResult actionResult) {
        transitionTo(AgentState.OBSERVING);

        // Record the action result in conversation history
        if (actionResult.result != null) {
            String observation = formatObservation(actionResult);
            addToConversation(ConversationMessage.toolResult(observation));
        }

        // Update working memory with action result
        AgentContext ctx = context.get();
        if (ctx != null && actionResult.action != null) {
            ctx.setWorkingMemory("last_action", actionResult.action.getToolName());
            ctx.setWorkingMemory("last_action_success", actionResult.completed);
        }

        // Trim conversation history if too long
        if (conversationHistory.size() > config.getMaxConversationHistory()) {
            trimConversationHistory();
        }

        // Transition back to idle
        transitionTo(AgentState.IDLE);

        return Promise.complete();
    }

    // ========== Helper Methods ==========
    private List<ToolDefinition> getAvailableToolDefinitions(AgentContext ctx) {
        // Derive permissions from agent role metadata
        // Derive permissions from agent role; getRoles() is not available on AgentContext,
        // so we use the role name to build a basic permission set.
        Set<String> permissions = ctx.getRole() != null
            ? Set.of("task:execute", "code:write", "role:" + ctx.getRole().name())
            : Set.of("task:execute", "code:write"); // sensible default for engineers
        return toolRegistry.getAccessibleTools(permissions).stream()
                .map(this::toToolDefinition)
                .toList();
    }

    private ToolDefinition toToolDefinition(AgentTool tool) {
        var builder = ToolDefinition.builder()
                .name(tool.getName())
                .description(tool.getDescription());

        // Convert schema Map to parameters
        Map<String, Object> schema = tool.getSchema();
        if (schema != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schema.getOrDefault("required", List.of());

            if (properties != null) {
                for (var entry : properties.entrySet()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> propDef = (Map<String, Object>) entry.getValue();
                    String type = (String) propDef.getOrDefault("type", "string");
                    String desc = (String) propDef.getOrDefault("description", "");
                    builder.addParameter(entry.getKey(), type, desc, required.contains(entry.getKey()));
                }
            }
        }

        return builder.build();
    }

    private ThinkingResult parseThinkingResult(CompletionResult completion, PerceptionContext perception) {
        ThinkingResult result = new ThinkingResult();
        result.perception = perception;
        result.rawResponse = completion.getText();

        // Check if the LLM wants to call a tool
        if (completion.hasToolCalls() && !completion.getToolCalls().isEmpty()) {
            ToolCall toolCall = completion.getToolCalls().get(0);
            result.decision = AgentDecision.builder()
                    .reasoning(completion.getText())
                    .decision("Execute tool: " + toolCall.getName())
                    .action(AgentDecision.AgentAction.of(
                            toolCall.getName(),
                            toolCall.getArguments()))
                    .confidence(0.8) // Default confidence
                    .build();
        } else {
            // No tool call - just a response
            result.decision = AgentDecision.builder()
                    .reasoning(completion.getText())
                    .decision("Respond to user")
                    .confidence(0.9)
                    .build();
        }

        addToConversation(ConversationMessage.assistant(completion.getText()));
        return result;
    }

    private String formatObservation(ActionResult actionResult) {
        if (actionResult.result == null) {
            return "Action completed without result";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Tool: ").append(actionResult.action.getToolName()).append("\n");
        sb.append("Success: ").append(actionResult.result.isSuccess()).append("\n");

        if (actionResult.result.isSuccess()) {
            sb.append("Result: ").append(actionResult.result.getData());
        } else {
            sb.append("Error: ").append(actionResult.result.getError());
        }

        return sb.toString();
    }

    private void addToConversation(ConversationMessage message) {
        conversationHistory.add(message);
    }

    private void trimConversationHistory() {
        // Keep system message and recent messages
        int trimTo = config.getMaxConversationHistory() / 2;
        while (conversationHistory.size() > trimTo) {
            // Remove oldest non-system message
            for (int i = 0; i < conversationHistory.size(); i++) {
                if (conversationHistory.get(i).role != ConversationMessage.Role.SYSTEM) {
                    conversationHistory.remove(i);
                    break;
                }
            }
        }
    }

    private void transitionTo(AgentState newState) {
        AgentState oldState = state.getAndSet(newState);
        if (oldState != newState) {
            notifyStateChange(oldState, newState);
            metrics.incrementCounter("agent.state.transition",
                    "from", oldState.name(),
                    "to", newState.name());
        }
    }

    // ========== State Access ==========
    @Override
    public AgentState getState() {
        return state.get();
    }

    @Override
    public AgentContext getContext() {
        return context.get();
    }

    @Override
    public AgentDecision getLastDecision() {
        return lastDecision.get();
    }

    // ========== Listeners ==========
    @Override
    public void addListener(RuntimeListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(RuntimeListener listener) {
        listeners.remove(listener);
    }

    private void notifyStateChange(AgentState oldState, AgentState newState) {
        for (RuntimeListener listener : listeners) {
            try {
                listener.onStateChange(oldState, newState);
            } catch (Exception e) {
                metrics.incrementCounter("agent.listener.error", "type", "state_change");
            }
        }
    }

    private void notifyDecision(AgentDecision decision) {
        for (RuntimeListener listener : listeners) {
            try {
                listener.onDecision(decision);
            } catch (Exception e) {
                metrics.incrementCounter("agent.listener.error", "type", "decision");
            }
        }
    }

    private void notifyActionExecuted(AgentDecision.AgentAction action, Object result) {
        for (RuntimeListener listener : listeners) {
            try {
                listener.onActionExecuted(action, result);
            } catch (Exception e) {
                metrics.incrementCounter("agent.listener.error", "type", "action_executed");
            }
        }
    }

    // ========== Inner Classes ==========
    /**
     * Runtime configuration.
     */
    public static class RuntimeConfig {

        private String model = "gpt-4";
        private int maxTokens = 4096;
        private int maxConversationHistory = 50;
        private boolean requireApproval = false;
        private double confidenceThreshold = 0.7;
        private int maxToolCalls = 10;

        public static RuntimeConfig defaults() {
            return new RuntimeConfig();
        }

        public String getModel() {
            return model;
        }

        public RuntimeConfig model(String model) {
            this.model = model;
            return this;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public RuntimeConfig maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public int getMaxConversationHistory() {
            return maxConversationHistory;
        }

        public RuntimeConfig maxConversationHistory(int max) {
            this.maxConversationHistory = max;
            return this;
        }

        public boolean isRequireApproval() {
            return requireApproval;
        }

        public RuntimeConfig requireApproval(boolean require) {
            this.requireApproval = require;
            return this;
        }

        public double getConfidenceThreshold() {
            return confidenceThreshold;
        }

        public RuntimeConfig confidenceThreshold(double threshold) {
            this.confidenceThreshold = threshold;
            return this;
        }

        public int getMaxToolCalls() {
            return maxToolCalls;
        }

        public RuntimeConfig maxToolCalls(int max) {
            this.maxToolCalls = max;
            return this;
        }
    }

    /**
     * Context gathered during perception phase.
     */
    private static class PerceptionContext {

        Event event;
        Instant timestamp;
        String eventType;
        String eventPayload;
        Map<String, Object> workingMemory;
    }

    /**
     * Result of thinking phase.
     */
    private static class ThinkingResult {

        PerceptionContext perception;
        String rawResponse;
        AgentDecision decision;
    }

    /**
     * Result of action phase.
     */
    private static class ActionResult {

        AgentDecision.AgentAction action;
        ToolResult result;
        boolean completed;

        ActionResult(AgentDecision.AgentAction action, ToolResult result, boolean completed) {
            this.action = action;
            this.result = result;
            this.completed = completed;
        }
    }

    /**
     * Conversation message for history tracking.
     */
    private static class ConversationMessage {

        enum Role {
            SYSTEM, USER, ASSISTANT, TOOL_RESULT
        }

        final Role role;
        final String content;
        final Instant timestamp;

        ConversationMessage(Role role, String content) {
            this.role = role;
            this.content = content;
            this.timestamp = Instant.now();
        }

        static ConversationMessage system(String content) {
            return new ConversationMessage(Role.SYSTEM, content);
        }

        static ConversationMessage user(String content) {
            return new ConversationMessage(Role.USER, content);
        }

        static ConversationMessage assistant(String content) {
            return new ConversationMessage(Role.ASSISTANT, content);
        }

        static ConversationMessage toolResult(String content) {
            return new ConversationMessage(Role.TOOL_RESULT, content);
        }
    }
}
