/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.ai;

import com.ghatana.datacloud.observability.ObservabilityService;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementation of AIAssistService for AI-powered query assistance.
 *
 * <p>Integrates with {@link ObservabilityService} to provide unified correlation tracking
 * across all AI operations, ensuring consistent observability for runtime truth.
 *
 * @doc.type class
 * @doc.purpose Concrete AI service with LLM integration and unified observability
 * @doc.layer application
 * @doc.pattern Service Implementation
 */
public class AIAssistServiceImpl implements AIAssistService {

    private static final Logger log = LoggerFactory.getLogger(AIAssistServiceImpl.class);

    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();
    private final MetricsCollector metrics;
    private final LLMProvider llmProvider;
    private final ObservabilityService observabilityService;
    private final List<AIEvaluationResult> evaluationResults = Collections.synchronizedList(new ArrayList<>());

    private long requestsProcessed = 0;
    private double totalLatencyMs = 0;

    public AIAssistServiceImpl(LLMProvider llmProvider, MetricsCollector metrics) {
        this(llmProvider, metrics, ObservabilityService.builder().serviceName("data-cloud-ai").build());
    }

    public AIAssistServiceImpl(LLMProvider llmProvider, MetricsCollector metrics, ObservabilityService observabilityService) {
        this.llmProvider = Objects.requireNonNull(llmProvider, "LLM provider required");
        this.metrics = Objects.requireNonNull(metrics, "Metrics required");
        this.observabilityService = Objects.requireNonNull(observabilityService, "Observability service required");
    }

    @Override
    public Promise<QueryResult> processQuery(String query, QueryContext context) {
        Objects.requireNonNull(query, "Query required");
        long startTime = System.currentTimeMillis();

        // Create observability context with unified correlation tracking
        String correlationId = UUID.randomUUID().toString();
        try (ObservabilityService.ObservabilityContext obsContext =
                observabilityService.createContext(
                    correlationId,
                    context.tenantId(),
                    "ai",
                    null,  // runId - not applicable for AI queries
                    null   // jobId - not applicable for AI queries
                )) {

            obsContext.addMetadata("operation", "processQuery");
            obsContext.addMetadata("queryLength", String.valueOf(query.length()));
            obsContext.recordMetric("query_length", query.length());

            return generateSQL(query, new DatabaseSchema(context.databaseSchema(), List.of()))
                .then(generatedSQL -> {
                    long processingTime = System.currentTimeMillis() - startTime;
                    requestsProcessed++;
                    totalLatencyMs += processingTime;

                    QueryResult result = new QueryResult(
                        correlationId,
                        query,
                        "User requested data retrieval",
                        generatedSQL,
                        List.of(),
                        new Explanation("Query processed", List.of(), List.of(), null),
                        processingTime,
                        false,
                        1.0 // Default confidence score
                    );

                    obsContext.recordMetric("processing_time_ms", processingTime);
                    obsContext.recordEvent("query_processed", Map.of(
                        "tenantId", context.tenantId(),
                        "queryLength", String.valueOf(query.length()),
                        "processingTimeMs", String.valueOf(processingTime)
                    ));

                    metrics.incrementCounter("ai.query.success", "tenant", context.tenantId());
                    log.info("Query processed: tenant={}, queryLength={}, correlationId={}",
                        context.tenantId(), query.length(), correlationId);

                    return Promise.of(result);
                })
                .whenException(e -> {
                    obsContext.recordEvent("query_failed", Map.of(
                        "tenantId", context.tenantId(),
                        "error", e.getMessage()
                    ));
                    metrics.incrementCounter("ai.query.error", "tenant", context.tenantId());
                    log.error("Query processing failed: tenant={}, correlationId={}",
                        context.tenantId(), correlationId, e);
                });
        }
    }

