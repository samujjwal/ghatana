package com.ghatana.products.yappc.domain.vector;

import com.ghatana.ai.llm.ChatMessage;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.LLMGateway;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Retrieval-Augmented Generation (RAG) Service.
 * <p>
 * Combines semantic search with LLM generation to produce
 * contextually-grounded responses based on retrieved documents.
 *
 * @doc.type class
 * @doc.purpose RAG pipeline orchestration
 * @doc.layer product
 * @doc.pattern Service
 */
public class RagService {

    private static final Logger LOG = LoggerFactory.getLogger(RagService.class);

    private final SemanticSearchService searchService;
    private final LLMGateway llmGateway;

    private static final String DEFAULT_SYSTEM_PROMPT = """
        You are a helpful AI assistant. Answer questions based on the provided context.
        If the context doesn't contain relevant information, say so clearly.
        Always cite your sources when possible.
        """;

    private static final int DEFAULT_CONTEXT_LIMIT = 5;
    private static final int DEFAULT_MAX_TOKENS = 1000;

    /**
     * Creates a new RagService.
     *
     * @param searchService The semantic search service
        * @param llmGateway The LLM gateway for generation
     */
    public RagService(
        @NotNull SemanticSearchService searchService,
        @NotNull LLMGateway llmGateway
    ) {
        this.searchService = Objects.requireNonNull(searchService);
        this.llmGateway = Objects.requireNonNull(llmGateway);
    }

    /**
     * Generates a response using RAG.
     *
     * @param request The RAG request
     * @return Promise resolving to the RAG response
     */
    @NotNull
    public Promise<RagResponse> generate(@NotNull RagRequest request) {
        LOG.debug("RAG generation for query: {}", request.query());
        long startTime = System.currentTimeMillis();

        // Step 1: Retrieve relevant context
        SemanticSearchService.SemanticSearchRequest searchRequest =
            new SemanticSearchService.SemanticSearchRequest(
                request.query(),
                request.contextLimit() > 0 ? request.contextLimit() : DEFAULT_CONTEXT_LIMIT,
                request.relevanceThreshold() > 0 ? request.relevanceThreshold() : 0.7,
                request.filters()
            );

        return searchService.search(searchRequest)
            .then(searchResult -> {
                if (!searchResult.isSuccess()) {
                    return Promise.of(RagResponse.error(
                        request.query(),
                        "Search failed: " + searchResult.error(),
                        System.currentTimeMillis() - startTime
                    ));
                }

                List<SemanticSearchService.SearchHit> hits = searchResult.hits();

                // Check if we have any relevant context
                if (hits.isEmpty()) {
                    return generateWithoutContext(request, startTime);
                }

                // Step 2: Build context from retrieved documents
                String contextBlock = buildContextBlock(hits);
                List<RetrievedContext> contexts = hits.stream()
                    .map(hit -> new RetrievedContext(
                        hit.id(),
                        hit.content(),
                        hit.score(),
                        hit.metadata()
                    ))
                    .collect(Collectors.toList());

                // Step 3: Generate response with context
                return generateWithContext(request, contextBlock, contexts, startTime);
            });
    }

    /**
     * Streams a RAG response.
     *
     * @param request The RAG request
     * @param onChunk Callback for each generated chunk
     * @return Promise resolving when generation is complete
     */
    @NotNull
    public Promise<RagResponse> generateStreaming(
        @NotNull RagRequest request,
        @NotNull StreamingCallback onChunk
    ) {
        LOG.debug("RAG streaming generation for query: {}", request.query());
        long startTime = System.currentTimeMillis();

        // First retrieve context
        SemanticSearchService.SemanticSearchRequest searchRequest =
            new SemanticSearchService.SemanticSearchRequest(
                request.query(),
                request.contextLimit() > 0 ? request.contextLimit() : DEFAULT_CONTEXT_LIMIT,
                request.relevanceThreshold() > 0 ? request.relevanceThreshold() : 0.7,
                request.filters()
            );

        return searchService.search(searchRequest)
            .then(searchResult -> {
                if (!searchResult.isSuccess()) {
                    return Promise.of(RagResponse.error(
                        request.query(),
                        "Search failed: " + searchResult.error(),
                        System.currentTimeMillis() - startTime
                    ));
                }

                List<SemanticSearchService.SearchHit> hits = searchResult.hits();
                String contextBlock = hits.isEmpty() ? "" : buildContextBlock(hits);
                List<RetrievedContext> contexts = hits.stream()
                    .map(hit -> new RetrievedContext(
                        hit.id(),
                        hit.content(),
                        hit.score(),
                        hit.metadata()
                    ))
                    .collect(Collectors.toList());

                // Build messages
                List<ChatMessage> messages = buildMessages(request, contextBlock);

                int maxTokens = request.maxTokens() > 0 ? request.maxTokens() : DEFAULT_MAX_TOKENS;
                double temperature = request.temperature() > 0 ? request.temperature() : 0.7;

                return completeChat(messages, maxTokens, temperature)
                        .map(fullText -> {
                            emitChunks(fullText, onChunk, 256);
                            return new RagResponse(
                                    request.query(),
                                    fullText,
                                    contexts,
                                    true,
                                    null,
                                    System.currentTimeMillis() - startTime,
                                    buildUsageStats(contexts.size(), fullText.length())
                            );
                        });
            });
    }

