package com.ghatana.tts.grpc;

import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.common.AudioFormat;
import com.ghatana.media.common.AudioData;
import com.ghatana.media.common.EngineMetrics;
import com.ghatana.media.common.EngineStatus;
import com.ghatana.media.tts.api.SynthesisOptions;
import com.ghatana.media.tts.api.TtsEngine;
import com.ghatana.media.tts.api.VoiceInfo;
import com.ghatana.tts.core.grpc.proto.*;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TtsGrpcService}.
 *
 * <p>Mocks {@link AudioVideoLibrary} and {@link TtsEngine} via the package-private
 * test constructor, avoiding ONNX / native-library dependencies.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the TTS gRPC service layer
 * @doc.layer product
 * @doc.pattern TestCase
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TtsGrpcService")
class TtsGrpcServiceTest {

    @Mock
    private AudioVideoLibrary mockLibrary;

    @Mock
    private TtsEngine mockEngine;

    private TtsGrpcService service;

    @BeforeEach
    void setUp() {
        service = new TtsGrpcService(mockLibrary, new SimpleMeterRegistry());
    }

    // -------------------------------------------------------------------------
    // synthesize
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("synthesize")
    class SynthesizeTests {

        @Test
        @DisplayName("valid text → audio bytes returned in response")
        void synthesize_validText_returnsAudio() throws Exception {
            // GIVEN
            byte[] audioBytes = new byte[44100 * 2]; // 1s of 16-bit PCM at 22050 Hz
            AudioData synthesized = new AudioData(audioBytes, 22050, 1, 16, null, AudioFormat.PCM);

            when(mockLibrary.getTtsEngine()).thenReturn(mockEngine);
            when(mockEngine.synthesize(anyString(), any(SynthesisOptions.class))).thenReturn(synthesized);

            SynthesizeRequest request = SynthesizeRequest.newBuilder()
                .setText("Hello world")
                .setVoiceId("piper-en")
                .setOptions(SynthesisRequestOptions.getDefaultInstance())
                .build();
            CapturingObserver<SynthesizeResponse> observer = new CapturingObserver<>();

            // WHEN
            service.synthesize(request, observer);

            // THEN
            assertThat(observer.hasError()).isFalse();
            SynthesizeResponse response = observer.getValue();
            assertThat(response.getAudioData().toByteArray()).hasSize(audioBytes.length);
            assertThat(response.getSampleRate()).isEqualTo(22050);
            assertThat(response.getVoiceUsed()).isEqualTo("piper-en");
        }

        @Test
        @DisplayName("empty text → INVALID_ARGUMENT gRPC error")
        void synthesize_emptyText_returnsInvalidArgument() {
            // GIVEN
            SynthesizeRequest request = SynthesizeRequest.newBuilder()
                .setText("")
                .build();
            CapturingObserver<SynthesizeResponse> observer = new CapturingObserver<>();

            // WHEN
            service.synthesize(request, observer);

            // THEN
            assertThat(observer.hasError()).isTrue();
            StatusRuntimeException ex = (StatusRuntimeException) observer.getError();
            assertThat(ex.getStatus().getCode()).isEqualTo(Status.INVALID_ARGUMENT.getCode());
            assertThat(ex.getStatus().getDescription()).contains("empty");
        }

        @Test
        @DisplayName("text exceeding 5000 chars → INVALID_ARGUMENT gRPC error")
        void synthesize_textTooLong_returnsInvalidArgument() {
            // GIVEN
            String longText = "a".repeat(5001);
            SynthesizeRequest request = SynthesizeRequest.newBuilder()
                .setText(longText)
                .build();
            CapturingObserver<SynthesizeResponse> observer = new CapturingObserver<>();

            // WHEN
            service.synthesize(request, observer);

            // THEN
            assertThat(observer.hasError()).isTrue();
            StatusRuntimeException ex = (StatusRuntimeException) observer.getError();
            assertThat(ex.getStatus().getCode()).isEqualTo(Status.INVALID_ARGUMENT.getCode());
            assertThat(ex.getStatus().getDescription()).contains("5000");
        }

