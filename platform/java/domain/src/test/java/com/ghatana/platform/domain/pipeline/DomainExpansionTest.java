/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.domain.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 Expansion tests for Domain module.
 * Tests pipeline composition, validation, YAML serialization at scale.
 *
 * @doc.type class
 * @doc.purpose Phase 3 expansion tests for domain model and pipeline specifications
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Domain - Phase 3 Expansion")
class DomainExpansionTest {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    // ============================================
    // PIPELINE SPEC COMPOSITION (4 tests)
    // ============================================

    @Nested
    @DisplayName("Pipeline Spec Composition")
    class PipelineCompositionTests {

        @Test
        @DisplayName("Build pipeline with multiple stages")
        void multipleStages() {
            List<PipelineStageSpec> stages = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                final int idx = i;
                PipelineStageSpec stage = PipelineStageSpec.builder()
                    .name("stage-" + idx)
                    .type(idx % 2 == 0 ? PipelineStageSpec.StageType.STREAM : PipelineStageSpec.StageType.PATTERN)
                    .workflow(List.of(
                        AgentSpec.builder()
                            .id("agent-" + idx)
                            .agent("test-agent")
                            .build()
                    ))
                    .build();
                stages.add(stage);
            }

            PipelineSpec spec = PipelineSpec.builder()
                .stages(stages)
                .tenantId("tenant-1")
                .build();

            assertThat(spec.getStages()).hasSize(10);
        }

        @Test
        @DisplayName("Complex connector configuration")
        void complexConnectors() {
            List<ConnectorSpec> connectors = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                final int idx = i;
                ConnectorSpec connector = ConnectorSpec.builder()
                    .id("connector-" + idx)
                    .type(idx % 3 == 0 ? ConnectorSpec.ConnectorType.EVENT_CLOUD_SOURCE : ConnectorSpec.ConnectorType.HTTP_INGRESS)
                    .endpoint("endpoint-" + idx)
                    .topicOrStream("stream-" + idx)
                    .tenantId("tenant-1")
                    .build();
                connectors.add(connector);
            }

            PipelineSpec spec = PipelineSpec.builder()
                .stages(List.of(PipelineStageSpec.builder().name("s1").build()))
                .connectors(connectors)
                .tenantId("tenant-1")
                .build();

