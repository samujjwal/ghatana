package com.ghatana.tts.grpc;

import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.common.AudioData;
import com.ghatana.media.common.AudioFormat;
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
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("TtsGrpcService [GH-90000]")
class TtsGrpcServiceTest {

    @Mock
    private AudioVideoLibrary mockLibrary;

    @Mock
    private TtsEngine mockEngine;

    private TtsGrpcService service;

    @BeforeEach
    void setUp() { // GH-90000
        service = new TtsGrpcService(mockLibrary, new SimpleMeterRegistry()); // GH-90000
        lenient().when(mockLibrary.getTtsEngine()).thenReturn(mockEngine); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────
    // synthesize (existing, regression coverage) // GH-90000
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("synthesize: valid text → audio returned [GH-90000]")
    void synthesize_validText_returnsAudio() throws Exception { // GH-90000
        AudioData audio = new AudioData(new byte[44100], 22050, 1, 16, // GH-90000
                Duration.ofSeconds(1), AudioFormat.PCM); // GH-90000
        when(mockEngine.synthesize(anyString(), any())).thenReturn(audio); // GH-90000

        SynthesizeRequest request = SynthesizeRequest.newBuilder().setText("Hello world [GH-90000]").build();
        CapturingObserver<SynthesizeResponse> observer = new CapturingObserver<>(); // GH-90000
        service.synthesize(request, observer); // GH-90000

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getAudioData().size()).isGreaterThan(0); // GH-90000
        assertThat(observer.getValue().getSampleRate()).isEqualTo(22050); // GH-90000
    }

    @Test
    @DisplayName("synthesize: empty text → INVALID_ARGUMENT [GH-90000]")
    void synthesize_emptyText_returnsInvalidArgument() { // GH-90000
        SynthesizeRequest request = SynthesizeRequest.newBuilder().setText(" [GH-90000]").build();
        CapturingObserver<SynthesizeResponse> observer = new CapturingObserver<>(); // GH-90000
        service.synthesize(request, observer); // GH-90000

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
                .isEqualTo(Status.INVALID_ARGUMENT.getCode()); // GH-90000
    }

    @Test
    @DisplayName("synthesize: text > 5000 chars → INVALID_ARGUMENT [GH-90000]")
    void synthesize_textTooLong_returnsInvalidArgument() { // GH-90000
        SynthesizeRequest request = SynthesizeRequest.newBuilder() // GH-90000
                .setText("a".repeat(5001)).build(); // GH-90000
        CapturingObserver<SynthesizeResponse> observer = new CapturingObserver<>(); // GH-90000
        service.synthesize(request, observer); // GH-90000

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
                .isEqualTo(Status.INVALID_ARGUMENT.getCode()); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────
    // createProfile (AV-002) // GH-90000
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createProfile: valid displayName → profile created and returned [GH-90000]")
    void createProfile_validName_createsProfile() throws Exception { // GH-90000
        TtsProfile created = new TtsProfile("p1", "Alice", "piper-en", // GH-90000
                ProfileSettings.builder().build(), List.of()); // GH-90000
        when(mockEngine.createProfile(anyString(), anyString(), any())).thenReturn(created); // GH-90000

        CapturingObserver<ProfileResponse> observer = new CapturingObserver<>(); // GH-90000
        service.createProfile( // GH-90000
                CreateProfileRequest.newBuilder().setDisplayName("Alice [GH-90000]").build(),
                observer);

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getDisplayName()).isEqualTo("Alice [GH-90000]");
        assertThat(observer.getValue().getProfileId()).isNotBlank(); // GH-90000
    }

    @Test
    @DisplayName("createProfile: blank displayName → INVALID_ARGUMENT [GH-90000]")
    void createProfile_blankName_returnsInvalidArgument() { // GH-90000
        CapturingObserver<ProfileResponse> observer = new CapturingObserver<>(); // GH-90000
        service.createProfile(CreateProfileRequest.getDefaultInstance(), observer); // GH-90000

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
                .isEqualTo(Status.INVALID_ARGUMENT.getCode()); // GH-90000
    }

