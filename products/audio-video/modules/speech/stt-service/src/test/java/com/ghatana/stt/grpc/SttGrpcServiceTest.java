package com.ghatana.stt.grpc;

import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.common.EngineStatus;
import com.ghatana.media.common.InferenceError;
import com.ghatana.media.common.ValidationError;
import com.ghatana.media.stt.api.SttEngine;
import com.ghatana.media.stt.api.TranscriptionOptions;
import com.ghatana.media.stt.api.TranscriptionResult;
import com.ghatana.stt.core.grpc.proto.*;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SttGrpcService}.
 *
 * <p>Mocks {@link AudioVideoLibrary} (final class — supported by Mockito 5 inline mock-maker)
 * and {@link SttEngine} to avoid ONNX / native-library dependencies.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the STT gRPC service layer
 * @doc.layer product
 * @doc.pattern TestCase
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SttGrpcService")
class SttGrpcServiceTest {

    @Mock
    private AudioVideoLibrary mockLibrary;

    @Mock
    private SttEngine mockEngine;

    private SttGrpcService service;

    @BeforeEach
    void setUp() {
        // Use test constructor to inject pre-configured mocks
        service = new SttGrpcService(mockLibrary, new SimpleMeterRegistry());
    }

    // -------------------------------------------------------------------------
    // transcribe
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("transcribe: valid audio → text returned in response")
    void transcribe_validAudio_returnsTranscription() throws Exception {
        // GIVEN
        byte[] audioBytes = new byte[]{0x01, 0x02, 0x03, 0x04};
        TranscriptionResult result = new TranscriptionResult(
            "hello world",
            0.95,
            Collections.emptyList(),
            Collections.emptyList(),
            Duration.ofMillis(120),
            "en",
            "whisper-base"
        );
        when(mockLibrary.getSttEngine()).thenReturn(mockEngine);
        when(mockEngine.transcribe(any(), any(TranscriptionOptions.class))).thenReturn(result);

        TranscribeRequest request = TranscribeRequest.newBuilder()
            .setAudioData(ByteString.copyFrom(audioBytes))
            .setSampleRate(16000)
            .build();
        CapturingObserver<TranscribeResponse> observer = new CapturingObserver<>();

        // WHEN
        service.transcribe(request, observer);

        // THEN
        assertThat(observer.hasError()).isFalse();
        TranscribeResponse response = observer.getValue();
        assertThat(response.getText()).isEqualTo("hello world");
        assertThat(response.getConfidence()).isEqualTo(0.95f);
        assertThat(response.getProcessingTimeMs()).isEqualTo(120L);
        assertThat(response.getModelUsed()).isEqualTo("whisper-base");
    }

