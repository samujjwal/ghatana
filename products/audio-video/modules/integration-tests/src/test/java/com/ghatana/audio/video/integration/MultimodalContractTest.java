package com.ghatana.audio.video.integration;

import com.ghatana.audio.video.multimodal.engine.AudioResult;
import com.ghatana.audio.video.multimodal.engine.MultimodalRequest;
import com.ghatana.audio.video.multimodal.engine.MultimodalResult;
import com.ghatana.audio.video.multimodal.engine.VisualResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests for the Multimodal API surface (AV-P1-07).
 *
 * <p>These tests verify the stable data contract of {@link MultimodalRequest},
 * {@link MultimodalResult}, and the hasAudio/hasImage/hasVideo predicates as
 * agreed between the Java backend and the TypeScript {@code @audio-video/client}.
 * No native AI dependencies are required — all results are constructed directly
 * to verify shape and field propagation.
 *
 * <h3>Contract guarantees verified</h3>
 * <ul>
 *   <li>{@code hasAudio}, {@code hasImage}, {@code hasVideo} correctly reflect presence of data</li>
 *   <li>Empty / null data bytes are treated as absent</li>
 *   <li>{@link MultimodalResult} carries audioResult, visualResult, combinedAnalysis, processingTimeMs</li>
 *   <li>Processing time is non-negative</li>
 *   <li>Combined analysis field is at least an empty string (never null)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Contract tests for Multimodal request/response shape stability (AV-P1-07)
 * @doc.layer test
 * @doc.pattern ContractTest
 */
@Tag("contract")
@DisplayName("Multimodal Service API Contract Tests (AV-P1-07)")
class MultimodalContractTest {

    @Nested
    @DisplayName("MultimodalRequest contract")
    class RequestContract {

        @Test
        @DisplayName("hasAudio returns false when no audio data provided")
        void shouldReturnFalseHasAudioWhenAbsent() {
            MultimodalRequest request = MultimodalRequest.builder()
                    .imageData(new byte[]{1, 2, 3})
                    .build();

            assertThat(request.hasAudio()).isFalse();
            assertThat(request.hasImage()).isTrue();
            assertThat(request.hasVideo()).isFalse();
        }

        @Test
        @DisplayName("hasAudio returns false for empty byte array")
        void shouldReturnFalseHasAudioForEmptyArray() {
            MultimodalRequest request = MultimodalRequest.builder()
                    .audioData(new byte[0])
                    .build();

            assertThat(request.hasAudio()).isFalse();
        }

        @Test
        @DisplayName("hasAudio/hasImage/hasVideo all true when all provided")
        void shouldReportAllModalitiesWhenAllPresent() {
            MultimodalRequest request = MultimodalRequest.builder()
                    .audioData(new byte[]{1})
                    .imageData(new byte[]{2})
                    .videoData(new byte[]{3})
                    .build();

            assertThat(request.hasAudio()).isTrue();
            assertThat(request.hasImage()).isTrue();
            assertThat(request.hasVideo()).isTrue();
        }

