/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.framework.config;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.FailureMode;
import com.ghatana.agent.adaptive.AdaptiveAgentConfig;
import com.ghatana.agent.composite.CompositeAgentConfig;
import com.ghatana.agent.deterministic.DeterministicAgentConfig;
import com.ghatana.agent.deterministic.DeterministicSubtype;
import com.ghatana.agent.hybrid.HybridAgentConfig;
import com.ghatana.agent.probabilistic.ProbabilisticAgentConfig;
import com.ghatana.agent.probabilistic.ProbabilisticSubtype;
import com.ghatana.agent.reactive.ReactiveAgentConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AgentConfigMaterializer} — YAML → AgentConfig conversion.
 *
 * Covers all 6 agent types, base field propagation, error cases,
 * directory materialization, and DTO accessors.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AgentConfigMaterializerTest {

    private AgentConfigMaterializer materializer;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        materializer = new AgentConfigMaterializer();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Deterministic Agent
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("Deterministic agent — YAML materialization")
    void deterministic() {
        String yaml = """
                agentId: fraud-detector
                type: DETERMINISTIC
                version: "2.1.0"
                timeout: PT5S
                confidenceThreshold: 0.85
                maxRetries: 3
                failureMode: CIRCUIT_BREAKER
                metricsEnabled: true
                labels:
                  team: security
                  domain: fraud
                subtype: RULE_BASED
                evaluateAllRules: true
                exactMatchField: txnType
                """;

        AgentConfig config = materializer.materialize(yaml);

        assertInstanceOf(DeterministicAgentConfig.class, config);
        DeterministicAgentConfig det = (DeterministicAgentConfig) config;

        assertEquals("fraud-detector", det.getAgentId());
        assertEquals(AgentType.DETERMINISTIC, det.getType());
        assertEquals("2.1.0", det.getVersion());
        assertEquals(Duration.ofSeconds(5), det.getTimeout());
        assertEquals(0.85, det.getConfidenceThreshold(), 0.001);
        assertEquals(3, det.getMaxRetries());
        assertEquals(FailureMode.CIRCUIT_BREAKER, det.getFailureMode());
        assertTrue(det.isMetricsEnabled());
        assertEquals("security", det.getLabels().get("team"));
        assertEquals(DeterministicSubtype.RULE_BASED, det.getSubtype());
        assertTrue(det.isEvaluateAllRules());
        assertEquals("txnType", det.getExactMatchField());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Probabilistic Agent
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("Probabilistic agent — YAML materialization")
    void probabilistic() {
        String yaml = """
                agentId: anomaly-scorer
                type: PROBABILISTIC
                version: "1.0.0"
                subtype: ML_MODEL
                modelName: fraud-xgboost
                modelVersion: "3.2"
                modelEndpoint: http://ml-service:8080/predict
                inferenceTimeout: PT0.2S
                batchSize: 32
                shadowMode: true
                fallbackEndpoints:
                  - http://backup1:8080/predict
                  - http://backup2:8080/predict
                """;

        AgentConfig config = materializer.materialize(yaml);

        assertInstanceOf(ProbabilisticAgentConfig.class, config);
        ProbabilisticAgentConfig prob = (ProbabilisticAgentConfig) config;

        assertEquals("anomaly-scorer", prob.getAgentId());
        assertEquals(ProbabilisticSubtype.ML_MODEL, prob.getSubtype());
        assertEquals("fraud-xgboost", prob.getModelName());
        assertEquals("3.2", prob.getModelVersion());
        assertEquals("http://ml-service:8080/predict", prob.getModelEndpoint());
        assertEquals(Duration.ofMillis(200), prob.getInferenceTimeout());
        assertEquals(32, prob.getBatchSize());
        assertTrue(prob.isShadowMode());
        assertEquals(2, prob.getFallbackEndpoints().size());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Hybrid Agent
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("Hybrid agent — YAML materialization")
    void hybrid() {
        String yaml = """
                agentId: hybrid-detector
                type: HYBRID
                strategy: DETERMINISTIC_FIRST
                deterministicAgentId: rule-agent
                probabilisticAgentId: ml-agent
                escalationConfidenceThreshold: 0.7
                """;

        AgentConfig config = materializer.materialize(yaml);

        assertInstanceOf(HybridAgentConfig.class, config);
        HybridAgentConfig hybrid = (HybridAgentConfig) config;

        assertEquals("hybrid-detector", hybrid.getAgentId());
        assertEquals(HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST, hybrid.getStrategy());
        assertEquals("rule-agent", hybrid.getDeterministicAgentId());
        assertEquals("ml-agent", hybrid.getProbabilisticAgentId());
        assertEquals(0.7, hybrid.getEscalationConfidenceThreshold(), 0.001);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Adaptive Agent
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("Adaptive agent — YAML materialization")
    void adaptive() {
        String yaml = """
                agentId: ab-tester
                type: ADAPTIVE
                subtype: BANDIT
                banditAlgorithm: THOMPSON_SAMPLING
                explorationRate: 0.15
                tunedParameter: threshold
                parameterMin: 0.1
                parameterMax: 0.9
                armCount: 5
                objectiveMetric: precision
                maximize: true
                """;

        AgentConfig config = materializer.materialize(yaml);

        assertInstanceOf(AdaptiveAgentConfig.class, config);
        AdaptiveAgentConfig adaptive = (AdaptiveAgentConfig) config;

        assertEquals("ab-tester", adaptive.getAgentId());
        assertEquals(AdaptiveAgentConfig.AdaptiveSubtype.BANDIT, adaptive.getSubtype());
        assertEquals(AdaptiveAgentConfig.BanditAlgorithm.THOMPSON_SAMPLING,
                adaptive.getBanditAlgorithm());
        assertEquals(0.15, adaptive.getExplorationRate(), 0.001);
        assertEquals("threshold", adaptive.getTunedParameter());
        assertEquals(0.1, adaptive.getParameterMin(), 0.001);
        assertEquals(0.9, adaptive.getParameterMax(), 0.001);
        assertEquals(5, adaptive.getArmCount());
        assertEquals("precision", adaptive.getObjectiveMetric());
        assertTrue(adaptive.isMaximize());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Composite Agent
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @DisplayName("Composite agent — YAML materialization")
    void composite() {
        String yaml = """
                agentId: ensemble-detector
                type: COMPOSITE
                subtype: ENSEMBLE
                aggregationStrategy: WEIGHTED_AVERAGE
                subAgentIds:
                  - agent-a
                  - agent-b
                  - agent-c
                weights:
                  - 0.5
                  - 0.3
                  - 0.2
                votingField: decision
                numericField: score
                """;

        AgentConfig config = materializer.materialize(yaml);

        assertInstanceOf(CompositeAgentConfig.class, config);
        CompositeAgentConfig composite = (CompositeAgentConfig) config;

        assertEquals("ensemble-detector", composite.getAgentId());
        assertEquals(CompositeAgentConfig.CompositeSubtype.ENSEMBLE, composite.getSubtype());
        assertEquals(CompositeAgentConfig.AggregationStrategy.WEIGHTED_AVERAGE,
                composite.getAggregationStrategy());
        assertEquals(List.of("agent-a", "agent-b", "agent-c"),
                composite.getSubAgentIds());
        assertEquals(3, composite.getWeights().size());
        assertEquals(0.5, composite.getWeights().get(0), 0.001);
        assertEquals("decision", composite.getVotingField());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Reactive Agent
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @DisplayName("Reactive agent — YAML materialization with triggers")
    void reactive() {
        String yaml = """
                agentId: alert-trigger
                type: REACTIVE
                subtype: TRIGGER
                triggers:
                  - name: high-amount-alert
                    eventTypeField: type
                    eventTypeValue: TRANSACTION
                    conditionField: amount
                    conditionOperator: GT
                    conditionValue: 10000
                    threshold: 3
                    countingWindow: PT5M
                    cooldown: PT1M
                    priority: 10
                    actions:
                      notify: security-team
                      severity: HIGH
                """;

        AgentConfig config = materializer.materialize(yaml);

        assertInstanceOf(ReactiveAgentConfig.class, config);
        ReactiveAgentConfig reactive = (ReactiveAgentConfig) config;

        assertEquals("alert-trigger", reactive.getAgentId());
        assertEquals(ReactiveAgentConfig.ReactiveSubtype.TRIGGER, reactive.getSubtype());
        assertEquals(1, reactive.getTriggers().size());

        ReactiveAgentConfig.TriggerDefinition trigger = reactive.getTriggers().getFirst();
        assertEquals("high-amount-alert", trigger.getName());
        assertEquals("type", trigger.getEventTypeField());
        assertEquals("TRANSACTION", trigger.getEventTypeValue());
        assertEquals("amount", trigger.getConditionField());
        assertEquals("GT", trigger.getConditionOperator());
        assertEquals(10000, ((Number) trigger.getConditionValue()).intValue());
        assertEquals(3, trigger.getThreshold());
        assertEquals(Duration.ofMinutes(5), trigger.getCountingWindow());
        assertEquals(Duration.ofMinutes(1), trigger.getCooldown());
        assertEquals(10, trigger.getPriority());
        assertEquals("security-team", trigger.getActions().get("notify"));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Base Field Defaults
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(10)
    @DisplayName("Minimal YAML — uses defaults for omitted base fields")
    void minimalYaml_defaults() {
        String yaml = """
                agentId: minimal-agent
                type: DETERMINISTIC
                """;

        AgentConfig config = materializer.materialize(yaml);

        assertEquals("1.0.0", config.getVersion());
        assertEquals(Duration.ofSeconds(5), config.getTimeout());
        assertEquals(0.5, config.getConfidenceThreshold(), 0.001);
        assertEquals(0, config.getMaxRetries());
        assertTrue(config.isMetricsEnabled());
        assertTrue(config.getProperties().isEmpty());
        assertTrue(config.getLabels().isEmpty());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // File-Based Materialization
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(20)
    @DisplayName("Materialize from YAML file path")
    void materializeFromFile() throws IOException {
        Path yaml = tempDir.resolve("agent.yaml");
        Files.writeString(yaml, """
                agentId: file-agent
                type: COMPOSITE
                subtype: VOTING
                aggregationStrategy: MAJORITY_VOTE
                subAgentIds:
                  - voter-1
                  - voter-2
                """);

        AgentConfig config = materializer.materialize(yaml);

        assertInstanceOf(CompositeAgentConfig.class, config);
        assertEquals("file-agent", config.getAgentId());
    }

    @Test
    @Order(21)
    @DisplayName("Materialize directory of YAML files")
    void materializeDirectory() throws IOException {
        Files.writeString(tempDir.resolve("agent1.yaml"), """
                agentId: agent-1
                type: DETERMINISTIC
                """);
        Files.writeString(tempDir.resolve("agent2.yaml"), """
                agentId: agent-2
                type: PROBABILISTIC
                """);
        Files.writeString(tempDir.resolve("not-yaml.txt"), "ignore me");

        List<AgentConfig> configs = materializer.materializeDirectory(tempDir);

        assertEquals(2, configs.size());
        assertTrue(configs.stream().anyMatch(c -> c.getAgentId().equals("agent-1")));
        assertTrue(configs.stream().anyMatch(c -> c.getAgentId().equals("agent-2")));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Error Cases
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(30)
    @DisplayName("Missing agentId throws")
    void missingAgentId_throws() {
        String yaml = """
                type: DETERMINISTIC
                """;

        assertThrows(NullPointerException.class,
                () -> materializer.materialize(yaml));
    }

    @Test
    @Order(31)
    @DisplayName("Missing type throws")
    void missingType_throws() {
        String yaml = """
                agentId: no-type
                """;

        assertThrows(NullPointerException.class,
                () -> materializer.materialize(yaml));
    }

    @Test
    @Order(32)
    @DisplayName("Unknown type throws AgentMaterializationException")
    void unknownType_throws() {
        String yaml = """
                agentId: bad-type
                type: QUANTUM
                """;

        AgentMaterializationException ex = assertThrows(
                AgentMaterializationException.class,
                () -> materializer.materialize(yaml));
        assertTrue(ex.getMessage().contains("Unknown agent type: 'QUANTUM'"));
    }

    @Test
    @Order(33)
    @DisplayName("Invalid YAML throws AgentMaterializationException")
    void invalidYaml_throws() {
        String yaml = "!!invalid: [yaml: {{}}]\\n  broken";

        assertThrows(AgentMaterializationException.class,
                () -> materializer.materialize(yaml));
    }

    @Test
    @Order(34)
    @DisplayName("Non-directory path throws")
    void nonDirectory_throws() throws IOException {
        Path file = tempDir.resolve("single.yaml");
        Files.writeString(file, "agentId: x\ntype: DETERMINISTIC\n");

        assertThrows(AgentMaterializationException.class,
                () -> materializer.materializeDirectory(file));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // AgentConfigDto
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(40)
    @DisplayName("AgentConfigDto — extra property accessors")
    void dtoExtraProperties() {
        AgentConfigDto dto = new AgentConfigDto();
        dto.setExtraProperty("strVal", "hello");
        dto.setExtraProperty("boolVal", true);
        dto.setExtraProperty("intVal", 42);
        dto.setExtraProperty("doubleVal", 3.14);
        dto.setExtraProperty("listVal", List.of("a", "b"));
        dto.setExtraProperty("mapVal", Map.of("k", "v"));

        assertEquals("hello", dto.getExtraString("strVal"));
        assertTrue(dto.getExtraBoolean("boolVal"));
        assertEquals(42, dto.getExtraInt("intVal"));
        assertEquals(3.14, dto.getExtraDouble("doubleVal"), 0.001);
        assertEquals(List.of("a", "b"), dto.getExtraList("listVal"));
        assertEquals(Map.of("k", "v"), dto.getExtraMap("mapVal"));

        // Missing keys return null
        assertNull(dto.getExtraString("missing"));
        assertNull(dto.getExtraBoolean("missing"));
        assertNull(dto.getExtraInt("missing"));
    }

    @Test
    @Order(41)
    @DisplayName("parseDuration — ISO-8601 and seconds")
    void parseDuration() {
        assertEquals(Duration.ofSeconds(5),
                AgentConfigDto.parseDuration("PT5S", Duration.ZERO));
        assertEquals(Duration.ofMinutes(1),
                AgentConfigDto.parseDuration("PT1M", Duration.ZERO));
        assertEquals(Duration.ofSeconds(30),
                AgentConfigDto.parseDuration("30", Duration.ZERO));
        assertEquals(Duration.ofHours(1),
                AgentConfigDto.parseDuration(null, Duration.ofHours(1)));
        assertEquals(Duration.ofHours(2),
                AgentConfigDto.parseDuration("", Duration.ofHours(2)));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Properties & Labels Propagation
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(50)
    @DisplayName("Properties and labels propagated from YAML")
    void propertiesAndLabels() {
        String yaml = """
                agentId: labeled-agent
                type: DETERMINISTIC
                properties:
                  key1: value1
                  key2: 42
                labels:
                  env: production
                  owner: platform-team
                requiredCapabilities:
                  - gpu
                  - high-memory
                """;

        AgentConfig config = materializer.materialize(yaml);

        assertEquals("value1", config.getProperties().get("key1"));
        assertEquals(42, config.getProperties().get("key2"));
        assertEquals("production", config.getLabels().get("env"));
        assertEquals("platform-team", config.getLabels().get("owner"));
        assertTrue(config.getRequiredCapabilities().contains("gpu"));
        assertTrue(config.getRequiredCapabilities().contains("high-memory"));
    }
}