    @Override
    public Promise<GeneratedSQL> generateSQL(String description, DatabaseSchema schema) {
        Objects.requireNonNull(description, "Description required");

        // Create observability context for SQL generation
        String correlationId = UUID.randomUUID().toString();
        try (ObservabilityService.ObservabilityContext obsContext =
                observabilityService.createContext(
                    correlationId,
                    null,  // tenantId - may not be available at this level
                    "ai",
                    null,
                    null
                )) {

            obsContext.addMetadata("operation", "generateSQL");
            obsContext.addMetadata("descriptionLength", String.valueOf(description.length()));

            // Use LLM provider to generate SQL
            String prompt = buildSQLGenerationPrompt(description, schema);

            LLMProvider.CompletionRequest request = LLMProvider.CompletionRequest.builder()
                .model("gpt-4")  // Set default model
                .prompt(prompt)
                .build();

            return llmProvider.complete(request)
                .then(response -> {
                    String generatedSQL = extractSQLFromResponse(response.text());

                    GeneratedSQL result = new GeneratedSQL(
                        generatedSQL,
                        "Generated from: " + description,
                        extractTablesFromSQL(generatedSQL),
                        validateSQLSafety(generatedSQL),
                        isReadOnlySQL(generatedSQL)
                    );

                    obsContext.recordEvent("sql_generated", Map.of(
                        "descriptionLength", String.valueOf(description.length()),
                        "sqlLength", String.valueOf(generatedSQL.length()),
                        "tables", String.valueOf(extractTablesFromSQL(generatedSQL).size())
                    ));

                    metrics.incrementCounter("ai.sql.generate.success");
                    return Promise.of(result);
                })
                .whenException(e -> {
                    obsContext.recordEvent("sql_generation_failed", Map.of(
                        "error", e.getMessage()
                    ));
                    metrics.incrementCounter("ai.sql.generate.error");
                    log.error("SQL generation failed: correlationId={}", correlationId, e);
                });
        }
    }

    @Override
    public Promise<Explanation> explainResults(String query, List<Map<String, Object>> results,
                                                QueryContext context) {
        Objects.requireNonNull(query, "Query required");
        Objects.requireNonNull(results, "Results required");

        List<String> keyPoints = List.of(
            "Query returned " + results.size() + " rows",
            "Query type: " + determineQueryType(query),
            "Execution context: " + context.tenantId()
        );

        List<String> recommendations = new ArrayList<>();
        if (results.size() > 1000) {
            recommendations.add("Consider adding LIMIT clause for large result sets");
        }
        if (query.contains("SELECT *")) {
            recommendations.add("Select specific columns instead of * for better performance");
        }

        Explanation explanation = new Explanation(
            "Results from query execution",
            keyPoints,
            recommendations,
            results.size() > 10 ? "table" : "list"
        );

        metrics.incrementCounter("ai.explain.success");
        return Promise.of(explanation);
    }

    @Override
    public Promise<List<QuerySuggestion>> suggestQueries(QueryContext context, int limit) {
        Objects.requireNonNull(context, "Context required");

        List<QuerySuggestion> suggestions = new ArrayList<>();

        if (context.availableTables() != null) {
            for (String table : context.availableTables()) {
                suggestions.add(new QuerySuggestion(
                    "SELECT * FROM " + table + " LIMIT 10",
                    "Preview first 10 rows from " + table,
                    0.9,
                    "preview"
                ));
                suggestions.add(new QuerySuggestion(
                    "SELECT COUNT(*) FROM " + table,
                    "Count total rows in " + table,
                    0.85,
                    "aggregation"
                ));
            }
        }

        List<QuerySuggestion> limited = suggestions.stream()
            .limit(Math.max(1, limit))
            .collect(Collectors.toList());

        metrics.incrementCounter("ai.suggest.success", "count", String.valueOf(limited.size()));
        return Promise.of(limited);
    }

