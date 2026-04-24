package com.ghatana.yappc.services.ai;

import com.ghatana.ai.llm.ChatMessage;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.LLMConfiguration;
import com.ghatana.ai.llm.OllamaCompletionService;
import com.ghatana.ai.llm.ToolCallResult;
import com.ghatana.ai.llm.ToolDefinition;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.dns.DnsClient;
import io.activej.http.HttpClient;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.mockito.ArgumentCaptor;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ToolAwareOllamaCompletionService")
class ToolAwareOllamaCompletionServiceTest extends EventloopTestBase {

    @Test
    @DisplayName("completeWithTools enforces tool permission policy and audits denied tools")
    void shouldEnforceToolPermissionPolicy() {
    OllamaCompletionService delegate = mock(OllamaCompletionService.class);
    when(delegate.complete(any())).thenReturn(Promise.of(CompletionResult.of("ok")));

    List<String> deniedToolNames = new ArrayList<>();
    ToolAwareOllamaCompletionService.AgentActionAuditSink auditSink = new ToolAwareOllamaCompletionService.AgentActionAuditSink() {
        @Override
        public void onToolDenied(String provider, String toolName, String reason) {
        deniedToolNames.add(toolName + ":" + reason);
        }
    };

    ToolAwareOllamaCompletionService service = new ToolAwareOllamaCompletionService(
        delegate,
        (request, tool) -> "search_code".equals(tool.getName()),
        auditSink);

    CompletionRequest request = CompletionRequest.builder()
        .prompt("Suggest next action")
        .maxTokens(128)
        .temperature(0.2)
        .build();

    ToolDefinition allowed = ToolDefinition.builder()
        .name("search_code")
        .description("Search the codebase")
        .addParameter("query", "string", "Search phrase", true)
        .build();

    ToolDefinition denied = ToolDefinition.builder()
        .name("write_file")
        .description("Write file")
        .addParameter("path", "string", "Path", true)
        .build();

    CompletionResult result = runPromise(() -> service.completeWithTools(request, List.of(allowed, denied)));

    assertThat(result.text()).isEqualTo("ok");
    assertThat(deniedToolNames).containsExactly("write_file:policy_denied");

    ArgumentCaptor<CompletionRequest> captor = ArgumentCaptor.forClass(CompletionRequest.class);
    verify(delegate).complete(captor.capture());

    Object toolsMetadata = captor.getValue().getMetadata().get("tools");
    assertThat(toolsMetadata).isInstanceOf(List.class);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> tools = (List<Map<String, Object>>) toolsMetadata;
    assertThat(tools).hasSize(1);
    }

    @Test
    @DisplayName("completeWithTools sets tool_choice=none when no tools are allowed")
    void shouldDisableToolChoiceWhenAllToolsDenied() {
    OllamaCompletionService delegate = mock(OllamaCompletionService.class);
    when(delegate.complete(any())).thenReturn(Promise.of(CompletionResult.of("ok")));

    ToolAwareOllamaCompletionService service = new ToolAwareOllamaCompletionService(
        delegate,
        (request, tool) -> false,
        null);

    CompletionRequest request = CompletionRequest.builder()
        .prompt("Suggest next action")
        .maxTokens(128)
        .temperature(0.2)
        .build();

    ToolDefinition denied = ToolDefinition.builder()
        .name("write_file")
        .description("Write file")
        .addParameter("path", "string", "Path", true)
        .build();

    CompletionResult result = runPromise(() -> service.completeWithTools(request, List.of(denied)));
    assertThat(result.text()).isEqualTo("ok");

    ArgumentCaptor<CompletionRequest> captor = ArgumentCaptor.forClass(CompletionRequest.class);
    verify(delegate).complete(captor.capture());
    CompletionRequest forwarded = captor.getValue();
    assertThat(forwarded.getMetadata()).containsEntry("tool_choice", "none");
    assertThat(forwarded.getMetadata()).doesNotContainKey("tools");
    }

