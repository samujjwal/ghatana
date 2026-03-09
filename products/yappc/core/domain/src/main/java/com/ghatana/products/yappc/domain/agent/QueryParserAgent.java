package com.ghatana.products.yappc.domain.agent;

import com.ghatana.ai.llm.ChatMessage;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Query Parser Agent for natural language search query parsing.
 * <p>
 * Parses natural language queries into structured filters, intents,
 * and actions for the DevSecOps platform.
 * <p>
 * <b>Responsibilities:</b>
 * <ul>
 *   <li>Parse natural language search queries</li>
 *   <li>Extract entities (item IDs, phases, statuses, etc.)</li>
 *   <li>Detect user intent (search, filter, command, question, navigate)</li>
 *   <li>Generate structured filters for querying</li>
 * </ul>
 * <p>
 * <b>Models:</b> Custom NLU + GPT-3.5-turbo for complex queries
 * <p>
 * <b>Latency SLA:</b> 300ms
 *
 * @doc.type class
 * @doc.purpose Natural language query parser agent
 * @doc.layer product
 * @doc.pattern AIAgent
 */
public class QueryParserAgent extends AbstractAIAgent<QueryParserInput, QueryParserOutput> {

    private static final Logger LOG = LoggerFactory.getLogger(QueryParserAgent.class);

    private static final String VERSION = "2.0.0";
    private static final String DESCRIPTION = "Natural language query parsing and intent detection";
    private static final List<String> CAPABILITIES = List.of(
            "intent-detection",
            "entity-extraction",
            "filter-generation",
            "semantic-understanding"
    );
    private static final List<String> SUPPORTED_MODELS = List.of(
            "custom-nlu",
            "gpt-3.5-turbo"
    );

    private static final String PARSER_PROMPT = """
            Parse the following natural language query into a structured format.
            
            Query: "%s"
            Context Route: %s
            Persona: %s
            
            Extract:
            1. Intent: SEARCH | FILTER | COMMAND | QUESTION | NAVIGATE
            2. Entities: item IDs, phases, statuses, priorities, assignees, dates, tags
            3. Filters: Convert entities into filter criteria
            4. Confidence: Your confidence in the parsing (0-1)
            
            Respond in JSON format:
            {
              "intent": "...",
              "entities": [...],
              "filters": {...},
              "action": null or {...},
              "confidence": 0.95
            }
            """;

