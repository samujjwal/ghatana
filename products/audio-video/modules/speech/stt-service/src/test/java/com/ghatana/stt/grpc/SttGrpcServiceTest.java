package com.ghatana.stt.grpc;

import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.common.EngineMetrics;
import com.ghatana.media.common.EngineStatus;
import com.ghatana.media.common.InferenceError;
import com.ghatana.media.common.ModelLoadingError;
import com.ghatana.media.common.ValidationError;
import com.ghatana.media.stt.api.ModelInfo;
import com.ghatana.media.stt.api.SttEngine;
import com.ghatana.media.stt.api.StreamingSession;
import com.ghatana.media.stt.api.TranscriptionOptions;
import com.ghatana.media.stt.api.TranscriptionResult;
import com.ghatana.media.stt.api.UserProfile;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SttGrpcService}.
 *
 * <p>Mocks {@link AudioVideoLibrary} (final class — supported by Mockito 5 inline mock-maker) // GH-90000
 * and {@link SttEngine} to avoid ONNX / native-library dependencies.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the STT gRPC service layer
 * @doc.layer product
 * @doc.pattern TestCase
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("SttGrpcService [GH-90000]")
class SttGrpcServiceTest {

    @Mock
    private AudioVideoLibrary mockLibrary;

    @Mock
    private SttEngine mockEngine;

    private SttGrpcService service;

    @BeforeEach
    void setUp() { // GH-90000
        service = new SttGrpcService(mockLibrary, new SimpleMeterRegistry()); // GH-90000
        // Lenient: getSttEngine is not needed in every test
        lenient().when(mockLibrary.getSttEngine()).thenReturn(mockEngine); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────
    // transcribe
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("transcribe: valid audio → text returned in response [GH-90000]")
    void transcribe_validAudio_returnsTranscription() throws Exception { // GH-90000
        byte[] audioBytes = new byte[]{0x01, 0x02, 0x03, 0x04};
        TranscriptionResult result = new TranscriptionResult( // GH-90000
            "hello world", 0.95, Collections.emptyList(), Collections.emptyList(), // GH-90000
            Duration.ofMillis(120), "en", "whisper-base" // GH-90000
        );
        when(mockEngine.transcribe(any(), any(TranscriptionOptions.class))).thenReturn(result); // GH-90000

        TranscribeRequest request = TranscribeRequest.newBuilder() // GH-90000
            .setAudioData(ByteString.copyFrom(audioBytes)) // GH-90000
            .setSampleRate(16000) // GH-90000
            .build(); // GH-90000
        CapturingObserver<TranscribeResponse> observer = new CapturingObserver<>(); // GH-90000

        service.transcribe(request, observer); // GH-90000

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getText()).isEqualTo("hello world [GH-90000]");
        assertThat(observer.getValue().getConfidence()).isEqualTo(0.95f); // GH-90000
        assertThat(observer.getValue().getProcessingTimeMs()).isEqualTo(120L); // GH-90000
        assertThat(observer.getValue().getModelUsed()).isEqualTo("whisper-base [GH-90000]");
    }

