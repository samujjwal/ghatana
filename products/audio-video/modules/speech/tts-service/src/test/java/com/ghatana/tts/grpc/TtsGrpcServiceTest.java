package com.ghatana.tts.grpc;

import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.common.AudioData;
import com.ghatana.media.common.AudioFormat;
import com.ghatana.media.common.EngineMetrics;
import com.ghatana.media.common.EngineStatus;
import com.ghatana.media.common.ValidationError;
import com.ghatana.media.tts.api.ProfileSettings;
import com.ghatana.media.tts.api.TtsEngine;
import com.ghatana.media.tts.api.TtsProfile;
import com.ghatana.media.tts.api.VoiceInfo;
import com.ghatana.tts.core.grpc.proto.*;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TtsGrpcService}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the TTS gRPC service layer — covers AV-002 methods
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
        lenient().when(mockLibrary.getTtsEngine()).thenReturn(mockEngine);
    }

    // ─────────────────────────────────────────────────────────────
    // synthesize (existing, regression coverage)
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("synthesize: valid text → audio returned")
    void synthesize_validText_returnsAudio() throws Exception {
        AudioData audio = new AudioData(new byte[44100], 22050, 1, 16,
                Duration.ofSeconds(1), AudioFormat.PCM);
        when(mockEngine.synthesize(anyString(), any())).thenReturn(audio);

        SynthesizeRequest request = SynthesizeRequest.newBuilder().setText("Hello world").build();
        CapturingObserver<SynthesizeResponse> observer = new CapturingObserver<>();
        service.synthesize(request, observer);

        assertThat(observer.hasError()).isFalse();
        assertThat(observer.getValue().getAudioData().size()).isGreaterThan(0);
        assertThat(observer.getValue().getSampleRate()).isEqualTo(22050);
    }

    @Test
    @DisplayName("synthesize: empty text → INVALID_ARGUMENT")
    void synthesize_emptyText_returnsInvalidArgument() {
        SynthesizeRequest request = SynthesizeRequest.newBuilder().setText("").build();
        CapturingObserver<SynthesizeResponse> observer = new CapturingObserver<>();
        service.synthesize(request, observer);

        assertThat(observer.hasError()).isTrue();
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode())
                .isEqualTo(Status.INVALID_ARGUMENT.getCode());
    }

    @Test
    @DisplayName("synthesize: text > 5000 chars → INVALID_ARGUMENT")
    void synthesize_textTooLong_returnsInvalidArgument() {
        SynthesizeRequest request = SynthesizeRequest.newBuilder()
                .setText("a".repeat(5001)).build();
        CapturingObserver<SynthesizeResponse> observer = new CapturingObserver<>();
        service.synthesize(request, observer);

        assertThat(observer.hasError()).isTrue();
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode())
                .isEqualTo(Status.INVALID_ARGUMENT.getCode());
    }

    // ─────────────────────────────────────────────────────────────
    // createProfile (AV-002)
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createProfile: valid displayName → profile created and returned")
    void createProfile_validName_createsProfile() throws Exception {
        TtsProfile created = new TtsProfile("p1", "Alice", "piper-en",
                ProfileSettings.builder().build(), List.of());
        when(mockEngine.createProfile(anyString(), anyString(), any())).thenReturn(created);

        CapturingObserver<ProfileResponse> observer = new CapturingObserver<>();
        service.createProfile(
                CreateProfileRequest.newBuilder().setDisplayName("Alice").build(),
                observer);

        assertThat(observer.hasError()).isFalse();
        assertThat(observer.getValue().getDisplayName()).isEqualTo("Alice");
        assertThat(observer.getValue().getProfileId()).isNotBlank();
    }

    @Test
    @DisplayName("createProfile: blank displayName → INVALID_ARGUMENT")
    void createProfile_blankName_returnsInvalidArgument() {
        CapturingObserver<ProfileResponse> observer = new CapturingObserver<>();
        service.createProfile(CreateProfileRequest.getDefaultInstance(), observer);

        assertThat(observer.hasError()).isTrue();
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode())
                .isEqualTo(Status.INVALID_ARGUMENT.getCode());
    }

    @Test
    @DisplayName("createProfile: engine throws → INTERNAL")
    void createProfile_engineThrows_returnsInternal() throws Exception {
        when(mockEngine.createProfile(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("engine failure"));

        CapturingObserver<ProfileResponse> observer = new CapturingObserver<>();
        service.createProfile(
                CreateProfileRequest.newBuilder().setDisplayName("Alice").build(),
                observer);

        assertThat(observer.hasError()).isTrue();
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode())
                .isEqualTo(Status.INTERNAL.getCode());
    }

    // ─────────────────────────────────────────────────────────────
    // getProfile (AV-002)
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getProfile: existing profile → returned")
    void getProfile_existing_returnsProfile() throws Exception {
        TtsProfile profile = new TtsProfile("p1", "Bob", "piper-en",
                ProfileSettings.builder().build(), List.of("Hello", "World"));
        when(mockEngine.loadProfile("p1")).thenReturn(Optional.of(profile));

        CapturingObserver<ProfileResponse> observer = new CapturingObserver<>();
        service.getProfile(GetProfileRequest.newBuilder().setProfileId("p1").build(), observer);

        assertThat(observer.hasError()).isFalse();
        assertThat(observer.getValue().getProfileId()).isEqualTo("p1");
        assertThat(observer.getValue().getDisplayName()).isEqualTo("Bob");
    }

    @Test
    @DisplayName("getProfile: unknown profileId → NOT_FOUND")
    void getProfile_unknown_returnsNotFound() throws Exception {
        when(mockEngine.loadProfile("ghost")).thenReturn(Optional.empty());

        CapturingObserver<ProfileResponse> observer = new CapturingObserver<>();
        service.getProfile(GetProfileRequest.newBuilder().setProfileId("ghost").build(), observer);

        assertThat(observer.hasError()).isTrue();
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode())
                .isEqualTo(Status.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("getProfile: blank profileId → INVALID_ARGUMENT")
    void getProfile_blank_returnsInvalidArgument() {
        CapturingObserver<ProfileResponse> observer = new CapturingObserver<>();
        service.getProfile(GetProfileRequest.getDefaultInstance(), observer);

        assertThat(observer.hasError()).isTrue();
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode())
                .isEqualTo(Status.INVALID_ARGUMENT.getCode());
    }

    // ─────────────────────────────────────────────────────────────
    // updateProfile (AV-002)
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateProfile: existing profile → updated and returned")
    void updateProfile_existing_updatesProfile() throws Exception {
        TtsProfile existing = new TtsProfile("p1", "Carol", "piper-en",
                ProfileSettings.builder().build(), List.of());
        when(mockEngine.loadProfile("p1")).thenReturn(Optional.of(existing));
        doNothing().when(mockEngine).saveProfile(any());

        UpdateProfileRequest request = UpdateProfileRequest.newBuilder()
                .setProfileId("p1")
                .setSettings(com.ghatana.tts.core.grpc.proto.ProfileSettings.newBuilder()
                        .setDefaultVoiceId("piper-fr").build())
                .build();
        CapturingObserver<ProfileResponse> observer = new CapturingObserver<>();
        service.updateProfile(request, observer);

        assertThat(observer.hasError()).isFalse();
        assertThat(observer.getValue().getProfileId()).isEqualTo("p1");
        verify(mockEngine).saveProfile(any());
    }

    @Test
    @DisplayName("updateProfile: unknown profileId → NOT_FOUND")
    void updateProfile_unknown_returnsNotFound() throws Exception {
        when(mockEngine.loadProfile("ghost")).thenReturn(Optional.empty());

        CapturingObserver<ProfileResponse> observer = new CapturingObserver<>();
        service.updateProfile(
                UpdateProfileRequest.newBuilder().setProfileId("ghost").build(),
                observer);

        assertThat(observer.hasError()).isTrue();
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode())
                .isEqualTo(Status.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("updateProfile: blank profileId → INVALID_ARGUMENT")
    void updateProfile_blank_returnsInvalidArgument() {
        CapturingObserver<ProfileResponse> observer = new CapturingObserver<>();
        service.updateProfile(UpdateProfileRequest.getDefaultInstance(), observer);

        assertThat(observer.hasError()).isTrue();
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode())
                .isEqualTo(Status.INVALID_ARGUMENT.getCode());
    }

    // ─────────────────────────────────────────────────────────────
    // cloneVoice (AV-002.1)
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cloneVoice: valid name + samples → cloned voice returned")
    void cloneVoice_validRequest_returnsClonedVoice() throws Exception {
        VoiceInfo cloned = new VoiceInfo("cloned-1", "MyVoice", "Custom cloned voice",
                Locale.ENGLISH, VoiceInfo.Gender.NEUTRAL, 22050, true, 5_000_000L);
        when(mockEngine.cloneVoice(anyString(), any(), any())).thenReturn(cloned);

        CloneVoiceRequest request = CloneVoiceRequest.newBuilder()
                .setVoiceName("MyVoice")
                .addAudioSamples(ByteString.copyFrom(new byte[44100]))
                .build();
        CapturingObserver<CloneVoiceResponse> observer = new CapturingObserver<>();
        service.cloneVoice(request, observer);

        assertThat(observer.hasError()).isFalse();
        CloneVoiceResponse response = observer.getValue();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getVoiceId()).isEqualTo("cloned-1");
        assertThat(response.getVoice().getIsCloned()).isTrue();
    }

    @Test
    @DisplayName("cloneVoice: blank voiceName → INVALID_ARGUMENT")
    void cloneVoice_blankName_returnsInvalidArgument() {
        CapturingObserver<CloneVoiceResponse> observer = new CapturingObserver<>();
        service.cloneVoice(
                CloneVoiceRequest.newBuilder().setVoiceName("").build(),
                observer);

        assertThat(observer.hasError()).isTrue();
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode())
                .isEqualTo(Status.INVALID_ARGUMENT.getCode());
    }

    @Test
    @DisplayName("cloneVoice: no audio samples → INVALID_ARGUMENT")
    void cloneVoice_noSamples_returnsInvalidArgument() {
        CapturingObserver<CloneVoiceResponse> observer = new CapturingObserver<>();
        service.cloneVoice(
                CloneVoiceRequest.newBuilder().setVoiceName("Test").build(),
                observer);

        assertThat(observer.hasError()).isTrue();
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode())
                .isEqualTo(Status.INVALID_ARGUMENT.getCode());
    }

    @Test
    @DisplayName("cloneVoice: ValidationError from engine → INVALID_ARGUMENT")
    void cloneVoice_validationError_returnsInvalidArgument() throws Exception {
        when(mockEngine.cloneVoice(anyString(), any(), any()))
                .thenThrow(new ValidationError("insufficient audio samples"));

        CloneVoiceRequest request = CloneVoiceRequest.newBuilder()
                .setVoiceName("Test")
                .addAudioSamples(ByteString.copyFrom(new byte[100]))
                .build();
        CapturingObserver<CloneVoiceResponse> observer = new CapturingObserver<>();
        service.cloneVoice(request, observer);

        assertThat(observer.hasError()).isTrue();
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode())
                .isEqualTo(Status.INVALID_ARGUMENT.getCode());
    }

    @Test
    @DisplayName("cloneVoice: engine throws generic exception → INTERNAL")
    void cloneVoice_engineError_returnsInternal() throws Exception {
        when(mockEngine.cloneVoice(anyString(), any(), any()))
                .thenThrow(new RuntimeException("engine crash"));

        CloneVoiceRequest request = CloneVoiceRequest.newBuilder()
                .setVoiceName("Test")
                .addAudioSamples(ByteString.copyFrom(new byte[44100]))
                .build();
        CapturingObserver<CloneVoiceResponse> observer = new CapturingObserver<>();
        service.cloneVoice(request, observer);

        assertThat(observer.hasError()).isTrue();
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode())
                .isEqualTo(Status.INTERNAL.getCode());
    }

    // ─────────────────────────────────────────────────────────────
    // Test infrastructure
    // ─────────────────────────────────────────────────────────────

    static class CapturingObserver<T> implements StreamObserver<T> {
        private T value;
        private Throwable error;

        @Override public void onNext(T value) { this.value = value; }
        @Override public void onError(Throwable t) { this.error = t; }
        @Override public void onCompleted() {}

        T getValue() { return value; }
        boolean hasError() { return error != null; }
        Throwable getError() { return error; }
    }
}

