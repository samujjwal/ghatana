/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.audio.video.multimodal.adapter;

import com.ghatana.audio.video.multimodal.engine.AudioResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
@DisplayName("GrpcSttClientAdapter Integration Tests [GH-90000]")
@Tag("integration [GH-90000]")
class GrpcSttClientAdapterTest {

    /**
     * Test that verifies LLM_FALLBACK mode uses AI Inference fallback.
     */
    @Test
    @DisplayName("LLM_FALLBACK mode uses AI Inference fallback [GH-90000]")
    void llmFallbackModeUsesAiInference() { // GH-90000
        try (GrpcSttClientAdapter adapter = new GrpcSttClientAdapter( // GH-90000
                "localhost",
                50051,
                GrpcSttClientAdapter.SttMode.LLM_FALLBACK
        )) {
            byte[] audioData = new byte[1024];
            AudioResult result = adapter.transcribe(audioData); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(adapter.getCurrentMode()).isEqualTo(GrpcSttClientAdapter.SttMode.LLM_FALLBACK); // GH-90000
        }
    }

    /**
     * Test that verifies NOP mode returns empty result.
     */
    @Test
    @DisplayName("NOP mode returns empty result [GH-90000]")
    void nopModeReturnsEmptyResult() { // GH-90000
        try (GrpcSttClientAdapter adapter = new GrpcSttClientAdapter( // GH-90000
                "localhost",
                50051,
                GrpcSttClientAdapter.SttMode.NOP
        )) {
            byte[] audioData = new byte[1024];
            AudioResult result = adapter.transcribe(audioData); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.getTranscription()).isEmpty(); // GH-90000
            assertThat(result.getConfidence()).isEqualTo(0.0); // GH-90000
            assertThat(adapter.getCurrentMode()).isEqualTo(GrpcSttClientAdapter.SttMode.NOP); // GH-90000
        }
    }

    /**
     * Test that verifies GRPC mode is explicitly rejected.
     */
    @Test
    @DisplayName("GRPC mode is rejected and LLM_FALLBACK is required [GH-90000]")
    void grpcModeIsRejected() { // GH-90000
        assertThatThrownBy(() -> new GrpcSttClientAdapter( // GH-90000
            "localhost",
            50051,
            GrpcSttClientAdapter.SttMode.GRPC
        ))
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("LLM_FALLBACK [GH-90000]");
    }

    /**
     * Test that verifies default constructor uses LLM_FALLBACK mode.
     */
    @Test
    @DisplayName("Default constructor uses LLM_FALLBACK mode [GH-90000]")
    void defaultConstructorUsesLlmFallbackMode() { // GH-90000
        try (GrpcSttClientAdapter adapter = new GrpcSttClientAdapter("localhost", 50051)) { // GH-90000
            byte[] audioData = new byte[1024];
            adapter.transcribe(audioData); // GH-90000

            assertThat(adapter.getCurrentMode()).isEqualTo(GrpcSttClientAdapter.SttMode.LLM_FALLBACK); // GH-90000
        }
    }

    /**
     * Test that verifies getCurrentMode returns the current mode.
     */
    @Test
    @DisplayName("getCurrentMode returns the current mode [GH-90000]")
    void getCurrentModeReturnsCurrentMode() { // GH-90000
        try (GrpcSttClientAdapter adapter = new GrpcSttClientAdapter( // GH-90000
                "localhost",
                50051,
                GrpcSttClientAdapter.SttMode.LLM_FALLBACK
        )) {
            assertThat(adapter.getCurrentMode()).isEqualTo(GrpcSttClientAdapter.SttMode.LLM_FALLBACK); // GH-90000
        }
    }

    /**
     * Test that verifies adapter can be closed.
     */
    @Test
    @DisplayName("Adapter can be closed [GH-90000]")
    void adapterCanBeClosed() { // GH-90000
        try (GrpcSttClientAdapter ignored = new GrpcSttClientAdapter("localhost", 50051)) { // GH-90000
            // Auto-close via try-with-resources
        }
    }
}
