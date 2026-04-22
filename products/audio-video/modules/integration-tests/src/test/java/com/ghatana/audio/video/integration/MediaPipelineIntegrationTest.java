package com.ghatana.audio.video.integration;

import com.ghatana.media.config.SttConfig;
import com.ghatana.media.config.TtsConfig;
import com.ghatana.media.common.validation.MediaFormatValidator;
import com.ghatana.media.stt.api.SttEngineFactory;
import com.ghatana.media.tts.api.TtsEngineFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Integration smoke tests for core media pipeline dependencies
 * @doc.layer test
 * @doc.pattern IntegrationTest
 */
@DisplayName("Media Pipeline Integration Tests")
class MediaPipelineIntegrationTest {

    @Test
    @DisplayName("detectAudioFormat identifies WAV by header")
    void shouldDetectWavHeader() {
        byte[] wavHeader = new byte[] {
            'R', 'I', 'F', 'F', 0, 0, 0, 0,
            'W', 'A', 'V', 'E', 'f', 'm', 't', ' '
        };

        assertThat(MediaFormatValidator.detectAudioFormat(wavHeader)).isEqualTo("WAV");
    }

    @Test
    @DisplayName("STT engine factory returns an engine instance")
    void shouldCreateSttEngine() {
        SttConfig config = SttConfig.builder().modelId("whisper-tiny").build();
        assertThat(SttEngineFactory.create(config, null)).isNotNull();
    }

    @Test
    @DisplayName("TTS engine factory returns an engine instance")
    void shouldCreateTtsEngine() {
        TtsConfig config = TtsConfig.builder().defaultVoiceId("en-us").build();
        assertThat(TtsEngineFactory.create(config, null)).isNotNull();
    }
}