    // Common patterns for quick parsing
    private static final Pattern ITEM_ID_PATTERN = Pattern.compile(
            "\\b([A-Z]+-\\d+|ITEM-\\d+|TASK-\\d+|BUG-\\d+|VULN-\\d+)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PRIORITY_PATTERN = Pattern.compile(
            "\\b(high|medium|low|critical|urgent)\\s*priority\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern STATUS_PATTERN = Pattern.compile(
            "\\b(open|in[- ]progress|done|completed|blocked|pending|closed)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PHASE_PATTERN = Pattern.compile(
            "\\b(planning|design|development|testing|security|deployment|monitoring)\\s*phase\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final LLMGateway llmGateway;
    private final boolean useFastPath;

    /**
     * Creates a new QueryParserAgent.
     *
     * @param llmGateway       The LLM gateway for AI completions
     * @param metricsCollector The metrics collector
     * @param useFastPath      Whether to use fast pattern matching before LLM
     */
    public QueryParserAgent(
            @NotNull LLMGateway llmGateway,
            @NotNull MetricsCollector metricsCollector,
            boolean useFastPath
    ) {
        super(
                AgentName.QUERY_PARSER_AGENT,
                VERSION,
                DESCRIPTION,
                CAPABILITIES,
                SUPPORTED_MODELS,
                metricsCollector
        );
        this.llmGateway = llmGateway;
        this.useFastPath = useFastPath;
    }

    /**
     * Creates a QueryParserAgent with fast path enabled.
     */
    public QueryParserAgent(
            @NotNull LLMGateway llmGateway,
            @NotNull MetricsCollector metricsCollector
    ) {
        this(llmGateway, metricsCollector, true);
    }

    @Override
    public void validateInput(@NotNull QueryParserInput input) {
        if (input.query() == null || input.query().isBlank()) {
            throw new IllegalArgumentException("Query cannot be empty");
        }
    }

    @Override
    protected Promise<ProcessResult<QueryParserOutput>> processRequest(
            @NotNull QueryParserInput input,
            @NotNull AIAgentContext context
    ) {
        LOG.debug("Parsing query: {}", input.query());

        // Try fast path first for simple queries
        if (useFastPath) {
            QueryParserOutput fastResult = tryFastParse(input);
            if (fastResult != null && fastResult.parsed().confidence() > 0.8) {
                LOG.debug("Fast path succeeded with confidence: {}", fastResult.parsed().confidence());
                metricsCollector.incrementCounter("agent.query_parser.fast_path", "result", "success");
                return Promise.of(ProcessResult.of(fastResult, fastResult.parsed().confidence()));
            }
        }

        // Fall back to LLM for complex queries
        return parseWithLLM(input, context);
    }

    private QueryParserOutput tryFastParse(QueryParserInput input) {
        String query = input.query().toLowerCase();
        List<QueryParserOutput.ExtractedEntity> entities = new ArrayList<>();
        double confidence = 0.0;

        // Extract item IDs
        Matcher itemMatcher = ITEM_ID_PATTERN.matcher(input.query());
        while (itemMatcher.find()) {
            entities.add(new QueryParserOutput.ExtractedEntity(
                    QueryParserOutput.ExtractedEntity.TYPE_ITEM_ID,
                    itemMatcher.group(),
                    itemMatcher.group().toUpperCase(),
                    itemMatcher.start(),
                    itemMatcher.end(),
                    0.95
            ));
            confidence = Math.max(confidence, 0.9);
        }

        // Extract priorities
        Matcher priorityMatcher = PRIORITY_PATTERN.matcher(query);
        List<String> priorities = new ArrayList<>();
        while (priorityMatcher.find()) {
            String priority = priorityMatcher.group(1).toLowerCase();
            priorities.add(priority);
            entities.add(new QueryParserOutput.ExtractedEntity(
                    QueryParserOutput.ExtractedEntity.TYPE_PRIORITY,
                    priorityMatcher.group(),
                    priority,
                    priorityMatcher.start(),
                    priorityMatcher.end(),
                    0.9
            ));
            confidence = Math.max(confidence, 0.85);
        }

        // Extract statuses
        Matcher statusMatcher = STATUS_PATTERN.matcher(query);
        List<String> statuses = new ArrayList<>();
        while (statusMatcher.find()) {
            String status = statusMatcher.group(1).toLowerCase().replace(" ", "-");
            statuses.add(status);
            entities.add(new QueryParserOutput.ExtractedEntity(
                    QueryParserOutput.ExtractedEntity.TYPE_STATUS,
                    statusMatcher.group(),
                    status,
                    statusMatcher.start(),
                    statusMatcher.end(),
                    0.9
            ));
            confidence = Math.max(confidence, 0.85);
        }

        // Extract phases
        Matcher phaseMatcher = PHASE_PATTERN.matcher(query);
        List<String> phases = new ArrayList<>();
        while (phaseMatcher.find()) {
            String phase = phaseMatcher.group(1).toLowerCase();
            phases.add(phase);
            entities.add(new QueryParserOutput.ExtractedEntity(
                    QueryParserOutput.ExtractedEntity.TYPE_PHASE,
                    phaseMatcher.group(),
                    phase,
                    phaseMatcher.start(),
                    phaseMatcher.end(),
                    0.9
            ));
            confidence = Math.max(confidence, 0.85);
        }

        // If no entities found, return null to trigger LLM parsing
        if (entities.isEmpty()) {
            return null;
        }

        // Determine intent
        QueryParserOutput.ParsedQuery.QueryIntent intent = determineIntent(query, entities);

        // Build filters
        QueryParserOutput.ItemFilter filters = new QueryParserOutput.ItemFilter(
                statuses.isEmpty() ? null : statuses,
                priorities.isEmpty() ? null : priorities,
                phases.isEmpty() ? null : phases,
                null, // assignees
                null, // tags
                null, // searchText
                null, // dateRange
                null  // customFilters
        );

        QueryParserOutput.ParsedQuery parsed = new QueryParserOutput.ParsedQuery(
                intent,
                entities,
                filters,
                null,
                confidence
        );

        return new QueryParserOutput(
                parsed,
                generateSuggestions(input, parsed),
                List.of()
        );
    }

    private QueryParserOutput.ParsedQuery.QueryIntent determineIntent(
            String query,
            List<QueryParserOutput.ExtractedEntity> entities
    ) {
        // Check for navigation keywords
        if (query.contains("go to") || query.contains("navigate") || query.contains("show me")) {
            return QueryParserOutput.ParsedQuery.QueryIntent.NAVIGATE;
        }

        // Check for command keywords
        if (query.contains("create") || query.contains("add") || query.contains("delete") ||
                query.contains("update") || query.contains("assign") || query.contains("move")) {
            return QueryParserOutput.ParsedQuery.QueryIntent.COMMAND;
        }

        // Check for question keywords
        if (query.contains("what") || query.contains("why") || query.contains("how") ||
                query.contains("when") || query.endsWith("?")) {
            return QueryParserOutput.ParsedQuery.QueryIntent.QUESTION;
        }

        // Check for filter keywords
        if (query.contains("filter") || query.contains("only") || query.contains("exclude") ||
                !entities.isEmpty()) {
            return QueryParserOutput.ParsedQuery.QueryIntent.FILTER;
        }

        // Default to search
        return QueryParserOutput.ParsedQuery.QueryIntent.SEARCH;
    }

    private Promise<ProcessResult<QueryParserOutput>> parseWithLLM(
            QueryParserInput input,
            AIAgentContext context
    ) {
        String prompt = String.format(
                PARSER_PROMPT,
                input.query(),
                input.currentRoute() != null ? input.currentRoute() : "unknown",
                input.persona() != null ? input.persona() : "default"
        );

        List<ChatMessage> messages = List.of(
                ChatMessage.system("You are a query parser for a DevSecOps platform. Parse queries into structured filters."),
                ChatMessage.user(prompt)
        );

        CompletionRequest request = CompletionRequest.builder()
                .messages(messages)
                .model("gpt-3.5-turbo")
                .temperature(0.3) // Lower temperature for more consistent parsing
                .maxTokens(512)
                .build();

        return llmGateway.complete(request)
                .map(result -> {
                    // Parse the JSON response
                    QueryParserOutput output = parseJsonResponse(result.getText(), input);
                    return ProcessResult.of(
                            output,
                            result.getTokensUsed(),
                            result.getModelUsed(),
                            output.parsed().confidence()
                    );
                });
    }

    private QueryParserOutput parseJsonResponse(String jsonResponse, QueryParserInput input) {
        // In production, use Jackson/Gson for proper JSON parsing
        // For now, return a default parsed query
        QueryParserOutput.ParsedQuery parsed = new QueryParserOutput.ParsedQuery(
                QueryParserOutput.ParsedQuery.QueryIntent.SEARCH,
                List.of(),
                QueryParserOutput.ItemFilter.empty(),
                null,
                0.75
        );

        return new QueryParserOutput(
                parsed,
                generateSuggestions(input, parsed),
                List.of()
        );
    }

    private List<String> generateSuggestions(QueryParserInput input, QueryParserOutput.ParsedQuery parsed) {
        List<String> suggestions = new ArrayList<>();

        // Add context-based suggestions
        if (parsed.filters().statuses() != null && !parsed.filters().statuses().isEmpty()) {
            suggestions.add("Also show " + getOppositeStatus(parsed.filters().statuses().get(0)));
        }

        if (parsed.filters().priorities() != null && !parsed.filters().priorities().isEmpty()) {
            suggestions.add("Include medium priority items");
        }

        // Add common follow-up queries
        suggestions.add("Sort by due date");
        suggestions.add("Group by assignee");

        return suggestions;
    }

    private String getOppositeStatus(String status) {
        return switch (status.toLowerCase()) {
            case "open" -> "completed items";
            case "completed", "done" -> "open items";
            case "blocked" -> "unblocked items";
            case "in-progress" -> "pending items";
            default -> "other statuses";
        };
    }

    @Override
    protected Promise<Map<String, AgentHealth.DependencyStatus>> doHealthCheck() {
        return llmGateway.complete(
                CompletionRequest.builder()
                        .prompt("test")
                        .maxTokens(1)
                        .build()
        ).map(result -> Map.of(
                "llmGateway", AgentHealth.DependencyStatus.HEALTHY,
                "patternMatcher", AgentHealth.DependencyStatus.HEALTHY
        )).mapException(e -> {
            LOG.error("Health check failed: {}", e.getMessage());
            return new RuntimeException("LLM Gateway unhealthy");
        });
    }
}
