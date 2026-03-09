package com.ghatana.tts.core.grpc;

// Explicit imports from API package (used for engine mocking)
import com.ghatana.tts.core.api.TtsEngine;
import com.ghatana.tts.core.api.SynthesisResult;
import com.ghatana.tts.core.api.EngineStatus;
import com.ghatana.tts.core.api.EngineMetrics;
import com.ghatana.tts.core.api.UserProfile;
import com.ghatana.tts.core.api.ProfileStats;
import com.ghatana.tts.core.api.ProfileSettings;
import com.ghatana.tts.core.api.AdaptationMode;
import com.ghatana.tts.core.api.PrivacyLevel;
import com.ghatana.tts.core.api.EngineState;
import com.ghatana.tts.core.api.VoiceInfo;
import com.ghatana.tts.core.api.SynthesisOptions;

// gRPC proto classes (request/response types)
import com.ghatana.tts.core.grpc.proto.SynthesizeRequest;
import com.ghatana.tts.core.grpc.proto.SynthesizeResponse;
import com.ghatana.tts.core.grpc.proto.StatusRequest;
import com.ghatana.tts.core.grpc.proto.StatusResponse;
import com.ghatana.tts.core.grpc.proto.MetricsRequest;
import com.ghatana.tts.core.grpc.proto.MetricsResponse;
import com.ghatana.tts.core.grpc.proto.GetVoicesRequest;
import com.ghatana.tts.core.grpc.proto.GetVoicesResponse;
import com.ghatana.tts.core.grpc.proto.CreateProfileRequest;
import com.ghatana.tts.core.grpc.proto.ProfileResponse;

import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TtsGrpcService.
 * 
 * @doc.type class
 * @doc.purpose TTS gRPC service tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("TtsGrpcService Tests")
class TtsGrpcServiceTest {

    private TtsEngine mockEngine;
    private TtsGrpcService service;

    @BeforeEach
    void setUp() {
        mockEngine = mock(TtsEngine.class);
        service = new TtsGrpcService(mockEngine);
    }

    @Test
    @DisplayName("synthesize should return audio data on success")
    void synthesize_shouldReturnAudioData() {
        // GIVEN
        SynthesisResult engineResult = new SynthesisResult(
            new byte[]{1, 2, 3, 4},
            16000,
            1000L,
            "test-voice"
        );
        when(mockEngine.synthesize(anyString(), any())).thenReturn(engineResult);

        SynthesizeRequest request = SynthesizeRequest.newBuilder()
            .setText("Hello world")
            .setVoiceId("test-voice")
            .build();

        AtomicReference<SynthesizeResponse> responseRef = new AtomicReference<>();
        StreamObserver<SynthesizeResponse> responseObserver = createResponseObserver(responseRef);

        // WHEN
        service.synthesize(request, responseObserver);

        // THEN
        assertThat(responseRef.get()).isNotNull();
        assertThat(responseRef.get().getAudioData().toByteArray()).hasSize(4);
        assertThat(responseRef.get().getSampleRate()).isEqualTo(16000);
        assertThat(responseRef.get().getDurationMs()).isEqualTo(1000L);
        assertThat(responseRef.get().getVoiceUsed()).isEqualTo("test-voice");

        verify(mockEngine).synthesize(eq("Hello world"), any(SynthesisOptions.class));
    }

    @Test
    @DisplayName("getStatus should return engine status")
    void getStatus_shouldReturnEngineStatus() {
        // GIVEN
        EngineStatus engineStatus = new EngineStatus(
            EngineState.READY,
            "default-voice",
            null,
            0
        );
        when(mockEngine.getStatus()).thenReturn(engineStatus);

        StatusRequest request = StatusRequest.newBuilder()
            .setIncludeMetrics(false)
            .build();

        AtomicReference<StatusResponse> responseRef = new AtomicReference<>();
        StreamObserver<StatusResponse> responseObserver = createResponseObserver(responseRef);

        // WHEN
        service.getStatus(request, responseObserver);

        // THEN
        assertThat(responseRef.get()).isNotNull();
        assertThat(responseRef.get().getState()).isEqualTo(
            com.ghatana.tts.core.grpc.proto.EngineState.ENGINE_STATE_READY
        );
        assertThat(responseRef.get().getActiveVoice()).isEqualTo("default-voice");
    }