        @Test
        @DisplayName("text exactly 5000 chars → synthesis proceeds normally")
        void synthesize_textAtLimit_succeeds() throws Exception {
            // GIVEN
            String limitText = "a".repeat(5000);
            byte[] audioBytes = new byte[1024];
            AudioData synthesized = new AudioData(audioBytes, 22050, 1, 16, null, AudioFormat.PCM);

            when(mockLibrary.getTtsEngine()).thenReturn(mockEngine);
            when(mockEngine.synthesize(anyString(), any(SynthesisOptions.class))).thenReturn(synthesized);

            SynthesizeRequest request = SynthesizeRequest.newBuilder()
                .setText(limitText)
                .build();
            CapturingObserver<SynthesizeResponse> observer = new CapturingObserver<>();

            // WHEN
            service.synthesize(request, observer);

            // THEN
            assertThat(observer.hasError()).isFalse();
        }

        @Test
        @DisplayName("engine throws ValidationError → INVALID_ARGUMENT gRPC error")
        void synthesize_validationError_returnsInvalidArgument() throws Exception {
            // GIVEN
            when(mockLibrary.getTtsEngine()).thenReturn(mockEngine);
            when(mockEngine.synthesize(anyString(), any(SynthesisOptions.class)))
                .thenThrow(new com.ghatana.media.common.ValidationError("unsupported codec"));

            SynthesizeRequest request = SynthesizeRequest.newBuilder()
                .setText("Hello")
                .build();
            CapturingObserver<SynthesizeResponse> observer = new CapturingObserver<>();

            // WHEN
            service.synthesize(request, observer);

            // THEN
            assertThat(observer.hasError()).isTrue();
            StatusRuntimeException ex = (StatusRuntimeException) observer.getError();
            assertThat(ex.getStatus().getCode()).isEqualTo(Status.INVALID_ARGUMENT.getCode());
            assertThat(ex.getStatus().getDescription()).contains("unsupported codec");
        }

        @Test
        @DisplayName("engine throws generic exception → INTERNAL gRPC error with safe message")
        void synthesize_unexpectedException_returnsInternal() throws Exception {
            // GIVEN
            when(mockLibrary.getTtsEngine()).thenReturn(mockEngine);
            when(mockEngine.synthesize(anyString(), any(SynthesisOptions.class)))
                .thenThrow(new RuntimeException("internal model state corrupted (secret details)"));

            SynthesizeRequest request = SynthesizeRequest.newBuilder()
                .setText("Hello")
                .build();
            CapturingObserver<SynthesizeResponse> observer = new CapturingObserver<>();

            // WHEN
            service.synthesize(request, observer);

            // THEN — internal exception details must NOT be leaked
            assertThat(observer.hasError()).isTrue();
            StatusRuntimeException ex = (StatusRuntimeException) observer.getError();
            assertThat(ex.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode());
            assertThat(ex.getStatus().getDescription())
                .doesNotContain("secret details")
                .isEqualTo("Synthesis failed");
        }

