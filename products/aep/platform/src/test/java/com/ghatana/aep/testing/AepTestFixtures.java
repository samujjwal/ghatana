package com.ghatana.aep.testing;

import com.ghatana.aep.domain.pipeline.AgentSpec;
import com.ghatana.aep.domain.pipeline.PipelineSpec;
import com.ghatana.aep.domain.pipeline.PipelineStageSpec;
import com.ghatana.aep.domain.pattern.PatternRegistration;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Standard test fixtures for AEP tests.
 * Provides factory methods for creating test data objects with sensible
 * defaults.
 *
 * <p>
 * All fixtures follow the domain model patterns defined in
 * {@code products/agentic-event-processor/libs/java/domain-models}.
 *
 * @doc.type class
 * @doc.purpose Test data factory for AEP domain objects
 * @doc.layer testing
 * @doc.pattern Factory, Test Data Builder
 */
public final class AepTestFixtures {

    /** Default tenant ID for test fixtures. */
    public static final String DEFAULT_TENANT_ID = "test-tenant";

    /** Default namespace for test fixtures. */
    public static final String DEFAULT_NAMESPACE = "test-namespace";

    private AepTestFixtures() {
        // Utility class - prevent instantiation
    }

    // ============================================================================
    // Event Fixtures
    // ============================================================================

    /**
     * Creates a test event map with default values.
     *
     * @return A new test event as a Map
     */
    public static Map<String, Object> createTestEvent() {
        return createTestEvent("test.event");
    }

    /**
     * Creates a test event map with the specified type.
     *
     * @param eventType The event type
     * @return A new test event as a Map
     */
    public static Map<String, Object> createTestEvent(String eventType) {
        Map<String, Object> event = new HashMap<>();
        event.put("id", UUID.randomUUID().toString());
        event.put("tenantId", DEFAULT_TENANT_ID);
        event.put("type", eventType);
        event.put("timestamp", Instant.now().toString());
        event.put("payload", Map.of("key", "value", "count", 1));
        return event;
    }

    /**
     * Creates a batch of test events.
     *
     * @param count Number of events to create
     * @return List of test events
     */
    public static List<Map<String, Object>> createTestEvents(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> createTestEvent("test.event." + i))
                .toList();
    }

    // ============================================================================
    // Pipeline Fixtures
    // ============================================================================

    /**
     * Creates a test pipeline spec with default values.
     *
     * @return A new test pipeline spec
     */
    public static PipelineSpec createTestPipelineSpec() {
        return PipelineSpec.builder()
                .stages(List.of(
                        createIngestionStage(),
                        createProcessingStage()))
                .build();
    }

    /**
     * Creates an ingestion stage spec.
     *
     * @return A new ingestion stage spec
     */
    public static PipelineStageSpec createIngestionStage() {
        return PipelineStageSpec.builder()
                .name("ingestion")
                .stageType("CONNECTOR")
                .workflow(List.of(
                        AgentSpec.builder()
                                .id("kafka-ingest")
                                .agent("KafkaIngestionAgent")
                                .role("ingester")
                                .build()))
                .build();
    }

    /**
     * Creates a processing stage spec.
     *
     * @return A new processing stage spec
     */
    public static PipelineStageSpec createProcessingStage() {
        return PipelineStageSpec.builder()
                .name("processing")
                .stageType("STREAM")
                .workflow(List.of(
                        AgentSpec.builder()
                                .id("validator")
                                .agent("SchemaValidatorAgent")
                                .role("validator")
                                .agentTasks(List.of("validate schema", "check integrity"))
                                .acceptanceCriteria(List.of("error_rate < 0.1%"))
                                .build(),
                        AgentSpec.builder()
                                .id("enricher")
                                .agent("DataEnricherAgent")
                                .role("enricher")
                                .build()))
                .build();
    }

    /**
     * Creates a filter stage spec.
     *
     * @return A new filter stage spec
     */
    public static PipelineStageSpec createFilterStage() {
        return PipelineStageSpec.builder()
                .name("filter")
                .stageType("STREAM")
                .workflow(List.of(
                        AgentSpec.builder()
                                .id("filter-agent")
                                .agent("FilterAgent")
                                .role("filter")
                                .build()))
                .build();
    }

    /**
     * Creates a transform stage spec.
     *
     * @return A new transform stage spec
     */
    public static PipelineStageSpec createTransformStage() {
        return PipelineStageSpec.builder()
                .name("transform")
                .stageType("STREAM")
                .workflow(List.of(
                        AgentSpec.builder()
                                .id("transform-agent")
                                .agent("TransformAgent")
                                .role("transformer")
                                .build()))
                .build();
    }

    // ============================================================================
    // Agent Fixtures
    // ============================================================================

    /**
     * Creates a test agent spec with default values.
     *
     * @return A new test agent spec
     */
    public static AgentSpec createTestAgentSpec() {
        return createTestAgentSpec("test-agent-" + UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * Creates a test agent spec with the specified ID.
     *
     * @param agentId The agent ID
     * @return A new test agent spec
     */
    public static AgentSpec createTestAgentSpec(String agentId) {
        return AgentSpec.builder()
                .id(agentId)
                .agent("TestAgent")
                .role("processor")
                .agentTasks(List.of("process events", "transform data"))
                .acceptanceCriteria(List.of("latency < 100ms p99"))
                .dependencies(List.of("cache-service"))
                .inputsSpec(List.of(
                        AgentSpec.AgentIOSpec.builder()
                                .name("events")
                                .format("json")
                                .description("Input events to process")
                                .build()))
                .outputsSpec(List.of(
                        AgentSpec.AgentIOSpec.builder()
                                .name("processed_events")
                                .format("json")
                                .description("Processed output events")
                                .build()))
                .build();
    }

    /**
     * Creates a test agent spec with HITL (Human-in-the-Loop) enabled.
     *
     * @param agentId The agent ID
     * @return A new test agent spec with HITL
     */
    public static AgentSpec createTestAgentSpecWithHitl(String agentId) {
        return AgentSpec.builder()
                .id(agentId)
                .agent("ReviewAgent")
                .role("reviewer")
                .hitl(true)
                .hitlReason("Manual review required for sensitive data")
                .build();
    }

    // ============================================================================
    // Pattern Fixtures
    // ============================================================================

    /**
     * Creates a test pattern registration with default values.
     *
     * @return A new test pattern registration
     */
    public static PatternRegistration createTestPatternRegistration() {
        return createTestPatternRegistration("pattern-" + UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * Creates a test pattern registration with the specified ID.
     *
     * @param patternId The pattern ID
     * @return A new test pattern registration
     */
    public static PatternRegistration createTestPatternRegistration(String patternId) {
        return PatternRegistration.builder()
                .patternId(patternId)
                .registrationId("reg-" + patternId)
                .tenantId(DEFAULT_TENANT_ID)
                .build();
    }

    // ============================================================================
    // Utility Methods
    // ============================================================================

    /**
     * Generates a unique test ID with the given prefix.
     *
     * @param prefix The ID prefix
     * @return A unique test ID
     */
    public static String generateTestId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Gets the default tenant ID used in test fixtures.
     *
     * @return The default tenant ID
     */
    public static String getDefaultTenantId() {
        return DEFAULT_TENANT_ID;
    }

    /**
     * Gets the default namespace used in test fixtures.
     *
     * @return The default namespace
     */
    public static String getDefaultNamespace() {
        return DEFAULT_NAMESPACE;
    }
}