    @Test
    @DisplayName("completeWithTools forwards OpenAI-compatible tools payload")
    void shouldForwardToolsViaRequestMetadata() { // GH-90000
        OllamaCompletionService delegate = mock(OllamaCompletionService.class); // GH-90000
        when(delegate.complete(any())).thenReturn(Promise.of(CompletionResult.of("ok")));

        ToolAwareOllamaCompletionService service = new ToolAwareOllamaCompletionService(delegate); // GH-90000

        CompletionRequest request = CompletionRequest.builder() // GH-90000
                .prompt("Suggest next action")
                .maxTokens(128) // GH-90000
                .temperature(0.2) // GH-90000
                .build(); // GH-90000

        ToolDefinition tool = ToolDefinition.builder() // GH-90000
                .name("search_code")
                .description("Search the codebase")
                .addParameter("query", "string", "Search phrase", true) // GH-90000
                .build(); // GH-90000

        CompletionResult result = runPromise(() -> service.completeWithTools(request, List.of(tool))); // GH-90000

        assertThat(result.text()).isEqualTo("ok");

        ArgumentCaptor<CompletionRequest> captor = ArgumentCaptor.forClass(CompletionRequest.class); // GH-90000
        verify(delegate).complete(captor.capture()); // GH-90000

        CompletionRequest forwarded = captor.getValue(); // GH-90000
        assertThat(forwarded.getMetadata()).containsEntry("tool_choice", "auto"); // GH-90000
        assertThat(forwarded.getMetadata()).containsKey("tools");

        Object toolsMetadata = forwarded.getMetadata().get("tools");
        assertThat(toolsMetadata).isInstanceOf(List.class); // GH-90000
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) toolsMetadata; // GH-90000
        assertThat(tools).hasSize(1); // GH-90000
        assertThat(tools.get(0)).containsEntry("type", "function"); // GH-90000
    }

    @Test
    @DisplayName("continueWithToolResults appends tool messages and metadata")
    void shouldForwardToolResultsInMessagesAndMetadata() { // GH-90000
        OllamaCompletionService delegate = mock(OllamaCompletionService.class); // GH-90000
        when(delegate.complete(any())).thenReturn(Promise.of(CompletionResult.of("final answer")));

        ToolAwareOllamaCompletionService service = new ToolAwareOllamaCompletionService(delegate); // GH-90000

        CompletionRequest request = CompletionRequest.builder() // GH-90000
                .messages(List.of(ChatMessage.user("Find impacted modules")))
                .maxTokens(256) // GH-90000
                .temperature(0.4) // GH-90000
                .build(); // GH-90000

        ToolCallResult toolResult = ToolCallResult.success( // GH-90000
                "call-1",
                "search_code",
                "{\"files\":[\"core/ai/module.java\"]}");

        CompletionResult result = runPromise(() -> service.continueWithToolResults(request, List.of(toolResult))); // GH-90000

        assertThat(result.text()).isEqualTo("final answer");

        ArgumentCaptor<CompletionRequest> captor = ArgumentCaptor.forClass(CompletionRequest.class); // GH-90000
        verify(delegate).complete(captor.capture()); // GH-90000

        CompletionRequest forwarded = captor.getValue(); // GH-90000
        assertThat(forwarded.getMessages()).hasSize(2); // GH-90000
        assertThat(forwarded.getMessages().get(1).getRole()).isEqualTo(ChatMessage.Role.TOOL); // GH-90000
        assertThat(forwarded.getMessages().get(1).getName()).isEqualTo("search_code");
        assertThat(forwarded.getMetadata()).containsKey("tool_results");
    }

    @Test
    @DisplayName("continueWithToolResults emits audit event")
    void shouldAuditToolResultsForwarding() {
        OllamaCompletionService delegate = mock(OllamaCompletionService.class);
        when(delegate.complete(any())).thenReturn(Promise.of(CompletionResult.of("final answer")));

        List<Integer> forwardedCounts = new ArrayList<>();
        ToolAwareOllamaCompletionService.AgentActionAuditSink auditSink = new ToolAwareOllamaCompletionService.AgentActionAuditSink() {
            @Override
            public void onToolResultsForwarded(String provider, int resultCount) {
                forwardedCounts.add(resultCount);
            }
        };

        ToolAwareOllamaCompletionService service = new ToolAwareOllamaCompletionService(delegate, null, auditSink);

        CompletionRequest request = CompletionRequest.builder()
                .messages(List.of(ChatMessage.user("Find impacted modules")))
                .maxTokens(256)
                .temperature(0.4)
                .build();

        ToolCallResult toolResult = ToolCallResult.success(
                "call-1",
                "search_code",
                "{\"files\":[\"core/ai/module.java\"]}");

        CompletionResult result = runPromise(() -> service.continueWithToolResults(request, List.of(toolResult)));

        assertThat(result.text()).isEqualTo("final answer");
        assertThat(forwardedCounts).containsExactly(1);
    }

    @Test
    @DisplayName("integration smoke: completeWithTools against real Ollama model")
    void realOllamaToolCallingSmokeTest() { // GH-90000
        String enabled = System.getenv("YAPPC_OLLAMA_IT_ENABLED");
        Assumptions.assumeTrue("true".equalsIgnoreCase(enabled), // GH-90000
            "Set YAPPC_OLLAMA_IT_ENABLED=true to run Ollama integration smoke test");

        String ollamaHost = System.getenv().getOrDefault("YAPPC_OLLAMA_IT_HOST", "http://localhost:11434"); // GH-90000
        String ollamaModel = System.getenv().getOrDefault("YAPPC_OLLAMA_IT_MODEL", "llama3.2"); // GH-90000

        DnsClient dnsClient = DnsClient.builder(eventloop(), InetAddress.getLoopbackAddress()).build(); // GH-90000
        HttpClient httpClient = HttpClient.create(eventloop(), dnsClient); // GH-90000
        MetricsCollector metrics = mock(MetricsCollector.class); // GH-90000

        LLMConfiguration cfg = LLMConfiguration.builder() // GH-90000
            .apiKey("ollama")
            .baseUrl(ollamaHost) // GH-90000
            .modelName(ollamaModel) // GH-90000
            .temperature(0.2) // GH-90000
            .maxTokens(128) // GH-90000
            .maxRetries(0) // GH-90000
            .build(); // GH-90000

        ToolAwareOllamaCompletionService service = new ToolAwareOllamaCompletionService( // GH-90000
            new OllamaCompletionService(cfg, httpClient, metrics)); // GH-90000

        CompletionRequest request = CompletionRequest.builder() // GH-90000
            .messages(List.of(ChatMessage.user("Reply with one short sentence about testing.")))
            .maxTokens(128) // GH-90000
            .temperature(0.2) // GH-90000
            .build(); // GH-90000

        ToolDefinition tool = ToolDefinition.builder() // GH-90000
            .name("search_code")
            .description("Search source code")
            .addParameter("query", "string", "Search phrase", true) // GH-90000
            .build(); // GH-90000

        CompletionResult result = runPromise(() -> service.completeWithTools(request, List.of(tool))); // GH-90000

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.text()).isNotBlank(); // GH-90000
    }
}
