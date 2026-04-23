/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
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
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // GH-90000
class AgentConfigMaterializerTest {

    private AgentConfigMaterializer materializer;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() { // GH-90000
        materializer = new AgentConfigMaterializer(); // GH-90000
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Deterministic Agent
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1) // GH-90000
    @DisplayName("Deterministic agent — YAML materialization")
    void deterministic() { // GH-90000
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

        AgentConfig config = materializer.materialize(yaml); // GH-90000

        assertInstanceOf(DeterministicAgentConfig.class, config); // GH-90000
        DeterministicAgentConfig det = (DeterministicAgentConfig) config; // GH-90000

        assertEquals("fraud-detector", det.getAgentId()); // GH-90000
        assertEquals(AgentType.DETERMINISTIC, det.getType()); // GH-90000
        assertEquals("2.1.0", det.getVersion()); // GH-90000
        assertEquals(Duration.ofSeconds(5), det.getTimeout()); // GH-90000
        assertEquals(0.85, det.getConfidenceThreshold(), 0.001); // GH-90000
        assertEquals(3, det.getMaxRetries()); // GH-90000
        assertEquals(FailureMode.CIRCUIT_BREAKER, det.getFailureMode()); // GH-90000
        assertTrue(det.isMetricsEnabled()); // GH-90000
        assertEquals("security", det.getLabels().get("team"));
        assertEquals(DeterministicSubtype.RULE_BASED, det.getSubtype()); // GH-90000
        assertTrue(det.isEvaluateAllRules()); // GH-90000
        assertEquals("txnType", det.getExactMatchField()); // GH-90000
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Probabilistic Agent
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(2) // GH-90000
    @DisplayName("Probabilistic agent — YAML materialization")
    void probabilistic() { // GH-90000
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

        AgentConfig config = materializer.materialize(yaml); // GH-90000

        assertInstanceOf(ProbabilisticAgentConfig.class, config); // GH-90000
        ProbabilisticAgentConfig prob = (ProbabilisticAgentConfig) config; // GH-90000

        assertEquals("anomaly-scorer", prob.getAgentId()); // GH-90000
        assertEquals(ProbabilisticSubtype.ML_MODEL, prob.getSubtype()); // GH-90000
        assertEquals("fraud-xgboost", prob.getModelName()); // GH-90000
        assertEquals("3.2", prob.getModelVersion()); // GH-90000
        assertEquals("http://ml-service:8080/predict", prob.getModelEndpoint()); // GH-90000
        assertEquals(Duration.ofMillis(200), prob.getInferenceTimeout()); // GH-90000
        assertEquals(32, prob.getBatchSize()); // GH-90000
        assertTrue(prob.isShadowMode()); // GH-90000
        assertEquals(2, prob.getFallbackEndpoints().size()); // GH-90000
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Hybrid Agent
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(3) // GH-90000
    @DisplayName("Hybrid agent — YAML materialization")
    void hybrid() { // GH-90000
        String yaml = """
                agentId: hybrid-detector
                type: HYBRID
                strategy: DETERMINISTIC_FIRST
                deterministicAgentId: rule-agent
                probabilisticAgentId: ml-agent
                escalationConfidenceThreshold: 0.7
                """;

        AgentConfig config = materializer.materialize(yaml); // GH-90000

        assertInstanceOf(HybridAgentConfig.class, config); // GH-90000
        HybridAgentConfig hybrid = (HybridAgentConfig) config; // GH-90000

        assertEquals("hybrid-detector", hybrid.getAgentId()); // GH-90000
        assertEquals(HybridAgentConfig.RoutingStrategy.DETERMINISTIC_FIRST, hybrid.getStrategy()); // GH-90000
        assertEquals("rule-agent", hybrid.getDeterministicAgentId()); // GH-90000
        assertEquals("ml-agent", hybrid.getProbabilisticAgentId()); // GH-90000
        assertEquals(0.7, hybrid.getEscalationConfidenceThreshold(), 0.001); // GH-90000
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Adaptive Agent
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(4) // GH-90000
    @DisplayName("Adaptive agent — YAML materialization")
    void adaptive() { // GH-90000
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

        AgentConfig config = materializer.materialize(yaml); // GH-90000

        assertInstanceOf(AdaptiveAgentConfig.class, config); // GH-90000
        AdaptiveAgentConfig adaptive = (AdaptiveAgentConfig) config; // GH-90000

        assertEquals("ab-tester", adaptive.getAgentId()); // GH-90000
        assertEquals(AdaptiveAgentConfig.AdaptiveSubtype.BANDIT, adaptive.getSubtype()); // GH-90000
        assertEquals(AdaptiveAgentConfig.BanditAlgorithm.THOMPSON_SAMPLING, // GH-90000
                adaptive.getBanditAlgorithm()); // GH-90000
        assertEquals(0.15, adaptive.getExplorationRate(), 0.001); // GH-90000
        assertEquals("threshold", adaptive.getTunedParameter()); // GH-90000
        assertEquals(0.1, adaptive.getParameterMin(), 0.001); // GH-90000
        assertEquals(0.9, adaptive.getParameterMax(), 0.001); // GH-90000
        assertEquals(5, adaptive.getArmCount()); // GH-90000
        assertEquals("precision", adaptive.getObjectiveMetric()); // GH-90000
        assertTrue(adaptive.isMaximize()); // GH-90000
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Composite Agent
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(5) // GH-90000
    @DisplayName("Composite agent — YAML materialization")
    void composite() { // GH-90000
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

        AgentConfig config = materializer.materialize(yaml); // GH-90000

        assertInstanceOf(CompositeAgentConfig.class, config); // GH-90000
        CompositeAgentConfig composite = (CompositeAgentConfig) config; // GH-90000

        assertEquals("ensemble-detector", composite.getAgentId()); // GH-90000
        assertEquals(CompositeAgentConfig.CompositeSubtype.ENSEMBLE, composite.getSubtype()); // GH-90000
        assertEquals(CompositeAgentConfig.AggregationStrategy.WEIGHTED_AVERAGE, // GH-90000
                composite.getAggregationStrategy()); // GH-90000
        assertEquals(List.of("agent-a", "agent-b", "agent-c"), // GH-90000
                composite.getSubAgentIds()); // GH-90000
        assertEquals(3, composite.getWeights().size()); // GH-90000
        assertEquals(0.5, composite.getWeights().get(0), 0.001); // GH-90000
        assertEquals("decision", composite.getVotingField()); // GH-90000
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Reactive Agent
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(6) // GH-90000
    @DisplayName("Reactive agent — YAML materialization with triggers")
    void reactive() { // GH-90000
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

        AgentConfig config = materializer.materialize(yaml); // GH-90000

        assertInstanceOf(ReactiveAgentConfig.class, config); // GH-90000
        ReactiveAgentConfig reactive = (ReactiveAgentConfig) config; // GH-90000

        assertEquals("alert-trigger", reactive.getAgentId()); // GH-90000
        assertEquals(ReactiveAgentConfig.ReactiveSubtype.TRIGGER, reactive.getSubtype()); // GH-90000
        assertEquals(1, reactive.getTriggers().size()); // GH-90000

        ReactiveAgentConfig.TriggerDefinition trigger = reactive.getTriggers().getFirst(); // GH-90000
        assertEquals("high-amount-alert", trigger.getName()); // GH-90000
        assertEquals("type", trigger.getEventTypeField()); // GH-90000
        assertEquals("TRANSACTION", trigger.getEventTypeValue()); // GH-90000
        assertEquals("amount", trigger.getConditionField()); // GH-90000
        assertEquals("GT", trigger.getConditionOperator()); // GH-90000
        assertEquals(10000, ((Number) trigger.getConditionValue()).intValue()); // GH-90000
        assertEquals(3, trigger.getThreshold()); // GH-90000
        assertEquals(Duration.ofMinutes(5), trigger.getCountingWindow()); // GH-90000
        assertEquals(Duration.ofMinutes(1), trigger.getCooldown()); // GH-90000
        assertEquals(10, trigger.getPriority()); // GH-90000
        assertEquals("security-team", trigger.getActions().get("notify"));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Base Field Defaults
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(10) // GH-90000
    @DisplayName("Minimal YAML — uses defaults for omitted base fields")
    void minimalYaml_defaults() { // GH-90000
        String yaml = """
                agentId: minimal-agent
                type: DETERMINISTIC
                """;

        AgentConfig config = materializer.materialize(yaml); // GH-90000

        assertEquals("1.0.0", config.getVersion()); // GH-90000
        assertEquals(Duration.ofSeconds(5), config.getTimeout()); // GH-90000
        assertEquals(0.5, config.getConfidenceThreshold(), 0.001); // GH-90000
        assertEquals(0, config.getMaxRetries()); // GH-90000
        assertTrue(config.isMetricsEnabled()); // GH-90000
        assertTrue(config.getProperties().isEmpty()); // GH-90000
        assertTrue(config.getLabels().isEmpty()); // GH-90000
    }

    // ═════════════════════════════════════════════════════════════════════════
    // File-Based Materialization
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(20) // GH-90000
    @DisplayName("Materialize from YAML file path")
    void materializeFromFile() throws IOException { // GH-90000
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

        AgentConfig config = materializer.materialize(yaml); // GH-90000

        assertInstanceOf(CompositeAgentConfig.class, config); // GH-90000
        assertEquals("file-agent", config.getAgentId()); // GH-90000
    }

    @Test
    @Order(21) // GH-90000
    @DisplayName("Materialize directory of YAML files")
    void materializeDirectory() throws IOException { // GH-90000
        Files.writeString(tempDir.resolve("agent1.yaml"), """
                agentId: agent-1
                type: DETERMINISTIC
                """);
        Files.writeString(tempDir.resolve("agent2.yaml"), """
                agentId: agent-2
                type: PROBABILISTIC
                """);
        Files.writeString(tempDir.resolve("not-yaml.txt"), "ignore me");

        List<AgentConfig> configs = materializer.materializeDirectory(tempDir); // GH-90000

        assertEquals(2, configs.size()); // GH-90000
        assertTrue(configs.stream().anyMatch(c -> c.getAgentId().equals("agent-1")));
        assertTrue(configs.stream().anyMatch(c -> c.getAgentId().equals("agent-2")));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Error Cases
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(30) // GH-90000
    @DisplayName("Missing agentId throws")
    void missingAgentId_throws() { // GH-90000
        String yaml = """
                type: DETERMINISTIC
                """;

        assertThrows(NullPointerException.class, // GH-90000
                () -> materializer.materialize(yaml)); // GH-90000
    }

    @Test
    @Order(31) // GH-90000
    @DisplayName("Missing type throws")
    void missingType_throws() { // GH-90000
        String yaml = """
                agentId: no-type
                """;

        assertThrows(NullPointerException.class, // GH-90000
                () -> materializer.materialize(yaml)); // GH-90000
    }

    @Test
    @Order(32) // GH-90000
    @DisplayName("Unknown type throws AgentMaterializationException")
    void unknownType_throws() { // GH-90000
        String yaml = """
                agentId: bad-type
                type: QUANTUM
                """;

        AgentMaterializationException ex = assertThrows( // GH-90000
                AgentMaterializationException.class,
                () -> materializer.materialize(yaml)); // GH-90000
        assertTrue(ex.getMessage().contains("Unknown agent type: 'QUANTUM'"));
    }

    @Test
    @Order(33) // GH-90000
    @DisplayName("Invalid YAML throws AgentMaterializationException")
    void invalidYaml_throws() { // GH-90000
        String yaml = "!!invalid: [yaml: {{}}]\\n  broken";

        assertThrows(AgentMaterializationException.class, // GH-90000
                () -> materializer.materialize(yaml)); // GH-90000
    }

    @Test
    @Order(34) // GH-90000
    @DisplayName("Non-directory path throws")
    void nonDirectory_throws() throws IOException { // GH-90000
        Path file = tempDir.resolve("single.yaml");
        Files.writeString(file, "agentId: x\ntype: DETERMINISTIC\n"); // GH-90000

        assertThrows(AgentMaterializationException.class, // GH-90000
                () -> materializer.materializeDirectory(file)); // GH-90000
    }

    // ═════════════════════════════════════════════════════════════════════════
    // AgentConfigDto
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(40) // GH-90000
    @DisplayName("AgentConfigDto — extra property accessors")
    void dtoExtraProperties() { // GH-90000
        AgentConfigDto dto = new AgentConfigDto(); // GH-90000
        dto.setExtraProperty("strVal", "hello"); // GH-90000
        dto.setExtraProperty("boolVal", true); // GH-90000
        dto.setExtraProperty("intVal", 42); // GH-90000
        dto.setExtraProperty("doubleVal", 3.14); // GH-90000
        dto.setExtraProperty("listVal", List.of("a", "b")); // GH-90000
        dto.setExtraProperty("mapVal", Map.of("k", "v")); // GH-90000

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
    @Order(41) // GH-90000
    @DisplayName("parseDuration — ISO-8601 and seconds")
    void parseDuration() { // GH-90000
        assertEquals(Duration.ofSeconds(5), // GH-90000
                AgentConfigDto.parseDuration("PT5S", Duration.ZERO)); // GH-90000
        assertEquals(Duration.ofMinutes(1), // GH-90000
                AgentConfigDto.parseDuration("PT1M", Duration.ZERO)); // GH-90000
        assertEquals(Duration.ofSeconds(30), // GH-90000
                AgentConfigDto.parseDuration("30", Duration.ZERO)); // GH-90000
        assertEquals(Duration.ofHours(1), // GH-90000
                AgentConfigDto.parseDuration(null, Duration.ofHours(1))); // GH-90000
        assertEquals(Duration.ofHours(2), // GH-90000
                AgentConfigDto.parseDuration("", Duration.ofHours(2))); // GH-90000
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Properties & Labels Propagation
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(50) // GH-90000
    @DisplayName("Properties and labels propagated from YAML")
    void propertiesAndLabels() { // GH-90000
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

        AgentConfig config = materializer.materialize(yaml); // GH-90000

        assertEquals("value1", config.getProperties().get("key1"));
        assertEquals(42, config.getProperties().get("key2"));
        assertEquals("production", config.getLabels().get("env"));
        assertEquals("platform-team", config.getLabels().get("owner"));
        assertTrue(config.getRequiredCapabilities().contains("gpu"));
        assertTrue(config.getRequiredCapabilities().contains("high-memory"));
    }
}