    /**
     * Performs RAG with conversation history.
     *
     * @param request The conversational RAG request
     * @return Promise resolving to the RAG response
     */
    @NotNull
    public Promise<RagResponse> chat(@NotNull ConversationalRagRequest request) {
        LOG.debug("Conversational RAG for query: {}", request.query());
        long startTime = System.currentTimeMillis();

        // Extract context from conversation history for better search
        String enrichedQuery = enrichQueryFromHistory(request.query(), request.history());

        // Search with enriched query
        SemanticSearchService.SemanticSearchRequest searchRequest =
            new SemanticSearchService.SemanticSearchRequest(
                enrichedQuery,
                request.contextLimit() > 0 ? request.contextLimit() : DEFAULT_CONTEXT_LIMIT,
                request.relevanceThreshold() > 0 ? request.relevanceThreshold() : 0.7,
                request.filters()
            );

        return searchService.search(searchRequest)
            .then(searchResult -> {
                List<SemanticSearchService.SearchHit> hits = searchResult.hits();
                String contextBlock = hits.isEmpty() ? "" : buildContextBlock(hits);
                List<RetrievedContext> contexts = hits.stream()
                    .map(hit -> new RetrievedContext(
                        hit.id(),
                        hit.content(),
                        hit.score(),
                        hit.metadata()
                    ))
                    .collect(Collectors.toList());

                // Build messages with history
                List<ChatMessage> messages = buildConversationalMessages(
                    request,
                    contextBlock,
                    request.history()
                );

                int maxTokens = request.maxTokens() > 0 ? request.maxTokens() : DEFAULT_MAX_TOKENS;
                double temperature = request.temperature() > 0 ? request.temperature() : 0.7;

                return completeChat(messages, maxTokens, temperature)
                        .map(text -> new RagResponse(
                                request.query(),
                                text,
                                contexts,
                                true,
                                null,
                                System.currentTimeMillis() - startTime,
                                buildUsageStats(contexts.size(), text.length())
                        ));
            });
    }

    // ==================== HELPER METHODS ====================

    private Promise<RagResponse> generateWithoutContext(
        RagRequest request,
        long startTime
    ) {
        LOG.debug("No relevant context found, generating response without RAG");

        List<ChatMessage> messages = List.of(
            ChatMessage.system(request.systemPrompt() != null
                ? request.systemPrompt()
                : DEFAULT_SYSTEM_PROMPT),
            ChatMessage.user(request.query())
        );

        int maxTokens = request.maxTokens() > 0 ? request.maxTokens() : DEFAULT_MAX_TOKENS;
        double temperature = request.temperature() > 0 ? request.temperature() : 0.7;

        return completeChat(messages, maxTokens, temperature)
                .map(text -> new RagResponse(
                        request.query(),
                        text,
                        List.of(),
                        true,
                        "No relevant context found - response generated without RAG",
                        System.currentTimeMillis() - startTime,
                        buildUsageStats(0, text.length())
                ));
    }

    private Promise<RagResponse> generateWithContext(
        RagRequest request,
        String contextBlock,
        List<RetrievedContext> contexts,
        long startTime
    ) {
        List<ChatMessage> messages = buildMessages(request, contextBlock);

        int maxTokens = request.maxTokens() > 0 ? request.maxTokens() : DEFAULT_MAX_TOKENS;
        double temperature = request.temperature() > 0 ? request.temperature() : 0.7;

        return completeChat(messages, maxTokens, temperature)
                .map(text -> new RagResponse(
                        request.query(),
                        text,
                        contexts,
                        true,
                        null,
                        System.currentTimeMillis() - startTime,
                        buildUsageStats(contexts.size(), text.length())
                ));
    }

    private Promise<String> completeChat(List<ChatMessage> messages, int maxTokens, double temperature) {
        CompletionRequest request = CompletionRequest.builder()
                .messages(messages)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .build();

        return llmGateway.complete(request).map(r -> r.getText() != null ? r.getText() : "");
    }

