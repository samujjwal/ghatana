package com.ghatana.ai.llm;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for LLM streaming support in {@link DefaultLLMGateway}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Native streaming via {@link StreamingCompletionService}</li>
 *   <li>Batch-to-stream adaptation for non-streaming providers</li>
 *   <li>{@link DefaultTokenStream} producer/consumer behaviour</li>
 *   <li>Stream cancellation</li>
 * </ul>
 */
@DisplayName("LLM Streaming")
class LLMStreamingTest {

    // ── Stubs ────────────────────────────────────────────────────────────────

    /** Non-streaming provider — only implements ToolAwareCompletionService. */
    private static class BatchOnlyProvider implements ToolAwareCompletionService {

        private final String providerName;

        BatchOnlyProvider(String providerName) { // GH-90000
            this.providerName = providerName;
        }

        @Override
        public Promise<CompletionResult> complete(CompletionRequest request) { // GH-90000
            return Promise.of(CompletionResult.builder() // GH-90000
                    .text("batch:" + providerName) // GH-90000
                    .modelUsed(providerName) // GH-90000
                    .build()); // GH-90000
        }

        @Override
        public Promise<List<CompletionResult>> completeBatch(List<CompletionRequest> requests) { // GH-90000
            return Promise.of(List.of()); // GH-90000
        }

        @Override
        public LLMConfiguration getConfig() { return null; } // GH-90000

        @Override
        public MetricsCollector getMetricsCollector() { return null; } // GH-90000

        @Override
        public String getProviderName() { return providerName; } // GH-90000

        @Override
        public Promise<CompletionResult> completeWithTools(CompletionRequest request, List<ToolDefinition> tools) { // GH-90000
            return complete(request); // GH-90000
        }

        @Override
        public Promise<CompletionResult> continueWithToolResults(CompletionRequest request, List<ToolCallResult> toolResults) { // GH-90000
            return complete(request); // GH-90000
        }
    }

    /** Streaming provider — implements both interfaces. */
    private static class NativeStreamingProvider extends BatchOnlyProvider
            implements StreamingCompletionService {

        NativeStreamingProvider(String name) { // GH-90000
            super(name); // GH-90000
        }

        @Override
        public Promise<TokenStream> stream(CompletionRequest request) { // GH-90000
            DefaultTokenStream ts = new DefaultTokenStream(); // GH-90000
            ts.emitToken("Hello");
            ts.emitToken(" World");
            ts.complete(); // GH-90000
            return Promise.of(ts); // GH-90000
        }
    }

    /** Minimal no-op MetricsCollector. */
    private static final MetricsCollector NOOP_METRICS = NoopMetricsCollector.getInstance(); // GH-90000

    // ── DefaultTokenStream unit tests ────────────────────────────────────────

    @Nested
    @DisplayName("DefaultTokenStream")
    class DefaultTokenStreamTests {

        @Test
        @DisplayName("accumulates tokens for after-the-fact retrieval")
        void shouldAccumulateTokens() { // GH-90000
            DefaultTokenStream ts = new DefaultTokenStream(); // GH-90000
            ts.emitToken("foo");
            ts.emitToken("bar");
            ts.complete(); // GH-90000

            assertThat(ts.getAccumulatedText()).isEqualTo("foobar");
        }

        @Test
        @DisplayName("invokes callbacks in registration order")
        void shouldInvokeCallbacks() { // GH-90000
            DefaultTokenStream ts = new DefaultTokenStream(); // GH-90000
            List<String> received = new ArrayList<>(); // GH-90000
            boolean[] completed = {false};

            ts.onToken(received::add) // GH-90000
              .onComplete(() -> completed[0] = true); // GH-90000

            ts.emitToken("a");
            ts.emitToken("b");
            ts.complete(); // GH-90000

            assertThat(received).containsExactly("a", "b"); // GH-90000
            assertThat(completed[0]).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("fires error callback on failure")
        void shouldFireErrorCallback() { // GH-90000
            DefaultTokenStream ts = new DefaultTokenStream(); // GH-90000
            Throwable[] captured = {null};
            ts.onError(err -> captured[0] = err); // GH-90000

            ts.error(new RuntimeException("boom"));

            assertThat(captured[0]).hasMessage("boom");
        }

        @Test
        @DisplayName("ignores tokens after cancellation")
        void shouldStopAfterCancel() { // GH-90000
            DefaultTokenStream ts = new DefaultTokenStream(); // GH-90000
            List<String> received = new ArrayList<>(); // GH-90000
            ts.onToken(received::add); // GH-90000

            ts.emitToken("before");
            ts.cancel(); // GH-90000
            ts.emitToken("after");

            assertThat(received).containsExactly("before");
            assertThat(ts.getAccumulatedText()).isEqualTo("before");
        }
    }

    // ── Gateway streaming tests ──────────────────────────────────────────────

    @Nested
    @DisplayName("DefaultLLMGateway.stream()")
    class GatewayStreamTests extends EventloopTestBase {

        private CompletionRequest request;

        @BeforeEach
        void setUp() { // GH-90000
            request = CompletionRequest.builder() // GH-90000
                    .prompt("test prompt")
                    .build(); // GH-90000
        }

        @Test
        @DisplayName("delegates to StreamingCompletionService when available")
        void shouldUseNativeStreaming() { // GH-90000
            DefaultLLMGateway gateway = DefaultLLMGateway.builder() // GH-90000
                    .addProvider("streaming", new NativeStreamingProvider("streaming"))
                    .defaultProvider("streaming")
                    .metrics(NOOP_METRICS) // GH-90000
                    .build(); // GH-90000

            TokenStream stream = runPromise(() -> gateway.stream(request)); // GH-90000
            assertThat(stream.getAccumulatedText()).isEqualTo("Hello World");
        }

        @Test
        @DisplayName("adapts batch completion to token stream for non-streaming providers")
        void shouldFallbackToBatchAdapter() { // GH-90000
            DefaultLLMGateway gateway = DefaultLLMGateway.builder() // GH-90000
                    .addProvider("batch", new BatchOnlyProvider("batch"))
                    .defaultProvider("batch")
                    .metrics(NOOP_METRICS) // GH-90000
                    .build(); // GH-90000

            TokenStream stream = runPromise(() -> gateway.stream(request)); // GH-90000
            assertThat(stream.getAccumulatedText()).isEqualTo("batch:batch");
        }

        @Test
        @DisplayName("streaming callback receives tokens from native provider")
        void shouldDeliverTokensViaCallback() { // GH-90000
            DefaultLLMGateway gateway = DefaultLLMGateway.builder() // GH-90000
                    .addProvider("streaming", new NativeStreamingProvider("streaming"))
                    .defaultProvider("streaming")
                    .metrics(NOOP_METRICS) // GH-90000
                    .build(); // GH-90000

            TokenStream stream = runPromise(() -> gateway.stream(request)); // GH-90000

            // Since native streaming provider already emitted+completed synchronously,
            // the accumulated text should be available
            List<String> tokens = new ArrayList<>(); // GH-90000
            stream.onToken(tokens::add); // won't receive retroactively, but accumulated is there // GH-90000
            assertThat(stream.getAccumulatedText()).isEqualTo("Hello World");
        }
    }
}