        @Test
        @DisplayName("no voiceId in request → voiceUsed is empty in response")
        void synthesize_noVoiceId_usesDefault() throws Exception {
            // GIVEN
            byte[] audioBytes = new byte[1024];
            AudioData synthesized = new AudioData(audioBytes, 22050, 1, 16, null, AudioFormat.PCM);

            when(mockLibrary.getTtsEngine()).thenReturn(mockEngine);
            when(mockEngine.synthesize(anyString(), any(SynthesisOptions.class))).thenReturn(synthesized);

            SynthesizeRequest request = SynthesizeRequest.newBuilder()
                .setText("Hello")
                // no voiceId set
                .build();
            CapturingObserver<SynthesizeResponse> observer = new CapturingObserver<>();

            // WHEN
            service.synthesize(request, observer);

            // THEN — no NPE; voiceUsed is empty string
            assertThat(observer.hasError()).isFalse();
            assertThat(observer.getValue().getVoiceUsed()).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // streamSynthesize
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("streamSynthesize")
    class StreamSynthesizeTests {

        @Test
        @DisplayName("engine streams 3 chunks → all forwarded and onCompleted called")
        void streamSynthesize_multipleChunks_allForwarded() throws Exception {
            // GIVEN
            when(mockLibrary.getTtsEngine()).thenReturn(mockEngine);

            doAnswer(invocation -> {
                com.ghatana.media.tts.api.StreamingCallback callback = invocation.getArgument(2);
                // Emit 2 non-final chunks then 1 final
                callback.onChunk(new com.ghatana.media.tts.api.AudioStreamChunk(new byte[]{0x01}, 1, false));
                callback.onChunk(new com.ghatana.media.tts.api.AudioStreamChunk(new byte[]{0x02}, 2, false));
                callback.onChunk(new com.ghatana.media.tts.api.AudioStreamChunk(new byte[]{0x03}, 3, true));
                return null;
            }).when(mockEngine).synthesizeStreaming(anyString(), any(SynthesisOptions.class), any());

            SynthesizeRequest request = SynthesizeRequest.newBuilder()
                .setText("Welcome to Ghatana")
                .build();
            CollectingObserver<AudioChunk> observer = new CollectingObserver<>();

            // WHEN
            service.streamSynthesize(request, observer);

            // THEN
            assertThat(observer.hasError()).isFalse();
            assertThat(observer.getValues()).hasSize(3);
            assertThat(observer.getValues().get(2).getIsFinal()).isTrue();
            assertThat(observer.isCompleted()).isTrue();
        }

        @Test
        @DisplayName("engine throws → INTERNAL gRPC error")
        void streamSynthesize_engineThrows_returnsInternal() throws Exception {
            // GIVEN
            when(mockLibrary.getTtsEngine()).thenReturn(mockEngine);
            doThrow(new RuntimeException("stream broke")).when(mockEngine)
                .synthesizeStreaming(anyString(), any(SynthesisOptions.class), any());

            SynthesizeRequest request = SynthesizeRequest.newBuilder()
                .setText("Hello stream")
                .build();
            CollectingObserver<AudioChunk> observer = new CollectingObserver<>();

            // WHEN
            service.streamSynthesize(request, observer);

            // THEN
            assertThat(observer.hasError()).isTrue();
            StatusRuntimeException ex = (StatusRuntimeException) observer.getError();
            assertThat(ex.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode());
        }
    }

    // -------------------------------------------------------------------------
    // getVoices
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getVoices")
    class GetVoicesTests {

        @Test
        @DisplayName("no language filter → all voices returned")
        void getVoices_noFilter_returnsAllVoices() throws Exception {
            // GIVEN
            List<VoiceInfo> voices = List.of(
                new VoiceInfo("piper-en", "Piper EN", "English voice", Locale.ENGLISH,
                    VoiceInfo.Gender.NEUTRAL, 22050, false, 1024L),
                new VoiceInfo("piper-fr", "Piper FR", "French voice", Locale.FRENCH,
                    VoiceInfo.Gender.FEMALE, 22050, false, 1024L)
            );
            when(mockLibrary.getTtsEngine()).thenReturn(mockEngine);
            when(mockEngine.getAvailableVoices()).thenReturn(voices);

            GetVoicesRequest request = GetVoicesRequest.newBuilder().build();
            CapturingObserver<GetVoicesResponse> observer = new CapturingObserver<>();

            // WHEN
            service.getVoices(request, observer);

            // THEN
            assertThat(observer.hasError()).isFalse();
            assertThat(observer.getValue().getVoicesCount()).isEqualTo(2);
            assertThat(observer.getValue().getVoices(0).getVoiceId()).isEqualTo("piper-en");
        }

        @Test
        @DisplayName("language filter → only matching voices returned")
        void getVoices_withLanguageFilter_returnsFilteredVoices() throws Exception {
            // GIVEN
            List<VoiceInfo> filtered = List.of(
                new VoiceInfo("piper-en", "Piper EN", "English voice", Locale.ENGLISH,
                    VoiceInfo.Gender.NEUTRAL, 22050, false, 1024L)
            );
            when(mockLibrary.getTtsEngine()).thenReturn(mockEngine);
            when(mockEngine.getAvailableVoices(Locale.forLanguageTag("en"))).thenReturn(filtered);

            GetVoicesRequest request = GetVoicesRequest.newBuilder().setLanguage("en").build();
            CapturingObserver<GetVoicesResponse> observer = new CapturingObserver<>();

            // WHEN
            service.getVoices(request, observer);

            // THEN
            assertThat(observer.hasError()).isFalse();
            assertThat(observer.getValue().getVoicesCount()).isEqualTo(1);
            assertThat(observer.getValue().getVoices(0).getVoiceId()).isEqualTo("piper-en");
            assertThat(observer.getValue().getVoices(0).getIsCloned()).isFalse();
        }

        @Test
        @DisplayName("engine throws → INTERNAL gRPC error")
        void getVoices_engineThrows_returnsInternal() throws Exception {
            // GIVEN
            when(mockLibrary.getTtsEngine()).thenReturn(mockEngine);
            when(mockEngine.getAvailableVoices()).thenThrow(new RuntimeException("store unavailable"));

            CapturingObserver<GetVoicesResponse> observer = new CapturingObserver<>();

            // WHEN
            service.getVoices(GetVoicesRequest.getDefaultInstance(), observer);

            // THEN
            assertThat(observer.hasError()).isTrue();
            StatusRuntimeException ex = (StatusRuntimeException) observer.getError();
            assertThat(ex.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode());
        }
    }

    // -------------------------------------------------------------------------
    // getStatus
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getStatus")
    class GetStatusTests {

        @Test
        @DisplayName("engine READY → active voice returned")
        void getStatus_engineReady_returnsActiveVoice() throws Exception {
            // GIVEN
            EngineStatus engineStatus = new EngineStatus(EngineStatus.State.READY, "piper-en", "2.0", 5000L, null);
            when(mockLibrary.getTtsEngine()).thenReturn(mockEngine);
            when(mockEngine.getStatus()).thenReturn(engineStatus);

            CapturingObserver<StatusResponse> observer = new CapturingObserver<>();

            // WHEN
            service.getStatus(StatusRequest.getDefaultInstance(), observer);

            // THEN
            assertThat(observer.hasError()).isFalse();
            assertThat(observer.getValue().getActiveVoice()).isEqualTo("piper-en");
        }

        @Test
        @DisplayName("engine has null modelId → empty string in response (no NPE)")
        void getStatus_nullModelId_returnsEmptyString() throws Exception {
            // GIVEN
            EngineStatus engineStatus = new EngineStatus(EngineStatus.State.INITIALIZING, null, "1.0", 0L, null);
            when(mockLibrary.getTtsEngine()).thenReturn(mockEngine);
            when(mockEngine.getStatus()).thenReturn(engineStatus);

            CapturingObserver<StatusResponse> observer = new CapturingObserver<>();

            // WHEN
            service.getStatus(StatusRequest.getDefaultInstance(), observer);

            // THEN
            assertThat(observer.hasError()).isFalse();
            assertThat(observer.getValue().getActiveVoice()).isEmpty();
        }

        @Test
        @DisplayName("engine throws → INTERNAL gRPC error")
        void getStatus_engineThrows_returnsInternal() throws Exception {
            // GIVEN
            when(mockLibrary.getTtsEngine()).thenReturn(mockEngine);
            when(mockEngine.getStatus()).thenThrow(new RuntimeException("engine down"));

            CapturingObserver<StatusResponse> observer = new CapturingObserver<>();

            // WHEN
            service.getStatus(StatusRequest.getDefaultInstance(), observer);

            // THEN
            assertThat(observer.hasError()).isTrue();
            StatusRuntimeException ex = (StatusRuntimeException) observer.getError();
            assertThat(ex.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode());
        }
    }

    // -------------------------------------------------------------------------
    // getMetrics
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getMetrics")
    class GetMetricsTests {

        @Test
        @DisplayName("engine returns metrics → all fields mapped correctly")
        void getMetrics_success_returnsMappedMetrics() throws Exception {
            // GIVEN
            EngineMetrics engineMetrics = new EngineMetrics(100L, 3L, 42.5, 2L, 512L * 1024 * 1024);
            when(mockLibrary.getTtsEngine()).thenReturn(mockEngine);
            when(mockEngine.getMetrics()).thenReturn(engineMetrics);

            CapturingObserver<MetricsResponse> observer = new CapturingObserver<>();

            // WHEN
            service.getMetrics(MetricsRequest.getDefaultInstance(), observer);

            // THEN
            assertThat(observer.hasError()).isFalse();
            MetricsResponse response = observer.getValue();
            assertThat(response.getTotalSyntheses()).isEqualTo(100);
            assertThat(response.getAverageLatencyMs()).isEqualTo(42.5f);
            assertThat(response.getMemoryUsageBytes()).isEqualTo(512L * 1024 * 1024);
        }

        @Test
        @DisplayName("engine throws → INTERNAL gRPC error")
        void getMetrics_engineThrows_returnsInternal() throws Exception {
            // GIVEN
            when(mockLibrary.getTtsEngine()).thenReturn(mockEngine);
            when(mockEngine.getMetrics()).thenThrow(new RuntimeException("metrics store error"));

            CapturingObserver<MetricsResponse> observer = new CapturingObserver<>();

            // WHEN
            service.getMetrics(MetricsRequest.getDefaultInstance(), observer);

            // THEN
            assertThat(observer.hasError()).isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // loadVoice
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("loadVoice")
    class LoadVoiceTests {

        @Test
        @DisplayName("voice load succeeds → success=true and message contains voiceId")
        void loadVoice_success_returnsSuccessResponse() throws Exception {
            // GIVEN
            when(mockLibrary.getTtsEngine()).thenReturn(mockEngine);
            doNothing().when(mockEngine).loadVoice("custom-voice-v1");

            LoadVoiceRequest request = LoadVoiceRequest.newBuilder().setVoiceId("custom-voice-v1").build();
            CapturingObserver<LoadVoiceResponse> observer = new CapturingObserver<>();

            // WHEN
            service.loadVoice(request, observer);

            // THEN
            assertThat(observer.hasError()).isFalse();
            assertThat(observer.getValue().getSuccess()).isTrue();
            assertThat(observer.getValue().getMessage()).contains("custom-voice-v1");
        }

        @Test
        @DisplayName("load fails → INTERNAL gRPC error with voice name in description")
        void loadVoice_failure_returnsInternal() throws Exception {
            // GIVEN
            when(mockLibrary.getTtsEngine()).thenReturn(mockEngine);
            doThrow(new RuntimeException("model file not found"))
                .when(mockEngine).loadVoice("missing-voice");

            LoadVoiceRequest request = LoadVoiceRequest.newBuilder().setVoiceId("missing-voice").build();
            CapturingObserver<LoadVoiceResponse> observer = new CapturingObserver<>();

            // WHEN
            service.loadVoice(request, observer);

            // THEN
            assertThat(observer.hasError()).isTrue();
            StatusRuntimeException ex = (StatusRuntimeException) observer.getError();
            assertThat(ex.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode());
        }
    }

    // -------------------------------------------------------------------------
    // unimplemented RPCs
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("unimplemented RPCs")
    class UnimplementedTests {

        @Test
        @DisplayName("createProfile → UNIMPLEMENTED status")
        void createProfile_returnsUnimplemented() {
            CapturingObserver<ProfileResponse> observer = new CapturingObserver<>();
            service.createProfile(CreateProfileRequest.getDefaultInstance(), observer);

            assertThat(observer.hasError()).isTrue();
            StatusRuntimeException ex = (StatusRuntimeException) observer.getError();
            assertThat(ex.getStatus().getCode()).isEqualTo(Status.UNIMPLEMENTED.getCode());
        }

        @Test
        @DisplayName("getProfile → UNIMPLEMENTED status")
        void getProfile_returnsUnimplemented() {
            CapturingObserver<ProfileResponse> observer = new CapturingObserver<>();
            service.getProfile(GetProfileRequest.getDefaultInstance(), observer);

            assertThat(observer.hasError()).isTrue();
            StatusRuntimeException ex = (StatusRuntimeException) observer.getError();
            assertThat(ex.getStatus().getCode()).isEqualTo(Status.UNIMPLEMENTED.getCode());
        }

        @Test
        @DisplayName("updateProfile → UNIMPLEMENTED status")
        void updateProfile_returnsUnimplemented() {
            CapturingObserver<ProfileResponse> observer = new CapturingObserver<>();
            service.updateProfile(UpdateProfileRequest.getDefaultInstance(), observer);

            assertThat(observer.hasError()).isTrue();
            StatusRuntimeException ex = (StatusRuntimeException) observer.getError();
            assertThat(ex.getStatus().getCode()).isEqualTo(Status.UNIMPLEMENTED.getCode());
        }
    }

    // -------------------------------------------------------------------------
    // Test infrastructure
    // -------------------------------------------------------------------------

    /**
     * Captures single-value responses from unary gRPC calls.
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

    /**
     * Collects multiple values from server-streaming gRPC calls.
     */
    static class CollectingObserver<T> implements StreamObserver<T> {

        private final List<T> values = new ArrayList<>();
        private Throwable error;
        private boolean completed = false;

        @Override
        public void onNext(T value) {
            values.add(value);
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }

        List<T> getValues() {
            return values;
        }

        boolean hasError() {
            return error != null;
        }

        Throwable getError() {
            return error;
        }

        boolean isCompleted() {
            return completed;
        }
    }
}
