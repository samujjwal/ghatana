/**
 * JDK Flight Recorder (JFR) custom events for AI operations profiling.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides low-overhead, production-ready profiling events for LLM API calls,
 * embedding generation, and vector search operations. Enables deep performance
 * analysis, bottleneck identification, and production debugging using JFR tools
 * (JDK Mission Control, async-profiler, IntelliJ Profiler).
 *
 * <p>
 * <b>Key Components</b><br>
 * <ul>
 * <li>{@link com.ghatana.requirements.ai.profiling.LLMApiCallEvent} - LLM
 * completion profiling</li>
 * <li>{@link com.ghatana.requirements.ai.profiling.EmbeddingGenerationEvent} -
 * Embedding profiling</li>
 * <li>{@link com.ghatana.requirements.ai.profiling.VectorSearchEvent} - Vector
 * search profiling</li>
 * </ul>
 *
 * <p>
 * <b>What is JFR?</b><br>
 * JDK Flight Recorder is a profiling framework built into the JVM that
 * provides:
 * <ul>
 * <li>Sub-microsecond overhead (&lt;1% performance impact)</li>
 * <li>Continuous recording in production</li>
 * <li>Rich contextual data (thread, stack traces, timestamps)</li>
 * <li>Time-series analysis capabilities</li>
 * <li>Integration with monitoring tools</li>
 * </ul>
 *
 * <p>
 * <b>Event Structure</b><br>
 * All custom events extend {@code jdk.jfr.Event} and include:
 * <ul>
 * <li><b>Category</b>: "AI Requirements" - Groups related events</li>
 * <li><b>Labels</b>: Human-readable event names</li>
 * <li><b>Descriptions</b>: Detailed event purpose</li>
 * <li><b>Fields</b>: Strongly-typed event data with labels/descriptions</li>
 * <li><b>Thread/Timestamp</b>: Automatically captured by JFR</li>
 * </ul>
 *
 * <p>
 * <b>Usage Pattern</b><br>
 * Standard pattern for all custom events:
 * <pre>{@code
 * // Create event
 * LLMApiCallEvent event = new LLMApiCallEvent();
 * event.begin(); // Start timing
 *
 * try {
 *     // Set initial data
 *     event.model = "gpt-4";
 *     event.promptTokens = 150;
 *     event.tenantId = "tenant-123";
 *
 *     // Execute operation
 *     LLMResponse response = performLLMCall();
 *
 *     // Record success metrics
 *     event.completionTokens = response.getCompletionTokens();
 *     event.totalTokens = response.getTotalTokens();
 *     event.success = true;
 *
 * } catch (Exception e) {
 *     // Record failure
 *     event.success = false;
 *     event.errorMessage = e.getMessage();
 *
 * } finally {
 *     event.end(); // Stop timing
 *     if (event.shouldCommit()) {
 *         event.commit(); // Write to JFR stream
 *     }
 * }
 * }</pre>
 *
 * <p>
 * <b>LLM API Call Events</b><br>
 * {@link com.ghatana.requirements.ai.profiling.LLMApiCallEvent} captures:
 * <ul>
 * <li>Model name (gpt-4, gpt-3.5-turbo, etc.)</li>
 * <li>Token usage (prompt, completion, total)</li>
 * <li>Request parameters (temperature, maxTokens)</li>
 * <li>Duration (automatically measured by JFR)</li>
 * <li>Success/failure status</li>
 * <li>Tenant ID (multi-tenancy tracking)</li>
 * <li>Error details (if failed)</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * <pre>{@code
 * LLMApiCallEvent event = new LLMApiCallEvent();
 * event.begin();
 * event.model = "gpt-4";
 * event.promptTokens = 150;
 * event.maxTokens = 1000;
 * event.temperature = 0.7;
 * event.tenantId = "tenant-123";
 *
 * try {
 *     LLMResponse response = llmService.complete(request).getResult();
 *     event.completionTokens = response.getCompletionTokens();
 *     event.totalTokens = response.getTotalTokens();
 *     event.success = true;
 * } catch (Exception e) {
 *     event.success = false;
 *     event.errorMessage = e.getMessage();
 * } finally {
 *     event.end();
 *     if (event.shouldCommit()) {
 *         event.commit();
 *     }
 * }
 * }</pre>
 *
 * <p>
 * <b>Embedding Generation Events</b><br>
 * {@link com.ghatana.requirements.ai.profiling.EmbeddingGenerationEvent}
 * captures:
 * <ul>
 * <li>Model name (text-embedding-ada-002, etc.)</li>
 * <li>Input text length (characters)</li>
 * <li>Token usage</li>
 * <li>Vector dimensions (1536, 3072, etc.)</li>
 * <li>Batch size (for batch operations)</li>
 * <li>Duration</li>
 * <li>Success/failure</li>
 * <li>Tenant ID</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * <pre>{@code
 * EmbeddingGenerationEvent event = new EmbeddingGenerationEvent();
 * event.begin();
 * event.model = "text-embedding-ada-002";
 * event.inputTextLength = text.length();
 * event.batchSize = 1;
 * event.tenantId = "tenant-123";
 *
 * try {
 *     EmbeddingResult result = embeddingService.createEmbedding(text).getResult();
 *     event.tokensUsed = result.getTokensUsed();
 *     event.dimensions = result.getDimensions();
 *     event.success = true;
 * } catch (Exception e) {
 *     event.success = false;
 *     event.errorMessage = e.getMessage();
 * } finally {
 *     event.end();
 *     if (event.shouldCommit()) {
 *         event.commit();
 *     }
 * }
 * }</pre>
 *
 * <p>
 * <b>Vector Search Events</b><br>
 * {@link com.ghatana.requirements.ai.profiling.VectorSearchEvent} captures:
 * <ul>
 * <li>Collection name (table name)</li>
 * <li>Vector dimensions</li>
 * <li>Search limit (k)</li>
 * <li>Minimum similarity threshold</li>
 * <li>Results count (actual matches)</li>
 * <li>Duration</li>
 * <li>Success/failure</li>
 * <li>Tenant ID</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * <pre>{@code
 * VectorSearchEvent event = new VectorSearchEvent();
 * event.begin();
 * event.collectionName = "requirements_vectors";
 * event.dimensions = 1536;
 * event.limit = 10;
 * event.minSimilarity = 0.7;
 * event.tenantId = "tenant-123";
 *
 * try {
 *     List<SearchResult> results = vectorStore.search(vector, 10, 0.7).getResult();
 *     event.resultsCount = results.size();
 *     event.success = true;
 * } catch (Exception e) {
 *     event.success = false;
 *     event.errorMessage = e.getMessage();
 * } finally {
 *     event.end();
 *     if (event.shouldCommit()) {
 *         event.commit();
 *     }
 * }
 * }</pre>
 *
 * <p>
 * <b>Recording JFR Events</b><br>
 *
 * <p>
 * <b>1. Continuous Recording (Production)</b><br>
 * <pre>{@code
 * # Start application with JFR enabled
 * java -XX:StartFlightRecording=\
 *   name=ai-profiling,\
 *   settings=profile,\
 *   duration=1h,\
 *   filename=/tmp/ai-recording.jfr \
 *   -jar application.jar
 * }</pre>
 *
 * <p>
 * <b>2. On-Demand Recording</b><br>
 * <pre>{@code
 * # Start recording
 * jcmd <pid> JFR.start name=ai-profiling settings=profile
 *
 * # Dump recording
 * jcmd <pid> JFR.dump name=ai-profiling filename=/tmp/ai-recording.jfr
 *
 * # Stop recording
 * jcmd <pid> JFR.stop name=ai-profiling
 * }</pre>
 *
 * <p>
 * <b>3. Programmatic Recording</b><br>
 * <pre>{@code
 * Configuration config = Configuration.getConfiguration("profile");
 * Recording recording = new Recording(config);
 * recording.setName("ai-profiling");
 * recording.setMaxAge(Duration.ofHours(1));
 * recording.setMaxSize(100 * 1024 * 1024); // 100 MB
 * recording.start();
 *
 * // ... run application
 *
 * recording.dump(Paths.get("/tmp/ai-recording.jfr"));
 * recording.stop();
 * recording.close();
 * }</pre>
 *
 * <p>
 * <b>Analyzing JFR Data</b><br>
 *
 * <p>
 * <b>1. JDK Mission Control (GUI)</b><br>
 * <pre>
 * # Open recording
 * jmc /tmp/ai-recording.jfr
 *
 * # Navigate to Event Browser → AI Requirements
 * # View event details, timelines, histograms, statistics
 * </pre>
 *
 * <p>
 * <b>2. Command Line (jfr tool)</b><br>
 * <pre>{@code
 * # Print event summary
 * jfr print --events "AI Requirements.*" /tmp/ai-recording.jfr
 *
 * # Export to JSON
 * jfr print --json /tmp/ai-recording.jfr > events.json
 *
 * # View LLM API calls
 * jfr print --events LLMApiCallEvent /tmp/ai-recording.jfr
 *
 * # View statistics
 * jfr summary /tmp/ai-recording.jfr
 * }</pre>
 *
 * <p>
 * <b>3. Programmatic Analysis</b><br>
 * <pre>{@code
 * RecordingFile recordingFile = new RecordingFile(Paths.get("/tmp/ai-recording.jfr"));
 * while (recordingFile.hasMoreEvents()) {
 *     RecordedEvent event = recordingFile.readEvent();
 *     if (event.getEventType().getName().equals("LLMApiCallEvent")) {
 *         long duration = event.getDuration().toMillis();
 *         String model = event.getString("model");
 *         int tokens = event.getInt("totalTokens");
 *         boolean success = event.getBoolean("success");
 *
 *         logger.info("LLM call: model={}, duration={}ms, tokens={}, success={}",
 *             model, duration, tokens, success);
 *     }
 * }
 * }</pre>
 *
 * <p>
 * <b>Performance Impact</b><br>
 * JFR custom events have minimal overhead:
 * <ul>
 * <li>Event creation: ~50ns</li>
 * <li>Event commit: ~100-200ns (if enabled)</li>
 * <li>Total overhead: &lt;1% for typical workloads</li>
 * <li>Memory: ~1KB per event (buffered in memory)</li>
 * </ul>
 *
 * <p>
 * <b>Best Practices</b><br>
 * <ul>
 * <li>Always wrap in try-finally to ensure event.end() is called</li>
 * <li>Check {@code event.shouldCommit()} before committing (respects JFR
 * settings)</li>
 * <li>Keep event data fields minimal (avoid large strings/objects)</li>
 * <li>Use structured event names (Category → Event Type)</li>
 * <li>Add tenant ID for multi-tenant analysis</li>
 * <li>Record both success and failure cases</li>
 * <li>Use descriptive error messages</li>
 * </ul>
 *
 * <p>
 * <b>Integration with Observability</b><br>
 * JFR events complement Micrometer metrics:
 * <ul>
 * <li><b>Metrics</b>: High-level aggregates (counters, timers, gauges)</li>
 * <li><b>JFR</b>: Low-level detailed traces (per-request profiling)</li>
 * <li>Use both: Metrics for dashboards/alerts, JFR for deep investigation</li>
 * </ul>
 *
 * <p>
 * <b>Common Analysis Queries</b><br>
 * <pre>{@code
 * // Average LLM API latency by model
 * SELECT model, AVG(duration) as avg_latency
 * FROM LLMApiCallEvent
 * GROUP BY model;
 *
 * // Token usage distribution
 * SELECT totalTokens, COUNT(*) as frequency
 * FROM LLMApiCallEvent
 * GROUP BY totalTokens
 * ORDER BY totalTokens;
 *
 * // Error rate by tenant
 * SELECT tenantId,
 *        SUM(CASE WHEN success = false THEN 1 ELSE 0 END) * 100.0 / COUNT(*) as error_rate
 * FROM LLMApiCallEvent
 * GROUP BY tenantId;
 *
 * // Slow embedding generations (>1s)
 * SELECT inputTextLength, tokensUsed, duration
 * FROM EmbeddingGenerationEvent
 * WHERE duration > 1000
 * ORDER BY duration DESC;
 *
 * // Vector search performance by collection size
 * SELECT collectionName, AVG(duration) as avg_latency, MAX(resultsCount) as max_results
 * FROM VectorSearchEvent
 * GROUP BY collectionName;
 * }</pre>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * JFR events are thread-safe. Each event instance should be used on a single
 * thread (standard JFR pattern). Create new event instances per operation.
 *
 * <p>
 * <b>Testing</b><br>
 * JFR events can be tested using {@code FlightRecorderMXBean}:
 * <pre>{@code
 * {@literal @}Test
 * void shouldRecordLLMApiCallEvent() throws Exception {
 *     Recording recording = new Recording();
 *     recording.enable(LLMApiCallEvent.class);
 *     recording.start();
 *
 *     // Execute operation that triggers event
 *     llmService.complete(request);
 *
 *     recording.stop();
 *     List<RecordedEvent> events = recording.getEvents();
 *
 *     assertThat(events)
 *         .hasSize(1)
 *         .first()
 *         .satisfies(event -> {
 *             assertThat(event.getString("model")).isEqualTo("gpt-4");
 *             assertThat(event.getBoolean("success")).isTrue();
 *         });
 *
 *     recording.close();
 * }
 * }</pre>
 *
 * <p>
 * <b>Dependencies</b><br>
 * <ul>
 * <li>{@code jdk.jfr} - JDK Flight Recorder API (built-in JDK 11+)</li>
 * </ul>
 *
 * <p>
 * <b>Future Enhancements</b><br>
 * <ul>
 * <li>Streaming event processing (real-time analysis)</li>
 * <li>Automatic anomaly detection (outlier events)</li>
 * <li>Cost tracking events ($ per operation)</li>
 * <li>Quality metrics events (BLEU scores, etc.)</li>
 * <li>Custom JFR dashboard templates</li>
 * </ul>
 *
 * @since 1.0.0
 * @see com.ghatana.requirements.ai.profiling.LLMApiCallEvent
 * @see com.ghatana.requirements.ai.profiling.EmbeddingGenerationEvent
 * @see com.ghatana.requirements.ai.profiling.VectorSearchEvent
 * @see <a href="https://docs.oracle.com/en/java/javase/17/jfapi/">JFR API
 * Documentation</a>
 * @doc.type package
 * @doc.purpose JFR custom events for AI operations profiling and performance
 * analysis
 * @doc.layer product
 * @doc.pattern Event-Based Profiling
 */
package com.ghatana.requirements.ai.profiling;
