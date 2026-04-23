/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory()); // GH-90000

    // ============================================
    // PIPELINE SPEC COMPOSITION (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Pipeline Spec Composition")
    class PipelineCompositionTests {

        @Test
        @DisplayName("Build pipeline with multiple stages")
        void multipleStages() { // GH-90000
            List<PipelineStageSpec> stages = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 10; i++) { // GH-90000
                final int idx = i;
                PipelineStageSpec stage = PipelineStageSpec.builder() // GH-90000
                    .name("stage-" + idx) // GH-90000
                    .type(idx % 2 == 0 ? PipelineStageSpec.StageType.STREAM : PipelineStageSpec.StageType.PATTERN) // GH-90000
                    .workflow(List.of( // GH-90000
                        AgentSpec.builder() // GH-90000
                            .id("agent-" + idx) // GH-90000
                            .agent("test-agent")
                            .build() // GH-90000
                    ))
                    .build(); // GH-90000
                stages.add(stage); // GH-90000
            }

            PipelineSpec spec = PipelineSpec.builder() // GH-90000
                .stages(stages) // GH-90000
                .tenantId("tenant-1")
                .build(); // GH-90000

            assertThat(spec.getStages()).hasSize(10); // GH-90000
        }

        @Test
        @DisplayName("Complex connector configuration")
        void complexConnectors() { // GH-90000
            List<ConnectorSpec> connectors = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 15; i++) { // GH-90000
                final int idx = i;
                ConnectorSpec connector = ConnectorSpec.builder() // GH-90000
                    .id("connector-" + idx) // GH-90000
                    .type(idx % 3 == 0 ? ConnectorSpec.ConnectorType.EVENT_CLOUD_SOURCE : ConnectorSpec.ConnectorType.HTTP_INGRESS) // GH-90000
                    .endpoint("endpoint-" + idx) // GH-90000
                    .topicOrStream("stream-" + idx) // GH-90000
                    .tenantId("tenant-1")
                    .build(); // GH-90000
                connectors.add(connector); // GH-90000
            }

            PipelineSpec spec = PipelineSpec.builder() // GH-90000
                .stages(List.of(PipelineStageSpec.builder().name("s1").build()))
                .connectors(connectors) // GH-90000
                .tenantId("tenant-1")
                .build(); // GH-90000

            assertThat(spec.getConnectors()).hasSize(15); // GH-90000
        }

        @Test
        @DisplayName("DAG edges with fan-out and fan-in")
        void dagEdges() { // GH-90000
            List<PipelineEdgeSpec> edges = new ArrayList<>(); // GH-90000

            // Create fan-out: stage-0 to many stages
            for (int i = 1; i < 6; i++) { // GH-90000
                final int targetIdx = i;
                edges.add(PipelineEdgeSpec.builder() // GH-90000
                    .fromStageId("stage-0")
                    .toStageId("stage-" + targetIdx) // GH-90000
                    .label("out-" + targetIdx) // GH-90000
                    .build()); // GH-90000
            }

            // Create fan-in: many stages to final
            for (int i = 1; i < 6; i++) { // GH-90000
                final int sourceIdx = i;
                edges.add(PipelineEdgeSpec.builder() // GH-90000
                    .fromStageId("stage-" + sourceIdx) // GH-90000
                    .toStageId("stage-final")
                    .label("in-" + sourceIdx) // GH-90000
                    .build()); // GH-90000
            }

            PipelineSpec spec = PipelineSpec.builder() // GH-90000
                .stages(List.of(PipelineStageSpec.builder().name("s").build()))
                .edges(edges) // GH-90000
                .tenantId("tenant-1")
                .build(); // GH-90000

            assertThat(spec.getEdges()).hasSize(10); // GH-90000
        }

        @Test
        @DisplayName("Many agent hints and tags")
        void agentHintsAndTags() { // GH-90000
            Map<String, String> hints = new HashMap<>(); // GH-90000
            for (int i = 0; i < 50; i++) { // GH-90000
                hints.put("hint-" + i, "value-" + i); // GH-90000
            }

            List<String> tags = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 30; i++) { // GH-90000
                tags.add("tag-" + i); // GH-90000
            }

            PipelineSpec spec = PipelineSpec.builder() // GH-90000
                .stages(List.of(PipelineStageSpec.builder().name("s").build()))
                .agentHints(hints) // GH-90000
                .tags(tags) // GH-90000
                .tenantId("tenant-1")
                .build(); // GH-90000

            assertThat(spec.getAgentHints()).hasSize(50); // GH-90000
            assertThat(spec.getTags()).hasSize(30); // GH-90000
        }
    }

    // ============================================
    // YAML SERIALIZATION (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("YAML Serialization")
    class YAMLSerializationTests {

        @Test
        @DisplayName("Round-trip serialization with complex structure")
        void roundTripComplexStructure() throws Exception { // GH-90000
            PipelineSpec original = PipelineSpec.builder() // GH-90000
                .stages(List.of( // GH-90000
                    PipelineStageSpec.builder() // GH-90000
                        .name("stage-1")
                        .type(PipelineStageSpec.StageType.STREAM) // GH-90000
                        .workflow(List.of( // GH-90000
                            AgentSpec.builder().id("agent-1").agent("test").build()
                        ))
                        .build() // GH-90000
                ))
                .connectors(List.of( // GH-90000
                    ConnectorSpec.builder() // GH-90000
                        .id("conn-1")
                        .type(ConnectorSpec.ConnectorType.EVENT_CLOUD_SOURCE) // GH-90000
                        .endpoint("ep1")
                        .topicOrStream("stream1")
                        .tenantId("t1")
                        .build() // GH-90000
                ))
                .edges(List.of( // GH-90000
                    PipelineEdgeSpec.builder() // GH-90000
                        .fromStageId("s1")
                        .toStageId("s2")
                        .label("edge1")
                        .build() // GH-90000
                ))
                .tenantId("tenant-1")
                .environment("prod")
                .tags(List.of("v1", "v2")) // GH-90000
                .agentHints(Map.of("key", "value")) // GH-90000
                .build(); // GH-90000

            String yaml = yamlMapper.writeValueAsString(original); // GH-90000
            PipelineSpec deserialized = yamlMapper.readValue(yaml, PipelineSpec.class); // GH-90000

            assertThat(deserialized).isEqualTo(original); // GH-90000
        }

        @Test
        @DisplayName("Serialize and deserialize many pipelines")
        void batchSerialization() throws Exception { // GH-90000
            List<PipelineSpec> specs = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 50; i++) { // GH-90000
                final int idx = i;
                PipelineSpec spec = PipelineSpec.builder() // GH-90000
                    .stages(List.of( // GH-90000
                        PipelineStageSpec.builder() // GH-90000
                            .name("stage-" + idx) // GH-90000
                            .build() // GH-90000
                    ))
                    .tenantId("tenant-" + idx) // GH-90000
                    .environment("test")
                    .build(); // GH-90000
                specs.add(spec); // GH-90000
            }

            for (PipelineSpec spec : specs) { // GH-90000
                String yaml = yamlMapper.writeValueAsString(spec); // GH-90000
                PipelineSpec deserialized = yamlMapper.readValue(yaml, PipelineSpec.class); // GH-90000
                assertThat(deserialized).isEqualTo(spec); // GH-90000
            }
        }

        @Test
        @DisplayName("Very large stage workflow YAML")
        void largeWorkflowYAML() throws Exception { // GH-90000
            List<AgentSpec> workflow = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 100; i++) { // GH-90000
                final int idx = i;
                workflow.add(AgentSpec.builder() // GH-90000
                    .id("agent-" + idx) // GH-90000
                    .agent("agent-type")
                    .references(List.of("param-" + idx + "=value-" + idx)) // GH-90000
                    .build()); // GH-90000
            }

            PipelineStageSpec stage = PipelineStageSpec.builder() // GH-90000
                .name("large-stage")
                .type(PipelineStageSpec.StageType.STREAM) // GH-90000
                .workflow(workflow) // GH-90000
                .build(); // GH-90000

            PipelineSpec spec = PipelineSpec.builder() // GH-90000
                .stages(List.of(stage)) // GH-90000
                .tenantId("tenant-1")
                .build(); // GH-90000

            String yaml = yamlMapper.writeValueAsString(spec); // GH-90000
            assertThat(yaml).isNotEmpty(); // GH-90000

            PipelineSpec deserialized = yamlMapper.readValue(yaml, PipelineSpec.class); // GH-90000
            assertThat(deserialized.getStages().get(0).getWorkflow()).hasSize(100); // GH-90000
        }

        @Test
        @DisplayName("Handles null and empty collections in YAML")
        void nullAndEmptyCollections() throws Exception { // GH-90000
            PipelineSpec spec = new PipelineSpec(); // GH-90000

            String yaml = yamlMapper.writeValueAsString(spec); // GH-90000
            PipelineSpec deserialized = yamlMapper.readValue(yaml, PipelineSpec.class); // GH-90000

            assertThat(deserialized.getStages()).isNull(); // GH-90000
            assertThat(deserialized.getConnectors()).isNull(); // GH-90000
            assertThat(deserialized.getEdges()).isNull(); // GH-90000
        }
    }

    // ============================================
    // AGENT SPEC COMPOSITION (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Agent Spec Composition")
    class AgentSpecTests {

        @Test
        @DisplayName("Agent with complex configuration")
        void agentComplexConfig() { // GH-90000
            List<String> references = List.of( // GH-90000
                "model:gpt-4",
                "temperature:0.7",
                "tools:tool-1,tool-2,tool-3"
            );

            AgentSpec agent = AgentSpec.builder() // GH-90000
                .id("agent-1")
                .agent("llm-agent")
                .references(references) // GH-90000
                .build(); // GH-90000

            assertThat(agent.getId()).isEqualTo("agent-1");
            assertThat(agent.getReferences()).hasSize(3); // GH-90000
        }

        @Test
        @DisplayName("Many agents in single stage")
        void manyAgentsPerStage() { // GH-90000
            List<AgentSpec> agents = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 50; i++) { // GH-90000
                final int idx = i;
                AgentSpec agent = AgentSpec.builder() // GH-90000
                    .id("agent-" + idx) // GH-90000
                    .agent("type-" + (idx % 5)) // GH-90000
                    .dependencies(List.of("priority:" + idx)) // GH-90000
                    .build(); // GH-90000
                agents.add(agent); // GH-90000
            }

            PipelineStageSpec stage = PipelineStageSpec.builder() // GH-90000
                .name("multi-agent-stage")
                .type(PipelineStageSpec.StageType.STREAM) // GH-90000
                .workflow(agents) // GH-90000
                .build(); // GH-90000

            assertThat(stage.getWorkflow()).hasSize(50); // GH-90000
        }

        @Test
        @DisplayName("Agent dependency chains")
        void agentDependencyChains() { // GH-90000
            List<AgentSpec> workflow = new ArrayList<>(); // GH-90000

            // Sequential agents: each depends on previous output
            for (int i = 0; i < 10; i++) { // GH-90000
                final int idx = i;
                AgentSpec agent = AgentSpec.builder() // GH-90000
                    .id("agent-" + idx) // GH-90000
                    .agent("processor")
                    .build(); // GH-90000
                workflow.add(agent); // GH-90000
            }

            PipelineStageSpec stage = PipelineStageSpec.builder() // GH-90000
                .name("processing-pipeline")
                .type(PipelineStageSpec.StageType.STREAM) // GH-90000
                .workflow(workflow) // GH-90000
                .build(); // GH-90000

            assertThat(stage.getWorkflow()).hasSize(10); // GH-90000
        }

        @Test
        @DisplayName("Agent spec equality and hashing")
        void agentSpecEquality() { // GH-90000
            AgentSpec agent1 = AgentSpec.builder() // GH-90000
                .id("a1")
                .agent("type")
                .references(List.of("key=value"))
                .build(); // GH-90000

            AgentSpec agent2 = AgentSpec.builder() // GH-90000
                .id("a1")
                .agent("type")
                .references(List.of("key=value"))
                .build(); // GH-90000

            assertThat(agent1).isEqualTo(agent2); // GH-90000
            assertThat(agent1.hashCode()).isEqualTo(agent2.hashCode()); // GH-90000
        }
    }

    // ============================================
    // STAGE TYPE VARIATIONS (4 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Pipeline Stage Types")
    class StageTypeTests {

        @Test
        @DisplayName("Mix STREAM and PATTERN stages")
        void mixedStageTypes() { // GH-90000
            List<PipelineStageSpec> stages = new ArrayList<>(); // GH-90000

            for (int i = 0; i < 20; i++) { // GH-90000
                final int idx = i;
                PipelineStageSpec.StageType type = idx % 3 == 0 ?
                    PipelineStageSpec.StageType.PATTERN :
                    PipelineStageSpec.StageType.STREAM;

                PipelineStageSpec stage = PipelineStageSpec.builder() // GH-90000
                    .name("stage-" + idx) // GH-90000
                    .type(type) // GH-90000
                    .workflow(List.of( // GH-90000
                        AgentSpec.builder().id("a" + idx).agent("t").build()
                    ))
                    .build(); // GH-90000
                stages.add(stage); // GH-90000
            }

            PipelineSpec spec = PipelineSpec.builder() // GH-90000
                .stages(stages) // GH-90000
                .tenantId("t1")
                .build(); // GH-90000

            long streamCount = spec.getStages().stream() // GH-90000
                .filter(s -> s.getType() == PipelineStageSpec.StageType.STREAM) // GH-90000
                .count(); // GH-90000
            long patternCount = spec.getStages().stream() // GH-90000
                .filter(s -> s.getType() == PipelineStageSpec.StageType.PATTERN) // GH-90000
                .count(); // GH-90000

            assertThat(streamCount + patternCount).isEqualTo(20); // GH-90000
        }

        @Test
        @DisplayName("Stream processing pipeline")
        void streamPipeline() { // GH-90000
            List<PipelineStageSpec> stages = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 5; i++) { // GH-90000
                final int idx = i;
                PipelineStageSpec stage = PipelineStageSpec.builder() // GH-90000
                    .name("stream-stage-" + idx) // GH-90000
                    .type(PipelineStageSpec.StageType.STREAM) // GH-90000
                    .workflow(List.of( // GH-90000
                        AgentSpec.builder().id("streamer-" + idx).agent("stream-processor").build()
                    ))
                    .build(); // GH-90000
                stages.add(stage); // GH-90000
            }

            PipelineSpec spec = PipelineSpec.builder() // GH-90000
                .stages(stages) // GH-90000
                .tenantId("t1")
                .build(); // GH-90000

            assertThat(spec.getStages()).allMatch(s -> s.getType() == PipelineStageSpec.StageType.STREAM); // GH-90000
        }

        @Test
        @DisplayName("Pattern processing pipeline")
        void patternPipeline() { // GH-90000
            List<PipelineStageSpec> stages = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 5; i++) { // GH-90000
                final int idx = i;
                PipelineStageSpec stage = PipelineStageSpec.builder() // GH-90000
                    .name("pattern-stage-" + idx) // GH-90000
                    .type(PipelineStageSpec.StageType.PATTERN) // GH-90000
                    .workflow(List.of( // GH-90000
                        AgentSpec.builder().id("matcher-" + idx).agent("pattern-processor").build()
                    ))
                    .build(); // GH-90000
                stages.add(stage); // GH-90000
            }

            PipelineSpec spec = PipelineSpec.builder() // GH-90000
                .stages(stages) // GH-90000
                .tenantId("t1")
                .build(); // GH-90000

            assertThat(spec.getStages()).allMatch(s -> s.getType() == PipelineStageSpec.StageType.PATTERN); // GH-90000
        }

        @Test
        @DisplayName("Connector types coverage")
        void connectorTypes() { // GH-90000
            List<ConnectorSpec> connectors = new ArrayList<>(); // GH-90000
            connectors.add(ConnectorSpec.builder() // GH-90000
                .id("event-source")
                .type(ConnectorSpec.ConnectorType.EVENT_CLOUD_SOURCE) // GH-90000
                .endpoint("ec-1")
                .topicOrStream("stream-1")
                .tenantId("t1")
                .build()); // GH-90000

            connectors.add(ConnectorSpec.builder() // GH-90000
                .id("http-source")
                .type(ConnectorSpec.ConnectorType.HTTP_INGRESS) // GH-90000
                .endpoint("http://endpoint")
                .tenantId("t1")
                .build()); // GH-90000

            PipelineSpec spec = PipelineSpec.builder() // GH-90000
                .stages(List.of(PipelineStageSpec.builder().name("s1").build()))
                .connectors(connectors) // GH-90000
                .tenantId("t1")
                .build(); // GH-90000

            assertThat(spec.getConnectors()).hasSize(2); // GH-90000
        }
    }

    // ============================================
    // CONCURRENT PIPELINE OPERATIONS (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Concurrent Pipeline Operations")
    class ConcurrencyTests {

        @Test
        @DisplayName("Many pipelines built concurrently")
        void concurrentPipelineBuilder() throws Exception { // GH-90000
            int threadCount = 20;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000
            List<PipelineSpec> specs = new ArrayList<>(); // GH-90000

            ExecutorService exec = Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int i = 0; i < threadCount; i++) { // GH-90000
                    final int idx = i;
                    exec.submit(() -> { // GH-90000
                        try {
                            PipelineSpec spec = PipelineSpec.builder() // GH-90000
                                .stages(List.of( // GH-90000
                                    PipelineStageSpec.builder() // GH-90000
                                        .name("stage-" + idx) // GH-90000
                                        .workflow(List.of( // GH-90000
                                            AgentSpec.builder() // GH-90000
                                                .id("agent-" + idx) // GH-90000
                                                .agent("type")
                                                .build() // GH-90000
                                        ))
                                        .build() // GH-90000
                                ))
                                .tenantId("tenant-" + idx) // GH-90000
                                .environment("test")
                                .build(); // GH-90000
                            specs.add(spec); // GH-90000
                        } finally {
                            latch.countDown(); // GH-90000
                        }
                    });
                }
                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                exec.shutdownNow(); // GH-90000
            }

            assertThat(specs).hasSize(threadCount); // GH-90000
        }

        @Test
        @DisplayName("Concurrent YAML serialization")
        void concurrentSerialization() throws Exception { // GH-90000
            int threadCount = 15;
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000
            AtomicInteger successCount = new AtomicInteger(0); // GH-90000

            ExecutorService exec = Executors.newFixedThreadPool(threadCount); // GH-90000
            try {
                for (int i = 0; i < threadCount; i++) { // GH-90000
                    final int idx = i;
                    exec.submit(() -> { // GH-90000
                        try {
                            PipelineSpec spec = PipelineSpec.builder() // GH-90000
                                .stages(List.of( // GH-90000
                                    PipelineStageSpec.builder().name("s-" + idx).build() // GH-90000
                                ))
                                .tenantId("t-" + idx) // GH-90000
                                .build(); // GH-90000

                            try {
                                String yaml = yamlMapper.writeValueAsString(spec); // GH-90000
                                PipelineSpec deserialized = yamlMapper.readValue(yaml, PipelineSpec.class); // GH-90000
                                if (deserialized.equals(spec)) { // GH-90000
                                    successCount.incrementAndGet(); // GH-90000
                                }
                            } catch (Exception e) { // GH-90000
                                // Serialization failure doesn't count as success
                            }
                        } finally {
                            latch.countDown(); // GH-90000
                        }
                    });
                }
                assertThat(latch.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); // GH-90000
            } finally {
                exec.shutdownNow(); // GH-90000
            }

            assertThat(successCount.get()).isEqualTo(threadCount); // GH-90000
        }
    }

    // ============================================
    // EDGE CASES (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Very large and complex pipeline structures")
        void veryLargePipeline() throws Exception { // GH-90000
            List<PipelineStageSpec> stages = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 100; i++) { // GH-90000
                final int stageIdx = i;
                List<AgentSpec> workflow = new ArrayList<>(); // GH-90000
                for (int j = 0; j < 10; j++) { // GH-90000
                    final int agentIdx = j;
                    workflow.add(AgentSpec.builder() // GH-90000
                        .id("agent-" + stageIdx + "-" + agentIdx) // GH-90000
                        .agent("agent-type")
                        .build()); // GH-90000
                }

                stages.add(PipelineStageSpec.builder() // GH-90000
                    .name("stage-" + stageIdx) // GH-90000
                    .type(stageIdx % 2 == 0 ? PipelineStageSpec.StageType.STREAM : PipelineStageSpec.StageType.PATTERN) // GH-90000
                    .workflow(workflow) // GH-90000
                    .build()); // GH-90000
            }

            PipelineSpec spec = PipelineSpec.builder() // GH-90000
                .stages(stages) // GH-90000
                .tenantId("massive-pipeline")
                .environment("production")
                .tags(List.of("large", "complex")) // GH-90000
                .agentHints(Map.of("optimization", "parallel", "scalability", "high")) // GH-90000
                .build(); // GH-90000

            String yaml = yamlMapper.writeValueAsString(spec); // GH-90000
            assertThat(yaml).isNotEmpty(); // GH-90000

            PipelineSpec deserialized = yamlMapper.readValue(yaml, PipelineSpec.class); // GH-90000
            assertThat(deserialized.getStages()).hasSize(100); // GH-90000
            assertThat(deserialized.getStages().get(0).getWorkflow()).hasSize(10); // GH-90000
        }
    }
}
