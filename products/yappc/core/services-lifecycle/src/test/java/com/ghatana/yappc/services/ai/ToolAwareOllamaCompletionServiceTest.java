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
    @DisplayName("completeWithTools forwards OpenAI-compatible tools payload")
    void shouldForwardToolsViaRequestMetadata() {
        OllamaCompletionService delegate = mock(OllamaCompletionService.class);
        when(delegate.complete(any())).thenReturn(Promise.of(CompletionResult.of("ok")));

        ToolAwareOllamaCompletionService service = new ToolAwareOllamaCompletionService(delegate);

        CompletionRequest request = CompletionRequest.builder()
                .prompt("Suggest next action")
                .maxTokens(128)
                .temperature(0.2)
                .build();

        ToolDefinition tool = ToolDefinition.builder()
                .name("search_code")
                .description("Search the codebase")
                .addParameter("query", "string", "Search phrase", true)
                .build();

        CompletionResult result = runPromise(() -> service.completeWithTools(request, List.of(tool)));

        assertThat(result.text()).isEqualTo("ok");

        ArgumentCaptor<CompletionRequest> captor = ArgumentCaptor.forClass(CompletionRequest.class);
        verify(delegate).complete(captor.capture());

        CompletionRequest forwarded = captor.getValue();
        assertThat(forwarded.getMetadata()).containsEntry("tool_choice", "auto");
        assertThat(forwarded.getMetadata()).containsKey("tools");

        Object toolsMetadata = forwarded.getMetadata().get("tools");
        assertThat(toolsMetadata).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) toolsMetadata;
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0)).containsEntry("type", "function");
    }

    @Test
    @DisplayName("continueWithToolResults appends tool messages and metadata")
    void shouldForwardToolResultsInMessagesAndMetadata() {
        OllamaCompletionService delegate = mock(OllamaCompletionService.class);
        when(delegate.complete(any())).thenReturn(Promise.of(CompletionResult.of("final answer")));

        ToolAwareOllamaCompletionService service = new ToolAwareOllamaCompletionService(delegate);

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

        ArgumentCaptor<CompletionRequest> captor = ArgumentCaptor.forClass(CompletionRequest.class);
        verify(delegate).complete(captor.capture());

        CompletionRequest forwarded = captor.getValue();
        assertThat(forwarded.getMessages()).hasSize(2);
        assertThat(forwarded.getMessages().get(1).getRole()).isEqualTo(ChatMessage.Role.TOOL);
        assertThat(forwarded.getMessages().get(1).getName()).isEqualTo("search_code");
        assertThat(forwarded.getMetadata()).containsKey("tool_results");
    }

    @Test
    @DisplayName("integration smoke: completeWithTools against real Ollama model")
    void realOllamaToolCallingSmokeTest() {
        String enabled = System.getenv("YAPPC_OLLAMA_IT_ENABLED");
        Assumptions.assumeTrue("true".equalsIgnoreCase(enabled),
            "Set YAPPC_OLLAMA_IT_ENABLED=true to run Ollama integration smoke test");

        String ollamaHost = System.getenv().getOrDefault("YAPPC_OLLAMA_IT_HOST", "http://localhost:11434");
        String ollamaModel = System.getenv().getOrDefault("YAPPC_OLLAMA_IT_MODEL", "llama3.2");

        DnsClient dnsClient = DnsClient.builder(eventloop(), InetAddress.getLoopbackAddress()).build();
        HttpClient httpClient = HttpClient.create(eventloop(), dnsClient);
        MetricsCollector metrics = mock(MetricsCollector.class);

        LLMConfiguration cfg = LLMConfiguration.builder()
            .apiKey("ollama")
            .baseUrl(ollamaHost)
            .modelName(ollamaModel)
            .temperature(0.2)
            .maxTokens(128)
            .maxRetries(0)
            .build();

        ToolAwareOllamaCompletionService service = new ToolAwareOllamaCompletionService(
            new OllamaCompletionService(cfg, httpClient, metrics));

        CompletionRequest request = CompletionRequest.builder()
            .messages(List.of(ChatMessage.user("Reply with one short sentence about testing.")))
            .maxTokens(128)
            .temperature(0.2)
            .build();

        ToolDefinition tool = ToolDefinition.builder()
            .name("search_code")
            .description("Search source code")
            .addParameter("query", "string", "Search phrase", true)
            .build();

        CompletionResult result = runPromise(() -> service.completeWithTools(request, List.of(tool)));

        assertThat(result).isNotNull();
        assertThat(result.text()).isNotBlank();
    }
}