    @Override
    public Promise<Conversation> getConversation(String conversationId) {
        Objects.requireNonNull(conversationId, "Conversation ID required");
        Conversation conversation = conversations.get(conversationId);

        if (conversation == null) {
            return Promise.ofException(new IllegalArgumentException("Conversation not found: " + conversationId));
        }

        return Promise.of(conversation);
    }

    @Override
    public Promise<Conversation> createConversation(String tenantId, String userId) {
        Objects.requireNonNull(tenantId, "Tenant ID required");
        Objects.requireNonNull(userId, "User ID required");

        String conversationId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        // Create observability context
        String correlationId = UUID.randomUUID().toString();
        try (ObservabilityService.ObservabilityContext obsContext =
                observabilityService.createContext(
                    correlationId,
                    tenantId,
                    "ai",
                    null,
                    null
                )) {

            obsContext.addMetadata("operation", "createConversation");
            obsContext.addMetadata("userId", userId);

            Conversation conversation = new Conversation(
                conversationId,
                tenantId,
                userId,
                new ArrayList<>(),
                now,
                now,
                0
            );

            conversations.put(conversationId, conversation);

            obsContext.recordEvent("conversation_created", Map.of(
                "tenantId", tenantId,
                "userId", userId,
                "conversationId", conversationId
            ));

            metrics.incrementCounter("ai.conversation.create", "tenant", tenantId);

            return Promise.of(conversation);
        }
    }

    @Override
    public Promise<Conversation> addMessage(String conversationId, Message message) {
        Objects.requireNonNull(conversationId, "Conversation ID required");
        Objects.requireNonNull(message, "Message required");

        Conversation existing = conversations.get(conversationId);
        if (existing == null) {
            return Promise.ofException(new IllegalArgumentException("Conversation not found: " + conversationId));
        }

        // Create observability context
        String correlationId = UUID.randomUUID().toString();
        try (ObservabilityService.ObservabilityContext obsContext =
                observabilityService.createContext(
                    correlationId,
                    existing.tenantId(),
                    "ai",
                    null,
                    null
                )) {

            obsContext.addMetadata("operation", "addMessage");
            obsContext.addMetadata("conversationId", conversationId);

            List<Message> updatedMessages = new ArrayList<>(existing.messages());
            updatedMessages.add(message);

            Conversation updated = new Conversation(
                existing.id(),
                existing.tenantId(),
                existing.userId(),
                updatedMessages,
                existing.createdAt(),
                Instant.now(),
                updatedMessages.size()
            );

            conversations.put(conversationId, updated);

            obsContext.recordEvent("message_added", Map.of(
                "tenantId", existing.tenantId(),
                "conversationId", conversationId,
                "messageCount", String.valueOf(updatedMessages.size())
            ));

            metrics.incrementCounter("ai.conversation.message.add", "conversation", conversationId);

            return Promise.of(updated);
        }
    }

    @Override
    public Promise<Void> clearConversation(String conversationId) {
        Objects.requireNonNull(conversationId, "Conversation ID required");

        Conversation existing = conversations.get(conversationId);
        if (existing != null) {
            Conversation cleared = new Conversation(
                existing.id(),
                existing.tenantId(),
                existing.userId(),
                new ArrayList<>(),
                existing.createdAt(),
                Instant.now(),
                0
            );
            conversations.put(conversationId, cleared);
        }

        return Promise.of(null);
    }

    @Override
    public Promise<ServiceStatus> getStatus() {
        double avgLatency = requestsProcessed > 0 ? totalLatencyMs / requestsProcessed : 0;

        ServiceStatus status = new ServiceStatus(
            true,
            llmProvider.getName(),
            "unknown",
            requestsProcessed,
            avgLatency,
            Instant.now()
        );

        return Promise.of(status);
    }

