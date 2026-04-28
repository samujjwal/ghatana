/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.voice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests for SttTranscription
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("SttTranscription")
class SttTranscriptionTest {

    @Test
    @DisplayName("of creates transcription with provider")
    void of_createsTranscription() {
        SttTranscription result = SttTranscription.of("hello world", 0.95, "whisper");
        
        assertThat(result.text()).isEqualTo("hello world");
        assertThat(result.confidence()).isEqualTo(0.95);
        assertThat(result.provider()).isEqualTo("whisper");
        assertThat(result.fallback()).isFalse();
    }

    @Test
    @DisplayName("unavailable creates fallback transcription")
    void unavailable_createsFallback() {
        SttTranscription result = SttTranscription.unavailable();
        
        assertThat(result.text()).isEmpty();
        assertThat(result.confidence()).isEqualTo(0.0);
        assertThat(result.provider()).isEqualTo("nop");
        assertThat(result.fallback()).isTrue();
    }

    @Test
    @DisplayName("record constructor works")
    void record_constructor() {
        SttTranscription result = new SttTranscription("test", 0.8, "test-provider", false);
        
        assertThat(result.text()).isEqualTo("test");
        assertThat(result.confidence()).isEqualTo(0.8);
        assertThat(result.provider()).isEqualTo("test-provider");
        assertThat(result.fallback()).isFalse();
    }
}
