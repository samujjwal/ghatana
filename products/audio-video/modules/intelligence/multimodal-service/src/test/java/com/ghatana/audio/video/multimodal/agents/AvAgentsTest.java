/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.audio.video.multimodal.agents;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.framework.api.AgentContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AudioTranscriptionAgent} and {@link MultimodalAnalysisAgent}.
 */
@DisplayName("AV Domain Agents (P7-T5)")
class AvAgentsTest {

    private AgentContext ctx;

    @BeforeEach
    void setUp() { // GH-90000
        ctx = AgentContext.empty(); // GH-90000
    }

    // =========================================================================
    // AudioTranscriptionAgent
    // =========================================================================

    @Nested
    @DisplayName("AudioTranscriptionAgent")
    class AudioTranscriptionAgentTests {

        private AudioTranscriptionAgent agent;

        @BeforeEach
        void setUp() { // GH-90000
            agent = new AudioTranscriptionAgent(); // GH-90000
            AgentConfig config = AgentConfig.builder() // GH-90000
                    .agentId(AudioTranscriptionAgent.AGENT_ID) // GH-90000
                    .type(AgentType.REACTIVE) // GH-90000
                    .build(); // GH-90000
            agent.initialize(config).getResult(); // GH-90000
        }

        @Test
        @DisplayName("descriptor returns REACTIVE type and correct agent ID")
        void descriptorIsCorrect() { // GH-90000
            assertThat(agent.descriptor().getAgentId()).isEqualTo(AudioTranscriptionAgent.AGENT_ID); // GH-90000
            assertThat(agent.descriptor().getType()).isEqualTo(AgentType.REACTIVE); // GH-90000
        }

        @Test
        @DisplayName("transcribes from source artifact ID successfully")
        void transcribesFromAudioSource() { // GH-90000
            AudioTranscriptionRequest request = AudioTranscriptionRequest.fromSource("artifact-001", "en-US"); // GH-90000

            AgentResult<AudioTranscriptionResult> result = agent.process(ctx, request).getResult(); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            AudioTranscriptionResult transcription = result.getOutput(); // GH-90000
            assertThat(transcription.transcript()).isNotNull(); // GH-90000
            assertThat(transcription.audioSource()).isEqualTo("artifact:artifact-001");
        }

        @Test
        @DisplayName("transcribes from raw bytes successfully")
        void transcribesFromBytes() { // GH-90000
            AudioTranscriptionRequest request = AudioTranscriptionRequest.fromBytes( // GH-90000
                    new byte[]{0x00, 0x01, 0x02}, "fr-FR");

            AgentResult<AudioTranscriptionResult> result = agent.process(ctx, request).getResult(); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("request toToolInput includes mediaArtifactId in audioSource map")
        void toToolInputContainsMediaArtifactId() { // GH-90000
            AudioTranscriptionRequest request = AudioTranscriptionRequest.fromSource("art-42", null); // GH-90000
            Map<String, Object> input = request.toToolInput(); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> audioSource = (Map<String, Object>) input.get("audioSource");
            assertThat(audioSource).containsEntry("mediaArtifactId", "art-42"); // GH-90000
        }

        @Test
        @DisplayName("request toToolInput includes audioBytes when no source")
        void toToolInputContainsAudioBytes() { // GH-90000
            byte[] bytes = {0x10};
            AudioTranscriptionRequest request = AudioTranscriptionRequest.fromBytes(bytes, null); // GH-90000
            Map<String, Object> input = request.toToolInput(); // GH-90000
            @SuppressWarnings("unchecked")
            Map<String, Object> audioSource = (Map<String, Object>) input.get("audioSource");
            assertThat(audioSource).containsKey("audioBytes");
        }
    }

    // =========================================================================
    // MultimodalAnalysisAgent
    // =========================================================================

    @Nested
    @DisplayName("MultimodalAnalysisAgent")
    class MultimodalAnalysisAgentTests {

        private MultimodalAnalysisAgent agent;

        @BeforeEach
        void setUp() { // GH-90000
            agent = new MultimodalAnalysisAgent(); // GH-90000
            AgentConfig config = AgentConfig.builder() // GH-90000
                    .agentId(MultimodalAnalysisAgent.AGENT_ID) // GH-90000
                    .type(AgentType.COMPOSITE) // GH-90000
                    .build(); // GH-90000
            agent.initialize(config).getResult(); // GH-90000
        }

        @Test
        @DisplayName("descriptor returns COMPOSITE type and correct agent ID")
        void descriptorIsCorrect() { // GH-90000
            assertThat(agent.descriptor().getAgentId()).isEqualTo(MultimodalAnalysisAgent.AGENT_ID); // GH-90000
            assertThat(agent.descriptor().getType()).isEqualTo(AgentType.COMPOSITE); // GH-90000
        }

        @Test
        @DisplayName("processes without vision when no analysis types")
        void processesWithoutVision() { // GH-90000
            MultimodalAnalysisRequest request = MultimodalAnalysisRequest.forArtifact("artifact-999");

            AgentResult<MultimodalAnalysisResult> result = agent.process(ctx, request).getResult(); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            MultimodalAnalysisResult analysis = result.getOutput(); // GH-90000
            assertThat(analysis.summary()).isNotNull(); // GH-90000
            assertThat(analysis.visionResults()).isEmpty(); // GH-90000
            assertThat(analysis.transcript()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("includes vision results when analysis types provided")
        void includesVisionResults() { // GH-90000
            MultimodalAnalysisRequest request = new MultimodalAnalysisRequest( // GH-90000
                    "artifact-v1", List.of("objects", "scenes"), // GH-90000
                    "FULL", false, null, null);

            AgentResult<MultimodalAnalysisResult> result = agent.process(ctx, request).getResult(); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            MultimodalAnalysisResult analysis = result.getOutput(); // GH-90000
            assertThat(analysis.visionResults()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("includes transcript when enableTranscription is true")
        void includesTranscript() { // GH-90000
            MultimodalAnalysisRequest request = new MultimodalAnalysisRequest( // GH-90000
                    "artifact-t1", List.of(), "SUMMARY", true, "en-US", null); // GH-90000

            AgentResult<MultimodalAnalysisResult> result = agent.process(ctx, request).getResult(); // GH-90000

            assertThat(result.isSuccess()).isTrue(); // GH-90000
            MultimodalAnalysisResult analysis = result.getOutput(); // GH-90000
            assertThat(analysis.transcript()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("MultimodalAnalysisResult.fromToolOutputs handles null transcription")
        void fromToolOutputsHandlesNullTranscription() { // GH-90000
            Map<String, Object> inferenceOutput = Map.of("summary", "test summary", "confidence", 0.9); // GH-90000
            MultimodalAnalysisResult result = MultimodalAnalysisResult.fromToolOutputs( // GH-90000
                    inferenceOutput, Map.of(), null); // GH-90000
            assertThat(result.summary()).isEqualTo("test summary");
            assertThat(result.confidence()).isEqualTo(0.9); // GH-90000
            assertThat(result.transcript()).isNull(); // GH-90000
            assertThat(result.transcriptSegments()).isEmpty(); // GH-90000
        }
    }
}
