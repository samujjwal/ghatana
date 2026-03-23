/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.aep.config;

import com.ghatana.aep.domain.pipeline.AgentSpec;
import com.ghatana.aep.domain.pipeline.PipelineSpec;
import com.ghatana.aep.domain.pipeline.PipelineStageSpec;
import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.pipeline.Pipeline;
import com.ghatana.core.pipeline.PipelineEdge;
import com.ghatana.core.pipeline.PipelineStage;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PipelineMaterializer} — PipelineSpec → Pipeline conversion.
 *
 * Covers:
 * - Single-stage pipeline materialization
 * - Multi-stage with sequential auto-chaining
 * - Explicit dependency edges
 * - Error/escalation edges
 * - OperatorId resolution (simple name, fully-qualified, custom resolver)
 * - Stage config propagation (role, tasks, I/O specs, connectors)
 * - Edge cases: null spec, empty stages, missing agent names
 * - Children agents
 * - HITL flag propagation
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PipelineMaterializerTest {

    private PipelineMaterializer materializer;

    @BeforeEach
    void setUp() {
        materializer = new PipelineMaterializer();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Basic Materialization
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("Single-stage pipeline with one agent")
    void singleStage_oneAgent() {
        PipelineSpec spec = PipelineSpec.builder()
                .stages(List.of(
                        PipelineStageSpec.builder()
                                .name("ingestion")
                                .workflow(List.of(
                                        AgentSpec.builder()
                                                .id("ingest-agent")
                                                .agent("data-ingester")
                                                .role("collector")
                                                .build()))
                                .build()))
                .build();

        Pipeline pipeline = materializer.materialize(spec, "simple-pipeline", "1.0.0");

        assertNotNull(pipeline);
        assertEquals("simple-pipeline", pipeline.getId());
        assertEquals("1.0.0", pipeline.getVersion());
        assertEquals(1, pipeline.getStages().size());

        PipelineStage stage = pipeline.getStages().getFirst();
        assertEquals("ingestion.ingest-agent", stage.stageId());
        assertEquals("aep", stage.operatorId().getNamespace());
        assertEquals("agent", stage.operatorId().getType());
        assertEquals("data-ingester", stage.operatorId().getName());
        assertEquals("latest", stage.operatorId().getVersion());
        assertEquals("collector", stage.config().get("role"));
    }

    @Test
    @Order(2)
    @DisplayName("Multi-stage sequential auto-chaining")
    void multiStage_autoChaining() {
        PipelineSpec spec = PipelineSpec.builder()
                .stages(List.of(
                        stage("extract", "extractor"),
                        stage("transform", "transformer"),
                        stage("load", "loader")))
                .build();

        Pipeline pipeline = materializer.materialize(spec, "etl", "2.0.0");

        assertEquals(3, pipeline.getStages().size());
        // Should have 2 primary edges: extract→transform, transform→load
        List<PipelineEdge> edges = pipeline.getEdges();
        assertEquals(2, edges.size());
        assertTrue(edges.stream().allMatch(PipelineEdge::isPrimary));

        assertEquals("extract.extractor", edges.get(0).from());
        assertEquals("transform.transformer", edges.get(0).to());
        assertEquals("transform.transformer", edges.get(1).from());
        assertEquals("load.loader", edges.get(1).to());
    }

    @Test
    @Order(3)
    @DisplayName("Stage with no workflow (pass-through)")
    void stage_noWorkflow_passthrough() {
        PipelineSpec spec = PipelineSpec.builder()
                .stages(List.of(
                        PipelineStageSpec.builder()
                                .name("passthrough")
                                .stageType("noop")
                                .build()))
                .build();

        Pipeline pipeline = materializer.materialize(spec, "pass", "1.0.0");

        assertEquals(1, pipeline.getStages().size());
        PipelineStage stage = pipeline.getStages().getFirst();
        assertEquals("passthrough", stage.stageId());
        assertEquals("noop", stage.config().get("stageType"));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Dependency Edges
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(10)
    @DisplayName("Explicit dependency edges override auto-chaining")
    void explicitDependencies() {
        PipelineSpec spec = PipelineSpec.builder()
                .stages(List.of(
                        stageWithId("collect", "collector", "c1"),
                        stageWithId("enrich", "enricher", "e1",
                                List.of("c1"), null),
                        stageWithId("detect", "detector", "d1",
                                List.of("c1"), null))) // depends on c1, not e1
                .build();

        Pipeline pipeline = materializer.materialize(spec, "fan-out", "1.0.0");

        assertEquals(3, pipeline.getStages().size());

        // e1 depends on c1 (explicit), d1 depends on c1 (explicit)
        // d1 is wired, so no auto-chain from e1→d1
        List<PipelineEdge> edges = pipeline.getEdges();

        // Should have: c1→e1 (explicit), c1→d1 (explicit), e1→d1 auto-chain only if d1 unwired
        // Actually both e1 and d1 are wired (they have dependencies), so only explicit edges
        // Plus auto-chain: unwired = not having any dep. c1 has no deps → auto-chain from nothing before it
        // e1 has dep → wired. d1 has dep → wired.
        // Auto-chain: c1 (i=0)→e1 (i=1) only if e1 unwired → e1 IS wired → skip
        // e1 (i=1)→d1 (i=2) only if d1 unwired → d1 IS wired → skip
        assertEquals(2, edges.size());
        assertTrue(edges.stream().allMatch(PipelineEdge::isPrimary));
    }

    @Test
    @Order(11)
    @DisplayName("Error/escalation edges")
    void errorEscalation() {
        PipelineSpec spec = PipelineSpec.builder()
                .stages(List.of(
                        stageWithId("process", "processor", "p1"),
                        stageWithId("handle-error", "error-handler", "eh1",
                                null, List.of()))) // No escalation targets
                .build();

        // Add an escalation from p1 → eh1
        AgentSpec processAgent = AgentSpec.builder()
                .id("p1")
                .agent("processor")
                .failureEscalation(List.of("eh1"))
                .build();
        PipelineSpec specWithEscalation = PipelineSpec.builder()
                .stages(List.of(
                        PipelineStageSpec.builder()
                                .name("process")
                                .workflow(List.of(processAgent))
                                .build(),
                        stageWithId("handle-error", "error-handler", "eh1",
                                null, null)))
                .build();

        Pipeline pipeline = materializer.materialize(specWithEscalation, "escalation", "1.0.0");

        // Should have: error edge p1→eh1, plus auto-chain for unwired nodes
        List<PipelineEdge> errorEdges = pipeline.getEdges().stream()
                .filter(PipelineEdge::isError)
                .toList();
        assertEquals(1, errorEdges.size());
        assertTrue(errorEdges.getFirst().from().contains("p1"));
        assertTrue(errorEdges.getFirst().to().contains("eh1"));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // OperatorId Resolution
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(20)
    @DisplayName("Fully-qualified operator ID parsed directly")
    void fullyQualifiedOperatorId() {
        AgentSpec agent = AgentSpec.builder()
                .id("fq")
                .agent("myns:mytype:myname:1.2.3")
                .build();
        PipelineSpec spec = PipelineSpec.builder()
                .stages(List.of(
                        PipelineStageSpec.builder()
                                .name("stage1")
                                .workflow(List.of(agent))
                                .build()))
                .build();

        Pipeline pipeline = materializer.materialize(spec, "fq-test", "1.0.0");

        OperatorId opId = pipeline.getStages().getFirst().operatorId();
        assertEquals("myns", opId.getNamespace());
        assertEquals("mytype", opId.getType());
        assertEquals("myname", opId.getName());
        assertEquals("1.2.3", opId.getVersion());
    }

    @Test
    @Order(21)
    @DisplayName("Custom OperatorIdResolver is used")
    void customResolver() {
        OperatorId custom = OperatorId.of("custom", "op", "resolved", "9.9.9");
        PipelineMaterializer withResolver = new PipelineMaterializer(name -> custom);

        PipelineSpec spec = PipelineSpec.builder()
                .stages(List.of(stage("s", "any-agent")))
                .build();

        Pipeline pipeline = withResolver.materialize(spec, "custom-resolve", "1.0.0");

        OperatorId opId = pipeline.getStages().getFirst().operatorId();
        assertEquals("custom", opId.getNamespace());
        assertEquals("resolved", opId.getName());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Config Propagation
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(30)
    @DisplayName("Agent config propagated to stage config map")
    void agentConfig_propagated() {
        AgentSpec agent = AgentSpec.builder()
                .id("a1")
                .agent("enricher")
                .role("data-enrichment")
                .agentTasks(List.of("enrich-geo", "enrich-device"))
                .acceptanceCriteria(List.of("geo must be present"))
                .hitl(true)
                .hitlReason("Sensitive data enrichment")
                .build();

        PipelineSpec spec = PipelineSpec.builder()
                .stages(List.of(
                        PipelineStageSpec.builder()
                                .name("enrich")
                                .stageType("enrichment")
                                .connectorIds(List.of("geo-api", "device-db"))
                                .workflow(List.of(agent))
                                .build()))
                .build();

        Pipeline pipeline = materializer.materialize(spec, "config-test", "1.0.0");

        Map<String, Object> config = pipeline.getStages().getFirst().config();
        assertEquals("data-enrichment", config.get("role"));
        assertEquals(List.of("enrich-geo", "enrich-device"), config.get("agentTasks"));
        assertEquals(List.of("geo-api", "device-db"), config.get("connectorIds"));
        assertEquals("enrichment", config.get("stageType"));
        assertEquals(true, config.get("hitl"));
        assertEquals("Sensitive data enrichment", config.get("hitlReason"));
    }

    @Test
    @Order(31)
    @DisplayName("Metadata includes materializedFrom and stageCount")
    void metadata_present() {
        PipelineSpec spec = PipelineSpec.builder()
                .stages(List.of(stage("s1", "a1"), stage("s2", "a2")))
                .build();

        Pipeline pipeline = materializer.materialize(spec, "meta-test", "1.0.0");

        assertEquals("PipelineSpec", pipeline.getMetadata().get("materializedFrom"));
        assertEquals(2, pipeline.getMetadata().get("stageCount"));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Error Cases
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(40)
    @DisplayName("Null spec throws NullPointerException")
    void nullSpec_throws() {
        assertThrows(NullPointerException.class,
                () -> materializer.materialize(null, "x", "1.0"));
    }

    @Test
    @Order(41)
    @DisplayName("Empty stages throws PipelineMaterializationException")
    void emptyStages_throws() {
        PipelineSpec spec = PipelineSpec.builder()
                .stages(List.of())
                .build();

        assertThrows(PipelineMaterializationException.class,
                () -> materializer.materialize(spec, "empty", "1.0"));
    }

    @Test
    @Order(42)
    @DisplayName("Stage with no name throws")
    void noStageName_throws() {
        PipelineSpec spec = PipelineSpec.builder()
                .stages(List.of(
                        PipelineStageSpec.builder()
                                .workflow(List.of(AgentSpec.builder()
                                        .agent("x").build()))
                                .build()))
                .build();

        assertThrows(PipelineMaterializationException.class,
                () -> materializer.materialize(spec, "no-name", "1.0"));
    }

    @Test
    @Order(43)
    @DisplayName("Agent with no name and no id throws")
    void noAgentNameOrId_throws() {
        PipelineSpec spec = PipelineSpec.builder()
                .stages(List.of(
                        PipelineStageSpec.builder()
                                .name("stage1")
                                .workflow(List.of(AgentSpec.builder().build()))
                                .build()))
                .build();

        assertThrows(PipelineMaterializationException.class,
                () -> materializer.materialize(spec, "no-agent", "1.0"));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Multi-agent workflow in single stage
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(50)
    @DisplayName("Multiple agents in one stage create multiple pipeline nodes")
    void multiAgentStage() {
        PipelineSpec spec = PipelineSpec.builder()
                .stages(List.of(
                        PipelineStageSpec.builder()
                                .name("detection")
                                .workflow(List.of(
                                        AgentSpec.builder()
                                                .id("det1")
                                                .agent("anomaly-detector")
                                                .build(),
                                        AgentSpec.builder()
                                                .id("det2")
                                                .agent("fraud-detector")
                                                .build()))
                                .build()))
                .build();

        Pipeline pipeline = materializer.materialize(spec, "multi-agent", "1.0.0");

        assertEquals(2, pipeline.getStages().size());
        assertEquals("detection.det1", pipeline.getStages().get(0).stageId());
        assertEquals("detection.det2", pipeline.getStages().get(1).stageId());

        // Auto-chain: det1 → det2
        assertEquals(1, pipeline.getEdges().size());
        assertEquals("detection.det1", pipeline.getEdges().getFirst().from());
        assertEquals("detection.det2", pipeline.getEdges().getFirst().to());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Convenience overload
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @Order(60)
    @DisplayName("materialize(spec, id) uses default version 1.0.0")
    void defaultVersion() {
        PipelineSpec spec = PipelineSpec.builder()
                .stages(List.of(stage("s", "a")))
                .build();

        Pipeline pipeline = materializer.materialize(spec, "default-ver");
        assertEquals("1.0.0", pipeline.getVersion());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Test helpers
    // ═════════════════════════════════════════════════════════════════════════

    private static PipelineStageSpec stage(String name, String agentName) {
        return PipelineStageSpec.builder()
                .name(name)
                .workflow(List.of(AgentSpec.builder()
                        .id(agentName)
                        .agent(agentName)
                        .build()))
                .build();
    }

    private static PipelineStageSpec stageWithId(String name, String agentName, String agentId) {
        return stageWithId(name, agentName, agentId, null, null);
    }

    private static PipelineStageSpec stageWithId(String name, String agentName, String agentId,
                                                  List<String> deps, List<String> escalation) {
        return PipelineStageSpec.builder()
                .name(name)
                .workflow(List.of(AgentSpec.builder()
                        .id(agentId)
                        .agent(agentName)
                        .dependencies(deps)
                        .failureEscalation(escalation)
                        .build()))
                .build();
    }
}