    @Override
    public Promise<AIEvaluationMetrics> getEvaluationMetrics(TimeRange timeRange) {
        Instant cutoff = switch (timeRange) {
            case LAST_HOUR -> Instant.now().minusSeconds(3600);
            case LAST_DAY -> Instant.now().minusSeconds(86400);
            case LAST_WEEK -> Instant.now().minusSeconds(604800);
            case LAST_MONTH -> Instant.now().minusSeconds(2592000);
            case ALL_TIME -> Instant.EPOCH;
        };

        List<AIEvaluationResult> filtered;
        synchronized (evaluationResults) {
            filtered = evaluationResults.stream()
                .filter(r -> r.timestamp() != null && !r.timestamp().isBefore(cutoff))
                .toList();
        }

        int total = filtered.size();
        long passed = filtered.stream().filter(AIEvaluationResult::passed).count();
        double accuracy = total == 0 ? 0.0 : (double) passed / total;
        double avgLatency = total == 0 ? 0.0 : filtered.stream().mapToDouble(AIEvaluationResult::latencyMs).average().orElse(0.0);

        AIEvaluationMetrics snapshot = new AIEvaluationMetrics(
            accuracy,
            accuracy,
            accuracy,
            accuracy,
            avgLatency,
            total,
            (int) passed,
            Instant.now()
        );

        return Promise.of(snapshot);
    }

    @Override
    public Promise<Void> recordEvaluationResult(AIEvaluationResult result) {
        Objects.requireNonNull(result, "Evaluation result required");

        // Create observability context
        String correlationId = UUID.randomUUID().toString();
        try (ObservabilityService.ObservabilityContext obsContext =
                observabilityService.createContext(
                    correlationId,
                    result.tenantId(),
                    "ai",
                    null,
                    null
                )) {

            obsContext.addMetadata("operation", "recordEvaluationResult");
            obsContext.addMetadata("passed", String.valueOf(result.passed()));
            obsContext.recordMetric("latency_ms", result.latencyMs());

            evaluationResults.add(result);

            obsContext.recordEvent("evaluation_recorded", Map.of(
                "tenantId", result.tenantId() == null ? "unknown" : result.tenantId(),
                "passed", String.valueOf(result.passed()),
                "latencyMs", String.valueOf(result.latencyMs())
            ));

            metrics.incrementCounter("ai.evaluation.recorded", "tenant", result.tenantId() == null ? "unknown" : result.tenantId());
            return Promise.complete();
        }
    }

    // Helper methods
    private String buildSQLGenerationPrompt(String description, DatabaseSchema schema) {
        return "Generate SQL for: " + description + "\nSchema: " + schema.name();
    }

    private String extractSQLFromResponse(String response) {
        // In production, this would parse the actual LLM response to extract SQL
        // For now, return the response as-is
        return response;
    }

    private List<String> extractTablesFromSQL(String sql) {
        List<String> tables = new ArrayList<>();
        if (sql.toUpperCase().contains("FROM")) {
            tables.add("table"); // Simplified extraction
        }
        return tables;
    }

    private List<String> validateSQLSafety(String sql) {
        List<String> warnings = new ArrayList<>();
        String upperSQL = sql.toUpperCase();

        if (upperSQL.contains("DELETE") || upperSQL.contains("DROP") || upperSQL.contains("TRUNCATE")) {
            warnings.add("Destructive operation detected");
        }
        if (!upperSQL.contains("WHERE")) {
            warnings.add("No WHERE clause - may affect all rows");
        }

        return warnings;
    }

    private boolean isReadOnlySQL(String sql) {
        String upperSQL = sql.toUpperCase().trim();
        return upperSQL.startsWith("SELECT") || upperSQL.startsWith("WITH");
    }

    private String determineQueryType(String query) {
        String upper = query.toUpperCase().trim();
        if (upper.startsWith("SELECT")) return "SELECT";
        if (upper.startsWith("INSERT")) return "INSERT";
        if (upper.startsWith("UPDATE")) return "UPDATE";
        if (upper.startsWith("DELETE")) return "DELETE";
        return "UNKNOWN";
    }
}
