/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.audio.video.multimodal.agents;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
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
class AvAgentsTest extends EventloopTestBase {

    private AgentContext ctx;

    @BeforeEach
    void setUp() { 
        ctx = AgentContext.empty(); 
    }

    // =========================================================================
    // AudioTranscriptionAgent
    // =========================================================================

    @Nested
    @DisplayName("AudioTranscriptionAgent")
    class AudioTranscriptionAgentTests {

        private AudioTranscriptionAgent agent;

        @BeforeEach
        void setUp() { 
            agent = new AudioTranscriptionAgent(); 
            AgentConfig config = AgentConfig.builder() 
                    .agentId(AudioTranscriptionAgent.AGENT_ID) 
                    .type(AgentType.REACTIVE) 
                    .build(); 
            runPromise(() -> agent.initialize(config)); 
        }

        @Test
        @DisplayName("descriptor returns REACTIVE type and correct agent ID")
        void descriptorIsCorrect() { 
            assertThat(agent.descriptor().getAgentId()).isEqualTo(AudioTranscriptionAgent.AGENT_ID); 
            assertThat(agent.descriptor().getType()).isEqualTo(AgentType.REACTIVE); 
        }

        @Test
        @DisplayName("transcribes from source artifact ID successfully")
        void transcribesFromAudioSource() { 
            AudioTranscriptionRequest request = AudioTranscriptionRequest.fromSource("artifact-001", "en-US"); 

            AgentResult<AudioTranscriptionResult> result = runPromise(() -> agent.process(ctx, request)); 

            assertThat(result.isSuccess()).isTrue(); 
            AudioTranscriptionResult transcription = result.getOutput(); 
            assertThat(transcription.transcript()).isNotNull(); 
            assertThat(transcription.audioSource()).isEqualTo("artifact:artifact-001");
        }

        @Test
        @DisplayName("transcribes from raw bytes successfully")
        void transcribesFromBytes() { 
            AudioTranscriptionRequest request = AudioTranscriptionRequest.fromBytes( 
                    new byte[]{0x00, 0x01, 0x02}, "fr-FR");

            AgentResult<AudioTranscriptionResult> result = runPromise(() -> agent.process(ctx, request)); 

            assertThat(result.isSuccess()).isTrue(); 
        }

        @Test
        @DisplayName("request toToolInput includes mediaArtifactId in audioSource map")
        void toToolInputContainsMediaArtifactId() { 
            AudioTranscriptionRequest request = AudioTranscriptionRequest.fromSource("art-42", null); 
            Map<String, Object> input = request.toToolInput(); 
            @SuppressWarnings("unchecked")
            Map<String, Object> audioSource = (Map<String, Object>) input.get("audioSource");
            assertThat(audioSource).containsEntry("mediaArtifactId", "art-42"); 
        }

        @Test
        @DisplayName("request toToolInput includes audioBytes when no source")
        void toToolInputContainsAudioBytes() { 
            byte[] bytes = {0x10};
            AudioTranscriptionRequest request = AudioTranscriptionRequest.fromBytes(bytes, null); 
            Map<String, Object> input = request.toToolInput(); 
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
        void setUp() { 
            agent = new MultimodalAnalysisAgent(); 
            AgentConfig config = AgentConfig.builder() 
                    .agentId(MultimodalAnalysisAgent.AGENT_ID) 
                    .type(AgentType.COMPOSITE) 
                    .build(); 
            runPromise(() -> agent.initialize(config)); 
        }

        @Test
        @DisplayName("descriptor returns COMPOSITE type and correct agent ID")
        void descriptorIsCorrect() { 
            assertThat(agent.descriptor().getAgentId()).isEqualTo(MultimodalAnalysisAgent.AGENT_ID); 
            assertThat(agent.descriptor().getType()).isEqualTo(AgentType.COMPOSITE); 
        }

        @Test
        @DisplayName("processes without vision when no analysis types")
        void processesWithoutVision() { 
            MultimodalAnalysisRequest request = MultimodalAnalysisRequest.forArtifact("artifact-999");

            AgentResult<MultimodalAnalysisResult> result = runPromise(() -> agent.process(ctx, request)); 

            assertThat(result.isSuccess()).isTrue(); 
            MultimodalAnalysisResult analysis = result.getOutput(); 
            assertThat(analysis.summary()).isNotNull(); 
            assertThat(analysis.visionResults()).isEmpty(); 
            assertThat(analysis.transcript()).isNull(); 
        }

        @Test
        @DisplayName("includes vision results when analysis types provided")
        void includesVisionResults() { 
            MultimodalAnalysisRequest request = new MultimodalAnalysisRequest( 
                    "artifact-v1", List.of("objects", "scenes"), 
                    "FULL", false, null, null);

            AgentResult<MultimodalAnalysisResult> result = runPromise(() -> agent.process(ctx, request)); 

            assertThat(result.isSuccess()).isTrue(); 
            MultimodalAnalysisResult analysis = result.getOutput(); 
            assertThat(analysis.visionResults()).isNotEmpty(); 
        }

        @Test
        @DisplayName("includes transcript when enableTranscription is true")
        void includesTranscript() { 
            MultimodalAnalysisRequest request = new MultimodalAnalysisRequest( 
                    "artifact-t1", List.of(), "SUMMARY", true, "en-US", null); 

            AgentResult<MultimodalAnalysisResult> result = runPromise(() -> agent.process(ctx, request)); 

            assertThat(result.isSuccess()).isTrue(); 
            MultimodalAnalysisResult analysis = result.getOutput(); 
            assertThat(analysis.transcript()).isNotNull(); 
        }

        @Test
        @DisplayName("MultimodalAnalysisResult.fromToolOutputs handles null transcription")
        void fromToolOutputsHandlesNullTranscription() { 
            Map<String, Object> inferenceOutput = Map.of("summary", "test summary", "confidence", 0.9); 
            MultimodalAnalysisResult result = MultimodalAnalysisResult.fromToolOutputs( 
                    inferenceOutput, Map.of(), null); 
            assertThat(result.summary()).isEqualTo("test summary");
            assertThat(result.confidence()).isEqualTo(0.9); 
            assertThat(result.transcript()).isNull(); 
            assertThat(result.transcriptSegments()).isEmpty(); 
        }
    }
}