    private static void emitChunks(String text, StreamingCallback onChunk, int chunkSize) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int size = Math.max(1, chunkSize);
        for (int i = 0; i < text.length(); i += size) {
            int end = Math.min(text.length(), i + size);
            onChunk.onChunk(text.substring(i, end));
        }
    }

    private List<ChatMessage> buildMessages(RagRequest request, String contextBlock) {
        String systemPrompt = request.systemPrompt() != null
            ? request.systemPrompt()
            : DEFAULT_SYSTEM_PROMPT;

        String userPrompt = contextBlock.isEmpty()
            ? request.query()
            : String.format("""
                Context:
                %s

                Question: %s

                Please answer the question based on the provided context.
                """, contextBlock, request.query());

        return List.of(
            ChatMessage.system(systemPrompt),
            ChatMessage.user(userPrompt)
        );
    }

    private List<ChatMessage> buildConversationalMessages(
        ConversationalRagRequest request,
        String contextBlock,
        List<ConversationTurn> history
    ) {
        List<ChatMessage> messages = new ArrayList<>();

        // System message with context
        String systemPrompt = request.systemPrompt() != null
            ? request.systemPrompt()
            : DEFAULT_SYSTEM_PROMPT;

        if (!contextBlock.isEmpty()) {
            systemPrompt += "\n\nRelevant Context:\n" + contextBlock;
        }

        messages.add(ChatMessage.system(systemPrompt));

        // Add conversation history
        for (ConversationTurn turn : history) {
            messages.add(ChatMessage.user(turn.userMessage()));
            if (turn.assistantMessage() != null) {
                messages.add(ChatMessage.assistant(turn.assistantMessage()));
            }
        }

        // Add current query
        messages.add(ChatMessage.user(request.query()));

        return messages;
    }

    private String buildContextBlock(List<SemanticSearchService.SearchHit> hits) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            SemanticSearchService.SearchHit hit = hits.get(i);
            sb.append(String.format("[Source %d (relevance: %.2f)]\n", i + 1, hit.score()));
            sb.append(hit.content());
            sb.append("\n\n");
        }
        return sb.toString().trim();
    }

    private String enrichQueryFromHistory(String query, List<ConversationTurn> history) {
        if (history == null || history.isEmpty()) {
            return query;
        }

        // Get last few turns for context
        int lastTurns = Math.min(3, history.size());
        StringBuilder enriched = new StringBuilder();

        for (int i = history.size() - lastTurns; i < history.size(); i++) {
            ConversationTurn turn = history.get(i);
            enriched.append(turn.userMessage()).append(" ");
        }
        enriched.append(query);

        return enriched.toString().trim();
    }

    private UsageStats buildUsageStats(int contextDocs, int responseLength) {
        return new UsageStats(
            contextDocs,
            responseLength,
            System.currentTimeMillis()
        );
    }

    // ==================== REQUEST/RESPONSE TYPES ====================

    /**
     * RAG request
     */
    public record RagRequest(
        @NotNull String query,
        @Nullable String systemPrompt,
        int contextLimit,
        double relevanceThreshold,
        int maxTokens,
        double temperature,
        @Nullable Map<String, String> filters
    ) {
        public static RagRequest of(String query) {
            return new RagRequest(
                query,
                null,
                DEFAULT_CONTEXT_LIMIT,
                0.7,
                DEFAULT_MAX_TOKENS,
                0.7,
                null
            );
        }
    }

    /**
     * Conversational RAG request
     */
    public record ConversationalRagRequest(
        @NotNull String query,
        @NotNull List<ConversationTurn> history,
        @Nullable String systemPrompt,
        int contextLimit,
        double relevanceThreshold,
        int maxTokens,
        double temperature,
        @Nullable Map<String, String> filters
    ) {}

    /**
     * Conversation turn
     */
    public record ConversationTurn(
        @NotNull String userMessage,
        @Nullable String assistantMessage
    ) {}

    /**
     * RAG response
     */
    public record RagResponse(
        @NotNull String query,
        @NotNull String response,
        @NotNull List<RetrievedContext> contexts,
        boolean success,
        @Nullable String warning,
        long durationMs,
        @Nullable UsageStats usage
    ) {
        public static RagResponse error(String query, String error, long durationMs) {
            return new RagResponse(
                query,
                "",
                List.of(),
                false,
                error,
                durationMs,
                null
            );
        }
    }

    /**
     * Retrieved context
     */
    public record RetrievedContext(
        @NotNull String id,
        @NotNull String content,
        double relevanceScore,
        @Nullable Map<String, String> metadata
    ) {}

    /**
     * Usage statistics
     */
    public record UsageStats(
        int contextDocumentsUsed,
        int responseLength,
        long timestamp
    ) {}

    /**
     * Streaming callback interface
     */
    @FunctionalInterface
    public interface StreamingCallback {
        void onChunk(String chunk);
    }
}
