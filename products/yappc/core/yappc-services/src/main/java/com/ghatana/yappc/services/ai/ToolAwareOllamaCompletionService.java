/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC AI Service Module
 */
package com.ghatana.yappc.services.ai;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.LLMConfiguration;
import com.ghatana.ai.llm.OllamaCompletionService;
import com.ghatana.ai.llm.ToolAwareCompletionService;
import com.ghatana.ai.llm.ChatMessage;
import com.ghatana.ai.llm.ToolCallResult;
import com.ghatana.ai.llm.ToolDefinition;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapts {@link OllamaCompletionService} to the {@link ToolAwareCompletionService} contract.
 *
 * <p>Ollama's HTTP API does support tool calling for compatible models (llama3.2+), but
 * the current platform {@link OllamaCompletionService} only implements the base
 * {@link com.ghatana.ai.llm.CompletionService} interface. This adapter bridges the gap so
 * that Ollama can be registered as an {@link com.ghatana.ai.llm.LLMGateway} provider.
 *
 * <p>Tool-calling methods ({@link #completeWithTools} and {@link #continueWithToolResults})
 * enrich the request metadata/messages so the underlying Ollama OpenAI-compatible endpoint
 * receives tool definitions and tool output messages.
 *
 * @doc.type class
 * @doc.purpose Adapter making OllamaCompletionService compatible with ToolAwareCompletionService
 * @doc.layer product
 * @doc.pattern Adapter
 */
final class ToolAwareOllamaCompletionService implements ToolAwareCompletionService {

    private static final Logger log = LoggerFactory.getLogger(ToolAwareOllamaCompletionService.class);
    private static final ToolPermissionPolicy ALLOW_ALL_TOOLS = (request, tool) -> true;
    private static final AgentActionAuditSink NOOP_AUDIT_SINK = new AgentActionAuditSink() { };

    private final OllamaCompletionService delegate;
    private final ToolPermissionPolicy permissionPolicy;
    private final AgentActionAuditSink auditSink;

    ToolAwareOllamaCompletionService(OllamaCompletionService delegate) {
        this(delegate, ALLOW_ALL_TOOLS, NOOP_AUDIT_SINK);
    }

    ToolAwareOllamaCompletionService(
            OllamaCompletionService delegate,
            ToolPermissionPolicy permissionPolicy,
            AgentActionAuditSink auditSink) {
        this.delegate = delegate;
        this.permissionPolicy = permissionPolicy == null ? ALLOW_ALL_TOOLS : permissionPolicy;
        this.auditSink = auditSink == null ? NOOP_AUDIT_SINK : auditSink;
    }

    // ── CompletionService ─────────────────────────────────────────────────────

    @Override
    public Promise<CompletionResult> complete(CompletionRequest request) {
        return delegate.complete(request);
    }

    @Override
    public Promise<List<CompletionResult>> completeBatch(List<CompletionRequest> requests) {
        return delegate.completeBatch(requests);
    }

    @Override
    public LLMConfiguration getConfig() {
        return delegate.getConfig();
    }

    @Override
    public MetricsCollector getMetricsCollector() {
        return delegate.getMetricsCollector();
    }

    @Override
    public String getProviderName() {
        return delegate.getProviderName();
    }

    // ── ToolAwareCompletionService ────────────────────────────────────────────

    @Override
    public Promise<CompletionResult> completeWithTools(CompletionRequest request,
                                                       List<ToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return delegate.complete(request);
        }

        List<ToolDefinition> allowedTools = new ArrayList<>();
        for (ToolDefinition tool : tools) {
            if (permissionPolicy.isAllowed(request, tool)) {
                allowedTools.add(tool);
            } else {
                auditSink.onToolDenied(getProviderName(), tool.getName(), "policy_denied");
                log.warn("Tool '{}' denied by permission policy for provider {}",
                        tool.getName(), getProviderName());
            }
        }

        if (allowedTools.isEmpty()) {
            Map<String, Object> metadata = new HashMap<>(request.getMetadata());
            metadata.put("tool_choice", "none");
            CompletionRequest noToolRequest = copyRequest(request)
                    .metadata(metadata)
                    .build();
            return delegate.complete(noToolRequest);
        }

        List<Map<String, Object>> toolPayload = allowedTools.stream()
            .map(ToolDefinition::toOpenAIFormat)
            .toList();

        Map<String, Object> metadata = new HashMap<>(request.getMetadata());
        metadata.put("tools", toolPayload);
        metadata.put("tool_choice", "auto");

        CompletionRequest toolAwareRequest = copyRequest(request)
            .metadata(metadata)
            .build();

        auditSink.onToolsForwarded(getProviderName(), allowedTools.size());
        log.debug("Ollama provider forwarding {} tool definitions", allowedTools.size());
        return delegate.complete(toolAwareRequest);
    }

    @Override
    public Promise<CompletionResult> continueWithToolResults(CompletionRequest request,
                                                              List<ToolCallResult> toolResults) {
        if (toolResults == null || toolResults.isEmpty()) {
            return delegate.complete(request);
        }

        List<ChatMessage> messages = new ArrayList<>();
        if (!request.getMessages().isEmpty()) {
            messages.addAll(request.getMessages());
        } else if (request.getPrompt() != null && !request.getPrompt().isBlank()) {
            messages.add(ChatMessage.user(request.getPrompt()));
        }

        for (ToolCallResult toolResult : toolResults) {
            messages.add(ChatMessage.of(
                ChatMessage.Role.TOOL,
                toolResult.getResult(),
                toolResult.getToolName()));
        }

        List<Map<String, Object>> serializedToolResults = toolResults.stream()
            .map(result -> Map.<String, Object>of(
                "tool_call_id", result.getToolCallId(),
                "tool_name", result.getToolName(),
                "success", result.isSuccess(),
                "result", result.getResult()))
            .toList();

        Map<String, Object> metadata = new HashMap<>(request.getMetadata());
        metadata.put("tool_results", serializedToolResults);

        CompletionRequest continuationRequest = copyRequest(request)
            .messages(messages)
            .metadata(metadata)
            .build();

        auditSink.onToolResultsForwarded(getProviderName(), toolResults.size());
        log.debug("Ollama provider forwarding {} tool results", toolResults.size());
        return delegate.complete(continuationRequest);
    }

    @FunctionalInterface
    interface ToolPermissionPolicy {
        boolean isAllowed(CompletionRequest request, ToolDefinition tool);
    }

    interface AgentActionAuditSink {
        default void onToolDenied(String provider, String toolName, String reason) { }
        default void onToolsForwarded(String provider, int toolCount) { }
        default void onToolResultsForwarded(String provider, int resultCount) { }
    }

        private CompletionRequest.Builder copyRequest(CompletionRequest request) {
        return CompletionRequest.builder()
            .prompt(request.getPrompt())
            .messages(request.getMessages())
            .maxTokens(request.getMaxTokens())
            .temperature(request.getTemperature())
            .topP(request.getTopP())
            .stop(request.getStop())
            .model(request.getModel())
            .stopSequences(request.getStopSequences())
            .responseFormat(request.getResponseFormat())
            .frequencyPenalty(request.getFrequencyPenalty())
            .presencePenalty(request.getPresencePenalty());
        }
}
