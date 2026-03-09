/**
 * AI Requirements module providing LLM, embedding, and vector search capabilities.
 *
 * <p>
 * <b>Purpose</b><br>
 * Core AI functionality for requirements generation, analysis, and semantic
 * search. Integrates with OpenAI API for LLM completions and embeddings,
 * pgvector for similarity search. Supports multi-tenant isolation, API key
 * rotation, and comprehensive observability.
 *
 * <p>
 * <b>Key Components</b><br>
 * <ul>
 * <li>{@link com.ghatana.requirements.ai.llm.LLMService} - LLM API integration
 * for text generation</li>
 * <li>{@link com.ghatana.requirements.ai.llm.OpenAILLMService} -
 * OpenAI-specific LLM implementation</li>
 * <li>{@link com.ghatana.requirements.ai.llm.LLMConfigurationProvider} -
 * Dynamic configuration with key rotation</li>
 * <li>{@link com.ghatana.requirements.ai.RequirementEmbeddingService} -
 * High-level requirements embedding service</li>
 * </ul>
 *
 * <p>
 * <b>Architecture</b><br>
 * Follows Port/Adapter (Hexagonal) pattern:
 * <ul>
 * <li><b>Ports</b>: Service interfaces ({@code LLMService},
 * {@code EmbeddingService}, {@code VectorStore})</li>
 * <li><b>Adapters</b>: Implementations for OpenAI API and pgvector</li>
 * <li><b>Configuration</b>: Dynamic providers for zero-downtime updates</li>
 * <li><b>Observability</b>: Micrometer metrics + JFR profiling events</li>
 * </ul>
 *
 * <p>
 * <b>Sub-packages</b><br>
 * <ul>
 * <li>{@link com.ghatana.requirements.ai.llm} - LLM services and
 * configuration</li>
 * <li>{@link com.ghatana.requirements.ai.profiling} - JFR profiling events for
 * performance monitoring</li>
 * <li>{@link com.ghatana.requirements.ai.persona} - User persona
 * management</li>
 * <li>{@link com.ghatana.requirements.ai.suggestions} - Requirement suggestions
 * and recommendations</li>
 * <li>{@link com.ghatana.requirements.ai.prompts} - Prompt templates and
 * management</li>
 * <li>{@link com.ghatana.requirements.ai.feedback} - User feedback collection
 * and analysis</li>
 * </ul>
 *
 * <p>
 * <b>Usage Example</b><br>
 * <pre>{@code
 * // Configure LLM with API key rotation
 * RotatableKeyProvider provider = RotatableKeyProvider.builder()
 *     .addKey("primary", System.getenv("OPENAI_KEY_1"))
 *     .addKey("secondary", System.getenv("OPENAI_KEY_2"))
 *     .withModel("gpt-4")
 *     .build();
 *
 * // Create services
 * MetricsCollector metrics = MetricsCollectorFactory.create(meterRegistry);
 * LLMService llmService = new OpenAILLMService(provider, metrics);
 * EmbeddingService embeddingService = new OpenAIEmbeddingService(provider, metrics);
 * VectorStore vectorStore = new PgVectorStore(dataSource, metrics, "requirements", 1536);
 *
 * // Generate requirements
 * LLMRequest request = LLMRequest.builder()
 *     .prompt("Generate 5 requirements for user authentication system")
 *     .maxTokens(1000)
 *     .temperature(0.7)
 *     .build();
 *
 * Promise<LLMResponse> response = llmService.complete(request);
 *
 * // Generate and store embeddings
 * Promise<EmbeddingResult> embedding = embeddingService.createEmbedding("User login requirement");
 * embedding.then(result ->
 *     vectorStore.store("req-001", "User login requirement", result.getVector(), metadata)
 * );
 *
 * // Search similar requirements
 * Promise<List<SearchResult>> similar = vectorStore.search(queryVector, 10, 0.7);
 * }</pre>
 *
 * <p>
 * <b>Performance Characteristics</b><br>
 * <ul>
 * <li>LLM API calls: ~2-5 seconds for 500-1000 token completions</li>
 * <li>Embedding generation: ~100-300ms for typical text (100-500 tokens)</li>
 * <li>Vector search: &lt;50ms for 100k vectors, &lt;200ms for 1M vectors</li>
 * <li>API key rotation overhead: &lt;100ns per request</li>
 * <li>Retry logic: Exponential backoff with jitter (\u00b110%), max 30s
 * delay</li>
 * </ul>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * All service implementations are thread-safe and can be safely shared across
 * threads. Configuration providers use atomic operations for key rotation.
 * Vector store uses connection pooling for concurrent access.
 *
 * <p>
 * <b>Error Handling</b><br>
 * All operations return ActiveJ {@code Promise<T>} for asynchronous execution.
 * Errors are propagated through Promise exception handlers. Services
 * automatically retry transient failures (HTTP 429, 500-504, timeouts) with
 * exponential backoff.
 *
 * <p>
 * <b>Multi-Tenancy</b><br>
 * All operations are tenant-aware. Tenant ID is extracted from context and
 * propagated through all async operations. Vector store uses tenant-prefixed
 * keys for isolation.
 *
 * <p>
 * <b>Observability</b><br>
 * <ul>
 * <li><b>Metrics</b>: Micrometer counters/timers for all operations (via
 * {@code core/observability})</li>
 * <li><b>Profiling</b>: JFR custom events for LLM calls, embedding generation,
 * vector search</li>
 * <li><b>Tracing</b>: OpenTelemetry spans for distributed tracing</li>
 * <li><b>Logging</b>: Structured JSON logs with correlation IDs</li>
 * </ul>
 *
 * <p>
 * <b>Dependencies</b><br>
 * <ul>
 * <li>{@code core/observability} - Metrics collection abstraction</li>
 * <li>{@code libs/activej-http} - HTTP client for API calls</li>
 * <li>{@code libs/jackson} - JSON serialization</li>
 * <li>{@code libs/postgresql} - pgvector database driver</li>
 * <li>{@code libs/slf4j} + {@code libs/log4j2} - Logging</li>
 * </ul>
 *
 * <p>
 * <b>Configuration</b><br>
 * Environment variables (via
 * {@link com.ghatana.requirements.ai.llm.EnvironmentConfigurationProvider}):
 * <ul>
 * <li>{@code OPENAI_API_KEY} - Required: API key for OpenAI</li>
 * <li>{@code OPENAI_MODEL} - Default: gpt-4</li>
 * <li>{@code OPENAI_BASE_URL} - Default: https://api.openai.com</li>
 * <li>{@code OPENAI_TIMEOUT_MS} - Default: 30000</li>
 * <li>{@code OPENAI_MAX_RETRIES} - Default: 3</li>
 * <li>{@code OPENAI_TEMPERATURE} - Default: 0.7</li>
 * <li>{@code OPENAI_MAX_TOKENS} - Default: 1000</li>
 * </ul>
 *
 * <p>
 * <b>Testing</b><br>
 * <ul>
 * <li>Unit tests: {@code src/test/java/com/ghatana/requirements/ai/}</li>
 * <li>Integration tests: Require valid OpenAI API key and PostgreSQL with
 * pgvector</li>
 * <li>Test utilities:
 * {@link com.ghatana.requirements.ai.test.TestDataBuilders}</li>
 * <li>Base class: {@code EventloopTestBase} from
 * {@code core/testing/activej-test-utils}</li>
 * </ul>
 *
 * <p>
 * <b>Migration Guide</b><br>
 * For API key rotation migration:
 * <pre>{@code
 * // Before: Static configuration (deprecated)
 * LLMConfiguration config = LLMConfiguration.builder()
 *     .apiKey(System.getenv("OPENAI_API_KEY"))
 *     .model("gpt-4")
 *     .build();
 * LLMService service = new OpenAILLMService(config);
 *
 * // After: Dynamic provider with rotation
 * LLMConfigurationProvider provider = EnvironmentConfigurationProvider.withDefaults();
 * LLMService service = new OpenAILLMService(provider);
 * }</pre>
 *
 * @since 1.0.0
 * @see com.ghatana.requirements.ai.llm
 * @doc.type package
 * @doc.purpose AI requirements generation and analysis with LLM, embeddings,
 * and vector search
 * @doc.layer product
 * @doc.pattern Hexagonal Architecture (Ports and Adapters)
 */
package com.ghatana.requirements.ai;
