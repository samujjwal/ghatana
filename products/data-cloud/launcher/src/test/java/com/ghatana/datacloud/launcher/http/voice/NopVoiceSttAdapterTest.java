/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.voice;

import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests for NopVoiceSttAdapter
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("NopVoiceSttAdapter")
class NopVoiceSttAdapterTest {

    @Test
    @DisplayName("INSTANCE returns singleton")
    void instance_returnsSingleton() {
        assertThat(NopVoiceSttAdapter.INSTANCE).isNotNull();
        assertThat(NopVoiceSttAdapter.INSTANCE).isSameAs(NopVoiceSttAdapter.INSTANCE);
    }

    @Test
    @DisplayName("transcribe returns unavailable transcription")
    void transcribe_returnsUnavailable() {
        byte[] audioData = new byte[]{1, 2, 3};
        Promise<SttTranscription> result = NopVoiceSttAdapter.INSTANCE.transcribe(
            audioData, "wav", "en-US"
        );
        
        assertThat(result).isNotNull();
        SttTranscription transcription = result.getResult();
        assertThat(transcription).isNotNull();
    }

    @Test
    @DisplayName("isAvailable returns false")
    void isAvailable_returnsFalse() {
        assertThat(NopVoiceSttAdapter.INSTANCE.isAvailable()).isFalse();
    }
}
