package com.ghatana.yappc.ai.requirements.ai.profiling;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * JFR event for LLM API call profiling.
 *
 * <p>
 * <b>Purpose</b><br>
 * Records detailed performance metrics for LLM API calls including model name,
 * token usage, latency, and outcome. Enables production profiling with minimal
 * overhead using Java Flight Recorder.
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Part of AI Requirements observability infrastructure: - Created by:
 * OpenAILLMService before/after each API call - Consumed by: JFR profiling
 * tools, production monitoring - Provides: Per-request latency, token
 * consumption, error rates - Enables: Cost analysis, performance optimization,
 * anomaly detection
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * LLMApiCallEvent event = new LLMApiCallEvent();
 * event.begin();
 * try {
 *     event.model = "gpt-4";
 *     event.requestId = correlationId;
 *     event.promptTokens = request.estimateTokens();
 *
 *     LLMResponse response = callLLMApi(request);
 *
 *     event.completionTokens = response.getOutputTokens();
 *     event.totalTokens = response.getTokensUsed();
 *     event.success = true;
 * } catch (Exception e) {
 *     event.success = false;
 *     event.errorMessage = e.getMessage();
 * } finally {
 *     event.commit();
 * }
 * }</pre>
 *
 * <p>
 * <b>JFR Configuration</b><br>
 * Enable via JVM flags:
 * <pre>
 * -XX:StartFlightRecording=settings=profile,filename=recording.jfr
 * -XX:FlightRecorderOptions=stackdepth=64
 * </pre>
 *
 * Or programmatically:
 * <pre>{@code
 * Recording recording = new Recording();
 * recording.enable(LLMApiCallEvent.class).withThreshold(Duration.ofMillis(10));
 * recording.start();
 * // ... application runs
 * recording.stop();
 * recording.dump(Paths.get("llm-profile.jfr"));
 * }</pre>
 *
 * <p>
 * <b>Performance Impact</b><br>
 * JFR events have minimal overhead (typically <1% CPU): - Event creation: ~10ns
 * - Commit (no data): ~20ns - Commit (with data): ~100-500ns depending on
 * string lengths - Safe for production at high volumes
 *
 * <p>
 * <b>Analysis Tools</b><br>
 * - JDK Mission Control (JMC): GUI for JFR file analysis - jfr CLI: `jfr print
 * --events LLMApiCallEvent recording.jfr` - Custom parsers: Use JFR streaming
 * API
 *
 * @doc.type class
 * @doc.purpose JFR event for LLM API call profiling
 * @doc.layer product
 * @doc.pattern Profiling Event
 * @see Event
 * @see jdk.jfr
 * @since 1.0.0
 */
@Name("com.ghatana.requirements.LLMApiCall")
@Label("LLM API Call")
@Description("LLM API call with token usage and latency metrics")
@Category({"AI Requirements", "LLM"})
@StackTrace(false) // Don't capture stack traces - reduces overhead
public class LLMApiCallEvent extends Event {

    /**
     * LLM model name (e.g., "gpt-4", "gpt-3.5-turbo").
     */
    @Label("Model")
    @Description("LLM model name")
    public String model;

    /**
     * Unique request identifier for correlation across logs.
     */
    @Label("Request ID")
    @Description("Unique request identifier")
    public String requestId;

    /**
     * Number of tokens in the prompt.
     */
    @Label("Prompt Tokens")
    @Description("Number of tokens in prompt")
    public int promptTokens;

    /**
     * Number of tokens in the completion response.
     */
    @Label("Completion Tokens")
    @Description("Number of tokens in completion")
    public int completionTokens;

    /**
     * Total tokens used (prompt + completion).
     */
    @Label("Total Tokens")
    @Description("Total tokens used")
    public int totalTokens;

    /**
     * Whether the API call succeeded.
     */
    @Label("Success")
    @Description("Whether API call succeeded")
    public boolean success;

    /**
     * Error message if call failed (null if successful).
     */
    @Label("Error Message")
    @Description("Error message if failed")
    public String errorMessage;

    /**
     * LLM temperature parameter (0.0-2.0).
     */
    @Label("Temperature")
    @Description("Sampling temperature")
    public double temperature;

    /**
     * Maximum tokens requested in response.
     */
    @Label("Max Tokens")
    @Description("Maximum response tokens")
    public int maxTokens;
}
