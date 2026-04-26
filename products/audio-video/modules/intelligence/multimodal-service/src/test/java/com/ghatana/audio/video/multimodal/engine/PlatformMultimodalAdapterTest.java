package com.ghatana.audio.video.multimodal.engine;

import com.ghatana.audio.video.vision.video.VideoFrameExtractor;
import com.ghatana.media.AudioVideoLibrary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Unit tests for PlatformMultimodalAdapter STT adapter routing
 * @doc.layer test
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("PlatformMultimodalAdapter")
class PlatformMultimodalAdapterTest {

    @Mock
    private AudioVideoLibrary library;

    @Mock
    private VideoFrameExtractor frameExtractor;

    @Mock
    private SttClientAdapter sttClientAdapter;

    @Test
    @DisplayName("transcribe delegates to STT adapter and returns adapter result")
    void transcribeDelegatesToSttAdapter() { // GH-90000
        AudioResult expected = AudioResult.builder() // GH-90000
            .transcription("hello via llm")
            .confidence(0.93) // GH-90000
            .build(); // GH-90000
        when(sttClientAdapter.transcribe(any())).thenReturn(expected); // GH-90000

        PlatformMultimodalAdapter adapter = new PlatformMultimodalAdapter( // GH-90000
            AudioVideoRuntimeSettings.defaults(), // GH-90000
            library,
            frameExtractor,
            sttClientAdapter
        );

        AudioResult actual = adapter.transcribe(new byte[] {1, 2, 3}); // GH-90000

        assertThat(actual.getTranscription()).isEqualTo("hello via llm");
        assertThat(actual.getConfidence()).isEqualTo(0.93); // GH-90000
    }
}