            assertThat(spec.getConnectors()).hasSize(15);
        }

        @Test
        @DisplayName("DAG edges with fan-out and fan-in")
        void dagEdges() {
            List<PipelineEdgeSpec> edges = new ArrayList<>();

            // Create fan-out: stage-0 to many stages
            for (int i = 1; i < 6; i++) {
                final int targetIdx = i;
                edges.add(PipelineEdgeSpec.builder()
                    .fromStageId("stage-0")
                    .toStageId("stage-" + targetIdx)
                    .label("out-" + targetIdx)
                    .build());
            }

            // Create fan-in: many stages to final
            for (int i = 1; i < 6; i++) {
                final int sourceIdx = i;
                edges.add(PipelineEdgeSpec.builder()
                    .fromStageId("stage-" + sourceIdx)
                    .toStageId("stage-final")
                    .label("in-" + sourceIdx)
                    .build());
            }

            PipelineSpec spec = PipelineSpec.builder()
                .stages(List.of(PipelineStageSpec.builder().name("s").build()))
                .edges(edges)
                .tenantId("tenant-1")
                .build();

            assertThat(spec.getEdges()).hasSize(10);
        }

        @Test
        @DisplayName("Many agent hints and tags")
        void agentHintsAndTags() {
            Map<String, String> hints = new HashMap<>();
            for (int i = 0; i < 50; i++) {
                hints.put("hint-" + i, "value-" + i);
            }

            List<String> tags = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                tags.add("tag-" + i);
            }

            PipelineSpec spec = PipelineSpec.builder()
                .stages(List.of(PipelineStageSpec.builder().name("s").build()))
                .agentHints(hints)
                .tags(tags)
                .tenantId("tenant-1")
                .build();

            assertThat(spec.getAgentHints()).hasSize(50);
            assertThat(spec.getTags()).hasSize(30);
        }
    }

    // ============================================
    // YAML SERIALIZATION (4 tests)
    // ============================================

    @Nested
    @DisplayName("YAML Serialization")
    class YAMLSerializationTests {

        @Test
        @DisplayName("Round-trip serialization with complex structure")
        void roundTripComplexStructure() throws Exception {
            PipelineSpec original = PipelineSpec.builder()
                .stages(List.of(
                    PipelineStageSpec.builder()
                        .name("stage-1")
                        .type(PipelineStageSpec.StageType.STREAM)
                        .workflow(List.of(
                            AgentSpec.builder().id("agent-1").agent("test").build()
                        ))
                        .build()
                ))
                .connectors(List.of(
                    ConnectorSpec.builder()
                        .id("conn-1")
                        .type(ConnectorSpec.ConnectorType.EVENT_CLOUD_SOURCE)
                        .endpoint("ep1")
                        .topicOrStream("stream1")
                        .tenantId("t1")
                        .build()
                ))
                .edges(List.of(
                    PipelineEdgeSpec.builder()
                        .fromStageId("s1")
                        .toStageId("s2")
                        .label("edge1")
                        .build()
                ))
                .tenantId("tenant-1")
                .environment("prod")
                .tags(List.of("v1", "v2"))
                .agentHints(Map.of("key", "value"))
                .build();

            String yaml = yamlMapper.writeValueAsString(original);
            PipelineSpec deserialized = yamlMapper.readValue(yaml, PipelineSpec.class);

            assertThat(deserialized).isEqualTo(original);
        }

        @Test
        @DisplayName("Serialize and deserialize many pipelines")
        void batchSerialization() throws Exception {
            List<PipelineSpec> specs = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                final int idx = i;
                PipelineSpec spec = PipelineSpec.builder()
                    .stages(List.of(
                        PipelineStageSpec.builder()
                            .name("stage-" + idx)
                            .build()
                    ))
                    .tenantId("tenant-" + idx)
                    .environment("test")
                    .build();
                specs.add(spec);
            }

            for (PipelineSpec spec : specs) {
                String yaml = yamlMapper.writeValueAsString(spec);
                PipelineSpec deserialized = yamlMapper.readValue(yaml, PipelineSpec.class);
                assertThat(deserialized).isEqualTo(spec);
            }
        }

        @Test
        @DisplayName("Very large stage workflow YAML")
        void largeWorkflowYAML() throws Exception {
            List<AgentSpec> workflow = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                final int idx = i;
                workflow.add(AgentSpec.builder()
                    .id("agent-" + idx)
                    .agent("agent-type")
                    .references(List.of("param-" + idx + "=value-" + idx))
                    .build());
            }

            PipelineStageSpec stage = PipelineStageSpec.builder()
                .name("large-stage")
                .type(PipelineStageSpec.StageType.STREAM)
                .workflow(workflow)
                .build();

            PipelineSpec spec = PipelineSpec.builder()
                .stages(List.of(stage))
                .tenantId("tenant-1")
                .build();

            String yaml = yamlMapper.writeValueAsString(spec);
            assertThat(yaml).isNotEmpty();

            PipelineSpec deserialized = yamlMapper.readValue(yaml, PipelineSpec.class);
            assertThat(deserialized.getStages().get(0).getWorkflow()).hasSize(100);
        }

        @Test
        @DisplayName("Handles null and empty collections in YAML")
        void nullAndEmptyCollections() throws Exception {
            PipelineSpec spec = new PipelineSpec();

            String yaml = yamlMapper.writeValueAsString(spec);
            PipelineSpec deserialized = yamlMapper.readValue(yaml, PipelineSpec.class);

            assertThat(deserialized.getStages()).isNull();
            assertThat(deserialized.getConnectors()).isNull();
            assertThat(deserialized.getEdges()).isNull();
        }
    }

    // ============================================
    // AGENT SPEC COMPOSITION (4 tests)
    // ============================================

    @Nested
    @DisplayName("Agent Spec Composition")
    class AgentSpecTests {

        @Test
        @DisplayName("Agent with complex configuration")
        void agentComplexConfig() {
            List<String> references = List.of(
                "model:gpt-4",
                "temperature:0.7",
                "tools:tool-1,tool-2,tool-3"
            );

            AgentSpec agent = AgentSpec.builder()
                .id("agent-1")
                .agent("llm-agent")
                .references(references)
                .build();

            assertThat(agent.getId()).isEqualTo("agent-1");
            assertThat(agent.getReferences()).hasSize(3);
        }

        @Test
        @DisplayName("Many agents in single stage")
        void manyAgentsPerStage() {
            List<AgentSpec> agents = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                final int idx = i;
                AgentSpec agent = AgentSpec.builder()
                    .id("agent-" + idx)
                    .agent("type-" + (idx % 5))
                    .dependencies(List.of("priority:" + idx))
                    .build();
                agents.add(agent);
            }

            PipelineStageSpec stage = PipelineStageSpec.builder()
                .name("multi-agent-stage")
                .type(PipelineStageSpec.StageType.STREAM)
                .workflow(agents)
                .build();

            assertThat(stage.getWorkflow()).hasSize(50);
        }

        @Test
        @DisplayName("Agent dependency chains")
        void agentDependencyChains() {
            List<AgentSpec> workflow = new ArrayList<>();

            // Sequential agents: each depends on previous output
            for (int i = 0; i < 10; i++) {
                final int idx = i;
                AgentSpec agent = AgentSpec.builder()
                    .id("agent-" + idx)
                    .agent("processor")
                    .build();
                workflow.add(agent);
            }

            PipelineStageSpec stage = PipelineStageSpec.builder()
                .name("processing-pipeline")
                .type(PipelineStageSpec.StageType.STREAM)
                .workflow(workflow)
                .build();

            assertThat(stage.getWorkflow()).hasSize(10);
        }

        @Test
        @DisplayName("Agent spec equality and hashing")
        void agentSpecEquality() {
            AgentSpec agent1 = AgentSpec.builder()
                .id("a1")
                .agent("type")
                .references(List.of("key=value"))
                .build();

            AgentSpec agent2 = AgentSpec.builder()
                .id("a1")
                .agent("type")
                .references(List.of("key=value"))
                .build();

            assertThat(agent1).isEqualTo(agent2);
            assertThat(agent1.hashCode()).isEqualTo(agent2.hashCode());
        }
    }

    // ============================================
    // STAGE TYPE VARIATIONS (4 tests)
    // ============================================

    @Nested
    @DisplayName("Pipeline Stage Types")
    class StageTypeTests {

        @Test
        @DisplayName("Mix STREAM and PATTERN stages")
        void mixedStageTypes() {
            List<PipelineStageSpec> stages = new ArrayList<>();

            for (int i = 0; i < 20; i++) {
                final int idx = i;
                PipelineStageSpec.StageType type = idx % 3 == 0 ?
                    PipelineStageSpec.StageType.PATTERN :
                    PipelineStageSpec.StageType.STREAM;

                PipelineStageSpec stage = PipelineStageSpec.builder()
                    .name("stage-" + idx)
                    .type(type)
                    .workflow(List.of(
                        AgentSpec.builder().id("a" + idx).agent("t").build()
                    ))
                    .build();
                stages.add(stage);
            }

            PipelineSpec spec = PipelineSpec.builder()
                .stages(stages)
                .tenantId("t1")
                .build();

            long streamCount = spec.getStages().stream()
                .filter(s -> s.getType() == PipelineStageSpec.StageType.STREAM)
                .count();
            long patternCount = spec.getStages().stream()
                .filter(s -> s.getType() == PipelineStageSpec.StageType.PATTERN)
                .count();

            assertThat(streamCount + patternCount).isEqualTo(20);
        }

        @Test
        @DisplayName("Stream processing pipeline")
        void streamPipeline() {
            List<PipelineStageSpec> stages = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                final int idx = i;
                PipelineStageSpec stage = PipelineStageSpec.builder()
                    .name("stream-stage-" + idx)
                    .type(PipelineStageSpec.StageType.STREAM)
                    .workflow(List.of(
                        AgentSpec.builder().id("streamer-" + idx).agent("stream-processor").build()
                    ))
                    .build();
                stages.add(stage);
            }

            PipelineSpec spec = PipelineSpec.builder()
                .stages(stages)
                .tenantId("t1")
                .build();

            assertThat(spec.getStages()).allMatch(s -> s.getType() == PipelineStageSpec.StageType.STREAM);
        }

        @Test
        @DisplayName("Pattern processing pipeline")
        void patternPipeline() {
            List<PipelineStageSpec> stages = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                final int idx = i;
                PipelineStageSpec stage = PipelineStageSpec.builder()
                    .name("pattern-stage-" + idx)
                    .type(PipelineStageSpec.StageType.PATTERN)
                    .workflow(List.of(
                        AgentSpec.builder().id("matcher-" + idx).agent("pattern-processor").build()
                    ))
                    .build();
                stages.add(stage);
            }

            PipelineSpec spec = PipelineSpec.builder()
                .stages(stages)
                .tenantId("t1")
                .build();

            assertThat(spec.getStages()).allMatch(s -> s.getType() == PipelineStageSpec.StageType.PATTERN);
        }

        @Test
        @DisplayName("Connector types coverage")
        void connectorTypes() {
            List<ConnectorSpec> connectors = new ArrayList<>();
            connectors.add(ConnectorSpec.builder()
                .id("event-source")
                .type(ConnectorSpec.ConnectorType.EVENT_CLOUD_SOURCE)
                .endpoint("ec-1")
                .topicOrStream("stream-1")
                .tenantId("t1")
                .build());

            connectors.add(ConnectorSpec.builder()
                .id("http-source")
                .type(ConnectorSpec.ConnectorType.HTTP_INGRESS)
                .endpoint("http://endpoint")
                .tenantId("t1")
                .build());

            PipelineSpec spec = PipelineSpec.builder()
                .stages(List.of(PipelineStageSpec.builder().name("s1").build()))
                .connectors(connectors)
                .tenantId("t1")
                .build();

            assertThat(spec.getConnectors()).hasSize(2);
        }
    }

    // ============================================
    // CONCURRENT PIPELINE OPERATIONS (2 tests)
    // ============================================

    @Nested
    @DisplayName("Concurrent Pipeline Operations")
    class ConcurrencyTests {

        @Test
        @DisplayName("Many pipelines built concurrently")
        void concurrentPipelineBuilder() throws Exception {
            int threadCount = 20;
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<PipelineSpec> specs = new ArrayList<>();

            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            try {
                for (int i = 0; i < threadCount; i++) {
                    final int idx = i;
                    exec.submit(() -> {
                        try {
                            PipelineSpec spec = PipelineSpec.builder()
                                .stages(List.of(
                                    PipelineStageSpec.builder()
                                        .name("stage-" + idx)
                                        .workflow(List.of(
                                            AgentSpec.builder()
                                                .id("agent-" + idx)
                                                .agent("type")
                                                .build()
                                        ))
                                        .build()
                                ))
                                .tenantId("tenant-" + idx)
                                .environment("test")
                                .build();
                            specs.add(spec);
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                exec.shutdownNow();
            }

            assertThat(specs).hasSize(threadCount);
        }

        @Test
        @DisplayName("Concurrent YAML serialization")
        void concurrentSerialization() throws Exception {
            int threadCount = 15;
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            ExecutorService exec = Executors.newFixedThreadPool(threadCount);
            try {
                for (int i = 0; i < threadCount; i++) {
                    final int idx = i;
                    exec.submit(() -> {
                        try {
                            PipelineSpec spec = PipelineSpec.builder()
                                .stages(List.of(
                                    PipelineStageSpec.builder().name("s-" + idx).build()
                                ))
                                .tenantId("t-" + idx)
                                .build();

                            try {
                                String yaml = yamlMapper.writeValueAsString(spec);
                                PipelineSpec deserialized = yamlMapper.readValue(yaml, PipelineSpec.class);
                                if (deserialized.equals(spec)) {
                                    successCount.incrementAndGet();
                                }
                            } catch (Exception e) {
                                // Serialization failure doesn't count as success
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            } finally {
                exec.shutdownNow();
            }

            assertThat(successCount.get()).isEqualTo(threadCount);
        }
    }

    // ============================================
    // EDGE CASES (1 test)
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Very large and complex pipeline structures")
        void veryLargePipeline() throws Exception {
            List<PipelineStageSpec> stages = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                final int stageIdx = i;
                List<AgentSpec> workflow = new ArrayList<>();
                for (int j = 0; j < 10; j++) {
                    final int agentIdx = j;
                    workflow.add(AgentSpec.builder()
                        .id("agent-" + stageIdx + "-" + agentIdx)
                        .agent("agent-type")
                        .build());
                }

                stages.add(PipelineStageSpec.builder()
                    .name("stage-" + stageIdx)
                    .type(stageIdx % 2 == 0 ? PipelineStageSpec.StageType.STREAM : PipelineStageSpec.StageType.PATTERN)
                    .workflow(workflow)
                    .build());
            }

            PipelineSpec spec = PipelineSpec.builder()
                .stages(stages)
                .tenantId("massive-pipeline")
                .environment("production")
                .tags(List.of("large", "complex"))
                .agentHints(Map.of("optimization", "parallel", "scalability", "high"))
                .build();

            String yaml = yamlMapper.writeValueAsString(spec);
            assertThat(yaml).isNotEmpty();

            PipelineSpec deserialized = yamlMapper.readValue(yaml, PipelineSpec.class);
            assertThat(deserialized.getStages()).hasSize(100);
            assertThat(deserialized.getStages().get(0).getWorkflow()).hasSize(10);
        }
    }
}
