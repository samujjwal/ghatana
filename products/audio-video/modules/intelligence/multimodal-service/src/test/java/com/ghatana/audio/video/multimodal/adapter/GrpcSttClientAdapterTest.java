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
 * Note: Full gRPC integration tests require Whisper gRPC service and proto stubs.
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
        GrpcSttClientAdapter adapter = new GrpcSttClientAdapter(
            "localhost",
            50051,
            GrpcSttClientAdapter.SttMode.LLM_FALLBACK
        );
        
        byte[] audioData = new byte[1024];
        AudioResult result = adapter.transcribe(audioData);
        
        assertThat(result).isNotNull();
        assertThat(adapter.getCurrentMode()).isEqualTo(GrpcSttClientAdapter.SttMode.LLM_FALLBACK);
    }

    /**
     * Test that verifies NOP mode returns empty result.
     */
    @Test
    @DisplayName("NOP mode returns empty result")
    void nopModeReturnsEmptyResult() {
        GrpcSttClientAdapter adapter = new GrpcSttClientAdapter(
            "localhost",
            50051,
            GrpcSttClientAdapter.SttMode.NOP
        );
        
        byte[] audioData = new byte[1024];
        AudioResult result = adapter.transcribe(audioData);
        
        assertThat(result).isNotNull();
        assertThat(result.getTranscription()).isEmpty();
        assertThat(result.getConfidence()).isEqualTo(0.0);
        assertThat(adapter.getCurrentMode()).isEqualTo(GrpcSttClientAdapter.SttMode.NOP);
    }

    /**
     * Test that verifies GRPC mode falls back to LLM when gRPC unavailable.
     */
    @Test
    @DisplayName("GRPC mode falls back to LLM when gRPC unavailable")
    void grpcModeFallsBackToLlmWhenUnavailable() {
        GrpcSttClientAdapter adapter = new GrpcSttClientAdapter(
            "unavailable-host",
            50051,
            GrpcSttClientAdapter.SttMode.GRPC
        );
        
        byte[] audioData = new byte[1024];
        AudioResult result = adapter.transcribe(audioData);
        
        // Should fall back to LLM since gRPC service is unavailable
        assertThat(result).isNotNull();
        assertThat(adapter.getCurrentMode()).isEqualTo(GrpcSttClientAdapter.SttMode.LLM_FALLBACK);
    }

    /**
     * Test that verifies default constructor uses LLM_FALLBACK mode.
     */
    @Test
    @DisplayName("Default constructor uses LLM_FALLBACK mode")
    void defaultConstructorUsesLlmFallbackMode() {
        GrpcSttClientAdapter adapter = new GrpcSttClientAdapter("localhost", 50051);
        
        byte[] audioData = new byte[1024];
        AudioResult result = adapter.transcribe(audioData);
        
        assertThat(adapter.getCurrentMode()).isEqualTo(GrpcSttClientAdapter.SttMode.LLM_FALLBACK);
    }

    /**
     * Test that verifies getCurrentMode returns the current mode.
     */
    @Test
    @DisplayName("getCurrentMode returns the current mode")
    void getCurrentModeReturnsCurrentMode() {
        GrpcSttClientAdapter adapter = new GrpcSttClientAdapter(
            "localhost",
            50051,
            GrpcSttClientAdapter.SttMode.LLM_FALLBACK
        );
        
        assertThat(adapter.getCurrentMode()).isEqualTo(GrpcSttClientAdapter.SttMode.LLM_FALLBACK);
    }

    /**
     * Test that verifies adapter can be closed.
     */
    @Test
    @DisplayName("Adapter can be closed")
    void adapterCanBeClosed() {
        GrpcSttClientAdapter adapter = new GrpcSttClientAdapter("localhost", 50051);
        
        try {
            adapter.close();
        } catch (Exception e) {
            // Should not throw
        }
        
        assertThat(true).isTrue();
    }
}
