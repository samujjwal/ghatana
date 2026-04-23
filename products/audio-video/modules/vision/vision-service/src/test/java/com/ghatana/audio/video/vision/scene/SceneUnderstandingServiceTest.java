package com.ghatana.audio.video.vision.scene;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link SceneUnderstandingService} — AV-009.2.
 *
 * @doc.type class
 * @doc.purpose Unit tests for scene classification and scene understanding
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("SceneUnderstandingService")
class SceneUnderstandingServiceTest {

    /** Stub model: returns outdoor/park at 0.92 and indoor/office at 0.45. */
    private static final SceneUnderstandingService.SceneClassificationModel STUB_MODEL =
            imageBytes -> List.of( // GH-90000
                    new SceneUnderstandingService.SceneLabel("outdoor/park", 0.92), // GH-90000
                    new SceneUnderstandingService.SceneLabel("indoor/office", 0.45) // GH-90000
            );

    // ─── of() ───────────────────────────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("of(null) throws NullPointerException")
    void of_null_throwsNPE() { // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> SceneUnderstandingService.of(null)); // GH-90000
    }

    @Test
    @DisplayName("of(model, threshold > 1) throws IllegalArgumentException")
    void of_invalidThreshold_throwsIAE() { // GH-90000
        assertThatIllegalArgumentException() // GH-90000
                .isThrownBy(() -> SceneUnderstandingService.of(STUB_MODEL, 1.5)); // GH-90000
    }

    // ─── classify() ─────────────────────────────────────────────────────────── // GH-90000

    @Nested
    @DisplayName("classify()")
    class Classify {

        @Test
        @DisplayName("returns outdoor/park as top scene (0.92 > 0.60 default threshold)")
        void classify_topScene_outdoorPark() { // GH-90000
            SceneUnderstandingService service = SceneUnderstandingService.of(STUB_MODEL); // GH-90000
            SceneUnderstandingService.SceneDescription desc = service.classify(new byte[]{1, 2, 3}); // GH-90000

            assertThat(desc.hasTopScene()).isTrue(); // GH-90000
            assertThat(desc.topSceneLabel()).isEqualTo("outdoor/park");
        }

        @Test
        @DisplayName("low-confidence indoor/office is filtered by default threshold")
        void classify_indoorOffice_filtered() { // GH-90000
            SceneUnderstandingService service = SceneUnderstandingService.of(STUB_MODEL); // GH-90000
            SceneUnderstandingService.SceneDescription desc = service.classify(new byte[]{1}); // GH-90000

            assertThat(desc.qualifyingScenes()).hasSize(1); // GH-90000
            assertThat(desc.qualifyingScenes().get(0).label()).isEqualTo("outdoor/park");
        }

        @Test
        @DisplayName("threshold 0.0 includes all scene candidates")
        void classify_zeroThreshold_includesAll() { // GH-90000
            SceneUnderstandingService service = SceneUnderstandingService.of(STUB_MODEL, 0.0); // GH-90000
            SceneUnderstandingService.SceneDescription desc = service.classify(new byte[]{1}); // GH-90000

            assertThat(desc.qualifyingScenes()).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("imageSizeBytes matches the input array length")
        void classify_imageSizeBytes_correct() { // GH-90000
            SceneUnderstandingService service = SceneUnderstandingService.of(STUB_MODEL); // GH-90000
            SceneUnderstandingService.SceneDescription desc = service.classify(new byte[42]); // GH-90000
            assertThat(desc.imageSizeBytes()).isEqualTo(42); // GH-90000
        }

        @Test
        @DisplayName("null imageBytes throws NullPointerException")
        void classify_null_throwsNPE() { // GH-90000
            SceneUnderstandingService service = SceneUnderstandingService.of(STUB_MODEL); // GH-90000
            assertThatNullPointerException().isThrownBy(() -> service.classify(null)); // GH-90000
        }

        @Test
        @DisplayName("empty imageBytes throws IllegalArgumentException")
        void classify_empty_throwsIAE() { // GH-90000
            SceneUnderstandingService service = SceneUnderstandingService.of(STUB_MODEL); // GH-90000
            assertThatIllegalArgumentException() // GH-90000
                    .isThrownBy(() -> service.classify(new byte[0])); // GH-90000
        }

        @Test
        @DisplayName("model returning no labels above threshold still returns a description")
        void classify_noLabelsAboveThreshold_hasNoTopScene() { // GH-90000
            SceneUnderstandingService.SceneClassificationModel noneModel =
                    bytes -> List.of(new SceneUnderstandingService.SceneLabel("x", 0.10)); // GH-90000

            SceneUnderstandingService service = SceneUnderstandingService.of(noneModel, 0.99); // GH-90000
            SceneUnderstandingService.SceneDescription desc = service.classify(new byte[]{1}); // GH-90000

            // No qualifying scenes but best candidate is used as top
            assertThat(desc.hasTopScene()).isTrue(); // GH-90000
            assertThat(desc.qualifyingScenes()).isEmpty(); // GH-90000
        }
    }

    // ─── SceneLabel validation ────────────────────────────────────────────────

    @Test
    @DisplayName("SceneLabel: null label throws NullPointerException")
    void sceneLabel_nullLabel_throwsNPE() { // GH-90000
        assertThatNullPointerException() // GH-90000
                .isThrownBy(() -> new SceneUnderstandingService.SceneLabel(null, 0.8)); // GH-90000
    }

    @Test
    @DisplayName("SceneLabel: confidence > 1 throws IllegalArgumentException")
    void sceneLabel_invalidConfidence_throwsIAE() { // GH-90000
        assertThatIllegalArgumentException() // GH-90000
                .isThrownBy(() -> new SceneUnderstandingService.SceneLabel("outdoor", 1.1)); // GH-90000
    }
}