    @Test
    @DisplayName("getMetrics should return engine metrics")
    void getMetrics_shouldReturnEngineMetrics() {
        // GIVEN
        EngineMetrics engineMetrics = new EngineMetrics(
            0.5f,
            1024 * 1024 * 100L,
            2,
            1000L,
            50.0f
        );
        when(mockEngine.getMetrics()).thenReturn(engineMetrics);

        MetricsRequest request = MetricsRequest.newBuilder().build();

        AtomicReference<MetricsResponse> responseRef = new AtomicReference<>();
        StreamObserver<MetricsResponse> responseObserver = createResponseObserver(responseRef);

        // WHEN
        service.getMetrics(request, responseObserver);

        // THEN
        assertThat(responseRef.get()).isNotNull();
        assertThat(responseRef.get().getRealTimeFactor()).isEqualTo(0.5f);
        assertThat(responseRef.get().getActiveSessions()).isEqualTo(2);
        assertThat(responseRef.get().getTotalSyntheses()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("getVoices should return available voices")
    void getVoices_shouldReturnAvailableVoices() {
        // GIVEN
        List<VoiceInfo> voices = List.of(
            new VoiceInfo("voice-1", "Voice One", "A test voice", 
                List.of("en-US"), "female", 1024 * 1024, true, false),
            new VoiceInfo("voice-2", "Voice Two", null, 
                List.of("en-US", "en-GB"), "male", 2048 * 1024, false, true)
        );
        when(mockEngine.getAvailableVoices(any())).thenReturn(voices);

        GetVoicesRequest request = GetVoicesRequest.newBuilder()
            .setLanguage("en-US")
            .build();

        AtomicReference<GetVoicesResponse> responseRef = new AtomicReference<>();
        StreamObserver<GetVoicesResponse> responseObserver = createResponseObserver(responseRef);

        // WHEN
        service.getVoices(request, responseObserver);

        // THEN
        assertThat(responseRef.get()).isNotNull();
        assertThat(responseRef.get().getVoicesCount()).isEqualTo(2);
        assertThat(responseRef.get().getVoices(0).getVoiceId()).isEqualTo("voice-1");
        assertThat(responseRef.get().getVoices(1).getIsCloned()).isTrue();
    }

    @Test
    @DisplayName("createProfile should create and return profile")
    void createProfile_shouldCreateAndReturnProfile() {
        // GIVEN
        long now = System.currentTimeMillis();
        UserProfile profile = new UserProfile(
            "profile-123",
            "Test User",
            new ProfileSettings("en-US", null, AdaptationMode.BALANCED, PrivacyLevel.HIGH),
            new ProfileStats(0, 0, now, now)
        );
        when(mockEngine.createProfile(anyString(), anyString(), any())).thenReturn(profile);

        CreateProfileRequest request = CreateProfileRequest.newBuilder()
            .setDisplayName("Test User")
            .setSettings(com.ghatana.tts.core.grpc.proto.ProfileSettings.newBuilder()
                .setPreferredLanguage("en-US")
                .setAdaptationMode(com.ghatana.tts.core.grpc.proto.AdaptationMode.ADAPTATION_MODE_BALANCED)
                .setPrivacyLevel(com.ghatana.tts.core.grpc.proto.PrivacyLevel.PRIVACY_LEVEL_HIGH)
                .build())
            .build();

        AtomicReference<ProfileResponse> responseRef = new AtomicReference<>();
        StreamObserver<ProfileResponse> responseObserver = createResponseObserver(responseRef);

        // WHEN
        service.createProfile(request, responseObserver);

        // THEN
        assertThat(responseRef.get()).isNotNull();
        assertThat(responseRef.get().getProfileId()).isEqualTo("profile-123");
        assertThat(responseRef.get().getDisplayName()).isEqualTo("Test User");
    }

    private <T> StreamObserver<T> createResponseObserver(AtomicReference<T> responseRef) {
        return new StreamObserver<>() {
            @Override
            public void onNext(T value) {
                responseRef.set(value);
            }

            @Override
            public void onError(Throwable t) {
                throw new RuntimeException(t);
            }

            @Override
            public void onCompleted() {
                // no-op
            }
        };
    }
}