    @Test
    @DisplayName("createProfile: engine throws → INTERNAL [GH-90000]")
    void createProfile_engineThrows_returnsInternal() throws Exception { // GH-90000
        when(mockEngine.createProfile(anyString(), anyString(), any())) // GH-90000
                .thenThrow(new RuntimeException("engine failure [GH-90000]"));

        CapturingObserver<ProfileResponse> observer = new CapturingObserver<>(); // GH-90000
        service.createProfile( // GH-90000
                CreateProfileRequest.newBuilder().setDisplayName("Alice [GH-90000]").build(),
                observer);

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
                .isEqualTo(Status.INTERNAL.getCode()); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────
    // getProfile (AV-002) // GH-90000
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getProfile: existing profile → returned [GH-90000]")
    void getProfile_existing_returnsProfile() throws Exception { // GH-90000
        TtsProfile profile = new TtsProfile("p1", "Bob", "piper-en", // GH-90000
                ProfileSettings.builder().build(), List.of("Hello", "World")); // GH-90000
        when(mockEngine.loadProfile("p1 [GH-90000]")).thenReturn(Optional.of(profile));

        CapturingObserver<ProfileResponse> observer = new CapturingObserver<>(); // GH-90000
        service.getProfile(GetProfileRequest.newBuilder().setProfileId("p1 [GH-90000]").build(), observer);

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getProfileId()).isEqualTo("p1 [GH-90000]");
        assertThat(observer.getValue().getDisplayName()).isEqualTo("Bob [GH-90000]");
    }

    @Test
    @DisplayName("getProfile: unknown profileId → NOT_FOUND [GH-90000]")
    void getProfile_unknown_returnsNotFound() throws Exception { // GH-90000
        when(mockEngine.loadProfile("ghost [GH-90000]")).thenReturn(Optional.empty());

        CapturingObserver<ProfileResponse> observer = new CapturingObserver<>(); // GH-90000
        service.getProfile(GetProfileRequest.newBuilder().setProfileId("ghost [GH-90000]").build(), observer);

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
                .isEqualTo(Status.NOT_FOUND.getCode()); // GH-90000
    }

    @Test
    @DisplayName("getProfile: blank profileId → INVALID_ARGUMENT [GH-90000]")
    void getProfile_blank_returnsInvalidArgument() { // GH-90000
        CapturingObserver<ProfileResponse> observer = new CapturingObserver<>(); // GH-90000
        service.getProfile(GetProfileRequest.getDefaultInstance(), observer); // GH-90000

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
                .isEqualTo(Status.INVALID_ARGUMENT.getCode()); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────
    // updateProfile (AV-002) // GH-90000
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateProfile: existing profile → updated and returned [GH-90000]")
    void updateProfile_existing_updatesProfile() throws Exception { // GH-90000
        TtsProfile existing = new TtsProfile("p1", "Carol", "piper-en", // GH-90000
                ProfileSettings.builder().build(), List.of()); // GH-90000
        when(mockEngine.loadProfile("p1 [GH-90000]")).thenReturn(Optional.of(existing));
        doNothing().when(mockEngine).saveProfile(any()); // GH-90000

        UpdateProfileRequest request = UpdateProfileRequest.newBuilder() // GH-90000
                .setProfileId("p1 [GH-90000]")
                .setSettings(com.ghatana.tts.core.grpc.proto.ProfileSettings.newBuilder() // GH-90000
                        .setDefaultVoiceId("piper-fr [GH-90000]").build())
                .build(); // GH-90000
        CapturingObserver<ProfileResponse> observer = new CapturingObserver<>(); // GH-90000
        service.updateProfile(request, observer); // GH-90000

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getProfileId()).isEqualTo("p1 [GH-90000]");
        verify(mockEngine).saveProfile(any()); // GH-90000
    }

    @Test
    @DisplayName("updateProfile: unknown profileId → NOT_FOUND [GH-90000]")
    void updateProfile_unknown_returnsNotFound() throws Exception { // GH-90000
        when(mockEngine.loadProfile("ghost [GH-90000]")).thenReturn(Optional.empty());

        CapturingObserver<ProfileResponse> observer = new CapturingObserver<>(); // GH-90000
        service.updateProfile( // GH-90000
                UpdateProfileRequest.newBuilder().setProfileId("ghost [GH-90000]").build(),
                observer);

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
                .isEqualTo(Status.NOT_FOUND.getCode()); // GH-90000
    }