    @Test
    @DisplayName("transcribe: empty audio bytes → INVALID_ARGUMENT gRPC error")
    void transcribe_emptyAudio_returnsInvalidArgument() {
        // GIVEN
        TranscribeRequest request = TranscribeRequest.newBuilder()
            .setAudioData(ByteString.EMPTY)
            .setSampleRate(16000)
            .build();
        CapturingObserver<TranscribeResponse> observer = new CapturingObserver<>();

        // WHEN
        service.transcribe(request, observer);

        // THEN — service guards against empty audio before calling engine
        assertThat(observer.hasError()).isTrue();
        assertThat(observer.getError()).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException ex = (StatusRuntimeException) observer.getError();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INVALID_ARGUMENT.getCode());
    }

    @Test
    @DisplayName("transcribe: ValidationError from engine → INVALID_ARGUMENT gRPC error")
    void transcribe_validationError_returnsInvalidArgument() throws Exception {
        // GIVEN
        when(mockLibrary.getSttEngine()).thenReturn(mockEngine);
        when(mockEngine.transcribe(any(), any(TranscriptionOptions.class)))
            .thenThrow(new ValidationError("bad audio format"));

        TranscribeRequest request = TranscribeRequest.newBuilder()
            .setAudioData(ByteString.copyFrom(new byte[]{0x01}))
            .setSampleRate(16000)
            .build();
        CapturingObserver<TranscribeResponse> observer = new CapturingObserver<>();

        // WHEN
        service.transcribe(request, observer);

        // THEN
        assertThat(observer.hasError()).isTrue();
        StatusRuntimeException ex = (StatusRuntimeException) observer.getError();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INVALID_ARGUMENT.getCode());
        assertThat(ex.getStatus().getDescription()).contains("bad audio format");
    }

    @Test
    @DisplayName("transcribe: non-retryable InferenceError → INTERNAL gRPC error")
    void transcribe_nonRetryableInferenceError_returnsInternal() throws Exception {
        // GIVEN
        when(mockLibrary.getSttEngine()).thenReturn(mockEngine);
        when(mockEngine.transcribe(any(), any(TranscriptionOptions.class)))
            .thenThrow(new InferenceError("model crash", null, false));

        TranscribeRequest request = TranscribeRequest.newBuilder()
            .setAudioData(ByteString.copyFrom(new byte[]{0x01}))
            .setSampleRate(16000)
            .build();
        CapturingObserver<TranscribeResponse> observer = new CapturingObserver<>();

        // WHEN
        service.transcribe(request, observer);

        // THEN
        assertThat(observer.hasError()).isTrue();
        StatusRuntimeException ex = (StatusRuntimeException) observer.getError();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode());
    }

    @Test
    @DisplayName("transcribe: retryable InferenceError → UNAVAILABLE gRPC error")
    void transcribe_retryableInferenceError_returnsUnavailable() throws Exception {
        // GIVEN
        when(mockLibrary.getSttEngine()).thenReturn(mockEngine);
        when(mockEngine.transcribe(any(), any(TranscriptionOptions.class)))
            .thenThrow(new InferenceError("backend overloaded", null, true));

        TranscribeRequest request = TranscribeRequest.newBuilder()
            .setAudioData(ByteString.copyFrom(new byte[]{0x01}))
            .setSampleRate(16000)
            .build();
        CapturingObserver<TranscribeResponse> observer = new CapturingObserver<>();

        // WHEN
        service.transcribe(request, observer);

        // THEN
        assertThat(observer.hasError()).isTrue();
        StatusRuntimeException ex = (StatusRuntimeException) observer.getError();
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.UNAVAILABLE.getCode());
    }

    @Test
    @DisplayName("transcribe: default sample rate applied when not set in request")
    void transcribe_defaultSampleRate_noNullPointer() throws Exception {
        // GIVEN — request without explicit sample_rate (defaults to 0)
        TranscriptionResult result = new TranscriptionResult(
            "test", 1.0, Collections.emptyList(), Collections.emptyList(),
            Duration.ofMillis(10), "en", "whisper-base"
        );
        when(mockLibrary.getSttEngine()).thenReturn(mockEngine);
        when(mockEngine.transcribe(any(), any(TranscriptionOptions.class))).thenReturn(result);

        TranscribeRequest request = TranscribeRequest.newBuilder()
            .setAudioData(ByteString.copyFrom(new byte[]{0x01, 0x02}))
            .build();  // no sample rate — service should default to 16000
        CapturingObserver<TranscribeResponse> observer = new CapturingObserver<>();

        // WHEN
        service.transcribe(request, observer);

        // THEN
        assertThat(observer.hasError()).isFalse();
        assertThat(observer.getValue().getText()).isEqualTo("test");
    }

    // -------------------------------------------------------------------------
    // getStatus
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getStatus: engine READY → active model name returned")
    void getStatus_engineReady_returnsModel() throws Exception {
        // GIVEN
        EngineStatus engineStatus = new EngineStatus(
            EngineStatus.State.READY, "whisper-base", "1.0", 5000L, null
        );
        when(mockLibrary.getSttEngine()).thenReturn(mockEngine);
        when(mockEngine.getStatus()).thenReturn(engineStatus);

        CapturingObserver<StatusResponse> observer = new CapturingObserver<>();

        // WHEN
        service.getStatus(StatusRequest.getDefaultInstance(), observer);

        // THEN
        assertThat(observer.hasError()).isFalse();
        assertThat(observer.getValue().getActiveModel()).isEqualTo("whisper-base");
    }

    // -------------------------------------------------------------------------
    // submitCorrection
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("submitCorrection: always accepted")
    void submitCorrection_alwaysAccepted() {
        // GIVEN
        CapturingObserver<CorrectionResponse> observer = new CapturingObserver<>();

        // WHEN
        service.submitCorrection(CorrectionRequest.getDefaultInstance(), observer);

        // THEN
        assertThat(observer.hasError()).isFalse();
        assertThat(observer.getValue().getAccepted()).isTrue();
    }

    // -------------------------------------------------------------------------
    // Test infrastructure
    // -------------------------------------------------------------------------

    /**
     * Simple {@link StreamObserver} that captures a single value or error for assertions.
     */
    static class CapturingObserver<T> implements StreamObserver<T> {

        private T value;
        private Throwable error;

        @Override
        public void onNext(T value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }

        @Override
        public void onCompleted() {
        }

        T getValue() {
            return value;
        }

        boolean hasError() {
            return error != null;
        }

        Throwable getError() {
            return error;
        }
    }
}
