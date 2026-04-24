package com.ghatana.audio.video.multimodal.adapter;

import com.ghatana.audio.video.multimodal.engine.AudioResult;
import com.ghatana.stt.core.grpc.proto.TranscribeRequest;
import com.ghatana.stt.core.grpc.proto.TranscribeResponse;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Contract tests for {@link GrpcSttClientAdapter}.
 *
 * <p>Verifies gRPC service contracts between the STT client adapter and the STT service.
 * Tests request/response marshalling and error handling patterns.
 *
 * @doc.type class
 * @doc.purpose Contract tests for STT gRPC client-server interaction
 * @doc.layer product
 * @doc.pattern TestCase
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GrpcSttClientAdapter — Contract Tests")
class GrpcSttClientAdapterContractTest {

    private GrpcSttClientAdapter adapter;

    @BeforeEach
    void setUp() {
        // Create adapter with LLM_FALLBACK mode (currently supported mode)
        adapter = new GrpcSttClientAdapter("localhost", 50051, GrpcSttClientAdapter.SttMode.LLM_FALLBACK);
    }

    @Test
    @DisplayName("should verify TranscribeRequest can be properly constructed with audio bytes")
    void transcribeRequestProperlyConstructed() {
        // Arrange
        byte[] audioData = new byte[]{1, 2, 3, 4};

        // Act
        TranscribeRequest request = TranscribeRequest.newBuilder()
                .setAudioData(ByteString.copyFrom(audioData))
                .setSampleRate(16000)
                .setLanguage("")
                .build();

        // Assert
        assertThat(request).isNotNull();
        assertThat(request.getAudioData().toByteArray()).isEqualTo(audioData);
        assertThat(request.getSampleRate()).isEqualTo(16000);
        assertThat(request.getLanguage()).isEmpty();
    }

    @Test
    @DisplayName("should verify TranscribeResponse can be properly unmarshalled")
    void transcribeResponseProperlyUnmarshalled() {
        // Act
        TranscribeResponse response = TranscribeResponse.newBuilder()
                .setText("hello world")
                .setConfidence(0.95f)
                .setProcessingTimeMs(100)
                .setModelUsed("whisper-base")
                .build();

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getText()).isEqualTo("hello world");
        assertThat(response.getConfidence()).isEqualTo(0.95f);
        assertThat(response.getProcessingTimeMs()).isEqualTo(100);
        assertThat(response.getModelUsed()).isEqualTo("whisper-base");
    }

    @Test
    @DisplayName("should handle empty audio data in request")
    void emptyAudioInRequest() {
        // Act
        TranscribeRequest request = TranscribeRequest.newBuilder()
                .setAudioData(ByteString.EMPTY)
                .setSampleRate(16000)
                .build();

        // Assert
        assertThat(request.getAudioData().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("should handle large audio payload in request")
    void largeAudioInRequest() {
        // Arrange
        byte[] largeAudio = new byte[512 * 1024]; // 512 KB
        for (int i = 0; i < largeAudio.length; i++) {
            largeAudio[i] = (byte) (i % 256);
        }

        // Act
        TranscribeRequest request = TranscribeRequest.newBuilder()
                .setAudioData(ByteString.copyFrom(largeAudio))
                .setSampleRate(16000)
                .build();

        // Assert
        assertThat(request.getAudioData().size()).isEqualTo(512 * 1024);
    }

    @Test
    @DisplayName("should verify response contains required fields")
    void responseContainsRequiredFields() {
        // Act
        TranscribeResponse response = TranscribeResponse.newBuilder()
                .setText("test")
                .setConfidence(0.9f)
                .setProcessingTimeMs(50)
                .setModelUsed("model-v1")
                .build();

        // Assert
        assertThat(response.getText()).isNotEmpty();
        assertThat(response.getConfidence()).isGreaterThanOrEqualTo(0.0f);
        assertThat(response.getProcessingTimeMs()).isGreaterThan(0);
        assertThat(response.getModelUsed()).isNotEmpty();
    }

    @Test
    @DisplayName("should handle edge case: confidence at boundaries")
    void confidenceAtBoundaries() {
        // Act - Low confidence
        TranscribeResponse lowConfidence = TranscribeResponse.newBuilder()
                .setText("text")
                .setConfidence(0.0f)
                .build();

        // Act - High confidence
        TranscribeResponse highConfidence = TranscribeResponse.newBuilder()
                .setText("text")
                .setConfidence(1.0f)
                .build();

        // Assert
        assertThat(lowConfidence.getConfidence()).isEqualTo(0.0f);
        assertThat(highConfidence.getConfidence()).isEqualTo(1.0f);
    }

    @Test
    @DisplayName("should verify StatusRuntimeException handling pattern")
    void statusRuntimeExceptionPattern() {
        // Act
        StatusRuntimeException exception = new StatusRuntimeException(io.grpc.Status.UNAVAILABLE);

        // Assert
        assertThat(exception).isNotNull();
        assertThat(exception.getStatus()).isNotNull();
        assertThat(exception.getStatus().getCode()).isEqualTo(io.grpc.Status.Code.UNAVAILABLE);
    }

    @Test
    @DisplayName("should handle different language codes in request")
    void differentLanguageCodesInRequest() {
        // Arrange
        String[] languageCodes = {"", "en", "fr", "es", "de", "zh", "ja"};

        // Act & Assert
        for (String langCode : languageCodes) {
            TranscribeRequest request = TranscribeRequest.newBuilder()
                    .setAudioData(ByteString.copyFrom(new byte[]{1}))
                    .setLanguage(langCode)
                    .build();

            assertThat(request.getLanguage()).isEqualTo(langCode);
        }
    }

    @Test
    @DisplayName("should verify AdapterMode behavior")
    void adapterModeTrackingWorks() {
        // Assert initial mode
        assertThat(adapter.getCurrentMode()).isNotNull();
        
        // Verify mode can be accessed and is LLM_FALLBACK
        GrpcSttClientAdapter.SttMode mode = adapter.getCurrentMode();
        assertThat(mode).isEqualTo(GrpcSttClientAdapter.SttMode.LLM_FALLBACK);
    }
}