    @Test
    @DisplayName("updateProfile: blank profileId → INVALID_ARGUMENT [GH-90000]")
    void updateProfile_blank_returnsInvalidArgument() { // GH-90000
        CapturingObserver<ProfileResponse> observer = new CapturingObserver<>(); // GH-90000
        service.updateProfile(UpdateProfileRequest.getDefaultInstance(), observer); // GH-90000

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
                .isEqualTo(Status.INVALID_ARGUMENT.getCode()); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────
    // cloneVoice (AV-002.1) // GH-90000
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cloneVoice: valid name + samples → cloned voice returned [GH-90000]")
    void cloneVoice_validRequest_returnsClonedVoice() throws Exception { // GH-90000
        VoiceInfo cloned = new VoiceInfo("cloned-1", "MyVoice", "Custom cloned voice", // GH-90000
                Locale.ENGLISH, VoiceInfo.Gender.NEUTRAL, 22050, true, 5_000_000L);
        when(mockEngine.cloneVoice(anyString(), any(), any())).thenReturn(cloned); // GH-90000

        CloneVoiceRequest request = CloneVoiceRequest.newBuilder() // GH-90000
                .setVoiceName("MyVoice [GH-90000]")
                .addAudioSamples(ByteString.copyFrom(new byte[44100])) // GH-90000
                .build(); // GH-90000
        CapturingObserver<CloneVoiceResponse> observer = new CapturingObserver<>(); // GH-90000
        service.cloneVoice(request, observer); // GH-90000

        assertThat(observer.hasError()).isFalse(); // GH-90000
        CloneVoiceResponse response = observer.getValue(); // GH-90000
        assertThat(response.getSuccess()).isTrue(); // GH-90000
        assertThat(response.getVoiceId()).isEqualTo("cloned-1 [GH-90000]");
        assertThat(response.getVoice().getIsCloned()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("cloneVoice: blank voiceName → INVALID_ARGUMENT [GH-90000]")
    void cloneVoice_blankName_returnsInvalidArgument() { // GH-90000
        CapturingObserver<CloneVoiceResponse> observer = new CapturingObserver<>(); // GH-90000
        service.cloneVoice( // GH-90000
                CloneVoiceRequest.newBuilder().setVoiceName(" [GH-90000]").build(),
                observer);

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
                .isEqualTo(Status.INVALID_ARGUMENT.getCode()); // GH-90000
    }

    @Test
    @DisplayName("cloneVoice: no audio samples → INVALID_ARGUMENT [GH-90000]")
    void cloneVoice_noSamples_returnsInvalidArgument() { // GH-90000
        CapturingObserver<CloneVoiceResponse> observer = new CapturingObserver<>(); // GH-90000
        service.cloneVoice( // GH-90000
                CloneVoiceRequest.newBuilder().setVoiceName("Test [GH-90000]").build(),
                observer);

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
                .isEqualTo(Status.INVALID_ARGUMENT.getCode()); // GH-90000
    }

    @Test
    @DisplayName("cloneVoice: ValidationError from engine → INVALID_ARGUMENT [GH-90000]")
    void cloneVoice_validationError_returnsInvalidArgument() throws Exception { // GH-90000
        when(mockEngine.cloneVoice(anyString(), any(), any())) // GH-90000
                .thenThrow(new ValidationError("insufficient audio samples [GH-90000]"));

        CloneVoiceRequest request = CloneVoiceRequest.newBuilder() // GH-90000
                .setVoiceName("Test [GH-90000]")
                .addAudioSamples(ByteString.copyFrom(new byte[100])) // GH-90000
                .build(); // GH-90000
        CapturingObserver<CloneVoiceResponse> observer = new CapturingObserver<>(); // GH-90000
        service.cloneVoice(request, observer); // GH-90000

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
                .isEqualTo(Status.INVALID_ARGUMENT.getCode()); // GH-90000
    }

    @Test
    @DisplayName("cloneVoice: engine throws generic exception → INTERNAL [GH-90000]")
    void cloneVoice_engineError_returnsInternal() throws Exception { // GH-90000
        when(mockEngine.cloneVoice(anyString(), any(), any())) // GH-90000
                .thenThrow(new RuntimeException("engine crash [GH-90000]"));

        CloneVoiceRequest request = CloneVoiceRequest.newBuilder() // GH-90000
                .setVoiceName("Test [GH-90000]")
                .addAudioSamples(ByteString.copyFrom(new byte[44100])) // GH-90000
                .build(); // GH-90000
        CapturingObserver<CloneVoiceResponse> observer = new CapturingObserver<>(); // GH-90000
        service.cloneVoice(request, observer); // GH-90000

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
                .isEqualTo(Status.INTERNAL.getCode()); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────
    // Test infrastructure
    // ─────────────────────────────────────────────────────────────

    static class CapturingObserver<T> implements StreamObserver<T> {
        private T value;
        private Throwable error;

        @Override public void onNext(T value) { this.value = value; } // GH-90000
        @Override public void onError(Throwable t) { this.error = t; } // GH-90000
        @Override public void onCompleted() {} // GH-90000

        T getValue() { return value; } // GH-90000
        boolean hasError() { return error != null; } // GH-90000
        Throwable getError() { return error; } // GH-90000
    }
}
