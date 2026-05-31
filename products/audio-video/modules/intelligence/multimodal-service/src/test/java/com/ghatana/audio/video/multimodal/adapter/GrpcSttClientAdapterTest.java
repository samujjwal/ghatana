/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.audio.video.multimodal.adapter;

import com.ghatana.audio.video.multimodal.engine.AudioResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for GrpcSttClientAdapter.
 *
 * <p>Verifies STT mode configuration, fallback behavior, and transcription results.
 * Note: full service-level assertions run in the STT gRPC service integration tests.
 *
 * @doc.type class
 * @doc.purpose Integration tests for GrpcSttClientAdapter STT modes and fallback
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("GrpcSttClientAdapter Integration Tests")
@Tag("integration")
class GrpcSttClientAdapterTest {

    /**
     * Test that verifies LLM_FALLBACK mode uses AI Inference fallback.
     */
    @Test
    @DisplayName("LLM_FALLBACK mode uses AI Inference fallback")
    void llmFallbackModeUsesAiInference() { 
        try (GrpcSttClientAdapter adapter = new GrpcSttClientAdapter( 
                "localhost",
                50051,
                GrpcSttClientAdapter.SttMode.LLM_FALLBACK
        )) {
            byte[] audioData = new byte[1024];
            AudioResult result = adapter.transcribe(audioData); 

            assertThat(result).isNotNull(); 
            assertThat(adapter.getCurrentMode()).isEqualTo(GrpcSttClientAdapter.SttMode.LLM_FALLBACK); 
        }
    }

    /**
     * Test that verifies NOP mode returns empty result.
     */
    @Test
    @DisplayName("NOP mode returns empty result")
    void nopModeReturnsEmptyResult() { 
        try (GrpcSttClientAdapter adapter = new GrpcSttClientAdapter( 
                "localhost",
                50051,
                GrpcSttClientAdapter.SttMode.NOP
        )) {
            byte[] audioData = new byte[1024];
            AudioResult result = adapter.transcribe(audioData); 

            assertThat(result).isNotNull(); 
            assertThat(result.getTranscription()).isEmpty(); 
            assertThat(result.getConfidence()).isEqualTo(0.0); 
            assertThat(adapter.getCurrentMode()).isEqualTo(GrpcSttClientAdapter.SttMode.NOP); 
        }
    }

    /**
     * Test that verifies GRPC mode is accepted and falls back when the service is unavailable.
     */
    @Test
    @DisplayName("GRPC mode falls back to LLM_FALLBACK when endpoint is unavailable")
    void grpcModeFallsBackWhenEndpointUnavailable() {
        try (GrpcSttClientAdapter adapter = new GrpcSttClientAdapter(
                "localhost",
                1,
                GrpcSttClientAdapter.SttMode.GRPC
        )) {
            AudioResult result = adapter.transcribe(new byte[1024]);

            assertThat(result).isNotNull();
            assertThat(adapter.getCurrentMode()).isEqualTo(GrpcSttClientAdapter.SttMode.LLM_FALLBACK);
        }
    }

    /**
     * Test that verifies default constructor uses LLM_FALLBACK mode.
     */
    @Test
    @DisplayName("Default constructor uses LLM_FALLBACK mode")
    void defaultConstructorUsesLlmFallbackMode() { 
        try (GrpcSttClientAdapter adapter = new GrpcSttClientAdapter("localhost", 50051)) { 
            byte[] audioData = new byte[1024];
            adapter.transcribe(audioData); 

            assertThat(adapter.getCurrentMode()).isEqualTo(GrpcSttClientAdapter.SttMode.LLM_FALLBACK); 
        }
    }

    /**
     * Test that verifies getCurrentMode returns the current mode.
     */
    @Test
    @DisplayName("getCurrentMode returns the current mode")
    void getCurrentModeReturnsCurrentMode() { 
        try (GrpcSttClientAdapter adapter = new GrpcSttClientAdapter( 
                "localhost",
                50051,
                GrpcSttClientAdapter.SttMode.LLM_FALLBACK
        )) {
            assertThat(adapter.getCurrentMode()).isEqualTo(GrpcSttClientAdapter.SttMode.LLM_FALLBACK); 
        }
    }

    /**
     * Test that verifies adapter can be closed.
     */
    @Test
    @DisplayName("Adapter can be closed")
    void adapterCanBeClosed() { 
        try (GrpcSttClientAdapter ignored = new GrpcSttClientAdapter("localhost", 50051)) { 
            // Auto-close via try-with-resources
        }
    }
}