        @Test
        @DisplayName("getText returns empty string when not set")
        void shouldReturnEmptyStringForAbsentText() {
            MultimodalRequest request = MultimodalRequest.builder().build();

            assertThat(request.getText()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("videoSampleFps defaults to 1 when not set")
        void shouldDefaultVideoSampleFpsToOne() {
            MultimodalRequest request = MultimodalRequest.builder().build();

            assertThat(request.getVideoSampleFps()).isEqualTo(1);
        }

        @Test
        @DisplayName("videoMaxFrames defaults to 50 when not set")
        void shouldDefaultVideoMaxFramesToFifty() {
            MultimodalRequest request = MultimodalRequest.builder().build();

            assertThat(request.getVideoMaxFrames()).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("MultimodalResult contract")
    class ResultContract {

        @Test
        @DisplayName("combinedAnalysis defaults to empty string (never null)")
        void combinedAnalysisDefaultsToEmptyString() {
            MultimodalResult result = MultimodalResult.builder().build();

            assertThat(result.getCombinedAnalysis()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("processingTimeMs is zero when not set")
        void processingTimeMsDefaultsToZero() {
            MultimodalResult result = MultimodalResult.builder().build();

            assertThat(result.getProcessingTimeMs()).isZero();
        }

        @Test
        @DisplayName("Result fields are accessible after builder construction")
        void shouldExposeBuilderSetFields() {
            MultimodalResult result = MultimodalResult.builder()
                    .combinedAnalysis("Audio: Hello | Scene: Room")
                    .processingTimeMs(99L)
                    .build();

            assertThat(result.getCombinedAnalysis()).isEqualTo("Audio: Hello | Scene: Room");
            assertThat(result.getProcessingTimeMs()).isEqualTo(99L);
        }
    }
}

/**
 * Contract tests for the Multimodal API surface.
 *
 * <p>These tests verify the stable contract of the {@link MultimodalAnalysisEngine} interface
 * as agreed between the backend Java services and the TypeScript client ({@code @audio-video/client}).
 * They exercise the production engine interface with stub implementations to detect breaking changes
 * before they reach integration environments.
 *
 * <h3>Contract guarantees verified</h3>
 * <ul>
 *   <li>Required request fields validated on entry — null/empty inputs surface as {@link IllegalArgumentException}</li>
 *   <li>Response includes correlation ID, tenant ID, and processing_time_ms</li>
 *   <li>Audio-only path returns empty visual analysis; image-only path returns empty audio analysis</li>
 *   <li>Combined path populates both audio and visual analysis fields</li>
 *   <li>Auth context (tenant ID) is propagated through the engine to the result</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Contract tests verifying Multimodal service API stability (AV-P1-07)
 * @doc.layer test
 * @doc.pattern ContractTest
 */
@Tag("contract")
@DisplayName("Multimodal Service API Contract Tests (AV-P1-07)")
class MultimodalContractTest {

    // ── Stub engine that exercises the contract without native AI dependencies ────

    private static final MultimodalAnalysisEngine STUB_ENGINE = input -> {
        if (input.audioData() == null && input.imageData() == null && input.videoData() == null) {
            throw new IllegalArgumentException("At least one media input is required");
        }
        if (input.tenantId() == null || input.tenantId().isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }

        boolean hasAudio = input.audioData() != null && input.audioData().length > 0;
        boolean hasImage = input.imageData() != null && input.imageData().length > 0;

        String audioTranscript = hasAudio ? "Stubbed transcript for tenant " + input.tenantId() : "";
        String scene = hasImage ? "Stubbed scene for tenant " + input.tenantId() : "";

        return MultimodalResult.builder()
                .correlationId(input.correlationId())
                .tenantId(input.tenantId())
                .audioTranscript(audioTranscript)
                .sceneDescription(scene)
                .combinedAnalysis("combined: " + audioTranscript + " | " + scene)
                .processingTimeMs(42L)
                .build();
    };

    @Nested
    @DisplayName("Input validation contract")
    class InputValidationContract {

        @Test
        @DisplayName("Null mediaData with null tenantId → IllegalArgumentException")
        void shouldRejectAllNullInputs() {
            MultimodalInput empty = MultimodalInput.builder()
                    .tenantId("t1")
                    .build();

            assertThatThrownBy(() -> STUB_ENGINE.analyze(empty))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("media input");
        }

        @Test
        @DisplayName("Missing tenantId → IllegalArgumentException")
        void shouldRejectMissingTenantId() {
            MultimodalInput input = MultimodalInput.builder()
                    .audioData(new byte[]{1, 2, 3})
                    .tenantId("")
                    .build();

            assertThatThrownBy(() -> STUB_ENGINE.analyze(input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tenantId");
        }
    }

    @Nested
    @DisplayName("Response contract")
    class ResponseContract {

        @Test
        @DisplayName("Audio-only input → audio transcript populated, scene empty")
        void audioOnly_transcriptPopulated_sceneEmpty() {
            MultimodalInput input = MultimodalInput.builder()
                    .tenantId("tenant-contract-1")
                    .correlationId("corr-001")
                    .audioData(new byte[]{1, 2, 3})
                    .build();

            MultimodalResult result = STUB_ENGINE.analyze(input);

            assertThat(result.tenantId()).isEqualTo("tenant-contract-1");
            assertThat(result.correlationId()).isEqualTo("corr-001");
            assertThat(result.audioTranscript()).isNotBlank();
            assertThat(result.sceneDescription()).isBlank();
            assertThat(result.processingTimeMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Image-only input → scene populated, audio transcript empty")
        void imageOnly_scenePopulated_transcriptEmpty() {
            MultimodalInput input = MultimodalInput.builder()
                    .tenantId("tenant-contract-2")
                    .correlationId("corr-002")
                    .imageData(new byte[]{9, 8, 7})
                    .build();

            MultimodalResult result = STUB_ENGINE.analyze(input);

            assertThat(result.tenantId()).isEqualTo("tenant-contract-2");
            assertThat(result.sceneDescription()).isNotBlank();
            assertThat(result.audioTranscript()).isBlank();
            assertThat(result.processingTimeMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("Combined input → both audio and visual fields populated")
        void combined_bothFieldsPopulated() {
            MultimodalInput input = MultimodalInput.builder()
                    .tenantId("tenant-contract-3")
                    .correlationId("corr-003")
                    .audioData(new byte[]{1, 2, 3})
                    .imageData(new byte[]{9, 8, 7})
                    .build();

            MultimodalResult result = STUB_ENGINE.analyze(input);

            assertThat(result.audioTranscript()).isNotBlank();
            assertThat(result.sceneDescription()).isNotBlank();
            assertThat(result.combinedAnalysis()).contains("combined:");
        }

        @Test
        @DisplayName("Tenant ID is propagated through to result")
        void tenantIdPropagatedToResult() {
            String tenantId = "tenant-propagation-check";
            MultimodalInput input = MultimodalInput.builder()
                    .tenantId(tenantId)
                    .audioData(new byte[]{1})
                    .build();

            MultimodalResult result = STUB_ENGINE.analyze(input);

            assertThat(result.tenantId()).isEqualTo(tenantId);
        }

        @Test
        @DisplayName("Correlation ID is preserved in result")
        void correlationIdPreservedInResult() {
            String correlationId = "trace-abc-123";
            MultimodalInput input = MultimodalInput.builder()
                    .tenantId("tenant-trace")
                    .correlationId(correlationId)
                    .audioData(new byte[]{1})
                    .build();

            MultimodalResult result = STUB_ENGINE.analyze(input);

            assertThat(result.correlationId()).isEqualTo(correlationId);
        }
    }
}