    @Test
    @DisplayName("transcribe: empty audio bytes → INVALID_ARGUMENT gRPC error [GH-90000]")
    void transcribe_emptyAudio_returnsInvalidArgument() { // GH-90000
        TranscribeRequest request = TranscribeRequest.newBuilder() // GH-90000
            .setAudioData(ByteString.EMPTY) // GH-90000
            .setSampleRate(16000) // GH-90000
            .build(); // GH-90000
        CapturingObserver<TranscribeResponse> observer = new CapturingObserver<>(); // GH-90000

        service.transcribe(request, observer); // GH-90000

        assertThat(observer.hasError()).isTrue(); // GH-90000
        StatusRuntimeException ex = (StatusRuntimeException) observer.getError(); // GH-90000
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INVALID_ARGUMENT.getCode()); // GH-90000
    }

    @Test
    @DisplayName("transcribe: ValidationError from engine → INVALID_ARGUMENT gRPC error [GH-90000]")
    void transcribe_validationError_returnsInvalidArgument() throws Exception { // GH-90000
        when(mockEngine.transcribe(any(), any(TranscriptionOptions.class))) // GH-90000
            .thenThrow(new ValidationError("bad audio format [GH-90000]"));

        TranscribeRequest request = TranscribeRequest.newBuilder() // GH-90000
            .setAudioData(ByteString.copyFrom(new byte[]{0x01})) // GH-90000
            .setSampleRate(16000) // GH-90000
            .build(); // GH-90000
        CapturingObserver<TranscribeResponse> observer = new CapturingObserver<>(); // GH-90000

        service.transcribe(request, observer); // GH-90000

        assertThat(observer.hasError()).isTrue(); // GH-90000
        StatusRuntimeException ex = (StatusRuntimeException) observer.getError(); // GH-90000
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INVALID_ARGUMENT.getCode()); // GH-90000
        assertThat(ex.getStatus().getDescription()).contains("bad audio format [GH-90000]");
    }

    @Test
    @DisplayName("transcribe: non-retryable InferenceError → INTERNAL gRPC error [GH-90000]")
    void transcribe_nonRetryableInferenceError_returnsInternal() throws Exception { // GH-90000
        when(mockEngine.transcribe(any(), any(TranscriptionOptions.class))) // GH-90000
            .thenThrow(new InferenceError("model crash", null, false)); // GH-90000

        TranscribeRequest request = TranscribeRequest.newBuilder() // GH-90000
            .setAudioData(ByteString.copyFrom(new byte[]{0x01})) // GH-90000
            .setSampleRate(16000) // GH-90000
            .build(); // GH-90000
        CapturingObserver<TranscribeResponse> observer = new CapturingObserver<>(); // GH-90000

        service.transcribe(request, observer); // GH-90000

        assertThat(observer.hasError()).isTrue(); // GH-90000
        StatusRuntimeException ex = (StatusRuntimeException) observer.getError(); // GH-90000
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.INTERNAL.getCode()); // GH-90000
    }

    @Test
    @DisplayName("transcribe: retryable InferenceError → UNAVAILABLE gRPC error [GH-90000]")
    void transcribe_retryableInferenceError_returnsUnavailable() throws Exception { // GH-90000
        when(mockEngine.transcribe(any(), any(TranscriptionOptions.class))) // GH-90000
            .thenThrow(new InferenceError("backend overloaded", null, true)); // GH-90000

        TranscribeRequest request = TranscribeRequest.newBuilder() // GH-90000
            .setAudioData(ByteString.copyFrom(new byte[]{0x01})) // GH-90000
            .setSampleRate(16000) // GH-90000
            .build(); // GH-90000
        CapturingObserver<TranscribeResponse> observer = new CapturingObserver<>(); // GH-90000

        service.transcribe(request, observer); // GH-90000

        assertThat(observer.hasError()).isTrue(); // GH-90000
        StatusRuntimeException ex = (StatusRuntimeException) observer.getError(); // GH-90000
        assertThat(ex.getStatus().getCode()).isEqualTo(Status.UNAVAILABLE.getCode()); // GH-90000
    }

    @Test
    @DisplayName("transcribe: default sample rate applied when not set in request [GH-90000]")
    void transcribe_defaultSampleRate_noNullPointer() throws Exception { // GH-90000
        TranscriptionResult result = new TranscriptionResult( // GH-90000
            "test", 1.0, Collections.emptyList(), Collections.emptyList(), // GH-90000
            Duration.ofMillis(10), "en", "whisper-base" // GH-90000
        );
        when(mockEngine.transcribe(any(), any(TranscriptionOptions.class))).thenReturn(result); // GH-90000

        TranscribeRequest request = TranscribeRequest.newBuilder() // GH-90000
            .setAudioData(ByteString.copyFrom(new byte[]{0x01, 0x02})) // GH-90000
            .build(); // GH-90000
        CapturingObserver<TranscribeResponse> observer = new CapturingObserver<>(); // GH-90000

        service.transcribe(request, observer); // GH-90000

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getText()).isEqualTo("test [GH-90000]");
    }

    // ─────────────────────────────────────────────────────────────
    // loadModel (AV-001.1) // GH-90000
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("loadModel: valid modelId → success response with timing [GH-90000]")
    void loadModel_validId_succeeds() throws Exception { // GH-90000
        doNothing().when(mockEngine).loadModel("whisper-base [GH-90000]");
        when(mockEngine.getMetrics()).thenReturn(new EngineMetrics(1L, 0L, 0.0, 0L, 150_000_000L)); // GH-90000

        CapturingObserver<LoadModelResponse> observer = new CapturingObserver<>(); // GH-90000
        service.loadModel( // GH-90000
            LoadModelRequest.newBuilder().setModelId("whisper-base [GH-90000]").build(),
            observer);

        assertThat(observer.hasError()).isFalse(); // GH-90000
        LoadModelResponse response = observer.getValue(); // GH-90000
        assertThat(response.getSuccess()).isTrue(); // GH-90000
        assertThat(response.getModelId()).isEqualTo("whisper-base [GH-90000]");
        assertThat(response.getLoadTimeMs()).isGreaterThanOrEqualTo(0L); // GH-90000
    }

    @Test
    @DisplayName("loadModel: blank modelId → INVALID_ARGUMENT [GH-90000]")
    void loadModel_blankId_returnsInvalidArgument() { // GH-90000
        CapturingObserver<LoadModelResponse> observer = new CapturingObserver<>(); // GH-90000
        service.loadModel(LoadModelRequest.newBuilder().setModelId(" [GH-90000]").build(), observer);

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
            .isEqualTo(Status.INVALID_ARGUMENT.getCode()); // GH-90000
    }

    @Test
    @DisplayName("loadModel: ModelLoadingError from engine → NOT_FOUND [GH-90000]")
    void loadModel_modelNotFound_returnsNotFound() throws Exception { // GH-90000
        doThrow(new ModelLoadingError("Model not found: whisper-xl", null)) // GH-90000
            .when(mockEngine).loadModel("whisper-xl [GH-90000]");

        CapturingObserver<LoadModelResponse> observer = new CapturingObserver<>(); // GH-90000
        service.loadModel(LoadModelRequest.newBuilder().setModelId("whisper-xl [GH-90000]").build(), observer);

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
            .isEqualTo(Status.NOT_FOUND.getCode()); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────
    // unloadModel (AV-001.2) // GH-90000
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("unloadModel: model exists → success response [GH-90000]")
    void unloadModel_modelExists_succeeds() throws Exception { // GH-90000
        ModelInfo info = new ModelInfo("whisper-base", "Whisper Base", "1.0", // GH-90000
            new Locale[]{Locale.ENGLISH}, 150_000_000L, false);
        when(mockEngine.getAvailableModels()).thenReturn(List.of(info)); // GH-90000
        when(mockEngine.getMetrics()).thenReturn(new EngineMetrics(1L, 0L, 0.0, 0L, 150_000_000L)); // GH-90000

        CapturingObserver<UnloadModelResponse> observer = new CapturingObserver<>(); // GH-90000
        service.unloadModel(UnloadModelRequest.newBuilder().setModelId("whisper-base [GH-90000]").build(), observer);

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getSuccess()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("unloadModel: model not in list → NOT_FOUND [GH-90000]")
    void unloadModel_unknownModel_returnsNotFound() throws Exception { // GH-90000
        when(mockEngine.getAvailableModels()).thenReturn(List.of()); // GH-90000

        CapturingObserver<UnloadModelResponse> observer = new CapturingObserver<>(); // GH-90000
        service.unloadModel(UnloadModelRequest.newBuilder().setModelId("ghost [GH-90000]").build(), observer);

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
            .isEqualTo(Status.NOT_FOUND.getCode()); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────
    // listModels (AV-001.3) // GH-90000
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listModels: returns all registered models [GH-90000]")
    void listModels_returnsAll() throws Exception { // GH-90000
        ModelInfo m1 = new ModelInfo("whisper-base", "Whisper Base", "1.0", // GH-90000
            new Locale[]{Locale.ENGLISH}, 100_000_000L, false);
        ModelInfo m2 = new ModelInfo("whisper-large", "Whisper Large", "2.0", // GH-90000
            new Locale[]{Locale.ENGLISH, Locale.FRENCH}, 200_000_000L, true);
        when(mockEngine.getAvailableModels()).thenReturn(List.of(m1, m2)); // GH-90000
        when(mockEngine.getActiveModel()).thenReturn(m1); // GH-90000

        CapturingObserver<ListModelsResponse> observer = new CapturingObserver<>(); // GH-90000
        service.listModels(ListModelsRequest.getDefaultInstance(), observer); // GH-90000

        assertThat(observer.hasError()).isFalse(); // GH-90000
        ListModelsResponse response = observer.getValue(); // GH-90000
        assertThat(response.getTotalCount()).isEqualTo(2); // GH-90000
        assertThat(response.getLoadedCount()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("listModels: loadedOnly=true returns only active model [GH-90000]")
    void listModels_loadedOnlyFilter() throws Exception { // GH-90000
        ModelInfo m1 = new ModelInfo("whisper-base", "Whisper Base", "1.0", // GH-90000
            new Locale[]{Locale.ENGLISH}, 100_000_000L, false);
        ModelInfo m2 = new ModelInfo("whisper-large", "Whisper Large", "2.0", // GH-90000
            new Locale[]{Locale.ENGLISH}, 200_000_000L, true);
        when(mockEngine.getAvailableModels()).thenReturn(List.of(m1, m2)); // GH-90000
        when(mockEngine.getActiveModel()).thenReturn(m1); // GH-90000

        CapturingObserver<ListModelsResponse> observer = new CapturingObserver<>(); // GH-90000
        service.listModels(ListModelsRequest.newBuilder().setIncludeLoadedOnly(true).build(), observer); // GH-90000

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getTotalCount()).isEqualTo(1); // GH-90000
        assertThat(observer.getValue().getModels(0).getModelId()).isEqualTo("whisper-base [GH-90000]");
    }

    // ─────────────────────────────────────────────────────────────
    // adaptModel (AV-001.4) // GH-90000
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("adaptModel: valid profile + interactions → success with vocab count [GH-90000]")
    void adaptModel_validRequest_succeeds() throws Exception { // GH-90000
        UserProfile profile = new UserProfile("p1", "Alice", Locale.ENGLISH, // GH-90000
            List.of("existing [GH-90000]"), new byte[0]);
        when(mockEngine.loadProfile("p1 [GH-90000]")).thenReturn(Optional.of(profile));
        doNothing().when(mockEngine).saveProfile(any()); // GH-90000

        AdaptRequest request = AdaptRequest.newBuilder() // GH-90000
            .setProfileId("p1 [GH-90000]")
            .addInteractions(Interaction.newBuilder() // GH-90000
                .setCorrectedTranscript("ghatana platform agent [GH-90000]")
                .build()) // GH-90000
            .build(); // GH-90000
        CapturingObserver<AdaptResponse> observer = new CapturingObserver<>(); // GH-90000
        service.adaptModel(request, observer); // GH-90000

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getSuccess()).isTrue(); // GH-90000
        assertThat(observer.getValue().getStats().getInteractionsProcessed()).isEqualTo(1); // GH-90000
        assertThat(observer.getValue().getStats().getNewVocabularyTerms()).isGreaterThan(0); // GH-90000
    }

    @Test
    @DisplayName("adaptModel: blank profileId → INVALID_ARGUMENT [GH-90000]")
    void adaptModel_blankProfileId_returnsInvalidArgument() { // GH-90000
        CapturingObserver<AdaptResponse> observer = new CapturingObserver<>(); // GH-90000
        service.adaptModel(AdaptRequest.getDefaultInstance(), observer); // GH-90000

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
            .isEqualTo(Status.INVALID_ARGUMENT.getCode()); // GH-90000
    }

    @Test
    @DisplayName("adaptModel: profile not found → NOT_FOUND [GH-90000]")
    void adaptModel_profileNotFound_returnsNotFound() throws Exception { // GH-90000
        when(mockEngine.loadProfile("ghost [GH-90000]")).thenReturn(Optional.empty());

        AdaptRequest request = AdaptRequest.newBuilder() // GH-90000
            .setProfileId("ghost [GH-90000]")
            .addInteractions(Interaction.newBuilder().setCorrectedTranscript("word [GH-90000]").build())
            .build(); // GH-90000
        CapturingObserver<AdaptResponse> observer = new CapturingObserver<>(); // GH-90000
        service.adaptModel(request, observer); // GH-90000

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
            .isEqualTo(Status.NOT_FOUND.getCode()); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────
    // createProfile (AV-001.5) // GH-90000
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createProfile: valid name → profile created and returned [GH-90000]")
    void createProfile_validName_returnsProfile() throws Exception { // GH-90000
        UserProfile created = new UserProfile("uuid-1", "Alice", Locale.ENGLISH, // GH-90000
            List.of(), new byte[0]); // GH-90000
        when(mockEngine.createProfile(anyString(), any())).thenReturn(created); // GH-90000

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

    // ─────────────────────────────────────────────────────────────
    // getProfile (AV-001.6) // GH-90000
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getProfile: existing profile → profile returned [GH-90000]")
    void getProfile_existing_returnsProfile() throws Exception { // GH-90000
        UserProfile profile = new UserProfile("p1", "Bob", Locale.ENGLISH, List.of("word [GH-90000]"), new byte[0]);
        when(mockEngine.loadProfile("p1 [GH-90000]")).thenReturn(Optional.of(profile));

        CapturingObserver<ProfileResponse> observer = new CapturingObserver<>(); // GH-90000
        service.getProfile(GetProfileRequest.newBuilder().setProfileId("p1 [GH-90000]").build(), observer);

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getProfileId()).isEqualTo("p1 [GH-90000]");
        assertThat(observer.getValue().getDisplayName()).isEqualTo("Bob [GH-90000]");
        assertThat(observer.getValue().getStats().getVocabularySize()).isEqualTo(1); // GH-90000
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
    void getProfile_blankId_returnsInvalidArgument() { // GH-90000
        CapturingObserver<ProfileResponse> observer = new CapturingObserver<>(); // GH-90000
        service.getProfile(GetProfileRequest.getDefaultInstance(), observer); // GH-90000

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
            .isEqualTo(Status.INVALID_ARGUMENT.getCode()); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────
    // updateProfile (AV-001.7) // GH-90000
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateProfile: existing profile → updated and returned [GH-90000]")
    void updateProfile_existing_updatesProfile() throws Exception { // GH-90000
        UserProfile existing = new UserProfile("p1", "Carol", Locale.ENGLISH, List.of(), new byte[0]); // GH-90000
        when(mockEngine.loadProfile("p1 [GH-90000]")).thenReturn(Optional.of(existing));
        doNothing().when(mockEngine).saveProfile(any()); // GH-90000

        UpdateProfileRequest request = UpdateProfileRequest.newBuilder() // GH-90000
            .setProfileId("p1 [GH-90000]")
            .setSettings(ProfileSettings.newBuilder().setPreferredLanguage("fr [GH-90000]").build())
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
        service.updateProfile(UpdateProfileRequest.newBuilder().setProfileId("ghost [GH-90000]").build(), observer);

        assertThat(observer.hasError()).isTrue(); // GH-90000
        assertThat(((StatusRuntimeException) observer.getError()).getStatus().getCode()) // GH-90000
            .isEqualTo(Status.NOT_FOUND.getCode()); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────
    // getStatus
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getStatus: engine READY → active model name returned [GH-90000]")
    void getStatus_engineReady_returnsModel() throws Exception { // GH-90000
        EngineStatus engineStatus = new EngineStatus( // GH-90000
            EngineStatus.State.READY, "whisper-base", "1.0", 5000L, null
        );
        when(mockEngine.getStatus()).thenReturn(engineStatus); // GH-90000

        CapturingObserver<StatusResponse> observer = new CapturingObserver<>(); // GH-90000
        service.getStatus(StatusRequest.getDefaultInstance(), observer); // GH-90000

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getActiveModel()).isEqualTo("whisper-base [GH-90000]");
    }

    // ─────────────────────────────────────────────────────────────
    // submitCorrection
    // ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("submitCorrection: always accepted [GH-90000]")
    void submitCorrection_alwaysAccepted() { // GH-90000
        CapturingObserver<CorrectionResponse> observer = new CapturingObserver<>(); // GH-90000
        service.submitCorrection(CorrectionRequest.getDefaultInstance(), observer); // GH-90000

        assertThat(observer.hasError()).isFalse(); // GH-90000
        assertThat(observer.getValue().getAccepted()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("streamAudio: completion ends stream before closing session [GH-90000]")
    void streamAudio_completionEndsStreamBeforeClosingSession() throws Exception { // GH-90000
        StreamingSession streamingSession = mock(StreamingSession.class); // GH-90000
        when(mockEngine.createStreamingSession()).thenReturn(streamingSession); // GH-90000

        AtomicBoolean completed = new AtomicBoolean(false); // GH-90000
        StreamObserver<Transcription> responseObserver = new StreamObserver<>() { // GH-90000
            @Override
            public void onNext(Transcription value) { // GH-90000
            }

            @Override
            public void onError(Throwable t) { // GH-90000
            }

            @Override
            public void onCompleted() { // GH-90000
                completed.set(true); // GH-90000
            }
        };

        StreamObserver<AudioChunk> requestObserver = service.streamTranscribe(responseObserver); // GH-90000

        requestObserver.onCompleted(); // GH-90000

        assertThat(completed.get()).isTrue(); // GH-90000
        InOrder inOrder = inOrder(streamingSession); // GH-90000
        inOrder.verify(streamingSession).endStream(); // GH-90000
        inOrder.verify(streamingSession).close(); // GH-90000
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
